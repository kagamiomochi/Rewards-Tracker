package com.example.rewardstracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rewardstracker.data.FetchResult
import com.example.rewardstracker.data.PointEntry
import com.example.rewardstracker.data.PointStore
import java.time.LocalDate

class PointsWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Queue the next scheduled run first, so the chain continues even if this fetch fails.
        Scheduler.scheduleNext(applicationContext)

        // Take a snapshot before the fetch stores the new record.
        val before = PointStore.entries(applicationContext).value
        val previous = before.lastOrNull()?.available
        val increasedToday = hasIncreasedOn(before, LocalDate.now().toEpochDay())

        return when (val result = Fetcher.run(applicationContext)) {
            is FetchResult.Ok -> {
                // No change since the last record (+0): let the user know,
                // unless the points have already increased earlier today.
                if (previous != null && result.available == previous && !increasedToday) {
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

    // True if any consecutive pair of records on the given day shows an increase.
    // The last record before that day is used as the baseline for the first record of the day.
    private fun hasIncreasedOn(entries: List<PointEntry>, epochDay: Long): Boolean {
        val baseline = entries.lastOrNull { it.epochDay < epochDay }
        val today = entries.filter { it.epochDay == epochDay }
        return (listOfNotNull(baseline) + today)
            .zipWithNext()
            .any { (a, b) -> b.available > a.available }
    }
}
