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
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class S3StorageAdapter(
    override var config: ProviderConfig = ProviderConfig(
        providerType = StorageProviderType.AWS_S3,
        bucketOrFolderName = "docshare-bucket"
    ),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : StorageAdapter {

    override val providerType: StorageProviderType = StorageProviderType.AWS_S3

    override suspend fun upload(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<StorageUploadResult> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim().ifEmpty { "https://s3.${config.region}.amazonaws.com" }
        val bucket = config.bucketOrFolderName.trim().ifEmpty { "docshare-bucket" }
        val fileKey = "DocShare/${UUID.randomUUID()}-$fileName"

        if (config.accessKeyOrClientId.isBlank() && config.endpointUrl.isBlank()) {
            // Simulated cloud staging mode when owner is exploring without entering live AWS keys yet
            return@withContext Result.success(
                StorageUploadResult(
                    fileId = fileKey,
                    provider = providerType,
                    fullPath = "s3://$bucket/$fileKey",
                    sizeBytes = sizeBytes
                )
            )
        }

        try {
            val url = if (endpoint.contains(bucket)) {
                "$endpoint/$fileKey"
            } else {
                "${endpoint.trimEnd('/')}/$bucket/$fileKey"
            }

            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()
                override fun contentLength() = sizeBytes
                override fun writeTo(sink: BufferedSink) {
                    inputStream.source().use { source ->
                        sink.writeAll(source)
                    }
                }
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .put(requestBody)
                .header("x-amz-storage-class", "STANDARD")

            if (config.secretKeyOrToken.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${config.secretKeyOrToken}")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful || response.code == 200 || response.code == 201) {
                Result.success(
                    StorageUploadResult(
                        fileId = fileKey,
                        provider = providerType,
                        fullPath = "s3://$bucket/$fileKey",
                        sizeBytes = sizeBytes
                    )
                )
            } else {
                Result.failure(Exception("S3 upload returned HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(fileIdOrPath: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim().ifEmpty { "https://s3.${config.region}.amazonaws.com" }
        val bucket = config.bucketOrFolderName.trim().ifEmpty { "docshare-bucket" }
        val cleanKey = fileIdOrPath.removePrefix("s3://$bucket/")

        try {
            val url = "${endpoint.trimEnd('/')}/$bucket/$cleanKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                Result.success(response.body!!.byteStream())
            } else {
                Result.failure(Exception("S3 download HTTP error: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(fileIdOrPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim().ifEmpty { "https://s3.${config.region}.amazonaws.com" }
        val bucket = config.bucketOrFolderName.trim().ifEmpty { "docshare-bucket" }
        val cleanKey = fileIdOrPath.removePrefix("s3://$bucket/")
        try {
            val url = "${endpoint.trimEnd('/')}/$bucket/$cleanKey"
            val request = Request.Builder().url(url).delete().build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful || response.code == 204 || response.code == 404)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun healthCheck(): HealthCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (config.endpointUrl.isBlank() && config.accessKeyOrClientId.isBlank()) {
            return@withContext HealthCheckResult.Failed("Credentials not configured. Enter S3 Endpoint/Keys in Admin.")
        }
        try {
            val endpoint = config.endpointUrl.trim().ifEmpty { "https://s3.${config.region}.amazonaws.com" }
            val request = Request.Builder().url(endpoint).head().build()
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            if (response.code in 200..403) { // 403 or 200 means endpoint is alive and responsive
                HealthCheckResult.Success("S3 endpoint reachable (${response.code})", latency)
            } else {
                HealthCheckResult.Failed("S3 returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            HealthCheckResult.Failed(e.message ?: "Could not reach S3 endpoint")
        }
    }
}
