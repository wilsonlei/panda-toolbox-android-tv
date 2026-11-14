package com.panda.toolbox.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.panda.toolbox.R
import com.panda.toolbox.model.FileItem
import com.panda.toolbox.model.FileType
import com.panda.toolbox.model.SortOrder
import com.panda.toolbox.model.StorageRoot
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class FileRepository(private val context: Context) {

    data class DirectoryLoadResult(
        val items: List<FileItem>,
        val readable: Boolean
    )

    data class DeleteResult(
        val deletedCount: Int,
        val failedPaths: List<String>
    )

    fun getStorageRoots(): List<StorageRoot> {
        val roots = mutableListOf<StorageRoot>()
        val seenPaths = mutableSetOf<String>()

        fun addIfValid(label: String, dir: File?) {
            if (dir != null && dir.exists() && dir.isDirectory && seenPaths.add(dir.absolutePath)) {
                roots.add(StorageRoot(label, dir))
            }
        }

        val primary = Environment.getExternalStorageDirectory()
        addIfValid(context.getString(R.string.storage_internal), primary)

        if (primary != null) {
            addIfValid(context.getString(R.string.storage_documents), File(primary, "Documents"))
        }

        // Discover mounted external USB / SD card volumes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            storageManager?.storageVolumes?.forEach { volume ->
                if (!volume.isPrimary) {
                    val dir = volume.directory
                    val label = volume.getDescription(context)
                        ?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.storage_external)
                    addIfValid(label, dir)
                }
            }
        } else {
            val storageDir = File("/storage")
            storageDir.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name != "emulated" && child.name != "self") {
                    addIfValid(child.name, child)
                }
            }
        }

        return roots
    }

    fun listDirectory(directory: File, sortOrder: SortOrder): DirectoryLoadResult {
        val rawFiles = directory.listFiles() ?: return DirectoryLoadResult(emptyList(), false)

        val mapped = rawFiles.map { file ->
            val isDir = file.isDirectory
            val ext = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
            val childCount = if (isDir) file.list()?.size ?: 0 else 0
            FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                childCount = childCount,
                type = if (isDir) FileType.FOLDER else resolveFileType(ext),
                extension = ext
            )
        }

        val sorted = when (sortOrder) {
            SortOrder.NAME_ASC -> mapped.sortedWith(
                compareBy<FileItem> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            SortOrder.NAME_DESC -> mapped.sortedWith(
                compareBy<FileItem> { !it.isDirectory }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            SortOrder.SIZE_DESC -> mapped.sortedWith(
                compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.sizeBytes }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            SortOrder.DATE_ASC -> mapped.sortedWith(
                compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            SortOrder.DATE_DESC -> mapped.sortedWith(
                compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        }

        return DirectoryLoadResult(sorted, true)
    }

    fun deleteFiles(paths: Collection<String>): DeleteResult {
        var deletedCount = 0
        val failedPaths = mutableListOf<String>()

        for (path in paths) {
            val target = File(path)
            if (!target.exists()) {
                continue
            }
            val success = if (target.isDirectory) {
                target.deleteRecursively()
            } else {
                target.delete()
            }
            if (success) {
                deletedCount++
            } else {
                failedPaths.add(path)
            }
        }

        return DeleteResult(deletedCount, failedPaths)
    }

    fun getStorageSpaceSummary(directory: File): String? {
        return try {
            val stat = StatFs(directory.absolutePath)
            val freeBytes = stat.availableBytes
            val totalBytes = stat.totalBytes
            "${formatSize(freeBytes)} free of ${formatSize(totalBytes)}"
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun resolveFileType(ext: String): FileType {
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts" -> FileType.VIDEO
            "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus" -> FileType.AUDIO
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> FileType.IMAGE
            else -> FileType.OTHER
        }
    }

    companion object {
        private val VIDEO_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

        fun formatSize(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            var value = bytes.toDouble()
            var unitIndex = 0
            while (value >= 1024.0 && unitIndex < VIDEO_UNITS.lastIndex) {
                value /= 1024.0
                unitIndex++
            }
            return String.format(Locale.US, "%.1f %s", value, VIDEO_UNITS[unitIndex])
        }

        fun formatDate(millis: Long): String {
            if (millis <= 0L) return ""
            return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(millis))
        }
    }
}
