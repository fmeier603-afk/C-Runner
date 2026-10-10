package com.example.data.model

import java.io.File

data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isElfBinary: Boolean = false,
    val elfInfo: ElfInfo? = null,
    val canRead: Boolean = true,
    val canExecute: Boolean = false
)
