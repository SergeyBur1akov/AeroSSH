package com.companyname.aerossh.data

import com.companyname.aerossh.security.LuksEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepository(private val dao: ServerDao) {

    fun getAll(): Flow<List<Server>> {
        return dao.getAll().map { servers ->
            servers.map { it.decryptPassword() }
        }
    }

    fun search(query: String): Flow<List<Server>> {
        return dao.search(query).map { servers ->
            servers.map { it.decryptPassword() }
        }
    }

    suspend fun getById(id: Long): Server? {
        return dao.getById(id)?.decryptPassword()
    }

    suspend fun insert(server: Server): Long {
        return dao.insert(server.encryptPassword())
    }

    suspend fun update(server: Server) {
        dao.update(server.encryptPassword())
    }

    suspend fun delete(server: Server) {
        dao.delete(server)
    }

    suspend fun touchLastConnected(id: Long) {
        dao.touchLastConnected(id)
    }

    suspend fun getRecent(limit: Int = 5): List<Server> {
        return dao.getRecent(limit).map { it.decryptPassword() }
    }

    private fun Server.encryptPassword(): Server {
        if (password.isEmpty()) return this
        return try {
            copy(password = LuksEncryption.encryptWithMaster(password))
        } catch (_: Exception) {
            this
        }
    }

    private fun Server.decryptPassword(): Server {
        if (password.isEmpty()) return this
        return try {
            copy(password = LuksEncryption.decryptWithMaster(password))
        } catch (_: Exception) {
            this
        }
    }
}
