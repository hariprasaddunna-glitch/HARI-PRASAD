package com.example.storage

import android.content.Context
import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.model.StorageUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream

class StorageAdapterRegistry(private val context: Context) {

    private val adapters = mutableMapOf<StorageProviderType, StorageAdapter>()

    private val _activeProviderType = MutableStateFlow(StorageProviderType.LOCAL_SECURE)
    val activeProviderType: StateFlow<StorageProviderType> = _activeProviderType.asStateFlow()

    init {
        // Register all available adapters
        registerAdapter(LocalStorageAdapter(context))
        registerAdapter(S3StorageAdapter())
        registerAdapter(GoogleDriveStorageAdapter())
        registerAdapter(OneDriveStorageAdapter())
        registerAdapter(DropboxStorageAdapter())
        registerAdapter(WebDavStorageAdapter())
    }

    fun registerAdapter(adapter: StorageAdapter) {
        adapters[adapter.providerType] = adapter
    }

    fun getAdapter(providerType: StorageProviderType): StorageAdapter {
        return adapters[providerType] ?: adapters[StorageProviderType.LOCAL_SECURE]!!
    }

    fun getActiveAdapter(): StorageAdapter {
        return getAdapter(_activeProviderType.value)
    }

    fun setActiveProvider(type: StorageProviderType) {
        _activeProviderType.value = type
        // Update flags
        adapters.values.forEach { adapter ->
            val updated = adapter.config.copy(isActive = adapter.providerType == type)
            updateAdapterConfig(updated)
        }
    }

    fun updateAdapterConfig(config: ProviderConfig) {
        val adapter = adapters[config.providerType]
        if (adapter != null) {
            when (adapter) {
                is LocalStorageAdapter -> adapter.config = config
                is S3StorageAdapter -> adapter.config = config
                is GoogleDriveStorageAdapter -> adapter.config = config
                is OneDriveStorageAdapter -> adapter.config = config
                is DropboxStorageAdapter -> adapter.config = config
                is WebDavStorageAdapter -> adapter.config = config
            }
        }
    }

    fun getAllConfigs(): List<ProviderConfig> {
        return StorageProviderType.values().map { type ->
            adapters[type]?.config ?: ProviderConfig(providerType = type)
        }
    }

    suspend fun checkHealth(type: StorageProviderType): HealthCheckResult {
        return getAdapter(type).healthCheck()
    }

    /**
     * Upload using the currently active provider.
     */
    suspend fun uploadToActiveProvider(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<StorageUploadResult> {
        return getActiveAdapter().upload(inputStream, fileName, mimeType, sizeBytes)
    }

    /**
     * Download using the provider that owns this file record.
     * Guarantees older files still download even after provider switch.
     */
    suspend fun downloadFromProvider(
        providerType: StorageProviderType,
        remoteFileId: String
    ): Result<InputStream> {
        return getAdapter(providerType).download(remoteFileId)
    }

    /**
     * Delete from the specified provider.
     */
    suspend fun deleteFromProvider(
        providerType: StorageProviderType,
        remoteFileId: String
    ): Result<Boolean> {
        return getAdapter(providerType).delete(remoteFileId)
    }
}
