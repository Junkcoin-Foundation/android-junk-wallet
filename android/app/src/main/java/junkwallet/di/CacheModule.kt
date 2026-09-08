package junkwallet.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import junkwallet.data.cache.WalletCacheDao
import junkwallet.data.cache.WalletDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideWalletDatabase(
        @ApplicationContext context: Context
    ): WalletDatabase {
        return Room.databaseBuilder(
            context,
            WalletDatabase::class.java,
            "junk_wallet_cache.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideWalletCacheDao(database: WalletDatabase): WalletCacheDao {
        return database.walletCacheDao()
    }
}
