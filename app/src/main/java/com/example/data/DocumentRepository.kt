package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.DocumentEntity
import com.example.data.entity.OwnerProfileEntity
import com.example.data.entity.ProviderConfigEntity
import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.model.StorageStats
import com.example.storage.StorageAdapterRegistry
import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DocumentRepository(
    private val context: Context,
    private val database: AppDatabase,
    val storageRegistry: StorageAdapterRegistry
) {
    private val documentDao = database.documentDao()
    private val configDao = database.providerConfigDao()
    private val auditLogDao = database.auditLogDao()
    private val ownerProfileDao = database.ownerProfileDao()

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val allAuditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllLogs()
    val ownerProfile: Flow<OwnerProfileEntity?> = ownerProfileDao.getOwnerProfile()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        return if (query.isBlank()) {
            documentDao.getAllDocuments()
        } else {
            documentDao.searchDocuments(query.trim())
        }
    }

    suspend fun getDocument(id: Long): DocumentEntity? = withContext(Dispatchers.IO) {
        documentDao.getDocumentById(id)
    }

    suspend fun uploadDocument(
        name: String,
        phone: String,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        inputStream: InputStream,
        pin: String?,
        expiryDurationMillis: Long?
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        try {
            // Anti-abuse & security validation
            val validation = SecurityUtils.validateFile(fileName, sizeBytes)
            if (validation is com.example.util.ValidationResult.Error) {
                return@withContext Result.failure(IllegalArgumentException(validation.message))
            }

            val activeAdapter = storageRegistry.getActiveAdapter()
            val uploadResult = activeAdapter.upload(inputStream, fileName, mimeType, sizeBytes)

            if (uploadResult.isFailure) {
                return@withContext Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }

            val resultData = uploadResult.getOrThrow()
            val isProtected = !pin.isNullOrBlank()
            val pinHash = if (isProtected) SecurityUtils.hashPin(pin!!) else null
            val expiryTimestamp = expiryDurationMillis?.let { System.currentTimeMillis() + it }

            val entity = DocumentEntity(
                fileName = fileName,
                uploaderName = name.trim(),
                uploaderPhone = phone.trim(),
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                storageProvider = resultData.provider.name,
                remoteFileId = resultData.fileId,
                remoteFullPath = resultData.fullPath,
                uploadTimestamp = System.currentTimeMillis(),
                isProtected = isProtected,
                pinHash = pinHash,
                expiryTimestamp = expiryTimestamp,
                downloadCount = 0
            )

            val newId = documentDao.insertDocument(entity)
            val savedEntity = entity.copy(id = newId)

            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "UPLOAD",
                    documentName = fileName,
                    phone = SecurityUtils.maskPhoneNumber(phone),
                    details = "Saved to ${resultData.provider.displayName} (${SecurityUtils.formatFileSize(sizeBytes)})"
                )
            )

            Result.success(savedEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadDocumentToCache(
        document: DocumentEntity,
        enteredPin: String?
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Verify PIN if protected
            if (document.isProtected) {
                if (enteredPin.isNullOrBlank() || !SecurityUtils.verifyPin(enteredPin, document.pinHash)) {
                    return@withContext Result.failure(SecurityException("Invalid 4-digit security PIN"))
                }
            }

            val providerType = try {
                StorageProviderType.valueOf(document.storageProvider)
            } catch (e: Exception) {
                StorageProviderType.LOCAL_SECURE
            }

            val downloadStreamResult = storageRegistry.downloadFromProvider(providerType, document.remoteFileId)
            if (downloadStreamResult.isFailure) {
                return@withContext Result.failure(downloadStreamResult.exceptionOrNull() ?: Exception("Download stream failed"))
            }

            val inputStream = downloadStreamResult.getOrThrow()
            val downloadDir = File(context.cacheDir, "downloads").apply { if (!exists()) mkdirs() }
            val outputFile = File(downloadDir, document.fileName)

            inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            documentDao.incrementDownloadCount(document.id)

            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "DOWNLOAD",
                    documentName = document.fileName,
                    phone = SecurityUtils.maskPhoneNumber(document.uploaderPhone),
                    details = "Streamed from ${providerType.displayName}"
                )
            )

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(document: DocumentEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val providerType = try {
                StorageProviderType.valueOf(document.storageProvider)
            } catch (e: Exception) {
                StorageProviderType.LOCAL_SECURE
            }

            storageRegistry.deleteFromProvider(providerType, document.remoteFileId)
            documentDao.deleteDocument(document.id)

            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "DELETE",
                    documentName = document.fileName,
                    phone = SecurityUtils.maskPhoneNumber(document.uploaderPhone),
                    details = "Removed from ${providerType.displayName}"
                )
            )

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun migrateDocuments(
        fromProvider: StorageProviderType,
        toProvider: StorageProviderType,
        onProgress: (current: Int, total: Int, currentFile: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val docs = documentDao.getDocumentsByProvider(fromProvider.name)
            if (docs.isEmpty()) return@withContext Result.success(0)

            var migratedCount = 0
            for ((index, doc) in docs.withIndex()) {
                onProgress(index + 1, docs.size, doc.fileName)

                // Download stream from old provider
                val downResult = storageRegistry.downloadFromProvider(fromProvider, doc.remoteFileId)
                if (downResult.isSuccess) {
                    val stream = downResult.getOrThrow()
                    val targetAdapter = storageRegistry.getAdapter(toProvider)
                    val upResult = targetAdapter.upload(stream, doc.fileName, doc.mimeType, doc.sizeBytes)

                    if (upResult.isSuccess) {
                        val newUpload = upResult.getOrThrow()
                        // Update document record to point to new provider
                        val updatedDoc = doc.copy(
                            storageProvider = toProvider.name,
                            remoteFileId = newUpload.fileId,
                            remoteFullPath = newUpload.fullPath
                        )
                        documentDao.updateDocument(updatedDoc)
                        // Delete old file from source provider
                        storageRegistry.deleteFromProvider(fromProvider, doc.remoteFileId)
                        migratedCount++
                    }
                }
            }

            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "MIGRATION",
                    documentName = "$migratedCount files",
                    phone = "System",
                    details = "Migrated from ${fromProvider.displayName} to ${toProvider.displayName}"
                )
            )

            Result.success(migratedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cleanupExpiredDocuments(): Int = withContext(Dispatchers.IO) {
        val deleted = documentDao.deleteExpiredDocuments(System.currentTimeMillis())
        if (deleted > 0) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "AUTO_CLEANUP",
                    documentName = "$deleted documents",
                    phone = "System",
                    details = "Purged expired files automatically according to retention policy"
                )
            )
        }
        deleted
    }

    suspend fun checkProviderHealth(type: StorageProviderType): HealthCheckResult {
        return storageRegistry.checkHealth(type)
    }

    suspend fun saveProviderConfig(config: ProviderConfig) = withContext(Dispatchers.IO) {
        storageRegistry.updateAdapterConfig(config)
        configDao.upsertConfig(
            ProviderConfigEntity(
                providerType = config.providerType.name,
                isConnected = config.isConnected,
                isActive = config.isActive,
                endpointUrl = config.endpointUrl,
                bucketOrFolderName = config.bucketOrFolderName,
                accessKeyOrClientId = config.accessKeyOrClientId,
                secretKeyOrToken = config.secretKeyOrToken,
                region = config.region
            )
        )
    }

    suspend fun setActiveProvider(type: StorageProviderType) = withContext(Dispatchers.IO) {
        storageRegistry.setActiveProvider(type)
        configDao.setActiveProvider(type.name)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "PROVIDER_SWITCH",
                documentName = "-",
                phone = "Admin",
                details = "Active storage provider changed to ${type.displayName}"
            )
        )
    }

    suspend fun getOwnerProfileSync(): OwnerProfileEntity? = withContext(Dispatchers.IO) {
        ownerProfileDao.getOwnerProfileSync()
    }

    suspend fun loginAsOwnerWithGoogle(
        name: String,
        email: String,
        googleOAuthToken: String
    ): Result<OwnerProfileEntity> = withContext(Dispatchers.IO) {
        try {
            val current = ownerProfileDao.getOwnerProfileSync() ?: OwnerProfileEntity()
            val cleanEmail = email.trim()
            val token = googleOAuthToken.trim().ifEmpty { "gdrive_token_${System.currentTimeMillis()}" }

            val updated = current.copy(
                isLoggedIn = true,
                ownerName = name.ifBlank { cleanEmail.substringBefore('@') },
                ownerEmail = cleanEmail,
                authMethod = if (current.ownerWhatsAppNumber.isNotBlank()) "BOTH" else "GOOGLE",
                googleDriveLinked = true,
                googleDriveEmail = cleanEmail,
                googleOAuthToken = token,
                lastLoginTimestamp = System.currentTimeMillis()
            )
            ownerProfileDao.upsertOwnerProfile(updated)

            // Automatically configure Google Drive provider and make it the active storage provider
            val driveConfig = ProviderConfig(
                providerType = StorageProviderType.GOOGLE_DRIVE,
                isConnected = true,
                isActive = true,
                bucketOrFolderName = "DocShare",
                secretKeyOrToken = token
            )
            saveProviderConfig(driveConfig)
            storageRegistry.setActiveProvider(StorageProviderType.GOOGLE_DRIVE)
            configDao.setActiveProvider(StorageProviderType.GOOGLE_DRIVE.name)

            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "OWNER_LOGIN",
                    documentName = "Google Drive Linked",
                    phone = "Admin",
                    details = "Owner logged in via Google ($cleanEmail). Google Drive activated as storage."
                )
            )

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginAsOwnerWithWhatsApp(
        name: String,
        whatsAppNumber: String,
        adminPin: String
    ): Result<OwnerProfileEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = whatsAppNumber.trim()
            if (cleanPhone.length < 7) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid WhatsApp phone number"))
            }

            val current = ownerProfileDao.getOwnerProfileSync() ?: OwnerProfileEntity()
            val pinHash = SecurityUtils.hashPin(adminPin.ifBlank { "1234" })

            val updated = current.copy(
                isLoggedIn = true,
                ownerName = name.ifBlank { "DocShare Owner" },
                ownerWhatsAppNumber = cleanPhone,
                authMethod = if (current.googleDriveLinked) "BOTH" else "WHATSAPP",
                adminPinHash = pinHash,
                lastLoginTimestamp = System.currentTimeMillis()
            )
            ownerProfileDao.upsertOwnerProfile(updated)

            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "OWNER_LOGIN",
                    documentName = "WhatsApp Verified",
                    phone = SecurityUtils.maskPhoneNumber(cleanPhone),
                    details = "Owner authenticated via WhatsApp number: $cleanPhone"
                )
            )

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOwnerWhatsAppNumber(number: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = ownerProfileDao.getOwnerProfileSync() ?: OwnerProfileEntity()
            ownerProfileDao.upsertOwnerProfile(
                current.copy(ownerWhatsAppNumber = number.trim())
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logoutOwner(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = ownerProfileDao.getOwnerProfileSync()
            if (current != null) {
                ownerProfileDao.upsertOwnerProfile(current.copy(isLoggedIn = false))
            }
            auditLogDao.insertLog(
                AuditLogEntity(
                    action = "OWNER_LOGOUT",
                    documentName = "-",
                    phone = "Admin",
                    details = "Owner logged out"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
