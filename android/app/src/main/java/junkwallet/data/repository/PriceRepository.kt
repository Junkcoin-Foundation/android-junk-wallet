package junkwallet.data.repository

import junkwallet.data.api.CoinGeckoApi
import junkwallet.domain.model.FiatPrice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepository @Inject constructor(
    private val api: CoinGeckoApi
) {
    private var cachedPrice: FiatPrice? = null
    private var lastFetchTime: Long = 0

    private val cacheValidityMs = 5 * 60 * 1000L // 5 minutes

    suspend fun getPrice(forceRefresh: Boolean = false): FiatPrice {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedPrice != null && (now - lastFetchTime) < cacheValidityMs) {
            return cachedPrice!!
        }

        return try {
            val response = api.getPrice()
            val price = FiatPrice(
                usd = response.junkcoin?.usd ?: 0.0,
                lastUpdated = now
            )
            cachedPrice = price
            lastFetchTime = now
            price
        } catch (e: Exception) {
            cachedPrice ?: FiatPrice()
        }
    }
}
