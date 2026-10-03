package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AuditLogDao
import com.example.data.dao.DocumentDao
import com.example.data.dao.OwnerProfileDao
import com.example.data.dao.ProviderConfigDao
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.DocumentEntity
import com.example.data.entity.OwnerProfileEntity
import com.example.data.entity.ProviderConfigEntity

@Database(
    entities = [
        DocumentEntity::class,
        ProviderConfigEntity::class,
        AuditLogEntity::class,
        OwnerProfileEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun ownerProfileDao(): OwnerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "docshare_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
