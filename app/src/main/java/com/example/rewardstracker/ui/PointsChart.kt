package com.example.rewardstracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.rewardstracker.data.PointEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PointsChart(entries: List<PointEntry>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val fmt = DateTimeFormatter.ofPattern("M/d HH:mm")

    if (entries.size < 2) {
        Text("グラフは2回以上取得すると表示されます", modifier = modifier)
        return
    }

    val min = entries.minOf { it.available }
    val max = entries.maxOf { it.available }
    val range = (max - min).coerceAtLeast(1)
    val t0 = entries.first().fetchedAt
    val span = (entries.last().fetchedAt - t0).coerceAtLeast(1L)

    fun label(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fmt)

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("max $max", style = MaterialTheme.typography.labelSmall)
            Text("min $min", style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val pad = 8.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2

            // Horizontal grid lines at the top, middle and bottom.
            listOf(0f, 0.5f, 1f).forEach { r ->
                val y = pad + h * r
                drawLine(grid, Offset(pad, y), Offset(pad + w, y), strokeWidth = 1.dp.toPx())
            }

            // The x axis is real time, so several fetches in one day are spaced correctly.
            val path = Path()
            entries.forEachIndexed { i, e ->
                val x = pad + w * ((e.fetchedAt - t0).toFloat() / span)
                val y = pad + h * (1f - (e.available - min).toFloat() / range)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(line, radius = 3.dp.toPx(), center = Offset(x, y))
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label(entries.first().fetchedAt), style = MaterialTheme.typography.labelSmall)
            Text(label(entries.last().fetchedAt), style = MaterialTheme.typography.labelSmall)
        }
    }
}
