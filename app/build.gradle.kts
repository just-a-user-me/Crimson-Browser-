plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun versionOf(name: String, fallback: String): String =
    (project.findProperty(name) as String?) ?: fallback

val geckoVersion: String = (project.findProperty("geckoVersion") as String?)
    ?: error("Missing geckoVersion property")

android {
    namespace = "app.crimson.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.crimson.browser"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:$geckoVersion")

    implementation(platform("androidx.compose:compose-bom:${versionOf("composeBomVersion", "2025.10.00")}"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:${versionOf("activityComposeVersion", "1.11.0")}")
    implementation("androidx.core:core-ktx:${versionOf("coreKtxVersion", "1.17.0")}")
}
