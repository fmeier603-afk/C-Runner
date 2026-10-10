package com.example.data.execution

import android.content.Context
import android.os.Build
import com.example.data.model.ExecutionMode
import com.example.data.model.OutputType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

class ProcessRunner(private val context: Context) {

    private var activeProcess: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private val isRunning = AtomicBoolean(false)

    /**
     * Executes a binary with real-time output callbacks and stdin support
     */
    suspend fun execute(
        binaryPath: String,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDir: File?,
        mode: ExecutionMode,
        onOutput: (String, OutputType) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val binaryFile = File(binaryPath)
        if (!binaryFile.exists()) {
            onOutput("Error: Binary file not found at $binaryPath\n", OutputType.STDERR)
            return@withContext -1
        }

        // Ensure executable permissions
        ensureExecutable(binaryFile)

        onOutput("[Host: System ABI ${Build.SUPPORTED_ABIS.firstOrNull()} | Android API ${Build.VERSION.SDK_INT}]\n", OutputType.SYSTEM)
        onOutput("[Target: ${binaryFile.name} (${binaryFile.length()} bytes)]\n", OutputType.SYSTEM)

        when (mode) {
            ExecutionMode.AUTO -> runWithAutoFallback(binaryFile, arguments, environment, workingDir, onOutput)
            ExecutionMode.LINKER -> runWithLinker(binaryFile, arguments, environment, workingDir, onOutput)
            ExecutionMode.DIRECT -> runDirect(binaryFile, arguments, environment, workingDir, onOutput)
            ExecutionMode.SHELL -> runWithShell(binaryFile, arguments, environment, workingDir, onOutput)
        }
    }

