package com.panda.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.panda.toolbox.R
import com.panda.toolbox.model.FileItem
import com.panda.toolbox.model.RunningAppItem
import com.panda.toolbox.model.SortOrder
import com.panda.toolbox.model.StorageRoot
import com.panda.toolbox.ui.filemanager.FileManagerPane
import com.panda.toolbox.ui.filemanager.FileManagerUiState

enum class ToolboxSection {
    FILE_MANAGER,
    RUNNING_APPS,
    FTP_SERVER
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ToolboxScreen(
    state: FileManagerUiState,
    runningAppsState: RunningAppsUiState,
    ftpServerState: FtpServerUiState,
    onSelectRunningApps: () -> Unit,
    onSelectFtpServer: () -> Unit,
    onToggleFtpServer: () -> Unit,
    onSelectRoot: (StorageRoot) -> Unit,
    onFocusRoot: (StorageRoot) -> Unit,
    onFocusRunningApps: () -> Unit,
    onNavigateUp: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onOpenSortDialog: () -> Unit,
    onSelectSortOrder: (SortOrder) -> Unit,
    onDismissSortDialog: () -> Unit,
    onRefresh: () -> Unit,
    onGrantPermission: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onDismissDetails: () -> Unit,
    onConfirmDelete: (List<FileItem>) -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onConfirmQuit: () -> Unit,
    onDismissQuitDialog: () -> Unit,
    onDismissImageViewer: () -> Unit,
    onDismissVideoViewer: () -> Unit,
    onDismissAudioViewer: () -> Unit,
    onToggleActiveOnly: () -> Unit,
    onRefreshApps: () -> Unit,
    onAppClick: (RunningAppItem) -> Unit,
    onAppLongClick: (RunningAppItem) -> Unit,
    onDismissAppDetails: () -> Unit,
    onOpenAppInfo: (RunningAppItem) -> Unit,
    onContentFocusChanged: (Boolean) -> Unit
) {
    val sidebarRootFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val firstFileFocusRequester = remember { FocusRequester() }
    val parentRowFocusRequester = remember { FocusRequester() }
    val runningAppsSidebarFocusRequester = remember { FocusRequester() }
    val ftpServerSidebarFocusRequester = remember { FocusRequester() }
    val runningAppsContentFocusRequester = remember { FocusRequester() }
    val ftpServerContentFocusRequester = remember { FocusRequester() }
    val initialSidebarFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(state.storageRoots) {
        if (!hasRequestedInitialFocus && state.storageRoots.isNotEmpty()) {
            hasRequestedInitialFocus = true
            runCatching { initialSidebarFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(state.focusSidebarTick) {
        if (state.focusSidebarTick > 0) {
            val targetRequester = when (state.activeSection) {
                ToolboxSection.RUNNING_APPS -> runningAppsSidebarFocusRequester
                ToolboxSection.FTP_SERVER -> ftpServerSidebarFocusRequester
                ToolboxSection.FILE_MANAGER -> {
                    state.activeRootPath?.let { sidebarRootFocusRequesters[it] }
                        ?: initialSidebarFocusRequester
                }
            }
            runCatching { targetRequester.requestFocus() }
        }
    }

    LaunchedEffect(state.focusContentTick, state.activeSection) {
        if (state.focusContentTick > 0) {
            when (state.activeSection) {
                ToolboxSection.RUNNING_APPS -> {
                    runCatching { runningAppsContentFocusRequester.requestFocus() }
                }
                ToolboxSection.FTP_SERVER -> {
                    runCatching { ftpServerContentFocusRequester.requestFocus() }
                }
                ToolboxSection.FILE_MANAGER -> Unit
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        MainMenuSidebar(
            state = state,
            runningAppsState = runningAppsState,
            ftpServerState = ftpServerState,
            sidebarRootFocusRequesters = sidebarRootFocusRequesters,
            initialSidebarFocusRequester = initialSidebarFocusRequester,
            runningAppsSidebarFocusRequester = runningAppsSidebarFocusRequester,
            ftpServerSidebarFocusRequester = ftpServerSidebarFocusRequester,
            parentRowFocusRequester = parentRowFocusRequester,
            firstFileFocusRequester = firstFileFocusRequester,
            onSelectRoot = onSelectRoot,
            onSelectRunningApps = onSelectRunningApps,
            onSelectFtpServer = onSelectFtpServer,
            onFocusRoot = onFocusRoot,
            onFocusRunningApps = onFocusRunningApps
        )

        Spacer(modifier = Modifier.width(20.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged { onContentFocusChanged(it.hasFocus) }
        ) {
            when (state.activeSection) {
                ToolboxSection.RUNNING_APPS -> {
                    RunningAppsPane(
                        state = runningAppsState,
                        modifier = Modifier.fillMaxSize(),
                        initialFocusRequester = runningAppsContentFocusRequester,
                        onToggleActiveOnly = onToggleActiveOnly,
                        onRefresh = onRefreshApps,
                        onAppClick = onAppClick,
                        onAppLongClick = onAppLongClick,
                        onDismissDetails = onDismissAppDetails,
                        onOpenAppInfo = onOpenAppInfo
                    )
                }
                ToolboxSection.FTP_SERVER -> {
                    FtpServerPane(
                        state = ftpServerState,
                        hasStoragePermission = state.hasStoragePermission,
                        modifier = Modifier.fillMaxSize(),
                        initialFocusRequester = ftpServerContentFocusRequester,
                        onToggleServer = onToggleFtpServer,
                        onGrantPermission = onGrantPermission
                    )
                }
                ToolboxSection.FILE_MANAGER -> {
                    FileManagerPane(
                        state = state,
                        parentRowFocusRequester = parentRowFocusRequester,
                        firstFileFocusRequester = firstFileFocusRequester,
                        onNavigateUp = onNavigateUp,
                        onToggleSelectionMode = onToggleSelectionMode,
                        onToggleSelectAll = onToggleSelectAll,
                        onRequestDeleteSelected = onRequestDeleteSelected,
                        onOpenSortDialog = onOpenSortDialog,
                        onSelectSortOrder = onSelectSortOrder,
                        onDismissSortDialog = onDismissSortDialog,
                        onRefresh = onRefresh,
                        onGrantPermission = onGrantPermission,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onDismissDetails = onDismissDetails,
                        onConfirmDelete = onConfirmDelete,
                        onDismissDeleteConfirm = onDismissDeleteConfirm,
                        onDismissImageViewer = onDismissImageViewer,
                        onDismissVideoViewer = onDismissVideoViewer,
                        onDismissAudioViewer = onDismissAudioViewer
                    )
                }
            }
        }
    }

    if (state.showQuitDialog) {
        QuitConfirmDialog(
            onConfirm = onConfirmQuit,
            onDismiss = onDismissQuitDialog
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.quit_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.quit_confirm_message),
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
                        Text(text = stringResource(R.string.quit_action))
                    }
                }
            }
        }
    }
}
