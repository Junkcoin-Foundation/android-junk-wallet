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
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.FeeEstimates
import junkwallet.domain.model.JunkcoinNetwork
import junkwallet.domain.model.JunkcoinParams
import junkwallet.domain.wallet.AddressValidator
import junkwallet.domain.wallet.JunkcoinCrypto
import junkwallet.domain.wallet.TransactionBuilder
import junkwallet.domain.wallet.CoinSelector
import junkwallet.utils.QrPaymentData
import javax.inject.Inject

@HiltViewModel
class SendViewModel @Inject constructor(
    private val blockchainRepository: BlockchainRepository,
    private val storage: WalletStorage,
    private val crypto: JunkcoinCrypto,
    private val transactionBuilder: TransactionBuilder,
    private val coinSelector: CoinSelector,
    private val addressValidator: AddressValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    private val _validation = MutableStateFlow<AddressValidator.ValidationResult?>(null)
    val validation: StateFlow<AddressValidator.ValidationResult?> = _validation.asStateFlow()

    private val _addressInfo = MutableStateFlow<AddressValidator.AddressTypeInfo?>(null)
    val addressInfo: StateFlow<AddressValidator.AddressTypeInfo?> = _addressInfo.asStateFlow()

    var currentNetwork: JunkcoinParams = JunkcoinNetwork.MAINNET
        private set

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
        val sentAmountSatoshis: Long = 0
    )

    enum class Step {
        INPUT,
        CONFIRM,
        BROADCAST,
        SUCCESS
    }

    init {
        loadNetwork()
        loadBalance()
    }

    private fun loadNetwork() {
        val networkName = storage.getNetwork()
        currentNetwork = if (networkName == WalletStorage.NETWORK_TESTNET) {
            JunkcoinNetwork.TESTNET
        } else {
            JunkcoinNetwork.MAINNET
        }
    }

    private fun loadBalance() {
        viewModelScope.launch {
            try {
                val balance = blockchainRepository.getBalance(blockchainRepository.getWalletAddress())
                _uiState.update { it.copy(balance = balance) }
            } catch (_: Exception) { }
        }
    }

    fun updateRecipientAddress(address: String) {
        _uiState.update { it.copy(recipientAddress = address) }

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
        val feePerByte = _uiState.value.feePerByte
        val estimatedFee = kotlin.math.ceil((10 + 148 + 34 + 34) * feePerByte.toDouble()).toLong()
        val maxSendable = _uiState.value.balance - estimatedFee
        if (maxSendable > 0) {
            val amountStr = String.format("%.8f", maxSendable / 100_000_000.0)
            _uiState.update { it.copy(amount = amountStr, feeEstimate = estimatedFee) }
        }
    }

    private fun estimateFee() {
        viewModelScope.launch {
            try {
                val inputCount = 1
                val outputCount = 2
                val feePerByte = _uiState.value.feePerByte
                val fee = kotlin.math.ceil((10 + 148 + 34 * outputCount) * feePerByte.toDouble()).toLong()
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

                val walletAddress = blockchainRepository.getWalletAddress()
                Log.d("SendVM", "walletAddress=$walletAddress")
                val utxos = blockchainRepository.getUtxos(walletAddress)
                Log.d("SendVM", "utxos=${utxos.size}, total=${utxos.sumOf { it.value }}")
                val feePerByte = state.feePerByte

                val wif = blockchainRepository.getWif() ?: throw Exception("Wallet locked")
                Log.d("SendVM", "wif length=${wif.length}")
                val keyPair = crypto.getKeyPairFromWif(wif)
                Log.d("SendVM", "keyPair created")

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
