package com.example

import com.example.data.model.ElfInfo
import com.example.data.parser.ElfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ElfParserTest {

    @Test
    fun testNonElfFileHandling() {
        val tempFile = File.createTempFile("test_script", ".sh")
        try {
            tempFile.writeText("#!/bin/sh\necho 'hello world'\n")
            val elf = ElfParser.parse(tempFile)
            assertFalse(elf.isValidElf)
            assertTrue(elf.error?.contains("Shell/Script") == true)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSyntheticElfHeaderParsing() {
        val tempFile = File.createTempFile("synth_elf", ".bin")
        try {
            // Write standard 64-bit little-endian ELF header
            val header = ByteArray(64)
            // Magic: \x7f E L F
            header[0] = 0x7F.toByte()
            header[1] = 'E'.code.toByte()
            header[2] = 'L'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = 2 // 64-bit (ELFCLASS64)
            header[5] = 1 // Little-endian (ELFDATA2LSB)
            header[6] = 1 // Version

            // e_type: ET_DYN (3) -> bytes 16-17
            header[16] = 3
            header[17] = 0

            // e_machine: EM_AARCH64 (183 = 0x00B7) -> bytes 18-19
            header[18] = 0xB7.toByte()
            header[19] = 0x00

            tempFile.writeBytes(header)

            val elf = ElfParser.parse(tempFile)
            assertTrue(elf.isValidElf)
            assertEquals("64-bit", elf.bitness)
            assertEquals("ARM64 / AArch64 (ARMv8-A)", elf.machineType)
            assertEquals(183, elf.machineCode)
            assertTrue(elf.elfType.contains("ET_DYN"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testArchitectureCompatibility() {
        val (arm64Compat, arm64Details) = ElfInfo.checkCompatibility(183)
        assertNotNull(arm64Details)

        val (arm32Compat, arm32Details) = ElfInfo.checkCompatibility(40)
        assertNotNull(arm32Details)

        val (unknownCompat, unknownDetails) = ElfInfo.checkCompatibility(9999)
        assertFalse(unknownCompat)
        assertTrue(unknownDetails.contains("Unknown"))
    }
}
