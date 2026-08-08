package edu.bmu.attendance.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores Maitri credentials in [EncryptedSharedPreferences], keyed by an
 * AES-256 master key held in the Android Keystore (StrongBox if available).
 *
 * The encrypted file is named `bmu_credentials` and is excluded from
 * cloud/auto backup via `data_extraction_rules.xml`.
 *
 * If Keystore unwrap fails (factory reset, OEM migration), we wipe the prefs
 * file and retry once. We never fall back to plaintext password storage.
 */
class CredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences? = createPrefs(appContext)

    /** False when the device Keystore cannot host an encrypted prefs file. */
    val isSecure: Boolean get() = prefs != null

    fun load(): Credentials? {
        val store = prefs ?: return null
        val u = store.getString(KEY_USERNAME, null) ?: return null
        val p = store.getString(KEY_PASSWORD, null) ?: return null
        return Credentials(u, p).takeIf { it.username.isNotBlank() && p.isNotBlank() }
    }

    fun save(creds: Credentials) {
        val store = prefs ?: throw IllegalStateException(
            "Secure credential storage is unavailable on this device.",
        )
        store.edit()
            .putString(KEY_USERNAME, creds.username.trim())
            .putString(KEY_PASSWORD, creds.password)
            .apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    fun hasCredentials(): Boolean = load() != null

    companion object {
        private const val TAG = "CredentialStore"
        private const val FILE_NAME = "bmu_credentials"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        private fun createPrefs(context: Context): SharedPreferences? {
            return try {
                encryptedPrefs(context)
            } catch (first: Exception) {
                Log.w(TAG, "Encrypted prefs failed; wiping and retrying.", first)
                runCatching { context.deleteSharedPreferences(FILE_NAME) }
                try {
                    encryptedPrefs(context)
                } catch (second: Exception) {
                    Log.e(TAG, "Encrypted credential storage unavailable.", second)
                    null
                }
            }
        }

        private fun encryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
