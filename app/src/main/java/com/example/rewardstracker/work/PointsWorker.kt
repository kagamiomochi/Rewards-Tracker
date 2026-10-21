package com.example.rewardstracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rewardstracker.data.FetchResult

class PointsWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Queue the next scheduled run first, so the chain continues even if this fetch fails.
        Scheduler.scheduleNext(applicationContext)

        return when (val result = Fetcher.run(applicationContext)) {
            is FetchResult.Ok -> Result.success()
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
