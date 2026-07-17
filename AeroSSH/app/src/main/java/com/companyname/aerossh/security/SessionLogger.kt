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
import javax.crypto.spec.SecretKeySpec

class SessionLogger(private val context: Context) {

    private var logFile: File? = null
    private var outputStream: FileOutputStream? = null
    private val buffer = ConcurrentLinkedQueue<String>()
    private val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private var encryptionKey: SecretKey? = null
    private var isActive = false

    fun start(sessionId: String, encrypted: Boolean = true) {
        val logDir = File(context.filesDir, "logs")
        logDir.mkdirs()

        val fileName = "session_${sessionId}_$timestamp.aeslog"
        logFile = File(logDir, fileName)

        if (encrypted) {
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            encryptionKey = SecretKeySpec(keyBytes, "AES")

            // Store key encrypted via KeystoreHelper
            val keyFile = File(logDir, "${fileName}.key")
            val encodedKey = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
            keyFile.writeText(encodedKey)
            keyBytes.fill(0)
        }

        outputStream = FileOutputStream(logFile!!, true)
        isActive = true

        val header = "=== AeroSSH Session Log ===\n" +
                "Date: $timestamp\n" +
                "Encrypted: $encrypted\n" +
                "===========================\n\n"
        write(header)
    }

    fun write(text: String) {
        if (!isActive) return

        val line = "[${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())}] $text"
        buffer.add(line)

        if (buffer.size >= 10) {
            flush()
        }
    }

    fun flush() {
        if (!isActive) return

        val out = outputStream ?: return
        while (buffer.isNotEmpty()) {
            val line = buffer.poll() ?: break
            val bytes = line.toByteArray(Charsets.UTF_8)

            encryptionKey?.let { key ->
                val encrypted = SecurityManager.encrypt(bytes, key)
                out.write(encrypted.toByteArray())
                out.write('\n'.code)
                bytes.fill(0)
            } ?: run {
                out.write(bytes)
                out.write('\n'.code)
            }
        }
        out.flush()
    }

    fun stop() {
        isActive = false
        flush()
        outputStream?.close()
        outputStream = null
        encryptionKey = null
    }

    fun exportAsText(): File? {
        val file = logFile ?: return null
        val exportFile = File(context.cacheDir, "export_${file.nameWithoutExtension}.txt")

        return try {
            val keyFile = File(file.parent, "${file.name}.key")
            val sessionKey = if (keyFile.exists()) {
                val encodedKey = keyFile.readText().trim()
                val keyBytes = android.util.Base64.decode(encodedKey, android.util.Base64.NO_WRAP)
                SecretKeySpec(keyBytes, "AES").also { keyBytes.fill(0) }
            } else null

            val lines = file.readLines()
            exportFile.bufferedWriter().use { writer ->
                for (line in lines) {
                    sessionKey?.let { key ->
                        try {
                            val decrypted = SecurityManager.decrypt(line.trim(), key)
                            writer.write(String(decrypted, Charsets.UTF_8))
                        } catch (_: Exception) {
                            writer.write("[encrypted]")
                        }
                    } ?: writer.write(line)
                    writer.newLine()
                }
            }
            exportFile
        } catch (_: Exception) {
            null
        }
    }

    fun getLogFile(): File? = logFile

    companion object {
        private var instance: SessionLogger? = null

        fun getInstance(context: Context): SessionLogger {
            return instance ?: SessionLogger(context.applicationContext).also { instance = it }
        }
    }
}
