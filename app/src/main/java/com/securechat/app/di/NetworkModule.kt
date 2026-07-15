package com.securechat.app.di

import com.securechat.core.network.ChatApiService
import com.securechat.core.network.RealtimeMessageClient
import com.securechat.core.security.KeystoreManager
import com.securechat.core.security.SessionCipherManager
import com.securechat.core.security.X3DHKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ── Supabase Configuration ────────────────────────────────────────────────
    // Replace these with your actual project credentials from:
    // https://supabase.com/dashboard → Project Settings → API
    private const val SUPABASE_URL = "https://pxjummkimfspemfkjcnq.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_N2166LLPEaVoF1ceb15uFw_1t-aNbRz"

    // ── JSON ──────────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient         = true
        encodeDefaults    = true
        prettyPrint       = false
        coerceInputValues = true
    }

    // ── Supabase Client ───────────────────────────────────────────────────────

    /**
     * Creates the Supabase client with all required plugins installed.
     * The Ktor OkHttp engine is used for Android compatibility.
     */
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY,
    ) {
        install(Auth)         // Supabase GoTrue — login, register, token refresh
        install(Postgrest)    // PostgREST — typed database queries
        install(Realtime)     // Real-time channel subscriptions
        install(Storage)      // File storage (encrypted media uploads)

        httpEngine = OkHttp   // Android-compatible Ktor engine
    }

    // ── Service layer ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSupabaseService(supabase: SupabaseClient): ChatApiService =
        ChatApiService(supabase)

    @Provides
    @Singleton
    fun provideRealtimeMessageClient(supabase: SupabaseClient, json: Json): RealtimeMessageClient =
        RealtimeMessageClient(supabase, json)

    // ── Security ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides
    @Singleton
    fun provideX3DHKeyManager(keystoreManager: KeystoreManager): X3DHKeyManager =
        X3DHKeyManager(keystoreManager)

    @Provides
    @Singleton
    fun provideSessionCipherManager(x3dh: X3DHKeyManager): SessionCipherManager =
        SessionCipherManager(x3dh)
}
