package com.example.storage

import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.model.StorageUploadResult
import java.io.InputStream

interface StorageAdapter {
    val providerType: StorageProviderType
    val config: ProviderConfig

    /**
     * Streams file directly into storage provider without loading all bytes in memory.
     */
    suspend fun upload(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<StorageUploadResult>

    /**
     * Downloads file as a stream from the remote fileId / path.
     */
    suspend fun download(fileIdOrPath: String): Result<InputStream>

    /**
     * Deletes file from remote storage.
     */
    suspend fun delete(fileIdOrPath: String): Result<Boolean>

    /**
     * Tests connectivity, credentials, and folder reachability.
     */
    suspend fun healthCheck(): HealthCheckResult
}
