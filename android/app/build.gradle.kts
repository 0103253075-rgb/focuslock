plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.focuslock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.focuslock"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.5"
        manifestPlaceholders["appLabel"] = "FocusLock"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("follower") {
            dimension = "edition"
            applicationId = "com.focuslock.follower"
            versionNameSuffix = "-follower"
            manifestPlaceholders["appLabel"] = "FocusLock"
        }
        create("creator") {
            dimension = "edition"
            applicationId = "com.focuslock"
            versionNameSuffix = "-creator"
            manifestPlaceholders["appLabel"] = "FocusLock Pro"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
