package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.HealthCheckResult
import com.example.model.ProviderConfig
import com.example.model.StorageProviderType
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.theme.DocBluePrimary
import com.example.ui.theme.DocError
import com.example.ui.theme.DocIndigo
import com.example.ui.theme.DocSuccess
import com.example.ui.theme.DocTeal
import com.example.ui.theme.WhatsAppGreen
import com.example.util.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStorageScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeProvider by viewModel.activeProviderType.collectAsStateWithLifecycle()
    val providerConfigs by viewModel.providerConfigs.collectAsStateWithLifecycle()
    val healthMap by viewModel.healthResults.collectAsStateWithLifecycle()
    val migrationState by viewModel.migrationState.collectAsStateWithLifecycle()
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val ownerProfile by viewModel.ownerProfile.collectAsStateWithLifecycle()

    var editingConfig by remember { mutableStateOf<ProviderConfig?>(null) }
    var showMigrationDialog by remember { mutableStateOf(false) }

    val totalBytes = documents.sumOf { it.sizeBytes }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Owner Account Banner / Login Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_auth_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (ownerProfile?.isLoggedIn == true)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (ownerProfile?.isLoggedIn == true) DocSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (ownerProfile?.isLoggedIn == true) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = if (ownerProfile?.isLoggedIn == true) DocSuccess else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (ownerProfile?.isLoggedIn == true) "Owner: ${ownerProfile?.ownerName}" else "Owner Authentication",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (ownerProfile?.isLoggedIn == true)
                                    "Google Drive & WhatsApp Authenticated"
                                else "Sign in with Google Drive & WhatsApp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.openOwnerLoginDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ownerProfile?.isLoggedIn == true) MaterialTheme.colorScheme.surface else DocBluePrimary
                        ),
                        modifier = Modifier.testTag("owner_login_btn")
                    ) {
                        Text(
                            text = if (ownerProfile?.isLoggedIn == true) "Manage" else "Login",
                            color = if (ownerProfile?.isLoggedIn == true) MaterialTheme.colorScheme.onSurface else Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (ownerProfile?.isLoggedIn == true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = DocBluePrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (ownerProfile?.googleDriveLinked == true) "Drive: ${ownerProfile?.googleDriveEmail}" else "Drive: Not linked",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (!ownerProfile?.ownerWhatsAppNumber.isNullOrBlank()) ownerProfile?.ownerWhatsAppNumber ?: "" else "No WhatsApp",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Admin Overview Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Cloud Storage Admin",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Connect any drive. One active at a time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.checkAllProvidersHealth() },
                        modifier = Modifier.testTag("refresh_health_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Health",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Stored Files",
                        value = "${documents.size}",
                        icon = Icons.Default.Dataset
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Storage Used",
                        value = SecurityUtils.formatFileSize(totalBytes),
                        icon = Icons.Default.Storage
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Active Cloud",
                        value = activeProvider.name.take(6),
                        icon = Icons.Default.CloudDone
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Providers Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Supported Storage Providers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${providerConfigs.size} Adapters",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        providerConfigs.forEach { config ->
            val isActive = config.providerType == activeProvider
            val health = healthMap[config.providerType]

            StorageProviderCard(
                config = config,
                isActive = isActive,
                health = health,
                onConfigure = { editingConfig = config },
                onSetActive = {
                    viewModel.setActiveProvider(config.providerType)
                    Toast.makeText(context, "${config.providerType.displayName} is now active", Toast.LENGTH_SHORT).show()
                },
                onTestHealth = {
                    viewModel.checkProviderHealth(config.providerType)
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cross-Provider Migration Tool Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DocIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = DocIndigo)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Drive Migration Tool",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Migrate older files between storage providers without downtime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (migrationState.isMigrating) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = migrationState.message ?: "Migrating...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    if (migrationState.message != null) {
                        Text(
                            text = migrationState.message ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = DocSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = { showMigrationDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_migration_dialog_btn")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Cross-Provider Migration")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Policy & Audit Link Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = DocBluePrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DocShare Platform Architecture Policies",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                PolicyItem("Streaming Transfer: Files never load entire 100 MB into memory.")
                PolicyItem("Link Obfuscation: Direct cloud links are never exposed to clients.")
                PolicyItem("Provider Tracking: Old files always download from their original drive.")
                PolicyItem("Encrypted Storage: Credentials and PIN hashes are kept secure at rest.")

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.selectTab(AppTab.AUDIT) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Security Audit Logs", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Provider Configuration Dialog
    editingConfig?.let { cfg ->
        ProviderConfigDialog(
            config = cfg,
            onDismiss = { editingConfig = null },
            onSave = { updated ->
                viewModel.saveProviderConfig(updated)
                editingConfig = null
                Toast.makeText(context, "Saved ${updated.providerType.displayName} settings", Toast.LENGTH_SHORT).show()
            },
            onTestHealth = {
                viewModel.checkProviderHealth(cfg.providerType)
            }
        )
    }

    // Migration Selection Dialog
    if (showMigrationDialog) {
        MigrationDialog(
            onDismiss = { showMigrationDialog = false },
            onConfirm = { from, to ->
                showMigrationDialog = false
                viewModel.startMigration(from, to)
            }
        )
    }
}

@Composable
private fun StorageProviderCard(
    config: ProviderConfig,
    isActive: Boolean,
    health: HealthCheckResult?,
    onConfigure: () -> Unit,
    onSetActive: () -> Unit,
    onTestHealth: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${config.providerType.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) DocBluePrimary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (config.providerType) {
                                StorageProviderType.LOCAL_SECURE -> Icons.Default.Storage
                                StorageProviderType.AWS_S3 -> Icons.Default.Cloud
                                StorageProviderType.GOOGLE_DRIVE -> Icons.Default.Folder
                                StorageProviderType.MICROSOFT_ONEDRIVE -> Icons.Default.Dns
                                StorageProviderType.DROPBOX -> Icons.Default.CloudDone
                                StorageProviderType.WEBDAV_NEXTCLOUD -> Icons.Default.Link
                            },
                            contentDescription = null,
                            tint = if (isActive) DocBluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = config.providerType.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target: /${config.bucketOrFolderName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isActive) {
                    Surface(
                        color = DocBluePrimary,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Health Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (health) {
                                    is HealthCheckResult.Success -> DocSuccess
                                    is HealthCheckResult.Failed -> DocError
                                    null -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (health) {
                            is HealthCheckResult.Success -> "Online (${health.latencyMs}ms)"
                            is HealthCheckResult.Failed -> "Not configured / ${health.error.take(28)}..."
                            null -> "Testing health..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (health) {
                            is HealthCheckResult.Success -> DocSuccess
                            is HealthCheckResult.Failed -> DocError
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onConfigure,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Setup", style = MaterialTheme.typography.labelSmall)
                    }

                    if (!isActive) {
                        Button(
                            onClick = onSetActive,
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DocBluePrimary)
                        ) {
                            Text("Make Active", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(icon, contentDescription = null, tint = DocBluePrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PolicyItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text("• ", color = DocBluePrimary, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProviderConfigDialog(
    config: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onTestHealth: () -> Unit
) {
    var endpoint by remember { mutableStateOf(config.endpointUrl) }
    var folder by remember { mutableStateOf(config.bucketOrFolderName) }
    var keyOrUser by remember { mutableStateOf(config.accessKeyOrClientId) }
    var secretOrToken by remember { mutableStateOf(config.secretKeyOrToken) }
    var region by remember { mutableStateOf(config.region) }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Setup ${config.providerType.displayName}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Configure credentials & root directory for this storage provider:",
                    style = MaterialTheme.typography.bodySmall
                )

                when (config.providerType) {
                    StorageProviderType.LOCAL_SECURE -> {
                        Text(
                            "Local App Vault stores uploaded documents inside the isolated app sandbox on this device. Fully functional offline, no API keys needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("Local Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    StorageProviderType.AWS_S3 -> {
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("Endpoint URL (e.g. AWS S3, MinIO, Wasabi)") },
                            placeholder = { Text("https://s3.amazonaws.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("S3 Bucket Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = { Text("Region") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = keyOrUser,
                            onValueChange = { keyOrUser = it },
                            label = { Text("Access Key ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = secretOrToken,
                            onValueChange = { secretOrToken = it },
                            label = { Text("Secret Access Key / Bearer") },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    StorageProviderType.GOOGLE_DRIVE -> {
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("Google Drive Folder (default 'DocShare')") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = secretOrToken,
                            onValueChange = { secretOrToken = it },
                            label = { Text("OAuth 2.0 Access Token / Refresh Token") },
                            placeholder = { Text("ya29.a0AfH6SM...") },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                // Simulate OAuth connection flow
                                secretOrToken = "gdrive_oauth_token_${System.currentTimeMillis()}"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Drive (OAuth)")
                        }
                    }
                    StorageProviderType.MICROSOFT_ONEDRIVE -> {
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("OneDrive Folder (default 'DocShare')") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = secretOrToken,
                            onValueChange = { secretOrToken = it },
                            label = { Text("Microsoft Graph Bearer Token") },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                secretOrToken = "onedrive_graph_token_${System.currentTimeMillis()}"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect OneDrive (OAuth)")
                        }
                    }
                    StorageProviderType.DROPBOX -> {
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("Dropbox Folder Path") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = secretOrToken,
                            onValueChange = { secretOrToken = it },
                            label = { Text("Dropbox API v2 Access Token") },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                secretOrToken = "dropbox_sl_${System.currentTimeMillis()}"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Dropbox (OAuth)")
                        }
                    }
                    StorageProviderType.WEBDAV_NEXTCLOUD -> {
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("WebDAV / Nextcloud Server URL") },
                            placeholder = { Text("https://cloud.myserver.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("Folder Path (e.g. /DocShare)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = keyOrUser,
                            onValueChange = { keyOrUser = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = secretOrToken,
                            onValueChange = { secretOrToken = it },
                            label = { Text("Password or App Password") },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = config.copy(
                        endpointUrl = endpoint.trim(),
                        bucketOrFolderName = folder.trim(),
                        accessKeyOrClientId = keyOrUser.trim(),
                        secretKeyOrToken = secretOrToken.trim(),
                        region = region.trim(),
                        isConnected = secretOrToken.isNotBlank() || endpoint.isNotBlank() || config.providerType == StorageProviderType.LOCAL_SECURE
                    )
                    onSave(updated)
                }
            ) {
                Text("Save Configuration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MigrationDialog(
    onDismiss: () -> Unit,
    onConfirm: (from: StorageProviderType, to: StorageProviderType) -> Unit
) {
    var fromProvider by remember { mutableStateOf(StorageProviderType.LOCAL_SECURE) }
    var toProvider by remember { mutableStateOf(StorageProviderType.GOOGLE_DRIVE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Migrate Files Between Cloud Drives") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Transfer all documents currently stored on one provider to another provider. Database references are automatically updated.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text("From Source Provider:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                StorageProviderType.values().forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = fromProvider == type,
                            onClick = { fromProvider = type }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(type.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("To Target Provider:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                StorageProviderType.values().forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = toProvider == type,
                            onClick = { toProvider = type }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(type.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fromProvider, toProvider) },
                enabled = fromProvider != toProvider
            ) {
                Text("Start Migration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
