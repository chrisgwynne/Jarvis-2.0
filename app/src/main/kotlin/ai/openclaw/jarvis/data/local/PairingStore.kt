package ai.openclaw.jarvis.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = createEncryptedPrefs()

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context, "jarvis_pairing", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Tink keyset corrupted (reinstall, Keystore key invalidated, etc.).
            // Wipe the file and start fresh — device will re-pair on next connect.
            context.deleteSharedPreferences("jarvis_pairing")
            EncryptedSharedPreferences.create(
                context, "jarvis_pairing", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    companion object {
        private const val KEY_PRIVATE_KEY_PKCS8 = "ed25519_private_pkcs8_b64"
        private const val KEY_PUBLIC_KEY_RAW    = "ed25519_public_raw_b64url"
        private const val KEY_DEVICE_TOKEN      = "device_token"
        private const val KEY_NODE_ID           = "node_id"
        private const val KEY_PAIR_REQUEST_ID   = "pair_request_id"
        private const val KEY_IS_APPROVED       = "is_approved"
    }

    // ─── Ed25519 keypair ──────────────────────────────────────────────────────

    private fun ensureKeyPair() {
        if (prefs.getString(KEY_PRIVATE_KEY_PKCS8, null) != null) return

        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp  = kpg.generateKeyPair()

        // SPKI-encoded Ed25519 public key is 44 bytes.
        // First 12 bytes are the DER/OID prefix; raw key is bytes 12–43.
        val spki   = kp.public.encoded
        val rawPub = if (spki.size >= 44) spki.copyOfRange(12, 44) else spki

        prefs.edit()
            .putString(KEY_PRIVATE_KEY_PKCS8,
                Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
            .putString(KEY_PUBLIC_KEY_RAW,
                Base64.encodeToString(rawPub, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            .apply()
    }

    /** Stable device ID: SHA-256 hex of the raw Ed25519 public key. */
    fun getDeviceId(): String = try {
        ensureKeyPair()
        val rawPub = Base64.decode(
            prefs.getString(KEY_PUBLIC_KEY_RAW, "")!!,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        MessageDigest.getInstance("SHA-256")
            .digest(rawPub)
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "error:${e.javaClass.simpleName}"
    }

    /** Raw 32-byte Ed25519 public key as base64url (no padding). */
    fun getPublicKeyRawBase64Url(): String = try {
        ensureKeyPair()
        prefs.getString(KEY_PUBLIC_KEY_RAW, "")!!
    } catch (e: Exception) {
        ""
    }

    /**
     * Sign [payload] (UTF-8) with Ed25519 and return the signature as
     * base64url (no padding), matching the format expected by the server.
     */
    fun signPayload(payload: String): String {
        ensureKeyPair()
        val pkcs8Bytes = Base64.decode(
            prefs.getString(KEY_PRIVATE_KEY_PKCS8, "")!!,
            Base64.NO_WRAP,
        )
        val privateKey = KeyFactory.getInstance("Ed25519", "BC")
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))

        val sig = Signature.getInstance("Ed25519", "BC").apply {
            initSign(privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
        }.sign()

        return Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Returns true if Ed25519 / BouncyCastle is functional on this device. */
    fun isCryptoAvailable(): Boolean = try {
        ensureKeyPair()
        true
    } catch (e: Exception) {
        false
    }

    // ─── Device token (issued by gateway after hello-ok) ─────────────────────

    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)
    fun saveDeviceToken(token: String) =
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()

    // ─── Node identity (assigned by gateway) ─────────────────────────────────

    fun getNodeId(): String? = prefs.getString(KEY_NODE_ID, null)
    fun saveNodeId(nodeId: String) =
        prefs.edit().putString(KEY_NODE_ID, nodeId).apply()

    // ─── Pairing state ────────────────────────────────────────────────────────

    fun getPairRequestId(): String? = prefs.getString(KEY_PAIR_REQUEST_ID, null)
    fun savePairRequestId(id: String) =
        prefs.edit().putString(KEY_PAIR_REQUEST_ID, id).apply()

    fun isApproved(): Boolean = prefs.getBoolean(KEY_IS_APPROVED, false)
    fun setApproved(v: Boolean) =
        prefs.edit().putBoolean(KEY_IS_APPROVED, v).apply()

    /** Returns true if we have a device token from the gateway. */
    fun isPaired(): Boolean = getDeviceToken() != null

    /**
     * Clear stored device token, node ID, and pairing state.
     * The Ed25519 keypair (and thus the device ID) is kept so the device
     * identity remains stable on the server.
     */
    fun clearPairing() = prefs.edit()
        .remove(KEY_DEVICE_TOKEN)
        .remove(KEY_NODE_ID)
        .remove(KEY_PAIR_REQUEST_ID)
        .putBoolean(KEY_IS_APPROVED, false)
        .apply()

    /**
     * Full factory reset — also deletes the Ed25519 keypair so a new device
     * identity is generated on the next connection.
     */
    fun clearAll() = prefs.edit().clear().apply()

    // ─── Legacy compat (kept so call sites don't break during migration) ──────

    fun getPairingToken(): String? = getDeviceToken()
    fun savePairingToken(token: String) = saveDeviceToken(token)
}
