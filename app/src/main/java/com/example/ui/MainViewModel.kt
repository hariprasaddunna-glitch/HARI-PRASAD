package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DocumentRepository
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.DocumentEntity
import com.example.data.entity.OwnerProfileEntity
import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.storage.StorageAdapterRegistry
import com.example.util.QrCodeGenerator
import com.example.util.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

enum class AppTab(val title: String) {
    STATION_QR("QR Station"),
    UPLOAD("Upload"),
    DOWNLOAD("Download"),
    ADMIN("Cloud Storage"),
    AUDIT("Audit Logs")
}

enum class RetentionOption(val label: String, val durationMillis: Long?) {
    PERMANENT("Never Expire", null),
    HOURS_24("24 Hours", 24 * 60 * 60 * 1000L),
    DAYS_7("7 Days", 7 * 24 * 60 * 60 * 1000L),
    DAYS_30("30 Days", 30L * 24 * 60 * 60 * 1000L)
}

data class UploadUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val uploaderName: String = "",
    val uploaderPhone: String = "",
    val isPinProtected: Boolean = false,
    val pin: String = "",
    val retentionOption: RetentionOption = RetentionOption.PERMANENT,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val errorMessage: String? = null,
    val successDocument: DocumentEntity? = null
)

data class MigrationState(
    val isMigrating: Boolean = false,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val currentFileName: String = "",
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val storageRegistry = StorageAdapterRegistry(application)
    val repository = DocumentRepository(application, database, storageRegistry)

    private val _currentTab = MutableStateFlow(AppTab.STATION_QR)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Station State
    private val _stationUrl = MutableStateFlow("https://ais-dev-dz5aq2dyx7ilrjivrk5ke4-192046698665.europe-west2.run.app")
    val stationUrl: StateFlow<String> = _stationUrl.asStateFlow()

    private val _stationName = MutableStateFlow("DocShare Express Terminal")
    val stationName: StateFlow<String> = _stationName.asStateFlow()

    private val _stationQrBitmap = MutableStateFlow<Bitmap?>(null)
    val stationQrBitmap: StateFlow<Bitmap?> = _stationQrBitmap.asStateFlow()

    // Active Provider
    val activeProviderType: StateFlow<StorageProviderType> = storageRegistry.activeProviderType

    // Upload Form State
    private val _uploadState = MutableStateFlow(UploadUiState())
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    // Download / Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterOnlyProtected = MutableStateFlow(false)
    val filterOnlyProtected: StateFlow<Boolean> = _filterOnlyProtected.asStateFlow()

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        _searchQuery,
        _filterOnlyProtected
    ) { docs, query, onlyProtected ->
        docs.filter { doc ->
            val matchesQuery = query.isBlank() ||
                    doc.uploaderPhone.contains(query, ignoreCase = true) ||
                    doc.uploaderName.contains(query, ignoreCase = true) ||
                    doc.fileName.contains(query, ignoreCase = true)
            val matchesFilter = !onlyProtected || doc.isProtected
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin & Providers State
    private val _providerConfigs = MutableStateFlow<List<ProviderConfig>>(storageRegistry.getAllConfigs())
    val providerConfigs: StateFlow<List<ProviderConfig>> = _providerConfigs.asStateFlow()

    private val _healthResults = MutableStateFlow<Map<StorageProviderType, HealthCheckResult?>>(emptyMap())
    val healthResults: StateFlow<Map<StorageProviderType, HealthCheckResult?>> = _healthResults.asStateFlow()

    private val _migrationState = MutableStateFlow(MigrationState())
    val migrationState: StateFlow<MigrationState> = _migrationState.asStateFlow()

    // Download Action State
    private val _downloadingDocumentId = MutableStateFlow<Long?>(null)
    val downloadingDocumentId: StateFlow<Long?> = _downloadingDocumentId.asStateFlow()

    private val _downloadedFile = MutableStateFlow<File?>(null)
    val downloadedFile: StateFlow<File?> = _downloadedFile.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private val _selectedDocForPin = MutableStateFlow<DocumentEntity?>(null)
    val selectedDocForPin: StateFlow<DocumentEntity?> = _selectedDocForPin.asStateFlow()

    private val _selectedDocDetail = MutableStateFlow<DocumentEntity?>(null)
    val selectedDocDetail: StateFlow<DocumentEntity?> = _selectedDocDetail.asStateFlow()

    // Owner Authentication State
    val ownerProfile: StateFlow<OwnerProfileEntity?> = repository.ownerProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showOwnerLoginDialog = MutableStateFlow(false)
    val showOwnerLoginDialog: StateFlow<Boolean> = _showOwnerLoginDialog.asStateFlow()

    fun openOwnerLoginDialog() {
        _showOwnerLoginDialog.value = true
    }

    fun dismissOwnerLoginDialog() {
        _showOwnerLoginDialog.value = false
    }

    fun loginOwnerWithGoogle(name: String, email: String, token: String) {
        viewModelScope.launch {
            repository.loginAsOwnerWithGoogle(name, email, token)
            _providerConfigs.value = storageRegistry.getAllConfigs()
            checkProviderHealth(StorageProviderType.GOOGLE_DRIVE)
            _showOwnerLoginDialog.value = false
        }
    }

    fun loginOwnerWithWhatsApp(name: String, phone: String, pin: String) {
        viewModelScope.launch {
            repository.loginAsOwnerWithWhatsApp(name, phone, pin)
            _showOwnerLoginDialog.value = false
        }
    }

    fun updateOwnerWhatsAppNumber(phone: String) {
        viewModelScope.launch {
            repository.updateOwnerWhatsAppNumber(phone)
        }
    }

    fun logoutOwner() {
        viewModelScope.launch {
            repository.logoutOwner()
        }
    }

    init {
        generateQr()
        checkAllProvidersHealth()
        // Auto purge expired files on startup
        viewModelScope.launch {
            repository.cleanupExpiredDocuments()
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setStationUrl(url: String) {
        _stationUrl.value = url.trim()
        generateQr()
    }

    fun setStationName(name: String) {
        _stationName.value = name
    }

    fun generateQr() {
        val bitmap = QrCodeGenerator.generateQrBitmap(_stationUrl.value, width = 600, height = 600)
        _stationQrBitmap.value = bitmap
    }

    // Upload Actions
    fun onFileSelected(uri: Uri?) {
        if (uri == null) return
        val context = getApplication<Application>()
        var fileName = "document"
        var fileSize = 0L
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "document"
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val validation = SecurityUtils.validateFile(fileName, fileSize)
        val error = if (validation is com.example.util.ValidationResult.Error) validation.message else null

        _uploadState.value = _uploadState.value.copy(
            selectedUri = uri,
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            errorMessage = error
        )
    }

    fun updateUploadName(name: String) {
        _uploadState.value = _uploadState.value.copy(uploaderName = name)
    }

    fun updateUploadPhone(phone: String) {
        _uploadState.value = _uploadState.value.copy(uploaderPhone = phone)
    }

    fun updatePinProtection(enabled: Boolean) {
        _uploadState.value = _uploadState.value.copy(isPinProtected = enabled)
    }

    fun updatePin(pin: String) {
        if (pin.length <= 6) {
            _uploadState.value = _uploadState.value.copy(pin = pin)
        }
    }

    fun updateRetention(option: RetentionOption) {
        _uploadState.value = _uploadState.value.copy(retentionOption = option)
    }

    fun clearUploadError() {
        _uploadState.value = _uploadState.value.copy(errorMessage = null)
    }

    fun dismissSuccessDocument() {
        _uploadState.value = _uploadState.value.copy(successDocument = null)
    }

    fun submitUpload() {
        val state = _uploadState.value
        val uri = state.selectedUri ?: return

        if (state.uploaderName.trim().isBlank()) {
            _uploadState.value = state.copy(errorMessage = "Please enter your name")
            return
        }
        if (state.uploaderPhone.trim().isBlank()) {
            _uploadState.value = state.copy(errorMessage = "Please enter your phone number")
            return
        }
        if (state.isPinProtected && state.pin.length < 4) {
            _uploadState.value = state.copy(errorMessage = "Protection PIN must be at least 4 digits")
            return
        }

        viewModelScope.launch {
            _uploadState.value = state.copy(isUploading = true, errorMessage = null)
            val context = getApplication<Application>()
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)

            if (inputStream == null) {
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    errorMessage = "Unable to read selected file"
                )
                return@launch
            }

            val result = repository.uploadDocument(
                name = state.uploaderName,
                phone = state.uploaderPhone,
                fileName = state.fileName,
                mimeType = state.mimeType,
                sizeBytes = state.fileSize,
                inputStream = inputStream,
                pin = if (state.isPinProtected) state.pin else null,
                expiryDurationMillis = state.retentionOption.durationMillis
            )

            if (result.isSuccess) {
                _uploadState.value = UploadUiState(
                    successDocument = result.getOrNull()
                )
            } else {
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Upload failed"
                )
            }
        }
    }

    // Download Actions
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterOnlyProtected(onlyProtected: Boolean) {
        _filterOnlyProtected.value = onlyProtected
    }

    fun startDownload(doc: DocumentEntity) {
        if (doc.isProtected) {
            _selectedDocForPin.value = doc
        } else {
            performDownload(doc, null)
        }
    }

    fun submitPinAndDownload(pin: String) {
        val doc = _selectedDocForPin.value ?: return
        _selectedDocForPin.value = null
        performDownload(doc, pin)
    }

    fun dismissPinDialog() {
        _selectedDocForPin.value = null
    }

    private fun performDownload(doc: DocumentEntity, pin: String?) {
        viewModelScope.launch {
            _downloadingDocumentId.value = doc.id
            _downloadError.value = null
            _downloadedFile.value = null

            val result = repository.downloadDocumentToCache(doc, pin)
            _downloadingDocumentId.value = null

            if (result.isSuccess) {
                _downloadedFile.value = result.getOrNull()
            } else {
                _downloadError.value = result.exceptionOrNull()?.message ?: "Download failed"
            }
        }
    }

    fun clearDownloadedFile() {
        _downloadedFile.value = null
    }

    fun clearDownloadError() {
        _downloadError.value = null
    }

    fun showDocumentDetail(doc: DocumentEntity?) {
        _selectedDocDetail.value = doc
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            if (_selectedDocDetail.value?.id == doc.id) {
                _selectedDocDetail.value = null
            }
        }
    }

    // Admin & Provider Actions
    fun setActiveProvider(type: StorageProviderType) {
        viewModelScope.launch {
            repository.setActiveProvider(type)
            _providerConfigs.value = storageRegistry.getAllConfigs()
        }
    }

    fun saveProviderConfig(config: ProviderConfig) {
        viewModelScope.launch {
            repository.saveProviderConfig(config)
            _providerConfigs.value = storageRegistry.getAllConfigs()
            checkProviderHealth(config.providerType)
        }
    }

    fun checkProviderHealth(type: StorageProviderType) {
        viewModelScope.launch {
            val result = repository.checkProviderHealth(type)
            val map = _healthResults.value.toMutableMap()
            map[type] = result
            _healthResults.value = map
        }
    }

    fun checkAllProvidersHealth() {
        StorageProviderType.values().forEach { type ->
            checkProviderHealth(type)
        }
    }

    fun startMigration(fromProvider: StorageProviderType, toProvider: StorageProviderType) {
        if (fromProvider == toProvider) return
        viewModelScope.launch {
            _migrationState.value = MigrationState(isMigrating = true, message = "Starting migration...")
            val result = repository.migrateDocuments(fromProvider, toProvider) { current, total, file ->
                _migrationState.value = MigrationState(
                    isMigrating = true,
                    currentCount = current,
                    totalCount = total,
                    currentFileName = file,
                    message = "Migrating $current of $total: $file"
                )
            }
            if (result.isSuccess) {
                _migrationState.value = MigrationState(
                    isMigrating = false,
                    message = "Successfully migrated ${result.getOrNull()} documents to ${toProvider.displayName}!"
                )
            } else {
                _migrationState.value = MigrationState(
                    isMigrating = false,
                    message = "Migration failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun dismissMigrationState() {
        _migrationState.value = MigrationState()
    }
}
