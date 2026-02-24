package `in`.cartunez.flow.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.data.TransactionRepository

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FlowApp
        val txRepo = TransactionRepository(
            app.database.transactionDao(),
            app.apiService,
            app.prefsStore
        )
        return try {
            // Slip sync runs first so both use the same lastSync timestamp.
            // txRepo.sync() updates lastSync at the end.
            app.slipsRepository.sync()
            txRepo.sync()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
