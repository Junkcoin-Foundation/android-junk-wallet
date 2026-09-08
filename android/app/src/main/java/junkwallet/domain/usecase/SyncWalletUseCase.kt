package junkwallet.domain.usecase

import android.util.Log
import junkwallet.data.cache.WalletCacheRepository
import junkwallet.data.cache.toDomain
import junkwallet.data.cache.toFeesDomain
import junkwallet.data.repository.BlockchainRepository
import junkwallet.domain.model.NetworkType
import junkwallet.domain.model.TransactionInfo
import junkwallet.domain.model.UtxoInfo
import junkwallet.domain.model.WalletState
import junkwallet.data.storage.WalletStorage
import javax.inject.Inject

class SyncWalletUseCase @Inject constructor(
    private val blockchainRepo: BlockchainRepository,
    private val cacheRepo: WalletCacheRepository,
    private val storage: WalletStorage
) {
    companion object {
        private const val TAG = "SyncWalletUseCase"
    }

    /**
     * Cache-first sync strategy:
     * 1. Return cached data immediately (instant display)
     * 2. If cache is fresh (< 2 min), stop here
     * 3. If cache is stale/empty, fetch from network and update cache
     * 4. Return whatever we have (cache or fresh)
     */
    suspend fun sync(address: String, currentState: WalletState): WalletState {
        val networkName = storage.getNetwork()

        // Step 1: Load from cache immediately
        val cachedState = loadFromCache(address, networkName)
        Log.d(TAG, "Cache loaded: balance=${cachedState.confirmedBalance} txs=${cachedState.transactions.size}")

        // Step 2: If cache is fresh, return it (no network call)
        if (cacheRepo.isCacheFresh(address, networkName)) {
            Log.d(TAG, "Cache is fresh, skipping network fetch")
            return cachedState.copy(isLoading = false, error = null)
        }

        // Step 3: Cache is stale or empty — fetch from network
        Log.d(TAG, "Cache stale, fetching from network...")
        return try {
            val networkState = fetchFromNetwork(address, networkName)

            // Step 4: Save to cache
            saveToCache(address, networkName, networkState)

            Log.d(TAG, "Network fetch complete: balance=${networkState.confirmedBalance} txs=${networkState.transactions.size}")
            networkState.copy(isLoading = false, error = null)
        } catch (e: Exception) {
            Log.e(TAG, "Network fetch failed: ${e.message}, returning cache")
            // Network failed — return cached data with a warning
            cachedState.copy(
                isLoading = false,
                error = "Offline — showing cached data"
            )
        }
    }

    /**
     * Force refresh: always fetch from network (pull-to-refresh).
     */
    suspend fun forceRefresh(address: String, currentState: WalletState): WalletState {
        val networkName = storage.getNetwork()
        return try {
            val networkState = fetchFromNetwork(address, networkName)
            saveToCache(address, networkName, networkState)
            Log.d(TAG, "Force refresh complete: balance=${networkState.confirmedBalance} txs=${networkState.transactions.size}")
            networkState.copy(isLoading = false, error = null)
        } catch (e: Exception) {
            Log.e(TAG, "Force refresh failed: ${e.message}")
            val cachedState = loadFromCache(address, networkName)
            cachedState.copy(
                isLoading = false,
                error = "Refresh failed — showing cached data"
            )
        }
    }

    /**
     * Clear all cached data for a specific address on a specific network.
     */
    suspend fun clearCache(address: String, network: String) {
        cacheRepo.clearAllData(address, network)
    }

    private suspend fun loadFromCache(address: String, network: String): WalletState {
        val balance = cacheRepo.getCachedBalance(address, network)
        val transactions = cacheRepo.getCachedTransactions(address, network)
        val utxos = cacheRepo.getCachedUtxos(address, network)
        val meta = cacheRepo.getCachedNetworkMeta(network)
        val lastSynced = cacheRepo.getLastSyncTime(network)

        return WalletState(
            hasStoredWallet = true,
            isLocked = false,
            confirmedBalance = balance?.confirmed ?: 0,
            unconfirmedBalance = balance?.unconfirmed ?: 0,
            transactions = transactions.map { it.toDomain() },
            utxos = utxos.map { it.toDomain() },
            blockHeight = meta?.blockHeight ?: 0,
            feeEstimates = meta?.toFeesDomain() ?: junkwallet.domain.model.FeeEstimates(),
            isLoading = true,
            error = null,
            lastSynced = lastSynced
        )
    }

    private suspend fun fetchFromNetwork(address: String, network: String): WalletState {
        val (confirmed, unconfirmed) = blockchainRepo.getBalancePair(address)
        val transactions = blockchainRepo.getTransactions(address)
        val utxos = blockchainRepo.getUtxos(address)
        val blockHeight = blockchainRepo.getBlockHeight()
        val fees = blockchainRepo.getFeeEstimates()

        return WalletState(
            hasStoredWallet = true,
            isLocked = false,
            confirmedBalance = confirmed,
            unconfirmedBalance = unconfirmed,
            transactions = transactions.map { tx ->
                val isSent = tx.vin.any { it.prevout?.scriptpubkeyAddress == address }
                val receivedFromTx = tx.vout
                    .filter { it.scriptpubkeyAddress == address }
                    .sumOf { it.value }
                val sentToOthers = if (isSent) {
                    tx.vout
                        .filter { it.scriptpubkeyAddress != address }
                        .sumOf { it.value }
                } else 0L

                TransactionInfo(
                    txid = tx.txid,
                    confirmed = tx.status.confirmed,
                    blockHeight = tx.status.blockHeight,
                    blockTime = tx.status.blockTime,
                    fee = tx.fee,
                    sent = if (isSent) sentToOthers else 0L,
                    received = receivedFromTx,
                    isSent = isSent
                )
            },
            utxos = utxos.map { utxo ->
                UtxoInfo(
                    txid = utxo.txid,
                    vout = utxo.vout,
                    value = utxo.value,
                    confirmed = utxo.status.confirmed,
                    blockHeight = utxo.status.blockHeight
                )
            },
            blockHeight = blockHeight,
            feeEstimates = fees,
            lastSynced = System.currentTimeMillis()
        )
    }

    private suspend fun saveToCache(address: String, network: String, state: WalletState) {
        cacheRepo.cacheBalance(address, network, state.confirmedBalance, state.unconfirmedBalance)
        cacheRepo.cacheTransactions(address, network, state.transactions)
        cacheRepo.cacheUtxos(address, network, state.utxos)
        cacheRepo.cacheNetworkMeta(network, state.blockHeight, state.feeEstimates)
    }
}
