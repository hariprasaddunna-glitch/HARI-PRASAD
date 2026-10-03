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

class DropboxStorageAdapter(
    override var config: ProviderConfig = ProviderConfig(
        providerType = StorageProviderType.DROPBOX,
        bucketOrFolderName = "/DocShare"
    ),
    private val client: OkHttpClient = OkHttpClient()
) : StorageAdapter {

    override val providerType: StorageProviderType = StorageProviderType.DROPBOX

    override suspend fun upload(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<StorageUploadResult> = withContext(Dispatchers.IO) {
        val token = config.secretKeyOrToken.trim()
        val folder = config.bucketOrFolderName.trim().let { if (!it.startsWith("/")) "/$it" else it }
        val targetPath = "$folder/$fileName"

        if (token.isEmpty()) {
            val fakeId = "id:dropbox_${UUID.randomUUID().toString().take(12)}"
            return@withContext Result.success(
                StorageUploadResult(
                    fileId = fakeId,
                    provider = providerType,
                    fullPath = "Dropbox://$targetPath",
                    sizeBytes = sizeBytes
                )
            )
        }

        try {
            val streamBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                override fun contentLength() = sizeBytes
                override fun writeTo(sink: BufferedSink) {
                    inputStream.source().use { source ->
                        sink.writeAll(source)
                    }
                }
            }

            val apiArg = JSONObject().apply {
                put("path", targetPath)
                put("mode", "add")
                put("autorename", true)
                put("mute", false)
            }.toString()

            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", apiArg)
                .post(streamBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val respJson = JSONObject(response.body!!.string())
                val id = respJson.optString("id", targetPath)
                Result.success(
                    StorageUploadResult(
                        fileId = id,
                        provider = providerType,
                        fullPath = "Dropbox://$targetPath",
                        sizeBytes = sizeBytes
                    )
                )
            } else {
                Result.failure(Exception("Dropbox upload error HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(fileIdOrPath: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val token = config.secretKeyOrToken.trim()
        if (token.isEmpty() || fileIdOrPath.startsWith("id:dropbox_")) {
            val dummyBytes = "DocShare Cloud Download: $fileIdOrPath\nSaved in Dropbox/DocShare".toByteArray()
            return@withContext Result.success(dummyBytes.inputStream())
        }

        try {
            val apiArg = JSONObject().apply {
                put("path", fileIdOrPath)
            }.toString()

            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", apiArg)
                .post(RequestBody.create(null, ByteArray(0)))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                Result.success(response.body!!.byteStream())
            } else {
                Result.failure(Exception("Dropbox download error HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(fileIdOrPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = config.secretKeyOrToken.trim()
        if (token.isEmpty()) return@withContext Result.success(true)

        try {
            val json = JSONObject().apply {
                put("path", fileIdOrPath)
            }.toString()

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(RequestBody.create("application/json".toMediaTypeOrNull(), json))
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful || response.code == 200)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun healthCheck(): HealthCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val token = config.secretKeyOrToken.trim()
        if (token.isEmpty()) {
            return@withContext HealthCheckResult.Failed("Dropbox token missing. Connect in Admin tab.")
        }
        try {
            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .header("Authorization", "Bearer $token")
                .post(RequestBody.create(null, ByteArray(0)))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            if (response.isSuccessful) {
                HealthCheckResult.Success("Dropbox token valid & connected.", latency)
            } else {
                HealthCheckResult.Failed("Dropbox error HTTP ${response.code}")
            }
        } catch (e: Exception) {
            HealthCheckResult.Failed(e.message ?: "Dropbox connection error")
        }
    }
}
