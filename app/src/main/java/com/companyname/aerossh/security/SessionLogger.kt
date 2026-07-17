package com.companyname.aerossh.security

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.SecretKey
import javax.crypto.SecretKeySpec

class SessionLogger(private val context: Context) {
    private var logFile: File? = null; private var outputStream: FileOutputStream? = null
    private val buffer = ConcurrentLinkedQueue<String>()
    private val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private var encryptionKey: SecretKey? = null; private var isActive = false

    fun start(sessionId: String, encrypted: Boolean = true) {
        val logDir = File(context.filesDir, "logs"); logDir.mkdirs()
        logFile = File(logDir, "session_${sessionId}_$timestamp.aeslog")
        if (encrypted) {
            if (!LuksEncryption.isVaultUnlocked()) { isActive = false; return }
            val kb = ByteArray(32); SecureRandom().nextBytes(kb)
            encryptionKey = SecretKeySpec(kb, "AES")
            val encryptedKey: String = try { LuksEncryption.encryptWithMaster(android.util.Base64.encodeToString(kb, android.util.Base64.NO_WRAP)) } catch (_: Exception) { "" }
            kb.fill(0)
            if (encryptedKey.isNotEmpty()) File(logDir, "${logFile!!.name}.key").writeText(encryptedKey)
        }
        outputStream = FileOutputStream(logFile!!, true); isActive = true
        write("=== AeroSSH Session Log ===\nDate: $timestamp\nEncrypted: $encrypted\n===========================\n\n")
    }

    fun write(text: String) { if (!isActive) return; buffer.add("[${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())}] $text"); if (buffer.size >= 10) flush() }

    fun flush() { if (!isActive) return; val out = outputStream ?: return; while (buffer.isNotEmpty()) { val line = buffer.poll() ?: break; val bytes = line.toByteArray(Charsets.UTF_8); encryptionKey?.let { out.write(SecurityManager.encrypt(bytes, it).toByteArray()); out.write('\n'.code); bytes.fill(0) } ?: run { out.write(bytes); out.write('\n'.code) } }; out.flush() }

    fun stop() { isActive = false; flush(); outputStream?.close(); outputStream = null; encryptionKey = null }

    fun exportAsText(): File? {
        val file = logFile ?: return null; val ef = File(context.cacheDir, "export_${file.nameWithoutExtension}.txt")
        return try {
            val kf = File(file.parent, "${file.name}.key")
            val key: SecretKey? = if (kf.exists()) {
                val decryptedKey = try { LuksEncryption.decryptWithMaster(kf.readText().trim()) } catch (_: Exception) { "" }
                if (decryptedKey.isNotEmpty()) { val kb = android.util.Base64.decode(decryptedKey, android.util.Base64.NO_WRAP); SecretKeySpec(kb, "AES").also { kb.fill(0) } } else null
            } else null
            ef.bufferedWriter().use { w -> file.readLines().forEach { line -> key?.let { try { w.write(String(SecurityManager.decrypt(line.trim(), it), Charsets.UTF_8)) } catch (_: Exception) { w.write("[encrypted]") } } ?: w.write(line); w.newLine() } }; ef
        } catch (_: Exception) { null }
    }

    companion object { private var instance: SessionLogger? = null; fun getInstance(context: Context) = instance ?: SessionLogger(context.applicationContext).also { instance = it } }
}
