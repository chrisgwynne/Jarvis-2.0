package com.jarvis.githubissues.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the GitHub Personal Access Token using Android's EncryptedSharedPreferences
 * (AES-256-GCM, hardware-backed master key on supported devices).
 *
 * The token never leaves disk-encrypted storage and is only read on-demand
 * by [com.jarvis.githubissues.api.GitHubApiClient] when issuing requests.
 */
class SecureTokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.ValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun hasToken(): Boolean = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val FILE_NAME = "jarvis_github_secure_prefs"
        private const val KEY_TOKEN = "github_pat"
    }
}
