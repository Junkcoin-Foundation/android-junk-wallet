package junkwallet.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddressInfo(
    val address: String = "",
    @SerialName("chain_stats") val chainStats: ChainStats = ChainStats(),
    @SerialName("mempool_stats") val mempoolStats: MempoolStats = MempoolStats()
) {
    @Serializable
    data class ChainStats(
        @SerialName("funded_txo_count") val fundedTxoCount: Int = 0,
        @SerialName("funded_txo_sum") val fundedTxoSum: Long = 0,
        @SerialName("spent_txo_count") val spentTxoCount: Int = 0,
        @SerialName("spent_txo_sum") val spentTxoSum: Long = 0,
        @SerialName("tx_count") val txCount: Int = 0
    )

    @Serializable
    data class MempoolStats(
        @SerialName("funded_txo_count") val fundedTxoCount: Int = 0,
        @SerialName("funded_txo_sum") val fundedTxoSum: Long = 0,
        @SerialName("spent_txo_count") val spentTxoCount: Int = 0,
        @SerialName("spent_txo_sum") val spentTxoSum: Long = 0,
        @SerialName("tx_count") val txCount: Int = 0
    )

    val confirmedBalance: Long
        get() = chainStats.fundedTxoSum - chainStats.spentTxoSum

    val unconfirmedBalance: Long
        get() = mempoolStats.fundedTxoSum - mempoolStats.spentTxoSum
}

@Serializable
data class AddressTxsResponse(
    val transactions: List<Transaction> = emptyList(),
    val total: Int = 0,
    @SerialName("start_index") val startIndex: Int = 0,
    val limit: Int = 25,
    @SerialName("next_page_after_txid") val nextPageAfterTxid: String? = null
)

@Serializable
data class Transaction(
    val txid: String,
    val version: Int = 1,
    val locktime: Int = 0,
    val vin: List<Vin> = emptyList(),
    val vout: List<Vout> = emptyList(),
    val size: Int = 0,
    val weight: Int = 0,
    val fee: Long? = null,
    val status: TxStatus = TxStatus()
) {
    @Serializable
    data class Vin(
        val txid: String = "",
        val vout: Int = 0,
        val prevout: Prevout? = null,
        @SerialName("scriptsig") val scriptsig: String = "",
        @SerialName("scriptsig_asm") val scriptsigAsm: String = "",
        val witness: List<String>? = null,
        @SerialName("is_coinbase") val isCoinbase: Boolean = false,
        val sequence: Long = 0
    )

    @Serializable
    data class Vout(
        val n: Int = 0,
        @SerialName("scriptpubkey") val scriptpubkey: String = "",
        @SerialName("scriptpubkey_asm") val scriptpubkeyAsm: String = "",
        @SerialName("scriptpubkey_type") val scriptpubkeyType: String = "",
        @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String = "",
        val value: Long = 0
    )

    @Serializable
    data class Prevout(
        @SerialName("scriptpubkey") val scriptpubkey: String = "",
        @SerialName("scriptpubkey_asm") val scriptpubkeyAsm: String = "",
        @SerialName("scriptpubkey_type") val scriptpubkeyType: String = "",
        @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String = "",
        val value: Long = 0
    )

    @Serializable
    data class TxStatus(
        val confirmed: Boolean = false,
        @SerialName("block_height") val blockHeight: Int? = null,
        @SerialName("block_hash") val blockHash: String? = null,
        @SerialName("block_time") val blockTime: Long? = null
    )
}

@Serializable
data class Utxo(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: UtxoStatus = UtxoStatus()
) {
    @Serializable
    data class UtxoStatus(
        val confirmed: Boolean = false,
        @SerialName("block_height") val blockHeight: Int? = null,
        @SerialName("block_hash") val blockHash: String? = null,
        @SerialName("block_time") val blockTime: Long? = null
    )
}

@Serializable
data class BalanceResponse(
    @SerialName("confirm_amount") val confirmAmount: String = "0",
    @SerialName("pending_amount") val pendingAmount: String = "0",
    val amount: String = "0",
    @SerialName("confirm_coin_amount") val confirmCoinAmount: String = "0",
    @SerialName("pending_coin_amount") val pendingCoinAmount: String = "0",
    @SerialName("coin_amount") val coinAmount: String = "0"
)
