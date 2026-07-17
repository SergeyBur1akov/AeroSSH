package com.companyname.aerossh

import android.content.Context
import com.companyname.aerossh.crypto.HostKeyManager
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SshService(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: CharArray
) : Closeable {

    private var client: SSHClient? = null
    private var session: Session? = null
    private var shellIn: InputStream? = null
    private var shellOut: OutputStream? = null
    private val connected = AtomicBoolean(false)

    // Host key verification state
    enum class HostKeyStatus { UNKNOWN, KNOWN, CHANGED, ERROR }
    private val hostKeyStatus = AtomicReference(HostKeyStatus.UNKNOWN)
    private var hostKeyFingerprint = ""

    fun connect(context: Context? = null) {
        val c = SSHClient().apply {
            if (context != null) {
                // Use proper host key verification against known_hosts
                addHostKeyVerifier { sshHost, sshPort, key ->
                    val fingerprint = computeFingerprint(key)
                    hostKeyFingerprint = fingerprint

                    if (!HostKeyManager.isKnown(context, sshHost, sshPort, key.algorithm, fingerprint)) {
                        hostKeyStatus.set(HostKeyStatus.UNKNOWN)
                        false  // Will need explicit trust
                    } else {
                        hostKeyStatus.set(HostKeyStatus.KNOWN)
                        true
                    }
                }
            } else {
                // Fallback: verify but mark as unknown for caller to handle
                addHostKeyVerifier { sshHost, sshPort, key ->
                    val fingerprint = computeFingerprint(key)
                    hostKeyFingerprint = fingerprint
                    hostKeyStatus.set(HostKeyStatus.UNKNOWN)
                    false
                }
            }

            connect(host, port)
            authPassword(username, password)
            // Password no longer needed after auth
        }
        client = c
        connected.set(true)
    }

    fun connectTrusting(context: Context) {
        val c = SSHClient().apply {
            addHostKeyVerifier { sshHost, sshPort, key ->
                val fingerprint = computeFingerprint(key)
                hostKeyFingerprint = fingerprint
                // Save as trusted
                HostKeyManager.saveHost(context, sshHost, sshPort, key.algorithm, fingerprint)
                hostKeyStatus.set(HostKeyStatus.KNOWN)
                true
            }
            connect(host, port)
            authPassword(username, password)
        }
        client = c
        connected.set(true)
    }

    fun connectWithKnownHosts(knownHosts: Map<String, String>) {
        val c = SSHClient().apply {
            addHostKeyVerifier { sshHost, sshPort, key ->
                val fingerprint = computeFingerprint(key)
                hostKeyFingerprint = fingerprint
                val hostKey = "$sshHost:$sshPort|${key.algorithm}|$fingerprint"
                if (knownHosts.containsKey(hostKey)) {
                    hostKeyStatus.set(HostKeyStatus.KNOWN)
                    true
                } else {
                    hostKeyStatus.set(HostKeyStatus.UNKNOWN)
                    false
                }
            }
            connect(host, port)
            authPassword(username, password)
        }
        client = c
        connected.set(true)
    }

    fun getHostKeyStatus(): HostKeyStatus = hostKeyStatus.get()
    fun getHostKeyFingerprint(): String = hostKeyFingerprint

    fun getHostKeyInfo(): Pair<String, String> {
        val c = client ?: return "" to ""
        val serverKey = c.hostKeyEntry?.key ?: return "" to ""
        val keyType = serverKey.algorithm
        val fingerprint = computeFingerprint(serverKey)
        return keyType to fingerprint
    }

    private fun computeFingerprint(key: PublicKey): String {
        val md = MessageDigest.getInstance("SHA-256")
        val encoded = key.encoded
        val hash = md.digest(encoded)
        encoded.fill(0)
        return Base64.getEncoder().encodeToString(hash)
    }

    fun openShell(onOutput: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val c = client ?: throw IllegalStateException("Not connected")
        val s = c.startSession().apply {
            allocateDefaultPTY()
            startShell()
        }
        session = s
        shellIn = s.inputStream
        shellOut = s.outputStream

        Thread {
            try {
                val buf = ByteArray(4096)
                while (connected.get()) {
                    val n = shellIn?.read(buf) ?: break
                    if (n == -1) break
                    val copy = buf.copyOf(n)
                    onOutput(copy)
                    copy.fill(0)
                }
            } catch (_: IOException) {}
        }.apply { isDaemon = true; name = "ssh-reader" }.start()

        Thread {
            try {
                val errBuf = ByteArray(1024)
                val errStream = s.extendedOutputStream
                while (connected.get()) {
                    val n = errStream.read(errBuf)
                    if (n == -1) break
                    val errStr = String(errBuf, 0, n, Charsets.UTF_8)
                    onError(errStr)
                }
            } catch (_: IOException) {}
        }.apply { isDaemon = true; name = "ssh-stderr" }.start()
    }

    fun send(data: ByteArray) {
        try {
            shellOut?.write(data)
            shellOut?.flush()
        } catch (_: IOException) {}
    }

    fun sendText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        send(bytes)
        bytes.fill(0)
    }

    fun sendKey(keyCode: Int) {
        val seq = when (keyCode) {
            KEY_UP    -> byteArrayOf(0x1B, 0x5B, 0x41)
            KEY_DOWN  -> byteArrayOf(0x1B, 0x5B, 0x42)
            KEY_RIGHT -> byteArrayOf(0x1B, 0x5B, 0x43)
            KEY_LEFT  -> byteArrayOf(0x1B, 0x5B, 0x44)
            KEY_HOME  -> byteArrayOf(0x1B, 0x5B, 0x48)
            KEY_END   -> byteArrayOf(0x1B, 0x5B, 0x46)
            KEY_PGUP  -> byteArrayOf(0x1B, 0x5B, 0x35, 0x7E)
            KEY_PGDN  -> byteArrayOf(0x1B, 0x5B, 0x36, 0x7E)
            KEY_DEL   -> byteArrayOf(0x1B, 0x5B, 0x33, 0x7E)
            KEY_TAB   -> byteArrayOf(0x09)
            KEY_ESC   -> byteArrayOf(0x1B)
            KEY_ENTER -> byteArrayOf(0x0D)
            else -> byteArrayOf()
        }
        send(seq)
    }

    fun sendCtrlChar(c: Char) {
        val code = c.lowercaseChar().code - 'a'.code + 1
        if (code in 1..26) {
            send(byteArrayOf(code.toByte()))
        }
    }

    fun isConnected(): Boolean = connected.get()

    fun resize(cols: Int, rows: Int) {
        session?.setPTYSize(cols, rows)
    }

    override fun close() {
        connected.set(false)
        try { shellOut?.close() } catch (_: IOException) {}
        try { shellIn?.close() } catch (_: IOException) {}
        try { session?.close() } catch (_: IOException) {}
        try { client?.disconnect() } catch (_: IOException) {}
        // Securely clear password from memory
        password.fill('\u0000')
    }

    fun runCommand(command: String): String {
        val c = client ?: throw IllegalStateException("Not connected")
        val s = c.startSession()
        return s.use { sess ->
            val cmd = sess.exec(command)
            val output = String(cmd.inputStream.readBytes(), Charsets.UTF_8)
            val err = String(cmd.errorStream.readBytes(), Charsets.UTF_8)
            cmd.join()
            output + err
        }
    }

    companion object {
        const val KEY_UP = 1
        const val KEY_DOWN = 2
        const val KEY_LEFT = 3
        const val KEY_RIGHT = 4
        const val KEY_HOME = 5
        const val KEY_END = 6
        const val KEY_PGUP = 7
        const val KEY_PGDN = 8
        const val KEY_DEL = 9
        const val KEY_TAB = 10
        const val KEY_ESC = 11
        const val KEY_ENTER = 12
    }
}
