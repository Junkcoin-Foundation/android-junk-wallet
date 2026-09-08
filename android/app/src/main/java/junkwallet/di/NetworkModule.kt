package junkwallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import junkwallet.data.api.CoinGeckoApi
import junkwallet.data.api.CoinGeckoClient
import junkwallet.data.api.ElectrsApi
import junkwallet.data.api.ElectrsClient
import junkwallet.domain.model.JunkcoinNetwork
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Named("mainnet_api")
    @Singleton
    fun provideMainnetElectrsApi(): ElectrsApi {
        return ElectrsClient.createApi(JunkcoinNetwork.MAINNET.electrsUrl)
    }

    @Provides
    @Named("testnet_api")
    @Singleton
    fun provideTestnetElectrsApi(): ElectrsApi {
        return ElectrsClient.createApi(JunkcoinNetwork.TESTNET.electrsUrl)
    }

    @Provides
    @Singleton
    fun provideCoinGeckoApi(): CoinGeckoApi {
        return CoinGeckoClient.create()
    }
}
