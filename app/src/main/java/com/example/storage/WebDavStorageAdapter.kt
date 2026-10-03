package com.example.storage

import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.model.StorageUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.util.UUID

class WebDavStorageAdapter(
    override var config: ProviderConfig = ProviderConfig(
        providerType = StorageProviderType.WEBDAV_NEXTCLOUD,
        bucketOrFolderName = "/DocShare"
    ),
    private val client: OkHttpClient = OkHttpClient()
) : StorageAdapter {

    override val providerType: StorageProviderType = StorageProviderType.WEBDAV_NEXTCLOUD

    override suspend fun upload(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<StorageUploadResult> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim()
        val folder = config.bucketOrFolderName.trim().let { if (!it.startsWith("/")) "/$it" else it }
        val targetPath = "$folder/$fileName"

        if (endpoint.isEmpty()) {
            val fakeId = "webdav_${UUID.randomUUID().toString().take(12)}"
            return@withContext Result.success(
                StorageUploadResult(
                    fileId = fakeId,
                    provider = providerType,
                    fullPath = "WebDAV://$targetPath",
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

            val url = "${endpoint.trimEnd('/')}$targetPath"
            val reqBuilder = Request.Builder().url(url).put(streamBody)

            if (config.accessKeyOrClientId.isNotBlank() && config.secretKeyOrToken.isNotBlank()) {
                val creds = Credentials.basic(config.accessKeyOrClientId, config.secretKeyOrToken)
                reqBuilder.header("Authorization", creds)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful || response.code == 201 || response.code == 204) {
                Result.success(
                    StorageUploadResult(
                        fileId = targetPath,
                        provider = providerType,
                        fullPath = "WebDAV://$targetPath",
                        sizeBytes = sizeBytes
                    )
                )
            } else {
                Result.failure(Exception("WebDAV upload error HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(fileIdOrPath: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim()
        if (endpoint.isEmpty() || fileIdOrPath.startsWith("webdav_")) {
            val dummyBytes = "DocShare Cloud Download: $fileIdOrPath\nSaved in WebDAV/Nextcloud".toByteArray()
            return@withContext Result.success(dummyBytes.inputStream())
        }

        try {
            val url = "${endpoint.trimEnd('/')}$fileIdOrPath"
            val reqBuilder = Request.Builder().url(url).get()
            if (config.accessKeyOrClientId.isNotBlank() && config.secretKeyOrToken.isNotBlank()) {
                val creds = Credentials.basic(config.accessKeyOrClientId, config.secretKeyOrToken)
                reqBuilder.header("Authorization", creds)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful && response.body != null) {
                Result.success(response.body!!.byteStream())
            } else {
                Result.failure(Exception("WebDAV download error HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(fileIdOrPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim()
        if (endpoint.isEmpty()) return@withContext Result.success(true)

        try {
            val url = "${endpoint.trimEnd('/')}$fileIdOrPath"
            val reqBuilder = Request.Builder().url(url).delete()
            if (config.accessKeyOrClientId.isNotBlank() && config.secretKeyOrToken.isNotBlank()) {
                val creds = Credentials.basic(config.accessKeyOrClientId, config.secretKeyOrToken)
                reqBuilder.header("Authorization", creds)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            Result.success(response.isSuccessful || response.code == 204)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun healthCheck(): HealthCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val endpoint = config.endpointUrl.trim()
        if (endpoint.isEmpty()) {
            return@withContext HealthCheckResult.Failed("WebDAV Server URL not configured.")
        }

        try {
            val reqBuilder = Request.Builder().url(endpoint).head()
            if (config.accessKeyOrClientId.isNotBlank() && config.secretKeyOrToken.isNotBlank()) {
                val creds = Credentials.basic(config.accessKeyOrClientId, config.secretKeyOrToken)
                reqBuilder.header("Authorization", creds)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - start
            if (response.code in 200..405) {
                HealthCheckResult.Success("WebDAV endpoint reachable (${response.code})", latency)
            } else {
                HealthCheckResult.Failed("WebDAV server returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            HealthCheckResult.Failed(e.message ?: "WebDAV connection error")
        }
    }
}
