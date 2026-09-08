package junkwallet.data.cache

import android.util.Log
import junkwallet.domain.model.FeeEstimates
import junkwallet.domain.model.TransactionInfo
import junkwallet.domain.model.UtxoInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletCacheRepository @Inject constructor(
    private val dao: WalletCacheDao
) {
    companion object {
        private const val TAG = "WalletCache"
        /** Cache is considered fresh for 2 minutes */
        const val CACHE_FRESHNESS_MS = 2 * 60 * 1000L
    }

    // ── Read ──

    suspend fun getCachedBalance(address: String, network: String): CachedBalance? {
        return try {
            dao.getBalance(address, network)
        } catch (e: Exception) {
            Log.e(TAG, "getCachedBalance failed: ${e.message}")
            null
        }
    }

    suspend fun getCachedTransactions(address: String, network: String): List<CachedTransaction> {
        return try {
            dao.getTransactions(address, network)
        } catch (e: Exception) {
            Log.e(TAG, "getCachedTransactions failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getCachedUtxos(address: String, network: String): List<CachedUtxo> {
        return try {
            dao.getUtxos(address, network)
        } catch (e: Exception) {
            Log.e(TAG, "getCachedUtxos failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getCachedNetworkMeta(network: String): CachedNetworkMeta? {
        return try {
            dao.getNetworkMeta(network)
        } catch (e: Exception) {
            Log.e(TAG, "getCachedNetworkMeta failed: ${e.message}")
            null
        }
    }

    /**
     * Check if cache is fresh enough (synced within CACHE_FRESHNESS_MS).
     */
    suspend fun isCacheFresh(address: String, network: String): Boolean {
        val meta = dao.getNetworkMeta(network) ?: return false
        return (System.currentTimeMillis() - meta.lastSynced) < CACHE_FRESHNESS_MS
    }

    /**
     * Get last sync timestamp for display.
     */
    suspend fun getLastSyncTime(network: String): Long {
        return dao.getNetworkMeta(network)?.lastSynced ?: 0L
    }

    // ── Write ──

    suspend fun cacheBalance(address: String, network: String, confirmed: Long, unconfirmed: Long) {
        try {
            dao.upsertBalance(
                CachedBalance(
                    address = address,
                    network = network,
                    confirmed = confirmed,
                    unconfirmed = unconfirmed
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "cacheBalance failed: ${e.message}")
        }
    }

    suspend fun cacheTransactions(address: String, network: String, transactions: List<TransactionInfo>) {
        try {
            dao.clearTransactions(address, network)
            val cached = transactions.map { tx ->
                CachedTransaction(
                    txid = tx.txid,
                    network = network,
                    address = address,
                    confirmed = tx.confirmed,
                    blockHeight = tx.blockHeight,
                    blockTime = tx.blockTime,
                    fee = tx.fee,
                    sent = tx.sent,
                    received = tx.received,
                    isSent = tx.isSent,
                    mempoolTime = tx.mempoolTime
                )
            }
            if (cached.isNotEmpty()) {
                dao.upsertTransactions(cached)
            }
        } catch (e: Exception) {
            Log.e(TAG, "cacheTransactions failed: ${e.message}")
        }
    }

    suspend fun cacheUtxos(address: String, network: String, utxos: List<UtxoInfo>) {
        try {
            dao.clearUtxos(address, network)
            val cached = utxos.map { utxo ->
                CachedUtxo(
                    txid = utxo.txid,
                    vout = utxo.vout,
                    network = network,
                    address = address,
                    value = utxo.value,
                    confirmed = utxo.confirmed,
                    blockHeight = utxo.blockHeight
                )
            }
            if (cached.isNotEmpty()) {
                dao.upsertUtxos(cached)
            }
        } catch (e: Exception) {
            Log.e(TAG, "cacheUtxos failed: ${e.message}")
        }
    }

    suspend fun cacheNetworkMeta(network: String, blockHeight: Int, fees: FeeEstimates) {
        try {
            dao.upsertNetworkMeta(
                CachedNetworkMeta(
                    network = network,
                    blockHeight = blockHeight,
                    feeBlocks1 = fees.blocks1,
                    feeBlocks3 = fees.blocks3,
                    feeBlocks6 = fees.blocks6,
                    feeBlocks12 = fees.blocks12,
                    feeBlocks24 = fees.blocks24,
                    feeBlocks144 = fees.blocks144
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "cacheNetworkMeta failed: ${e.message}")
        }
    }

    /**
     * Clear all cached data for a specific address on a specific network.
     */
    suspend fun clearAllData(address: String, network: String) {
        try {
            dao.clearBalance(address, network)
            dao.clearTransactions(address, network)
            dao.clearUtxos(address, network)
            Log.d(TAG, "clearAllData: cleared cache for $address on $network")
        } catch (e: Exception) {
            Log.e(TAG, "clearAllData failed: ${e.message}")
        }
    }

}

// ── Converters ──

fun CachedTransaction.toDomain(): TransactionInfo {
    return TransactionInfo(
        txid = txid,
        confirmed = confirmed,
        blockHeight = blockHeight,
        blockTime = blockTime,
        fee = fee,
        sent = sent,
        received = received,
        isSent = isSent,
        mempoolTime = mempoolTime
    )
}

fun CachedUtxo.toDomain(): UtxoInfo {
    return UtxoInfo(
        txid = txid,
        vout = vout,
        value = value,
        confirmed = confirmed,
        blockHeight = blockHeight
    )
}

fun CachedNetworkMeta.toFeesDomain(): FeeEstimates {
    return FeeEstimates(
        blocks1 = feeBlocks1,
        blocks3 = feeBlocks3,
        blocks6 = feeBlocks6,
        blocks12 = feeBlocks12,
        blocks24 = feeBlocks24,
        blocks144 = feeBlocks144
    )
}
