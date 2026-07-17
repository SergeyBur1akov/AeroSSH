package com.companyname.aerossh.security

import android.content.Context
import android.os.Build
import android.view.WindowManager
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import java.io.File

object SecurityManager {

    // --- Secure memory: zero out byte arrays ---
    fun secureZero(array: ByteArray) {
        array.fill(0)
    }

    fun secureZeroChars(array: CharArray) {
        array.fill('\u0000')
    }

    fun secureString(s: String): ByteArray {
        return s.toByteArray(Charsets.UTF_8)
    }

    // --- AES-256-GCM encryption ---
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    fun generateKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256, SecureRandom())
        return kg.generateKey()
    }

    fun encrypt(data: ByteArray, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, key: SecretKey): ByteArray {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    // --- Secure file write (atomic) ---
    fun secureWriteFile(file: File, data: String) {
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(data)
        tmp.renameTo(file)
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    // --- Wipe file content before delete ---
    fun secureDelete(file: File) {
        if (!file.exists()) return
        val length = file.length()
        file.outputStream().use { out ->
            out.write(ByteArray(length.toInt()))
        }
        file.delete()
    }

    // --- Prevent screenshots ---
    fun preventScreenshots(activity: android.app.Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    // --- Root detection ---
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = process.inputStream.bufferedReader()
            val line = reader.readLine()
            reader.close()
            line != null
        } catch (_: Exception) {
            false
        }
    }

    // --- Detect emulator ---
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    // --- Clipboard auto-clear ---
    private var clipboardClearRunnable: Runnable? = null

    fun setupClipboardAutoClear(context: Context, delayMs: Long = 30_000) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.addPrimaryClipChangedListener {
            clipboardClearRunnable?.let { android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it) }
            clipboardClearRunnable = Runnable {
                cm.primaryClip = null
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(clipboardClearRunnable!!, delayMs)
        }
    }

    // --- SHA-256 fingerprint ---
    fun sha256(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(data)
        return hash.joinToString(":") { "%02x".format(it) }
    }
}
