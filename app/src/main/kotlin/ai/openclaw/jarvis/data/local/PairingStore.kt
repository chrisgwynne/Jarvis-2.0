package ai.openclaw.jarvis.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the Gateway pairing token and device ID in EncryptedSharedPreferences.
 */
@Singleton
class PairingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "jarvis_pairing",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        private const val KEY_DEVICE_ID     = "device_id"
        private const val KEY_PAIRING_TOKEN = "pairing_token"
        private const val KEY_NODE_ID       = "node_id"
    }

    fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    fun getPairingToken(): String? = prefs.getString(KEY_PAIRING_TOKEN, null)
    fun getNodeId(): String? = prefs.getString(KEY_NODE_ID, null)

    fun savePairingToken(token: String) = prefs.edit().putString(KEY_PAIRING_TOKEN, token).apply()
    fun saveNodeId(nodeId: String) = prefs.edit().putString(KEY_NODE_ID, nodeId).apply()

    fun clearPairing() = prefs.edit()
        .remove(KEY_PAIRING_TOKEN)
        .remove(KEY_NODE_ID)
        .apply()

    fun isPaired(): Boolean = getPairingToken() != null
}
