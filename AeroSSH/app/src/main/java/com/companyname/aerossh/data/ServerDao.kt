package com.companyname.aerossh.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY lastConnected DESC, name ASC")
    fun getAll(): Flow<List<Server>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: Long): Server?

    @Query("SELECT * FROM servers WHERE name LIKE '%' || :query || '%' OR host LIKE '%' || :query || '%' ORDER BY lastConnected DESC")
    fun search(query: String): Flow<List<Server>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: Server): Long

    @Update
    suspend fun update(server: Server)

    @Delete
    suspend fun delete(server: Server)

    @Query("UPDATE servers SET lastConnected = :time WHERE id = :id")
    suspend fun touchLastConnected(id: Long, time: Long = System.currentTimeMillis())

    @Query("SELECT * FROM servers ORDER BY lastConnected DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 5): List<Server>
}
