package com.panda.toolbox.model

import java.io.File

enum class FileType {
    FOLDER,
    VIDEO,
    AUDIO,
    IMAGE,
    OTHER
}

enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DATE_DESC;

    fun next(): SortOrder {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val childCount: Int,
    val type: FileType,
    val extension: String
)

data class StorageRoot(
    val label: String,
    val directory: File
)
