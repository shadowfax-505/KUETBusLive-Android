plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The Firebase config is not in git. Builds without it (such as CI) still compile;
// the app then shows a short "setup needed" screen instead of signing in.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "io.github.shadowfax505.kuetbuslive"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.shadowfax505.kuetbuslive"
        minSdk = 26
        // Google Play asks new apps and updates to target the latest Android (API 36 from Aug 2026).
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Runs the screenshot tour against the local Firebase emulators.
        testInstrumentationRunner = "io.github.shadowfax505.kuetbuslive.ShowcaseRunner"
    }

    // A fixed debug key, so a newer demo build installs over an older one on the same phone.
    // The release (Play upload) key is never in git: it comes from environment variables
    // (CI secrets) or from ~/.gradle/gradle.properties on the computer that builds releases.
    // Without it, the release build is produced unsigned.
    fun secret(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
    val releaseStore = secret("KUETBUS_RELEASE_STORE_FILE")
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = secret("KUETBUS_RELEASE_STORE_PASSWORD")
                keyAlias = secret("KUETBUS_RELEASE_KEY_ALIAS")
                keyPassword = secret("KUETBUS_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // Debug builds accept the @test.com demo accounts, like the iOS debug build.
            buildConfigField("Boolean", "DEMO_ACCOUNTS", "true")
        }
        release {
            // R8 shrinks and obfuscates the release build; keep rules in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            buildConfigField("Boolean", "DEMO_ACCOUNTS", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.location)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    implementation(libs.maplibre)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
