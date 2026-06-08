package com.nuvio.app.core.network

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseProvider {
    val isConfigured: Boolean
        get() = SupabaseConfig.URL.isNotBlank() && SupabaseConfig.ANON_KEY.isNotBlank()

    val client by lazy {
        if (!isConfigured) {
            error("Supabase is not configured — running in offline/standalone mode.")
        }
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
        }
    }

    val clientOrNull by lazy {
        if (!isConfigured) null
        else runCatching { client }.getOrNull()
    }
}
