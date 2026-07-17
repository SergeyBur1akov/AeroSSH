package com.companyname.aerossh.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SupportFactory
import java.io.File

object SecureStorage {

    private const val PREFS_NAME = "aerossh_secure_prefs"
    private const val KEY_DB_PASS = "db_encryption_key"
    private const val KEY_APP_PASS = "app_password_hash"

    private var masterKey: MasterKey? = null
    private var securePrefs: SharedPreferences? = null

    fun init(context: Context) {
        val mk = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()
        masterKey = mk

        securePrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = securePrefs ?: throw IllegalStateException("SecureStorage not initialized")
        var pass = prefs.getString(KEY_DB_PASS, null)
        if (pass == null) {
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            pass = Base64.encodeToString(bytes, Base64.NO_WRAP)
            prefs.edit().putString(KEY_DB_PASS, pass).apply()
        }
        return Base64.decode(pass, Base64.NO_WRAP)
    }

    fun getSQLCipherFactory(context: Context): SupportFactory {
        val passphrase = getDatabasePassphrase(context)
        return SupportFactory(passphrase)
    }

    fun setAppPasswordHash(hash: String) {
        securePrefs?.edit()?.putString(KEY_APP_PASS, hash)?.apply()
    }

    fun getAppPasswordHash(): String? {
        return securePrefs?.getString(KEY_APP_PASS, null)
    }

    fun hasAppPassword(): Boolean {
        return !getAppPasswordHash().isNullOrBlank()
    }

    fun clear() {
        securePrefs?.edit()?.clear()?.apply()
    }

    private object Base64 {
        fun encodeToString(data: ByteArray, flags: Int): String =
            android.util.Base64.encodeToString(data, flags)

        fun decode(str: String, flags: Int): ByteArray =
            android.util.Base64.decode(str, flags)
    }
}
