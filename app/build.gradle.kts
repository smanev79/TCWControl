plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.tcwcontrol.app"
    compileSdk = 35

    defaultConfig {
        // Same package as the TCW Control app on Google Play (3.0.6 = versionCode 306), so that this
        // app installs as its update and finds its saved devices.
        applicationId = "cc.teracom.app.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 401
        versionName = "4.0.1"
    }

    // The upload key of the Play app. CI writes it from GitHub secrets; without them the release
    // build is left unsigned.
    val keystoreFile = System.getenv("TCW_KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (keystoreFile != null) {
            create("upload") {
                storeFile = keystoreFile
                storePassword = System.getenv("TCW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TCW_KEY_ALIAS")
                keyPassword = System.getenv("TCW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// The debug build is a separate test app ("TCW Control test") that installs next to the store app
// and keeps the package of the earlier test builds, so their devices stay.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.tcwcontrol.app")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    // Reads the passwords the previous TCW Control app kept in MAUI SecureStorage.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // QR codes for sharing the device list: generating (zxing core) and camera scanning.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
