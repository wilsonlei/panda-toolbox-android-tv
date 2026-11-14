package com.panda.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.panda.toolbox.R
import com.panda.toolbox.model.StorageRoot
import com.panda.toolbox.ui.filemanager.FileManagerUiState

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun MainMenuSidebar(
    state: FileManagerUiState,
    runningAppsState: RunningAppsUiState,
    ftpServerState: FtpServerUiState,
    sidebarRootFocusRequesters: MutableMap<String, FocusRequester>,
    initialSidebarFocusRequester: FocusRequester,
    runningAppsSidebarFocusRequester: FocusRequester,
    ftpServerSidebarFocusRequester: FocusRequester,
    parentRowFocusRequester: FocusRequester,
    firstFileFocusRequester: FocusRequester,
    onSelectRoot: (StorageRoot) -> Unit,
    onSelectRunningApps: () -> Unit,
    onSelectFtpServer: () -> Unit,
    onFocusRoot: (StorageRoot) -> Unit,
    onFocusRunningApps: () -> Unit
) {
    var focusedSection by remember { mutableStateOf<ToolboxSection?>(null) }
    Surface(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionRight &&
                    state.activeSection == ToolboxSection.FILE_MANAGER
                ) {
                    when {
                        state.canNavigateUp -> {
                            runCatching { parentRowFocusRequester.requestFocus() }
                            return@onPreviewKeyEvent true
                        }
                        state.items.isNotEmpty() -> {
                            runCatching { firstFileFocusRequester.requestFocus() }
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            },
        shape = RoundedCornerShape(14.dp),
        colors = SurfaceDefaults.colors(
            containerColor = Color(0xFF161D26)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.tools_section_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            val internalLabel = stringResource(R.string.storage_internal)
            val internalRoot = state.storageRoots.firstOrNull { it.label == internalLabel }
                ?: state.storageRoots.firstOrNull()
            val otherRoots = if (internalRoot != null) {
                state.storageRoots.filter { it.directory.absolutePath != internalRoot.directory.absolutePath }
            } else {
                emptyList()
            }

            if (internalRoot != null) {
                val internalPath = internalRoot.directory.absolutePath
                val isInternalActive = state.activeSection == ToolboxSection.FILE_MANAGER &&
                    internalPath == state.activeRootPath
                val internalFocusRequester = remember(internalPath) {
                    sidebarRootFocusRequesters.getOrPut(internalPath) { FocusRequester() }
                }
                DisposableEffect(internalPath) {
                    onDispose {
                        sidebarRootFocusRequesters.remove(internalPath)
                    }
                }
                ListItem(
                    selected = isInternalActive,
                    onClick = { onSelectRoot(internalRoot) },
                    modifier = Modifier
                        .focusRequester(internalFocusRequester)
                        .focusRequester(initialSidebarFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                focusedSection = ToolboxSection.FILE_MANAGER
                                onFocusRoot(internalRoot)
                            }
                        }
                        .focusProperties {
                            canFocus = !state.isFocusInContent
                            right = when {
                                state.activeSection != ToolboxSection.FILE_MANAGER ->
                                    FocusRequester.Default
                                state.canNavigateUp -> parentRowFocusRequester
                                state.items.isNotEmpty() -> firstFileFocusRequester
                                else -> FocusRequester.Default
                            }
                        },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_storage),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (isInternalActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    headlineContent = {
                        Text(
                            text = internalRoot.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            val isRunningAppsActive = state.activeSection == ToolboxSection.RUNNING_APPS
            ListItem(
                selected = isRunningAppsActive,
                onClick = onSelectRunningApps,
                modifier = Modifier
                    .focusRequester(runningAppsSidebarFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            focusedSection = ToolboxSection.RUNNING_APPS
                            onFocusRunningApps()
                        }
                    }
                    .focusProperties { canFocus = !state.isFocusInContent },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_apps),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isRunningAppsActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.running_apps_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )

            Spacer(modifier = Modifier.height(6.dp))
            val isFtpServerActive = state.activeSection == ToolboxSection.FTP_SERVER
            ListItem(
                selected = isFtpServerActive,
                onClick = onSelectFtpServer,
                modifier = Modifier
                    .focusRequester(ftpServerSidebarFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            focusedSection = ToolboxSection.FTP_SERVER
                        }
                    }
                    .focusProperties { canFocus = !state.isFocusInContent },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_ftp),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isFtpServerActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.ftp_server_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )

            if (otherRoots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.sidebar_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = otherRoots,
                        key = { _, root -> root.directory.absolutePath }
                    ) { _, root ->
                        val rootPath = root.directory.absolutePath
                        val isActive = state.activeSection == ToolboxSection.FILE_MANAGER &&
                            rootPath == state.activeRootPath
                        val rootFocusRequester = remember(rootPath) {
                            sidebarRootFocusRequesters.getOrPut(rootPath) { FocusRequester() }
                        }
                        DisposableEffect(rootPath) {
                            onDispose {
                                sidebarRootFocusRequesters.remove(rootPath)
                            }
                        }
                        ListItem(
                            selected = isActive,
                            onClick = { onSelectRoot(root) },
                            modifier = Modifier
                                .focusRequester(rootFocusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        focusedSection = ToolboxSection.FILE_MANAGER
                                        onFocusRoot(root)
                                    }
                                }
                                .focusProperties {
                                    canFocus = !state.isFocusInContent
                                    right = when {
                                        state.activeSection != ToolboxSection.FILE_MANAGER ->
                                            FocusRequester.Default
                                        state.items.isNotEmpty() -> firstFileFocusRequester
                                        state.canNavigateUp -> parentRowFocusRequester
                                        else -> FocusRequester.Default
                                    }
                                },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_storage),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = root.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            val displaySection = if (!state.isFocusInContent && focusedSection != null) {
                focusedSection!!
            } else {
                state.activeSection
            }
            val footerSummary = when (displaySection) {
                ToolboxSection.RUNNING_APPS -> runningAppsState.memorySummary
                ToolboxSection.FTP_SERVER -> ftpServerState.serverUrl
                    ?: stringResource(R.string.ftp_status_stopped)
                ToolboxSection.FILE_MANAGER -> state.storageSpaceSummary
            }
            if (footerSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = footerSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
