package com.example.rewardstracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// "00:00" style text for a time given as minutes since midnight.
fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
fun ScheduleDialog(
    times: List<Int>,
    onChange: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("取得する時刻") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                times.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (m == 0) "${formatTime(m)} (24時)" else formatTime(m),
                            style = MaterialTheme.typography.titleMedium
                        )
                        // Keep at least one time so the schedule never becomes empty.
                        TextButton(
                            onClick = { onChange(times - m) },
                            enabled = times.size > 1
                        ) { Text("削除") }
                    }
                }
                OutlinedButton(onClick = { picking = true }) { Text("時刻を追加") }
                Text(
                    "実行時刻は端末の省電力機能で多少ずれることがあります。",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )

    if (picking) {
        TimePickDialog(
            onPick = { m ->
                onChange((times + m).distinct().sorted())
                picking = false
            },
            onCancel = { picking = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(onPick: (Int) -> Unit, onCancel: () -> Unit) {
    val state = rememberTimePickerState(initialHour = 12, initialMinute = 0, is24Hour = true)

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("時刻を選ぶ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("キャンセル") } }
    )
}
