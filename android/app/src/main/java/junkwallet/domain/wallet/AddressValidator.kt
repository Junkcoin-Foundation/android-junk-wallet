package junkwallet.domain.wallet

import junkwallet.domain.model.AddressType
import junkwallet.domain.model.JunkcoinParams
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddressValidator @Inject constructor() {

    companion object {
        fun getAddressTypeDescription(addressType: AddressType): String {
            return when (addressType) {
                AddressType.P2PKH -> "Legacy"
                AddressType.P2SH_P2WPKH -> "Wrapped SegWit"
                AddressType.P2WPKH -> "Native SegWit"
                AddressType.P2TR -> "Taproot"
            }
        }

        fun getAddressTypeInfo(addressType: AddressType): AddressTypeInfo {
            return when (addressType) {
                AddressType.P2PKH -> AddressTypeInfo(
                    type = addressType,
                    displayName = "Legacy (P2PKH)",
                    description = "Classic Junkcoin address format",
                    icon = "\uD83C\uDFDB\uFE0F",
                    feeLevel = FeeLevel.HIGH
                )
                AddressType.P2SH_P2WPKH -> AddressTypeInfo(
                    type = addressType,
                    displayName = "Wrapped SegWit",
                    description = "SegWit wrapped in P2SH",
                    icon = "\uD83D\uDD10",
                    feeLevel = FeeLevel.MEDIUM
                )
                AddressType.P2WPKH -> AddressTypeInfo(
                    type = addressType,
                    displayName = "Native SegWit",
                    description = "Modern address with lower fees",
                    icon = "⚡",
                    feeLevel = FeeLevel.LOW
                )
                AddressType.P2TR -> AddressTypeInfo(
                    type = addressType,
                    displayName = "Taproot",
                    description = "Latest format with privacy",
                    icon = "\uD83D\uDD12",
                    feeLevel = FeeLevel.LOWEST
                )
            }
        }
    }
    /**
     * Validate an address and return detailed information.
     */
    fun validate(address: String, network: JunkcoinParams): ValidationResult {
        if (address.isBlank()) {
            return ValidationResult(
                isValid = false,
                error = "Address cannot be empty"
            )
        }

        // Check for bech32/Bech32m addresses (SegWit/Taproot)
        if (address.contains("1") && address.length > 5) {
            val bech32Result = validateBech32Address(address, network)
            if (bech32Result.isValid) {
                return bech32Result
            }
        }

        // Check for Base58 addresses (P2PKH/P2SH)
        if (address.startsWith(network.addressPrefix) || address.startsWith(network.p2shPrefix)) {
            return validateBase58Address(address, network)
        }

        return ValidationResult(
            isValid = false,
            error = "Invalid address format"
        )
    }

    private fun validateBase58Address(address: String, network: JunkcoinParams): ValidationResult {
        if (address.length < 25 || address.length > 35) {
            return ValidationResult(isValid = false, error = "Invalid address length")
        }
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        if (!address.all { it in base58Chars }) {
            return ValidationResult(isValid = false, error = "Invalid Base58 character")
        }
        try {
            val decoded = base58Decode(address)
            if (decoded.size != 25) {
                return ValidationResult(isValid = false, error = "Invalid decoded length")
            }
            val data = decoded.copyOfRange(0, 21)
            val checksum = decoded.copyOfRange(21, 25)
            val calculatedChecksum = calculateChecksum(data)
            if (!checksum.contentEquals(calculatedChecksum)) {
                return ValidationResult(isValid = false, error = "Invalid checksum")
            }
            val prefix = decoded[0].toInt() and 0xFF
            val addressType = when (prefix) {
                network.pubKeyHash -> AddressType.P2PKH
                network.scriptHash -> AddressType.P2SH_P2WPKH
                else -> return ValidationResult(isValid = false, error = "Unknown address prefix")
            }
            return ValidationResult(isValid = true, addressType = addressType, network = network.name)
        } catch (e: Exception) {
            return ValidationResult(isValid = false, error = "Invalid Base58 encoding")
        }
    }

    private fun validateBech32Address(address: String, network: JunkcoinParams): ValidationResult {
        val bech32Encoder = Bech32Encoder()
        val decoded = bech32Encoder.decode(address)
            ?: return ValidationResult(isValid = false, error = "Invalid Bech32 encoding")
        val (hrp, program) = decoded
        if (hrp != network.bech32) {
            return ValidationResult(isValid = false, error = "Invalid network prefix")
        }
        val witnessVersion = bech32Encoder.getWitnessVersion(address)
        return when (witnessVersion) {
            0 -> {
                if (program.size != 20 && program.size != 32) {
                    return ValidationResult(isValid = false, error = "Invalid SegWit program length")
                }
                ValidationResult(isValid = true, addressType = AddressType.P2WPKH, network = network.name, witnessVersion = witnessVersion)
            }
            1 -> {
                if (program.size != 32) {
                    return ValidationResult(isValid = false, error = "Invalid Taproot program length")
                }
                ValidationResult(isValid = true, addressType = AddressType.P2TR, network = network.name, witnessVersion = witnessVersion)
            }
            else -> ValidationResult(isValid = false, error = "Unsupported witness version: $witnessVersion")
        }
    }

    private fun calculateChecksum(data: ByteArray): ByteArray {
        val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(data)
        val doubleSha256 = java.security.MessageDigest.getInstance("SHA-256").digest(sha256)
        return doubleSha256.copyOfRange(0, 4)
    }

    private fun base58Decode(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        for (c in input) {
            val index = alphabet.indexOf(c)
            if (index < 0) throw IllegalArgumentException("Invalid Base58 character: $c")
            num = num.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(index.toLong()))
        }
        val bytes = num.toByteArray()
        val stripped = if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        val result = mutableListOf<Byte>()
        for (c in input) {
            if (c == '1') result.add(0) else break
        }
        result.addAll(stripped.toList())
        return result.toByteArray()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val addressType: AddressType? = null,
        val network: String? = null,
        val witnessVersion: Int? = null,
        val error: String? = null
    )

    data class AddressTypeInfo(
        val type: AddressType,
        val displayName: String,
        val description: String,
        val icon: String,
        val feeLevel: FeeLevel
    )

    enum class FeeLevel {
        HIGH,    // P2PKH - ~148 bytes input
        MEDIUM,  // P2SH-P2WPKH - ~148 bytes input + witness
        LOW,     // P2WPKH - ~68 bytes witness
        LOWEST   // P2TR - ~65 bytes witness
    }
}
