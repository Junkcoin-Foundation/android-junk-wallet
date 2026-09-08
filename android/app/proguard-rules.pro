# Proguard rules for Junk Wallet

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

-keep class junkwallet.data.model.** { *; }
-keep class junkwallet.domain.model.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class junkwallet.** {
    *** Companion;
}
-keepclasseswithmembers class junkwallet.**$$serializer {
    *** INSTANCE;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# BitcoinJ
-keep class org.bitcoinj.** { *; }
-keep class org.spongycastle.** { *; }
-dontwarn org.bitcoinj.**
-dontwarn org.spongycastle.**

# Keep Biometric
-keep class androidx.biometric.** { *; }
