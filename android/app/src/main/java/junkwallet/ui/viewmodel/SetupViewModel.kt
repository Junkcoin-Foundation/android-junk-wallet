package junkwallet.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import junkwallet.domain.model.JunkcoinNetwork
import junkwallet.domain.usecase.CreateWalletUseCase
import junkwallet.domain.usecase.ImportWalletUseCase
import junkwallet.domain.wallet.JunkcoinCrypto
import junkwallet.domain.wallet.WalletKeyPair
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val createWalletUseCase: CreateWalletUseCase,
    private val importWalletUseCase: ImportWalletUseCase,
    private val crypto: JunkcoinCrypto
) : ViewModel() {

    var generatedWallet by mutableStateOf<WalletKeyPair?>(null)
        private set

    var password by mutableStateOf("")
        private set

    var confirmPassword by mutableStateOf("")
        private set

    init {
        generateNewWallet()
    }

    fun generateNewWallet() {
        generatedWallet = crypto.generateWallet(JunkcoinNetwork.MAINNET)
    }

    fun updatePassword(value: String) {
        password = value
    }

    fun updateConfirmPassword(value: String) {
        confirmPassword = value
    }

    val isPasswordValid: Boolean
        get() = password.length >= 6 && password == confirmPassword

    fun storeWallet(): Boolean {
        val wallet = generatedWallet ?: return false
        if (!isPasswordValid) return false

        createWalletUseCase.store(
            wif = wallet.wif,
            address = wallet.address,
            password = password,
            params = JunkcoinNetwork.MAINNET
        )
        return true
    }

    fun importWallet(wif: String, password: String): Result<String> {
        return importWalletUseCase.import(wif, password, JunkcoinNetwork.MAINNET)
    }
}
