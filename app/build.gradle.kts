import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val wardenStoreFile = signingValue("WARDEN_STORE_FILE")
val wardenStorePassword = signingValue("WARDEN_STORE_PASSWORD")
val wardenKeyAlias = signingValue("WARDEN_KEY_ALIAS")
val wardenKeyPassword = signingValue("WARDEN_KEY_PASSWORD")
val wardenSigningValues = listOf(wardenStoreFile, wardenStorePassword, wardenKeyAlias, wardenKeyPassword)
require(wardenSigningValues.all { it == null } || wardenSigningValues.all { it != null }) {
    "Set all WARDEN_* signing values in local.properties or the environment, or none of them."
}

android {
    namespace = "com.warden.blocker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.warden.blocker"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (wardenSigningValues.all { it != null }) {
            create("release") {
                storeFile = rootProject.file(wardenStoreFile!!)
                storePassword = wardenStorePassword!!
                keyAlias = wardenKeyAlias!!
                keyPassword = wardenKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isDebuggable = false
            if (wardenSigningValues.all { it != null }) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // APK Signature Scheme v2 + v3. v1 (JAR signing) is requested but AGP omits it for
    // minSdk >= 24 — measured on the release output: v2/v3 verify, v1 reports false, which
    // is expected and still installs on every supported device (minSdk 26).
    // v3 is what lets a future Play key rotation work without breaking updates.
    signingConfigs {
        if (wardenSigningValues.all { it != null }) {
            getByName("release") {
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
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
        buildConfig = true // for BuildConfig.DEBUG (FLAG_SECURE only in release)
    }
}

// Export Room schemas so migrations can be reviewed and (later) tested with MigrationTestHelper.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // On-device NSFW image screening: official TF Lite Task Vision runtime. The model
    // file (MobileNet AutoML, ~5.8MB) ships in assets/nsfw/ so nothing is fetched at
    // runtime and classification never leaves the device.
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
