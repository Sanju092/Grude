package com.grude.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.grude.app.model.AuthMethod
import com.grude.app.ui.GrudeViewModel

@Composable
fun AuthScreen(viewModel: GrudeViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val activeAccount by viewModel.activeAccount.collectAsState()
    val deviceCode by viewModel.deviceCode.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("PAT", "Device Flow", "GitHub App", "OAuth Web", "SSH Key")

    var patInput by remember { mutableStateOf("") }
    var clientIdInput by remember { mutableStateOf("") }
    var sshLabelInput by remember { mutableStateOf("grude-android") }
    var generatedPublicKey by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("GitHub Authentication Vault", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Connect your account using any of the 5 GitHub auth modes. Credentials are encrypted with Android Keystore.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
        }

        item {
            when (selectedTab) {
                0 -> {
                    // Personal Access Token (Fine-grained or Classic)
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        label = { Text("Fine-grained or Classic PAT") },
                        placeholder = { Text("ghp_... or github_pat_...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Grant Contents (write), Workflows (write), Actions (read/write), and Pull Requests permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.signInWithPat(patInput)
                            patInput = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect with Personal Access Token")
                    }
                }

                1, 2 -> {
                    val isGitHubApp = selectedTab == 2
                    OutlinedTextField(
                        value = clientIdInput,
                        onValueChange = { clientIdInput = it },
                        label = { Text(if (isGitHubApp) "GitHub App Client ID" else "OAuth App Client ID") },
                        placeholder = { Text("Iv1... or Ov23li...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.startDeviceFlow(clientIdInput, isGitHubApp = isGitHubApp) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isGitHubApp) "Start GitHub App Device Flow" else "Start Serverless Device Flow")
                    }

                    deviceCode?.let { code ->
                        Spacer(Modifier.height(12.dp))
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Verification Code", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    code.userCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        clipboard.setText(AnnotatedString(code.userCode))
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy Code")
                                    }
                                    Button(onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)))
                                    }) {
                                        Text("Open GitHub")
                                    }
                                }
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Waiting for browser approval...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                3 -> {
                    OutlinedTextField(
                        value = clientIdInput,
                        onValueChange = { clientIdInput = it },
                        label = { Text("OAuth App Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Opens GitHub in browser and redirects back to grude://callback (connects to the Phase 5 Relay Server to exchange the code securely).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (clientIdInput.isNotBlank()) {
                                val url = viewModel.authVault.buildOAuthAuthorizeUrl(clientIdInput.trim())
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Authorize in Browser (OAuth Web Flow)")
                    }
                }

                4 -> {
                    OutlinedTextField(
                        value = sshLabelInput,
                        onValueChange = { sshLabelInput = it },
                        label = { Text("SSH Key Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val profile = viewModel.generateSshKey(sshLabelInput)
                            generatedPublicKey = profile.sshPublicKey
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Ed25519 SSH Key Pair")
                    }
                    generatedPublicKey?.let { pubKey ->
                        Spacer(Modifier.height(12.dp))
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Public Key (Add to GitHub Settings -> SSH Keys)", style = MaterialTheme.typography.labelMedium)
                                Text(pubKey.take(120) + "...", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(pubKey)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Public Key")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (accounts.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text("Connected Accounts", style = MaterialTheme.typography.titleMedium)
            }
            items(accounts, key = { it.id }) { acc ->
                val isActive = acc.id == activeAccount?.id
                ElevatedCard(
                    onClick = { viewModel.switchAccount(acc.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("@${acc.username}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                acc.authMethod.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isActive) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = { viewModel.removeAccount(acc.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
