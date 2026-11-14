package com.panda.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.window.Dialog
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
import com.panda.toolbox.data.FileRepository
import com.panda.toolbox.model.FileItem
import com.panda.toolbox.model.FileType
import com.panda.toolbox.model.RunningAppItem
import com.panda.toolbox.model.SortOrder
import com.panda.toolbox.model.StorageRoot
import java.io.File
import java.util.Locale

enum class ToolboxSection {
    FILE_MANAGER,
    RUNNING_APPS,
    FTP_SERVER
}

data class FileManagerUiState(
    val activeSection: ToolboxSection = ToolboxSection.FILE_MANAGER,
    val storageRoots: List<StorageRoot> = emptyList(),
    val activeRootPath: String? = null,
    val currentPath: String = "",
    val storageSpaceSummary: String = "",
    val items: List<FileItem> = emptyList(),
    val readable: Boolean = true,
    val isLoading: Boolean = false,
    val canNavigateUp: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val hasStoragePermission: Boolean = true,
    val detailItem: FileItem? = null,
    val deleteConfirmItems: List<FileItem>? = null,
    val viewingImageItem: FileItem? = null,
    val viewingVideoItem: FileItem? = null,
    val showSortDialog: Boolean = false,
    val showQuitDialog: Boolean = false,
    val isFocusInContent: Boolean = false,
    val focusSidebarTick: Int = 0,
    val focusContentTick: Int = 0
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FileManagerScreen(
    state: FileManagerUiState,
    runningAppsState: RunningAppsUiState,
    ftpServerState: FtpServerUiState,
    onSelectRunningApps: () -> Unit,
    onSelectFtpServer: () -> Unit,
    onToggleFtpServer: () -> Unit,
    onSelectRoot: (StorageRoot) -> Unit,
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
    onToggleActiveOnly: () -> Unit,
    onRefreshApps: () -> Unit,
    onAppClick: (RunningAppItem) -> Unit,
    onAppLongClick: (RunningAppItem) -> Unit,
    onDismissAppDetails: () -> Unit,
    onOpenAppInfo: (RunningAppItem) -> Unit,
    onContentFocusChanged: (Boolean) -> Unit
) {
    val itemFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val sidebarRootFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val fileListState = rememberLazyListState()
    val firstFileFocusRequester = remember { FocusRequester() }
    val parentRowFocusRequester = remember { FocusRequester() }
    val transitionFocusHoldRequester = remember { FocusRequester() }
    val fileFallbackFocusRequester = remember { FocusRequester() }
    val runningAppsSidebarFocusRequester = remember { FocusRequester() }
    val ftpServerSidebarFocusRequester = remember { FocusRequester() }
    val runningAppsContentFocusRequester = remember { FocusRequester() }
    val ftpServerContentFocusRequester = remember { FocusRequester() }
    val initialSidebarFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    var pendingRestoreFocusPath by remember { mutableStateOf<String?>(null) }
    var lastLoadedPath by remember { mutableStateOf<String?>(null) }
    var isTransitioningFolder by remember { mutableStateOf(false) }

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

    LaunchedEffect(state.focusContentTick, state.activeSection, state.isLoading) {
        if (state.focusContentTick > 0) {
            when (state.activeSection) {
                ToolboxSection.RUNNING_APPS -> {
                    runCatching { runningAppsContentFocusRequester.requestFocus() }
                }
                ToolboxSection.FTP_SERVER -> {
                    runCatching { ftpServerContentFocusRequester.requestFocus() }
                }
                ToolboxSection.FILE_MANAGER -> {
                    if (!state.isLoading) {
                        lastLoadedPath = state.currentPath
                        if (state.canNavigateUp || state.items.isNotEmpty()) {
                            runCatching { fileListState.scrollToItem(0) }
                            withFrameNanos { }
                        }
                        when {
                            state.canNavigateUp -> {
                                runCatching { parentRowFocusRequester.requestFocus() }
                            }
                            state.items.isNotEmpty() -> {
                                runCatching { firstFileFocusRequester.requestFocus() }
                            }
                            else -> {
                                runCatching { fileFallbackFocusRequester.requestFocus() }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(state.currentPath, state.isLoading) {
        if (state.isLoading) {
            if (lastLoadedPath != null) {
                isTransitioningFolder = true
                runCatching { transitionFocusHoldRequester.requestFocus() }
            }
            return@LaunchedEffect
        }
        if (state.currentPath.isNotEmpty()) {
            val isInitialLoad = lastLoadedPath == null
            val pathChanged = lastLoadedPath != state.currentPath
            lastLoadedPath = state.currentPath
            if (!isInitialLoad && pathChanged && state.activeSection == ToolboxSection.FILE_MANAGER) {
                isTransitioningFolder = true
                if (state.canNavigateUp || state.items.isNotEmpty()) {
                    runCatching { fileListState.scrollToItem(0) }
                    withFrameNanos { }
                }
                if (state.canNavigateUp) {
                    runCatching { parentRowFocusRequester.requestFocus() }
                } else if (state.items.isNotEmpty()) {
                    runCatching { firstFileFocusRequester.requestFocus() }
                }
            }
        }
        isTransitioningFolder = false
    }

    LaunchedEffect(state.viewingImageItem, state.viewingVideoItem, state.detailItem) {
        if (state.viewingImageItem == null &&
            state.viewingVideoItem == null &&
            state.detailItem == null
        ) {
            val targetPath = pendingRestoreFocusPath
            if (targetPath != null) {
                pendingRestoreFocusPath = null
                runCatching { itemFocusRequesters[targetPath]?.requestFocus() }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Left Sidebar: Storage Roots
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

                val footerSummary = when (state.activeSection) {
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

        Spacer(modifier = Modifier.width(20.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape) &&
                        state.activeSection == ToolboxSection.FILE_MANAGER &&
                        state.canNavigateUp &&
                        !state.selectionMode &&
                        state.detailItem == null &&
                        state.deleteConfirmItems == null &&
                        !state.showSortDialog
                    ) {
                        isTransitioningFolder = true
                        runCatching { transitionFocusHoldRequester.requestFocus() }
                    }
                    false
                }
                .onFocusChanged { onContentFocusChanged(it.hasFocus) }
        ) {
            if (state.activeSection == ToolboxSection.RUNNING_APPS) {
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
            } else if (state.activeSection == ToolboxSection.FTP_SERVER) {
                FtpServerPane(
                    state = ftpServerState,
                    hasStoragePermission = state.hasStoragePermission,
                    modifier = Modifier.fillMaxSize(),
                    initialFocusRequester = ftpServerContentFocusRequester,
                    onToggleServer = onToggleFtpServer,
                    onGrantPermission = onGrantPermission
                )
            } else {
                // Right Main Pane: File Browser
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        val actionBarCanFocus = !state.isLoading && !isTransitioningFolder
                        OutlinedButton(
                            onClick = onToggleSelectionMode,
                            modifier = Modifier
                                .focusRequester(fileFallbackFocusRequester)
                                .focusProperties { canFocus = actionBarCanFocus }
                        ) {
                    Text(
                        text = stringResource(
                            if (state.selectionMode) R.string.cancel_select else R.string.select_mode
                        )
                    )
                }

                if (state.selectionMode) {
                    val allSelected = state.items.isNotEmpty() &&
                        state.selectedPaths.size == state.items.size
                    OutlinedButton(onClick = onToggleSelectAll) {
                        Text(
                            text = stringResource(
                                if (allSelected) R.string.clear_selection else R.string.select_all
                            )
                        )
                    }
                }

                if (state.selectedPaths.isNotEmpty()) {
                    Button(
                        onClick = onRequestDeleteSelected,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            focusedContainerColor = Color(0xFFDC2626),
                            focusedContentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.delete_selected, state.selectedPaths.size)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenSortDialog,
                    modifier = Modifier.focusProperties { canFocus = actionBarCanFocus }
                ) {
                    Text(text = stringResource(R.string.sort_button, sortOrderLabel(state.sortOrder)))
                }

                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.focusProperties { canFocus = actionBarCanFocus }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.refresh_button),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current Path
            Text(
                text = state.currentPath,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis
            )

            // Storage Permission Banner
            if (!state.hasStoragePermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = Color(0xFF3B2314)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.permission_banner_text),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Button(onClick = onGrantPermission) {
                            Text(text = stringResource(R.string.grant_access))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Folder Summary
            Text(
                text = folderSummaryText(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // File List / Empty / Loading
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(transitionFocusHoldRequester)
                    .focusProperties { canFocus = state.isLoading || isTransitioningFolder }
                    .focusable()
            ) {
                when {
                    state.isLoading && state.items.isEmpty() && !state.canNavigateUp -> {
                        Text(
                            text = stringResource(R.string.loading_text),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    !state.readable -> {
                        Text(
                            text = stringResource(R.string.cannot_read_folder),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.items.isEmpty() && !state.canNavigateUp -> {
                        Text(
                            text = stringResource(R.string.empty_folder),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        val parentPath = formatParentPath(
                            currentPath = state.currentPath,
                            activeRootPath = state.activeRootPath
                        )
                        LazyColumn(
                            state = fileListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.canNavigateUp) {
                                item(key = "__parent_folder__") {
                                    ParentFolderRow(
                                        parentPath = parentPath,
                                        modifier = Modifier.focusRequester(parentRowFocusRequester),
                                        onClick = {
                                            isTransitioningFolder = true
                                            runCatching { transitionFocusHoldRequester.requestFocus() }
                                            onNavigateUp()
                                        }
                                    )
                                }
                            }
                            itemsIndexed(
                                items = state.items,
                                key = { _, item -> item.path }
                            ) { index, item ->
                                val isSelected = state.selectedPaths.contains(item.path)
                                val focusRequester = remember(item.path) {
                                    itemFocusRequesters.getOrPut(item.path) { FocusRequester() }
                                }
                                DisposableEffect(item.path) {
                                    onDispose {
                                        itemFocusRequesters.remove(item.path)
                                    }
                                }
                                val rowModifier = Modifier
                                    .focusRequester(focusRequester)
                                    .let {
                                        if (index == 0) it.focusRequester(firstFileFocusRequester) else it
                                    }
                                FileRow(
                                    item = item,
                                    selectionMode = state.selectionMode,
                                    isSelected = isSelected,
                                    modifier = rowModifier,
                                    onClick = {
                                        if (!state.selectionMode) {
                                            if (item.isDirectory) {
                                                isTransitioningFolder = true
                                                runCatching { transitionFocusHoldRequester.requestFocus() }
                                            } else {
                                                pendingRestoreFocusPath = item.path
                                            }
                                        }
                                        onItemClick(item)
                                    },
                                    onLongClick = { onItemLongClick(item) }
                                )
                            }
                            if (state.items.isEmpty()) {
                                item(key = "__empty_folder__") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.empty_folder),
                                            modifier = Modifier.align(Alignment.Center),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        }
    }

    // File Details Dialog
    state.detailItem?.let { item ->
        FileDetailsDialog(
            item = item,
            onDismiss = onDismissDetails
        )
    }

    // Delete Confirmation Dialog
    state.deleteConfirmItems?.let { itemsToDelete ->
        DeleteConfirmDialog(
            items = itemsToDelete,
            onConfirm = { onConfirmDelete(itemsToDelete) },
            onDismiss = onDismissDeleteConfirm
        )
    }

    // Sort Options Dialog
    if (state.showSortDialog) {
        SortOptionsDialog(
            currentOrder = state.sortOrder,
            onSelectOrder = onSelectSortOrder,
            onDismiss = onDismissSortDialog
        )
    }

    // Quit Confirm Dialog
    if (state.showQuitDialog) {
        QuitConfirmDialog(
            onConfirm = onConfirmQuit,
            onDismiss = onDismissQuitDialog
        )
    }

    // In-App Image Viewer
    state.viewingImageItem?.let { item ->
        ImageViewerOverlay(
            item = item,
            onClose = onDismissImageViewer
        )
    }

    // In-App Video Player
    state.viewingVideoItem?.let { item ->
        VideoViewerOverlay(
            item = item,
            onClose = onDismissVideoViewer
        )
    }
}

private fun formatParentPath(currentPath: String, activeRootPath: String?): String {
    val rawParentPath = File(currentPath).parent ?: return ".."
    if (activeRootPath.isNullOrEmpty()) return rawParentPath
    val normalizedRoot = activeRootPath.trimEnd('/')
    return when {
        rawParentPath == normalizedRoot -> "/"
        rawParentPath.startsWith("$normalizedRoot/") -> rawParentPath.removePrefix(normalizedRoot)
        else -> rawParentPath
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ParentFolderRow(
    parentPath: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_up),
                contentDescription = stringResource(R.string.up_button),
                modifier = Modifier.size(30.dp),
                tint = Color(0xFFFBBF24)
            )
        },
        headlineContent = {
            Text(
                text = parentPath,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FileRow(
    item: FileItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val iconRes = when (item.type) {
        FileType.FOLDER -> R.drawable.ic_folder
        FileType.VIDEO -> R.drawable.ic_file_video
        FileType.AUDIO -> R.drawable.ic_file_audio
        FileType.IMAGE -> R.drawable.ic_file_image
        FileType.OTHER -> R.drawable.ic_file_generic
    }

    val iconTint = when (item.type) {
        FileType.FOLDER -> Color(0xFFFBBF24)
        FileType.VIDEO -> Color(0xFF60A5FA)
        FileType.AUDIO -> Color(0xFFA78BFA)
        FileType.IMAGE -> Color(0xFF34D399)
        FileType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val dateStr = FileRepository.formatDate(item.lastModified)
    val metaText = if (item.isDirectory) {
        val countLabel = if (item.childCount == 1) {
            stringResource(R.string.one_item)
        } else {
            stringResource(R.string.items_count, item.childCount)
        }
        if (dateStr.isNotEmpty()) "$countLabel • $dateStr" else countLabel
    } else {
        val sizeStr = FileRepository.formatSize(item.sizeBytes)
        if (dateStr.isNotEmpty()) "$sizeStr • $dateStr" else sizeStr
    }

    val badgeText = if (item.isDirectory) {
        stringResource(R.string.folder_label).uppercase(Locale.ROOT)
    } else {
        item.extension.uppercase(Locale.ROOT)
    }

    ListItem(
        selected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
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
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = iconTint
                )
            }
        },
        headlineContent = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = metaText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (badgeText.isNotEmpty()) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FileDetailsDialog(
    item: FileItem,
    onDismiss: () -> Unit
) {
    val sizeStr = FileRepository.formatSize(item.sizeBytes)
    val dateStr = FileRepository.formatDate(item.lastModified)

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
                    text = stringResource(R.string.file_details_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Path: ${item.path}\nSize: $sizeStr" +
                        if (dateStr.isNotEmpty()) "\nModified: $dateStr" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text(text = stringResource(R.string.close_action))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DeleteConfirmDialog(
    items: List<FileItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (items.size == 1) {
        stringResource(R.string.delete_confirm_title, items.first().name)
    } else {
        stringResource(R.string.delete_confirm_title, "${items.size} items")
    }

    val message = if (items.size == 1) {
        val single = items.first()
        val sizeStr = if (single.isDirectory) {
            stringResource(R.string.folder_label)
        } else {
            FileRepository.formatSize(single.sizeBytes)
        }
        stringResource(R.string.delete_confirm_single, single.name, sizeStr)
    } else {
        val totalBytes = items.filter { !it.isDirectory }.sumOf { it.sizeBytes }
        stringResource(
            R.string.delete_confirm_multi,
            items.size,
            FileRepository.formatSize(totalBytes)
        )
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
                        Text(text = stringResource(R.string.delete_action))
                    }
                }
            }
        }
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SortOptionsDialog(
    currentOrder: SortOrder,
    onSelectOrder: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SortOrder.NAME_ASC,
        SortOrder.NAME_DESC,
        SortOrder.DATE_ASC,
        SortOrder.DATE_DESC,
        SortOrder.SIZE_DESC
    )

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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.sort_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                options.forEach { order ->
                    val isSelected = order == currentOrder
                    ListItem(
                        selected = isSelected,
                        onClick = { onSelectOrder(order) },
                        headlineContent = {
                            Text(
                                text = sortOrderLabel(order),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingContent = {
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.close_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun sortOrderLabel(sortOrder: SortOrder): String {
    return when (sortOrder) {
        SortOrder.NAME_ASC -> stringResource(R.string.sort_name_asc)
        SortOrder.NAME_DESC -> stringResource(R.string.sort_name_desc)
        SortOrder.DATE_ASC -> stringResource(R.string.sort_date_asc)
        SortOrder.DATE_DESC -> stringResource(R.string.sort_date_desc)
        SortOrder.SIZE_DESC -> stringResource(R.string.sort_size_desc)
    }
}

@Composable
private fun folderSummaryText(state: FileManagerUiState): String {
    if (!state.readable) return stringResource(R.string.cannot_read_folder)
    val countText = if (state.items.size == 1) {
        stringResource(R.string.one_item)
    } else {
        stringResource(R.string.items_count, state.items.size)
    }
    return if (state.selectionMode) {
        "$countText • ${state.selectedPaths.size} selected"
    } else {
        "$countText • Long-press OK on any item to select"
    }
}
