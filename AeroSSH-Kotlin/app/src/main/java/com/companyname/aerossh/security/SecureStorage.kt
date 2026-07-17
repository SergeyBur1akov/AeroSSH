package com.companyname.aerossh.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SupportFactory

object SecureStorage {
    private const val PREFS_NAME = "aerossh_secure_prefs"; private const val KEY_DB_PASS = "db_encryption_key"

    fun init(context: Context) { /* EncryptedSharedPreferences created lazily */ }

    fun getDatabasePassphrase(context: Context): ByteArray {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).setRequestStrongBoxBacked(true).build()
        val prefs = EncryptedSharedPreferences.create(context, PREFS_NAME, mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        var pass = prefs.getString(KEY_DB_PASS, null)
        if (pass == null) { val bytes = ByteArray(32); java.security.SecureRandom().nextBytes(bytes); pass = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP); prefs.edit().putString(KEY_DB_PASS, pass).apply() }
        return android.util.Base64.decode(pass, android.util.Base64.NO_WRAP)
    }

    fun getSQLCipherFactory(context: Context): SupportFactory = SupportFactory(getDatabasePassphrase(context))
}
