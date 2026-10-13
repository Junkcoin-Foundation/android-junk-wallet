package junkwallet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import junkwallet.data.repository.BlockchainRepository
import junkwallet.data.repository.MwebRepository
import junkwallet.data.repository.PriceRepository
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.FiatPrice
import android.util.Log
import junkwallet.domain.model.NetworkType
import junkwallet.domain.model.JunkcoinNetwork
import junkwallet.domain.model.JunkcoinParams
import junkwallet.domain.model.TransactionInfo
import junkwallet.domain.model.WalletAccount
import junkwallet.domain.model.WalletState
import junkwallet.domain.usecase.GetPriceUseCase
import junkwallet.domain.usecase.SyncWalletUseCase
import junkwallet.domain.wallet.AddressValidator
import junkwallet.domain.wallet.JunkcoinCrypto
import junkwallet.domain.wallet.MwebKeychain
import xyz.junkcoin.mweb.JunkcoinMweb
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val syncWalletUseCase: SyncWalletUseCase,
    private val getPriceUseCase: GetPriceUseCase,
    private val storage: WalletStorage,
    private val priceRepository: PriceRepository,
    private val crypto: JunkcoinCrypto,
    private val addressValidator: AddressValidator,
    private val mwebRepository: MwebRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletState())
    val uiState: StateFlow<WalletState> = _uiState.asStateFlow()

    private val _fiatPrice = MutableStateFlow(FiatPrice())
    val fiatPrice: StateFlow<FiatPrice> = _fiatPrice.asStateFlow()

    private val _defaultAddressType = MutableStateFlow(AddressType.P2PKH)
    val defaultAddressType: StateFlow<AddressType> = _defaultAddressType.asStateFlow()

    private val _generatedAddresses = MutableStateFlow<Map<AddressType, String>>(emptyMap())
    val generatedAddresses: StateFlow<Map<AddressType, String>> = _generatedAddresses.asStateFlow()

    // MWEB state
    private val _mwebBalance = MutableStateFlow(0L)
    val mwebBalance: StateFlow<Long> = _mwebBalance.asStateFlow()

    private val _mwebAddresses = MutableStateFlow<List<String>>(emptyList())
    val mwebAddresses: StateFlow<List<String>> = _mwebAddresses.asStateFlow()

    private val _isMwebRunning = MutableStateFlow(false)
    val isMwebRunning: StateFlow<Boolean> = _isMwebRunning.asStateFlow()

    private var syncJob: Job? = null
    private val syncIntervalMs = 45_000L // 45 seconds

    init {
        loadWallet()
        loadPrice()
        loadDefaultAddressType()
        startMwebDaemon()
    }

    private fun loadWallet() {
        try {
            // Migrate single wallet to account if needed
            storage.migrateSingleWalletToAccount()

            val accounts = storage.getAccounts()
            val activeAccountId = storage.getActiveAccountId()
            val activeAccount = accounts.find { it.id == activeAccountId }

            val address = storage.getAddress() ?: return
            val network = storedNetworkType()

            // The active account's key wins over the root key once unlocked.
            applyActiveAccountKey()
            val unlocked = storage.getSessionWif() != null

            _uiState.update {
                it.copy(
                    hasStoredWallet = true,
                    isLocked = !unlocked,
                    address = address,
                    network = network,
                    isLoading = unlocked,
                    accounts = accounts,
                    activeAccount = activeAccount
                )
            }

            if (!unlocked) return // cold start: no session key yet, wait for unlock

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

    /** Network as persisted in storage. */
    private fun storedNetworkType(): NetworkType =
        if (storage.getNetwork() == "testnet") NetworkType.TESTNET else NetworkType.MAINNET

    /** Canonical name for a network (storage key / cache key). */
    private fun networkName(network: NetworkType): String =
        if (network == NetworkType.TESTNET) "testnet" else "mainnet"

    /**
     * Single source of truth for chain parameters. Replace the scattered
     * `when (network)` blocks with this registry.
     */
    private fun paramsFor(network: NetworkType): JunkcoinParams =
        if (network == NetworkType.TESTNET) JunkcoinNetwork.TESTNET else JunkcoinNetwork.MAINNET

    /**
     * Make the session key follow the selected account. No-op while locked
     * (no root key in memory). Falls back to the root key for accounts that
     * predate per-account keys.
     */
    private fun applyActiveAccountKey(): String? {
        val root = storage.getMasterWif() ?: return null
        val activeId = storage.getActiveAccountId()
        val accountId = storage.getAccounts().find { it.id == activeId }?.id ?: run {
            storage.setActiveAccountWif(root)
            return root
        }

        storage.getAccountWif(accountId)?.let {
            storage.setActiveAccountWif(it)
            return it
        }

        if (!storage.hasAccountWif(accountId)) {
            // Account from before per-account keys: adopt the root key.
            storage.saveAccountEncryptedWif(accountId, root)
        }
        // If a key exists but cannot be decrypted, keep the root key WITHOUT
        // overwriting the stored ciphertext (never destroy recoverable data).
        storage.setActiveAccountWif(root)
        return root
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

    private fun mergeWithMwebTransactions(transparentTxs: List<TransactionInfo>): List<TransactionInfo> {
        val activeAccount = storage.getActiveAccountId() ?: ""
        val netName = networkName(_uiState.value.network)
        val mwebTxs = storage.getMwebTransactions(activeAccount, netName)
        if (mwebTxs.isEmpty()) return transparentTxs

        val mwebMap = mwebTxs.associateBy { it.txid }
        val merged = transparentTxs.map { tx ->
            mwebMap[tx.txid] ?: tx
        }
        val transparentTxids = transparentTxs.map { it.txid }.toSet()
        val onlyMweb = mwebTxs.filterNot { it.txid in transparentTxids }
        return (merged + onlyMweb).sortedByDescending { it.blockTime ?: 0L }
    }

    fun syncWallet() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                Log.d("WalletVM", "Syncing ${_uiState.value.network.name} address=${_uiState.value.address}")
                val updated = syncWalletUseCase.sync(_uiState.value.address, _uiState.value)
                val mergedTxs = mergeWithMwebTransactions(updated.transactions)
                _uiState.update {
                    it.copy(
                        confirmedBalance = updated.confirmedBalance,
                        unconfirmedBalance = updated.unconfirmedBalance,
                        transactions = mergedTxs,
                        utxos = updated.utxos,
                        blockHeight = updated.blockHeight,
                        feeEstimates = updated.feeEstimates,
                        isLoading = updated.isLoading,
                        error = updated.error,
                        lastSynced = updated.lastSynced
                    )
                }
                Log.d("WalletVM", "Sync complete: balance=${updated.confirmedBalance} txs=${mergedTxs.size}")

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
                val mergedTxs = mergeWithMwebTransactions(updated.transactions)
                _uiState.update {
                    it.copy(
                        confirmedBalance = updated.confirmedBalance,
                        unconfirmedBalance = updated.unconfirmedBalance,
                        transactions = mergedTxs,
                        utxos = updated.utxos,
                        blockHeight = updated.blockHeight,
                        feeEstimates = updated.feeEstimates,
                        isLoading = updated.isLoading,
                        error = updated.error,
                        lastSynced = updated.lastSynced
                    )
                }
                Log.d("WalletVM", "Force refresh complete: balance=${updated.confirmedBalance} txs=${mergedTxs.size}")

                // MWEB half of the dashboard refresh (local daemon): balance
                // every time, pool registration until it has landed.
                if (!_isMwebRunning.value) ensureMwebReady()
                if (_isMwebRunning.value) syncMwebBalance()
                if (_uiState.value.allAddresses[AddressType.MWEB.name] == null) {
                    getMwebAddresses()
                }

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
                    // MWEB is a local daemon: keep it up and its dashboard
                    // data fresh on every tick, regardless of which type
                    // the switcher is showing.
                    if (!_isMwebRunning.value) ensureMwebReady()
                    if (_isMwebRunning.value) syncMwebBalance()
                    if (_uiState.value.allAddresses[AddressType.MWEB.name] == null) {
                        // Pool registration can fail early (locked/daemon not
                        // ready): retry until it lands in the switcher.
                        getMwebAddresses()
                    }
                }
            }
        }
    }

    /**
     * Unlock with the ROOT key (password/PIN/biometric).
     * Re-derives the active account's key and address so the UI always shows
     * data for the selected account/network, not a stale snapshot.
     */
    suspend fun unlock(wif: String, address: String) {
        // Root key in memory; active account key on top of it.
        storage.saveMasterWif(wif)
        // Deriving the account key runs PBKDF2: never do that on the main
        // thread (it froze the UI for seconds and triggered ANRs).
        val accountWif = withContext(Dispatchers.IO) { applyActiveAccountKey() } ?: wif

        val network = storedNetworkType()
        val accounts = storage.getAccounts()
        val activeAccount = accounts.find { it.id == storage.getActiveAccountId() }
        _defaultAddressType.value = activeAccount?.defaultAddressType
            ?: storage.getDefaultAddressType()

        // Self-heal: derive the address from what is actually selected now.
        val resolvedAddress = regenerateAddressForNetwork(accountWif, network) ?: address
        if (resolvedAddress != address) storage.saveAddress(resolvedAddress)

        _uiState.update {
            it.copy(
                hasStoredWallet = true,
                isLocked = false,
                address = resolvedAddress,
                network = network,
                isLoading = true,
                accounts = accounts,
                activeAccount = activeAccount,
                error = null
            )
        }
        generateAllAddresses()
        syncWallet()
        startBackgroundSync()
        // Session keys now exist: register the MWEB pool + balance right
        // away so the dashboard switcher offers MWEB without requiring a
        // visit to Receive first (init ran while the wallet was locked and
        // this fetch failed for lack of keys, with no retry).
        startMwebDaemon()
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
        val oldNetworkName = networkName(oldNetwork)
        val targetNetworkName = networkName(network)
        storage.saveNetwork(targetNetworkName)

        // Keep all accounts in sync with the global network
        val allAccounts = storage.getAccounts()
        val activeAccount = allAccounts.find { it.id == storage.getActiveAccountId() }

        // The MWEB daemon is chain specific: restart it for the new network
        // (this also refreshes the MWEB addresses and balance).
        startMwebDaemon()

        // Clear old data immediately
        syncJob?.cancel()
        _mwebAddresses.value = emptyList()
        _mwebBalance.value = 0L
        _uiState.update {
            it.copy(
                isLoading = true,
                network = network,
                accounts = allAccounts,
                activeAccount = activeAccount,
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
                    it.copy(address = newAddress)
                }
                generateAllAddresses()

                // Clear cache for old address on old network (drops the network
                // meta too, so the new network can never look "fresh" for the
                // old address).
                viewModelScope.launch {
                    syncWalletUseCase.clearCache(oldAddress, oldNetworkName)
                    // Force fetch from new network
                    val updated = syncWalletUseCase.forceRefresh(newAddress, _uiState.value)
                    _uiState.update {
                        it.copy(
                            confirmedBalance = updated.confirmedBalance,
                            unconfirmedBalance = updated.unconfirmedBalance,
                            transactions = mergeWithMwebTransactions(updated.transactions),
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
                        isLoading = false,
                        error = "Failed to generate address for ${network.name.lowercase()}"
                    )
                }
            }
        } else {
            // Locked: the network is persisted now; unlock() derives the right
            // address for it, so there is nothing else to do (and no error).
            _uiState.update {
                it.copy(isLoading = false)
            }
        }
    }

    /** Persist an edit on the currently active account (if any). */
    private fun updateActiveAccount(transform: (WalletAccount) -> WalletAccount) {
        val accounts = storage.getAccounts()
        val activeId = storage.getActiveAccountId()
        val index = accounts.indexOfFirst { it.id == activeId }
        if (index < 0) return
        val updated = accounts.toMutableList()
        updated[index] = transform(updated[index])
        storage.saveAccounts(updated)
        _uiState.update { it.copy(accounts = updated, activeAccount = updated[index]) }
    }

    private fun regenerateAddressForNetwork(wif: String, network: NetworkType): String? {
        return try {
            val networkParams = paramsFor(network)
            val defaultType = _defaultAddressType.value
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
                AddressType.MWEB -> {
                    val keys = MwebKeychain.fromWif(wif) ?: return null
                    val chainStr = if (network == NetworkType.TESTNET) "testnet" else "mainnet"
                    JunkcoinMweb.addressesFor(chainStr, keys.scanSecret, keys.spendPub, 0, 1).firstOrNull()
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

        if (addressType == AddressType.MWEB) {
            // MWEB addresses come from the daemon pool, not local derivation,
            // and the transparent address/sync must keep running untouched:
            // only the active type (displayed address + balance source) flips.
            if (_mwebAddresses.value.isEmpty()) {
                _uiState.update { it.copy(error = "MWEB is not ready yet") }
                return
            }
            _defaultAddressType.value = addressType
            storage.saveDefaultAddressType(addressType.name)
            updateActiveAccount { it.copy(defaultAddressType = addressType) }
            return
        }

        if (!getSupportedAddressTypes().contains(addressType)) {
            _uiState.update {
                it.copy(error = "${addressType.name} is not available on ${_uiState.value.network.name.lowercase()}")
            }
            return
        }
        _defaultAddressType.value = addressType
        storage.saveDefaultAddressType(addressType.name)
        updateActiveAccount { it.copy(defaultAddressType = addressType) }

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
                syncWalletUseCase.clearCache(oldAddress, networkName(network))
                val updated = syncWalletUseCase.forceRefresh(newAddress, _uiState.value)
                _uiState.update {
                    it.copy(
                        confirmedBalance = updated.confirmedBalance,
                        unconfirmedBalance = updated.unconfirmedBalance,
                        transactions = mergeWithMwebTransactions(updated.transactions),
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
                it.copy(isLoading = false, error = "Failed to generate ${addressType.name} address")
            }
        }
    }

    /**
     * Generate an address of the specified type from the wallet's private key.
     */
    fun generateAddress(type: AddressType): String? {
        val wif = storage.getSessionWif() ?: return null
        val networkParams = paramsFor(_uiState.value.network)
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
                AddressType.MWEB -> {
                    val keys = MwebKeychain.fromWif(wif) ?: return null
                    val chainStr = if (_uiState.value.network == NetworkType.TESTNET) "testnet" else "mainnet"
                    JunkcoinMweb.addressesFor(chainStr, keys.scanSecret, keys.spendPub, 0, 1).firstOrNull()
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
        val networkParams = paramsFor(_uiState.value.network)
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
                    AddressType.MWEB -> {
                        val keys = MwebKeychain.fromWif(wif)
                        if (keys != null) {
                            val chainStr = if (_uiState.value.network == NetworkType.TESTNET) "testnet" else "mainnet"
                            val mwebList = JunkcoinMweb.addressesFor(chainStr, keys.scanSecret, keys.spendPub, 0, 10)
                            if (mwebList.isNotEmpty()) {
                                _mwebAddresses.value = mwebList
                                mwebList.first()
                            } else null
                        } else null
                    }
                } ?: continue
                addresses[type.name] = addr
            }
            _mwebAddresses.value.firstOrNull()?.let { mwebAddr ->
                addresses[AddressType.MWEB.name] = mwebAddr
            }
            _uiState.update { it.copy(allAddresses = addresses) }
        } catch (_: Exception) { }
    }

    /**
     * Get all supported address types for current network.
     * MWEB is excluded: it cannot be derived without mwebd.
     */
    fun getSupportedAddressTypes(): List<AddressType> {
        return paramsFor(_uiState.value.network).supportedAddressTypes
            .filter { it != AddressType.MWEB }
    }

    /**
     * Address types offered on the Receive screen. Unlike
     * [getSupportedAddressTypes] this keeps MWEB: the receive flow reads the
     * address from the daemon-derived pool instead of deriving it locally.
     */
    fun getReceiveAddressTypes(): List<AddressType> {
        return paramsFor(_uiState.value.network).supportedAddressTypes
    }

    /**
     * Validate a recipient address against the current network.
     */
    fun validateRecipient(address: String): AddressValidator.ValidationResult {
        return addressValidator.validate(address, paramsFor(_uiState.value.network))
    }

    /**
     * Make sure the MWEB daemon is up and the address pool is populated.
     * Idempotent: safe to call every time Receive opens.
     */
    fun ensureMwebReady() {
        startMwebDaemon()
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
        val account = storage.getAccounts().find { it.id == accountId } ?: return
        val oldAddress = _uiState.value.address
        val currentNetwork = storedNetworkType()
        val currentNetworkName = networkName(currentNetwork)

        storage.setActiveAccountId(accountId)
        storage.saveDefaultAddressType(account.defaultAddressType.name)
        _defaultAddressType.value = account.defaultAddressType

        // Reset MWEB state immediately for previous account
        _mwebAddresses.value = emptyList()
        _mwebBalance.value = 0L

        // Swap the session key to this account's key (falls back to the root
        // key for accounts created before per-account keys existed).
        val wif = applyActiveAccountKey() ?: storage.getMasterWif() ?: storage.getSessionWif()

        syncJob?.cancel()
        _uiState.update {
            it.copy(
                activeAccount = account,
                network = currentNetwork,
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

        if (wif == null) {
            // Locked: selection is persisted, unlock() will derive its address.
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        // Regenerate address for the new account using the global network and type
        val newAddress = regenerateAddressForNetwork(wif, currentNetwork)
        if (newAddress != null) {
            storage.saveAddress(newAddress)
            _uiState.update { it.copy(address = newAddress) }
            generateAllAddresses()
            viewModelScope.launch {
                // Drop what belonged to the previous account.
                syncWalletUseCase.clearCache(oldAddress, currentNetworkName)
                syncWallet()
                syncMwebBalance()
            }
            startBackgroundSync()
        } else {
            _uiState.update {
                it.copy(isLoading = false, error = "Failed to generate address for ${account.name}")
            }
        }
    }

    /**
     * Create a new account with a fresh WIF.
     */
    fun createAccount(name: String) {
        viewModelScope.launch {
            try {
                if (storage.getSessionWif() == null) {
                    _uiState.update { it.copy(error = "Unlock the wallet to create accounts") }
                    return@launch
                }

                // Legacy installs only have a single-wallet record — turn it
                // into "Account 1" first so the new account is never silently
                // dropped.
                storage.migrateSingleWalletToAccount()
                val existingAccounts = storage.getAccounts()

                // Generate new key pair
                val networkParams = paramsFor(_uiState.value.network)
                val keyPair = crypto.generateWallet()
                val privateKeyBytes = crypto.getPrivateKeyBytes(keyPair.private)
                val wif = crypto.privateKeyToWif(privateKeyBytes, networkParams)

                val account = WalletAccount(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    network = _uiState.value.network,
                    defaultAddressType = _defaultAddressType.value
                )

                val updatedAccounts = existingAccounts + account
                storage.saveAccounts(updatedAccounts)
                storage.setActiveAccountId(account.id)
                // Every new account gets its OWN key, encrypted under the root key.
                storage.saveAccountEncryptedWif(account.id, wif)

                _uiState.update {
                    it.copy(
                        accounts = updatedAccounts,
                        activeAccount = account
                    )
                }

                // Switch to the new account
                switchAccount(account.id)
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

        if (accounts.isEmpty()) {
            // Last account gone → there is no wallet left on this device.
            storage.deleteWallet()
            syncJob?.cancel()
            _uiState.update {
                it.copy(
                    accounts = emptyList(),
                    activeAccount = null,
                    hasStoredWallet = false,
                    isLocked = true,
                    address = "",
                    allAddresses = emptyMap(),
                    confirmedBalance = 0,
                    unconfirmedBalance = 0,
                    transactions = emptyList(),
                    utxos = emptyList(),
                    blockHeight = 0,
                    feeEstimates = junkwallet.domain.model.FeeEstimates(),
                    error = null
                )
            }
            return
        }

        if (_uiState.value.activeAccount?.id == accountId) {
            switchAccount(accounts.first().id)
        } else {
            _uiState.update { it.copy(accounts = accounts) }
        }
    }

    /**
     * Change wallet password.
     * Re-encrypts the WIF with the new password.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean =
        withContext(Dispatchers.IO) {
            val wif = storage.getDecryptedWif(currentPassword) ?: return@withContext false
            storage.saveEncryptedWif(wif, newPassword)
            true
        }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        mwebRepository.stop()
    }

    // ── MWEB Methods ──

    /**
     * Start MWEB daemon
     */
    private fun startMwebDaemon() {
        viewModelScope.launch {
            try {
                val started = mwebRepository.start()
                _isMwebRunning.value = started
                if (started) {
                    Log.d("WalletVM", "MWEB daemon started")
                    getMwebAddresses()
                    syncMwebBalance()
                }
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to start MWEB daemon: ${e.message}")
                _isMwebRunning.value = false
            }
        }
    }

    /**
     * MWEB scan/spend keys for the active account, derived deterministically
     * from its private key (see [MwebKeychain]).
     */
    private fun mwebKeychain(): MwebKeychain? {
        val wif = storage.getSessionWif() ?: return null
        return MwebKeychain.fromWif(wif)
    }

    /**
     * Sync MWEB balance
     */
    fun syncMwebBalance() {
        viewModelScope.launch {
            try {
                val keys = mwebKeychain() ?: run {
                    Log.w("WalletVM", "No MWEB keys: wallet locked")
                    return@launch
                }
                val balance = mwebRepository.getBalance(keys.scanSecret)
                _mwebBalance.value = balance
                Log.d("WalletVM", "MWEB balance: $balance sat")

                val utxos = mwebRepository.getUtxos(keys.scanSecret)
                val activeAccount = storage.getActiveAccountId() ?: ""
                val netName = networkName(_uiState.value.network)
                val existingMwebTxs = storage.getMwebTransactions(activeAccount, netName)
                for (utxo in utxos) {
                    val alreadyRecorded = existingMwebTxs.any { it.txid == utxo.outputId }
                    if (!alreadyRecorded) {
                        val rxTx = TransactionInfo(
                            txid = utxo.outputId,
                            confirmed = true,
                            blockHeight = null,
                            blockTime = if (utxo.blockTime > 0) utxo.blockTime else System.currentTimeMillis() / 1000,
                            fee = 0L,
                            sent = 0L,
                            received = utxo.value,
                            isSent = false,
                            isMweb = true
                        )
                        storage.saveMwebTransaction(activeAccount, netName, rxTx)
                    }
                }
                val mergedTxs = mergeWithMwebTransactions(_uiState.value.transactions)
                _uiState.update { it.copy(transactions = mergedTxs) }
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to sync MWEB balance: ${e.message}")
            }
        }
    }

    /**
     * Get MWEB addresses
     */
    fun getMwebAddresses(from: Int = 0, to: Int = 10) {
        viewModelScope.launch {
            try {
                val keys = mwebKeychain() ?: run {
                    Log.w("WalletVM", "No MWEB keys: wallet locked")
                    return@launch
                }
                val addresses = mwebRepository.getAddresses(keys.scanSecret, keys.spendPub, from, to)
                _mwebAddresses.value = addresses
                if (addresses.isNotEmpty()) {
                    // Splice into the home switcher map as soon as the pool is
                    // ready (generateAllAddresses skips MWEB: not derivable).
                    _uiState.update {
                        it.copy(
                            allAddresses = it.allAddresses +
                                (AddressType.MWEB.name to addresses.first())
                        )
                    }
                }
                Log.d("WalletVM", "MWEB addresses: ${addresses.size}")
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to get MWEB addresses: ${e.message}")
            }
        }
    }
}
