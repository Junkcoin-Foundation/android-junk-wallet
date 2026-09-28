package junkwallet.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import junkwallet.data.repository.BlockchainRepository
import junkwallet.data.repository.MwebRepository
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.FeeEstimates
import junkwallet.domain.model.JunkcoinNetwork
import junkwallet.domain.model.JunkcoinParams
import junkwallet.domain.wallet.AddressValidator
import junkwallet.domain.wallet.JunkcoinCrypto
import junkwallet.domain.wallet.MwebKeychain
import junkwallet.domain.wallet.TransactionBuilder
import junkwallet.domain.wallet.CoinSelector
import junkwallet.utils.QrPaymentData
import xyz.junkcoin.mweb.JunkcoinMweb
import xyz.junkcoin.mweb.MwebTxOutput
import javax.inject.Inject

@HiltViewModel
class SendViewModel @Inject constructor(
    private val blockchainRepository: BlockchainRepository,
    private val storage: WalletStorage,
    private val crypto: JunkcoinCrypto,
    private val transactionBuilder: TransactionBuilder,
    private val coinSelector: CoinSelector,
    private val addressValidator: AddressValidator,
    private val mwebRepository: MwebRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    private val _validation = MutableStateFlow<AddressValidator.ValidationResult?>(null)
    val validation: StateFlow<AddressValidator.ValidationResult?> = _validation.asStateFlow()

    private val _addressInfo = MutableStateFlow<AddressValidator.AddressTypeInfo?>(null)
    val addressInfo: StateFlow<AddressValidator.AddressTypeInfo?> = _addressInfo.asStateFlow()

    /**
     * Read from storage on every access: a mainnet/testnet switch made while
     * this ViewModel is alive must be picked up by validation and signing.
     */
    val currentNetwork: JunkcoinParams
        get() = if (storage.getNetwork() == WalletStorage.NETWORK_TESTNET) {
            JunkcoinNetwork.TESTNET
        } else {
            JunkcoinNetwork.MAINNET
        }

    enum class FeeLevel(val label: String, val satPerVByte: Long) {
        ECONOMY("Economy", 1),
        NORMAL("Normal", 10),
        PRIORITY("Priority", 30),
        CUSTOM("Custom", 10)
    }

    data class SendUiState(
        val recipientAddress: String = "",
        val amount: String = "",
        val opReturnData: String = "",
        val feeEstimate: Long = 0,
        val feePerByte: Long = 10L,
        val balance: Long = 0,
        val isAddressValid: Boolean = false,
        val isAmountValid: Boolean = false,
        val isSending: Boolean = false,
        val isSent: Boolean = false,
        val txId: String? = null,
        val error: String? = null,
        val currentStep: Step = Step.INPUT,
        val detectedAddressType: AddressType? = null,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val customFeeRate: String = "10",
        val sentAmountSatoshis: Long = 0,
        /** True while the "filled from QR" summary banner should be visible. */
        val qrBanner: Boolean = false,
        val qrFilledOpReturn: Boolean = false,
        /**
         * Funding source, mirrors the home address-type switcher. MWEB means
         * spends come from the mweb balance (confidential / peg-out); anything
         * transparent means the normal UTXO path (with MWEB as a peg-in
         * destination when the recipient validates as MWEB).
         */
        val sourceType: AddressType = AddressType.P2PKH
    )

    enum class Step {
        INPUT,
        CONFIRM,
        BROADCAST,
        SUCCESS
    }

    init {
        loadBalance()
    }

    private fun loadBalance() {
        viewModelScope.launch {
            try {
                val balance = blockchainRepository.getBalance(blockchainRepository.getWalletAddress())
                _uiState.update { it.copy(balance = balance) }
            } catch (_: Exception) { }
        }
    }

    /**
     * Point this send flow at the active funding source (the home address
     * type switcher). MWEB swaps in the mweb balance; switching back to a
     * transparent type reloads the transparent balance.
     */
    fun setSource(type: AddressType, mwebBalance: Long) {
        val previous = _uiState.value.sourceType
        if (type == AddressType.MWEB) {
            _uiState.update { it.copy(sourceType = type, balance = mwebBalance) }
            return
        }
        _uiState.update { it.copy(sourceType = type) }
        if (previous == AddressType.MWEB) {
            // balance field was showing the mweb amount
            loadBalance()
        }
    }

    fun updateRecipientAddress(address: String) {
        _uiState.update { it.copy(recipientAddress = address, qrBanner = false) }

        val validationResult = addressValidator.validate(address, currentNetwork)
        _validation.value = validationResult

        if (validationResult.isValid && validationResult.addressType != null) {
            val typeInfo = AddressValidator.getAddressTypeInfo(validationResult.addressType)
            _addressInfo.value = typeInfo
            _uiState.update {
                it.copy(isAddressValid = true, detectedAddressType = validationResult.addressType)
            }
        } else {
            _addressInfo.value = null
            _uiState.update {
                it.copy(isAddressValid = false, detectedAddressType = null)
            }
        }

        estimateFee()
    }

    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amount = amount) }
        estimateFee()
    }

    fun updateOpReturnData(data: String) {
        _uiState.update { it.copy(opReturnData = data) }
        estimateFee()
    }

    fun applyQrPaymentData(qrData: QrPaymentData) {
        updateRecipientAddress(qrData.address)
        if (qrData.amount != null) {
            updateAmount(qrData.amount)
        }
        if (qrData.opReturnMemo != null) {
            if (qrData.opReturnIsHex) {
                updateOpReturnData(qrData.opReturnMemo)
            } else {
                val hex = qrData.opReturnMemo.toByteArray()
                    .joinToString("") { "%02x".format(it) }
                updateOpReturnData(hex)
            }
        }
        // Summary banner: proof to the user that address + amount + OP_RETURN
        // were all fetched from the scanned QR.
        _uiState.update {
            it.copy(
                qrBanner = true,
                qrFilledOpReturn = qrData.opReturnMemo != null
            )
        }
    }

    fun dismissQrBanner() {
        _uiState.update { it.copy(qrBanner = false) }
    }

    fun setFeeLevel(level: FeeLevel) {
        _uiState.update {
            it.copy(
                feeLevel = level,
                feePerByte = level.satPerVByte
            )
        }
        estimateFee()
    }

    fun updateCustomFeeRate(rate: String) {
        val parsed = rate.toLongOrNull() ?: 10L
        _uiState.update {
            it.copy(
                customFeeRate = rate,
                feePerByte = parsed,
                feeLevel = FeeLevel.CUSTOM
            )
        }
        estimateFee()
    }

    fun fillMaxAmount() {
        val state = _uiState.value
        val feePerByte = state.feePerByte
        if (state.sourceType == AddressType.MWEB) {
            // Spend from mweb balance: amount + kernel fee must fit. The
            // estimate covers confidential (2 mweb outputs) and peg-out
            // shapes; estimateFee() refines it once the recipient is typed.
            viewModelScope.launch {
                val fee = estimateMwebSpendFee(state.recipientAddress, feePerByte)
                val maxSendable = state.balance - fee
                if (maxSendable > 0) {
                    val amountStr = String.format("%.8f", maxSendable / 100_000_000.0)
                    _uiState.update { it.copy(amount = amountStr, feeEstimate = fee) }
                }
            }
            return
        }
        if (state.detectedAddressType == AddressType.MWEB) {
            // Peg-in: amount + kernel fee + transparent fee must fit the
            // balance. The kernel fee is size based (independent of value).
            viewModelScope.launch {
                val mwebFee = estimateMwebFee(state.recipientAddress, 1L, feePerByte)
                val ltcFee = peginLtcFee(feePerByte)
                val maxSendable = state.balance - mwebFee - ltcFee
                if (maxSendable > 0) {
                    val amountStr = String.format("%.8f", maxSendable / 100_000_000.0)
                    _uiState.update { it.copy(amount = amountStr, feeEstimate = mwebFee + ltcFee) }
                }
            }
            return
        }
        val estimatedFee = kotlin.math.ceil((10 + 148 + 34 + 34) * feePerByte.toDouble()).toLong()
        val maxSendable = state.balance - estimatedFee
        if (maxSendable > 0) {
            val amountStr = String.format("%.8f", maxSendable / 100_000_000.0)
            _uiState.update { it.copy(amount = amountStr, feeEstimate = estimatedFee) }
        }
    }

    /** mweb.Chain string ("mainnet"/"testnet") for the MWEB daemon API. */
    private val mwebChain: String
        get() = if (storage.getNetwork() == WalletStorage.NETWORK_TESTNET) "testnet" else "mainnet"

    /** Kernel fee for one MWEB recipient output at the given rate. */
    private suspend fun estimateMwebFee(recipient: String, amountSat: Long, feePerByte: Long): Long =
        try {
            JunkcoinMweb.estimateFee(
                mwebChain,
                listOf(MwebTxOutput(recipient, amountSat)),
                feePerByte * 1000
            )
        } catch (e: Exception) {
            Log.e("SendVM", "mweb fee estimate failed: ${e.message}")
            0L
        }

    /** First MWEB address of the pool (the daemon's change template). */
    private suspend fun mwebChangeAddress(): String? = try {
        val wif = storage.getSessionWif() ?: return null
        val keys = MwebKeychain.fromWif(wif) ?: return null
        mwebRepository.getAddresses(keys.scanSecret, keys.spendPub, 0, 1).firstOrNull()
    } catch (e: Exception) {
        null
    }

    /**
     * Kernel fee for spending from the mweb balance: outputs are the
     * recipient plus a change output, matching the coin selection in
     * MwebRepository.createTransaction. With no recipient typed yet the
     * estimate assumes the usual two-output shape; estimateFee() refines it
     * once the address field is filled.
     */
    private suspend fun estimateMwebSpendFee(recipient: String, feePerByte: Long): Long {
        val change = mwebChangeAddress()
        val outputs = when {
            recipient.isNotBlank() && change != null ->
                listOf(MwebTxOutput(recipient, 0L), MwebTxOutput(change, 0L))
            recipient.isNotBlank() ->
                listOf(MwebTxOutput(recipient, 0L))
            change != null ->
                listOf(MwebTxOutput(change, 0L), MwebTxOutput(change, 0L))
            else -> return 0L
        }
        return try {
            JunkcoinMweb.estimateFee(mwebChain, outputs, feePerByte * 1000)
        } catch (e: Exception) {
            Log.e("SendVM", "mweb spend fee estimate failed: ${e.message}")
            0L
        }
    }

    /**
     * Transparent part of a peg-in fee: overhead + one legacy sized input +
     * pegin output + worst case change output (mirrors
     * MwebRepository.createPegin).
     */
    private fun peginLtcFee(feePerByte: Long): Long = (10 + 148 + 43 + 43) * feePerByte

    private fun estimateFee() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val feePerByte = state.feePerByte
                if (state.sourceType == AddressType.MWEB) {
                    // Confidential or peg-out: kernel fee only, shaped like
                    // createTransaction's coin selection.
                    val fee = estimateMwebSpendFee(state.recipientAddress, feePerByte)
                    _uiState.update { it.copy(feeEstimate = fee) }
                    return@launch
                }
                if (state.detectedAddressType == AddressType.MWEB &&
                    state.recipientAddress.isNotBlank()
                ) {
                    val amountSat = jkcToSatoshis(state.amount)
                    if (amountSat > 0) {
                        val mwebFee = estimateMwebFee(state.recipientAddress, amountSat, feePerByte)
                        _uiState.update {
                            it.copy(feeEstimate = mwebFee + peginLtcFee(feePerByte))
                        }
                        return@launch
                    }
                }
                val fee = kotlin.math.ceil((10 + 148 + 34 * 2) * feePerByte.toDouble()).toLong()
                _uiState.update { it.copy(feeEstimate = fee) }
            } catch (e: Exception) {
                _uiState.update { it.copy(feeEstimate = 1000L) }
            }
        }
    }

    fun startConfirmation() {
        val state = _uiState.value
        if (!state.isAddressValid || state.amount.toDoubleOrNull() == null) {
            _uiState.update { it.copy(error = "Invalid address or amount") }
            return
        }
        val satoshis = jkcToSatoshis(state.amount)
        if (state.sourceType == AddressType.MWEB && satoshis > state.balance) {
            _uiState.update { it.copy(error = "Amount exceeds available MWEB balance") }
            return
        }
        _uiState.update {
            it.copy(
                currentStep = Step.CONFIRM,
                sentAmountSatoshis = satoshis
            )
        }
    }

    private fun jkcToSatoshis(jkcString: String): Long {
        val jkc = jkcString.toDoubleOrNull() ?: return 0L
        return (jkc * 100_000_000).toLong()
    }

    private fun satoshisToJkc(satoshis: Long): String {
        return String.format("%.8f", satoshis / 100_000_000.0)
    }

    fun broadcast() {
        viewModelScope.launch {
            Log.d("SendVM", "=== BROADCAST START ===")
            _uiState.update { it.copy(currentStep = Step.BROADCAST, isSending = true, error = null) }
            try {
                val state = _uiState.value
                Log.d("SendVM", "amount=${state.amount}, recipient=${state.recipientAddress}, feePerByte=${state.feePerByte}")
                val amountSatoshis = jkcToSatoshis(state.amount)
                Log.d("SendVM", "amountSatoshis=$amountSatoshis")
                if (amountSatoshis <= 0) throw Exception("Invalid amount")

                if (state.sourceType == AddressType.MWEB) {
                    // Spend from mweb balance (confidential tjcmweb1.. or
                    // peg-out to a transparent address): built and broadcast
                    // entirely by the daemon.
                    Log.d("SendVM", "Building MWEB spend: amount=$amountSatoshis")
                    val wif = blockchainRepository.getWif() ?: throw Exception("Wallet locked")
                    val keychain = MwebKeychain.fromWif(wif)
                        ?: throw Exception("MWEB keys unavailable")
                    val rawTx = mwebRepository.createTransaction(
                        scanSecret = keychain.scanSecret,
                        spendSecret = keychain.spendSecret,
                        spendPub = keychain.spendPub,
                        recipientAddress = state.recipientAddress,
                        amount = amountSatoshis,
                        feeRatePerKb = state.feePerByte * 1000
                    ) ?: throw Exception("Failed to create MWEB transaction")
                    val mwebTxid = mwebRepository.broadcast(rawTx)
                        ?: throw Exception("Failed to broadcast MWEB transaction")
                    Log.d("SendVM", "MWEB spend broadcast: $mwebTxid")
                    _uiState.update {
                        it.copy(
                            currentStep = Step.SUCCESS,
                            isSending = false,
                            isSent = true,
                            txId = mwebTxid,
                            sentAmountSatoshis = amountSatoshis
                        )
                    }
                    return@launch
                }

                val walletAddress = blockchainRepository.getWalletAddress()
                Log.d("SendVM", "walletAddress=$walletAddress")
                val utxos = blockchainRepository.getUtxos(walletAddress)
                Log.d("SendVM", "utxos=${utxos.size}, total=${utxos.sumOf { it.value }}")
                val feePerByte = state.feePerByte

                val wif = blockchainRepository.getWif() ?: throw Exception("Wallet locked")
                Log.d("SendVM", "wif length=${wif.length}")
                val keyPair = crypto.getKeyPairFromWif(wif)
                Log.d("SendVM", "keyPair created")

                if (state.detectedAddressType == AddressType.MWEB) {
                    // Peg-in: transparent UTXOs -> MWEB output, built and
                    // signed by the daemon, broadcast through the daemon
                    // (the transaction carries an MWEB body).
                    val keychain = MwebKeychain.fromWif(wif)
                        ?: throw Exception("MWEB keys unavailable")
                    val privKeyHex = crypto.getPrivateKeyBytes(keyPair.private)
                        .joinToString("") { "%02x".format(it) }
                    Log.d("SendVM", "Building peg-in: amount=$amountSatoshis")
                    val pegin = mwebRepository.createPegin(
                        utxos = utxos,
                        recipientAddress = state.recipientAddress,
                        amount = amountSatoshis,
                        walletAddress = walletAddress,
                        privKeyHex = privKeyHex,
                        scanSecret = keychain.scanSecret,
                        spendSecret = keychain.spendSecret,
                        feePerByte = feePerByte
                    ) ?: throw Exception("Failed to build peg-in transaction")
                    Log.d("SendVM", "Peg-in built: txid=${pegin.txid}, fee=${pegin.fee}")
                    val peginTxid = mwebRepository.broadcast(pegin.rawTx)
                        ?: throw Exception("Failed to broadcast peg-in")
                    Log.d("SendVM", "Peg-in broadcast: $peginTxid")
                    _uiState.update {
                        it.copy(
                            currentStep = Step.SUCCESS,
                            isSending = false,
                            isSent = true,
                            txId = peginTxid,
                            feeEstimate = pegin.fee,
                            sentAmountSatoshis = amountSatoshis
                        )
                    }
                    return@launch
                }

                Log.d("SendVM", "Creating transaction...")
                val result = transactionBuilder.createTransaction(
                    keyPair = keyPair,
                    utxos = utxos,
                    recipientAddress = state.recipientAddress,
                    amount = amountSatoshis,
                    feePerByte = feePerByte,
                    network = currentNetwork,
                    changeAddress = walletAddress,
                    opReturnData = state.opReturnData.hexToByteArrayOrNull()
                )
                Log.d("SendVM", "Transaction created: txId=${result.txId}, fee=${result.fee}, rawTx length=${result.rawTx.length}")
                Log.d("SendVM", "rawTx(first100)=${result.rawTx.take(100)}")
                Log.d("SendVM", "rawTx(inputs=${result.inputCount}, outputs=${result.outputCount})")

                Log.d("SendVM", "Broadcasting to API...")
                val broadcastResult = blockchainRepository.broadcastTransaction(result.rawTx)
                Log.d("SendVM", "Broadcast result=$broadcastResult")
                if (broadcastResult) {
                    _uiState.update {
                        it.copy(
                            currentStep = Step.SUCCESS,
                            isSending = false,
                            isSent = true,
                            txId = result.txId,
                            feeEstimate = result.fee,
                            sentAmountSatoshis = amountSatoshis
                        )
                    }
                } else {
                    throw Exception("Failed to broadcast transaction")
                }
            } catch (e: Exception) {
                Log.e("SendVM", "BROADCAST FAILED: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        currentStep = Step.INPUT,
                        isSending = false,
                        error = e.message ?: "Failed to send transaction"
                    )
                }
            }
        }
    }

    fun previousStep() {
        val currentStep = _uiState.value.currentStep
        when (currentStep) {
            Step.CONFIRM -> _uiState.update { it.copy(currentStep = Step.INPUT) }
            Step.BROADCAST -> _uiState.update { it.copy(currentStep = Step.CONFIRM) }
            else -> {}
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.value = SendUiState()
        _validation.value = null
        _addressInfo.value = null
    }

    private fun String.hexToByteArrayOrNull(): ByteArray? {
        return try {
            if (isEmpty()) null
            else chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}
