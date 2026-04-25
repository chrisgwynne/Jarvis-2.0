package ai.openclaw.jarvis.trust

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassphraseStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "jarvis_passphrases",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun set(speakerId: String, passphrase: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("${speakerId}_salt", salt.hex())
            .putString("${speakerId}_hash", hash(passphrase, salt))
            .apply()
    }

    fun verify(speakerId: String, passphrase: String): Boolean {
        val salt   = prefs.getString("${speakerId}_salt", null)?.unhex() ?: return false
        val stored = prefs.getString("${speakerId}_hash", null)          ?: return false
        return hash(passphrase, salt) == stored
    }

    /** True if any passphrase matches (used when speakerId is unknown). */
    fun verifyAny(passphrase: String): String? {
        val allKeys = prefs.all.keys
            .filter { it.endsWith("_hash") }
            .map { it.removeSuffix("_hash") }
        return allKeys.firstOrNull { verify(it, passphrase) }
    }

    fun has(speakerId: String): Boolean = prefs.contains("${speakerId}_hash")

    fun clear(speakerId: String) {
        prefs.edit()
            .remove("${speakerId}_salt")
            .remove("${speakerId}_hash")
            .apply()
    }

    private fun hash(passphrase: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(passphrase.toByteArray()).hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex(): ByteArray {
        val len = length / 2
        return ByteArray(len) { i ->
            ((this[2 * i].digitToInt(16) shl 4) + this[2 * i + 1].digitToInt(16)).toByte()
        }
    }
}
