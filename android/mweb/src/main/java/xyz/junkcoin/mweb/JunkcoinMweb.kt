package xyz.junkcoin.mweb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import xyz.junkcoin.mweb.mwebd.Mwebd
import xyz.junkcoin.mweb.mwebd.Server

data class MwebStatus(
    val blockHeaderHeight: Int,
    val mwebHeaderHeight: Int,
    val mwebUtxosHeight: Int,
    val blockTime: Long
)

data class MwebUtxoInfo(
    val height: Int,
    val value: Long,
    val address: String,
    val outputId: String,
    val blockTime: Long
)

data class MwebCreateResult(
    val rawTxHex: String,
    val outputIds: List<String>
)

data class MwebTxInput(
    val outputId: String,
    val addressIndex: Int
)

data class MwebTxOutput(
    val address: String,
    val value: Long
)

/** One transparent input funding a peg-in (transparent -> MWEB). */
data class MwebPeginInput(
    /** Outpoint hash in raw byte order (display txid byte-reversed). */
    val outputId: String,
    val index: Int,
    val value: Long,
    /** Address the previous output was received on (defines its script type). */
    val address: String,
    /** Hex encoded 32 byte private key owning the previous output. */
    val privateKeyHex: String
)

data class MwebPeginResult(
    val rawTxHex: String,
    val txid: String,
    /** Total fee in litoshis: transparent leftover + MWEB kernel fee. */
    val fee: Long
)

/**
 * Kotlin wrapper around the gomobile bindings of `junkcoin-mwebd`
 * (`xyz.junkcoin.mweb.mwebd.*`, shipped in `libs/junkcoin-mweb.aar`).
 *
 * All daemon RPCs are exposed here as JSON/primitive based methods because
 * gomobile cannot bind gRPC, `context.Context` or protobuf types.
 *
 * ```kotlin
 * val mweb = JunkcoinMweb()
 * mweb.start(chain = "mainnet", dataDir = dir, peerAddr = "mainnet.junk-coin.com:9771")
 * val addresses = mweb.addresses(scanSecret, spendPub, 0, 10)
 * val utxos = mweb.utxos(scanSecret)
 * mweb.stop()
 * ```
 *
 * Address generation and fee estimation also work without a running daemon
 * (see [addressesFor], [estimateFee] and [buildRawTx] in the companion object).
 */
class JunkcoinMweb {

    @Volatile
    private var server: Server? = null

    val isRunning: Boolean
        get() = server != null

    /**
     * Starts the daemon (neutrino SPV sync + in-process RPC surface).
     *
     * The gRPC listener is bound to an ephemeral localhost port only; callers
     * use this object directly, so the port number is irrelevant.
     *
     * @return the ephemeral gRPC port (informational)
     */
    suspend fun start(
        chain: String,
        dataDir: String,
        peerAddr: String
    ): Int = withContext(Dispatchers.IO) {
        stopInternal()
        val s = Server(chain, dataDir, peerAddr)
        val port = s.startAddr(BIND_ADDR).toInt()
        server = s
        port
    }

    suspend fun status(): MwebStatus = withContext(Dispatchers.IO) {
        val json = requireServer().statusJSON()
        val obj = JSONObject(json)
        MwebStatus(
            blockHeaderHeight = obj.optInt("blockHeaderHeight"),
            mwebHeaderHeight = obj.optInt("mwebHeaderHeight"),
            mwebUtxosHeight = obj.optInt("mwebUtxosHeight"),
            blockTime = obj.optLong("blockTime")
        )
    }

    suspend fun addresses(
        scanSecret: ByteArray,
        spendPub: ByteArray,
        from: Int = 0,
        to: Int = 10
    ): List<String> = withContext(Dispatchers.IO) {
        parseStringArray(requireServer().addressesJSON(scanSecret, spendPub, from, to))
    }

