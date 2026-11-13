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
import com.panda.toolbox.data.RunningAppsRepository
import com.panda.toolbox.model.FileItem
import com.panda.toolbox.model.RunningAppItem
import com.panda.toolbox.model.StorageRoot
import com.panda.toolbox.ui.FileManagerScreen
import com.panda.toolbox.ui.FileManagerUiState
import com.panda.toolbox.ui.RunningAppsUiState
import com.panda.toolbox.ui.ToolboxSection
import com.panda.toolbox.ui.theme.PandaTvTheme
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var repository: FileRepository
    private lateinit var runningAppsRepository: RunningAppsRepository
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var uiState by mutableStateOf(FileManagerUiState())
    private var runningAppsState by mutableStateOf(RunningAppsUiState())
    private val terminatedTimestamps = HashMap<String, Long>()
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
        runningAppsRepository = RunningAppsRepository(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState.activeSection == ToolboxSection.RUNNING_APPS) {
                    when {
                        runningAppsState.terminateConfirmApps != null -> {
                            runningAppsState = runningAppsState.copy(terminateConfirmApps = null)
                        }
                        runningAppsState.detailApp != null -> {
                            runningAppsState = runningAppsState.copy(detailApp = null)
                        }
                        runningAppsState.selectionMode -> exitAppsSelectionMode()
                        else -> {
                            uiState = uiState.copy(activeSection = ToolboxSection.FILE_MANAGER)
                        }
                    }
                    return
                }

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
                    runningAppsState = runningAppsState,
                    onSelectRunningApps = {
                        exitSelectionMode()
                        uiState = uiState.copy(activeSection = ToolboxSection.RUNNING_APPS)
                        loadRunningApps()
                    },
                    onSelectRoot = { root ->
                        exitSelectionMode()
                        uiState = uiState.copy(activeSection = ToolboxSection.FILE_MANAGER)
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
                    onDismissDeleteConfirm = { uiState = uiState.copy(deleteConfirmItems = null) },
                    onToggleAppsSelectionMode = {
                        if (runningAppsState.selectionMode) exitAppsSelectionMode() else enterAppsSelectionMode()
                    },
                    onToggleAppsSelectAll = { toggleAppsSelectAll() },
                    onRequestTerminateSelected = {
                        val selectedApps = runningAppsState.apps.filter {
                            runningAppsState.selectedPackages.contains(it.packageName)
                        }
                        if (selectedApps.isNotEmpty()) {
                            runningAppsState = runningAppsState.copy(terminateConfirmApps = selectedApps)
                        }
                    },
                    onToggleSystemApps = {
                        runningAppsState = runningAppsState.copy(
                            showSystemApps = !runningAppsState.showSystemApps
                        )
                        loadRunningApps()
                    },
                    onRefreshApps = { loadRunningApps() },
                    onAppClick = { app -> handleAppClick(app) },
                    onAppLongClick = { app -> handleAppLongClick(app) },
                    onDismissAppDetails = { runningAppsState = runningAppsState.copy(detailApp = null) },
                    onSelectAppFromDetails = { app ->
                        runningAppsState = runningAppsState.copy(
                            detailApp = null,
                            selectionMode = true,
                            selectedPackages = runningAppsState.selectedPackages + app.packageName
                        )
                    },
                    onOpenAppInfo = { app ->
                        runningAppsState = runningAppsState.copy(detailApp = null)
                        openAppInfoSettings(app.packageName)
                    },
                    onTerminateAppFromDetails = { app ->
                        runningAppsState = runningAppsState.copy(
                            detailApp = null,
                            terminateConfirmApps = listOf(app)
                        )
                    },
                    onConfirmTerminateApps = { apps -> executeTerminateApps(apps) },
                    onDismissTerminateConfirm = {
                        runningAppsState = runningAppsState.copy(terminateConfirmApps = null)
                    }
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
        if (uiState.activeSection == ToolboxSection.RUNNING_APPS) {
            loadRunningApps()
        }
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

    private fun openAppInfoSettings(targetPackage: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$targetPackage")
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.app_info_action), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRunningApps() {
        val includeSystem = runningAppsState.showSystemApps
        val terminatedSnapshot = HashMap(terminatedTimestamps)
        runningAppsState = runningAppsState.copy(isLoading = true)

        ioExecutor.execute {
            val apps = runningAppsRepository.getRunningApps(
                includeSystemApps = includeSystem,
                terminatedTimestamps = terminatedSnapshot
            )
            val memSummary = runningAppsRepository.getMemorySummary()
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                val validPackages = apps.mapTo(HashSet()) { it.packageName }
                // Clean up terminatedTimestamps for apps that actually restarted/re-launched
                for (pkg in validPackages) {
                    terminatedTimestamps.remove(pkg)
                }
                runningAppsState = runningAppsState.copy(
                    apps = apps,
                    isLoading = false,
                    memorySummary = memSummary,
                    selectedPackages = runningAppsState.selectedPackages.intersect(validPackages)
                )
            }
        }
    }

    private fun handleAppClick(app: RunningAppItem) {
        if (runningAppsState.selectionMode) {
            toggleAppSelection(app)
            return
        }
        runningAppsState = runningAppsState.copy(detailApp = app)
    }

    private fun handleAppLongClick(app: RunningAppItem) {
        val nextSelected = if (runningAppsState.selectedPackages.contains(app.packageName)) {
            runningAppsState.selectedPackages - app.packageName
        } else {
            runningAppsState.selectedPackages + app.packageName
        }
        runningAppsState = runningAppsState.copy(
            selectionMode = true,
            selectedPackages = nextSelected
        )
    }

    private fun enterAppsSelectionMode() {
        runningAppsState = runningAppsState.copy(selectionMode = true)
    }

    private fun exitAppsSelectionMode() {
        if (!runningAppsState.selectionMode && runningAppsState.selectedPackages.isEmpty()) return
        runningAppsState = runningAppsState.copy(
            selectionMode = false,
            selectedPackages = emptySet()
        )
    }

    private fun toggleAppSelection(app: RunningAppItem) {
        val nextSelected = if (runningAppsState.selectedPackages.contains(app.packageName)) {
            runningAppsState.selectedPackages - app.packageName
        } else {
            runningAppsState.selectedPackages + app.packageName
        }
        runningAppsState = runningAppsState.copy(selectedPackages = nextSelected)
    }

    private fun toggleAppsSelectAll() {
        val allSelected = runningAppsState.apps.isNotEmpty() &&
            runningAppsState.selectedPackages.size == runningAppsState.apps.size
        val nextSelected = if (allSelected) {
            emptySet()
        } else {
            runningAppsState.apps.mapTo(LinkedHashSet()) { it.packageName }
        }
        runningAppsState = runningAppsState.copy(selectedPackages = nextSelected)
    }

    private fun executeTerminateApps(apps: List<RunningAppItem>) {
        val packages = apps.map { it.packageName }
        runningAppsState = runningAppsState.copy(terminateConfirmApps = null, isLoading = true)

        ioExecutor.execute {
            val result = runningAppsRepository.terminateApps(packages)
            val failedSet = result.failedPackages.toSet()
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                val now = System.currentTimeMillis()
                for (pkg in packages) {
                    if (!failedSet.contains(pkg)) {
                        terminatedTimestamps[pkg] = now
                    }
                }
                val message = when {
                    result.terminatedCount > 0 && result.failedPackages.isEmpty() ->
                        getString(R.string.terminated_toast, result.terminatedCount)
                    result.terminatedCount > 0 ->
                        getString(
                            R.string.terminate_partial_toast,
                            result.terminatedCount,
                            result.failedPackages.size
                        )
                    else ->
                        getString(R.string.terminate_failed_toast)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                exitAppsSelectionMode()
                loadRunningApps()
            }
        }
    }
}
