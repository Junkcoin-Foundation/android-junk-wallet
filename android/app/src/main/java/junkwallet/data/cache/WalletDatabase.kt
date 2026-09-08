package junkwallet.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedBalance::class,
        CachedTransaction::class,
        CachedUtxo::class,
        CachedNetworkMeta::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun walletCacheDao(): WalletCacheDao
}
