package junkwallet.domain.usecase

import junkwallet.data.repository.BlockchainRepository
import junkwallet.data.repository.PriceRepository
import junkwallet.domain.model.FiatPrice
import javax.inject.Inject

class GetPriceUseCase @Inject constructor(
    private val priceRepo: PriceRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): FiatPrice {
        return priceRepo.getPrice(forceRefresh)
    }
}
