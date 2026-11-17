plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aribs.rsenhancify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aribs.rsenhancify"
        minSdk = 29
        targetSdk = 35
        // CI passes these; local builds fall back to 1 / "1.0"
        versionCode = (project.findProperty("rsVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("rsVersionName") as String?) ?: "1.0"

        ndk {
            // Real phones only (keeps APK small). x86 emulators are not supported.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Release signing comes from environment variables (GitHub Secrets in CI).
    val keystorePath = System.getenv("RS_KEYSTORE_PATH")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RS_KEY_ALIAS")
                keyPassword = System.getenv("RS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: strips unused code (material icons, bouncycastle) -> much smaller APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!keystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                // Post-quantum crypto tables from bouncycastle (not used for PDF decryption)
                "org/bouncycastle/pqc/**"
            )
        }
        jniLibs {
            // Compress native libs inside the APK -> smaller download
            useLegacyPackaging = true
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
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // On-device AI upscaling (Real-ESRGAN, TFLite). GPU with CPU fallback.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")

    // PDF text extraction for PDF -> Word (Apache 2.0)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Offline OCR for scanned PDFs (model bundled in the APK, works without internet)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
