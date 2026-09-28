# junkcoin-mweb (Android)

Android library for Junkcoin MWEB (MimbleWimble Extension Blocks): a gomobile
binding of [junkcoin-mwebd](https://github.com/JunkcoinFoundation/junkcoin-mwebd)
plus a small Kotlin wrapper.

## Layout

```
mweb/
├── libs/
│   └── junkcoin-mweb-classes.jar   # gomobile generated Java classes
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/xyz/junkcoin/mweb/
│   │   └── JunkcoinMweb.kt         # Kotlin wrapper (JSON based API)
│   └── jniLibs/<abi>/libgojni.so   # Go runtime + daemon (arm64, armeabi, x86, x86_64)
├── build.gradle.kts
└── consumer-rules.pro
```

The gomobile output is an AAR, but AGP refuses a *direct local .aar
dependency* while this module builds its own AAR, so the AAR is unpacked:
`classes.jar` goes to `libs/`, `jni/*` goes to `src/main/jniLibs/`.

## Requirements

- Android API 26+
- Go 1.26 (`GOTOOLCHAIN=go1.26.8`), Android SDK — only for rebuilding the
  bindings

## Rebuilding the bindings

`junkcoin-mwebd` needs the mobile API patch (gomobile cannot bind gRPC,
`context.Context` or protobuf types, so every RPC is re-exposed as JSON over
primitives). The patch is kept at `mweb/junkcoin-mwebd-mobile.patch`.

```bash
export GOTOOLCHAIN=go1.26.8
export ANDROID_HOME=$HOME/Android/Sdk
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest

git clone https://github.com/Junkcoin-Foundation/junkcoin-mwebd /tmp/junkcoin-mwebd
cd /tmp/junkcoin-mwebd
git apply /path/to/junkcoin-mwebd-mobile.patch
go get -tool golang.org/x/mobile/cmd/gobind
gomobile bind -target=android -androidapi 26 -javapkg=xyz.junkcoin.mweb \
    -o /tmp/junkcoin-mweb.aar .

# unpack into this module
mkdir -p /tmp/aar && (cd /tmp/aar && unzip -q /tmp/junkcoin-mweb.aar)
cp /tmp/aar/classes.jar libs/junkcoin-mweb-classes.jar
rm -rf src/main/jniLibs && mkdir -p src/main/jniLibs
cp -r /tmp/aar/jni/* src/main/jniLibs/
```

## API

```kotlin
val mweb = JunkcoinMweb()

// daemon (neutrino SPV sync); chain = "mainnet" | "testnet"
mweb.start(chain, dataDir = "${filesDir}/mwebd-$chain", peerAddr = "mainnet.junk-coin.com:9771")

mweb.status()                                  // sync heights
mweb.addresses(scan, spendPub, 0, 10)          // jcmweb1... addresses
mweb.utxos(scan, fromHeight = 0L)              // one-shot UTXO scan
mweb.create(rawTx, scan, spend, feeRatePerKb)  // build the MWEB kernel
mweb.broadcast(rawTx)                          // txid
mweb.spent(listOf(outputId))                   // which outputs are spent
mweb.stop()

// no daemon needed:
JunkcoinMweb.addressesFor(chain, scan, spendPub, from, to)
JunkcoinMweb.estimateFee(chain, recipients, feeRatePerKb)
JunkcoinMweb.buildRawTx(chain, inputs, recipients)
```

Wallet level (coin selection, change, fees) lives in
`junkwallet.data.repository.MwebRepository`; the shared `WalletViewModel`
drives it.

### gomobile gotchas

- `uint64` parameters are **silently dropped** — use `int64`.
- Methods taking gRPC streams, `context.Context` or proto messages are not
  bound; that is what `mobile.go` in `junkcoin-mwebd` is for.

## Key derivation

MWEB keys are derived from the account private key (BIP32 master = HMAC-SHA512
of the 32 byte key, `"Bitcoin seed"`):

```
scan  = m/1000'/0'/0'/0'   (scan secret)
spend = m/1000'/0'/0'/1'   (spend secret)
```

See `junkwallet.domain.wallet.MwebKeychain`.

## License

MIT
