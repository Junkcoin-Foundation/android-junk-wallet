package junkwallet.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WalletCacheDao {

    // ── Balance ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalance(balance: CachedBalance)

    @Query("SELECT * FROM cached_balance WHERE address = :address AND network = :network LIMIT 1")
    suspend fun getBalance(address: String, network: String): CachedBalance?

    // ── Transactions ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<CachedTransaction>)

    @Query("DELETE FROM cached_transaction WHERE address = :address AND network = :network")
    suspend fun clearTransactions(address: String, network: String)

    @Query("SELECT * FROM cached_transaction WHERE address = :address AND network = :network ORDER BY CASE WHEN blockTime IS NULL THEN 1 ELSE 0 END, blockTime DESC, CASE WHEN mempoolTime IS NULL THEN 1 ELSE 0 END, mempoolTime DESC")
    suspend fun getTransactions(address: String, network: String): List<CachedTransaction>

    // ── UTXOs ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUtxos(utxos: List<CachedUtxo>)

    @Query("DELETE FROM cached_utxo WHERE address = :address AND network = :network")
    suspend fun clearUtxos(address: String, network: String)

    @Query("SELECT * FROM cached_utxo WHERE address = :address AND network = :network")
    suspend fun getUtxos(address: String, network: String): List<CachedUtxo>

    // ── Network Meta ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNetworkMeta(meta: CachedNetworkMeta)

    @Query("SELECT * FROM cached_network_meta WHERE network = :network LIMIT 1")
    suspend fun getNetworkMeta(network: String): CachedNetworkMeta?

    // ── Cleanup ──

    @Query("DELETE FROM cached_balance WHERE address = :address AND network = :network")
    suspend fun clearBalance(address: String, network: String)

    @Query("DELETE FROM cached_transaction WHERE network = :network")
    suspend fun clearAllTransactions(network: String)

    @Query("DELETE FROM cached_utxo WHERE network = :network")
    suspend fun clearAllUtxos(network: String)
}
