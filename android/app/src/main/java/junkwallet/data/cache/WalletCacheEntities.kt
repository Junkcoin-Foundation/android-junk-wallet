package junkwallet.data.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached balance per address per network.
 */
@Entity(
    tableName = "cached_balance",
    primaryKeys = ["address", "network"]
)
data class CachedBalance(
    val address: String,
    val network: String,
    val confirmed: Long = 0,
    val unconfirmed: Long = 0,
    val lastSynced: Long = System.currentTimeMillis()
)

/**
 * Cached transaction per address per network.
 * Composite key: txid + network (same txid can exist on both networks).
 */
@Entity(
    tableName = "cached_transaction",
    primaryKeys = ["txid", "network"],
    indices = [Index(value = ["address", "network"])]
)
data class CachedTransaction(
    val txid: String,
    val network: String,
    val address: String,
    val confirmed: Boolean,
    val blockHeight: Int? = null,
    val blockTime: Long? = null,
    val fee: Long? = null,
    val sent: Long = 0,
    val received: Long = 0,
    val isSent: Boolean = false,
    val mempoolTime: Long? = null,
    val lastSynced: Long = System.currentTimeMillis()
)

/**
 * Cached UTXO per address per network.
 */
@Entity(
    tableName = "cached_utxo",
    primaryKeys = ["txid", "vout", "network"],
    indices = [Index(value = ["address", "network"])]
)
data class CachedUtxo(
    val txid: String,
    val vout: Int,
    val network: String,
    val address: String,
    val value: Long,
    val confirmed: Boolean,
    val blockHeight: Int? = null,
    val lastSynced: Long = System.currentTimeMillis()
)

/**
 * Cached metadata per network (block height, fees, last sync time).
 */
@Entity(
    tableName = "cached_network_meta",
    primaryKeys = ["network"]
)
data class CachedNetworkMeta(
    val network: String,
    val blockHeight: Int = 0,
    val feeBlocks1: Double = 0.0,
    val feeBlocks3: Double = 0.0,
    val feeBlocks6: Double = 0.0,
    val feeBlocks12: Double = 0.0,
    val feeBlocks24: Double = 0.0,
    val feeBlocks144: Double = 0.0,
    val lastSynced: Long = System.currentTimeMillis()
)
