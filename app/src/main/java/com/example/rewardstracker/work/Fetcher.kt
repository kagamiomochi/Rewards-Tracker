package com.example.rewardstracker.work

import android.content.Context
import com.example.rewardstracker.data.FetchResult
import com.example.rewardstracker.data.PointEntry
import com.example.rewardstracker.data.PointStore
import com.example.rewardstracker.data.RewardsClient
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Shared by the background worker and the "fetch now" button.
object Fetcher {
    suspend fun run(context: Context): FetchResult {
        val result = RewardsClient.fetch(context)
        if (result is FetchResult.Ok) {
            PointStore.upsert(
                context,
                PointEntry(
                    epochDay = LocalDate.now().toEpochDay(),
                    available = result.available,
                    lifetime = result.lifetime,
                    fetchedAt = System.currentTimeMillis()
                )
            )
        }
        PointStore.setFetchOk(context, result is FetchResult.Ok)
        val label = when (result) {
            is FetchResult.Ok -> "OK ${result.available} pt"
            is FetchResult.NotLoggedIn -> "NOT LOGGED IN"
            is FetchResult.Error -> "ERROR ${result.message}"
        }
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        PointStore.setStatus(context, "[$time] $label\n${result.trace}")
        return result
    }
}
