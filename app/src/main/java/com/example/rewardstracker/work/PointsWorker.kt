package com.example.rewardstracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rewardstracker.data.FetchResult
import com.example.rewardstracker.data.PointStore

class PointsWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Queue the next scheduled run first, so the chain continues even if this fetch fails.
        Scheduler.scheduleNext(applicationContext)

        // Remember the previous value before the fetch stores the new record.
        val previous = PointStore.entries(applicationContext).value.lastOrNull()?.available

        return when (val result = Fetcher.run(applicationContext)) {
            is FetchResult.Ok -> {
                // No change since the last record (+0): let the user know.
                if (previous != null && result.available == previous) {
                    Notifier.notify(
                        applicationContext,
                        "ポイントが増えていません (+0): ${result.available} pt",
                        id = 2,
                        icon = android.R.drawable.ic_dialog_info
                    )
                }
                Result.success()
            }
            is FetchResult.NotLoggedIn -> {
                Notifier.notify(applicationContext, "セッションが切れました。アプリを開いて再ログインしてください")
                Result.success()
            }
            is FetchResult.Error ->
                if (runAttemptCount < 3) Result.retry() else {
                    Notifier.notify(applicationContext, "ポイントの取得に失敗しました: ${result.message}")
                    Result.failure()
                }
        }
    }
}
