import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.roborazzi)
}

// Release signing secrets live in a gitignored keystore.properties (see
// keystore.properties.example). Environment variables are the fallback for CI.
// Never commit real passwords to this file.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}

android {
    namespace = "com.wearsic.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wearsic.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 11
        versionName = "1.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(
                keystoreProperties.getProperty("storeFile")
                    ?: System.getenv("WEARSIC_STORE_FILE")
                    ?: "wearsic-release.jks"
            )
            storePassword = keystoreProperties.getProperty("storePassword")
                ?: System.getenv("WEARSIC_STORE_PASSWORD")
                ?: ""
            keyAlias = keystoreProperties.getProperty("keyAlias")
                ?: System.getenv("WEARSIC_KEY_ALIAS")
                ?: ""
            keyPassword = keystoreProperties.getProperty("keyPassword")
                ?: System.getenv("WEARSIC_KEY_PASSWORD")
                ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Robolectric Compose tests (createComposeRule) launch the ComponentActivity
// provided by compose ui-test-manifest, which is merged into the debug APK
// only. Release unit tests cannot resolve that launcher activity, so run the
// unit tests on the debug variant only (the release APK is covered by the R8
// assembleRelease build instead).
tasks.configureEach {
    if (name == "testReleaseUnitTest") enabled = false
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Ambient (always-on display) support.
    implementation(libs.androidx.wear)
    // Overrides wear's ancient fragment 1.2.4 (needed by ActivityResult APIs).
    implementation(libs.androidx.fragment.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)

    // Wear Compose Material 3 (locked to 1.5.0 as per knowledge.md)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // Horologist: Wear OS UI toolkit — TimeText/AppScaffold/PositionIndicator
    // and rotary scroll+snap (compose-layout) + media controls (media-ui).
    implementation(libs.horologist.compose.layout)
    implementation(libs.horologist.media.ui)
    // TrackPositionUiModel (circular progress ring on the play button) lives
    // in this module since 0.7.x.
    implementation(libs.horologist.media.ui.model)

    // Media3 for Playback + offline cache (SQLite-backed cache index)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.database)

    // Networking - Ktor Client (OkHttp engine for symmetry with server)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    testImplementation(libs.ktor.client.mock)

    // DataStore Preferences
    implementation(libs.datastore.preferences)

    // Image Loading
    implementation(libs.coil.compose)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing Dependencies
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    // Debug-only tooling never ships in the Wear OS release APK.
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}
