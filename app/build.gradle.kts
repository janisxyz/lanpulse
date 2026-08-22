import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME", "1.0.0")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE", "1").toInt()

android {
    namespace = "com.lanpulse.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lanpulse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("ci") {
            val storePath = System.getenv("LANPULSE_STORE_FILE")
            val resolved = if (!storePath.isNullOrBlank()) {
                file(storePath)
            } else {
                file("${System.getProperty("user.home")}/.android/debug.keystore")
            }
            if (resolved.isFile) {
                storeFile = resolved
                storePassword = System.getenv("LANPULSE_STORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("LANPULSE_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("LANPULSE_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ci = signingConfigs.getByName("ci")
            if (ci.storeFile != null) {
                signingConfig = ci
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.github.mwiede:jsch:0.2.26")
}
