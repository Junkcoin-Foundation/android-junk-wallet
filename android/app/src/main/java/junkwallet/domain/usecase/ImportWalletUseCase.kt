package junkwallet.domain.usecase

import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.JunkcoinNetwork
import junkwallet.domain.model.JunkcoinParams
import junkwallet.domain.wallet.AddressValidator
import junkwallet.domain.wallet.JunkcoinCrypto
import javax.inject.Inject

class ImportWalletUseCase @Inject constructor(
    private val crypto: JunkcoinCrypto,
    private val storage: WalletStorage,
    private val addressValidator: AddressValidator
) {
    /**
     * Import a wallet from WIF private key.
     * Returns the derived address if valid, or an error.
     */
    fun import(wif: String, password: String, params: JunkcoinParams = JunkcoinNetwork.MAINNET): Result<String> {
        return try {
            // Derive address from WIF
            val keyPair = crypto.getKeyPairFromWif(wif)
            val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
            val address = crypto.createP2PKHAddress(compressedPubKey, params)

            // Validate address format
            if (!crypto.validateAddress(address, params)) {
                return Result.failure(IllegalArgumentException("Invalid address generated"))
            }

            // Store encrypted
            storage.saveEncryptedWif(wif, password)
            storage.saveAddress(address)
            storage.saveNetwork(params.name)
            storage.saveSessionWif(wif)

            Result.success(address)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid private key"))
        }
    }
}
