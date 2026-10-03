package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.HealthCheckResult
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.components.OwnerLoginDialog
import com.example.ui.screens.AdminStorageScreen
import com.example.ui.screens.AuditLogsScreen
import com.example.ui.screens.DownloadScreen
import com.example.ui.screens.StationQrScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.DocBluePrimary
import com.example.ui.theme.DocSuccess
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DocShareApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocShareApp(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProviderType.collectAsStateWithLifecycle()
    val healthMap by viewModel.healthResults.collectAsStateWithLifecycle()
    val ownerProfile by viewModel.ownerProfile.collectAsStateWithLifecycle()
    val showOwnerLoginDialog by viewModel.showOwnerLoginDialog.collectAsStateWithLifecycle()

    val currentHealth = healthMap[activeProvider]

    // BackHandler to navigate back to Station tab if on a sub-screen
    BackHandler(enabled = currentTab != AppTab.STATION_QR) {
        if (currentTab == AppTab.AUDIT) {
            viewModel.selectTab(AppTab.ADMIN)
        } else {
            viewModel.selectTab(AppTab.STATION_QR)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DocShare",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Active Provider Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (currentHealth is HealthCheckResult.Success) DocSuccess else Color(0xFFF59E0B)
                                        )
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = activeProvider.displayName.take(12),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openOwnerLoginDialog() },
                        modifier = Modifier.testTag("owner_login_top_btn")
                    ) {
                        if (ownerProfile?.isLoggedIn == true) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Owner Profile",
                                tint = if (ownerProfile?.googleDriveLinked == true) DocSuccess else WhatsAppGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Owner Login",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.selectTab(AppTab.ADMIN) },
                        modifier = Modifier.testTag("admin_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cloud Storage Admin",
                            tint = if (currentTab == AppTab.ADMIN) DocBluePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.STATION_QR,
                    onClick = { viewModel.selectTab(AppTab.STATION_QR) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.STATION_QR) Icons.Default.QrCode2 else Icons.Outlined.QrCode2,
                            contentDescription = "QR Station"
                        )
                    },
                    label = { Text("QR Station") },
                    modifier = Modifier.testTag("tab_station")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.UPLOAD,
                    onClick = { viewModel.selectTab(AppTab.UPLOAD) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.UPLOAD) Icons.Default.CloudUpload else Icons.Outlined.CloudUpload,
                            contentDescription = "Upload"
                        )
                    },
                    label = { Text("Upload") },
                    modifier = Modifier.testTag("tab_upload")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.DOWNLOAD,
                    onClick = { viewModel.selectTab(AppTab.DOWNLOAD) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.DOWNLOAD) Icons.Default.CloudDownload else Icons.Outlined.CloudDownload,
                            contentDescription = "Download"
                        )
                    },
                    label = { Text("Download") },
                    modifier = Modifier.testTag("tab_download")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.ADMIN || currentTab == AppTab.AUDIT,
                    onClick = { viewModel.selectTab(AppTab.ADMIN) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.ADMIN || currentTab == AppTab.AUDIT) Icons.Default.Storage else Icons.Outlined.Storage,
                            contentDescription = "Storage"
                        )
                    },
                    label = { Text("Cloud Setup") },
                    modifier = Modifier.testTag("tab_admin")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.STATION_QR -> StationQrScreen(viewModel = viewModel)
                AppTab.UPLOAD -> UploadScreen(viewModel = viewModel)
                AppTab.DOWNLOAD -> DownloadScreen(viewModel = viewModel)
                AppTab.ADMIN -> AdminStorageScreen(viewModel = viewModel)
                AppTab.AUDIT -> AuditLogsScreen(viewModel = viewModel)
            }
        }
    }

    if (showOwnerLoginDialog) {
        OwnerLoginDialog(
            ownerProfile = ownerProfile,
            onLoginGoogle = { name, email, token ->
                viewModel.loginOwnerWithGoogle(name, email, token)
            },
            onLoginWhatsApp = { name, phone, pin ->
                viewModel.loginOwnerWithWhatsApp(name, phone, pin)
            },
            onLogout = {
                viewModel.logoutOwner()
            },
            onDismiss = {
                viewModel.dismissOwnerLoginDialog()
            }
        )
    }
}
