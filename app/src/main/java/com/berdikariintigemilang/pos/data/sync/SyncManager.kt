package com.berdikariintigemilang.pos.data.sync

import com.berdikariintigemilang.pos.data.remote.ApiService
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
 * Mengirim semua transaksi tertunda ke server, urut waktu jual, memakai
 * idempotency key milik tiap transaksi sehingga aman diulang.
 *
 * Klasifikasi kegagalan:
 * - Jaringan / 5xx / 401  -> retriable (tetap di antrian, dicoba lagi).
 * - NO_OPEN_SHIFT (400)   -> retriable (operator perlu membuka shift di server).
 * - Error bisnis lain     -> conflict (butuh perhatian manual, tidak auto-retry).
 *
 * Server TIDAK pernah mencatat dobel karena idempotency key sudah unik di DB.
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
            var synced = 0
            var retriable = 0
            var conflict = 0
            for (e in store.pendingForSync()) {
                try {
                    val res = api.createTransaction(e.clientTxnId, store.toRequest(e))
                    val data = res.data
                    if (res.success && data != null) {
                        store.markSynced(e.clientTxnId, data.id, data.trxNo)
                        synced++
                    } else {
                        store.markConflict(e.clientTxnId, res.error?.message ?: res.message)
                        conflict++
                    }
                } catch (io: IOException) {
                    // Sinyal hilang: hentikan putaran, sisanya tetap tertunda.
                    store.markRetry(e.clientTxnId, "Koneksi terputus")
                    retriable++
                    break
                } catch (http: HttpException) {
                    val code = http.code()
                    when {
                        code == 401 -> { retriable++; break } // token kedaluwarsa: tunggu login ulang
                        code in 500..599 -> { store.markRetry(e.clientTxnId, "Server bermasalah ($code)"); retriable++ }
                        parseErrorCode(http) == "NO_OPEN_SHIFT" -> {
                            store.markRetry(e.clientTxnId, "Belum ada shift terbuka di server"); retriable++
                        }
                        else -> { store.markConflict(e.clientTxnId, "Ditolak server ($code)"); conflict++ }
                    }
                } catch (ex: Exception) {
                    store.markRetry(e.clientTxnId, ex.message)
                    retriable++
                }
            }
            SyncOutcome(synced, retriable, conflict)
        } finally {
            _syncing.value = false
        }
    }

    private fun parseErrorCode(http: HttpException): String? = try {
        http.response()?.errorBody()?.string()?.let { errorAdapter.fromJson(it)?.error?.code }
    } catch (e: Exception) {
        null
    }
}
