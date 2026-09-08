package junkwallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import junkwallet.data.api.CoinGeckoApi
import junkwallet.data.api.ElectrsApi
import junkwallet.data.repository.BlockchainRepository
import junkwallet.data.repository.PriceRepository
import junkwallet.data.storage.WalletStorage
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object WalletModule {

    @Provides
    fun provideBlockchainRepository(
        @Named("mainnet_api") mainnetApi: ElectrsApi,
        @Named("testnet_api") testnetApi: ElectrsApi,
        storage: WalletStorage
    ): BlockchainRepository {
        return BlockchainRepository(mainnetApi, testnetApi, storage)
    }

    @Provides
    fun providePriceRepository(
        api: CoinGeckoApi
    ): PriceRepository {
        return PriceRepository(api)
    }
}
