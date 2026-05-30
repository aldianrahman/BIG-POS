package com.berdikariintigemilang.pos.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Worker latar belakang yang mengirim transaksi tertunda saat ada koneksi. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = syncManager.syncPending()
        // Bila masih ada yang gagal sementara, minta WorkManager mencoba lagi (backoff).
        return if (outcome.retriable > 0) Result.retry() else Result.success()
    }
}
