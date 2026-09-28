package junkwallet.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import junkwallet.data.storage.SettingsStorage
import junkwallet.data.storage.WalletStorage
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import junkwallet.ui.navigation.BottomNavBar
import junkwallet.domain.model.TransactionInfo
import junkwallet.ui.navigation.Screen
import junkwallet.ui.screens.dashboard.DashboardScreen
import junkwallet.ui.screens.history.HistoryScreen
import junkwallet.ui.screens.history.TxDetailScreen
import junkwallet.ui.screens.lock.LockScreen
import junkwallet.ui.screens.receive.ReceiveScreen
import junkwallet.ui.screens.send.SendScreen
import junkwallet.ui.screens.settings.KeyVaultScreen
import junkwallet.ui.screens.settings.SettingsScreen
import junkwallet.ui.screens.send.QrScannerScreen
import junkwallet.ui.screens.setup.CreateWalletScreen
import junkwallet.ui.screens.setup.ImportWalletScreen
import junkwallet.ui.screens.setup.SetupScreen
import junkwallet.ui.viewmodel.WalletViewModel
import junkwallet.utils.parseQrPaymentData

@Composable
fun JunkWalletNavHost(
    navController: NavHostController = rememberNavController(),
    walletStorage: WalletStorage,
    settingsStorage: SettingsStorage
) {
    val hasWallet = remember { walletStorage.hasStoredWallet() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // ONE WalletViewModel for the whole graph (activity-scoped).
    // Previously every destination created its own instance via hiltViewModel(),
    // which meant up to 8 concurrent 45s sync loops, 8 MWEB daemon starts and
    // stale state when switching network/account in Settings.
    val walletViewModel: WalletViewModel = hiltViewModel()

    val mainRoutes = remember {
        setOf(
            Screen.Dashboard.route,
            Screen.Send.route,
            Screen.Receive.route,
            Screen.History.route,
            Screen.Settings.route
        )
    }

    Scaffold(
        containerColor = junkwallet.ui.theme.Background,
        bottomBar = {
            if (currentRoute in mainRoutes) {
                BottomNavBar(navController = navController)
            }
        }
    ) { outerPadding ->
    NavHost(
        navController = navController,
        startDestination = if (hasWallet) Screen.Lock.route else Screen.Setup.route,
        modifier = Modifier.padding(outerPadding)
    ) {
        // ── Setup Flow ──
        composable(Screen.Setup.route) {
            SetupScreen(
                onCreateWallet = { navController.navigate(Screen.CreateWallet.route) },
                onImportWallet = { navController.navigate(Screen.ImportWallet.route) }
            )
        }

        composable(Screen.CreateWallet.route) {
            CreateWalletScreen(
                onWalletCreated = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                        popUpTo(Screen.CreateWallet.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImportWallet.route) {
            ImportWalletScreen(
                onWalletImported = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                        popUpTo(Screen.ImportWallet.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Lock Screen ──
        composable(Screen.Lock.route) {
            var lockError by remember { mutableStateOf<String?>(null) }
            var verifying by remember { mutableStateOf(false) }
            val lockScope = rememberCoroutineScope()
            val context = LocalContext.current
            val activity = context as? androidx.fragment.app.FragmentActivity

            // Password/PIN verification runs PBKDF2 (600K iterations): it must
            // never execute on the main thread, otherwise the app freezes for
            // seconds and Android kills it with an ANR.
            fun verifyAndUnlock(verify: suspend () -> String?, wrongMessage: String) {
                if (verifying) return
                verifying = true
                lockScope.launch {
                    val wif = withContext(Dispatchers.IO) { verify() }
                    if (wif != null) {
                        val address = walletStorage.getAddress() ?: ""
                        walletStorage.saveSessionWif(wif)
                        walletViewModel.unlock(wif, address)
                        lockError = null
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Lock.route) { inclusive = true }
                        }
                    } else {
                        lockError = wrongMessage
                    }
                    verifying = false
                }
            }

            LockScreen(
                isVerifying = verifying,
                onPasswordVerified = { password ->
                    verifyAndUnlock(
                        verify = { walletStorage.getDecryptedWif(password) },
                        wrongMessage = "Incorrect password"
                    )
                },
                onPinVerified = { pin ->
                    verifyAndUnlock(
                        verify = { walletStorage.getDecryptedWifByPin(pin) },
                        wrongMessage = "Incorrect PIN"
                    )
                },
                onBiometricRequested = {
                    if (activity != null && !verifying) {
                        junkwallet.util.BiometricHelper.showBiometricPrompt(
                            activity = activity,
                            onSuccess = {
                                // Biometric verified — WIF must already be in
                                // the session (it is only kept in memory).
                                val wif = walletStorage.getSessionWif()
                                if (wif != null) {
                                    lockScope.launch {
                                        val address = walletStorage.getAddress() ?: ""
                                        walletViewModel.unlock(wif, address)
                                        navController.navigate(Screen.Dashboard.route) {
                                            popUpTo(Screen.Lock.route) { inclusive = true }
                                        }
                                    }
                                } else {
                                    lockError = "Please use password to unlock"
                                }
                            },
                            onError = { _, errString ->
                                lockError = errString.toString()
                            },
                            onFailed = {
                                lockError = "Fingerprint not recognized"
                            }
                        )
                    }
                },
                hasStoredWallet = walletStorage.hasStoredWallet(),
                hasPin = walletStorage.hasPin(),
                error = lockError,
                biometricAvailable = junkwallet.util.BiometricHelper.isBiometricAvailable(context)
            )
        }

        // ── Main Screens ──
        composable(Screen.Dashboard.route) {
            val uiState by walletViewModel.uiState.collectAsState()
            val fiatPrice by walletViewModel.fiatPrice.collectAsState()
            val defaultAddressType by walletViewModel.defaultAddressType.collectAsState()
            val mwebBalance by walletViewModel.mwebBalance.collectAsState()

            LaunchedEffect(uiState.isLocked) {
                if (uiState.isLocked && currentRoute == Screen.Dashboard.route) {
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            }

            // Last account deleted → the wallet is gone, restart at setup.
            LaunchedEffect(uiState.hasStoredWallet) {
                if (!uiState.hasStoredWallet && currentRoute == Screen.Dashboard.route) {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            DashboardScreen(
                onSend = { navController.navigate(Screen.Send.route) },
                onReceive = { navController.navigate(Screen.Receive.route) },
                onHistory = { navController.navigate(Screen.History.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onLock = {
                    walletViewModel.lock()
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onRefresh = { walletViewModel.forceRefresh() },
                onNetworkToggle = {
                    val targetNetwork = when (uiState.network) {
                        junkwallet.domain.model.NetworkType.MAINNET -> junkwallet.domain.model.NetworkType.TESTNET
                        junkwallet.domain.model.NetworkType.TESTNET -> junkwallet.domain.model.NetworkType.MAINNET
                    }
                    walletViewModel.switchNetwork(targetNetwork)
                },
                onAddressTypeChanged = { walletViewModel.setDefaultAddressType(it) },
                onAccountSelected = { walletViewModel.switchAccount(it) },
                onAccountRename = { id, name -> walletViewModel.renameAccount(id, name) },
                onAccountCreate = { walletViewModel.createAccount(it) },
                onAccountDelete = { walletViewModel.deleteAccount(it) },
                accounts = uiState.accounts,
                activeAccountId = uiState.activeAccount?.id ?: "",
                onTxClick = { txid ->
                    val tx = uiState.transactions.find { it.txid == txid }
                    if (tx != null) {
                        try {
                            navController.currentBackStackEntry?.savedStateHandle?.set("selectedTx", tx)
                        } catch (_: Exception) { }
                        navController.navigate(Screen.TxDetail.createRoute(txid))
                    }
                },
                confirmedBalance = uiState.confirmedBalance,
                unconfirmedBalance = uiState.unconfirmedBalance,
                address = uiState.address,
                allAddresses = uiState.allAddresses,
                blockHeight = uiState.blockHeight,
                networkName = when (uiState.network) {
                    junkwallet.domain.model.NetworkType.MAINNET -> "Mainnet"
                    junkwallet.domain.model.NetworkType.TESTNET -> "Testnet"
                },
                defaultAddressType = defaultAddressType,
                mwebBalance = mwebBalance,
                isLoading = uiState.isLoading,
                transactions = uiState.transactions,
                fiatPriceUsd = fiatPrice.usd,
                feeEstimates = uiState.feeEstimates,
                utxoCount = uiState.utxos.size,
                error = uiState.error,
                onErrorDismiss = { walletViewModel.clearError() },
                lastSyncedText = uiState.lastSyncedText
            )
        }

        composable(Screen.Send.route) {
            val sendViewModel: junkwallet.ui.viewmodel.SendViewModel = hiltViewModel()
            val defaultAddressType by walletViewModel.defaultAddressType.collectAsState()
            val mwebBalance by walletViewModel.mwebBalance.collectAsState()
            SendScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { txId ->
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onQrScan = { navController.navigate(Screen.QrScanner.route) },
                sourceAddressType = defaultAddressType,
                mwebBalance = mwebBalance,
                viewModel = sendViewModel
            )
        }

        composable(Screen.Receive.route) {
            ReceiveScreen(
                onBack = { navController.popBackStack() },
                viewModel = walletViewModel
            )
        }

        composable(Screen.History.route) {
            val uiState by walletViewModel.uiState.collectAsState()

            HistoryScreen(
                onBack = { navController.popBackStack() },
                onTxClick = { txid ->
                    val tx = uiState.transactions.find { it.txid == txid }
                    if (tx != null) {
                        try {
                            navController.currentBackStackEntry?.savedStateHandle?.set("selectedTx", tx)
                        } catch (_: Exception) { }
                        navController.navigate(Screen.TxDetail.createRoute(txid))
                    }
                },
                transactions = uiState.transactions
            )
        }

        // ── Transaction Detail ──
        composable(
            route = Screen.TxDetail.route,
            arguments = listOf(navArgument("txid") { type = NavType.StringType })
        ) { backStackEntry ->
            val txid = backStackEntry.arguments?.getString("txid") ?: ""
            val selectedTx = try {
                navController.previousBackStackEntry?.savedStateHandle?.get<TransactionInfo>("selectedTx")
            } catch (_: Exception) {
                null
            }

            val uiState by walletViewModel.uiState.collectAsState()

            val tx = selectedTx ?: uiState.transactions.find { it.txid == txid }

            if (tx != null) {
                TxDetailScreen(
                    tx = tx,
                    onBack = { navController.popBackStack() },
                    myAddress = uiState.address,
                    network = uiState.network
                )
            } else {
                // Fallback: show loading or error
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "Transaction not found",
                        color = junkwallet.ui.theme.TextMuted
                    )
                }
            }
        }

        composable(Screen.Settings.route) {
            val settingsScope = rememberCoroutineScope()
            val uiState by walletViewModel.uiState.collectAsState()
            val defaultAddressType by walletViewModel.defaultAddressType.collectAsState()
            val biometricEnabled by settingsStorage.biometricEnabled.collectAsState(initial = false)

            SettingsScreen(
                onBack = { navController.popBackStack() },
                onKeyVault = { navController.navigate(Screen.KeyVault.route) },
                onLock = {
                    walletViewModel.lock()
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                currentNetwork = uiState.network,
                onNetworkChanged = { walletViewModel.switchNetwork(it) },
                defaultAddressType = defaultAddressType,
                onAddressTypeChanged = { walletViewModel.setDefaultAddressType(it) },
                supportedAddressTypes = walletViewModel.getSupportedAddressTypes(),
                biometricEnabled = biometricEnabled,
                onBiometricChanged = {
                    kotlinx.coroutines.MainScope().launch {
                        settingsStorage.setBiometricEnabled(it)
                    }
                },
                hasPin = walletStorage.hasPin(),
                onPinSetup = { pin ->
                    // Save PIN: encrypt the ROOT key (never the active account
                    // key — the PIN must always unlock the whole wallet).
                    // PBKDF2 runs off the main thread.
                    val wif = walletStorage.getMasterWif() ?: walletStorage.getSessionWif()
                    if (wif != null) {
                        settingsScope.launch(Dispatchers.IO) {
                            walletStorage.savePinEncryptedWif(wif, pin)
                        }
                    }
                },
                onPinRemove = { walletStorage.clearPin() },
                onChangePassword = { currentPassword, newPassword ->
                    walletViewModel.changePassword(currentPassword, newPassword)
                },
                versionName = junkwallet.BuildConfig.VERSION_NAME
            )
        }

        composable(Screen.KeyVault.route) {
            KeyVaultScreen(
                onBack = { navController.popBackStack() },
                storage = walletStorage,
                crypto = junkwallet.domain.wallet.JunkcoinCrypto(
                    bech32Encoder = junkwallet.domain.wallet.Bech32Encoder()
                )
            )
        }

        composable(Screen.QrScanner.route) {
            // Keyed on the current entry: getBackStackEntry must not be called
            // un-remembered during composition.
            val sendEntry = remember(navBackStackEntry) {
                navController.getBackStackEntry(Screen.Send.route)
            }
            val sendViewModel: junkwallet.ui.viewmodel.SendViewModel = hiltViewModel(sendEntry)
            QrScannerScreen(
                onResult = { rawResult ->
                    val qrData = parseQrPaymentData(rawResult)
                    if (qrData != null) {
                        sendViewModel.applyQrPaymentData(qrData)
                    } else {
                        sendViewModel.updateRecipientAddress(rawResult)
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    } // end NavHost
    } // end Scaffold
}
