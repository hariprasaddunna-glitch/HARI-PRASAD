package com.example.storage

import android.content.Context
import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.model.StorageUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class LocalStorageAdapter(
    private val context: Context,
    override var config: ProviderConfig = ProviderConfig(
        providerType = StorageProviderType.LOCAL_SECURE,
        isConnected = true,
        isActive = true,
        bucketOrFolderName = "DocShare/Vault"
    )
) : StorageAdapter {

    override val providerType: StorageProviderType = StorageProviderType.LOCAL_SECURE

    private val baseDir: File by lazy {
        val dir = File(context.filesDir, "storage/DocShare")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    override suspend fun upload(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<StorageUploadResult> = withContext(Dispatchers.IO) {
        try {
            val fileUuid = UUID.randomUUID().toString()
            val safeExtension = fileName.substringAfterLast('.', "")
            val storedName = if (safeExtension.isNotEmpty()) "$fileUuid.$safeExtension" else fileUuid
            val targetFile = File(baseDir, storedName)

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            Result.success(
                StorageUploadResult(
                    fileId = storedName,
                    provider = providerType,
                    fullPath = targetFile.absolutePath,
                    sizeBytes = targetFile.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(fileIdOrPath: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val file = File(baseDir, fileIdOrPath)
            if (!file.exists()) {
                val directFile = File(fileIdOrPath)
                if (directFile.exists()) {
                    return@withContext Result.success(FileInputStream(directFile))
                }
                return@withContext Result.failure(NoSuchFileException(file, reason = "File not found in local vault"))
            }
            Result.success(FileInputStream(file))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(fileIdOrPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(baseDir, fileIdOrPath)
            if (file.exists()) {
                Result.success(file.delete())
            } else {
                val directFile = File(fileIdOrPath)
                if (directFile.exists()) {
                    Result.success(directFile.delete())
                } else {
                    Result.success(true)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun healthCheck(): HealthCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            if (!baseDir.exists()) baseDir.mkdirs()
            val testFile = File(baseDir, ".health_check_${System.currentTimeMillis()}")
            testFile.writeText("ok")
            testFile.delete()
            val freeSpaceMb = baseDir.freeSpace / (1024 * 1024)
            HealthCheckResult.Success(
                "Local Vault Ready. ${freeSpaceMb} MB available.",
                System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            HealthCheckResult.Failed(e.message ?: "Failed writing to local vault")
        }
    }
}
