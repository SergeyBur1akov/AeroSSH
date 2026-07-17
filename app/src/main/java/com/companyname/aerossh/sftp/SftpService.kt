package com.companyname.aerossh.sftp

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SftpService(private val ssh: SSHClient) : Closeable {
    private var client: SFTPClient? = null

    fun connect() { client = ssh.newSFTPClient() }
    fun listFiles(path: String): List<SftpEntry> { val c = client ?: throw IllegalStateException("Not connected"); return c.ls(path).map { SftpEntry(it.name, resolvePath(path, it.name), it.isDirectory, it.attributes.size, it.attributes.permissions?.toString() ?: "", it.attributes.mTime.toLong() * 1000) } }
    fun stat(path: String): SftpEntry { val c = client ?: throw IllegalStateException("Not connected"); val a = c.stat(path); return SftpEntry(path.substringAfterLast('/'), path, a.isDirectory, a.size, a.permissions?.toString() ?: "", a.mTime.toLong() * 1000) }
    fun mkdir(path: String) { client?.mkdir(path) }
    fun rm(path: String) { client?.rm(path) }
    fun rmdir(path: String) { client?.rmdir(path) }
    fun rename(old: String, new: String) { client?.rename(old, new) }
    fun download(remotePath: String, localOut: OutputStream) { (client ?: throw IllegalStateException("Not connected")).get(remotePath, localOut) }
    fun upload(localIn: InputStream, remotePath: String) { (client ?: throw IllegalStateException("Not connected")).put(localIn, remotePath) }
    fun pwd(): String = client?.pwd() ?: "/"
    fun resolvePath(base: String, child: String): String = if (child.startsWith("/")) child else "$base/$child".replace("//", "/")
    override fun close() { try { client?.close() } catch (_: IOException) {} }
    data class SftpEntry(val name: String, val path: String, val isDirectory: Boolean, val size: Long = 0, val permissions: String = "", val modTime: Long = 0)
}
