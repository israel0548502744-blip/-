plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the workflow run number, so every published APK has a higher versionCode
// and the in-app updater can tell which build is newer.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.triposr.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.triposr.mobile"
        minSdk = 33
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
        ndk { abiFilters += "arm64-v8a" }
    }

    // A fixed key, so each new APK installs over the previous one as an update.
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/triposr.jks")
            storePassword = "triposr"
            keyAlias = "triposr"
            keyPassword = "triposr"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources { noCompress += listOf("onnx") }
    packaging {
        // QNN libraries must be extracted to disk so the Hexagon skeleton libraries can be found.
        jniLibs {
            useLegacyPackaging = true
            // Hexagon versions older than Snapdragon 8 Gen 2 (V73) and the legacy DSP backend.
            excludes += listOf(
                "**/libQnnHtpV66*", "**/libQnnHtpV68*", "**/libQnnHtpV69*",
                "**/libQnnDsp*",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ONNX Runtime with the Qualcomm QNN execution provider (pulls in com.qualcomm.qti:qnn-runtime).
    implementation("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