    private suspend fun runWithAutoFallback(
        binaryFile: File,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDir: File?,
        onOutput: (String, OutputType) -> Unit
    ): Int {
        return try {
            onOutput("[Mode: Attempting Direct Execution...]\n", OutputType.SYSTEM)
            runDirect(binaryFile, arguments, environment, workingDir, onOutput)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Permission denied", ignoreCase = true) || msg.contains("error=13") || msg.contains("EACCES")) {
                onOutput("[SELinux restricted direct exec. Engaging Linker wrapper workaround...]\n", OutputType.SYSTEM)
                try {
                    runWithLinker(binaryFile, arguments, environment, workingDir, onOutput)
                } catch (linkerEx: Exception) {
                    onOutput("[Linker wrapper failed (${linkerEx.message}). Trying Shell wrapper (/system/bin/sh)...]\n", OutputType.SYSTEM)
                    runWithShell(binaryFile, arguments, environment, workingDir, onOutput)
                }
            } else {
                onOutput("Execution failed: ${e.message}\n", OutputType.STDERR)
                -1
            }
        }
    }

    private suspend fun runDirect(
        binaryFile: File,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDir: File?,
        onOutput: (String, OutputType) -> Unit
    ): Int {
        val cmd = mutableListOf(binaryFile.absolutePath)
        cmd.addAll(arguments.filter { it.isNotBlank() })
        return executeProcess(cmd, environment, workingDir, onOutput)
    }

    private suspend fun runWithLinker(
        binaryFile: File,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDir: File?,
        onOutput: (String, OutputType) -> Unit
    ): Int {
        val linker = findSystemLinker()
        onOutput("[Using Dynamic Linker: $linker]\n", OutputType.SYSTEM)
        val cmd = mutableListOf(linker, binaryFile.absolutePath)
        cmd.addAll(arguments.filter { it.isNotBlank() })
        return executeProcess(cmd, environment, workingDir, onOutput)
    }

    private suspend fun runWithShell(
        binaryFile: File,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDir: File?,
        onOutput: (String, OutputType) -> Unit
    ): Int {
        val escapedArgs = arguments.filter { it.isNotBlank() }.joinToString(" ") { "\"$it\"" }
        val fullCmd = "\"${binaryFile.absolutePath}\" $escapedArgs"
        onOutput("[Using Shell wrapper: sh -c $fullCmd]\n", OutputType.SYSTEM)
        val cmd = listOf("/system/bin/sh", "-c", fullCmd)
        return executeProcess(cmd, environment, workingDir, onOutput)
    }

    private fun executeProcess(
        command: List<String>,
        environment: Map<String, String>,
        workingDir: File?,
        onOutput: (String, OutputType) -> Unit
    ): Int {
        val pb = ProcessBuilder(command)

        // Set working directory (defaults to binary's parent or app files dir)
        val dir = workingDir ?: context.filesDir
        if (dir.exists()) {
            pb.directory(dir)
        }

        // Configure Environment variables
        val env = pb.environment()
        env["TERM"] = "xterm-256color"
        env["HOME"] = File(context.filesDir, "home").apply { mkdirs() }.absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath

        // Append standard Android & Termux paths
        val existingPath = env["PATH"] ?: "/system/bin:/system/xbin"
        val termuxBin = "/data/data/com.termux/files/usr/bin"
        env["PATH"] = "$termuxBin:$existingPath"

        // Setup LD_LIBRARY_PATH
        val termuxLib = "/data/data/com.termux/files/usr/lib"
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val sysLibs = if (is64Bit) "/system/lib64:/vendor/lib64" else "/system/lib:/vendor/lib"
        val appNativeDir = context.applicationInfo.nativeLibraryDir
        env["LD_LIBRARY_PATH"] = "$termuxLib:$appNativeDir:$sysLibs"
        env["PREFIX"] = "/data/data/com.termux/files/usr"

        // Add user-defined environment variables
        environment.forEach { (k, v) ->
            env[k] = v
        }

        isRunning.set(true)
        onOutput("[Process started: ${command.first().substringAfterLast('/')}]\n", OutputType.SYSTEM)

        val process = pb.start()
        activeProcess = process
        stdinWriter = BufferedWriter(OutputStreamWriter(process.outputStream))

        // Start async stream readers
        val stdoutThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onOutput(line + "\n", OutputType.STDOUT)
                    }
                }
            } catch (_: IOException) {}
        }

        val stderrThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onOutput(line + "\n", OutputType.STDERR)
                    }
                }
            } catch (_: IOException) {}
        }

        stdoutThread.start()
        stderrThread.start()

        val exitCode = try {
            process.waitFor()
        } catch (_: InterruptedException) {
            process.destroy()
            -1
        } finally {
            isRunning.set(false)
            activeProcess = null
            stdinWriter = null
        }

        stdoutThread.join(500)
        stderrThread.join(500)

        if (exitCode == 0) {
            onOutput("[Process finished with exit code 0 (Success)]\n", OutputType.SYSTEM)
        } else {
            onOutput("[Process exited with code $exitCode]\n", OutputType.STDERR)
        }

        return exitCode
    }

    fun sendStdin(text: String, appendNewline: Boolean = true): Boolean {
        return try {
            stdinWriter?.let { writer ->
                writer.write(text)
                if (appendNewline) {
                    writer.newLine()
                }
                writer.flush()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun closeStdin(): Boolean {
        return try {
            stdinWriter?.close()
            stdinWriter = null
            true
        } catch (e: Exception) {
            false
        }
    }

    fun killCurrentProcess() {
        try {
            activeProcess?.let { proc ->
                proc.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    proc.destroyForcibly()
                }
            }
        } catch (_: Exception) {}
        isRunning.set(false)
        activeProcess = null
        stdinWriter = null
    }

    fun isProcessRunning(): Boolean = isRunning.get()

    private fun findSystemLinker(): String {
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val linker64 = File("/system/bin/linker64")
        val linker32 = File("/system/bin/linker")

        return when {
            is64Bit && linker64.exists() -> "/system/bin/linker64"
            linker32.exists() -> "/system/bin/linker"
            linker64.exists() -> "/system/bin/linker64"
            else -> "/system/bin/linker64"
        }
    }

    private fun ensureExecutable(file: File) {
        file.setReadable(true, false)
        file.setExecutable(true, false)
        try {
            Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
        } catch (_: Exception) {}
    }
}
