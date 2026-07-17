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
import android.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SshService(private val host: String, private val port: Int, private val username: String, private val password: CharArray) : Closeable {
    private var client: SSHClient? = null; private var session: Session? = null; private var shellIn: InputStream? = null; private var shellOut: OutputStream? = null
    private val connected = AtomicBoolean(false)
    enum class HostKeyStatus { UNKNOWN, KNOWN, CHANGED, ERROR }
    private val hostKeyStatus = AtomicReference(HostKeyStatus.UNKNOWN); private var hostKeyFingerprint = ""

    fun connect(context: Context? = null) {
        val c = SSHClient().apply {
            addHostKeyVerifier { sshHost, sshPort, key ->
                val fp = computeFingerprint(key); hostKeyFingerprint = fp
                if (context != null && HostKeyManager.isKnown(context, sshHost, sshPort, key.algorithm, fp)) { hostKeyStatus.set(HostKeyStatus.KNOWN); true }
                else { hostKeyStatus.set(HostKeyStatus.UNKNOWN); false }
            }
            connect(host, port); authPassword(username, password)
        }
        client = c; connected.set(true)
    }

    fun connectTrusting(context: Context) {
        val c = SSHClient().apply {
            addHostKeyVerifier { sshHost, sshPort, key -> val fp = computeFingerprint(key); hostKeyFingerprint = fp; HostKeyManager.saveHost(context, sshHost, sshPort, key.algorithm, fp); hostKeyStatus.set(HostKeyStatus.KNOWN); true }
            connect(host, port); authPassword(username, password)
        }
        client = c; connected.set(true)
    }

    fun getHostKeyInfo(): Pair<String, String> { val sk = client?.hostKeyEntry?.key ?: return "" to ""; return sk.algorithm to computeFingerprint(sk) }
    fun getClient(): SSHClient = client ?: throw IllegalStateException("Not connected")
    private fun computeFingerprint(key: PublicKey): String { val h = MessageDigest.getInstance("SHA-256").digest(key.encoded); return Base64.encodeToString(h, Base64.NO_WRAP) }

    fun openShell(onOutput: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val c = client ?: throw IllegalStateException("Not connected")
        val s = c.startSession().apply { allocateDefaultPTY(); startShell() }
        session = s; shellIn = s.inputStream; shellOut = s.outputStream
        Thread { try { val buf = ByteArray(4096); while (connected.get()) { val n = shellIn?.read(buf) ?: break; if (n == -1) break; val copy = buf.copyOf(n); onOutput(copy); copy.fill(0) } } catch (_: IOException) {} }.apply { isDaemon = true; name = "ssh-reader" }.start()
        Thread { try { val buf = ByteArray(1024); val es = s.extendedOutputStream; while (connected.get()) { val n = es.read(buf); if (n == -1) break; onError(String(buf, 0, n, Charsets.UTF_8)) } } catch (_: IOException) {} }.apply { isDaemon = true; name = "ssh-stderr" }.start()
    }

    fun send(data: ByteArray) { try { shellOut?.write(data); shellOut?.flush() } catch (_: IOException) {} }
    fun sendText(text: String) { val b = text.toByteArray(Charsets.UTF_8); send(b); b.fill(0) }
    fun sendKey(keyCode: Int) { val seq = when (keyCode) { 1 -> byteArrayOf(27,91,65); 2 -> byteArrayOf(27,91,66); 3 -> byteArrayOf(27,91,68); 4 -> byteArrayOf(27,91,67); 5 -> byteArrayOf(27,91,72); 6 -> byteArrayOf(27,91,70); 7 -> byteArrayOf(27,91,53,126); 8 -> byteArrayOf(27,91,54,126); 9 -> byteArrayOf(27,91,51,126); 10 -> byteArrayOf(9); 11 -> byteArrayOf(27); 12 -> byteArrayOf(13); else -> byteArrayOf() }; send(seq) }
    fun sendCtrlChar(c: Char) { val code = c.lowercaseChar().code - 'a'.code + 1; if (code in 1..26) send(byteArrayOf(code.toByte())) }
    fun isConnected(): Boolean = connected.get()
    fun resize(cols: Int, rows: Int) { session?.setPTYSize(cols, rows) }
    override fun close() { connected.set(false); try { shellOut?.close() } catch (_: IOException) {}; try { shellIn?.close() } catch (_: IOException) {}; try { session?.close() } catch (_: IOException) {}; try { client?.disconnect() } catch (_: IOException) {}; password.fill('\u0000') }
    fun runCommand(command: String): String { val c = client ?: throw IllegalStateException("Not connected"); return c.startSession().use { s -> val cmd = s.exec(command); val o = String(cmd.inputStream.readBytes(), Charsets.UTF_8); val e = String(cmd.errorStream.readBytes(), Charsets.UTF_8); cmd.join(); o + e } }

    companion object { const val KEY_UP = 1; const val KEY_DOWN = 2; const val KEY_LEFT = 3; const val KEY_RIGHT = 4; const val KEY_HOME = 5; const val KEY_END = 6; const val KEY_PGUP = 7; const val KEY_PGDN = 8; const val KEY_DEL = 9; const val KEY_TAB = 10; const val KEY_ESC = 11; const val KEY_ENTER = 12 }
}
