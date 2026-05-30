package com.berdikariintigemilang.pos.data.sync

import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.BatchItem
import com.berdikariintigemilang.pos.data.remote.BatchRequest
import com.berdikariintigemilang.pos.data.repository.OfflineTransactionStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Ringkasan hasil satu putaran sinkronisasi. */
data class SyncOutcome(val synced: Int = 0, val retriable: Int = 0, val conflict: Int = 0)

/** Body error POS (untuk membaca kode error dari respons non-2xx). */
@JsonClass(generateAdapter = true)
data class SyncErrorBody(val error: SyncErrorDetail? = null)

@JsonClass(generateAdapter = true)
data class SyncErrorDetail(val code: String? = null, val message: String? = null)

/**
 * Mengirim transaksi tertunda ke server. Memakai endpoint BATCH (satu request
 * untuk banyak transaksi) agar hemat & andal di sinyal lemah; bila endpoint
 * batch tak tersedia (server lama / 404), otomatis fallback ke pengiriman satu
 * per satu. Tiap transaksi membawa idempotency key sehingga aman diulang —
 * server tidak pernah mencatat dobel.
 *
 * Klasifikasi kegagalan per transaksi:
 * - Jaringan / 5xx / 401  -> retriable (tetap di antrian, dicoba lagi).
 * - NO_OPEN_SHIFT         -> retriable (operator perlu membuka shift di server).
 * - Error bisnis lain     -> conflict (butuh perhatian manual, tidak auto-retry).
 */
@Singleton
class SyncManager @Inject constructor(
    private val store: OfflineTransactionStore,
    private val api: ApiService,
    moshi: Moshi
) {
    private val errorAdapter = moshi.adapter(SyncErrorBody::class.java)
    private val mutex = Mutex()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Aman dipanggil berkali-kali; hanya satu putaran berjalan pada satu waktu. */
    suspend fun syncPending(): SyncOutcome = mutex.withLock {
        _syncing.value = true
        try {
            val pending = store.pendingForSync()
            if (pending.isEmpty()) return SyncOutcome()
            try {
                syncBatch(pending)
            } catch (e: BatchUnsupported) {
                // Server tak mendukung batch -> kirim satu per satu.
                syncOneByOne(pending)
            }
        } finally {
            _syncing.value = false
        }
    }

    private class BatchUnsupported : Exception()

    private suspend fun syncBatch(pending: List<com.berdikariintigemilang.pos.data.local.PendingTransactionEntity>): SyncOutcome {
        val byKey = pending.associateBy { it.clientTxnId }
        val req = BatchRequest(pending.map { BatchItem(it.clientTxnId, store.toRequest(it)) })
        val res = try {
            api.createTransactionsBatch(req)
        } catch (io: IOException) {
            // Koneksi putus saat batch: tandai semua untuk dicoba lagi.
            pending.forEach { store.markRetry(it.clientTxnId, "Koneksi terputus") }
            return SyncOutcome(retriable = pending.size)
        } catch (http: HttpException) {
            when (http.code()) {
                404, 405 -> throw BatchUnsupported() // endpoint batch tidak ada -> fallback
                401 -> return SyncOutcome(retriable = pending.size) // tunggu login ulang
                in 500..599 -> {
                    pending.forEach { store.markRetry(it.clientTxnId, "Server bermasalah (${http.code()})") }
                    return SyncOutcome(retriable = pending.size)
                }
                else -> throw BatchUnsupported()
            }
        }

        val data = res.data
        if (!res.success || data == null) {
            pending.forEach { store.markRetry(it.clientTxnId, res.error?.message ?: res.message) }
            return SyncOutcome(retriable = pending.size)
        }

        var synced = 0; var retriable = 0; var conflict = 0
        for (r in data.results) {
            val entity = byKey[r.idempotencyKey] ?: continue
            if (r.success && r.transaction != null) {
                store.markSynced(r.idempotencyKey, r.transaction.id, r.transaction.trxNo, r.transaction.stockWarning)
                synced++
            } else if (r.errorCode == "NO_OPEN_SHIFT") {
                store.markRetry(r.idempotencyKey, "Belum ada shift terbuka di server"); retriable++
            } else {
                store.markConflict(r.idempotencyKey, r.errorMessage ?: r.errorCode ?: "Ditolak server"); conflict++
            }
        }
        return SyncOutcome(synced, retriable, conflict)
    }

    private suspend fun syncOneByOne(pending: List<com.berdikariintigemilang.pos.data.local.PendingTransactionEntity>): SyncOutcome {
        var synced = 0; var retriable = 0; var conflict = 0
        for (e in pending) {
            try {
                val res = api.createTransaction(e.clientTxnId, store.toRequest(e))
                val data = res.data
                if (res.success && data != null) {
                    store.markSynced(e.clientTxnId, data.id, data.trxNo, data.stockWarning); synced++
                } else {
                    store.markConflict(e.clientTxnId, res.error?.message ?: res.message); conflict++
                }
            } catch (io: IOException) {
                store.markRetry(e.clientTxnId, "Koneksi terputus"); retriable++; break
            } catch (http: HttpException) {
                val code = http.code()
                when {
                    code == 401 -> { retriable++; break }
                    code in 500..599 -> { store.markRetry(e.clientTxnId, "Server bermasalah ($code)"); retriable++ }
                    parseErrorCode(http) == "NO_OPEN_SHIFT" -> { store.markRetry(e.clientTxnId, "Belum ada shift terbuka di server"); retriable++ }
                    else -> { store.markConflict(e.clientTxnId, "Ditolak server ($code)"); conflict++ }
                }
            } catch (ex: Exception) {
                store.markRetry(e.clientTxnId, ex.message); retriable++
            }
        }
        return SyncOutcome(synced, retriable, conflict)
    }

    private fun parseErrorCode(http: HttpException): String? = try {
        http.response()?.errorBody()?.string()?.let { errorAdapter.fromJson(it)?.error?.code }
    } catch (e: Exception) {
        null
    }
}
