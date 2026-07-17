package com.companyname.aerossh.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LuksEncryption {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "AeroSSH_Luks_Master"
    private const val PREFS = "luks_vault"
    private const val KEY_ITERATIONS = 600_000
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val MAX_FAILED_ATTEMPTS = 10
    private const val MIN_PASSWORD_LENGTH = 8

    @Volatile
    private var masterKey: SecretKey? = null

    // --- Constant-time comparison to prevent timing attacks ---
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return constantTimeEquals(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    // --- Password hashing (for verification) ---

    fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        val hash = key.encoded
        spec.clearPassword()
        val result = Base64.encodeToString(hash, Base64.NO_WRAP)
        // Zero out hash bytes
        hash.fill(0)
        return result
    }

    fun verifyPassword(password: String, storedHash: String, salt: ByteArray): Boolean {
        val computed = hashPassword(password, salt)
        return constantTimeEquals(computed, storedHash)
    }

    // --- Key derivation (LUKS-style) ---

    fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(key.encoded, "AES")
        spec.clearPassword()
        key.encoded.fill(0)
        return secretKey
    }

    // --- LUKS-style encryption ---

    fun encrypt(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    fun decrypt(data: ByteArray, key: SecretKey): ByteArray {
        if (data.size < GCM_IV_LENGTH + 16) throw SecurityException("Ciphertext too short")
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(plainText: String, key: SecretKey): String {
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(plainBytes, key)
        plainBytes.fill(0)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptString(encoded: String, key: SecretKey): String {
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        val result = decrypt(encrypted, key)
        val str = String(result, Charsets.UTF_8)
        result.fill(0)
        return str
    }

    // --- Encrypted SharedPreferences for vault metadata ---

    private fun getSecurePrefs(context: Context): SharedPreferences {
        val mk = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()

        return androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            PREFS,
            mk,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Vault management ---

    fun setupVault(context: Context, password: String) {
        require(password.length >= MIN_PASSWORD_LENGTH) { "Password too short" }

        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)

        val passwordHash = hashPassword(password, salt)
        val masterKey = deriveKeyFromPassword(password, salt)

        val prefs = getSecurePrefs(context).edit()
        prefs.putString("password_hash", passwordHash)
        prefs.putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        prefs.putInt("failed_attempts", 0)
        prefs.putBoolean("vault_initialized", true)
        prefs.apply()

        // Zero salt from memory
        salt.fill(0)

        this.masterKey = masterKey
    }

    fun unlockVault(context: Context, password: String): Boolean {
        val prefs = getSecurePrefs(context)

        val failedAttempts = prefs.getInt("failed_attempts", 0)
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            wipeVault(context)
            return false
        }

        val storedHash = prefs.getString("password_hash", null) ?: return false
        val saltB64 = prefs.getString("salt", null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)

        if (!verifyPassword(password, storedHash, salt)) {
            prefs.edit().putInt("failed_attempts", failedAttempts + 1).apply()
            salt.fill(0)
            return false
        }

        prefs.edit().putInt("failed_attempts", 0).apply()
        this.masterKey = deriveKeyFromPassword(password, salt)
        salt.fill(0)
        return true
    }

    fun isVaultInitialized(context: Context): Boolean {
        return try {
            getSecurePrefs(context).getBoolean("vault_initialized", false)
        } catch (_: Exception) {
            false
        }
    }

    fun isVaultUnlocked(): Boolean = masterKey != null

    fun changePassword(context: Context, oldPassword: String, newPassword: String): Boolean {
        require(newPassword.length >= MIN_PASSWORD_LENGTH) { "Password too short" }
        if (!unlockVault(context, oldPassword)) return false
        setupVault(context, newPassword)
        return true
    }

    fun wipeVault(context: Context) {
        try {
            getSecurePrefs(context).edit().clear().apply()
        } catch (_: Exception) {}
        // Securely zero master key
        masterKey?.let { key ->
            try {
                val dummy = ByteArray(32)
                SecureRandom().nextBytes(dummy)
                Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key)
            } catch (_: Exception) {}
        }
        masterKey = null
    }

    fun isMinPasswordLength(): Int = MIN_PASSWORD_LENGTH

    // --- Encrypt/decrypt with master key ---

    fun encryptWithMaster(plainText: String): String {
        val key = masterKey ?: throw IllegalStateException("Vault locked")
        return encryptString(plainText, key)
    }

    fun decryptWithMaster(encoded: String): String {
        val key = masterKey ?: throw IllegalStateException("Vault locked")
        return try {
            decryptString(encoded, key)
        } catch (_: Exception) {
            ""
        }
    }

    fun encryptBytesWithMaster(data: ByteArray): ByteArray {
        val key = masterKey ?: throw IllegalStateException("Vault locked")
        return encrypt(data, key)
    }

    fun decryptBytesWithMaster(data: ByteArray): ByteArray {
        val key = masterKey ?: throw IllegalStateException("Vault locked")
        return decrypt(data, key)
    }
}
