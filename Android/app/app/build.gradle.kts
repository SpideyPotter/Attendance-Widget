import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "edu.bmu.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "edu.bmu.attendance"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.1"
    }

    signingConfigs {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        val keystoreProperties = Properties()
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
        }

        val envStoreFile = System.getenv("ANDROID_UPLOAD_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
        val envSigning =
            envStoreFile != null &&
                !System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD").isNullOrBlank() &&
                !System.getenv("ANDROID_UPLOAD_KEY_ALIAS").isNullOrBlank() &&
                !System.getenv("ANDROID_UPLOAD_KEY_PASSWORD").isNullOrBlank()

        val propsStoreFile =
            keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it) }
        val propsSigning =
            propsStoreFile != null &&
                propsStoreFile.isFile &&
                !keystoreProperties.getProperty("storePassword").isNullOrBlank() &&
                !keystoreProperties.getProperty("keyAlias").isNullOrBlank() &&
                !keystoreProperties.getProperty("keyPassword").isNullOrBlank()

        if (envSigning) {
            create("upload") {
                storeFile = file(envStoreFile!!)
                storePassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_UPLOAD_KEY_ALIAS")!!
                keyPassword = System.getenv("ANDROID_UPLOAD_KEY_PASSWORD")
            }
        } else if (propsSigning) {
            create("upload") {
                storeFile = propsStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")!!
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Installable APK for CI/sideload when no upload keystore is configured.
            signingConfig =
                signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
        debug {
            // Keep debug symbols for stack traces and easier widget debugging.
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

    buildFeatures {
        compose = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.security.crypto)
    compileOnly(libs.error.prone.annotations)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
