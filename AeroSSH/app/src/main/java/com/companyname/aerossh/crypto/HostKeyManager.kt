package com.companyname.aerossh.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.Base64

object HostKeyManager {

    private const val PREFS = "known_hosts_encrypted"
    private const val KEY_SEPARATOR = "|"

    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val mk = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isKnown(context: Context, host: String, port: Int, keyType: String, fingerprint: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val key = "$host:$port"
        val stored = prefs.getString(key, null) ?: return false
        val parts = stored.split(KEY_SEPARATOR)
        return parts.size == 2 && parts[0] == keyType && parts[1] == fingerprint
    }

    fun saveHost(context: Context, host: String, port: Int, keyType: String, fingerprint: String) {
        val prefs = getEncryptedPrefs(context).edit()
        val key = "$host:$port"
        prefs.putString(key, "$keyType$KEY_SEPARATOR$fingerprint")
        prefs.apply()
    }

    fun removeHost(context: Context, host: String, port: Int) {
        val prefs = getEncryptedPrefs(context).edit()
        prefs.remove("$host:$port")
        prefs.apply()
    }

    fun getAllKnown(context: Context): Map<String, String> {
        val prefs = getEncryptedPrefs(context)
        return prefs.all.mapValues { it.value.toString() }
    }

    fun computeFingerprint(pubKeyBytes: ByteArray, algorithm: String = "SHA-256"): String {
        val md = MessageDigest.getInstance(algorithm)
        val hash = md.digest(pubKeyBytes)
        return Base64.getEncoder().encodeToString(hash)
    }

    fun computeFingerprintFromOpenSSH(pubKeyStr: String): String {
        val parts = pubKeyStr.trim().split("\\s+".toRegex())
        if (parts.size < 2) return ""
        val keyBytes = Base64.getDecoder().decode(parts[1])
        return computeFingerprint(keyBytes)
    }
}
