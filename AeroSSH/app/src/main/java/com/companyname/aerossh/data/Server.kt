package com.companyname.aerossh.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class Server(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String = "",
    val groupTag: String = "",
    val lastConnected: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
