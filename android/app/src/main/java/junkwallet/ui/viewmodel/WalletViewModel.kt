package junkwallet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import junkwallet.data.repository.BlockchainRepository
import junkwallet.data.repository.PriceRepository
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.FiatPrice
import android.util.Log
import junkwallet.domain.model.NetworkType
import junkwallet.domain.model.WalletAccount
import junkwallet.domain.model.WalletState
import junkwallet.domain.usecase.GetPriceUseCase
import junkwallet.domain.usecase.SyncWalletUseCase
import junkwallet.domain.wallet.JunkcoinCrypto
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val syncWalletUseCase: SyncWalletUseCase,
    private val getPriceUseCase: GetPriceUseCase,
    private val storage: WalletStorage,
    private val priceRepository: PriceRepository,
    private val crypto: JunkcoinCrypto
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletState())
    val uiState: StateFlow<WalletState> = _uiState.asStateFlow()

    private val _fiatPrice = MutableStateFlow(FiatPrice())
    val fiatPrice: StateFlow<FiatPrice> = _fiatPrice.asStateFlow()

    private val _defaultAddressType = MutableStateFlow(AddressType.P2PKH)
    val defaultAddressType: StateFlow<AddressType> = _defaultAddressType.asStateFlow()

    private val _generatedAddresses = MutableStateFlow<Map<AddressType, String>>(emptyMap())
    val generatedAddresses: StateFlow<Map<AddressType, String>> = _generatedAddresses.asStateFlow()

    private var syncJob: Job? = null
    private val syncIntervalMs = 45_000L // 45 seconds

    init {
        loadWallet()
        loadPrice()
        loadDefaultAddressType()
    }

    private fun loadWallet() {
        try {
            // Migrate single wallet to account if needed
            storage.migrateSingleWalletToAccount()

            val accounts = storage.getAccounts()
            val activeAccountId = storage.getActiveAccountId()
            val activeAccount = accounts.find { it.id == activeAccountId }

            val address = storage.getAddress() ?: return
            val network = when (storage.getNetwork()) {
                "testnet" -> NetworkType.TESTNET
                else -> NetworkType.MAINNET
            }

            _uiState.update {
                it.copy(
                    hasStoredWallet = true,
                    isLocked = false,
                    address = address,
                    network = network,
                    isLoading = true,
                    accounts = accounts,
                    activeAccount = activeAccount
                )
            }

            syncWallet()
            startBackgroundSync()
            generateAllAddresses()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Failed to load wallet: ${e.message}"
                )
            }
        }
    }

    private fun loadPrice() {
        viewModelScope.launch {
            try {
                val price = getPriceUseCase(forceRefresh = false)
                _fiatPrice.value = price
            } catch (_: Exception) { }
        }
    }

    private fun loadDefaultAddressType() {
        try {
            val savedType = storage.getDefaultAddressType()
            _defaultAddressType.value = savedType
        } catch (_: Exception) { }
    }

    fun syncWallet() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                Log.d("WalletVM", "Syncing ${_uiState.value.network.name} address=${_uiState.value.address}")
                val updated = syncWalletUseCase.sync(_uiState.value.address, _uiState.value)
                _uiState.update {
                    it.copy(
                        confirmedBalance = updated.confirmedBalance,
                        unconfirmedBalance = updated.unconfirmedBalance,
                        transactions = updated.transactions,
                        utxos = updated.utxos,
                        blockHeight = updated.blockHeight,
                        feeEstimates = updated.feeEstimates,
                        isLoading = updated.isLoading,
                        error = updated.error,
                        lastSynced = updated.lastSynced
                    )
                }
                Log.d("WalletVM", "Sync complete: balance=${updated.confirmedBalance} txs=${updated.transactions.size}")

                // Fetch fiat price in background
                try {
                    val price = getPriceUseCase(forceRefresh = true)
                    _fiatPrice.value = price
                } catch (_: Exception) { }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Sync failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Force refresh from network (pull-to-refresh).
     */
    fun forceRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                Log.d("WalletVM", "Force refresh ${_uiState.value.network.name}")
                val updated = syncWalletUseCase.forceRefresh(_uiState.value.address, _uiState.value)
                _uiState.update {
                    it.copy(
                        confirmedBalance = updated.confirmedBalance,
                        unconfirmedBalance = updated.unconfirmedBalance,
                        transactions = updated.transactions,
                        utxos = updated.utxos,
                        blockHeight = updated.blockHeight,
                        feeEstimates = updated.feeEstimates,
                        isLoading = updated.isLoading,
                        error = updated.error,
                        lastSynced = updated.lastSynced
                    )
                }
                Log.d("WalletVM", "Force refresh complete: balance=${updated.confirmedBalance}")

                try {
                    val price = getPriceUseCase(forceRefresh = true)
                    _fiatPrice.value = price
                } catch (_: Exception) { }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Refresh failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun startBackgroundSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (true) {
                delay(syncIntervalMs)
                if (!_uiState.value.isLocked) {
                    syncWallet()
                }
            }
        }
    }

    fun unlock(wif: String, address: String) {
        _uiState.update {
            it.copy(
                hasStoredWallet = true,
                isLocked = false,
                address = address,
                isLoading = true,
                error = null
            )
        }
        syncWallet()
        startBackgroundSync()
    }

    fun lock() {
        storage.clearSessionWif()
        syncJob?.cancel()
        _uiState.update {
            it.copy(
                isLocked = true,
                address = "",
                confirmedBalance = 0,
                unconfirmedBalance = 0,
                transactions = emptyList(),
                utxos = emptyList(),
                error = null
            )
        }
    }

    /**
     * Switch network (mainnet/testnet).
     * Clears old data, regenerates address, and re-syncs from network.
     */
    fun switchNetwork(network: NetworkType) {
        if (network == _uiState.value.network) return

        val oldAddress = _uiState.value.address
        val oldNetwork = _uiState.value.network
        val oldNetworkName = when (oldNetwork) {
            NetworkType.MAINNET -> WalletStorage.NETWORK_MAINNET
            NetworkType.TESTNET -> WalletStorage.NETWORK_TESTNET
        }

        val networkName = when (network) {
            NetworkType.MAINNET -> WalletStorage.NETWORK_MAINNET
            NetworkType.TESTNET -> WalletStorage.NETWORK_TESTNET
        }
        storage.saveNetwork(networkName)

        // Clear old data immediately
        syncJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                confirmedBalance = 0,
                unconfirmedBalance = 0,
                transactions = emptyList(),
                utxos = emptyList(),
                blockHeight = 0,
                feeEstimates = junkwallet.domain.model.FeeEstimates(),
                error = null
            )
        }

        // Regenerate address for new network
        val wif = storage.getSessionWif()
        if (wif != null) {
            val newAddress = regenerateAddressForNetwork(wif, network)
            if (newAddress != null) {
                storage.saveAddress(newAddress)
                _uiState.update {
                    it.copy(
                        network = network,
                        address = newAddress
                    )
                }
                generateAllAddresses()

                // Clear cache for old address on old network
                viewModelScope.launch {
                    syncWalletUseCase.clearCache(oldAddress, oldNetworkName)
                    // Force fetch from new network
                    val updated = syncWalletUseCase.forceRefresh(newAddress, _uiState.value)
                    _uiState.update {
                        it.copy(
                            confirmedBalance = updated.confirmedBalance,
                            unconfirmedBalance = updated.unconfirmedBalance,
                            transactions = updated.transactions,
                            utxos = updated.utxos,
                            blockHeight = updated.blockHeight,
                            feeEstimates = updated.feeEstimates,
                            isLoading = false,
                            error = updated.error,
                            lastSynced = updated.lastSynced
                        )
                    }
                    startBackgroundSync()
                }
            } else {
                _uiState.update {
                    it.copy(
                        network = network,
                        isLoading = false,
                        error = "Failed to generate address for ${network.name.lowercase()}"
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    network = network,
                    isLoading = false,
                    error = "Wallet is locked. Unlock to switch networks."
                )
            }
        }
    }

    private fun regenerateAddressForNetwork(wif: String, network: NetworkType): String? {
        return try {
            val networkParams = when (network) {
                NetworkType.MAINNET -> junkwallet.domain.model.JunkcoinNetwork.MAINNET
                NetworkType.TESTNET -> junkwallet.domain.model.JunkcoinNetwork.TESTNET
            }
            val defaultType = storage.getDefaultAddressType()
            val keyPair = crypto.getKeyPairFromWif(wif)
            val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
            when (defaultType) {
                AddressType.P2PKH -> crypto.createP2PKHAddress(compressedPubKey, networkParams)
                AddressType.P2SH_P2WPKH -> crypto.createP2SH_P2WPKHAddress(compressedPubKey, networkParams)
                AddressType.P2WPKH -> crypto.createP2WPKHAddress(compressedPubKey, networkParams)
                AddressType.P2TR -> {
                    val privateKeyBytes = crypto.getPrivateKeyBytes(keyPair.private)
                    crypto.createP2TRAddressWithKey(privateKeyBytes, networkParams)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Set default address type and regenerate address for current network.
     */
    fun setDefaultAddressType(addressType: AddressType) {
        if (addressType == _defaultAddressType.value) return

        _defaultAddressType.value = addressType
        storage.saveDefaultAddressType(addressType.name)

        // Regenerate address for current network with new type
        val wif = storage.getSessionWif() ?: return
        val network = _uiState.value.network
        val newAddress = regenerateAddressForNetwork(wif, network)
        if (newAddress != null) {
            val oldAddress = _uiState.value.address
            storage.saveAddress(newAddress)
            _uiState.update {
                it.copy(
                    address = newAddress,
                    isLoading = true,
                    confirmedBalance = 0,
                    unconfirmedBalance = 0,
                    transactions = emptyList(),
                    utxos = emptyList(),
                    blockHeight = 0,
                    feeEstimates = junkwallet.domain.model.FeeEstimates(),
                    error = null
                )
            }
            syncJob?.cancel()

            // Clear old cache and force refresh with new address
            viewModelScope.launch {
                val networkName = when (network) {
                    NetworkType.MAINNET -> WalletStorage.NETWORK_MAINNET
                    NetworkType.TESTNET -> WalletStorage.NETWORK_TESTNET
                }
                syncWalletUseCase.clearCache(oldAddress, networkName)
                val updated = syncWalletUseCase.forceRefresh(newAddress, _uiState.value)
                _uiState.update {
                    it.copy(
                        confirmedBalance = updated.confirmedBalance,
                        unconfirmedBalance = updated.unconfirmedBalance,
                        transactions = updated.transactions,
                        utxos = updated.utxos,
                        blockHeight = updated.blockHeight,
                        feeEstimates = updated.feeEstimates,
                        isLoading = false,
                        error = updated.error,
                        lastSynced = updated.lastSynced
                    )
                }
                startBackgroundSync()
            }
        }
    }

    /**
     * Generate an address of the specified type from the wallet's private key.
     */
    fun generateAddress(type: AddressType): String? {
        val wif = storage.getSessionWif() ?: return null
        val networkParams = when (_uiState.value.network) {
            NetworkType.MAINNET -> junkwallet.domain.model.JunkcoinNetwork.MAINNET
            NetworkType.TESTNET -> junkwallet.domain.model.JunkcoinNetwork.TESTNET
        }
        return try {
            val keyPair = crypto.getKeyPairFromWif(wif)
            val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
            when (type) {
                AddressType.P2PKH -> crypto.createP2PKHAddress(compressedPubKey, networkParams)
                AddressType.P2SH_P2WPKH -> crypto.createP2SH_P2WPKHAddress(compressedPubKey, networkParams)
                AddressType.P2WPKH -> crypto.createP2WPKHAddress(compressedPubKey, networkParams)
                AddressType.P2TR -> {
                    val privateKeyBytes = crypto.getPrivateKeyBytes(keyPair.private)
                    crypto.createP2TRAddressWithKey(privateKeyBytes, networkParams)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate addresses for all supported types and cache them.
     */
    fun generateAllAddresses() {
        val wif = storage.getSessionWif() ?: return
        val networkParams = when (_uiState.value.network) {
            NetworkType.MAINNET -> junkwallet.domain.model.JunkcoinNetwork.MAINNET
            NetworkType.TESTNET -> junkwallet.domain.model.JunkcoinNetwork.TESTNET
        }
        try {
            val keyPair = crypto.getKeyPairFromWif(wif)
            val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
            val addresses = mutableMapOf<String, String>()
            for (type in networkParams.supportedAddressTypes) {
                val addr = when (type) {
                    AddressType.P2PKH -> crypto.createP2PKHAddress(compressedPubKey, networkParams)
                    AddressType.P2SH_P2WPKH -> crypto.createP2SH_P2WPKHAddress(compressedPubKey, networkParams)
                    AddressType.P2WPKH -> crypto.createP2WPKHAddress(compressedPubKey, networkParams)
                    AddressType.P2TR -> {
                        val privateKeyBytes = crypto.getPrivateKeyBytes(keyPair.private)
                        crypto.createP2TRAddressWithKey(privateKeyBytes, networkParams)
                    }
                }
                addresses[type.name] = addr
            }
            _uiState.update { it.copy(allAddresses = addresses) }
        } catch (_: Exception) { }
    }

    /**
     * Get all supported address types for current network.
     */
    fun getSupportedAddressTypes(): List<AddressType> {
        val networkParams = when (_uiState.value.network) {
            NetworkType.MAINNET -> junkwallet.domain.model.JunkcoinNetwork.MAINNET
            NetworkType.TESTNET -> junkwallet.domain.model.JunkcoinNetwork.TESTNET
        }
        return networkParams.supportedAddressTypes
    }

    /**
     * Calculate USD value from satoshis.
     */
    fun calculateUsdValue(satoshis: Long): Double {
        val jkcAmount = satoshis / 100_000_000.0
        return jkcAmount * _fiatPrice.value.usd
    }

    /**
     * Format USD value for display.
     */
    fun formatUsd(satoshis: Long): String {
        val usd = calculateUsdValue(satoshis)
        return if (usd >= 0.01) {
            String.format("$%.2f", usd)
        } else if (usd > 0) {
            String.format("$%.4f", usd)
        } else {
            "$0.00"
        }
    }

    // ── Account Management ──

    /**
     * Switch to a different account.
     */
    fun switchAccount(accountId: String) {
        val account = _uiState.value.accounts.find { it.id == accountId } ?: return

        storage.setActiveAccountId(accountId)
        storage.saveNetwork(if (account.network == NetworkType.TESTNET) WalletStorage.NETWORK_TESTNET else WalletStorage.NETWORK_MAINNET)
        storage.saveDefaultAddressType(account.defaultAddressType.name)

        syncJob?.cancel()
        _uiState.update {
            it.copy(
                activeAccount = account,
                network = account.network,
                isLoading = true,
                confirmedBalance = 0,
                unconfirmedBalance = 0,
                transactions = emptyList(),
                utxos = emptyList(),
                blockHeight = 0,
                feeEstimates = junkwallet.domain.model.FeeEstimates(),
                error = null
            )
        }

        // Regenerate address for new account's network and type
        val wif = storage.getSessionWif()
        if (wif != null) {
            val newAddress = regenerateAddressForNetwork(wif, account.network)
            if (newAddress != null) {
                storage.saveAddress(newAddress)
                _uiState.update { it.copy(address = newAddress) }
                generateAllAddresses()
                syncWallet()
                startBackgroundSync()
            }
        }
    }

    /**
     * Create a new account with a fresh WIF.
     */
    fun createAccount(name: String) {
        viewModelScope.launch {
            try {
                // Generate new key pair
                val networkParams = when (_uiState.value.network) {
                    NetworkType.MAINNET -> junkwallet.domain.model.JunkcoinNetwork.MAINNET
                    NetworkType.TESTNET -> junkwallet.domain.model.JunkcoinNetwork.TESTNET
                }
                val keyPair = crypto.generateWallet()
                val privateKeyBytes = crypto.getPrivateKeyBytes(keyPair.private)
                val wif = crypto.privateKeyToWif(privateKeyBytes, networkParams)

                val existingAccounts = storage.getAccounts()
                if (existingAccounts.isNotEmpty()) {
                    val account = WalletAccount(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        network = _uiState.value.network,
                        defaultAddressType = _defaultAddressType.value
                    )

                    val updatedAccounts = existingAccounts + account
                    storage.saveAccounts(updatedAccounts)
                    storage.setActiveAccountId(account.id)

                    _uiState.update {
                        it.copy(
                            accounts = updatedAccounts,
                            activeAccount = account
                        )
                    }

                    // Switch to the new account
                    switchAccount(account.id)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to create account: ${e.message}")
                }
            }
        }
    }

    /**
     * Rename an account.
     */
    fun renameAccount(accountId: String, newName: String) {
        val accounts = _uiState.value.accounts.map { account ->
            if (account.id == accountId) account.copy(name = newName) else account
        }
        storage.saveAccounts(accounts)

        val activeAccount = if (_uiState.value.activeAccount?.id == accountId) {
            accounts.find { it.id == accountId }
        } else {
            _uiState.value.activeAccount
        }

        _uiState.update {
            it.copy(
                accounts = accounts,
                activeAccount = activeAccount
            )
        }
    }

    /**
     * Delete an account.
     */
    fun deleteAccount(accountId: String) {
        val accounts = _uiState.value.accounts.filter { it.id != accountId }
        storage.saveAccounts(accounts)
        storage.deleteAccountStorage(accountId)

        if (_uiState.value.activeAccount?.id == accountId) {
            val newActive = accounts.firstOrNull()
            if (newActive != null) {
                switchAccount(newActive.id)
            } else {
                _uiState.update {
                    it.copy(
                        accounts = emptyList(),
                        activeAccount = null,
                        hasStoredWallet = false,
                        isLocked = true
                    )
                }
            }
        } else {
            _uiState.update { it.copy(accounts = accounts) }
        }
    }

    /**
     * Change wallet password.
     * Re-encrypts the WIF with the new password.
     */
    fun changePassword(currentPassword: String, newPassword: String): Boolean {
        val wif = storage.getDecryptedWif(currentPassword) ?: return false
        storage.saveEncryptedWif(wif, newPassword)
        return true
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
    }
}
