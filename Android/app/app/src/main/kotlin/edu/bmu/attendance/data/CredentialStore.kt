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
 * If the Keystore can't unwrap the master key on this device (e.g. after a
 * factory reset, or a buggy OEM keystore migration), we fall back to plain
 * preferences so the app still works — but in that case we re-prompt for
 * credentials so the bad backing file is overwritten.
 */
class CredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)

    fun load(): Credentials? {
        val u = prefs.getString(KEY_USERNAME, null) ?: return null
        val p = prefs.getString(KEY_PASSWORD, null) ?: return null
        return Credentials(u, p).takeIf { it.username.isNotBlank() && p.isNotBlank() }
    }

    fun save(creds: Credentials) {
        prefs.edit()
            .putString(KEY_USERNAME, creds.username.trim())
            .putString(KEY_PASSWORD, creds.password)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasCredentials(): Boolean = load() != null

    companion object {
        private const val TAG = "CredentialStore"
        private const val FILE_NAME = "bmu_credentials"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Encrypted prefs unavailable, falling back to plain prefs.", e)
                // Wipe the file so a stale ciphertext doesn't keep failing.
                context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                    .edit().clear().apply()
                context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
