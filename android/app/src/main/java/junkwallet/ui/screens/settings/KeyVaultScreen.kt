package junkwallet.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.JunkcoinNetwork
import junkwallet.domain.model.NetworkType
import junkwallet.domain.model.WalletAccount
import junkwallet.domain.wallet.JunkcoinCrypto
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyVaultScreen(
    onBack: () -> Unit,
    storage: WalletStorage,
    crypto: JunkcoinCrypto
) {
    var password by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerified by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<WalletAccount>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var rootWif by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun copyToClipboard(label: String, text: String, toastMessage: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    // Determine selected account
    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()

    // Retrieve active WIF for the selected account
    val currentWif = remember(isVerified, selectedAccountId, rootWif) {
        if (!isVerified) null
        else if (selectedAccountId != null) {
            storage.getWifForAccount(selectedAccountId!!) ?: rootWif
        } else {
            rootWif
        }
    }

    // Network and chain parameters for selected account
    val accountNetwork = selectedAccount?.network ?: when (storage.getNetwork()) {
        "testnet" -> NetworkType.TESTNET
        else -> NetworkType.MAINNET
    }
    val networkParams = if (accountNetwork == NetworkType.TESTNET) JunkcoinNetwork.TESTNET else JunkcoinNetwork.MAINNET

    // Address Type state (defaults to account's defaultAddressType or P2PKH)
    val defaultType = selectedAccount?.defaultAddressType ?: storage.getDefaultAddressType()
    var selectedAddressType by remember(selectedAccountId, defaultType) {
        mutableStateOf(if (defaultType == AddressType.MWEB) AddressType.P2PKH else defaultType)
    }

    // Dynamically derive the address directly from currentWif
    val derivedAddress = remember(currentWif, networkParams, selectedAddressType) {
        if (currentWif.isNullOrBlank()) ""
        else {
            try {
                val keyPair = crypto.getKeyPairFromWif(currentWif)
                val compPubKey = crypto.getCompressedPublicKey(keyPair.public)
                when (selectedAddressType) {
                    AddressType.P2PKH -> crypto.createP2PKHAddress(compPubKey, networkParams)
                    AddressType.P2SH_P2WPKH -> crypto.createP2SH_P2WPKHAddress(compPubKey, networkParams)
                    AddressType.P2WPKH -> crypto.createP2WPKHAddress(compPubKey, networkParams)
                    AddressType.P2TR -> {
                        val privBytes = crypto.getPrivateKeyBytes(keyPair.private)
                        crypto.createP2TRAddressWithKey(privBytes, networkParams)
                    }
                    AddressType.MWEB -> crypto.createP2PKHAddress(compPubKey, networkParams)
                }
            } catch (e: Exception) {
                ""
            }
        }
    }

    // Self-healing: if the active account is selected with its default address type, ensure storage matches
    LaunchedEffect(derivedAddress, selectedAccountId) {
        if (derivedAddress.isNotBlank() && selectedAccountId == storage.getActiveAccountId()) {
            if (selectedAddressType == (selectedAccount?.defaultAddressType ?: storage.getDefaultAddressType())) {
                val cached = storage.getAddress()
                if (cached != derivedAddress) {
                    storage.saveAddress(derivedAddress)
                }
            }
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Key Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextHighEmphasis
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .imePadding()
        ) {
            // ── Security Header ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Private Key Vault",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextHighEmphasis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your private keys are encrypted with AES-256-GCM. Never reveal or share keys with anyone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password Verification ──
            if (!isVerified) {
                Text(
                    text = "Enter Password to View Keys",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHighEmphasis
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = SurfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(it, color = ErrorCrimson) }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (verifying) return@Button
                        verifying = true
                        scope.launch {
                            val wif = withContext(Dispatchers.IO) {
                                storage.getDecryptedWif(password)
                            }
                            verifying = false
                            if (wif != null) {
                                storage.saveMasterWif(wif)
                                storage.migrateSingleWalletToAccount()
                                rootWif = wif
                                val accs = storage.getAccounts()
                                accounts = accs
                                selectedAccountId = storage.getActiveAccountId() ?: accs.firstOrNull()?.id
                                isVerified = true
                                error = null
                            } else {
                                error = "Incorrect password"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotBlank() && !verifying,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = if (verifying) "Verifying…" else "Verify Password",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                // ── Account Selector ──
                if (accounts.isNotEmpty()) {
                    Text(
                        text = "Select Account",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHighEmphasis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            val isSelected = acc.id == selectedAccountId
                            val isActive = acc.id == storage.getActiveAccountId()
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAccountId = acc.id },
                                label = {
                                    Text(
                                        text = if (isActive) "${acc.name} (Active)" else acc.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = PrimaryCyan
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Key Display ──
                Text(
                    text = "Private Key (WIF) — ${selectedAccount?.name ?: "Account"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHighEmphasis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (showKey) (currentWif ?: "") else "••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextHighEmphasis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showKey) "Hide" else "Show",
                                    tint = PrimaryCyan
                                )
                            }
                            IconButton(onClick = {
                                if (!currentWif.isNullOrBlank()) {
                                    copyToClipboard("Junkcoin Private Key", currentWif, "Private key copied")
                                }
                            }) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = PrimaryCyan
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "⚠ Never share this key. Anyone with this key can access and transfer your funds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorCrimson
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Derived Address ──
                Text(
                    text = "Derived Address (Calculated from Key)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHighEmphasis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Address Format selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AddressType.P2PKH to "P2PKH (Legacy)",
                        AddressType.P2WPKH to "P2WPKH (SegWit)",
                        AddressType.P2TR to "P2TR (Taproot)",
                        AddressType.P2SH_P2WPKH to "P2SH (Wrapped)"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = selectedAddressType == type,
                            onClick = { selectedAddressType = type },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                                selectedLabelColor = PrimaryCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedAddressType.name} · ${if (accountNetwork == NetworkType.MAINNET) "Mainnet" else "Testnet"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = derivedAddress.ifBlank { "Unable to derive address" },
                            style = MaterialTheme.typography.labelLarge,
                            color = TextHighEmphasis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (derivedAddress.isNotBlank()) {
                            copyToClipboard("Junkcoin Address", derivedAddress, "Address copied")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = derivedAddress.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Copy Address")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Lock Key Vault ──
                OutlinedButton(
                    onClick = {
                        showKey = false
                        isVerified = false
                        rootWif = null
                        password = ""
                        selectedAccountId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Lock Key Vault")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Danger zone
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ErrorCrimson.copy(alpha = 0.12f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.titleMedium,
                            color = ErrorCrimson
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                storage.deleteWallet()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorCrimson
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Delete Wallet", color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            }
        }
    }
}
