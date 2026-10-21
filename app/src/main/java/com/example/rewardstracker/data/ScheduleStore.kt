package com.example.rewardstracker.data

import android.content.Context

// Times of day (minutes since 00:00) at which points are fetched. 0 means 24:00 / 00:00.
object ScheduleStore {
    private const val PREFS = "rewards_schedule"
    private const val KEY = "times"

    // Default: 00:00 and 12:00.
    private val DEFAULT = listOf(0, 720)

    fun times(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY, null) ?: return DEFAULT
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..1439 }
            .distinct()
            .sorted()
            .ifEmpty { DEFAULT }
    }

    fun setTimes(context: Context, times: List<Int>) {
        val cleaned = times.filter { it in 0..1439 }.distinct().sorted().ifEmpty { DEFAULT }
        prefs(context).edit().putString(KEY, cleaned.joinToString(",")).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
