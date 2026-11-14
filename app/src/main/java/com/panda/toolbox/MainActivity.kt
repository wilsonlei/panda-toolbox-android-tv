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
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.panda.toolbox.data.FileRepository
import com.panda.toolbox.data.RunningAppsRepository
import com.panda.toolbox.data.ftp.LocalFtpServer
import com.panda.toolbox.model.FileItem
import com.panda.toolbox.model.FileType
import com.panda.toolbox.model.RunningAppItem
import com.panda.toolbox.model.SortOrder
import com.panda.toolbox.model.StorageRoot
import com.panda.toolbox.ui.FtpServerUiState
import com.panda.toolbox.ui.RunningAppsUiState
import com.panda.toolbox.ui.ToolboxScreen
import com.panda.toolbox.ui.ToolboxSection
import com.panda.toolbox.ui.filemanager.FileManagerUiState
import com.panda.toolbox.ui.theme.PandaTvTheme
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var repository: FileRepository
    private lateinit var runningAppsRepository: RunningAppsRepository
    private lateinit var ftpServer: LocalFtpServer
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var uiState by mutableStateOf(FileManagerUiState())
    private var runningAppsState by mutableStateOf(RunningAppsUiState())
    private var ftpServerState by mutableStateOf(FtpServerUiState())
    private var currentRoot: StorageRoot? = null
    private var currentDirectory: File? = null
    private var lastKnownPermissionState: Boolean? = null
    private var pendingApkInstall: FileItem? = null
    private var longPressBackHandled = false

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
        ftpServer = LocalFtpServer(
            onStateChanged = { isRunning, url, activeConnections ->
                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post
                    ftpServerState = ftpServerState.copy(
                        isRunning = isRunning,
                        serverUrl = url,
                        activeConnections = activeConnections
                    )
                }
            },
            onLog = { message ->
                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post
                    val updatedLogs = (listOf(message) + ftpServerState.activityLogs).take(50)
                    ftpServerState = ftpServerState.copy(activityLogs = updatedLogs)
                }
            }
        )
        uiState = uiState.copy(sortOrder = loadSavedSortOrder())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState.showQuitDialog) {
                    uiState = uiState.copy(showQuitDialog = false)
                    return
                }
                if (uiState.activeSection == ToolboxSection.RUNNING_APPS) {
                    when {
                        runningAppsState.detailApp != null -> {
                            runningAppsState = runningAppsState.copy(detailApp = null)
                        }
                        uiState.isFocusInContent -> {
                            uiState = uiState.copy(
                                isFocusInContent = false,
                                focusSidebarTick = uiState.focusSidebarTick + 1
                            )
                        }
                        else -> {
                            uiState = uiState.copy(showQuitDialog = true)
                        }
                    }
                    return
                }
                if (uiState.activeSection == ToolboxSection.FTP_SERVER) {
                    if (uiState.isFocusInContent) {
                        uiState = uiState.copy(
                            isFocusInContent = false,
                            focusSidebarTick = uiState.focusSidebarTick + 1
                        )
                    } else {
                        uiState = uiState.copy(showQuitDialog = true)
                    }
                    return
                }

                when {
                    uiState.viewingVideoItem != null -> {
                        uiState = uiState.copy(viewingVideoItem = null)
                    }
                    uiState.viewingImageItem != null -> {
                        uiState = uiState.copy(viewingImageItem = null)
                    }
                    uiState.showSortDialog -> {
                        uiState = uiState.copy(showSortDialog = false)
                    }
                    uiState.deleteConfirmItems != null -> {
                        uiState = uiState.copy(deleteConfirmItems = null)
                    }
                    uiState.detailItem != null -> {
                        uiState = uiState.copy(detailItem = null)
                    }
                    !uiState.isFocusInContent -> {
                        uiState = uiState.copy(showQuitDialog = true)
                    }
                    uiState.selectionMode -> exitSelectionMode()
                    canNavigateUp() -> navigateUp()
                    else -> {
                        uiState = uiState.copy(
                            isFocusInContent = false,
                            focusSidebarTick = uiState.focusSidebarTick + 1
                        )
                    }
                }
            }
        })

        loadStorageRoots()
        updatePermissionState()

        setContent {
            PandaTvTheme {
                ToolboxScreen(
                    state = uiState,
                    runningAppsState = runningAppsState,
                    ftpServerState = ftpServerState,
                    onSelectRunningApps = {
                        exitSelectionMode()
                        uiState = uiState.copy(
                            activeSection = ToolboxSection.RUNNING_APPS,
                            focusContentTick = uiState.focusContentTick + 1
                        )
                        loadRunningApps()
                    },
                    onSelectFtpServer = {
                        exitSelectionMode()
                        uiState = uiState.copy(
                            activeSection = ToolboxSection.FTP_SERVER,
                            focusContentTick = uiState.focusContentTick + 1
                        )
                    },
                    onToggleFtpServer = { toggleFtpServer() },
                    onSelectRoot = { root ->
                        exitSelectionMode()
                        uiState = uiState.copy(
                            activeSection = ToolboxSection.FILE_MANAGER,
                            focusContentTick = uiState.focusContentTick + 1
                        )
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
                    onOpenSortDialog = {
                        uiState = uiState.copy(showSortDialog = true)
                    },
                    onSelectSortOrder = { order ->
                        saveSortOrder(order)
                        uiState = uiState.copy(sortOrder = order, showSortDialog = false)
                        loadCurrentDirectory()
                    },
                    onDismissSortDialog = {
                        uiState = uiState.copy(showSortDialog = false)
                    },
                    onRefresh = { loadStorageRoots() },
                    onGrantPermission = { requestStoragePermission() },
                    onItemClick = { item -> handleItemClick(item) },
                    onItemLongClick = { item -> handleItemLongClick(item) },
                    onDismissDetails = { uiState = uiState.copy(detailItem = null) },
                    onConfirmDelete = { items -> executeDelete(items) },
                    onDismissDeleteConfirm = { uiState = uiState.copy(deleteConfirmItems = null) },
                    onConfirmQuit = { quitApp() },
                    onDismissQuitDialog = { uiState = uiState.copy(showQuitDialog = false) },
                    onDismissImageViewer = { uiState = uiState.copy(viewingImageItem = null) },
                    onDismissVideoViewer = { uiState = uiState.copy(viewingVideoItem = null) },
                    onToggleActiveOnly = {
                        runningAppsState = runningAppsState.copy(
                            activeOnly = !runningAppsState.activeOnly
                        )
                        loadRunningApps()
                    },
                    onRefreshApps = { loadRunningApps() },
                    onAppClick = { app -> openAppInfoSettings(app.packageName) },
                    onAppLongClick = { app -> runningAppsState = runningAppsState.copy(detailApp = app) },
                    onDismissAppDetails = { runningAppsState = runningAppsState.copy(detailApp = null) },
                    onOpenAppInfo = { app ->
                        runningAppsState = runningAppsState.copy(detailApp = null)
                        openAppInfoSettings(app.packageName)
                    },
                    onContentFocusChanged = { hasFocus ->
                        if (uiState.isFocusInContent != hasFocus) {
                            uiState = uiState.copy(isFocusInContent = hasFocus)
                        }
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
        val pendingApk = pendingApkInstall
        if (pendingApk != null) {
            pendingApkInstall = null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                launchApkInstaller(pendingApk)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            uiState.viewingImageItem == null &&
            uiState.viewingVideoItem == null
        ) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        longPressBackHandled = false
                    } else if (!longPressBackHandled && (event.isLongPress || event.repeatCount >= 1)) {
                        longPressBackHandled = true
                        if (!uiState.showQuitDialog) {
                            focusMainMenu()
                        }
                        return true
                    }
                    if (longPressBackHandled) return true
                }
                KeyEvent.ACTION_UP -> {
                    if (longPressBackHandled) {
                        longPressBackHandled = false
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU &&
            uiState.viewingImageItem == null &&
            uiState.viewingVideoItem == null
        ) {
            focusMainMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun focusMainMenu() {
        uiState = uiState.copy(
            detailItem = null,
            deleteConfirmItems = null,
            showSortDialog = false,
            showQuitDialog = false,
            isFocusInContent = false,
            focusSidebarTick = uiState.focusSidebarTick + 1
        )
        runningAppsState = runningAppsState.copy(detailApp = null)
    }

    private fun quitApp() {
        ftpServer.stop()
        ioExecutor.shutdownNow()
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
        kotlin.system.exitProcess(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        ftpServer.stop()
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

        val internalStorage = Environment.getExternalStorageDirectory()
        if (internalStorage != null) {
            ftpServerState = ftpServerState.copy(
                rootLabel = getString(R.string.storage_internal),
                rootPath = internalStorage.absolutePath
            )
        }

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
        when {
            item.isDirectory -> navigateTo(item.file)
            item.type == FileType.IMAGE -> uiState = uiState.copy(viewingImageItem = item)
            item.type == FileType.VIDEO -> uiState = uiState.copy(viewingVideoItem = item)
            item.extension == "apk" -> installApk(item)
            else -> uiState = uiState.copy(detailItem = item)
        }
    }

    private fun installApk(item: FileItem) {
        if (!item.file.exists()) {
            Toast.makeText(this, getString(R.string.apk_install_failed), Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingApkInstall = item
            Toast.makeText(this, getString(R.string.allow_unknown_apps_toast), Toast.LENGTH_LONG).show()
            try {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(settingsIntent)
                return
            } catch (_: ActivityNotFoundException) {
                pendingApkInstall = null
            }
        }
        launchApkInstaller(item)
    }

    private fun launchApkInstaller(item: FileItem) {
        try {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", item.file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.apk_install_failed), Toast.LENGTH_SHORT).show()
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
        val activeOnly = runningAppsState.activeOnly
        runningAppsState = runningAppsState.copy(isLoading = true)

        ioExecutor.execute {
            val apps = runningAppsRepository.getApps(activeOnly = activeOnly)
            val memSummary = runningAppsRepository.getMemorySummary()
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                runningAppsState = runningAppsState.copy(
                    apps = apps,
                    isLoading = false,
                    memorySummary = memSummary
                )
            }
        }
    }

    private fun toggleFtpServer() {
        if (ftpServer.isRunning) {
            ftpServer.stop()
            ftpServerState = ftpServerState.copy(errorMessage = null)
            return
        }

        val rootPath = ftpServerState.rootPath.ifBlank {
            Environment.getExternalStorageDirectory()?.absolutePath ?: ""
        }
        if (rootPath.isEmpty()) {
            ftpServerState = ftpServerState.copy(errorMessage = getString(R.string.cannot_read_folder))
            return
        }

        val result = ftpServer.start(File(rootPath), LocalFtpServer.DEFAULT_PORT)
        result.onSuccess {
            ftpServerState = ftpServerState.copy(errorMessage = null)
        }.onFailure { error ->
            val message = if (error.message == "NO_NETWORK") {
                getString(R.string.ftp_no_network_error)
            } else {
                getString(R.string.ftp_start_failed, error.message ?: "unknown error")
            }
            ftpServerState = ftpServerState.copy(errorMessage = message)
        }
    }

    private fun loadSavedSortOrder(): SortOrder {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedName = prefs.getString(KEY_SORT_ORDER, null)
        return SortOrder.entries.firstOrNull { it.name == savedName } ?: SortOrder.NAME_ASC
    }

    private fun saveSortOrder(order: SortOrder) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_SORT_ORDER, order.name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "panda_toolbox_prefs"
        private const val KEY_SORT_ORDER = "sort_order"
    }
}
