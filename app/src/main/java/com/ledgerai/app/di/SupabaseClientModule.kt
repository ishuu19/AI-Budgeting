package com.ledgerai.app.di

import androidx.annotation.Nullable
import com.ledgerai.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

/**
 * Provides a Supabase client when URL + anon key are present in BuildConfig.
 * Returns null when secrets are empty so local "Continue" still works offline.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseClientModule {

    @Provides
    @Singleton
    @Nullable
    fun provideSupabaseClient(): SupabaseClient? {
        val url = BuildConfig.SUPABASE_URL.trim()
        val anon = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (url.isEmpty() || anon.isEmpty()) return null

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anon
        ) {
            // The app keeps the session in its own store and refreshes it in one place (AuthRepository).
            // The plugin's own background refresh would rotate the refresh token behind the app's back.
            install(Auth) {
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                autoSaveToStorage = false
            }
            install(Postgrest)
        }
    }
}
