package com.ledgerai.app.di

import com.google.gson.Gson
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.sync.PostgrestApi
import com.ledgerai.app.data.sync.ExtraSync
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
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
 * PostgREST (Supabase `/rest/v1`) client. Public anon key only; user JWT is passed per request.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtraSyncBindings {
    @Multibinds
    abstract fun extraSyncs(): Set<ExtraSync>
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    @Named("supabaseOkHttp")
    fun provideSupabaseOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun providePostgrestApi(
        gson: Gson,
        @Named("supabaseOkHttp") client: OkHttpClient
    ): PostgrestApi {
        val base = restBaseUrl(BuildConfig.SUPABASE_URL)
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PostgrestApi::class.java)
    }

    /** Retrofit requires a non-empty absolute base URL even when sync is disabled. */
    private fun restBaseUrl(supabaseUrl: String): String {
        val trimmed = supabaseUrl.trim().ifBlank { "https://localhost/" }
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return if (withSlash.contains("/rest/v1")) withSlash else "${withSlash}rest/v1/"
    }
}
