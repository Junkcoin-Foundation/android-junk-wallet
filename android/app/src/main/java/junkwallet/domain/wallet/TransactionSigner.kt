package junkwallet.domain.wallet

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
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
        init {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
        const val SIGHASH_ALL = 0x01
    }

    fun sign(
        keyPair: KeyPair,
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        utxoValue: Long,
        addressType: junkwallet.domain.model.AddressType,
        hashType: Int = SIGHASH_ALL
    ): ByteArray {
        val sighash = when (addressType) {
            junkwallet.domain.model.AddressType.P2WPKH -> {
                val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
                val pubkeyHash = MessageDigest.getInstance("RIPEMD160").digest(
                    MessageDigest.getInstance("SHA-256").digest(compressedPubKey)
                )
                val scriptCode = createP2WPKHScript(pubkeyHash)
                calculateSegwitSighash(transaction, inputIndex, scriptCode, utxoValue, hashType)
            }
            junkwallet.domain.model.AddressType.P2TR -> {
                val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
                val xOnlyPubKey = compressedPubKey.copyOfRange(1, 33)
                val scriptCode = createP2TRScript(xOnlyPubKey)
                calculateTaprootSighash(transaction, inputIndex, scriptCode, utxoValue, hashType)
            }
            else -> {
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
                calculateLegacySighash(transaction, inputIndex, scriptCode, utxoValue, hashType)
            }
        }

        val privateKey = keyPair.private as java.security.interfaces.ECPrivateKey
        val rawSignature = signHash(sighash, privateKey)
        val signature = derEncodeSignature(rawSignature)

        return when (addressType) {
            junkwallet.domain.model.AddressType.P2PKH,
            junkwallet.domain.model.AddressType.P2SH_P2WPKH -> {
                signature + hashType.toByte()
            }
            junkwallet.domain.model.AddressType.P2WPKH -> {
                signature + hashType.toByte()
            }
            else -> signature
        }
    }

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

    private fun calculateSegwitSighash(
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        scriptCode: ByteArray,
        value: Long,
        hashType: Int
    ): ByteArray {
        val buffer = mutableListOf<Byte>()
        buffer.addAll(intToBytes(0).toList()) // epoch
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

    private fun calculateTaprootSighash(
        transaction: TransactionBuilder.Transaction,
        inputIndex: Int,
        scriptCode: ByteArray,
        value: Long,
        hashType: Int
    ): ByteArray {
        // Simplified Taproot sighash
        val buffer = mutableListOf<Byte>()
        buffer.add(0x00) // epoch
        buffer.addAll(intToBytes(transaction.version.toInt()).toList())
        buffer.addAll(intToBytes(transaction.lockTime.toInt()).toList())

        for (input in transaction.inputs) {
            buffer.addAll(input.txHash.reversedArray().toList())
            buffer.addAll(intToBytes(input.outputIndex).toList())
            buffer.addAll(intToBytes(input.sequence.toInt()).toList())
        }
        for (output in transaction.outputs) {
            buffer.addAll(longToBytes(output.amount).toList())
            buffer.addAll(toVarInt(output.scriptPubKey.size.toLong()).toList())
            buffer.addAll(output.scriptPubKey.toList())
        }
        buffer.addAll(intToBytes(hashType).toList())

        return doubleHash(buffer.toByteArray())
    }

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

    private fun createP2TRScript(xOnlyPubKey: ByteArray): ByteArray {
        return byteArrayOf(0x51, 0x20) + xOnlyPubKey
    }

    private fun doubleHash(data: ByteArray): ByteArray {
        val sha1 = MessageDigest.getInstance("SHA-256").digest(data)
        return MessageDigest.getInstance("SHA-256").digest(sha1)
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
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
}
