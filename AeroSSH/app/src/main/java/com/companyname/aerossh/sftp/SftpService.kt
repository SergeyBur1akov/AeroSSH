package com.companyname.aerossh.sftp

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SftpService(private val ssh: SSHClient) : Closeable {

    private var client: SFTPClient? = null

    fun connect() {
        client = ssh.newSFTPClient()
    }

    fun listFiles(path: String): List<SftpEntry> {
        val c = client ?: throw IllegalStateException("Not connected")
        return c.ls(path).map { it.toEntry(path) }
    }

    fun stat(path: String): SftpEntry {
        val c = client ?: throw IllegalStateException("Not connected")
        val attrs = c.stat(path)
        return SftpEntry(
            name = path.substringAfterLast('/'),
            path = path,
            isDirectory = attrs.isDirectory,
            size = attrs.size,
            permissions = attrs.permissions?.toString() ?: "",
            modTime = attrs.mTime.toLong() * 1000
        )
    }

    fun mkdir(path: String) {
        client?.mkdir(path)
    }

    fun rm(path: String) {
        client?.rm(path)
    }

    fun rmdir(path: String) {
        client?.rmdir(path)
    }

    fun rename(oldPath: String, newPath: String) {
        client?.rename(oldPath, newPath)
    }

    fun download(remotePath: String, localOut: OutputStream) {
        val c = client ?: throw IllegalStateException("Not connected")
        c.get(remotePath, localOut)
    }

    fun upload(localIn: InputStream, remotePath: String, size: Long = 0) {
        val c = client ?: throw IllegalStateException("Not connected")
        c.put(localIn, remotePath)
    }

    fun pwd(): String {
        return client?.pwd() ?: "/"
    }

    fun resolvePath(base: String, child: String): String {
        if (child.startsWith("/")) return child
        return "$base/$child".replace("//", "/")
    }

    override fun close() {
        try { client?.close() } catch (_: IOException) {}
    }

    private fun RemoteResourceInfo.toEntry(parentPath: String): SftpEntry {
        return SftpEntry(
            name = this.name,
            path = resolvePath(parentPath, this.name),
            isDirectory = this.isDirectory,
            size = this.attributes.size,
            permissions = this.attributes.permissions?.toString() ?: "",
            modTime = this.attributes.mTime.toLong() * 1000
        )
    }

    data class SftpEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long = 0,
        val permissions: String = "",
        val modTime: Long = 0
    )
}
