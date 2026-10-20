plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.triposr.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.triposr.mobile"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideload build: signed with the debug key so it installs without extra setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // The ONNX models are >1 GB. Storing them uncompressed lets the app copy them
    // out of the APK quickly, and the QNN libraries must be extracted to disk so the
    // Hexagon NPU skeleton libraries can be found at runtime.
    androidResources { noCompress += listOf("onnx") }
    packaging { jniLibs { useLegacyPackaging = true } }

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
}
