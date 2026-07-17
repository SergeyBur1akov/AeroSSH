package com.companyname.aerossh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.companyname.aerossh.security.SecureStorage

@Database(entities = [Server::class, SshKey::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun sshKeyDao(): SshKeyDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: run { SecureStorage.init(context); val pass = SecureStorage.getDatabasePassphrase(context)
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "aerossh.db").openHelperFactory(net.sqlcipher.database.SupportFactory(pass)).build().also { INSTANCE = it } }
        }
    }
}
