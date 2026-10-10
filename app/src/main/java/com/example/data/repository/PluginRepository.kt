package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.example.data.model.ElfInfo
import com.example.data.model.ExecutionLog
import com.example.data.model.ExecutionMode
import com.example.data.model.ExecutionStatus
import com.example.data.model.FileItem
import com.example.data.model.PluginItem
import com.example.data.parser.ElfParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PluginRepository(private val context: Context) {

    private val pluginsFile = File(context.filesDir, "plugins_registry.json")
    private val historyFile = File(context.filesDir, "history_registry.json")
    private val pluginsDir = File(context.filesDir, "plugins").apply { mkdirs() }

    private val _plugins = MutableStateFlow<List<PluginItem>>(emptyList())
    val plugins: StateFlow<List<PluginItem>> = _plugins.asStateFlow()

    private val _history = MutableStateFlow<List<ExecutionLog>>(emptyList())
    val history: StateFlow<List<ExecutionLog>> = _history.asStateFlow()

    init {
        loadPlugins()
        loadHistory()
        if (_plugins.value.isEmpty()) {
            initSamplePlugins()
        }
    }

    private fun loadPlugins() {
        if (!pluginsFile.exists()) return
        try {
            val content = pluginsFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<PluginItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val path = obj.getString("localExecutablePath")
                val binFile = File(path)
                val elf = if (binFile.exists()) ElfParser.parse(binFile) else null

                list.add(
                    PluginItem(
                        id = id,
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        binaryFileName = obj.getString("binaryFileName"),
                        localExecutablePath = path,
                        fileSize = obj.optLong("fileSize", binFile.length()),
                        machineType = obj.optString("machineType", elf?.machineType ?: "Unknown"),
                        isArchitectureCompatible = obj.optBoolean("isArchitectureCompatible", elf?.isArchitectureCompatible ?: true),
                        defaultArgs = obj.optString("defaultArgs", ""),
                        executionMode = try {
                            ExecutionMode.valueOf(obj.optString("executionMode", ExecutionMode.AUTO.name))
                        } catch (_: Exception) { ExecutionMode.AUTO },
                        category = obj.optString("category", "User Plugin"),
                        isBuiltIn = obj.optBoolean("isBuiltIn", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        lastRunAt = if (obj.has("lastRunAt")) obj.getLong("lastRunAt") else null,
                        elfInfo = elf
                    )
                )
            }
            _plugins.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePluginsToDisk() {
        try {
            val array = JSONArray()
            for (p in _plugins.value) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("description", p.description)
                    put("binaryFileName", p.binaryFileName)
                    put("localExecutablePath", p.localExecutablePath)
                    put("fileSize", p.fileSize)
                    put("machineType", p.machineType)
                    put("isArchitectureCompatible", p.isArchitectureCompatible)
                    put("defaultArgs", p.defaultArgs)
                    put("executionMode", p.executionMode.name)
                    put("category", p.category)
                    put("isBuiltIn", p.isBuiltIn)
                    put("createdAt", p.createdAt)
                    p.lastRunAt?.let { put("lastRunAt", it) }
                }
                array.put(obj)
            }
            pluginsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadHistory() {
        if (!historyFile.exists()) return
        try {
            val content = historyFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ExecutionLog>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ExecutionLog(
                        id = obj.getString("id"),
                        pluginId = obj.getString("pluginId"),
                        pluginName = obj.getString("pluginName"),
                        command = obj.getString("command"),
                        arguments = obj.optString("arguments", ""),
                        exitCode = if (obj.has("exitCode")) obj.getInt("exitCode") else null,
                        startTime = obj.optLong("startTime", 0),
                        durationMs = obj.optLong("durationMs", 0),
                        status = try {
                            ExecutionStatus.valueOf(obj.getString("status"))
                        } catch (_: Exception) { ExecutionStatus.SUCCESS },
                        outputPreview = obj.optString("outputPreview", ""),
                        executionModeUsed = obj.optString("executionModeUsed", "AUTO")
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistoryToDisk() {
        try {
            val array = JSONArray()
            for (log in _history.value.take(100)) { // limit to 100 history items
                val obj = JSONObject().apply {
                    put("id", log.id)
                    put("pluginId", log.pluginId)
                    put("pluginName", log.pluginName)
                    put("command", log.command)
                    put("arguments", log.arguments)
                    log.exitCode?.let { put("exitCode", it) }
                    put("startTime", log.startTime)
                    put("durationMs", log.durationMs)
                    put("status", log.status.name)
                    put("outputPreview", log.outputPreview)
                    put("executionModeUsed", log.executionModeUsed)
                }
                array.put(obj)
            }
            historyFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        customName: String?,
        customDescription: String?,
        defaultArgs: String = ""
    ): PluginItem = withContext(Dispatchers.IO) {
        val fileName = resolveFileName(uri) ?: "binary_${System.currentTimeMillis()}"
        val pluginId = UUID.randomUUID().toString()
        val pluginFolder = File(pluginsDir, pluginId).apply { mkdirs() }
        val targetFile = File(pluginFolder, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read binary stream from URI: $uri")

        makeFileExecutable(targetFile)
        val elf = ElfParser.parse(targetFile)

        val name = if (!customName.isNullOrBlank()) customName else fileName
        val description = if (!customDescription.isNullOrBlank()) customDescription else "Imported C++ binary plugin ($fileName)"

        val plugin = PluginItem(
            id = pluginId,
            name = name,
            description = description,
            binaryFileName = fileName,
            localExecutablePath = targetFile.absolutePath,
            fileSize = targetFile.length(),
            machineType = elf.machineType,
            isArchitectureCompatible = elf.isArchitectureCompatible,
            defaultArgs = defaultArgs,
            executionMode = ExecutionMode.AUTO,
            category = "User Plugin",
            isBuiltIn = false,
            elfInfo = elf
        )

        _plugins.value = listOf(plugin) + _plugins.value
        savePluginsToDisk()
        plugin
    }

    suspend fun importFromFile(
        sourceFile: File,
        customName: String?,
        customDescription: String?,
        defaultArgs: String = ""
    ): PluginItem = withContext(Dispatchers.IO) {
        val fileName = sourceFile.name
        val pluginId = UUID.randomUUID().toString()
        val pluginFolder = File(pluginsDir, pluginId).apply { mkdirs() }
        val targetFile = File(pluginFolder, fileName)

        sourceFile.copyTo(targetFile, overwrite = true)
        makeFileExecutable(targetFile)
        val elf = ElfParser.parse(targetFile)

        val name = if (!customName.isNullOrBlank()) customName else fileName
        val description = if (!customDescription.isNullOrBlank()) customDescription else "Imported C++ binary from ${sourceFile.parent}"

        val plugin = PluginItem(
            id = pluginId,
            name = name,
            description = description,
            binaryFileName = fileName,
            localExecutablePath = targetFile.absolutePath,
            fileSize = targetFile.length(),
            machineType = elf.machineType,
            isArchitectureCompatible = elf.isArchitectureCompatible,
            defaultArgs = defaultArgs,
            executionMode = ExecutionMode.AUTO,
            category = "User Plugin",
            isBuiltIn = false,
            elfInfo = elf
        )

        _plugins.value = listOf(plugin) + _plugins.value
        savePluginsToDisk()
        plugin
    }

    suspend fun updatePlugin(updated: PluginItem) = withContext(Dispatchers.IO) {
        _plugins.value = _plugins.value.map { if (it.id == updated.id) updated else it }
        savePluginsToDisk()
    }

    suspend fun deletePlugin(pluginId: String) = withContext(Dispatchers.IO) {
        val plugin = _plugins.value.find { it.id == pluginId }
        plugin?.let {
            try {
                File(it.localExecutablePath).parentFile?.deleteRecursively()
            } catch (_: Exception) {}
        }
        _plugins.value = _plugins.value.filter { it.id != pluginId }
        savePluginsToDisk()
    }

    suspend fun addExecutionLog(log: ExecutionLog) = withContext(Dispatchers.IO) {
        _history.value = listOf(log) + _history.value
        saveHistoryToDisk()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _history.value = emptyList()
        saveHistoryToDisk()
    }

    suspend fun listDirectory(dir: File): List<FileItem> = withContext(Dispatchers.IO) {
        val files = dir.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            val isDir = file.isDirectory
            val elf = if (!isDir && file.length() >= 16) ElfParser.parse(file) else null
            FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                isElfBinary = elf?.isValidElf ?: false,
                elfInfo = elf,
                canRead = file.canRead(),
                canExecute = file.canExecute()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun getQuickDirectories(): List<Pair<String, File>> {
        val list = mutableListOf<Pair<String, File>>()
        list.add(Pair("Plugins Folder", pluginsDir))
        list.add(Pair("App Internal Storage", context.filesDir))

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads.exists()) {
                list.add(Pair("Downloads (/sdcard/Download)", downloads))
            }
        } catch (_: Exception) {}

        try {
            val sdcard = File("/sdcard")
            if (sdcard.exists()) {
                list.add(Pair("Internal Storage (/sdcard)", sdcard))
            }
        } catch (_: Exception) {}

        val termuxHome = File("/data/data/com.termux/files/home")
        if (termuxHome.exists() && termuxHome.canRead()) {
            list.add(Pair("Termux Home", termuxHome))
        }

        val root = File("/")
        if (root.canRead()) {
            list.add(Pair("Device Root (/)", root))
        }

        return list
    }

    private fun resolveFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    private fun makeFileExecutable(file: File) {
        file.setReadable(true, false)
        file.setExecutable(true, false)
        try {
            Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
        } catch (_: Exception) {}
    }

    /**
     * Creates starter demonstration tools so the app is immediately useful out-of-the-box.
     */
    private fun initSamplePlugins() {
        try {
            val sampleFolder = File(pluginsDir, "sample_hw").apply { mkdirs() }
            val hwFile = File(sampleFolder, "hw_probe.sh")
            hwFile.writeText(
                """#!/system/bin/sh
echo "=========================================="
echo "  Native Hardware & ABI Architecture Probe"
echo "=========================================="
echo "Kernel:      $(uname -a 2>/dev/null || cat /proc/version)"
echo "Architecture: $(uname -m 2>/dev/null || echo 'Unknown')"
echo "Host Linker: $(ls -l /system/bin/linker* 2>/dev/null)"
echo "Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}"
echo "Primary ABI:    ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}"
echo "------------------------------------------"
echo "Termux Status:"
if [ -d "/data/data/com.termux" ]; then
    echo "  Termux is installed! (/data/data/com.termux exists)"
    ls -d /data/data/com.termux/files/usr/lib 2>/dev/null || true
else
    echo "  Termux directory not detected in private storage."
fi
echo "=========================================="
echo "Probe completed successfully."
"""
            )
            makeFileExecutable(hwFile)

            val hwElf = ElfParser.parse(hwFile)
            val hwPlugin = PluginItem(
                id = "sample_hw_probe",
                name = "Hardware & ABI Probe",
                description = "Probes device CPU architecture, kernel, 64-bit/32-bit linker, and Termux environment",
                binaryFileName = "hw_probe.sh",
                localExecutablePath = hwFile.absolutePath,
                fileSize = hwFile.length(),
                machineType = "Shell / POSIX Script",
                isArchitectureCompatible = true,
                defaultArgs = "",
                executionMode = ExecutionMode.SHELL,
                category = "System Probe",
                isBuiltIn = true,
                elfInfo = hwElf
            )

            // Sample Benchmark / Prime calculator script
            val benchFolder = File(pluginsDir, "sample_bench").apply { mkdirs() }
            val benchFile = File(benchFolder, "cpp_bench.sh")
            benchFile.writeText(
                """#!/system/bin/sh
LIMIT=${'$'}{1:-100}
echo "=========================================="
echo "   C++ Benchmark Simulator (Limit: ${'$'}LIMIT)"
echo "=========================================="
echo "Running computation on ${Build.SUPPORTED_ABIS.firstOrNull()}..."
START=$(date +%s%N 2>/dev/null || date +%s)

count=0
i=2
while [ ${'$'}i -le ${'$'}LIMIT ]; do
    isPrime=1
    j=2
    while [ $((j * j)) -le ${'$'}i ]; do
        if [ $((i % j)) -eq 0 ]; then
            isPrime=0
            break
        fi
        j=$((j + 1))
    done
    if [ ${'$'}isPrime -eq 1 ]; then
        count=$((count + 1))
    fi
    i=$((i + 1))
done

echo "Found ${'$'}count prime numbers up to ${'$'}LIMIT."
echo "Benchmark completed in simulated native loop."
"""
            )
            makeFileExecutable(benchFile)

            val benchPlugin = PluginItem(
                id = "sample_prime_bench",
                name = "Math & Prime Calculator",
                description = "Demonstrates CLI argument parsing (--limit N), execution timing, and calculation loop",
                binaryFileName = "cpp_bench.sh",
                localExecutablePath = benchFile.absolutePath,
                fileSize = benchFile.length(),
                machineType = "POSIX Executable",
                isArchitectureCompatible = true,
                defaultArgs = "500",
                executionMode = ExecutionMode.SHELL,
                category = "Math & Benchmarks",
                isBuiltIn = true
            )

            // Sample Interactive I/O Tool
            val ioFolder = File(pluginsDir, "sample_io").apply { mkdirs() }
            val ioFile = File(ioFolder, "interactive_io.sh")
            ioFile.writeText(
                """#!/system/bin/sh
echo "=== Interactive C++ I/O Tool ==="
echo "Please enter your name below:"
read name
echo "Hello, ${'$'}name! Enter a number to calculate its square:"
read num
square=$((num * num))
echo "Result: ${'$'}num squared = ${'$'}square"
echo "Interactive session finished."
"""
            )
            makeFileExecutable(ioFile)

            val ioPlugin = PluginItem(
                id = "sample_interactive_io",
                name = "Interactive I/O Test",
                description = "Tests stdin text streaming, interactive prompts, and user input in the terminal",
                binaryFileName = "interactive_io.sh",
                localExecutablePath = ioFile.absolutePath,
                fileSize = ioFile.length(),
                machineType = "Interactive Executable",
                isArchitectureCompatible = true,
                defaultArgs = "",
                executionMode = ExecutionMode.SHELL,
                category = "Interactive I/O",
                isBuiltIn = true
            )

            _plugins.value = listOf(hwPlugin, benchPlugin, ioPlugin)
            savePluginsToDisk()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
