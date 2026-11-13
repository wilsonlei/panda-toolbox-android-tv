package com.panda.toolbox.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.panda.toolbox.R
import com.panda.toolbox.model.RunningAppItem

data class RunningAppsUiState(
    val apps: List<RunningAppItem> = emptyList(),
    val isLoading: Boolean = false,
    val showSystemApps: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val memorySummary: String = "",
    val detailApp: RunningAppItem? = null,
    val terminateConfirmApps: List<RunningAppItem>? = null
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RunningAppsPane(
    state: RunningAppsUiState,
    modifier: Modifier = Modifier,
    onToggleSelectionMode: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRequestTerminateSelected: () -> Unit,
    onToggleSystemApps: () -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (RunningAppItem) -> Unit,
    onAppLongClick: (RunningAppItem) -> Unit,
    onDismissDetails: () -> Unit,
    onSelectFromDetails: (RunningAppItem) -> Unit,
    onOpenAppInfo: (RunningAppItem) -> Unit,
    onTerminateFromDetails: (RunningAppItem) -> Unit,
    onConfirmTerminate: (List<RunningAppItem>) -> Unit,
    onDismissTerminateConfirm: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxHeight()
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.running_apps_title),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            OutlinedButton(onClick = onToggleSelectionMode) {
                Text(
                    text = stringResource(
                        if (state.selectionMode) R.string.cancel_select else R.string.select_mode
                    )
                )
            }

            if (state.selectionMode) {
                val allSelected = state.apps.isNotEmpty() &&
                    state.selectedPackages.size == state.apps.size
                OutlinedButton(onClick = onToggleSelectAll) {
                    Text(
                        text = stringResource(
                            if (allSelected) R.string.clear_selection else R.string.select_all
                        )
                    )
                }
            }

            if (state.selectedPackages.isNotEmpty()) {
                Button(
                    onClick = onRequestTerminateSelected,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        focusedContainerColor = Color(0xFFDC2626),
                        focusedContentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.terminate_selected, state.selectedPackages.size)
                    )
                }
            }

            OutlinedButton(onClick = onToggleSystemApps) {
                Text(
                    text = stringResource(
                        if (state.showSystemApps) R.string.system_apps_on else R.string.system_apps_off
                    )
                )
            }

            OutlinedButton(onClick = onRefresh) {
                Text(text = stringResource(R.string.refresh_button))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Summary
        Text(
            text = runningAppsSummaryText(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // App List / Empty / Loading
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.isLoading -> {
                    Text(
                        text = stringResource(R.string.loading_text),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.apps.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_running_apps),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = state.apps,
                            key = { it.packageName }
                        ) { app ->
                            val isSelected = state.selectedPackages.contains(app.packageName)
                            RunningAppRow(
                                app = app,
                                selectionMode = state.selectionMode,
                                isSelected = isSelected,
                                onClick = { onAppClick(app) },
                                onLongClick = { onAppLongClick(app) }
                            )
                        }
                    }
                }
            }
        }
    }

    // App Details Dialog
    state.detailApp?.let { app ->
        AppDetailsDialog(
            app = app,
            onDismiss = onDismissDetails,
            onSelect = { onSelectFromDetails(app) },
            onOpenAppInfo = { onOpenAppInfo(app) },
            onTerminate = { onTerminateFromDetails(app) }
        )
    }

    // Terminate Confirmation Dialog
    state.terminateConfirmApps?.let { appsToTerminate ->
        TerminateConfirmDialog(
            apps = appsToTerminate,
            onConfirm = { onConfirmTerminate(appsToTerminate) },
            onDismiss = onDismissTerminateConfirm
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RunningAppRow(
    app: RunningAppItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val metaParts = buildList {
        add(app.packageName)
        app.pid?.let { add("PID $it") }
        app.memoryMb?.let { add("$it MB") }
        app.importanceLabel?.let { add(it) }
    }

    val badgeText = when {
        app.isSystemApp -> stringResource(R.string.system_badge)
        app.pid != null -> stringResource(R.string.active_badge)
        else -> ""
    }

    ListItem(
        selected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                }
                AppIconImage(icon = app.icon, modifier = Modifier.size(32.dp))
            }
        },
        headlineContent = {
            Text(
                text = app.appName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = metaParts.joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (badgeText.isNotEmpty()) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.isSystemApp) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
private fun AppIconImage(icon: Drawable?, modifier: Modifier = Modifier) {
    val bitmap = remember(icon) {
        icon?.let {
            try {
                it.toBitmap(width = 64, height = 64).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_apps),
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppDetailsDialog(
    app: RunningAppItem,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onTerminate: () -> Unit
) {
    val details = buildString {
        append("Package: ${app.packageName}")
        app.versionName?.let { append("\nVersion: $it") }
        app.pid?.let { append("\nPID: $it") }
        app.memoryMb?.let { append("\nMemory: $it MB") }
        app.importanceLabel?.let { append("\nState: $it") }
        append("\nType: ${if (app.isSystemApp) "System App" else "User App"}")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_details_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.cancel_action))
                    }
                    OutlinedButton(onClick = onSelect) {
                        Text(text = stringResource(R.string.select_item_action))
                    }
                    OutlinedButton(onClick = onOpenAppInfo) {
                        Text(text = stringResource(R.string.app_info_action))
                    }
                    Button(
                        onClick = onTerminate,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            focusedContainerColor = Color(0xFFDC2626),
                            focusedContentColor = Color.White
                        )
                    ) {
                        Text(text = stringResource(R.string.terminate_action))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TerminateConfirmDialog(
    apps: List<RunningAppItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (apps.size == 1) {
        stringResource(R.string.terminate_confirm_title, apps.first().appName)
    } else {
        stringResource(R.string.terminate_confirm_title, "${apps.size} apps")
    }

    val message = if (apps.size == 1) {
        stringResource(R.string.terminate_confirm_single, apps.first().appName, apps.first().packageName)
    } else {
        stringResource(R.string.terminate_confirm_multi, apps.size)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.cancel_action))
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            focusedContainerColor = Color(0xFFDC2626),
                            focusedContentColor = Color.White
                        )
                    ) {
                        Text(text = stringResource(R.string.terminate_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun runningAppsSummaryText(state: RunningAppsUiState): String {
    val countText = if (state.apps.size == 1) {
        stringResource(R.string.one_app)
    } else {
        stringResource(R.string.apps_count, state.apps.size)
    }
    return if (state.selectionMode) {
        "$countText • ${state.selectedPackages.size} selected"
    } else {
        "$countText • Long-press OK on any app to select"
    }
}
