package com.remodex.mobile.ui.parity

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.PairingPayload

@Composable
fun PairingScreen(
    connectionState: ConnectionState,
    status: String,
    relayUrl: String,
    onRelayUrlChange: (String) -> Unit,
    sessionId: String,
    onSessionIdChange: (String) -> Unit,
    macDeviceId: String,
    onMacDeviceIdChange: (String) -> Unit,
    macIdentityPublicKey: String,
    onMacIdentityPublicKeyChange: (String) -> Unit,
    notificationsEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRememberPairing: () -> Unit,
    onConnectLive: () -> Unit,
    onScannedPairing: (PairingPayload) -> Unit,
    onHeaderTap: () -> Unit
) {
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var scannerErrorMessage by remember { mutableStateOf<String?>(null) }
    var bridgeUpdatePrompt by remember { mutableStateOf<BridgeUpdatePrompt?>(null) }
    var didCopyBridgeCommand by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val indicatorColor = animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.Connected -> Color(0xFF2DB17D)
            ConnectionState.Connecting -> Color(0xFFE6A23C)
            is ConnectionState.Failed -> Color(0xFFD9534F)
            ConnectionState.Paired -> Color(0xFF5B9BD5)
            ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(),
        label = "pairingConnection"
    )

    val pageGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageGradient)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroCard(
                    stateLabel = connectionStateLabel(connectionState),
                    status = status,
                    indicatorColor = indicatorColor.value,
                    subtitle = "Pairing and connection",
                    onTap = onHeaderTap
                )
            }
            item {
                SectionCard(
                    title = "Scan QR",
                    subtitle = "Point the camera at the secure code shown by `remodex up`."
                ) {
                    if (bridgeUpdatePrompt == null) {
                        PairingQrScannerSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp),
                            onScan = { scannedCode ->
                                when (val result = validatePairingQrCode(scannedCode)) {
                                    is QrScannerPairingValidationResult.Success -> {
                                        onScannedPairing(result.payload)
                                        bridgeUpdatePrompt = null
                                    }

                                    is QrScannerPairingValidationResult.ScanError -> {
                                        scannerErrorMessage = result.message
                                    }

                                    is QrScannerPairingValidationResult.BridgeUpdateRequired -> {
                                        didCopyBridgeCommand = false
                                        bridgeUpdatePrompt = result.prompt
                                    }
                                }
                            }
                        )
                    } else {
                        val prompt = bridgeUpdatePrompt ?: return@SectionCard
                        Text(
                            text = prompt.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = prompt.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = prompt.command,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            label = { Text("Update command") }
                        )
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(prompt.command))
                                didCopyBridgeCommand = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (didCopyBridgeCommand) "Copied" else "Copy Command")
                        }
                        Button(
                            onClick = {
                                bridgeUpdatePrompt = null
                                didCopyBridgeCommand = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue")
                        }
                    }

                    OutlinedButton(
                        onClick = { showManualEntry = !showManualEntry },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showManualEntry) "Hide Manual Details" else "Enter Details Manually")
                    }
                }
            }
            if (showManualEntry) {
                item {
                    SectionCard(
                        title = "Manual Details",
                        subtitle = "Only use this when the QR path is unavailable."
                    ) {
                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = onRelayUrlChange,
                            label = { Text("Relay URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sessionId,
                            onValueChange = onSessionIdChange,
                            label = { Text("Session") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = macDeviceId,
                            onValueChange = onMacDeviceIdChange,
                            label = { Text("Mac device") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = macIdentityPublicKey,
                            onValueChange = onMacIdentityPublicKeyChange,
                            label = { Text("Identity key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRememberPairing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
                            }
                            Button(
                                onClick = onConnectLive,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(
                    title = "Notifications",
                    subtitle = "Get alerts when work needs your attention."
                ) {
                    Text(
                        text = if (notificationsEnabled) "Enabled" else "Off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!notificationsEnabled) {
                        Button(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Notifications")
                        }
                    }
                }
            }
        }
    }

    val error = scannerErrorMessage
    if (error != null) {
        AlertDialog(
            onDismissRequest = { scannerErrorMessage = null },
            title = { Text("Scan Error") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { scannerErrorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

fun connectionStateLabel(state: ConnectionState): String {
    return when (state) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Paired -> "Paired"
        ConnectionState.Connecting -> "Connecting"
        ConnectionState.Connected -> "Connected"
        is ConnectionState.Failed -> "Failed"
    }
}
