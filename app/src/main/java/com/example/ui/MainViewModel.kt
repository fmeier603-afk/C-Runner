package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.execution.ProcessRunner
import com.example.data.model.ElfInfo
import com.example.data.model.ExecutionLog
import com.example.data.model.ExecutionMode
import com.example.data.model.ExecutionStatus
import com.example.data.model.FileItem
import com.example.data.model.OutputType
import com.example.data.model.PluginItem
import com.example.data.model.TerminalLine
import com.example.data.parser.ElfParser
import com.example.data.repository.PluginRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PluginRepository(application)
    private val processRunner = ProcessRunner(application)

    val plugins: StateFlow<List<PluginItem>> = repository.plugins
    val history: StateFlow<List<ExecutionLog>> = repository.history

    // Navigation & Tabs
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // File Browser State
    private val defaultBrowserDir: File = run {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (dl != null && dl.exists() && dl.canRead()) dl else application.filesDir
    }
    private val _currentDirectory = MutableStateFlow(defaultBrowserDir)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _currentDirectoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val currentDirectoryFiles: StateFlow<List<FileItem>> = _currentDirectoryFiles.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    // Terminal State
    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    private val _isProcessRunning = MutableStateFlow(false)
    val isProcessRunning: StateFlow<Boolean> = _isProcessRunning.asStateFlow()

    private val _currentRunningPlugin = MutableStateFlow<PluginItem?>(null)
    val currentRunningPlugin: StateFlow<PluginItem?> = _currentRunningPlugin.asStateFlow()

    // Dialogs State
    private val _selectedElfDialog = MutableStateFlow<Pair<File, ElfInfo>?>(null)
    val selectedElfDialog: StateFlow<Pair<File, ElfInfo>?> = _selectedElfDialog.asStateFlow()

    private val _editingPlugin = MutableStateFlow<PluginItem?>(null)
    val editingPlugin: StateFlow<PluginItem?> = _editingPlugin.asStateFlow()

    // Search and Filter
    private val _pluginSearchQuery = MutableStateFlow("")
    val pluginSearchQuery: StateFlow<String> = _pluginSearchQuery.asStateFlow()

    init {
        loadDirectory(_currentDirectory.value)
    }

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    fun setPluginSearchQuery(query: String) {
        _pluginSearchQuery.value = query
    }

    fun openElfDialog(file: File) {
        viewModelScope.launch {
            val elf = ElfParser.parse(file)
            _selectedElfDialog.value = Pair(file, elf)
        }
    }

    fun closeElfDialog() {
        _selectedElfDialog.value = null
    }

    fun openEditPluginDialog(plugin: PluginItem) {
        _editingPlugin.value = plugin
    }

    fun closeEditPluginDialog() {
        _editingPlugin.value = null
    }

    // --- File Browser Actions ---

    fun navigateToDirectory(dir: File) {
        if (dir.isDirectory && dir.canRead()) {
            _currentDirectory.value = dir
            loadDirectory(dir)
        }
    }

    fun navigateUp() {
        val current = _currentDirectory.value
        val parent = current.parentFile
        if (parent != null && parent.canRead()) {
            navigateToDirectory(parent)
        }
    }

    fun refreshCurrentDirectory() {
        loadDirectory(_currentDirectory.value)
    }

    private fun loadDirectory(dir: File) {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val files = repository.listDirectory(dir)
            _currentDirectoryFiles.value = files
            _isLoadingFiles.value = false
        }
    }

    fun getQuickDirectories(): List<Pair<String, File>> = repository.getQuickDirectories()

    // --- Plugin Import & Management ---

    fun importFromUri(uri: Uri, name: String? = null, desc: String? = null, defaultArgs: String = "") {
        viewModelScope.launch {
            try {
                val plugin = repository.importFromUri(uri, name, desc, defaultArgs)
                addTerminalLine("[Plugin '${plugin.name}' imported successfully into secure internal storage]\n", OutputType.SYSTEM)
                _currentTab.value = 0 // Switch to Plugins tab
            } catch (e: Exception) {
                addTerminalLine("Failed to import plugin: ${e.message}\n", OutputType.STDERR)
            }
        }
    }

    fun importFromFile(file: File, name: String? = null, desc: String? = null, defaultArgs: String = "") {
        viewModelScope.launch {
            try {
                val plugin = repository.importFromFile(file, name, desc, defaultArgs)
                addTerminalLine("[Plugin '${plugin.name}' imported from ${file.name}]\n", OutputType.SYSTEM)
                _currentTab.value = 0 // Switch to Plugins tab
            } catch (e: Exception) {
                addTerminalLine("Failed to import plugin: ${e.message}\n", OutputType.STDERR)
            }
        }
    }

    fun updatePlugin(plugin: PluginItem) {
        viewModelScope.launch {
            repository.updatePlugin(plugin)
        }
    }

    fun deletePlugin(pluginId: String) {
        viewModelScope.launch {
            repository.deletePlugin(pluginId)
        }
    }

    // --- Process Execution & Terminal ---

    fun runPlugin(
        plugin: PluginItem,
        customArgs: String? = null,
        modeOverride: ExecutionMode? = null
    ) {
        if (_isProcessRunning.value) {
            addTerminalLine("Cannot start new process: Another process is already running!\n", OutputType.STDERR)
            return
        }

        viewModelScope.launch {
            _isProcessRunning.value = true
            _currentRunningPlugin.value = plugin
            _currentTab.value = 2 // Switch to Terminal view

            val argsString = customArgs ?: plugin.defaultArgs
            val argList = if (argsString.isBlank()) emptyList() else argsString.trim().split(Regex("\\s+"))
            val effectiveMode = modeOverride ?: plugin.executionMode

            addTerminalLine("========================================\n", OutputType.SYSTEM)
            addTerminalLine("Executing C++ Plugin: ${plugin.name}\n", OutputType.SYSTEM)
            addTerminalLine("Binary: ${plugin.binaryFileName}\n", OutputType.SYSTEM)
            if (argList.isNotEmpty()) {
                addTerminalLine("Arguments: ${argList.joinToString(" ")}\n", OutputType.SYSTEM)
            }
            addTerminalLine("Execution Mode: ${effectiveMode.displayName}\n", OutputType.SYSTEM)
            addTerminalLine("========================================\n", OutputType.SYSTEM)

            val startTime = System.currentTimeMillis()
            val outputBuffer = StringBuilder()

            val exitCode = try {
                processRunner.execute(
                    binaryPath = plugin.localExecutablePath,
                    arguments = argList,
                    environment = plugin.envVariables,
                    workingDir = File(plugin.localExecutablePath).parentFile,
                    mode = effectiveMode
                ) { text, type ->
                    addTerminalLine(text, type)
                    outputBuffer.append(text)
                }
            } catch (e: Exception) {
                val err = "Fatal execution error: ${e.message}\n"
                addTerminalLine(err, OutputType.STDERR)
                outputBuffer.append(err)
                -1
            }

            val durationMs = System.currentTimeMillis() - startTime
            val status = when {
                exitCode == 0 -> ExecutionStatus.SUCCESS
                exitCode == -1 && !processRunner.isProcessRunning() -> ExecutionStatus.TERMINATED
                else -> ExecutionStatus.ERROR
            }

            _isProcessRunning.value = false
            _currentRunningPlugin.value = null

            // Update plugin last run timestamp
            repository.updatePlugin(plugin.copy(lastRunAt = System.currentTimeMillis()))

            // Save to execution history
            val log = ExecutionLog(
                id = java.util.UUID.randomUUID().toString(),
                pluginId = plugin.id,
                pluginName = plugin.name,
                command = plugin.binaryFileName,
                arguments = argsString,
                exitCode = exitCode,
                startTime = startTime,
                durationMs = durationMs,
                status = status,
                outputPreview = outputBuffer.take(500).toString(),
                executionModeUsed = effectiveMode.name
            )
            repository.addExecutionLog(log)
        }
    }

    fun sendStdin(text: String, appendNewline: Boolean = true) {
        addTerminalLine(text + (if (appendNewline) "\n" else ""), OutputType.STDIN)
        processRunner.sendStdin(text, appendNewline)
    }

    fun closeStdin() {
        addTerminalLine("[Sent EOF (Ctrl+D) to stdin]\n", OutputType.SYSTEM)
        processRunner.closeStdin()
    }

    fun killRunningProcess() {
        addTerminalLine("[Terminating process by user request...]\n", OutputType.SYSTEM)
        processRunner.killCurrentProcess()
        _isProcessRunning.value = false
        _currentRunningPlugin.value = null
    }

    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private fun addTerminalLine(text: String, type: OutputType) {
        val newLine = TerminalLine(text = text, type = type)
        // Keep up to 2000 lines in memory
        val current = _terminalLines.value
        _terminalLines.value = if (current.size > 2000) {
            current.drop(current.size - 1800) + newLine
        } else {
            current + newLine
        }
    }
}
