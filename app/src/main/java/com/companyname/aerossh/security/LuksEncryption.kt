package com.companyname.aerossh.security

import android.content.Context
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
    private const val KEY_ITERATIONS = 600_000; private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128; private const val GCM_IV_LENGTH = 12
    private const val MAX_FAILED_ATTEMPTS = 10; private const val MIN_PASSWORD_LENGTH = 8
    private const val PREFS = "luks_vault"
    @Volatile private var masterKey: SecretKey? = null

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean { if (a.size != b.size) return false; var diff = 0; for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt()); return diff == 0 }
    private fun constantTimeEquals(a: String, b: String): Boolean = constantTimeEquals(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun hashPassword(password: String, salt: ByteArray): String { val spec = PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, KEY_LENGTH); val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec); val hash = key.encoded; spec.clearPassword(); val r = Base64.encodeToString(hash, Base64.NO_WRAP); hash.fill(0); return r }
    fun verifyPassword(password: String, storedHash: String, salt: ByteArray): Boolean = constantTimeEquals(hashPassword(password, salt), storedHash)
    fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey { val spec = PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, KEY_LENGTH); val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec); val sk = SecretKeySpec(key.encoded, "AES"); spec.clearPassword(); return sk }

    fun encrypt(data: ByteArray, key: SecretKey): ByteArray { val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key); return c.iv + c.doFinal(data) }
    fun decrypt(data: ByteArray, key: SecretKey): ByteArray { if (data.size < GCM_IV_LENGTH + 16) throw SecurityException("Ciphertext too short"); val iv = data.copyOfRange(0, GCM_IV_LENGTH); val ct = data.copyOfRange(GCM_IV_LENGTH, data.size); val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv)); return c.doFinal(ct) }
    fun encryptString(plainText: String, key: SecretKey): String { val pb = plainText.toByteArray(Charsets.UTF_8); val r = Base64.encodeToString(encrypt(pb, key), Base64.NO_WRAP); pb.fill(0); return r }
    fun decryptString(encoded: String, key: SecretKey): String { val d = decrypt(Base64.decode(encoded, Base64.NO_WRAP), key); val s = String(d, Charsets.UTF_8); d.fill(0); return s }

    private fun getSecurePrefs(context: Context) = androidx.security.crypto.EncryptedSharedPreferences.create(context, PREFS, androidx.security.crypto.MasterKey.Builder(context).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).setRequestStrongBoxBacked(true).build(), androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    fun setupVault(context: Context, password: String) { require(password.length >= MIN_PASSWORD_LENGTH); val salt = ByteArray(32); SecureRandom().nextBytes(salt); val h = hashPassword(password, salt); val mk = deriveKeyFromPassword(password, salt); getSecurePrefs(context).edit().putString("password_hash", h).putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).putInt("failed_attempts", 0).putBoolean("vault_initialized", true).apply(); salt.fill(0); masterKey = mk }

    fun unlockVault(context: Context, password: String): Boolean { val p = getSecurePrefs(context); val fa = p.getInt("failed_attempts", 0); if (fa >= MAX_FAILED_ATTEMPTS) { wipeVault(context); return false }; val h = p.getString("password_hash", null) ?: return false; val sb = p.getString("salt", null) ?: return false; val salt = Base64.decode(sb, Base64.NO_WRAP); if (!verifyPassword(password, h, salt)) { p.edit().putInt("failed_attempts", fa + 1).apply(); salt.fill(0); return false }; p.edit().putInt("failed_attempts", 0).apply(); masterKey = deriveKeyFromPassword(password, salt); salt.fill(0); return true }

    fun isVaultInitialized(context: Context): Boolean = try { getSecurePrefs(context).getBoolean("vault_initialized", false) } catch (_: Exception) { false }
    fun isVaultUnlocked(): Boolean = masterKey != null
    fun lockVault() { masterKey = null }
    fun isMinPasswordLength(): Int = MIN_PASSWORD_LENGTH

    fun changePassword(context: Context, oldP: String, newP: String): Boolean { require(newP.length >= MIN_PASSWORD_LENGTH); if (!unlockVault(context, oldP)) return false; setupVault(context, newP); return true }

    fun wipeVault(context: Context) { try { getSecurePrefs(context).edit().clear().apply() } catch (_: Exception) {}; masterKey = null }

    fun encryptWithMaster(plainText: String): String { val k = masterKey ?: throw IllegalStateException("Vault locked"); return encryptString(plainText, k) }
    fun decryptWithMaster(encoded: String): String { val k = masterKey ?: throw IllegalStateException("Vault locked"); return try { decryptString(encoded, k) } catch (e: Exception) { android.util.Log.e("LuksEncryption", "Decryption failed - possible tampering: ${e.javaClass.simpleName}"); "" } }
    fun encryptBytesWithMaster(data: ByteArray): ByteArray = encrypt(data, masterKey ?: throw IllegalStateException("Vault locked"))
    fun decryptBytesWithMaster(data: ByteArray): ByteArray = decrypt(data, masterKey ?: throw IllegalStateException("Vault locked"))
}
