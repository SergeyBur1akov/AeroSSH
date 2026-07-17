package com.companyname.aerossh.data

import com.companyname.aerossh.security.LuksEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepository(private val dao: ServerDao) {
    fun getAll(): Flow<List<Server>> = dao.getAll().map { it.map { s -> s.decryptPassword() } }
    fun search(query: String): Flow<List<Server>> = dao.search(query).map { it.map { s -> s.decryptPassword() } }
    suspend fun getById(id: Long): Server? = dao.getById(id)?.decryptPassword()
    suspend fun insert(server: Server): Long = dao.insert(server.encryptPassword())
    suspend fun update(server: Server) = dao.update(server.encryptPassword())
    suspend fun delete(server: Server) = dao.delete(server)
    suspend fun touchLastConnected(id: Long) = dao.touchLastConnected(id)
    suspend fun getRecent(limit: Int = 5): List<Server> = dao.getRecent(limit).map { it.decryptPassword() }

    private fun Server.encryptPassword(): Server = if (password.isEmpty()) this else copy(password = LuksEncryption.encryptWithMaster(password))
    private fun Server.decryptPassword(): Server = if (password.isEmpty()) this else copy(password = LuksEncryption.decryptWithMaster(password))
}
