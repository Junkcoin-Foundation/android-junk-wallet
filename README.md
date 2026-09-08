# Android Junk Wallet

A native Android wallet for Junkcoin (JKC) built with Kotlin, Jetpack Compose, and modern Android architecture.

## Features

- **Multi-address types**: Legacy (P2PKH), Wrapped SegWit (P2SH-P2WPKH), Native SegWit (P2WPKH), Taproot (P2TR)
- **Mainnet & Testnet** support with instant network switching
- **QR Scanner** with ML Kit — supports plain addresses, BIP21 URIs, JSON payloads, and bridge/OP_RETURN formats
- **Send & Receive** with custom fee levels (Economy, Normal, Priority, Custom)
- **UTXO management** with CoinSelector
- **Transaction history** with block explorer integration
- **Biometric authentication** (fingerprint/face unlock)
- **Offline cache** with automatic sync when online
- **Fiat price** tracking via CoinGecko API
- **CSV export** for transaction history
- **Key Vault** to view/export private keys

## Architecture

```
junkwallet/
├── data/           # API clients, cache, storage
│   ├── api/        # Electrs & CoinGecko REST clients
│   ├── cache/      # Room database for offline cache
│   ├── model/      # API response models
│   ├── repository/ # Blockchain & price repositories
│   └── storage/    # Encrypted SharedPreferences
├── di/             # Hilt dependency injection modules
├── domain/         # Business logic
│   ├── model/      # WalletState, TransactionInfo, Network params
│   ├── usecase/    # Sync, Create, Import, GetPrice
│   └── wallet/     # Crypto, TransactionBuilder, AddressValidator
├── ui/             # Compose UI
│   ├── screens/    # Dashboard, Send, Receive, History, Settings
│   ├── viewmodel/  # WalletViewModel, SendViewModel
│   └── theme/      # Colors, Typography, Theme
└── utils/          # QR parser, CSV exporter, biometrics
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| Network | Retrofit + OkHttp + Kotlin Serialization |
| Cache | Room Database |
| Storage | EncryptedSharedPreferences |
| Camera/QR | CameraX + Google ML Kit |
| Crypto | BouncyCastle (secp256k1) |
| Build | Gradle Kotlin DSL |

## Build

```bash
# Debug build
cd android
./gradlew assembleDebug

# Install on device
./build.sh -install
```

## Network Configuration

| Network | Electrs API | Explorer |
|---------|------------|----------|
| Mainnet | `junk-api.s3na.xyz` | `explorer.junk-coin.com` |
| Testnet | `jkc-testnet-api.s3na.xyz` | `explorer.junk-coin.com/testnet` |

## Address Types

| Type | Mainnet Prefix | Testnet Prefix | Description |
|------|---------------|----------------|-------------|
| P2PKH | `7...` | `m` / `n` | Legacy |
| P2SH-P2WPKH | `3...` | `2...` | Wrapped SegWit |
| P2WPKH | `jc1...` | `tjc1...` | Native SegWit |
| P2TR | `jc1p...` | `tjc1p...` | Taproot |

## License

MIT
