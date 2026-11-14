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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Button
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
    val activeOnly: Boolean = true,
    val memorySummary: String = "",
    val detailApp: RunningAppItem? = null
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RunningAppsPane(
    state: RunningAppsUiState,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null,
    onToggleActiveOnly: () -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (RunningAppItem) -> Unit,
    onAppLongClick: (RunningAppItem) -> Unit,
    onDismissDetails: () -> Unit,
    onOpenAppInfo: (RunningAppItem) -> Unit
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

            OutlinedButton(
                onClick = onToggleActiveOnly,
                modifier = if (initialFocusRequester != null) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
            ) {
                Text(
                    text = stringResource(
                        if (state.activeOnly) R.string.active_only_on else R.string.active_only_off
                    )
                )
            }

            OutlinedButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.refresh_button),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Summary
        Text(
            text = appListSummaryText(state),
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
                            RunningAppRow(
                                app = app,
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
            onOpenAppInfo = { onOpenAppInfo(app) }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RunningAppRow(
    app: RunningAppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val metaParts = buildList {
        add(app.packageName)
        app.versionName?.let { if (it.isNotBlank()) add("v$it") }
    }

    val badgeText = if (app.isStopped) {
        stringResource(R.string.stopped_badge)
    } else {
        stringResource(R.string.active_badge)
    }

    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            AppIconImage(icon = app.icon, modifier = Modifier.size(32.dp))
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
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = if (app.isStopped) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.Bold
            )
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
    onOpenAppInfo: () -> Unit
) {
    val details = buildString {
        append("Package: ${app.packageName}")
        app.versionName?.let { if (it.isNotBlank()) append("\nVersion: $it") }
        append("\nStatus: ${if (app.isStopped) "Stopped" else "Active (not force-stopped)"}")
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
                    Button(onClick = onOpenAppInfo) {
                        Text(text = stringResource(R.string.app_info_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun appListSummaryText(state: RunningAppsUiState): String {
    val countText = if (state.apps.size == 1) {
        stringResource(R.string.one_app)
    } else {
        stringResource(R.string.apps_count, state.apps.size)
    }
    return "$countText • ${stringResource(R.string.app_info_hint)}"
}
