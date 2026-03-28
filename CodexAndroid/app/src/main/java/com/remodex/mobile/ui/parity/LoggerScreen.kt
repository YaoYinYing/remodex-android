package com.remodex.mobile.ui.parity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.remodex.mobile.service.logging.LoggerEntry
import com.remodex.mobile.service.logging.LoggerLevel
import com.remodex.mobile.service.logging.LoggerSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoggerScreen(
    entries: List<LoggerEntry>,
    settings: LoggerSettings,
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionCard(
                title = "Connection Logger",
                subtitle = "Hidden diagnostics view. Captures transport/connection lifecycle."
            ) {
                Text(
                    text = "Level: ${settings.level.name} | Max lines: ${settings.maxLines} | Current: ${entries.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = onClear,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Logs")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries.asReversed(), key = { it.id }) { entry ->
                    LoggerRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun LoggerRow(entry: LoggerEntry) {
    val timestamp = formatTimestamp(entry.timestampMillis)
    val levelColor = when (entry.level) {
        LoggerLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        LoggerLevel.INFO -> Color(0xFF2B6CB0)
        LoggerLevel.WARN -> Color(0xFFC47A00)
        LoggerLevel.ERROR -> Color(0xFFD9534F)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$timestamp  ${entry.level.name}  ${entry.tag}",
                style = MaterialTheme.typography.labelSmall,
                color = levelColor
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatTimestamp(timestampMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return formatter.format(Date(timestampMillis))
}
