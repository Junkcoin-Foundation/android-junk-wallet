plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "xyz.junkcoin.mweb"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            // gomobile output (built by build-aar.sh / ./build.sh)
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            // Keep the Go runtime's .so files as-is.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // gomobile-generated classes (extracted from junkcoin-mweb.aar).
    // The native libraries live in src/main/jniLibs; a direct local .aar
    // dependency is not allowed when this module builds an AAR itself.
    implementation(files("libs/junkcoin-mweb-classes.jar"))
    implementation(libs.coroutines.android)
}
