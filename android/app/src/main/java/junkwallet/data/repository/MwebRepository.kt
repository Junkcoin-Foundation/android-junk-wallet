package junkwallet.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import junkwallet.data.model.Utxo
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.wallet.CoinSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.junkcoin.mweb.JunkcoinMweb
import xyz.junkcoin.mweb.MwebCreateResult
import xyz.junkcoin.mweb.MwebPeginInput
import xyz.junkcoin.mweb.MwebStatus
import xyz.junkcoin.mweb.MwebTxInput
import xyz.junkcoin.mweb.MwebTxOutput
import xyz.junkcoin.mweb.MwebUtxoInfo
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for MWEB (MimbleWimble Extension Block) operations.
 *
 * Wraps [JunkcoinMweb] (the gomobile binding of `junkcoin-mwebd`) and adds the
 * wallet specific logic: coin selection, address index lookup, change output
 * and fee calculation.
 */
@Singleton
class MwebRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: WalletStorage
) {
    companion object {
        private const val TAG = "MwebRepository"
        private const val MAINNET = "mainnet"
        private const val TESTNET = "testnet"
        private const val MAINNET_PEER = "mainnet.junk-coin.com:9771"
        // Public P2P proxy for the testnet junkcoind node. The previous seed
        // (testnet.junk-coin.com:19771) sits behind Cloudflare, which does not
        // forward the p2p port, so the daemon ended up with zero peers and
        // every broadcast failed with "no peers replied to inv message".
        private const val TESTNET_PEER = "junk-testnet.s3na.xyz:19771"
        private const val ADDRESS_POOL = 50
    }

    private val mweb = JunkcoinMweb()
    private var startedChain: String? = null

    private fun chain(): String =
        if (storage.getNetwork() == WalletStorage.NETWORK_TESTNET) TESTNET else MAINNET

    /**
     * Starts the daemon for the currently selected network, restarting it if
     * the network changed since the last start.
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            val chain = chain()
            if (mweb.isRunning && startedChain == chain) return@withContext true
            if (mweb.isRunning) stop()

            val peer = if (chain == TESTNET) TESTNET_PEER else MAINNET_PEER
            // Separate chain data dirs: the neutrino DB is chain specific.
            // mwebd does not create the parent directory itself.
            val dataDir = File(context.filesDir, "mwebd-$chain").also { it.mkdirs() }.absolutePath
            mweb.start(chain = chain, dataDir = dataDir, peerAddr = peer)
            startedChain = chain
            Log.d(TAG, "MWEB daemon started for $chain ($peer)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MWEB daemon: ${e.message}", e)
            false
        }
    }

    fun stop() {
        try {
            mweb.stop()
            startedChain = null
            Log.d(TAG, "MWEB daemon stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop MWEB daemon: ${e.message}", e)
        }
    }

    private suspend fun ensureRunning(): Boolean {
        if (mweb.isRunning && startedChain == chain()) return true
        return start()
    }

    /** Sync status of the daemon, or null when it is not running. */
    suspend fun status(): MwebStatus? = withContext(Dispatchers.IO) {
        try {
            if (!mweb.isRunning) null else mweb.status()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MWEB status: ${e.message}")
            null
        }
    }

    /**
     * Get MWEB addresses for the wallet.
     *
     * Works without a running daemon (address derivation is pure), so the
     * receive flow keeps working while the chain syncs.
     *
     * @param scanSecret 32-byte scan secret key
     * @param spendPub 33-byte compressed spend public key
     * @return List of MWEB addresses (jcmweb1...)
     */
    suspend fun getAddresses(
        scanSecret: ByteArray,
        spendPub: ByteArray,
        from: Int = 0,
        to: Int = 10
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            if (mweb.isRunning && startedChain == chain()) {
                mweb.addresses(scanSecret, spendPub, from, to)
            } else {
                JunkcoinMweb.addressesFor(chain(), scanSecret, spendPub, from, to)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MWEB addresses: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get MWEB UTXOs (requires the daemon: it scans the local MWEB coin DB).
     *
     * @param fromHeight start height (0 = from the beginning)
     */
    suspend fun getUtxos(
        scanSecret: ByteArray,
        fromHeight: Long = 0L
    ): List<MwebUtxo> = withContext(Dispatchers.IO) {
        try {
            if (!ensureRunning()) return@withContext emptyList()
            mweb.utxos(scanSecret, fromHeight).map { it.toMwebUtxo() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MWEB UTXOs: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get MWEB balance in satoshis.
     */
    suspend fun getBalance(scanSecret: ByteArray): Long = withContext(Dispatchers.IO) {
        try {
            getUtxos(scanSecret).sumOf { it.value }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MWEB balance: ${e.message}")
            0L
        }
    }

    /**
     * Builds, creates (via the daemon) and returns a signed MWEB transaction.
     *
     * Flow: select MWEB coins -> derive the address index of every selected
     * coin -> add a change output when needed -> serialize the skeleton with
     * [JunkcoinMweb.buildRawTx] -> let the daemon build the MWEB kernel via
     * `CreateRaw`.
     *
     * @return the serialized transaction, or null when it cannot be built
     * (daemon down, unknown address index, insufficient funds).
     */
    suspend fun createTransaction(
        scanSecret: ByteArray,
        spendSecret: ByteArray,
        spendPub: ByteArray,
        recipientAddress: String,
        amount: Long,
        feeRatePerKb: Long = 1000
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) {
                Log.e(TAG, "Invalid MWEB amount: $amount")
                return@withContext null
            }
            if (!ensureRunning()) {
                Log.e(TAG, "MWEB daemon unavailable")
                return@withContext null
            }
            val chain = chain()

            val addresses = getAddresses(scanSecret, spendPub, 0, ADDRESS_POOL)
            if (addresses.isEmpty()) {
                Log.e(TAG, "No MWEB addresses available")
                return@withContext null
            }
            val indexOf = addresses.withIndex().associate { (i, addr) -> addr to i }
            val changeAddress = addresses.first()

            val utxos = mweb.utxos(scanSecret, 0L)
            if (utxos.isEmpty()) {
                Log.e(TAG, "No MWEB UTXOs to spend")
                return@withContext null
            }

            val recipient = MwebTxOutput(recipientAddress, amount)
            val changeTemplate = MwebTxOutput(changeAddress, 0L)
            val feeWithChange = JunkcoinMweb.estimateFee(
                chain, listOf(recipient, changeTemplate), feeRatePerKb
            )
            val feeNoChange = JunkcoinMweb.estimateFee(
                chain, listOf(recipient), feeRatePerKb
            )

            // Coin selection: largest first until amount + fee with change fits.
            val selected = mutableListOf<MwebUtxoInfo>()
            var total = 0L
            val needed = amount + feeWithChange
            for (utxo in utxos.sortedByDescending { it.value }) {
                if (total >= needed) break
                selected.add(utxo)
                total += utxo.value
            }

            val inputs = selected.map { utxo ->
                val index = indexOf[utxo.address]
                if (index == null) {
                    // Received on an address outside the generated pool.
                    Log.e(TAG, "Address ${utxo.address} outside pool of $ADDRESS_POOL")
                    return@withContext null
                }
                MwebTxInput(utxo.outputId, index)
            }

            val recipients = when {
                total >= needed && total - amount - feeWithChange > 0L -> {
                    val change = total - amount - feeWithChange
                    listOf(recipient, MwebTxOutput(changeAddress, change))
                }
                total >= amount + feeNoChange -> {
                    // Leftover is absorbed as fee by the daemon.
                    listOf(recipient)
                }
                else -> {
                    Log.e(TAG, "Insufficient MWEB funds: have=$total need=${amount + feeNoChange}")
                    return@withContext null
                }
            }

            val skeleton = JunkcoinMweb.buildRawTx(chain, inputs, recipients)
            val result: MwebCreateResult = mweb.create(
                rawTx = skeleton,
                scanSecret = scanSecret,
                spendSecret = spendSecret,
                feeRatePerKb = feeRatePerKb,
                dryRun = false
            )
            val rawTx = JunkcoinMweb.hexToBytes(result.rawTxHex)
            Log.d(TAG, "MWEB tx created: ${rawTx.size} bytes, ${result.outputIds.size} outputs")
            rawTx
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MWEB transaction: ${e.message}", e)
            null
        }
    }

    /** Broadcasts a raw transaction, returning its txid or null on failure. */
    suspend fun broadcast(rawTx: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            mweb.broadcast(rawTx)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast MWEB transaction: ${e.message}", e)
            null
        }
    }

    data class PeginResult(
        val rawTx: ByteArray,
        val txid: String,
        /** Total fee: transparent leftover + MWEB kernel fee. */
        val fee: Long
    ) {
        override fun equals(other: Any?): Boolean =
            other is PeginResult && other.txid == txid && other.rawTx.contentEquals(rawTx)

        override fun hashCode(): Int = txid.hashCode()
    }

    /**
     * Builds a fully signed peg-in transaction (transparent UTXOs funding an
     * MWEB output) via `CreatePeginJSON`.
     *
     * Fee model (matches litecoin's Transact::AddMWEBTx):
     *  - `mwebFee`  - kernel fee, estimated with the daemon's own formula;
     *  - `ltcFee`   - transparent part, virtual size at [feePerByte]
     *    (over-estimate: 148 vB per input, pegin + worst case change output);
     *  - pegin output value = amount + mwebFee (funds the kernel fee);
     *  - change = total - amount - mwebFee - ltcFee, dropped when below
     *    [CoinSelector.DUST_THRESHOLD] (absorbed into the fee).
     *
     * @param walletAddress account address; derives the script type of every
     * input and receives the change.
     * @param privKeyHex hex private key owning the wallet's UTXOs.
     * @return the signed transaction or null (insufficient funds, daemon down).
     */
    suspend fun createPegin(
        utxos: List<Utxo>,
        recipientAddress: String,
        amount: Long,
        walletAddress: String,
        privKeyHex: String,
        scanSecret: ByteArray,
        spendSecret: ByteArray,
        feePerByte: Long
    ): PeginResult? = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) {
                Log.e(TAG, "Invalid peg-in amount: $amount")
                return@withContext null
            }
            if (feePerByte < 1) {
                Log.e(TAG, "Invalid fee rate: $feePerByte")
                return@withContext null
            }
            if (!ensureRunning()) {
                Log.e(TAG, "MWEB daemon unavailable")
                return@withContext null
            }
            val chain = chain()
            val feeRatePerKb = feePerByte * 1000

            val mwebFee = JunkcoinMweb.estimateFee(
                chain, listOf(MwebTxOutput(recipientAddress, amount)), feeRatePerKb
            )

            // Estimated transparent fee: overhead + n legacy-sized inputs +
            // pegin output (34 byte script) + worst case change output (43).
            fun ltcFee(n: Int): Long =
                (CoinSelector.TX_OVERHEAD + CoinSelector.P2PKH_INPUT_SIZE * n + 43 + 43) * feePerByte

            val candidates = utxos
                .filter { it.status.confirmed }
                .sortedByDescending { it.value }
            val selected = mutableListOf<Utxo>()
            var total = 0L
            var change = 0L
            for (utxo in candidates) {
                selected.add(utxo)
                total += utxo.value
                val need = amount + mwebFee + ltcFee(selected.size)
                if (total >= need) {
                    change = total - need
                    break
                }
            }
            val need = amount + mwebFee + ltcFee(selected.size.coerceAtLeast(1))
            if (selected.isEmpty() || total < need) {
                Log.e(TAG, "Insufficient funds for peg-in: have=$total need=$need")
                return@withContext null
            }
            if (change < CoinSelector.DUST_THRESHOLD) {
                change = 0L // below dust: absorbed into the fee
            }

            val inputs = selected.map { utxo ->
                MwebPeginInput(
                    // Outpoint hash needs raw byte order: display txid reversed.
                    outputId = utxo.txid.hexByteReversed(),
                    index = utxo.vout,
                    value = utxo.value,
                    address = walletAddress,
                    privateKeyHex = privKeyHex
                )
            }

            val result = mweb.createPegin(
                chain = chain,
                inputs = inputs,
                recipient = recipientAddress,
                amount = amount,
                changeAddress = walletAddress,
                changeValue = change,
                feeRatePerKb = feeRatePerKb,
                scanSecret = scanSecret,
                spendSecret = spendSecret
            )
            val rawTx = JunkcoinMweb.hexToBytes(result.rawTxHex)
            Log.d(TAG, "Peg-in built: ${rawTx.size} bytes, fee=${result.fee}, change=$change")
            PeginResult(rawTx = rawTx, txid = result.txid, fee = result.fee)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create peg-in: ${e.message}", e)
            null
        }
    }

    /** "aabbcc" -> "ccbbaa": byte order flip for outpoint hashes. */
    private fun String.hexByteReversed(): String = chunked(2).reversed().joinToString("")
}

private fun MwebUtxoInfo.toMwebUtxo() = MwebUtxo(
    address = address,
    value = value,
    outputId = outputId,
    blockTime = blockTime
)

/**
 * MWEB UTXO data class
 */
data class MwebUtxo(
    val address: String,
    val value: Long,
    val outputId: String,
    val blockTime: Long
)
