package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.DocumentEntity
import com.example.ui.MainViewModel
import com.example.ui.components.DocumentQrDialog
import com.example.ui.components.WhatsAppShareDialog
import com.example.ui.theme.DocBluePrimary
import com.example.ui.theme.DocError
import com.example.ui.theme.DocSuccess
import com.example.ui.theme.WhatsAppGreen
import com.example.util.SecurityUtils
import com.example.util.WhatsAppSharingUtils
import java.io.File

@Composable
fun DownloadScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterOnlyProtected by viewModel.filterOnlyProtected.collectAsStateWithLifecycle()
    val documents by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val downloadingDocId by viewModel.downloadingDocumentId.collectAsStateWithLifecycle()
    val downloadedFile by viewModel.downloadedFile.collectAsStateWithLifecycle()
    val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()
    val selectedDocForPin by viewModel.selectedDocForPin.collectAsStateWithLifecycle()
    val selectedDocDetail by viewModel.selectedDocDetail.collectAsStateWithLifecycle()
    val stationUrl by viewModel.stationUrl.collectAsStateWithLifecycle()

    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var documentForWhatsAppShare by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentForQrCode by remember { mutableStateOf<DocumentEntity?>(null) }

    // When file finishes downloading, prompt to open or share
    var showDownloadedDialog by remember { mutableStateOf(false) }
    LaunchedEffect(downloadedFile) {
        if (downloadedFile != null) {
            showDownloadedDialog = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Find & Download Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Search by phone number or name. Storage paths are always secure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by phone number, name, or filename...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_document_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !filterOnlyProtected,
                        onClick = { viewModel.setFilterOnlyProtected(false) },
                        label = { Text("All Documents (${documents.size})") }
                    )
                    FilterChip(
                        selected = filterOnlyProtected,
                        onClick = { viewModel.setFilterOnlyProtected(true) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("PIN-Protected") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Document List
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No documents uploaded yet" else "No matching documents found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (searchQuery.isBlank()) "Upload a file from the Upload tab or scan the QR code." else "Check phone number or name spelling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("documents_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentCard(
                        document = doc,
                        isDownloading = downloadingDocId == doc.id,
                        onDownload = { viewModel.startDownload(doc) },
                        onDetails = { viewModel.showDocumentDetail(doc) },
                        onDelete = { viewModel.deleteDocument(doc) },
                        onShareWhatsApp = {
                            documentForWhatsAppShare = doc
                        },
                        onShowQr = {
                            documentForQrCode = doc
                        }
                    )
                }
            }
        }
    }

    // PIN Input Dialog for Protected Documents
    selectedDocForPin?.let { doc ->
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissPinDialog()
                enteredPin = ""
                pinError = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = DocBluePrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Enter 4-Digit Security PIN", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "File: \"${doc.fileName}\" is protected by the uploader.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 6) {
                                enteredPin = it
                                pinError = false
                            }
                        },
                        label = { Text("Access PIN") },
                        isError = pinError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_pin_input")
                    )
                    if (pinError) {
                        Text(
                            text = "Incorrect PIN. Please re-enter.",
                            color = DocError,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (SecurityUtils.verifyPin(enteredPin, doc.pinHash)) {
                            viewModel.submitPinAndDownload(enteredPin)
                            enteredPin = ""
                            pinError = false
                        } else {
                            pinError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_pin_button")
                ) {
                    Text("Unlock & Stream")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissPinDialog()
                    enteredPin = ""
                    pinError = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Download Success & Open Dialog
    if (showDownloadedDialog && downloadedFile != null) {
        val file = downloadedFile!!
        AlertDialog(
            onDismissRequest = {
                showDownloadedDialog = false
                viewModel.clearDownloadedFile()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = DocSuccess,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = { Text("Download Ready") },
            text = {
                Column {
                    Text("File fetched securely from storage provider:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(file.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Size: ${SecurityUtils.formatFileSize(file.length())}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            WhatsAppSharingUtils.shareFileToWhatsApp(context, file, "📄 *DocShare*: ${file.name}")
                            showDownloadedDialog = false
                            viewModel.clearDownloadedFile()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("whatsapp_share_file_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send via WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openDownloadedFile(context, file)
                        showDownloadedDialog = false
                        viewModel.clearDownloadedFile()
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open File")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        shareDownloadedFile(context, file)
                        showDownloadedDialog = false
                        viewModel.clearDownloadedFile()
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }
        )
    }

    // Download Error Dialog
    downloadError?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDownloadError() },
            title = { Text("Download Error") },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = { viewModel.clearDownloadError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Document Details Dialog
    selectedDocDetail?.let { doc ->
        AlertDialog(
            onDismissRequest = { viewModel.showDocumentDetail(null) },
            title = { Text("Document Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(label = "Filename", value = doc.fileName)
                    DetailRow(label = "Uploader", value = doc.uploaderName)
                    DetailRow(label = "Phone", value = SecurityUtils.maskPhoneNumber(doc.uploaderPhone))
                    DetailRow(label = "Storage Provider", value = doc.storageProvider)
                    DetailRow(label = "Remote ID / Path", value = doc.remoteFullPath)
                    DetailRow(label = "Size", value = SecurityUtils.formatFileSize(doc.sizeBytes))
                    DetailRow(label = "Uploaded Date", value = SecurityUtils.formatDate(doc.uploadTimestamp))
                    DetailRow(label = "Download Count", value = "${doc.downloadCount} times")
                    if (doc.isProtected) {
                        DetailRow(label = "Security", value = "PIN Protected")
                    }
                    if (doc.expiryTimestamp != null) {
                        DetailRow(label = "Expires", value = SecurityUtils.formatDate(doc.expiryTimestamp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            documentForQrCode = doc
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DocBluePrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("qr_detail_btn")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Display Shareable QR Code", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            documentForWhatsAppShare = doc
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("whatsapp_share_detail_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share via WhatsApp Intent", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.showDocumentDetail(null) }) {
                    Text("Close")
                }
            }
        )
    }

    documentForWhatsAppShare?.let { doc ->
        WhatsAppShareDialog(
            document = doc,
            stationUrl = stationUrl,
            onDismiss = { documentForWhatsAppShare = null }
        )
    }

    documentForQrCode?.let { doc ->
        DocumentQrDialog(
            document = doc,
            stationUrl = stationUrl,
            onDismiss = { documentForQrCode = null }
        )
    }
}

@Composable
private fun DocumentCard(
    document: DocumentEntity,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onShowQr: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("document_card_${document.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = document.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (document.isProtected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "PIN Protected",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "PIN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${SecurityUtils.formatFileSize(document.sizeBytes)} • By ${document.uploaderName} (${SecurityUtils.maskPhoneNumber(document.uploaderPhone)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata footer & actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Storage Badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = document.storageProvider,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShowQr,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("qr_card_btn_${document.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Show QR Code",
                            tint = DocBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onShareWhatsApp,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("whatsapp_btn_${document.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share on WhatsApp",
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDetails, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DocError.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("download_btn_${document.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = DocBluePrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

private fun openDownloadedFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open file with..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareDownloadedFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = context.contentResolver.getType(uri) ?: "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show()
    }
}
