package junkwallet.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.http.GET
import retrofit2.http.Query
import junkwallet.data.model.CoinGeckoPrice
import java.util.concurrent.TimeUnit

interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getPrice(
        @Query("ids") ids: String = "junkcoin",
        @Query("vs_currencies") vsCurrencies: String = "usd"
    ): CoinGeckoPrice
}

object CoinGeckoClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun create(): CoinGeckoApi {
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(CoinGeckoApi::class.java)
    }
}
