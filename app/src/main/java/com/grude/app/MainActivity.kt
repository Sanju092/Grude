package com.grude.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.grude.app.ui.GrudeViewModel
import com.grude.app.ui.screens.ActionsScreen
import com.grude.app.ui.screens.AuthScreen
import com.grude.app.ui.screens.PrAndReleaseScreen
import com.grude.app.ui.screens.PushAndBuildScreen
import com.grude.app.ui.screens.ReposAndGitScreen
import com.grude.app.ui.theme.GrudeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GrudeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { viewModel.handleOAuthCallback(it) }

        setContent {
            GrudeTheme {
                GrudeAppScaffold(viewModel = viewModel, onTakePersistableUri = { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    viewModel.setSelectedFolder(uri)
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { viewModel.handleOAuthCallback(it) }
    }
}

@Composable
private fun GrudeAppScaffold(
    viewModel: GrudeViewModel,
    onTakePersistableUri: (android.net.Uri) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val statusBanner by viewModel.statusBanner.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onTakePersistableUri(uri)
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectReleaseApkFromUri(uri)
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    label = { Text("Push") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("Actions") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.CallMerge, contentDescription = null) },
                    label = { Text("PR & Rel") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    label = { Text("Code") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    label = { Text("Auth") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> PushAndBuildScreen(
                    viewModel = viewModel,
                    onPickLocalFolder = { folderPickerLauncher.launch(null) }
                )
                1 -> ActionsScreen(viewModel = viewModel)
                2 -> PrAndReleaseScreen(
                    viewModel = viewModel,
                    onPickReleaseApkFile = {
                        apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                    }
                )
                3 -> ReposAndGitScreen(viewModel = viewModel)
                4 -> AuthScreen(viewModel = viewModel)
            }
        }
    }
}
