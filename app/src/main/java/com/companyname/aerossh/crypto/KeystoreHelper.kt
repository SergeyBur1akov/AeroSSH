package com.companyname.aerossh.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreHelper {
    private const val KEY_ALIAS = "AeroSSH_MasterKey"; private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12; private const val TAG_SIZE = 128

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getEntry(KEY_ALIAS, null)?.let { return (it as KeyStore.SecretKeyEntry).secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return kg.generateKey()
    }

    fun encrypt(plainText: String): String { val c = Cipher.getInstance(TRANSFORMATION); c.init(Cipher.ENCRYPT_MODE, getOrCreateKey()); return Base64.encodeToString(c.iv + c.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP) }
    fun decrypt(encryptedBase64: String): String { val d = Base64.decode(encryptedBase64, Base64.NO_WRAP); val iv = d.copyOfRange(0, IV_SIZE); val ct = d.copyOfRange(IV_SIZE, d.size); val c = Cipher.getInstance(TRANSFORMATION); c.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE, iv)); return String(c.doFinal(ct), Charsets.UTF_8) }
    fun encryptPassword(password: String): String = if (password.isEmpty()) "" else encrypt(password)
    fun decryptPassword(encrypted: String): String = if (encrypted.isEmpty()) "" else try { decrypt(encrypted) } catch (_: Exception) { "" }
}
