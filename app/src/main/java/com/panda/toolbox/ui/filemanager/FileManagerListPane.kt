package com.panda.toolbox.ui.filemanager

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.panda.toolbox.model.SortOrder
import java.io.File
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FileManagerListPane(
    state: FileManagerUiState,
    isTransitioningFolder: Boolean,
    fileListState: LazyListState,
    itemFocusRequesters: MutableMap<String, FocusRequester>,
    fileFallbackFocusRequester: FocusRequester,
    transitionFocusHoldRequester: FocusRequester,
    parentRowFocusRequester: FocusRequester,
    firstFileFocusRequester: FocusRequester,
    onStartTransition: () -> Unit,
    onPendingRestoreFocusPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onOpenSortDialog: () -> Unit,
    onRefresh: () -> Unit,
    onGrantPermission: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit
) {
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
                                        onStartTransition()
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
                                            onStartTransition()
                                        } else {
                                            onPendingRestoreFocusPath(item.path)
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

@Composable
internal fun sortOrderLabel(sortOrder: SortOrder): String {
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
