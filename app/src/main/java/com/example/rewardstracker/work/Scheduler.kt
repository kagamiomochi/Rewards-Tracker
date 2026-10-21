package com.example.rewardstracker.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.rewardstracker.data.ScheduleStore
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

// Runs a fetch at each configured time of day. WorkManager cannot repeat at fixed
// clock times, so a chain of one-time jobs is used: every run enqueues the next one.
object Scheduler {
    private const val TAG = "scheduled_fetch"
    private const val LEGACY_DAILY_NAME = "daily_points"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // Called on app start. Safe to call repeatedly (an already queued job is kept).
    fun ensureScheduled(context: Context) {
        // Remove the old fixed 24-hour periodic job from earlier versions.
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_DAILY_NAME)
        scheduleNext(context)
    }

    // Called when the list of times changes: drop the queued job and plan again.
    fun reschedule(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        scheduleNext(context)
    }

    // Enqueues a job for the earliest upcoming time. The unique name contains the target
    // minute, so the running job never cancels itself and duplicates are avoided.
    fun scheduleNext(context: Context) {
        val times = ScheduleStore.times(context)
        val now = ZonedDateTime.now()
        val next = times.map { nextOccurrence(now, it) }.minBy { it.toEpochSecond() }
        val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<PointsWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${TAG}_${next.toEpochSecond() / 60}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    // One immediate fetch, independent of the schedule.
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PointsWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun nextOccurrence(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val today = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
