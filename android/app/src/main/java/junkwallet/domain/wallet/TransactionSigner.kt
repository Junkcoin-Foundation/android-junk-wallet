package junkwallet.domain.wallet

import fr.acinq.secp256k1.Secp256k1
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Security
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionSigner @Inject constructor(
    private val crypto: JunkcoinCrypto
) {
    companion object {
        private val secp256k1 = Secp256k1.get()

        init {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
        const val SIGHASH_ALL = 0x01
        const val SIGHASH_DEFAULT = 0x00
    }

    fun sign(
        keyPair: KeyPair,
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        utxoValue: Long,
        addressType: junkwallet.domain.model.AddressType,
        hashType: Int = SIGHASH_ALL,
        allUtxoValues: List<Long> = listOf(utxoValue),
        allScriptPubKeys: List<ByteArray> = emptyList()
    ): ByteArray {
        return when (addressType) {
            junkwallet.domain.model.AddressType.P2TR -> {
                signTaproot(keyPair, transaction, inputIndex, utxoValue, allUtxoValues, allScriptPubKeys, hashType)
            }
            junkwallet.domain.model.AddressType.P2WPKH -> {
                signSegwit(keyPair, transaction, inputIndex, utxoValue, hashType)
            }
            else -> {
                signLegacy(keyPair, transaction, inputIndex, utxoValue, hashType)
            }
        }
    }

    /**
     * Sign a P2TR input using BIP-340 Schnorr with BIP-341 sighash.
     */
    private fun signTaproot(
        keyPair: KeyPair,
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        utxoValue: Long,
        allUtxoValues: List<Long>,
        allScriptPubKeys: List<ByteArray>,
        hashType: Int
    ): ByteArray {
        val privateKeyBytes = crypto.getPrivateKeyBytes(keyPair.private)
        val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
        val xOnlyPubKey = compressedPubKey.copyOfRange(1, 33)

        // If scriptPubKeys not provided, build from the x-only pubkey
        val scriptPubKeys = if (allScriptPubKeys.isEmpty()) {
            List(transaction.inputs.size) { crypto.createP2TRScript(xOnlyPubKey) }
        } else {
            allScriptPubKeys
        }

        // BIP-341 sighash (SIGHASH_DEFAULT = 0x00)
        val sighash = calculateTaprootSighash(
            transaction = transaction,
            inputIndex = inputIndex,
            allUtxoValues = allUtxoValues,
            allScriptPubKeys = scriptPubKeys,
            hashType = hashType
        )

        // BIP-341 taproot_tweak_seckey
        val tweakedPriv = crypto.taprootTweakPrivateKey(privateKeyBytes)

        // BIP-340 Schnorr signature (64 bytes, no sighash type byte for SIGHASH_DEFAULT)
        val schnorrSig = crypto.signSchnorr(sighash, tweakedPriv)

        return if (hashType == SIGHASH_DEFAULT) {
            schnorrSig  // 64 bytes, no suffix
        } else {
            schnorrSig + hashType.toByte()  // 65 bytes with sighash type
        }
    }

    /**
     * Sign a P2WPKH input using BIP-143 sighash with ECDSA.
     */
    private fun signSegwit(
        keyPair: KeyPair,
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        utxoValue: Long,
        hashType: Int
    ): ByteArray {
        val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
        val pubkeyHash = MessageDigest.getInstance("RIPEMD160").digest(
            MessageDigest.getInstance("SHA-256").digest(compressedPubKey)
        )
        // BIP-143 scriptCode for P2WPKH is the P2PKH script, NOT the witness program
        val scriptCode = byteArrayOf(0x76, 0xa9.toByte(), 0x14) + pubkeyHash + byteArrayOf(0x88.toByte(), 0xac.toByte())
        val sighash = calculateSegwitSighash(transaction, inputIndex, scriptCode, utxoValue, hashType)

        val privateKey = keyPair.private as java.security.interfaces.ECPrivateKey
        val rawSignature = signHash(sighash, privateKey)
        val signature = derEncodeSignature(rawSignature)

        return signature + hashType.toByte()
    }

    /**
     * Sign a P2PKH or P2SH-P2WPKH input using legacy sighash with ECDSA.
     */
    private fun signLegacy(
        keyPair: KeyPair,
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        utxoValue: Long,
        hashType: Int
    ): ByteArray {
        val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
        val pubkeyHash = MessageDigest.getInstance("RIPEMD160").digest(
            MessageDigest.getInstance("SHA-256").digest(compressedPubKey)
        )
        val scriptCode = ByteArray(25)
        scriptCode[0] = 0x76.toByte()
        scriptCode[1] = 0xA9.toByte()
        scriptCode[2] = 0x14
        System.arraycopy(pubkeyHash, 0, scriptCode, 3, 20)
        scriptCode[23] = 0x88.toByte()
        scriptCode[24] = 0xAC.toByte()

        val sighash = calculateLegacySighash(transaction, inputIndex, scriptCode, utxoValue, hashType)

        val privateKey = keyPair.private as java.security.interfaces.ECPrivateKey
        val rawSignature = signHash(sighash, privateKey)
        val signature = derEncodeSignature(rawSignature)

        return signature + hashType.toByte()
    }

    // ========================
    // BIP-341 Taproot Sighash
    // ========================

    /**
     * BIP-341 Taproot sighash for key-path spending.
     * Sub-hashes use single SHA256; final hash uses TaggedHash("TapSighash", msg).
     */
    private fun calculateTaprootSighash(
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        allUtxoValues: List<Long>,
        allScriptPubKeys: List<ByteArray>,
        hashType: Int = SIGHASH_DEFAULT
    ): ByteArray {
        val isAnyoneCanPay = hashType and 0x80 != 0
        val baseType = hashType and 0x1f

        val msg = ByteArrayOutputStream()
        msg.write(0x00)                         // sighash epoch
        msg.write(hashType and 0xFF)            // hash_type
        msg.write(intToLittleEndian(transaction.version))
        msg.write(intToLittleEndian(transaction.lockTime.toInt()))

        if (!isAnyoneCanPay) {
            // sha_prevouts: single SHA256 of all outpoints
            val prevouts = ByteArrayOutputStream()
            for (input in transaction.inputs) {
                prevouts.write(input.txHash.reversedArray())
                prevouts.write(intToLittleEndian(input.outputIndex))
            }
            msg.write(sha256(prevouts.toByteArray()))

            // sha_amounts: single SHA256 of all input amounts
            val amountsBos = ByteArrayOutputStream()
            allUtxoValues.forEach { amountsBos.write(longToLittleEndian(it)) }
            msg.write(sha256(amountsBos.toByteArray()))

            // sha_scriptpubkeys: single SHA256 of all prevout scriptPubKeys
            val scriptsBos = ByteArrayOutputStream()
            allScriptPubKeys.forEach { spk ->
                writeVarInt(scriptsBos, spk.size)
                scriptsBos.write(spk)
            }
            msg.write(sha256(scriptsBos.toByteArray()))

            // sha_sequences: single SHA256 of all nSequences
            val seqBos = ByteArrayOutputStream()
            transaction.inputs.forEach { seqBos.write(intToLittleEndian(it.sequence.toInt())) }
            msg.write(sha256(seqBos.toByteArray()))
        }

        // sha_outputs (SIGHASH_ALL / SIGHASH_DEFAULT)
        if (baseType == 0 || baseType == 1) {
            val outputsBos = ByteArrayOutputStream()
            for (output in transaction.outputs) {
                outputsBos.write(longToLittleEndian(output.amount))
                writeVarInt(outputsBos, output.scriptPubKey.size)
                outputsBos.write(output.scriptPubKey)
            }
            msg.write(sha256(outputsBos.toByteArray()))
        }

        // spend_type: 0 = key-path, no annex
        msg.write(0x00)

        if (isAnyoneCanPay) {
            val input = transaction.inputs[inputIndex]
            msg.write(input.txHash.reversedArray())
            msg.write(intToLittleEndian(input.outputIndex))
            msg.write(longToLittleEndian(allUtxoValues[inputIndex]))
            writeVarInt(msg, allScriptPubKeys[inputIndex].size)
            msg.write(allScriptPubKeys[inputIndex])
            msg.write(intToLittleEndian(input.sequence.toInt()))
        } else {
            msg.write(intToLittleEndian(inputIndex))
        }

        // sha_single_output for SIGHASH_SINGLE
        if (baseType == 3) {
            val singleOut = ByteArrayOutputStream()
            val output = transaction.outputs[inputIndex]
            singleOut.write(longToLittleEndian(output.amount))
            writeVarInt(singleOut, output.scriptPubKey.size)
            singleOut.write(output.scriptPubKey)
            msg.write(sha256(singleOut.toByteArray()))
        }

        return crypto.taggedHash("TapSighash", msg.toByteArray())
    }

    // ========================
    // BIP-143 SegWit Sighash
    // ========================

    private fun calculateSegwitSighash(
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        scriptCode: ByteArray,
        value: Long,
        hashType: Int
    ): ByteArray {
        val buffer = mutableListOf<Byte>()
        // BIP-143 starts with nVersion (NO epoch - epoch is BIP-341 only)
        buffer.addAll(intToBytes(transaction.version.toInt()).toList())

        // hashPrevouts
        val prevouts = mutableListOf<Byte>()
        for (input in transaction.inputs) {
            prevouts.addAll(input.txHash.reversedArray().toList())
            prevouts.addAll(intToBytes(input.outputIndex).toList())
        }
        val hashPrevouts = doubleHash(prevouts.toByteArray())
        buffer.addAll(hashPrevouts.toList())

        // hashSequence
        val sequences = mutableListOf<Byte>()
        for (input in transaction.inputs) {
            sequences.addAll(intToBytes(input.sequence.toInt()).toList())
        }
        val hashSequence = doubleHash(sequences.toByteArray())
        buffer.addAll(hashSequence.toList())

        // outpoint
        buffer.addAll(transaction.inputs[inputIndex].txHash.reversedArray().toList())
        buffer.addAll(intToBytes(transaction.inputs[inputIndex].outputIndex).toList())

        // scriptCode
        buffer.addAll(toVarInt(scriptCode.size.toLong()).toList())
        buffer.addAll(scriptCode.toList())

        // value
        buffer.addAll(longToBytes(value).toList())

        // nSequence
        buffer.addAll(intToBytes(transaction.inputs[inputIndex].sequence.toInt()).toList())

        // hashOutputs
        val outputsData = mutableListOf<Byte>()
        for (output in transaction.outputs) {
            outputsData.addAll(longToBytes(output.amount).toList())
            outputsData.addAll(toVarInt(output.scriptPubKey.size.toLong()).toList())
            outputsData.addAll(output.scriptPubKey.toList())
        }
        val hashOutputs = doubleHash(outputsData.toByteArray())
        buffer.addAll(hashOutputs.toList())

        // locktime and sighash type
        buffer.addAll(intToBytes(transaction.lockTime.toInt()).toList())
        buffer.addAll(intToBytes(hashType).toList())

        val data = buffer.toByteArray()
        return doubleHash(data)
    }

    // ========================
    // Legacy Sighash
    // ========================

    private fun calculateLegacySighash(
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        scriptCode: ByteArray,
        value: Long,
        hashType: Int
    ): ByteArray {
        val buffer = mutableListOf<Byte>()
        buffer.addAll(intToBytes(transaction.version.toInt()).toList())
        buffer.addAll(toVarInt(transaction.inputs.size.toLong()).toList())
        for ((index, input) in transaction.inputs.withIndex()) {
            buffer.addAll(input.txHash.reversedArray().toList())
            buffer.addAll(intToBytes(input.outputIndex).toList())
            if (index == inputIndex) {
                buffer.addAll(toVarInt(scriptCode.size.toLong()).toList())
                buffer.addAll(scriptCode.toList())
            } else {
                buffer.addAll(toVarInt(0).toList())
            }
            buffer.addAll(intToBytes(input.sequence.toInt()).toList())
        }
        buffer.addAll(toVarInt(transaction.outputs.size.toLong()).toList())
        for (output in transaction.outputs) {
            buffer.addAll(longToBytes(output.amount).toList())
            buffer.addAll(toVarInt(output.scriptPubKey.size.toLong()).toList())
            buffer.addAll(output.scriptPubKey.toList())
        }
        buffer.addAll(intToBytes(transaction.lockTime.toInt()).toList())
        buffer.addAll(intToBytes(hashType).toList())

        val data = buffer.toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(data)
        return MessageDigest.getInstance("SHA-256").digest(sha)
    }

    // ========================
    // ECDSA Signing
    // ========================

    private fun signHash(hash: ByteArray, privateKey: java.security.interfaces.ECPrivateKey): ByteArray {
        val signer = org.bouncycastle.crypto.signers.ECDSASigner(
            org.bouncycastle.crypto.signers.HMacDSAKCalculator(org.bouncycastle.crypto.digests.SHA256Digest())
        )
        val ecSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1")
        val g = ecSpec.getG()
        val n = ecSpec.getN()
        val h = ecSpec.getH()
        val curve = ecSpec.getCurve()
        val domainParams = org.bouncycastle.crypto.params.ECDomainParameters(curve, g, n, h)
        val privKey = org.bouncycastle.crypto.params.ECPrivateKeyParameters(privateKey.s, domainParams)
        signer.init(true, privKey)
        val components = signer.generateSignature(hash)

        var r = components[0]
        var s = components[1]
        val halfCurveOrder = n.shiftRight(1)
        if (s > halfCurveOrder) {
            s = n.subtract(s)
        }
        return padTo32Bytes(r) + padTo32Bytes(s)
    }

    // ========================
    // Helper Functions
    // ========================

    private fun padTo32Bytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }

    private fun derEncodeSignature(rawSig: ByteArray): ByteArray {
        val r = BigInteger(1, rawSig.copyOfRange(0, 32))
        val s = BigInteger(1, rawSig.copyOfRange(32, 64))

        val rBytes = padTo32Bytes(r)
        val sBytes = padTo32Bytes(s)

        val rEncoded = if (rBytes[0].toInt() and 0x80 != 0) {
            byteArrayOf(0x00) + rBytes
        } else {
            rBytes
        }

        val sEncoded = if (sBytes[0].toInt() and 0x80 != 0) {
            byteArrayOf(0x00) + sBytes
        } else {
            sBytes
        }

        val totalLen = 2 + rEncoded.size + 2 + sEncoded.size
        return byteArrayOf(0x30, totalLen.toByte(), 0x02, rEncoded.size.toByte()) +
            rEncoded + byteArrayOf(0x02, sEncoded.size.toByte()) + sEncoded
    }

    private fun createP2WPKHScript(pubkeyHash: ByteArray): ByteArray {
        return byteArrayOf(0x00, 0x14) + pubkeyHash
    }

    private fun doubleHash(data: ByteArray): ByteArray {
        val sha1 = MessageDigest.getInstance("SHA-256").digest(data)
        return MessageDigest.getInstance("SHA-256").digest(sha1)
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun longToBytes(value: Long): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 32) and 0xFF).toByte(),
            ((value shr 40) and 0xFF).toByte(),
            ((value shr 48) and 0xFF).toByte(),
            ((value shr 56) and 0xFF).toByte()
        )
    }

    private fun longToLittleEndian(value: Long): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
    }

    private fun toVarInt(value: Long): ByteArray {
        return when {
            value < 0xFD -> byteArrayOf(value.toByte())
            value <= 0xFFFF -> byteArrayOf(0xFD.toByte(), (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
            else -> byteArrayOf(
                0xFE.toByte(),
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte()
            )
        }
    }

    private fun writeVarInt(stream: ByteArrayOutputStream, value: Int) {
        when {
            value < 0xFD -> stream.write(value)
            value <= 0xFFFF -> {
                stream.write(0xFD)
                stream.write(value and 0xFF)
                stream.write((value shr 8) and 0xFF)
            }
            else -> {
                stream.write(0xFE)
                stream.write(intToLittleEndian(value))
            }
        }
    }
}
