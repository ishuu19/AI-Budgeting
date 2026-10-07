package com.ledgerai.app.di

import com.google.gson.Gson
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.ai.AiConfig
import com.ledgerai.app.data.ai.GeminiApi
import com.ledgerai.app.data.ai.OpenAiCompatibleApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * OkHttp/Retrofit for the AI cascade.
 * Keys come from [AiConfig]/BuildConfig] — empty in release; Edge Function is the production path.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    @Named("aiOkHttp")
    fun provideAiOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            // BASIC only — never log Authorization / X-goog-api-key bodies with secrets.
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    @Named("openRouterApi")
    fun provideOpenRouterApi(
        config: AiConfig,
        gson: Gson,
        @Named("aiOkHttp") client: OkHttpClient,
    ): OpenAiCompatibleApi =
        Retrofit.Builder()
            .baseUrl(config.openRouterBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OpenAiCompatibleApi::class.java)

    @Provides
    @Singleton
    @Named("deepSeekApi")
    fun provideDeepSeekApi(
        config: AiConfig,
        gson: Gson,
        @Named("aiOkHttp") client: OkHttpClient,
    ): OpenAiCompatibleApi =
        Retrofit.Builder()
            .baseUrl(ensureV1Base(config.deepSeekBaseUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OpenAiCompatibleApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiApi(
        config: AiConfig,
        gson: Gson,
        @Named("aiOkHttp") client: OkHttpClient,
    ): GeminiApi =
        Retrofit.Builder()
            .baseUrl(config.geminiBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GeminiApi::class.java)

    /** DeepSeek accepts /v1/chat/completions; normalize base to include v1 when missing. */
    private fun ensureV1Base(base: String): String {
        val normalized = if (base.endsWith("/")) base else "$base/"
        return if (normalized.contains("/v1/")) normalized else "${normalized}v1/"
    }
}
