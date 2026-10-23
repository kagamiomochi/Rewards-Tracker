package com.example.rewardstracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rewardstracker.data.PointStore
import com.example.rewardstracker.data.ScheduleStore
import com.example.rewardstracker.work.Fetcher
import com.example.rewardstracker.work.Scheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by PointStore.entries(context).collectAsStateWithLifecycle()
    val status by PointStore.status(context).collectAsStateWithLifecycle()
    val fetchOk by PointStore.fetchOk(context).collectAsStateWithLifecycle()
    val fmt = remember { DateTimeFormatter.ofPattern("M/d HH:mm") }

    var times by remember { mutableStateOf(ScheduleStore.times(context)) }
    var showSchedule by remember { mutableStateOf(false) }

    // The chart shows the last 30 days; the list below keeps every record.
    val recent = remember(entries) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        entries.filter { it.fetchedAt >= cutoff }
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = entries.lastOrNull()?.let { "${it.available} pt" } ?: "--",
                style = MaterialTheme.typography.displayMedium
            )

            PointsChart(recent, Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            PointStore.setStatus(context, "取得中...")
                            Fetcher.run(context)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("今すぐ取得", maxLines = 1) }
                OutlinedButton(
                    onClick = { showSchedule = true },
                    modifier = Modifier.weight(1f)
                ) { Text("取得時刻", maxLines = 1) }
            }

            // Shown only while the last fetch failed (or nothing has been fetched yet).
            if (!fetchOk) {
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("ログインしてください", maxLines = 1) }
            }

            Text(
                text = "自動取得: " + times.joinToString(", ") { formatTime(it) },
                style = MaterialTheme.typography.labelMedium
            )

            // Result of the last fetch (cookie names only, no values).
            if (status.isNotBlank()) {
                SelectionContainer {
                    Text(status, style = MaterialTheme.typography.labelSmall, maxLines = 40)
                }
            }

            // Newest first, with the difference from the previous record.
            // Rows with no change (+0) are hidden; the diff is still computed against
            // the immediately preceding record, so the remaining rows stay accurate.
            val rows = entries.mapIndexed { i, e ->
                e to (if (i > 0) e.available - entries[i - 1].available else null)
            }.filter { it.second != 0 }.asReversed()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows, key = { it.first.fetchedAt }) { (e, diff) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Instant.ofEpochMilli(e.fetchedAt).atZone(ZoneId.systemDefault()).format(fmt))
                        val diffText = when {
                            diff == null -> ""
                            diff >= 0 -> "  (+$diff)"
                            else -> "  ($diff)"
                        }
                        Text("${e.available}$diffText")
                    }
                }
            }
        }
    }

    if (showSchedule) {
        ScheduleDialog(
            times = times,
            onChange = { updated ->
                times = updated
                ScheduleStore.setTimes(context, updated)
                // Re-plan the queued job so the new times take effect immediately.
                Scheduler.reschedule(context)
            },
            onDismiss = { showSchedule = false }
        )
    }
}
