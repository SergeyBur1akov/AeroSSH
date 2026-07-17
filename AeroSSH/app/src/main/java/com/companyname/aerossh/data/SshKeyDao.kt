package com.companyname.aerossh.data

import androidx.room.*

@Dao
interface SshKeyDao {

    @Query("SELECT * FROM ssh_keys ORDER BY name ASC")
    suspend fun getAll(): List<SshKey>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun getById(id: Long): SshKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: SshKey): Long

    @Update
    suspend fun update(key: SshKey)

    @Delete
    suspend fun delete(key: SshKey)
}
