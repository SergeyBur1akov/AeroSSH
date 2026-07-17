package com.companyname.aerossh.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_keys")
data class SshKey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,         // "Ed25519", "RSA-2048", "RSA-4096", "ECDSA"
    val publicKey: String,
    val privateKeyEncrypted: String, // encrypted with Keystore
    val createdAt: Long = System.currentTimeMillis()
)
