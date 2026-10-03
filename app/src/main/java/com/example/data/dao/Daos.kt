package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.DocumentEntity
import com.example.data.entity.OwnerProfileEntity
import com.example.data.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY uploadTimestamp DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE uploaderPhone LIKE '%' || :query || '%' OR uploaderName LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%' ORDER BY uploadTimestamp DESC")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE storageProvider = :provider")
    suspend fun getDocumentsByProvider(provider: String): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("UPDATE documents SET downloadCount = downloadCount + 1 WHERE id = :id")
    suspend fun incrementDownloadCount(id: Long)

    @Query("UPDATE documents SET status = :status WHERE id = :id")
    suspend fun updateDocumentStatus(id: Long, status: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("DELETE FROM documents WHERE expiryTimestamp IS NOT NULL AND expiryTimestamp < :currentTime")
    suspend fun deleteExpiredDocuments(currentTime: Long): Int

    @Query("SELECT COUNT(*) FROM documents")
    fun getDocumentCount(): Flow<Int>
}

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs")
    fun getAllConfigs(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE providerType = :type LIMIT 1")
    suspend fun getConfig(type: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ProviderConfigEntity)

    @Query("UPDATE provider_configs SET isActive = (providerType = :activeType)")
    suspend fun setActiveProvider(activeType: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

@Dao
interface OwnerProfileDao {
    @Query("SELECT * FROM owner_profile WHERE id = 1 LIMIT 1")
    fun getOwnerProfile(): Flow<OwnerProfileEntity?>

    @Query("SELECT * FROM owner_profile WHERE id = 1 LIMIT 1")
    suspend fun getOwnerProfileSync(): OwnerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOwnerProfile(profile: OwnerProfileEntity)

    @Query("DELETE FROM owner_profile")
    suspend fun clearOwnerProfile()
}
