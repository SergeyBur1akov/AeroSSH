package com.companyname.aerossh.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.Base64

object HostKeyManager {
    private const val PREFS = "known_hosts_encrypted"; private const val KEY_SEPARATOR = "|"

    private fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(context, PREFS, MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).setRequestStrongBoxBacked(true).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    fun isKnown(context: Context, host: String, port: Int, keyType: String, fingerprint: String): Boolean { val s = getEncryptedPrefs(context).getString("$host:$port", null) ?: return false; val p = s.split(KEY_SEPARATOR); return p.size == 2 && p[0] == keyType && p[1] == fingerprint }
    fun saveHost(context: Context, host: String, port: Int, keyType: String, fingerprint: String) { getEncryptedPrefs(context).edit().putString("$host:$port", "$keyType$KEY_SEPARATOR$fingerprint").apply() }
    fun removeHost(context: Context, host: String, port: Int) { getEncryptedPrefs(context).edit().remove("$host:$port").apply() }
    fun getAllKnown(context: Context): Map<String, String> = getEncryptedPrefs(context).all.mapValues { it.value.toString() }
    fun computeFingerprint(pubKeyBytes: ByteArray): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(pubKeyBytes))
    fun computeFingerprintFromOpenSSH(pubKeyStr: String): String { val parts = pubKeyStr.trim().split("\\s+".toRegex()); if (parts.size < 2) return ""; return computeFingerprint(Base64.getDecoder().decode(parts[1])) }
}
