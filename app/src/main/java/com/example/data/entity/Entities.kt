package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val uploaderName: String,
    val uploaderPhone: String,
    val sizeBytes: Long,
    val mimeType: String,
    val storageProvider: String, // Matches StorageProviderType name
    val remoteFileId: String,
    val remoteFullPath: String,
    val uploadTimestamp: Long = System.currentTimeMillis(),
    val isProtected: Boolean = false,
    val pinHash: String? = null,
    val expiryTimestamp: Long? = null,
    val downloadCount: Int = 0,
    val servicePurpose: String = "PRINTING", // "PRINTING", "EDITING", "PRINT_AND_EDIT"
    val printCopies: Int = 1,
    val printColor: String = "B&W", // "B&W", "COLOR"
    val printNotes: String = "",
    val status: String = "SUBMITTED" // "SUBMITTED", "IN_PROGRESS", "READY_FOR_PICKUP", "COMPLETED"
)

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey
    val providerType: String,
    val isConnected: Boolean = false,
    val isActive: Boolean = false,
    val endpointUrl: String = "",
    val bucketOrFolderName: String = "PrintShop_Customer_Uploads",
    val accessKeyOrClientId: String = "",
    val secretKeyOrToken: String = "",
    val region: String = "us-east-1"
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val action: String,
    val documentName: String,
    val phone: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "owner_profile")
data class OwnerProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val isLoggedIn: Boolean = false,
    val ownerName: String = "Shop Owner",
    val shopName: String = "Quick Print & Xerox Station",
    val ownerEmail: String = "hariprasaddunna@gmail.com",
    val ownerWhatsAppNumber: String = "",
    val authMethod: String = "GOOGLE", // "GOOGLE", "WHATSAPP", "BOTH"
    val googleDriveLinked: Boolean = false,
    val googleDriveEmail: String = "hariprasaddunna@gmail.com",
    val googleDriveFolderName: String = "PrintShop_Customer_Uploads",
    val googleOAuthToken: String = "",
    val adminPinHash: String? = null,
    val lastLoginTimestamp: Long = System.currentTimeMillis()
)
