package com.example.model

enum class StorageProviderType(val displayName: String, val defaultFolder: String) {
    LOCAL_SECURE("Local App Vault (Offline Ready)", "DocShare/Vault"),
    AWS_S3("AWS S3 / S3-Compatible", "docshare-bucket"),
    GOOGLE_DRIVE("Google Drive", "DocShare"),
    MICROSOFT_ONEDRIVE("Microsoft OneDrive", "DocShare"),
    DROPBOX("Dropbox", "/DocShare"),
    WEBDAV_NEXTCLOUD("WebDAV / Nextcloud", "/remote.php/dav/files/DocShare")
}

data class StorageUploadResult(
    val fileId: String,
    val provider: StorageProviderType,
    val fullPath: String,
    val sizeBytes: Long
)

sealed class HealthCheckResult {
    data class Success(val message: String, val latencyMs: Long) : HealthCheckResult()
    data class Failed(val error: String) : HealthCheckResult()
}

data class StorageStats(
    val totalFiles: Int,
    val totalBytes: Long,
    val providerBreakdown: Map<StorageProviderType, Long> = emptyMap()
)

data class ProviderConfig(
    val providerType: StorageProviderType,
    val isConnected: Boolean = false,
    val isActive: Boolean = false,
    val endpointUrl: String = "",
    val bucketOrFolderName: String = providerType.defaultFolder,
    val accessKeyOrClientId: String = "",
    val secretKeyOrToken: String = "",
    val region: String = "us-east-1",
    val customNotes: String = ""
)