    suspend fun utxos(
        scanSecret: ByteArray,
        fromHeight: Long = 0L
    ): List<MwebUtxoInfo> = withContext(Dispatchers.IO) {
        val arr = JSONArray(requireServer().utxosOnceJSON(scanSecret, fromHeight))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    MwebUtxoInfo(
                        height = o.optInt("height"),
                        value = o.optLong("value"),
                        address = o.optString("address"),
                        outputId = o.optString("outputId"),
                        blockTime = o.optLong("blockTime")
                    )
                )
            }
        }
    }

    suspend fun create(
        rawTx: ByteArray,
        scanSecret: ByteArray,
        spendSecret: ByteArray,
        feeRatePerKb: Long,
        dryRun: Boolean = false
    ): MwebCreateResult = withContext(Dispatchers.IO) {
        val obj = JSONObject(
            requireServer().createRaw(rawTx, scanSecret, spendSecret, feeRatePerKb, dryRun)
        )
        MwebCreateResult(
            rawTxHex = obj.optString("rawTx"),
            outputIds = obj.optJSONArray("outputIds")?.let { arr ->
                parseStringArray(arr.toString())
            } ?: emptyList()
        )
    }

    suspend fun broadcast(rawTx: ByteArray): String = withContext(Dispatchers.IO) {
        requireServer().broadcastRaw(rawTx)
    }

    /**
     * Builds a fully signed peg-in transaction (transparent inputs funding an
     * MWEB output) in one call: skeleton -> daemon Create -> transparent
     * change -> transparent input signing.
     *
     * Requires a running daemon. [changeValue] may be 0 (leftover is absorbed
     * as fee).
     */
    suspend fun createPegin(
        chain: String,
        inputs: List<MwebPeginInput>,
        recipient: String,
        amount: Long,
        changeAddress: String,
        changeValue: Long,
        feeRatePerKb: Long,
        scanSecret: ByteArray,
        spendSecret: ByteArray
    ): MwebPeginResult = withContext(Dispatchers.IO) {
        val inputArr = JSONArray()
        for (input in inputs) {
            inputArr.put(
                JSONObject()
                    .put("outputId", input.outputId)
                    .put("index", input.index)
                    .put("value", input.value)
                    .put("address", input.address)
                    .put("privKey", input.privateKeyHex)
            )
        }
        val req = JSONObject()
            .put("chain", chain)
            .put("inputs", inputArr)
            .put("recipient", recipient)
            .put("amount", amount)
            .put("changeAddress", changeAddress)
            .put("changeValue", changeValue)
            .put("feeRatePerKb", feeRatePerKb)
            .put("scanSecret", bytesToHex(scanSecret))
            .put("spendSecret", bytesToHex(spendSecret))
        val obj = JSONObject(requireServer().createPeginJSON(req.toString()))
        MwebPeginResult(
            rawTxHex = obj.optString("rawTx"),
            txid = obj.optString("txid"),
            fee = obj.optLong("fee")
        )
    }

    suspend fun spent(outputIds: List<String>): List<String> = withContext(Dispatchers.IO) {
        parseStringArray(requireServer().spentOnce(outputIds.joinToString(",")))
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        server?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // Daemon already gone.
            }
        }
        server = null
    }

    private fun requireServer(): Server =
        server ?: error("MWEB daemon not started")

    companion object {
        private const val BIND_ADDR = "127.0.0.1:0"

        /** Generates MWEB addresses without a running daemon. */
        fun addressesFor(
            chain: String,
            scanSecret: ByteArray,
            spendPub: ByteArray,
            from: Int = 0,
            to: Int = 10
        ): List<String> = parseStringArray(
            Mwebd.addressesFor(chain, scanSecret, spendPub, from, to)
        )

        /**
         * Estimates the fee (litoshis) for the given outputs, using exactly the
         * formula the daemon applies when creating the transaction.
         */
        fun estimateFee(
            chain: String,
            recipients: List<MwebTxOutput>,
            feeRatePerKb: Long
        ): Long = JSONObject(
            Mwebd.estimateFeeJSON(txRequestJson(chain, emptyList(), recipients), feeRatePerKb)
        ).optLong("fee")

        /** Serializes the unsigned transaction skeleton expected by [JunkcoinMweb.create]. */
        fun buildRawTx(
            chain: String,
            inputs: List<MwebTxInput>,
            recipients: List<MwebTxOutput>
        ): ByteArray = Mwebd.buildRawTxJSON(txRequestJson(chain, inputs, recipients))

        fun outputIdValid(outputId: String): Boolean = Mwebd.outputIdValid(outputId)

        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "odd length hex" }
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        private fun txRequestJson(
            chain: String,
            inputs: List<MwebTxInput>,
            recipients: List<MwebTxOutput>
        ): String {
            val inputArr = JSONArray()
            for (input in inputs) {
                inputArr.put(
                    JSONObject()
                        .put("outputId", input.outputId)
                        .put("index", input.addressIndex)
                )
            }
            val recipientArr = JSONArray()
            for (recipient in recipients) {
                recipientArr.put(
                    JSONObject()
                        .put("address", recipient.address)
                        .put("value", recipient.value)
                )
            }
            return JSONObject()
                .put("chain", chain)
                .put("inputs", inputArr)
                .put("recipients", recipientArr)
                .toString()
        }

        private fun parseStringArray(json: String): List<String> {
            val arr = JSONArray(json)
            return buildList {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        }
    }
}
