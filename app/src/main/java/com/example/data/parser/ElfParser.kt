package com.example.data.parser

import com.example.data.model.ElfInfo
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ElfParser {

    private val ELF_MAGIC = byteArrayOf(0x7F.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

    fun parse(file: File): ElfInfo {
        if (!file.exists() || !file.canRead() || file.length() < 16) {
            return ElfInfo.nonElf("File is missing, unreadable, or too small (${file.length()} bytes)")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (!magic.contentEquals(ELF_MAGIC)) {
                    // Check if it's a shell script with shebang
                    raf.seek(0)
                    val headerPreview = ByteArray(minOf(64, file.length().toInt()))
                    raf.readFully(headerPreview)
                    val text = String(headerPreview)
                    return if (text.startsWith("#!")) {
                        ElfInfo.nonElf("Shell/Script file (shebang: ${text.lines().firstOrNull()})")
                    } else {
                        ElfInfo.nonElf("Not an ELF binary (Invalid magic header)")
                    }
                }

                // EI_CLASS (1 = 32-bit, 2 = 64-bit)
                val eiClass = raf.readByte().toInt()
                val is64Bit = eiClass == 2
                val bitness = if (is64Bit) "64-bit" else if (eiClass == 1) "32-bit" else "Unknown ($eiClass)"

                // EI_DATA (1 = little-endian, 2 = big-endian)
                val eiData = raf.readByte().toInt()
                val isLittleEndian = eiData == 1
                val endianness = if (isLittleEndian) "Little-endian (LSB)" else "Big-endian (MSB)"
                val byteOrder = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

                // Skip version, osabi, abiversion, pad (up to offset 16)
                raf.seek(16)

                // Read e_type (2 bytes) and e_machine (2 bytes)
                val headerBuf = ByteArray(if (is64Bit) 48 else 36)
                raf.readFully(headerBuf)
                val bb = ByteBuffer.wrap(headerBuf).order(byteOrder)

                val eType = bb.short.toInt() and 0xFFFF
                val eMachine = bb.short.toInt() and 0xFFFF

                val elfTypeStr = when (eType) {
                    1 -> "Relocatable (ET_REL)"
                    2 -> "Executable (ET_EXEC)"
                    3 -> "PIE / Shared Library (ET_DYN)"
                    4 -> "Core dump (ET_CORE)"
                    else -> "Unknown (0x${Integer.toHexString(eType)})"
                }

                val machineTypeStr = when (eMachine) {
                    183 -> "ARM64 / AArch64 (ARMv8-A)"
                    40 -> "ARM 32-bit (armeabi-v7a)"
                    62 -> "x86_64 (AMD64)"
                    3 -> "x86 (i386 / 32-bit)"
                    243 -> "RISC-V 64-bit"
                    else -> "Machine Code $eMachine"
                }

                val entryPointHex = if (is64Bit) {
                    bb.int // e_version (4 bytes)
                    val entry = bb.long
                    "0x" + java.lang.Long.toHexString(entry)
                } else {
                    bb.int // e_version (4 bytes)
                    val entry = bb.int.toLong() and 0xFFFFFFFFL
                    "0x" + java.lang.Long.toHexString(entry)
                }

                // Check compatibility
                val (isCompatible, compDetails) = ElfInfo.checkCompatibility(eMachine)

                // Try to find PT_INTERP (dynamic linker) and library names
                val interpreter = extractInterpreter(raf, is64Bit, byteOrder)
                val neededLibs = extractNeededLibraries(raf, file.length())

                ElfInfo(
                    isValidElf = true,
                    bitness = bitness,
                    endianness = endianness,
                    machineType = machineTypeStr,
                    machineCode = eMachine,
                    elfType = elfTypeStr,
                    entryPointHex = entryPointHex,
                    interpreter = interpreter,
                    neededLibraries = neededLibs,
                    isArchitectureCompatible = isCompatible,
                    compatibilityDetails = compDetails,
                    error = null
                )
            }
        } catch (e: Exception) {
            ElfInfo.nonElf("Parsing error: ${e.message}")
        }
    }

    private fun extractInterpreter(raf: RandomAccessFile, is64Bit: Boolean, order: ByteOrder): String? {
        return try {
            raf.seek(if (is64Bit) 32 else 28) // e_phoff offset in ELF header
            val phoff = if (is64Bit) {
                readLong(raf, order)
            } else {
                readInt(raf, order).toLong() and 0xFFFFFFFFL
            }

            raf.seek(if (is64Bit) 54 else 42) // e_phentsize
            val phentsize = readShort(raf, order).toInt() and 0xFFFF
            val phnum = readShort(raf, order).toInt() and 0xFFFF

            if (phoff == 0L || phnum == 0 || phentsize == 0) return null

            for (i in 0 until minOf(phnum, 32)) {
                val offset = phoff + (i * phentsize)
                raf.seek(offset)
                val pType = readInt(raf, order)
                if (pType == 3) { // PT_INTERP
                    val pOffset = if (is64Bit) {
                        raf.seek(offset + 8)
                        readLong(raf, order)
                    } else {
                        raf.seek(offset + 4)
                        readInt(raf, order).toLong() and 0xFFFFFFFFL
                    }
                    val pFilesz = if (is64Bit) {
                        raf.seek(offset + 32)
                        readLong(raf, order)
                    } else {
                        raf.seek(offset + 16)
                        readInt(raf, order).toLong() and 0xFFFFFFFFL
                    }

                    if (pOffset > 0 && pFilesz in 1..256) {
                        raf.seek(pOffset)
                        val interpBytes = ByteArray(pFilesz.toInt())
                        raf.readFully(interpBytes)
                        return String(interpBytes).trimEnd { it == '\u0000' }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractNeededLibraries(raf: RandomAccessFile, fileSize: Long): List<String> {
        // Quick scan of string table or printable sequences in the binary looking for .so references
        return try {
            val libs = mutableSetOf<String>()
            val maxScan = minOf(fileSize, 256 * 1024L) // scan up to 256KB
            raf.seek(0)
            val buffer = ByteArray(maxScan.toInt())
            raf.readFully(buffer)

            val text = String(buffer, Charsets.ISO_8859_1)
            val regex = Regex("""[a-zA-Z0-9_\-\.\+]+\.so(?:\.[0-9]+)*""")
            regex.findAll(text).forEach { match ->
                val lib = match.value
                if (lib.length in 4..40 && (lib.startsWith("lib") || lib.contains(".so"))) {
                    libs.add(lib)
                }
            }
            libs.take(8).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readShort(raf: RandomAccessFile, order: ByteOrder): Short {
        val b = ByteArray(2)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(order).short
    }

    private fun readInt(raf: RandomAccessFile, order: ByteOrder): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(order).int
    }

    private fun readLong(raf: RandomAccessFile, order: ByteOrder): Long {
        val b = ByteArray(8)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(order).long
    }
}
