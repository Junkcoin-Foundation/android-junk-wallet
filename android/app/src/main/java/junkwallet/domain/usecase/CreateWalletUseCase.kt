package junkwallet.domain.usecase

import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.JunkcoinNetwork
import junkwallet.domain.model.JunkcoinParams
import junkwallet.domain.wallet.JunkcoinCrypto
import junkwallet.domain.wallet.WalletKeyPair
import javax.inject.Inject

class CreateWalletUseCase @Inject constructor(
    private val crypto: JunkcoinCrypto,
    private val storage: WalletStorage
) {
    /**
     * Generate a new wallet keypair.
     * Returns the WIF for backup — NOT stored yet.
     */
    fun generate(params: JunkcoinParams = JunkcoinNetwork.MAINNET): WalletKeyPair {
        return crypto.generateWallet(params)
    }

    /**
     * Store the wallet with password encryption.
     */
    fun store(wif: String, address: String, password: String, params: JunkcoinParams = JunkcoinNetwork.MAINNET) {
        storage.saveEncryptedWif(wif, password)
        storage.saveAddress(address)
        storage.saveNetwork(params.name)
        storage.saveSessionWif(wif) // Keep in session for immediate use
    }
}
