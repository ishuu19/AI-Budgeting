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

/** Escape a property for use inside a BuildConfig string literal. Never log the value. */
fun escapeBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

fun secretOrEmpty(key: String, default: String = ""): String =
    escapeBuildConfig(secrets.getProperty(key, default).orEmpty())

/** AI BuildConfig keys — populated only for debug; release stays empty. Never SUPABASE_SECRET_KEY. */
val aiBuildConfigKeys = listOf(
    "OPENROUTER_FREE_API_KEY",
    "OPENROUTER_API_KEY",
    "OPENROUTER_API_KEYS",
    "OPENROUTER_BASE_URL",
    "GEMINI_API_KEY",
    "GEMINI_API_KEYS",
    "GEMINI_BASE_URL",
    "DEEPSEEK_API_KEY",
    "DEEPSEEK_BASE_URL",
    "AI_MODEL_OPENROUTER_FREE",
    "AI_MODEL_GEMINI",
    "AI_MODEL_DEEPSEEK",
    "AI_MODEL_OPENROUTER",
)

fun com.android.build.api.dsl.ApplicationBuildType.emptyAiBuildConfigFields() {
    aiBuildConfigKeys.forEach { key ->
        buildConfigField("String", key, "\"\"")
    }
}

fun com.android.build.api.dsl.ApplicationBuildType.debugAiBuildConfigFields() {
    buildConfigField("String", "OPENROUTER_FREE_API_KEY", "\"${secretOrEmpty("OPENROUTER_FREE_API_KEY")}\"")
    buildConfigField("String", "OPENROUTER_API_KEY", "\"${secretOrEmpty("OPENROUTER_API_KEY")}\"")
    buildConfigField("String", "OPENROUTER_API_KEYS", "\"${secretOrEmpty("OPENROUTER_API_KEYS")}\"")
    buildConfigField(
        "String", "OPENROUTER_BASE_URL",
        "\"${secretOrEmpty("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1/")}\""
    )
    buildConfigField("String", "GEMINI_API_KEY", "\"${secretOrEmpty("GEMINI_API_KEY")}\"")
    buildConfigField("String", "GEMINI_API_KEYS", "\"${secretOrEmpty("GEMINI_API_KEYS")}\"")
    buildConfigField(
        "String", "GEMINI_BASE_URL",
        "\"${secretOrEmpty("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta")}\""
    )
    buildConfigField("String", "DEEPSEEK_API_KEY", "\"${secretOrEmpty("DEEPSEEK_API_KEY")}\"")
    buildConfigField(
        "String", "DEEPSEEK_BASE_URL",
        "\"${secretOrEmpty("DEEPSEEK_BASE_URL", "https://api.deepseek.com")}\""
    )
    buildConfigField(
        "String", "AI_MODEL_OPENROUTER_FREE",
        "\"${secretOrEmpty("AI_MODEL_OPENROUTER_FREE", "deepseek/deepseek-chat-v3-0324:free")}\""
    )
    buildConfigField(
        "String", "AI_MODEL_GEMINI",
        "\"${secretOrEmpty("AI_MODEL_GEMINI", "gemini-flash-latest")}\""
    )
    buildConfigField(
        "String", "AI_MODEL_DEEPSEEK",
        "\"${secretOrEmpty("AI_MODEL_DEEPSEEK", "deepseek-chat")}\""
    )
    buildConfigField(
        "String", "AI_MODEL_OPENROUTER",
        "\"${secretOrEmpty("AI_MODEL_OPENROUTER", "deepseek/deepseek-chat")}\""
    )
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

        // Public client config only. Never put SUPABASE_SECRET_KEY in BuildConfig.
        val supabaseAnon = secrets.getProperty("SUPABASE_ANON_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: secrets.getProperty("SUPABASE_PUBLISHABLE_KEY", "")

        buildConfigField(
            "String", "SUPABASE_URL",
            "\"${escapeBuildConfig(secrets.getProperty("SUPABASE_URL", "").orEmpty())}\""
        )
        buildConfigField(
            "String", "SUPABASE_ANON_KEY",
            "\"${escapeBuildConfig(supabaseAnon.orEmpty())}\""
        )
        buildConfigField(
            "String", "APP_NAME",
            "\"${escapeBuildConfig(secrets.getProperty("APP_NAME", "LedgerAI").orEmpty())}\""
        )
        buildConfigField(
            "String", "DEFAULT_CURRENCY",
            "\"${escapeBuildConfig(secrets.getProperty("DEFAULT_CURRENCY", "USD").orEmpty())}\""
        )
        buildConfigField(
            "String", "DEFAULT_CURRENCY_SYMBOL",
            "\"${escapeBuildConfig(secrets.getProperty("DEFAULT_CURRENCY_SYMBOL", "$").orEmpty())}\""
        )
        // Web OAuth client ID for Credential Manager → Supabase Google ID token exchange.
        // Prefer SUPABASE_GOOGLE_WEB_CLIENT_ID; fall back to legacy GOOGLE_WEB_CLIENT_ID alias.
        val googleWebClientId = secrets.getProperty("SUPABASE_GOOGLE_WEB_CLIENT_ID")
            ?.takeIf { it.isNotBlank() }
            ?: secrets.getProperty("GOOGLE_WEB_CLIENT_ID", "")
        buildConfigField(
            "String", "SUPABASE_GOOGLE_WEB_CLIENT_ID",
            "\"${escapeBuildConfig(googleWebClientId.orEmpty())}\""
        )
    }

    buildTypes {
        debug {
            // Debug-only: optional AI keys/models from secrets.properties for local cascade testing.
            // Production path remains a Supabase Edge Function; do not ship keys in release.
            debugAiBuildConfigFields()
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // AI keys/models must remain empty strings in release APKs.
            emptyAiBuildConfigFields()
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

    packaging {
        jniLibs {
            pickFirsts += listOf("lib/**/libc++_shared.so")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Kept for future Supabase Edge Function clients (Phase 2+)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Credential Manager + Google ID token (Phase 2 Supabase Google auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)

    // supabase-kt 2.x: gotrue-kt = Auth module (catalog also aliases as supabase-auth)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.gotrue)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.work.runtime)
    implementation(libs.datastore)
    implementation(libs.kotlinx.coroutines)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mediapipe.genai)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // Real SQLite on the JVM to run the Room 6 to 7 migration SQL against a v6 fixture.
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}
