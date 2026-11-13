package com.panda.toolbox

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.panda.toolbox.data.FileRepository
import com.panda.toolbox.model.FileItem
import com.panda.toolbox.model.StorageRoot
import com.panda.toolbox.ui.FileManagerScreen
import com.panda.toolbox.ui.FileManagerUiState
import com.panda.toolbox.ui.theme.PandaTvTheme
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var repository: FileRepository
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var uiState by mutableStateOf(FileManagerUiState())
    private var currentRoot: StorageRoot? = null
    private var currentDirectory: File? = null
    private var lastKnownPermissionState: Boolean? = null

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionState()
        loadStorageRoots()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileRepository(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    uiState.deleteConfirmItems != null -> {
                        uiState = uiState.copy(deleteConfirmItems = null)
                    }
                    uiState.detailItem != null -> {
                        uiState = uiState.copy(detailItem = null)
                    }
                    uiState.selectionMode -> exitSelectionMode()
                    canNavigateUp() -> navigateUp()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        loadStorageRoots()
        updatePermissionState()

        setContent {
            PandaTvTheme {
                FileManagerScreen(
                    state = uiState,
                    onSelectRoot = { root ->
                        exitSelectionMode()
                        currentRoot = root
                        navigateTo(root.directory)
                    },
                    onNavigateUp = { navigateUp() },
                    onToggleSelectionMode = {
                        if (uiState.selectionMode) exitSelectionMode() else enterSelectionMode()
                    },
                    onToggleSelectAll = { toggleSelectAll() },
                    onRequestDeleteSelected = {
                        val selectedItems = uiState.items.filter { uiState.selectedPaths.contains(it.path) }
                        if (selectedItems.isNotEmpty()) {
                            uiState = uiState.copy(deleteConfirmItems = selectedItems)
                        }
                    },
                    onCycleSort = {
                        uiState = uiState.copy(sortOrder = uiState.sortOrder.next())
                        loadCurrentDirectory()
                    },
                    onRefresh = { loadStorageRoots() },
                    onGrantPermission = { requestStoragePermission() },
                    onItemClick = { item -> handleItemClick(item) },
                    onItemLongClick = { item -> handleItemLongClick(item) },
                    onDismissDetails = { uiState = uiState.copy(detailItem = null) },
                    onSelectFromDetails = { item ->
                        uiState = uiState.copy(
                            detailItem = null,
                            selectionMode = true,
                            selectedPaths = uiState.selectedPaths + item.path
                        )
                    },
                    onDeleteFromDetails = { item ->
                        uiState = uiState.copy(
                            detailItem = null,
                            deleteConfirmItems = listOf(item)
                        )
                    },
                    onConfirmDelete = { items -> executeDelete(items) },
                    onDismissDeleteConfirm = { uiState = uiState.copy(deleteConfirmItems = null) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val hasAccess = hasStoragePermission()
        if (lastKnownPermissionState != null && lastKnownPermissionState != hasAccess) {
            updatePermissionState()
            loadStorageRoots()
        } else {
            updatePermissionState()
        }
        lastKnownPermissionState = hasAccess
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdownNow()
    }

    private fun loadStorageRoots() {
        val roots = repository.getStorageRoots()
        val targetDir = currentDirectory?.takeIf { it.exists() && it.isDirectory }
            ?: roots.firstOrNull()?.directory
            ?: Environment.getExternalStorageDirectory()

        if (currentRoot == null || roots.none { it.directory.absolutePath == currentRoot?.directory?.absolutePath }) {
            currentRoot = roots.firstOrNull()
        }

        uiState = uiState.copy(
            storageRoots = roots,
            activeRootPath = currentRoot?.directory?.absolutePath
        )

        if (targetDir != null) {
            navigateTo(targetDir)
        }
    }

    private fun navigateTo(directory: File) {
        currentDirectory = directory
        uiState = uiState.copy(
            activeRootPath = currentRoot?.directory?.absolutePath,
            currentPath = directory.absolutePath,
            storageSpaceSummary = repository.getStorageSpaceSummary(directory) ?: "",
            canNavigateUp = canNavigateUp()
        )
        loadCurrentDirectory()
    }

    private fun canNavigateUp(): Boolean {
        val dir = currentDirectory ?: return false
        val rootPath = currentRoot?.directory?.absolutePath
        if (rootPath != null && dir.absolutePath == rootPath) {
            return false
        }
        val parent = dir.parentFile ?: return false
        return parent.exists() && parent.canRead()
    }

    private fun navigateUp() {
        if (!canNavigateUp()) return
        val parent = currentDirectory?.parentFile ?: return
        exitSelectionMode()
        navigateTo(parent)
    }

    private fun loadCurrentDirectory() {
        val dir = currentDirectory ?: return
        val order = uiState.sortOrder
        uiState = uiState.copy(isLoading = true)

        ioExecutor.execute {
            val result = repository.listDirectory(dir, order)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                val validPaths = result.items.mapTo(HashSet()) { it.path }
                uiState = uiState.copy(
                    items = result.items,
                    readable = result.readable,
                    isLoading = false,
                    canNavigateUp = canNavigateUp(),
                    selectedPaths = uiState.selectedPaths.intersect(validPaths)
                )
            }
        }
    }

    private fun handleItemClick(item: FileItem) {
        if (uiState.selectionMode) {
            toggleItemSelection(item)
            return
        }
        if (item.isDirectory) {
            navigateTo(item.file)
        } else {
            uiState = uiState.copy(detailItem = item)
        }
    }

    private fun handleItemLongClick(item: FileItem) {
        val nextSelected = if (uiState.selectedPaths.contains(item.path)) {
            uiState.selectedPaths - item.path
        } else {
            uiState.selectedPaths + item.path
        }
        uiState = uiState.copy(
            selectionMode = true,
            selectedPaths = nextSelected
        )
    }

    private fun enterSelectionMode() {
        uiState = uiState.copy(selectionMode = true)
    }

    private fun exitSelectionMode() {
        if (!uiState.selectionMode && uiState.selectedPaths.isEmpty()) return
        uiState = uiState.copy(
            selectionMode = false,
            selectedPaths = emptySet()
        )
    }

    private fun toggleItemSelection(item: FileItem) {
        val nextSelected = if (uiState.selectedPaths.contains(item.path)) {
            uiState.selectedPaths - item.path
        } else {
            uiState.selectedPaths + item.path
        }
        uiState = uiState.copy(selectedPaths = nextSelected)
    }

    private fun toggleSelectAll() {
        val allSelected = uiState.items.isNotEmpty() &&
            uiState.selectedPaths.size == uiState.items.size
        val nextSelected = if (allSelected) {
            emptySet()
        } else {
            uiState.items.mapTo(LinkedHashSet()) { it.path }
        }
        uiState = uiState.copy(selectedPaths = nextSelected)
    }

    private fun executeDelete(items: List<FileItem>) {
        val paths = items.map { it.path }
        uiState = uiState.copy(deleteConfirmItems = null, isLoading = true)

        ioExecutor.execute {
            val result = repository.deleteFiles(paths)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                val message = when {
                    result.deletedCount > 0 && result.failedPaths.isEmpty() ->
                        getString(R.string.deleted_toast, result.deletedCount)
                    result.deletedCount > 0 ->
                        getString(R.string.delete_partial_toast, result.deletedCount, result.failedPaths.size)
                    else ->
                        getString(R.string.delete_failed_toast)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                exitSelectionMode()
                currentDirectory?.let { dir ->
                    uiState = uiState.copy(
                        storageSpaceSummary = repository.getStorageSpaceSummary(dir) ?: ""
                    )
                }
                loadCurrentDirectory()
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updatePermissionState() {
        uiState = uiState.copy(hasStoragePermission = hasStoragePermission())
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: ActivityNotFoundException) {
                    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(detailsIntent)
                }
            }
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}
