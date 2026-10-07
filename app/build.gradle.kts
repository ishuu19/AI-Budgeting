import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) load(secretsFile.inputStream())
}

android {
    namespace = "com.ledgerai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ledgerai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENROUTER_API_KEY",
            "\"${secrets.getProperty("OPENROUTER_API_KEY", "")}\"")
        buildConfigField("String", "OPENROUTER_BASE_URL",
            "\"${secrets.getProperty("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1/")}\"")
        buildConfigField("String", "AI_MODEL",
            "\"${secrets.getProperty("AI_MODEL", "anthropic/claude-3.7-sonnet")}\"")
        buildConfigField("String", "APP_NAME",
            "\"${secrets.getProperty("APP_NAME", "LedgerAI")}\"")
        buildConfigField("String", "APP_SITE_URL",
            "\"${secrets.getProperty("APP_SITE_URL", "https://ledgerai.app")}\"")
        buildConfigField("String", "DEFAULT_CURRENCY",
            "\"${secrets.getProperty("DEFAULT_CURRENCY", "USD")}\"")
        buildConfigField("String", "DEFAULT_CURRENCY_SYMBOL",
            "\"${secrets.getProperty("DEFAULT_CURRENCY_SYMBOL", "$")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID",
            "\"${secrets.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
        buildConfigField("String", "MONGODB_URI",
            "\"${secrets.getProperty("MONGODB_URI", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // MongoDB Atlas (Kotlin sync driver; we run in withContext(IO))
    implementation(platform("org.mongodb:mongodb-driver-bom:5.6.4"))
    implementation("org.mongodb:mongodb-driver-kotlin-sync")

    // Retrofit + OkHttp (OpenRouter AI)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Google Sign-In (Credential Manager)
    implementation(libs.google.signin)
    implementation(libs.credentials)
    implementation(libs.credentials.play)

    // Glance Widget
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // WorkManager
    implementation(libs.work.runtime)

    // DataStore (preferences)
    implementation(libs.datastore)

    // Coroutines
    implementation(libs.kotlinx.coroutines)

    debugImplementation(libs.androidx.ui.tooling)
}
