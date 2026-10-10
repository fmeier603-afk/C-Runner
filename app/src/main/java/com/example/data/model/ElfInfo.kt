package com.example.data.model

import android.os.Build

/**
 * Parsed ELF (Executable and Linkable Format) metadata
 */
data class ElfInfo(
    val isValidElf: Boolean,
    val bitness: String = "Unknown", // 32-bit or 64-bit
    val endianness: String = "Unknown", // Little-endian or Big-endian
    val machineType: String = "Unknown", // AArch64, ARM, x86_64, x86, etc.
    val machineCode: Int = 0,
    val elfType: String = "Unknown", // Executable (ET_EXEC), Shared Object / PIE (ET_DYN), Relocatable (ET_REL)
    val entryPointHex: String = "0x0",
    val interpreter: String? = null, // e.g. /system/bin/linker64 or /data/data/com.termux/files/usr/bin/linker64
    val neededLibraries: List<String> = emptyList(), // e.g. libc++.so, libc.so, libm.so
    val isArchitectureCompatible: Boolean = false,
    val compatibilityDetails: String = "",
    val error: String? = null
) {
    companion object {
        fun nonElf(reason: String): ElfInfo {
            return ElfInfo(
                isValidElf = false,
                error = reason
            )
        }

        /**
         * Checks if the ELF machine type matches the Android device's supported ABIs
         */
        fun checkCompatibility(machineCode: Int): Pair<Boolean, String> {
            val supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: listOf("arm64-v8a", "armeabi-v7a")
            val devicePrimary = supportedAbis.firstOrNull() ?: "unknown"

            return when (machineCode) {
                183 -> { // EM_AARCH64 (ARM 64-bit)
                    val compatible = supportedAbis.contains("arm64-v8a")
                    Pair(
                        compatible,
                        if (compatible) "Compatible (ARM64 / arm64-v8a supported by device: $devicePrimary)"
                        else "Incompatible (Requires ARM64 device, current: ${supportedAbis.joinToString()})"
                    )
                }
                40 -> { // EM_ARM (ARM 32-bit)
                    val compatible = supportedAbis.contains("armeabi-v7a") || supportedAbis.contains("armeabi")
                    Pair(
                        compatible,
                        if (compatible) "Compatible (ARM 32-bit / armeabi-v7a supported by device: $devicePrimary)"
                        else "Incompatible (Requires 32-bit ARM, current: ${supportedAbis.joinToString()})"
                    )
                }
                62 -> { // EM_X86_64 (x86 64-bit)
                    val compatible = supportedAbis.contains("x86_64")
                    Pair(
                        compatible,
                        if (compatible) "Compatible (x86_64 supported by emulator/device: $devicePrimary)"
                        else "Incompatible (Requires x86_64 architecture, current: ${supportedAbis.joinToString()})"
                    )
                }
                3 -> { // EM_386 (x86 32-bit)
                    val compatible = supportedAbis.contains("x86")
                    Pair(
                        compatible,
                        if (compatible) "Compatible (x86 32-bit supported by emulator/device: $devicePrimary)"
                        else "Incompatible (Requires x86 architecture, current: ${supportedAbis.joinToString()})"
                    )
                }
                243 -> { // EM_RISCV
                    val compatible = supportedAbis.contains("riscv64")
                    Pair(
                        compatible,
                        if (compatible) "Compatible (RISC-V 64-bit)"
                        else "Incompatible (Requires RISC-V device)"
                    )
                }
                else -> {
                    Pair(false, "Unknown ELF machine code: $machineCode")
                }
            }
        }
    }
}
