package com.syncdroid.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.syncdroid.app.model.SaveFolder
import com.syncdroid.app.model.SaveStatus

@Composable
fun SaveCard(
    save: SaveFolder,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onCreateNewFolder: () -> Unit = {},
    onChooseExistingFolder: () -> Unit = {},
    onDeclineFolder: () -> Unit = {},
    cloudEnabled: Boolean = false,
    cloudEditable: Boolean = false,
    cloudDetail: String = "Disabled",
    onCloudEnabledChange: (Boolean) -> Unit = {},
    onOpenFolder: () -> Unit = {},
    onOpenFolderSettings: () -> Unit = {},
    onReviewConflicts: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(save.game, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (save.status) {
                            SaveStatus.Configure -> "Available from your mesh · choose a local folder"
                            SaveStatus.Declined -> "Declined on this device"
                            else -> save.updatedOn
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(save.status)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(18.dp))
                DetailRow("Folder", save.path.ifBlank { "Not configured on this device" })
                DetailRow("Copies", "${save.copies} devices")
                if (save.status != SaveStatus.Configure && save.status != SaveStatus.Declined) {
                    DetailRow("Last synced", save.updatedOn)
                }
                DetailRow("File filters", save.filterSummary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cloud sync", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            cloudDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = cloudEnabled,
                        onCheckedChange = if (cloudEditable) onCloudEnabledChange else null,
                    )
                }
                if (save.status != SaveStatus.Configure && save.status != SaveStatus.Declined && save.path.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open folder")
                    }
                }
                if (save.supportsFolderSettings) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenFolderSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (save.exceptionCount == 0) {
                                "Folder settings"
                            } else {
                                "Folder settings · ${save.exceptionCount} ${if (save.exceptionCount == 1) "exception" else "exceptions"}"
                            },
                        )
                    }
                }
                if (save.status == SaveStatus.Conflict) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            "Two devices changed this folder independently. Review both versions before syncing.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onReviewConflicts,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Review conflicts")
                    }
                }
                if (save.status == SaveStatus.Configure) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This folder was added by another mesh device. Its files will not sync here until you choose where they belong.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onCreateNewFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create new folder")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onChooseExistingFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose existing folder")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDeclineFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Decline folder")
                    }
                }
                if (save.status == SaveStatus.Declined) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This folder is not synced to this device. You can configure it later without asking another mesh member to share it again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onCreateNewFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create new folder")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onChooseExistingFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose existing folder")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: SaveStatus) {
    val icon = when (status) {
        SaveStatus.Synced -> Icons.Rounded.CheckCircle
        SaveStatus.Syncing -> Icons.Rounded.Sync
        SaveStatus.Conflict -> Icons.Rounded.Warning
        SaveStatus.Configure -> Icons.Rounded.Folder
        SaveStatus.Declined -> Icons.Rounded.Block
    }
    val label = when (status) {
        SaveStatus.Synced -> "Synced"
        SaveStatus.Syncing -> "Syncing"
        SaveStatus.Conflict -> "Review"
        SaveStatus.Configure -> "Configure"
        SaveStatus.Declined -> "Declined"
    }
    val color = when (status) {
        SaveStatus.Synced -> MaterialTheme.colorScheme.secondary
        SaveStatus.Syncing -> MaterialTheme.colorScheme.primary
        SaveStatus.Conflict -> MaterialTheme.colorScheme.error
        SaveStatus.Configure -> MaterialTheme.colorScheme.tertiary
        SaveStatus.Declined -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.width(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
