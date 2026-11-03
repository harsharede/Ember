plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.harsharede.ember"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.harsharede.ember"
        minSdk = 31
        targetSdk = 35
        versionCode = 25
        versionName = "1.2.5"
    }

    signingConfigs {
        // A throwaway key committed so CI and anyone can build an installable, upgradeable APK.
        // It protects nothing: create your own upload key before publishing to the Play Store.
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Official releases: signed with the owner's private key, given to CI as secrets (never in the repository).
        val releaseStore = System.getenv("EMBER_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
        if (releaseStore != null) create("release") {
            storeFile = releaseStore
            storePassword = System.getenv("EMBER_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("EMBER_KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "ember"
            // An unset GitHub secret arrives as an empty string, not a missing one: fall back to the store password then.
            keyPassword = System.getenv("EMBER_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: System.getenv("EMBER_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The private key when CI has it (tagged releases); the throwaway key for everyday test builds.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("shared")
        }
    }

    lint { abortOnError = false }
    testOptions { unitTests.isReturnDefaultValues = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate)
    // Installs the Compose libraries' own startup profiles on sideloaded installs, so scrolling and animations are
    // compiled ahead of time instead of running interpreted for the first days.
    implementation(libs.androidx.profileinstaller)
    // The wallpaper timer: survives restarts and lets Android pick a battery-friendly moment.
    implementation(libs.androidx.work.runtime)
    // Reads where a wallpaper photo was taken.
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
}
