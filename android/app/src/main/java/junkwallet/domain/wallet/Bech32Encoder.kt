package junkwallet.domain.wallet

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bech32 and Bech32m encoding/decoding for SegWit and Taproot addresses.
 * Implements BIP-173 (Bech32) and BIP-350 (Bech32m).
 */
@Singleton
class Bech32Encoder @Inject constructor() {

    companion object {
        const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        const val BECH32M_CONST = 0x2bc830a3

        // Witness version constants
        const val WITNESS_V0 = 0    // P2WPKH (SegWit)
        const val WITNESS_V1 = 1    // P2TR (Taproot)
    }

    /**
     * Encode a witness program as a Bech32 address.
     *
     * @param hrp Human-readable part (e.g., "jc" for mainnet, "tjc" for testnet)
     * @param witnessVersion Witness version (0 for SegWit, 1 for Taproot)
     * @param program The witness program (20 bytes for P2WPKH, 32 bytes for P2TR)
     * @return Bech32 encoded address
     */
    fun encode(hrp: String, witnessVersion: Int, program: ByteArray): String {
        val data = mutableListOf<Int>()
        data.add(witnessVersion)

        // Convert 8-bit program to 5-bit groups
        val converted = convertBits(program.map { it.toInt() and 0xFF }, 8, 5, true)
        data.addAll(converted)

        // Compute checksum: polymod(hrp_expand(hrp) + data + [0,0,0,0,0,0])
        val polymod = polymod(hrpExpand(hrp) + data + listOf(0, 0, 0, 0, 0, 0)) xor (if (witnessVersion == 0) 1 else BECH32M_CONST)
        val checksum = IntArray(6)
        for (i in 0 until 6) {
            checksum[i] = (polymod shr 5 * (5 - i)) and 0x1f
        }

        // Build the address
        val dataChars = data.map { BECH32_CHARSET[it] }
        val checksumChars = checksum.map { BECH32_CHARSET[it] }

        return hrp + "1" + (dataChars + checksumChars).joinToString("")
    }

    /**
     * Decode a Bech32 address.
     *
     * @param address Bech32 encoded address
     * @return Pair of (hrp, witness program) or null if invalid
     */
    fun decode(address: String): Pair<String, ByteArray>? {
        // Find the separator
        val separatorIndex = address.lastIndexOf('1')
        if (separatorIndex < 1 || separatorIndex + 7 > address.length) {
            return null
        }

        val hrp = address.substring(0, separatorIndex)
        val dataPart = address.substring(separatorIndex + 1)

        // Decode data characters
        val data = dataPart.map { c ->
            val index = BECH32_CHARSET.indexOf(c)
            if (index < 0) return null
            index
        }.toIntArray()

        // Verify checksum: polymod(hrp_expand(hrp) + data) == 1 for bech32, == BECH32M_CONST for bech32m
        // data already includes the 6 checksum characters from the address string
        val polymod = polymod(hrpExpand(hrp) + data.toList())
        val bech32m = polymod == BECH32M_CONST

        if (polymod != 1 && polymod != BECH32M_CONST) {
            return null
        }

        // Extract witness version and program
        val witnessVersion = data[0]
        val programData = data.dropLast(6).drop(1)

        // Convert 5-bit to 8-bit
        val program = convertBits(programData, 5, 8, false) ?: return null

        return Pair(hrp, ByteArray(program.size) { program[it].toByte() })
    }

    /**
     * Validate a Bech32 address for a specific network.
     *
     * @param address Address to validate
     * @param expectedHrp Expected human-readable part
     * @return true if valid
     */
    fun isValid(address: String, expectedHrp: String): Boolean {
        val result = decode(address) ?: return false
        val (hrp, program) = result

        if (hrp != expectedHrp) return false

        // Check program length
        return when (program.size) {
            20 -> true // P2WPKH (SegWit v0)
            32 -> true // P2TR (Taproot v1) or P2WSH (SegWit v0)
            else -> false
        }
    }

    /**
     * Convert between bit groups.
     *
     * @param data Input data
     * @param fromBits Source bits per element
     * @param toBits Target bits per element
     * @param pad Whether to pad the result
     * @return Converted data or null on error
     */
    private fun convertBits(data: List<Int>, fromBits: Int, toBits: Int, pad: Boolean): List<Int> {
        val result = mutableListOf<Int>()
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1

        for (value in data) {
            if (value < 0 || (value shr fromBits) != 0) {
                return emptyList()
            }
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxv)
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add((acc shl (toBits - bits)) and maxv)
            }
        } else if (bits >= fromBits || (acc shl (toBits - bits)) and maxv != 0) {
            return emptyList()
        }

        return result
    }

    /**
     * Compute the Bech32/Bech32m polymod value.
     */
    private fun polymod(values: List<Int>): Int {
        val GEN = intArrayOf(
            0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3
        )
        var chk = 1
        for (v in values) {
            val b = (chk shr 25) and 0x1f
            chk = (chk and 0x1ffffff) shl 5 or v
            for (i in 0 until 5) {
                chk = chk xor (if ((b shr i) and 1 == 1) GEN[i] else 0)
            }
        }
        return chk
    }

    /**
     * Expand the HRP into values for checksum computation (BIP-173).
     */
    private fun hrpExpand(hrp: String): List<Int> {
        val result = mutableListOf<Int>()
        for (c in hrp) {
            result.add(c.code shr 5)
        }
        result.add(0)
        for (c in hrp) {
            result.add(c.code and 31)
        }
        return result
    }

    /**
     * Get the witness version from an address.
     */
    fun getWitnessVersion(address: String): Int {
        val separatorIndex = address.lastIndexOf('1')
        if (separatorIndex < 0) return -1
        return BECH32_CHARSET.indexOf(address[separatorIndex + 1])
    }

    /**
     * Get the witness program from an address.
     */
    fun getWitnessProgram(address: String): ByteArray? {
        return decode(address)?.second
    }
}
