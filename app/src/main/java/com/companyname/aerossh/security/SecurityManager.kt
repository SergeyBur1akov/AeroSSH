package com.companyname.aerossh.security

import android.content.Context
import android.os.Build
import android.view.WindowManager
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import java.io.File

object SecurityManager {
    fun secureZero(array: ByteArray) { array.fill(0); val dummy = array[0]; if (dummy != 0.toByte()) throw IllegalStateException("zeroing failed") }
    fun secureZeroChars(array: CharArray) { array.fill('\u0000'); val dummy = array[0]; if (dummy != '\u0000') throw IllegalStateException("zeroing failed") }

    private const val GCM_TAG_LENGTH = 128; private const val GCM_IV_LENGTH = 12

    fun generateKey(): SecretKey { val kg = javax.crypto.KeyGenerator.getInstance("AES"); kg.init(256, SecureRandom()); return kg.generateKey() }

    fun encrypt(data: ByteArray, key: SecretKey): String { val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key); return android.util.Base64.encodeToString(c.iv + c.doFinal(data), android.util.Base64.NO_WRAP) }
    fun decrypt(encoded: String, key: SecretKey): ByteArray { val d = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP); val iv = d.copyOfRange(0, GCM_IV_LENGTH); val ct = d.copyOfRange(GCM_IV_LENGTH, d.size); val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv)); return c.doFinal(ct) }

    fun secureWriteFile(file: File, data: String) { val tmp = File(file.absolutePath + ".tmp"); tmp.writeText(data); tmp.renameTo(file); file.setReadable(true, true); file.setWritable(true, true) }
    fun secureDelete(file: File) { if (!file.exists()) return; file.outputStream().use { it.write(ByteArray(file.length().toInt())) }; file.delete() }

    fun preventScreenshots(activity: android.app.Activity) { activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) }

    fun isDeviceRooted(): Boolean {
        for (path in arrayOf("/system/app/Superuser.apk", "/system/xbin/su", "/system/bin/su", "/sbin/su")) if (File(path).exists()) return true
        return try { val p = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su")); val r = p.inputStream.bufferedReader(); val l = r.readLine(); r.close(); p.errorStream.bufferedReader().readText(); p.waitFor(); l != null } catch (_: Exception) { false }
    }

    fun isEmulator(): Boolean = Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown") || Build.HARDWARE.contains("goldfish") || Build.MODEL.contains("Emulator") || Build.PRODUCT.contains("emulator")

    private var clipboardClearRunnable: Runnable? = null
    fun setupClipboardAutoClear(context: Context, delayMs: Long = 30_000) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.addPrimaryClipChangedListener {
            clipboardClearRunnable?.let { android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it) }
            clipboardClearRunnable = Runnable { cm.setPrimaryClip(null) }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(clipboardClearRunnable!!, delayMs)
        }
    }

    fun sha256(data: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString(":") { "%02x".format(it) }
}
