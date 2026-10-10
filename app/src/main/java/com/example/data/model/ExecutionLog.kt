package com.example.data.model

enum class ExecutionStatus(val label: String) {
    RUNNING("Running"),
    SUCCESS("Completed (Code 0)"),
    ERROR("Error / Non-Zero Exit"),
    TERMINATED("Terminated by User")
}

enum class OutputType {
    STDOUT,
    STDERR,
    SYSTEM,
    STDIN
}

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: OutputType,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExecutionLog(
    val id: String,
    val pluginId: String,
    val pluginName: String,
    val command: String,
    val arguments: String,
    val exitCode: Int?,
    val startTime: Long,
    val durationMs: Long,
    val status: ExecutionStatus,
    val outputPreview: String,
    val executionModeUsed: String
)
