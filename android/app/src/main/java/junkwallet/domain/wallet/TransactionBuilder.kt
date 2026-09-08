package junkwallet.domain.wallet

import junkwallet.domain.model.AddressType
import junkwallet.domain.model.JunkcoinParams
import junkwallet.data.model.Utxo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionBuilder @Inject constructor(
    private val crypto: JunkcoinCrypto,
    private val signer: TransactionSigner,
    private val coinSelector: CoinSelector
) {
    data class TxInput(
        val txHash: ByteArray,
        val outputIndex: Int,
        var scriptSig: ByteArray = ByteArray(0),
        val sequence: Long = 0xFFFFFFFF,
        var witness: List<ByteArray>? = null
    )

    data class TxOutput(
        val amount: Long,
        val scriptPubKey: ByteArray,
        val address: String? = null
    )

    data class Transaction(
        val version: Int = 1,
        val inputs: List<TxInput>,
        val outputs: List<TxOutput>,
        val lockTime: Long = 0
    )

    data class TxResult(
        val txId: String,
        val rawTx: String,
        val fee: Long,
        val inputCount: Int,
        val outputCount: Int
    )

    fun createTransaction(
        keyPair: java.security.KeyPair,
        utxos: List<Utxo>,
        recipientAddress: String,
        amount: Long,
        feePerByte: Long,
        network: JunkcoinParams,
        changeAddress: String,
        opReturnData: ByteArray? = null
    ): TxResult {
        if (!crypto.validateAddress(recipientAddress, network)) {
            throw IllegalArgumentException("Invalid recipient address")
        }

        val recipientAddressType = crypto.detectAddressType(recipientAddress, network)
            ?: throw IllegalArgumentException("Could not detect recipient address type")

        val inputAddressType = crypto.detectAddressType(changeAddress, network)
            ?: throw IllegalArgumentException("Could not detect wallet address type")

        val recipientScript = crypto.createLockingScript(recipientAddress, network)

        val outputs = mutableListOf(TxOutput(amount, recipientScript, recipientAddress))

        if (opReturnData != null && opReturnData.isNotEmpty()) {
            val opReturnScript = byteArrayOf(0x6A) + opReturnData.size.toByte() + opReturnData
            outputs.add(TxOutput(0, opReturnScript))
        }

        val selection = coinSelector.select(utxos, amount, feePerByte.toDouble())
        if (selection.utxos.isEmpty()) {
            throw IllegalStateException("Insufficient funds")
        }

        val selectedUtxos = selection.utxos
        val totalInputValue = selectedUtxos.sumOf { it.value }
        val change = totalInputValue - amount - selection.estimatedFee

        val DUST_LIMIT = 5000L
        if (change > DUST_LIMIT) {
            val changeScript = crypto.createLockingScript(changeAddress, network)
            outputs.add(TxOutput(change, changeScript, changeAddress))
        }

        val inputs = selectedUtxos.map { utxo ->
            TxInput(
                txHash = utxo.txid.hexToByteArray(),
                outputIndex = utxo.vout,
                scriptSig = ByteArray(0),
                sequence = 0xFFFFFFFF
            )
        }

        val transaction = Transaction(
            version = network.txVersion,
            inputs = inputs,
            outputs = outputs
        )

        val signedTransaction = signTransaction(keyPair, transaction, selectedUtxos.map { it.value }, inputAddressType)
        val rawTx = serializeTransaction(signedTransaction)
        val txId = calculateTxId(rawTx)

        return TxResult(
            txId = txId,
            rawTx = rawTx,
            fee = totalInputValue - outputs.sumOf { it.amount },
            inputCount = signedTransaction.inputs.size,
            outputCount = signedTransaction.outputs.size
        )
    }

    private fun signTransaction(
        keyPair: java.security.KeyPair,
        transaction: Transaction,
        utxoValues: List<Long>,
        inputAddressType: AddressType
    ): Transaction {
        val signedInputs = transaction.inputs.mapIndexed { index, input ->
            val utxoValue = utxoValues[index]
            val signature = signer.sign(keyPair, transaction, index, utxoValue, inputAddressType)
            val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)

            when (inputAddressType) {
                AddressType.P2WPKH -> {
                    input.copy(scriptSig = ByteArray(0), witness = listOf(signature, compressedPubKey))
                }
                AddressType.P2TR -> {
                    input.copy(scriptSig = ByteArray(0), witness = listOf(signature))
                }
                AddressType.P2PKH -> {
                    val scriptSig = ByteArray(signature.size + compressedPubKey.size + 2)
                    scriptSig[0] = signature.size.toByte()
                    System.arraycopy(signature, 0, scriptSig, 1, signature.size)
                    scriptSig[signature.size + 1] = compressedPubKey.size.toByte()
                    System.arraycopy(compressedPubKey, 0, scriptSig, signature.size + 2, compressedPubKey.size)
                    input.copy(scriptSig = scriptSig)
                }
                AddressType.P2SH_P2WPKH -> {
                    val pubkeyHash = crypto.hash160(compressedPubKey)
                    val witnessScript = byteArrayOf(0x00, 0x14) + pubkeyHash
                    val redeemScript = byteArrayOf(witnessScript.size.toByte()) + witnessScript
                    val scriptSig = byteArrayOf(redeemScript.size.toByte()) + redeemScript
                    input.copy(scriptSig = scriptSig, witness = listOf(signature, compressedPubKey))
                }
            }
        }
        return transaction.copy(inputs = signedInputs)
    }

    fun estimateFee(inputCount: Int, outputCount: Int, feePerByte: Long, inputAddressType: AddressType): Long {
        var size = 4 + 1 + inputCount * 148 + 1 + outputCount * 34 + 4
        when (inputAddressType) {
            AddressType.P2WPKH -> size += inputCount * 68
            AddressType.P2TR -> size += inputCount * 65
            AddressType.P2SH_P2WPKH -> size += inputCount * 91
            AddressType.P2PKH -> size += inputCount * 107
        }
        return size * feePerByte
    }

    fun serializeTransaction(transaction: Transaction): String {
        val buffer = mutableListOf<Byte>()
        buffer.addAll(intToBytes(transaction.version).toList())

        val hasWitness = transaction.inputs.any { it.witness != null }
        if (hasWitness) {
            buffer.add(0x00)
            buffer.add(0x01)
        }

        buffer.addAll(toVarInt(transaction.inputs.size.toLong()).toList())
        for (input in transaction.inputs) {
            buffer.addAll(input.txHash.reversedArray().toList())
            buffer.addAll(intToBytes(input.outputIndex).toList())
            buffer.addAll(toVarInt(input.scriptSig.size.toLong()).toList())
            buffer.addAll(input.scriptSig.toList())
            buffer.addAll(intToBytes(input.sequence.toInt()).toList())
        }

        buffer.addAll(toVarInt(transaction.outputs.size.toLong()).toList())
        for (output in transaction.outputs) {
            buffer.addAll(longToBytes(output.amount).toList())
            buffer.addAll(toVarInt(output.scriptPubKey.size.toLong()).toList())
            buffer.addAll(output.scriptPubKey.toList())
        }

        if (hasWitness) {
            for (input in transaction.inputs) {
                val witness = input.witness
                if (witness != null) {
                    buffer.addAll(toVarInt(witness.size.toLong()).toList())
                    for (item in witness) {
                        buffer.addAll(toVarInt(item.size.toLong()).toList())
                        buffer.addAll(item.toList())
                    }
                } else {
                    buffer.add(0x00)
                }
            }
        }

        buffer.addAll(intToBytes(transaction.lockTime.toInt()).toList())
        return buffer.joinToString("") { "%02x".format(it) }
    }

    private fun calculateTxId(rawTx: String): String {
        val txBytes = rawTx.hexToByteArray()
        val sha1 = java.security.MessageDigest.getInstance("SHA-256").digest(txBytes)
        val sha2 = java.security.MessageDigest.getInstance("SHA-256").digest(sha1)
        return sha2.reversedArray().joinToString("") { "%02x".format(it) }
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
            else -> byteArrayOf(0xFE.toByte(), (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte(), ((value shr 16) and 0xFF).toByte(), ((value shr 24) and 0xFF).toByte())
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
