package com.companyname.aerossh.sshconfig

import com.companyname.aerossh.data.Server
import java.io.BufferedReader
import java.io.StringReader

object SshConfigParser {

    data class SshConfigEntry(
        val host: String,
        val hostname: String,
        val port: Int = 22,
        val user: String = "",
        val identityFile: String = "",
        val forwardAgent: Boolean = false,
        val forwardX11: Boolean = false
    )

    fun parse(config: String): List<SshConfigEntry> {
        val entries = mutableListOf<SshConfigEntry>()
        var current: MutableMap<String, String>? = null

        val reader = BufferedReader(StringReader(config))
        var line = reader.readLine()

        while (line != null) {
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> {
                    // Skip comments and empty lines
                }
                trimmed.startsWith("Host ", ignoreCase = true) -> {
                    // Save previous entry
                    current?.let { entries.add(mapToEntry(it)) }
                    current = mutableMapOf()
                    val host = trimmed.removePrefix("Host ").trim()
                    current?.set("Host", host)
                }
                current != null && trimmed.contains("=") -> {
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx > 0) {
                        val key = trimmed.substring(0, eqIdx).trim()
                        val value = trimmed.substring(eqIdx + 1).trim()
                        current?.set(key, value)
                    }
                }
                current != null && trimmed.contains(" ") -> {
                    // Key Value format (SSH config style)
                    val parts = trimmed.split("\\s+".toRegex(), limit = 2)
                    if (parts.size == 2) {
                        current?.set(parts[0], parts[1])
                    }
                }
            }
            line = reader.readLine()
        }

        // Don't forget last entry
        current?.let { entries.add(mapToEntry(it)) }

        return entries
    }

    private fun mapToEntry(map: MutableMap<String, String>): SshConfigEntry {
        val host = map["Host"] ?: ""
        val hostname = map["HostName"] ?: host
        val port = map["Port"]?.toIntOrNull() ?: 22
        val user = map["User"] ?: ""
        val identityFile = map["IdentityFile"] ?: ""
        val forwardAgent = map["ForwardAgent"]?.equals("yes", ignoreCase = true) == true
        val forwardX11 = map["ForwardX11"]?.equals("yes", ignoreCase = true) == true

        return SshConfigEntry(
            host = host,
            hostname = hostname,
            port = port,
            user = user,
            identityFile = identityFile,
            forwardAgent = forwardAgent,
            forwardX11 = forwardX11
        )
    }

    fun parseFromFile(content: String): List<Server> {
        return parse(content).filter { entry ->
            entry.host != "*" && entry.hostname.isNotEmpty()
        }.map { entry ->
            Server(
                name = entry.host,
                host = entry.hostname,
                port = entry.port,
                username = entry.user
            )
        }
    }
}
