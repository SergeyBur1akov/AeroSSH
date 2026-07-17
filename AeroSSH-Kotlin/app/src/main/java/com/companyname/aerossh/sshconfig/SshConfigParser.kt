package com.companyname.aerossh.sshconfig

import com.companyname.aerossh.data.Server
import java.io.BufferedReader
import java.io.StringReader

object SshConfigParser {
    fun parse(config: String): List<Server> {
        val entries = mutableListOf<Server>(); var current: MutableMap<String, String>? = null
        val reader = BufferedReader(StringReader(config)); var line = reader.readLine()
        while (line != null) {
            val t = line.trim()
            when {
                t.isEmpty() || t.startsWith("#") -> {}
                t.startsWith("Host ", ignoreCase = true) -> { current?.let { if ((it["Host"] ?: "") != "*") entries.add(mapToServer(it)) }; current = mutableMapOf("Host" to t.removePrefix("Host ").trim()) }
                current != null && t.contains("=") -> { val eq = t.indexOf('='); if (eq > 0) current!![t.substring(0, eq).trim()] = t.substring(eq + 1).trim() }
            }
            line = reader.readLine()
        }
        current?.let { if ((it["Host"] ?: "") != "*") entries.add(mapToServer(it)) }
        return entries
    }

    fun parseFromFile(content: String): List<Server> = parse(content).filter { it.host.isNotEmpty() }

    private fun mapToServer(map: MutableMap<String, String>): Server {
        val host = map["Host"] ?: ""; val hostname = map["HostName"] ?: host
        return Server(name = host, host = hostname, port = map["Port"]?.toIntOrNull() ?: 22, username = map["User"] ?: "")
    }
}
