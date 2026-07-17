package com.companyname.aerossh.data

import androidx.room.*

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
    fun getAll(): List<SshKey>

    @Insert
    fun insert(key: SshKey): Long

    @Delete
    fun delete(key: SshKey)
}
