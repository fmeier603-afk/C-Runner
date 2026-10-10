package com.example.data.model

enum class ExecutionMode(val displayName: String, val description: String) {
    AUTO("Smart Auto-Fallback", "Tries direct execution, automatically falls back to /system/bin/linker64 if SELinux blocks exec"),
    LINKER("Dynamic Linker (/system/bin/linker64)", "Runs binary through Android dynamic linker; bypasses noexec restriction on app data"),
    DIRECT("Direct ProcessBuilder", "Standard process execution (requires executable permissions)"),
    SHELL("Subshell (/system/bin/sh -c)", "Runs inside /system/bin/sh subshell with piping and environment support")
}

data class PluginItem(
    val id: String,
    val name: String,
    val description: String,
    val binaryFileName: String,
    val localExecutablePath: String,
    val fileSize: Long,
    val machineType: String,
    val isArchitectureCompatible: Boolean,
    val defaultArgs: String = "",
    val envVariables: Map<String, String> = emptyMap(),
    val executionMode: ExecutionMode = ExecutionMode.AUTO,
    val category: String = "User Plugin",
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val elfInfo: ElfInfo? = null
)
