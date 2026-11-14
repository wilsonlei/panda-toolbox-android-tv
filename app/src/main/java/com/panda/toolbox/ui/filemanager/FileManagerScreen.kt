package com.panda.toolbox.ui.filemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.panda.toolbox.model.FileItem
import com.panda.toolbox.model.SortOrder
import com.panda.toolbox.model.StorageRoot
import com.panda.toolbox.ui.ToolboxSection

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
    val viewingAudioItem: FileItem? = null,
    val showSortDialog: Boolean = false,
    val showQuitDialog: Boolean = false,
    val isFocusInContent: Boolean = false,
    val focusSidebarTick: Int = 0,
    val focusContentTick: Int = 0
)

@Composable
fun FileManagerPane(
    state: FileManagerUiState,
    parentRowFocusRequester: FocusRequester,
    firstFileFocusRequester: FocusRequester,
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
    onDismissImageViewer: () -> Unit,
    onDismissVideoViewer: () -> Unit,
    onDismissAudioViewer: () -> Unit
) {
    val itemFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val fileListState = rememberLazyListState()
    val transitionFocusHoldRequester = remember { FocusRequester() }
    val fileFallbackFocusRequester = remember { FocusRequester() }
    var pendingRestoreFocusPath by remember { mutableStateOf<String?>(null) }
    var lastLoadedPath by remember { mutableStateOf<String?>(null) }
    var isTransitioningFolder by remember { mutableStateOf(false) }

    LaunchedEffect(state.focusContentTick, state.isLoading) {
        if (state.focusContentTick > 0 && !state.isLoading) {
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
            if (!isInitialLoad && pathChanged) {
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

    LaunchedEffect(
        state.viewingImageItem,
        state.viewingVideoItem,
        state.viewingAudioItem,
        state.detailItem
    ) {
        if (state.viewingImageItem == null &&
            state.viewingVideoItem == null &&
            state.viewingAudioItem == null &&
            state.detailItem == null
        ) {
            val targetPath = pendingRestoreFocusPath
            if (targetPath != null) {
                pendingRestoreFocusPath = null
                runCatching { itemFocusRequesters[targetPath]?.requestFocus() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape) &&
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
    ) {
        FileManagerListPane(
            state = state,
            isTransitioningFolder = isTransitioningFolder,
            fileListState = fileListState,
            itemFocusRequesters = itemFocusRequesters,
            fileFallbackFocusRequester = fileFallbackFocusRequester,
            transitionFocusHoldRequester = transitionFocusHoldRequester,
            parentRowFocusRequester = parentRowFocusRequester,
            firstFileFocusRequester = firstFileFocusRequester,
            onStartTransition = {
                isTransitioningFolder = true
                runCatching { transitionFocusHoldRequester.requestFocus() }
            },
            onPendingRestoreFocusPath = { path -> pendingRestoreFocusPath = path },
            onNavigateUp = onNavigateUp,
            onToggleSelectionMode = onToggleSelectionMode,
            onToggleSelectAll = onToggleSelectAll,
            onRequestDeleteSelected = onRequestDeleteSelected,
            onOpenSortDialog = onOpenSortDialog,
            onRefresh = onRefresh,
            onGrantPermission = onGrantPermission,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick
        )
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

    // In-App Audio Player
    state.viewingAudioItem?.let { item ->
        AudioViewerOverlay(
            item = item,
            onClose = onDismissAudioViewer
        )
    }
}
