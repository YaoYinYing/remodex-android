package com.remodex.mobile.ui.parity

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.model.TimelineRole
import com.remodex.mobile.service.PendingPermissionRequest
import com.remodex.mobile.service.ServiceEvent
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.R
import com.remodex.mobile.ui.theme.AlertAmber
import com.remodex.mobile.ui.theme.AlertRed
import com.remodex.mobile.ui.theme.CommandAccent
import com.remodex.mobile.ui.theme.PlanAccent

@Composable
fun HeroCard(
    title: String = "Remodex",
    stateLabel: String,
    status: String,
    indicatorColor: Color,
    subtitle: String,
    onTap: (() -> Unit)? = null
) {
    val heroGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (onTap != null) base.clickable(onClick = onTap) else base },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradient)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Text(
                    text = stateLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
fun CompactToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(label)
    }
}

@Composable
fun StatusPill(
    text: String,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SmallChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun InlineStatusCard(
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyHomeCard(
    connectionState: ConnectionState,
    status: String,
    trustedPairLabel: String?,
    projectPath: String?,
    rateLimitInfo: String,
    ciStatus: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
    onForgetPair: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(30.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.original_author_icon),
                    contentDescription = "Remodex",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                StatusPill(
                    text = when (connectionState) {
                        ConnectionState.Connected -> "Connected"
                        ConnectionState.Connecting -> "Connecting"
                        ConnectionState.Paired -> "Paired"
                        ConnectionState.Disconnected -> "Offline"
                        is ConnectionState.Failed -> "Failed"
                    },
                    accent = when (connectionState) {
                        ConnectionState.Connected -> CommandAccent
                        ConnectionState.Connecting -> AlertAmber
                        ConnectionState.Paired -> PlanAccent
                        ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
                        is ConnectionState.Failed -> AlertRed
                    }
                )
            }
            Text(
                text = "Remodex",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!trustedPairLabel.isNullOrBlank()) {
                Text(
                    text = trustedPairLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!projectPath.isNullOrBlank()) {
                Text(
                    text = projectPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = rateLimitInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ciStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimaryAction, modifier = Modifier.weight(1f)) {
                    Text(primaryActionLabel)
                }
                OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                    Text(secondaryActionLabel)
                }
            }
            if (onForgetPair != null) {
                OutlinedButton(
                    onClick = onForgetPair,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Forget Pair")
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    request: PendingPermissionRequest,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = request.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!request.summary.isNullOrBlank()) {
                Text(
                    text = request.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
                    Text("Deny")
                }
                Button(onClick = onAllow, modifier = Modifier.weight(1f)) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
fun TodoRow(todo: WebsiteTodo, state: TodoState) {
    val badgeColor = when (state) {
        TodoState.TODO -> AlertRed
        TodoState.IN_PROGRESS -> AlertAmber
        TodoState.DONE -> CommandAccent
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = state.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor
                )
                Text(
                    text = todo.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EventRow(event: ServiceEvent) {
    val roleColor = when (event.kind.name) {
        "RATE_LIMIT_HIT" -> AlertRed
        "PERMISSION_REQUIRED" -> AlertAmber
        "GIT_ACTION" -> CommandAccent
        "CI_CD_DONE" -> PlanAccent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, roleColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelSmall,
                color = roleColor
            )
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ThreadRow(
    thread: ThreadSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    depth: Int = 0,
    childCount: Int = 0,
    isChildrenExpanded: Boolean = false,
    onToggleChildren: (() -> Unit)? = null
) {
    val borderColor = animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "threadBorder"
    )
    val depthPadding = (depth.coerceIn(0, 5) * 10).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = depthPadding)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor.value.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = if (thread.isSubagent) 8.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = thread.agentDisplayLabel ?: thread.displayTitle,
                    style = if (thread.isSubagent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!thread.preview.isNullOrBlank()) {
                    Text(
                        text = thread.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (thread.isSubagent) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thread.isArchived) {
                        ThreadMetadataBadge(label = "Archived")
                    }
                    if (thread.isForkedThread) {
                        ThreadMetadataBadge(label = "Fork")
                    }
                    if (!thread.model.isNullOrBlank()) {
                        ThreadMetadataBadge(label = thread.model)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (childCount > 0 && onToggleChildren != null) {
                    Text(
                        text = if (isChildrenExpanded) "v $childCount" else "> $childCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleChildren)
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
                if (thread.isSubagent) {
                    ThreadMetadataBadge(label = "Agent")
                }
            }
        }
    }
}

@Composable
private fun ThreadMetadataBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TimelineRow(item: TimelineEntry) {
    val normalizedType = item.type.trim().lowercase()
    val isCommandLike = normalizedType.contains("command")
    val isVerboseType = isCommandLike
        || normalizedType.contains("reasoning")
        || normalizedType.contains("tool")
        || normalizedType.contains("plan")
    val textLineCount = remember(item.text) { item.text.count { it == '\n' } + 1 }
    val shouldDefaultCollapse = remember(item.id, item.text, normalizedType) {
        isVerboseType || item.text.length > 280 || textLineCount > 5
    }
    var expanded by rememberSaveable(item.id) { mutableStateOf(!shouldDefaultCollapse) }
    val collapsedMaxLines = if (isCommandLike) 2 else 4
    val firstLinePreview = remember(item.text) {
        item.text.lineSequence()
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: item.text.trim().take(160)
    }
    val accent = when {
        item.role == TimelineRole.USER -> PlanAccent
        item.role == TimelineRole.ASSISTANT -> CommandAccent
        normalizedType.contains("plan") -> PlanAccent
        normalizedType.contains("reasoning") -> AlertAmber
        normalizedType.contains("tool") || normalizedType.contains("command") -> CommandAccent
        normalizedType.contains("diff") || normalizedType.contains("filechange") -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val bubbleColor = when {
        item.role == TimelineRole.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        item.role == TimelineRole.ASSISTANT -> MaterialTheme.colorScheme.surface
        normalizedType.contains("reasoning") -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val label = when {
        item.role == TimelineRole.USER -> "user"
        item.role == TimelineRole.ASSISTANT -> "assistant"
        normalizedType.contains("plan") -> "plan"
        normalizedType.contains("reasoning") -> "thinking"
        normalizedType.contains("tool") -> "tool"
        normalizedType.contains("command") -> "command"
        normalizedType.contains("filechange") -> "file change"
        normalizedType.contains("diff") -> "diff"
        else -> "system"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = bubbleColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$label • ${item.type}",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            if (isCommandLike && shouldDefaultCollapse && !expanded && firstLinePreview.isNotBlank()) {
                Text(
                    text = firstLinePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded || !shouldDefaultCollapse) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (shouldDefaultCollapse) {
                Text(
                    text = if (expanded) "Show less" else if (isCommandLike) "Show output" else "Show more",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}
