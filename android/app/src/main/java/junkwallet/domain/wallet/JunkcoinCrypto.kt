package junkwallet.domain.wallet

import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JunkcoinCrypto @Inject constructor(
    private val bech32Encoder: Bech32Encoder
) {
    companion object {
        private val bouncyCastleProvider: BouncyCastleProvider by lazy {
            BouncyCastleProvider()
        }

        init {
            // Android ships a stripped BouncyCastle that lacks EC/ECDSA.
            // We must remove it and re-insert the full library version at position 1
            // so it takes priority over the platform's built-in provider.
            val existingProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (existingProvider == null || existingProvider !is BouncyCastleProvider) {
                if (existingProvider != null) {
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                }
                Security.insertProviderAt(bouncyCastleProvider, 1)
            }
        }
    }

    private val ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
    private val secureRandom = SecureRandom()

    /**
     * Generate a new wallet and return the keypair.
     */
    fun generateWallet(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", bouncyCastleProvider)
        keyPairGenerator.initialize(ECGenParameterSpec("secp256k1"), secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Generate a new wallet with network params and return WalletKeyPair.
     */
    fun generateWallet(network: junkwallet.domain.model.JunkcoinParams): WalletKeyPair {
        val keyPair = generateWallet()
        val compressedPubKey = getCompressedPublicKey(keyPair.public)
        val privateKeyBytes = getPrivateKeyBytes(keyPair.private)
        val address = createP2PKHAddress(compressedPubKey, network)
        val wif = privateKeyToWif(privateKeyBytes, network)
        val pubKeyHex = compressedPubKey.joinToString("") { "%02x".format(it) }
        return WalletKeyPair(wif = wif, address = address, publicKey = pubKeyHex)
    }

    /**
     * Extract raw 32-byte private key scalar from an EC private key.
     */
    fun getPrivateKeyBytes(privateKey: java.security.PrivateKey): ByteArray {
        val ecPrivateKey = privateKey as org.bouncycastle.jce.interfaces.ECPrivateKey
        val d = ecPrivateKey.d.toByteArray()
        // BigInteger.toByteArray() may have a leading 0x00 sign byte; strip it and left-pad to 32
        return if (d.size > 32) d.copyOfRange(d.size - 32, d.size)
               else ByteArray(32 - d.size) + d
    }

    /**
     * Encode a 32-byte private key as WIF (Wallet Import Format).
     * Format: [version byte] + [32-byte key] + [0x01 compression flag] + [4-byte checksum]
     * then Base58-encoded.
     */
    fun privateKeyToWif(privateKeyBytes: ByteArray, network: junkwallet.domain.model.JunkcoinParams): String {
        // 1-byte version + 32-byte key + 1-byte compression flag = 34 bytes
        val payload = ByteArray(34)
        payload[0] = network.wif.toByte()
        System.arraycopy(privateKeyBytes, 0, payload, 1, 32)
        payload[33] = 0x01  // compressed public key flag
        val checksum = calculateChecksum(payload)
        val full = payload + checksum
        return base58Encode(full)
    }

    /**
     * @deprecated Use privateKeyToWif(privateKeyBytes, network) instead.
     */
    fun getWif(compressedPubKey: ByteArray, network: junkwallet.domain.model.JunkcoinParams): String {
        // Kept for binary compatibility only — callers should pass the private key bytes.
        return "WIF_REQUIRES_PRIVATE_KEY"
    }

    /**
     * Get public key bytes (uncompressed, 65 bytes).
     */
    fun getPublicKeyBytes(publicKey: java.security.PublicKey): ByteArray {
        val ecPublicKey = publicKey as java.security.interfaces.ECPublicKey
        val x = ecPublicKey.w.affineX
        val y = ecPublicKey.w.affineY
        val xBytes = x.toByteArray().let {
            if (it.size > 32) it.copyOfRange(it.size - 32, it.size)
            else ByteArray(32 - it.size) + it
        }
        val yBytes = y.toByteArray().let {
            if (it.size > 32) it.copyOfRange(it.size - 32, it.size)
            else ByteArray(32 - it.size) + it
        }
        return byteArrayOf(0x04) + xBytes + yBytes
    }

    /**
     * Get compressed public key (33 bytes).
     */
    fun getCompressedPublicKey(publicKey: java.security.PublicKey): ByteArray {
        val ecPublicKey = publicKey as java.security.interfaces.ECPublicKey
        val x = ecPublicKey.w.affineX
        val y = ecPublicKey.w.affineY

        val prefix = if (y.testBit(0)) 0x03.toByte() else 0x02.toByte()

        val xBytes = x.toByteArray().let {
            if (it.size > 32) it.copyOfRange(it.size - 32, it.size)
            else ByteArray(32 - it.size) + it
        }

        return byteArrayOf(prefix) + xBytes
    }

    /**
     * Create P2PKH address (legacy).
     */
    fun createP2PKHAddress(compressedPubKey: ByteArray, network: junkwallet.domain.model.JunkcoinParams): String {
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(compressedPubKey)
        val ripemd160Hash = MessageDigest.getInstance("RIPEMD160").digest(sha256Hash)

        val networkByte = ByteArray(1)
        networkByte[0] = network.pubKeyHash.toByte()

        val fullHash = ByteArray(21)
        System.arraycopy(networkByte, 0, fullHash, 0, 1)
        System.arraycopy(ripemd160Hash, 0, fullHash, 1, 20)

        val checksum = calculateChecksum(fullHash)
        val addressBytes = ByteArray(25)
        System.arraycopy(fullHash, 0, addressBytes, 0, 21)
        System.arraycopy(checksum, 0, addressBytes, 21, 4)

        return base58Encode(addressBytes)
    }

    /**
     * Create P2SH-P2WPKH address (wrapped SegWit).
     */
    fun createP2SH_P2WPKHAddress(compressedPubKey: ByteArray, network: junkwallet.domain.model.JunkcoinParams): String {
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(compressedPubKey)
        val pubkeyHash = MessageDigest.getInstance("RIPEMD160").digest(sha256Hash)

        // Create witness script: OP_0 <20-byte-key-hash>
        val witnessScript = ByteArray(22)
        witnessScript[0] = 0x00 // OP_0
        witnessScript[1] = 0x14 // 20 bytes push
        System.arraycopy(pubkeyHash, 0, witnessScript, 2, 20)

        // Hash the witness script
        val scriptHash = MessageDigest.getInstance("SHA-256").digest(witnessScript)
        val scriptHashRipemd = MessageDigest.getInstance("RIPEMD160").digest(scriptHash)

        // Create P2SH address
        val networkByte = ByteArray(1)
        networkByte[0] = network.scriptHash.toByte()

        val fullHash = ByteArray(21)
        System.arraycopy(networkByte, 0, fullHash, 0, 1)
        System.arraycopy(scriptHashRipemd, 0, fullHash, 1, 20)

        val checksum = calculateChecksum(fullHash)
        val addressBytes = ByteArray(25)
        System.arraycopy(fullHash, 0, addressBytes, 0, 21)
        System.arraycopy(checksum, 0, addressBytes, 21, 4)

        return base58Encode(addressBytes)
    }

    /**
     * Create native SegWit P2WPKH address (bech32).
     */
    fun createP2WPKHAddress(compressedPubKey: ByteArray, network: junkwallet.domain.model.JunkcoinParams): String {
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(compressedPubKey)
        val pubkeyHash = MessageDigest.getInstance("RIPEMD160").digest(sha256Hash)

        return bech32Encoder.encode(
            hrp = network.bech32,
            witnessVersion = Bech32Encoder.WITNESS_V0,
            program = pubkeyHash
        )
    }

    /**
     * Create Taproot P2TR address.
     */
    fun createP2TRAddress(compressedPubKey: ByteArray, network: junkwallet.domain.model.JunkcoinParams): String {
        // For Taproot, the x-only pubkey (32 bytes) is used
        val xOnlyPubKey = compressedPubKey.copyOfRange(1, 33)

        // For a basic Taproot output (no script tree), the output key is
        // internal_key = x-only pubkey
        // We'll use a simple implementation where the output key is the pubkey itself
        // In production, this should use tweaked key with BIP-340

        return bech32Encoder.encode(
            hrp = network.bech32,
            witnessVersion = Bech32Encoder.WITNESS_V1,
            program = xOnlyPubKey
        )
    }

    /**
     * Determine the address type from an address string.
     */
    fun detectAddressType(address: String, network: junkwallet.domain.model.JunkcoinParams): junkwallet.domain.model.AddressType? {
        // Check for bech32 addresses
        if (address.startsWith("${network.bech32}1")) {
            val witnessVersion = bech32Encoder.getWitnessVersion(address)
            val program = bech32Encoder.getWitnessProgram(address) ?: return null

            return when {
                witnessVersion == 0 && program.size == 20 -> junkwallet.domain.model.AddressType.P2WPKH
                witnessVersion == 1 && program.size == 32 -> junkwallet.domain.model.AddressType.P2TR
                else -> null
            }
        }

        // Check for P2SH addresses (starts with '3' on mainnet, '2' on testnet)
        if (address.startsWith(network.p2shPrefix)) {
            return junkwallet.domain.model.AddressType.P2SH_P2WPKH
        }

        // Check for P2PKH addresses (starts with '7' on mainnet, 'm'/'n' on testnet)
        if (address.startsWith(network.addressPrefix)) {
            return junkwallet.domain.model.AddressType.P2PKH
        }

        return null
    }

    /**
     * Validate an address for the given network.
     */
    fun validateAddress(address: String, network: junkwallet.domain.model.JunkcoinParams): Boolean {
        return detectAddressType(address, network) != null
    }

    /**
     * Create the locking script (scriptPubKey) for an address.
     */
    fun createLockingScript(address: String, network: junkwallet.domain.model.JunkcoinParams): ByteArray {
        val addressType = detectAddressType(address, network)
            ?: throw IllegalArgumentException("Invalid address")

        return when (addressType) {
            junkwallet.domain.model.AddressType.P2PKH -> {
                val decoded = base58Decode(address)
                val hash = decoded.copyOfRange(1, 21)
                // OP_DUP OP_HASH160 <20-byte-hash> OP_EQUALVERIFY OP_CHECKSIG
                val script = ByteArray(25)
                script[0] = 0x76.toByte() // OP_DUP
                script[1] = 0xA9.toByte() // OP_HASH160
                script[2] = 0x14       // 20 bytes push
                System.arraycopy(hash, 0, script, 3, 20)
                script[23] = 0x88.toByte() // OP_EQUALVERIFY
                script[24] = 0xAC.toByte() // OP_CHECKSIG
                script
            }

            junkwallet.domain.model.AddressType.P2SH_P2WPKH -> {
                val decoded = base58Decode(address)
                val hash = decoded.copyOfRange(1, 21)
                // OP_HASH160 <20-byte-hash> OP_EQUAL
                val script = ByteArray(23)
                script[0] = 0xA9.toByte() // OP_HASH160
                script[1] = 0x14       // 20 bytes push
                System.arraycopy(hash, 0, script, 2, 20)
                script[22] = 0x87.toByte() // OP_EQUAL
                script
            }

            junkwallet.domain.model.AddressType.P2WPKH -> {
                val program = bech32Encoder.getWitnessProgram(address)
                    ?: throw IllegalArgumentException("Invalid SegWit address")
                // OP_0 <20-byte-key-hash>
                val script = ByteArray(22)
                script[0] = 0x00 // OP_0
                script[1] = 0x14 // 20 bytes push
                System.arraycopy(program, 0, script, 2, 20)
                script
            }

            junkwallet.domain.model.AddressType.P2TR -> {
                val program = bech32Encoder.getWitnessProgram(address)
                    ?: throw IllegalArgumentException("Invalid Taproot address")
                // OP_1 <32-byte-key>
                val script = ByteArray(34)
                script[0] = 0x51 // OP_1
                script[1] = 0x20 // 32 bytes push
                System.arraycopy(program, 0, script, 2, 32)
                script
            }
        }
    }

    /**
     * Create a P2WPKH locking script for SegWit transactions.
     */
    fun createP2WPKHScript(pubkeyHash: ByteArray): ByteArray {
        val script = ByteArray(22)
        script[0] = 0x00 // OP_0
        script[1] = 0x14 // 20 bytes push
        System.arraycopy(pubkeyHash, 0, script, 2, 20)
        return script
    }

    /**
     * Create a P2TR locking script for Taproot transactions.
     */
    fun createP2TRScript(xOnlyPubKey: ByteArray): ByteArray {
        val script = ByteArray(34)
        script[0] = 0x51 // OP_1
        script[1] = 0x20 // 32 bytes push
        System.arraycopy(xOnlyPubKey, 0, script, 2, 32)
        return script
    }

    /**
     * Get the public key hash for an address.
     */
    fun getAddressHash(address: String, network: junkwallet.domain.model.JunkcoinParams): ByteArray {
        val addressType = detectAddressType(address, network)
            ?: throw IllegalArgumentException("Invalid address")

        return when (addressType) {
            junkwallet.domain.model.AddressType.P2PKH,
            junkwallet.domain.model.AddressType.P2SH_P2WPKH -> {
                val decoded = base58Decode(address)
                decoded.copyOfRange(1, 21)
            }

            junkwallet.domain.model.AddressType.P2WPKH,
            junkwallet.domain.model.AddressType.P2TR -> {
                bech32Encoder.getWitnessProgram(address)
                    ?: throw IllegalArgumentException("Invalid witness address")
            }
        }
    }

    fun calculateChecksum(data: ByteArray): ByteArray {
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(data)
        val doubleSha256 = MessageDigest.getInstance("SHA-256").digest(sha256Hash)
        return doubleSha256.copyOfRange(0, 4)
    }

    fun base58Encode(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger(1, data)
        val sb = StringBuilder()
        while (num > java.math.BigInteger.ZERO) {
            val (quotient, remainder) = num.divideAndRemainder(java.math.BigInteger.valueOf(58))
            sb.append(alphabet[remainder.toInt()])
            num = quotient
        }

        for (b in data) {
            if (b == 0.toByte()) {
                sb.append('1')
            } else {
                break
            }
        }

        return sb.reverse().toString()
    }

    fun base58Decode(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger.ZERO
        for (c in input) {
            val index = alphabet.indexOf(c)
            if (index < 0) throw IllegalArgumentException("Invalid Base58 character: $c")
            num = num.multiply(java.math.BigInteger.valueOf(58))
                .add(java.math.BigInteger.valueOf(index.toLong()))
        }

        val bytes = num.toByteArray()
        val stripped = if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes

        val result = mutableListOf<Byte>()
        for (c in input) {
            if (c == '1') {
                result.add(0)
            } else {
                break
            }
        }

        result.addAll(stripped.toList())
        return result.toByteArray()
    }

    fun decryptWif(encryptedWif: String, password: String): String {
        val combined = Base64.getDecoder().decode(encryptedWif)
        val salt = combined.copyOfRange(0, 16)
        val iv = combined.copyOfRange(16, 28)
        val ciphertext = combined.copyOfRange(28, combined.size)

        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 210000, 256)
        val tmp = keyFactory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(ciphertext)

        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Get a KeyPair from a WIF private key string.
     */
    fun getKeyPairFromWif(wif: String): java.security.KeyPair {
        val decoded = base58Decode(wif)
        val privateKeyBytes = decoded.copyOfRange(1, 33)

        val ecSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1")
        val ecPoint = ecSpec.curve.decodePoint(getCompressedPublicKeyFromPrivate(privateKeyBytes))
        val publicKeySpec = org.bouncycastle.jce.spec.ECPublicKeySpec(ecPoint, ecSpec)
        val privateKeySpec = org.bouncycastle.jce.spec.ECPrivateKeySpec(java.math.BigInteger(1, privateKeyBytes), ecSpec)

        val keyFactory = java.security.KeyFactory.getInstance("ECDSA", org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME)
        val publicKey = keyFactory.generatePublic(publicKeySpec)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)

        return java.security.KeyPair(publicKey, privateKey)
    }

    private fun getCompressedPublicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        val ecSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1")
        val point = ecSpec.g.multiply(java.math.BigInteger(1, privateKey))
        val encoded = point.getEncoded(true)
        return encoded
    }

    fun hash160(data: ByteArray): ByteArray {
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(data)
        return MessageDigest.getInstance("RIPEMD160").digest(sha256Hash)
    }

    fun hash256(data: ByteArray): ByteArray {
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(data)
        return MessageDigest.getInstance("SHA-256").digest(sha256Hash)
    }

    fun doubleHash(data: ByteArray): ByteArray = hash256(data)

    fun signMessage(message: String, keyPair: KeyPair): String {
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val messageHash = MessageDigest.getInstance("SHA-256").digest(messageBytes)
        val signature = java.security.Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(messageHash)
        val sigBytes = signature.sign()
        return Base64.getEncoder().encodeToString(sigBytes)
    }
}
