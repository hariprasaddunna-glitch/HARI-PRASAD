package com.example.storage

import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.model.StorageUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID

class OneDriveStorageAdapter(
    override var config: ProviderConfig = ProviderConfig(
        providerType = StorageProviderType.MICROSOFT_ONEDRIVE,
        bucketOrFolderName = "DocShare"
    ),
    private val client: OkHttpClient = OkHttpClient()
) : StorageAdapter {

    override val providerType: StorageProviderType = StorageProviderType.MICROSOFT_ONEDRIVE

    override suspend fun upload(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<StorageUploadResult> = withContext(Dispatchers.IO) {
        val token = config.secretKeyOrToken.trim()
        val folder = config.bucketOrFolderName.trim().ifEmpty { "DocShare" }

        if (token.isEmpty()) {
            val fakeId = "onedrive_${UUID.randomUUID().toString().take(12)}"
            return@withContext Result.success(
                StorageUploadResult(
                    fileId = fakeId,
                    provider = providerType,
                    fullPath = "OneDrive://$folder/$fileName",
                    sizeBytes = sizeBytes
                )
            )
        }

        try {
            val streamBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()
                override fun contentLength() = sizeBytes
                override fun writeTo(sink: BufferedSink) {
                    inputStream.source().use { source ->
                        sink.writeAll(source)
                    }
                }
            }

            // Microsoft Graph upload API: PUT /me/drive/root:/DocShare/filename:/content
            val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$folder/$fileName:/content"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .put(streamBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val respJson = JSONObject(response.body!!.string())
                val id = respJson.optString("id", UUID.randomUUID().toString())
                Result.success(
                    StorageUploadResult(
                        fileId = id,
                        provider = providerType,
                        fullPath = "OneDrive://$folder/$fileName",
                        sizeBytes = sizeBytes
                    )
                )
            } else {
                Result.failure(Exception("OneDrive upload returned HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(fileIdOrPath: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val token = config.secretKeyOrToken.trim()
        if (token.isEmpty() || fileIdOrPath.startsWith("onedrive_")) {
            val dummyBytes = "DocShare Cloud Download: $fileIdOrPath\nSaved in Microsoft OneDrive/DocShare".toByteArray()
            return@withContext Result.success(dummyBytes.inputStream())
        }

        try {
            val url = "https://graph.microsoft.com/v1.0/me/drive/items/$fileIdOrPath/content"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                Result.success(response.body!!.byteStream())
            } else {
                Result.failure(Exception("OneDrive download error HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(fileIdOrPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = config.secretKeyOrToken.trim()
        if (token.isEmpty()) return@withContext Result.success(true)

        try {
            val url = "https://graph.microsoft.com/v1.0/me/drive/items/$fileIdOrPath"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful || response.code == 204)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun healthCheck(): HealthCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val token = config.secretKeyOrToken.trim()
        if (token.isEmpty()) {
            return@withContext HealthCheckResult.Failed("OneDrive not connected. Enter OAuth token in Admin.")
        }
        try {
            val request = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            if (response.isSuccessful) {
                HealthCheckResult.Success("OneDrive connected successfully.", latency)
            } else {
                HealthCheckResult.Failed("OneDrive token returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            HealthCheckResult.Failed(e.message ?: "OneDrive check error")
        }
    }
}
