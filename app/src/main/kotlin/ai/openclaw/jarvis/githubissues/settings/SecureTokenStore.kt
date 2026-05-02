package ai.openclaw.jarvis.githubissues.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the GitHub Personal Access Token using Android's EncryptedSharedPreferences
 * (AES-256-GCM, hardware-backed master key on supported devices).
 *
 * The token never leaves disk-encrypted storage and is only read on-demand
 * by [ai.openclaw.jarvis.githubissues.api.GitHubApiClient] when issuing requests.
 */
@Singleton
open class SecureTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context, FILE_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            context.deleteSharedPreferences(FILE_NAME)
            EncryptedSharedPreferences.create(
                context, FILE_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    open fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    open fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    open fun hasToken(): Boolean = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()

    open fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val FILE_NAME = "jarvis_github_secure_prefs"
        private const val KEY_TOKEN = "github_pat"
    }
}
