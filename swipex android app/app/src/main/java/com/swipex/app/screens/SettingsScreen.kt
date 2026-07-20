package com.swipex.app.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swipex.app.SwipeXViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SwipeXViewModel,
    onPairNewComputer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sensitivity by viewModel.sensitivity.collectAsState()
    val cursorSpeed by viewModel.cursorSpeed.collectAsState()
    val scrollSpeed by viewModel.scrollSpeed.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val autoConnect by viewModel.autoConnect.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var showManualIpDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xFF16181D)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ── Header ──────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF8A8D9B)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2A2D35), thickness = 1.dp)

                    // ── Slider helper ────────────────────────────────
                    @Composable
                    fun SettingSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.width(110.dp)
                            )
                            Slider(
                                value = value,
                                onValueChange = onValueChange,
                                valueRange = 0.1f..2.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFF2979FF),
                                    inactiveTrackColor = Color(0xFF2A2D35)
                                )
                            )
                            Text(
                                text = "%.2f".format(value),
                                color = Color(0xFF8A8D9B),
                                fontSize = 13.sp,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }

                    // ── Sliders ──────────────────────────────────────
                    SettingSlider("Sensitivity", sensitivity) { viewModel.setSensitivity(it) }
                    SettingSlider("Cursor Speed", cursorSpeed) { viewModel.setCursorSpeed(it) }
                    SettingSlider("Scroll Speed", scrollSpeed) { viewModel.setScrollSpeed(it) }

                    HorizontalDivider(color = Color(0xFF2A2D35), thickness = 1.dp)

                    // ── Toggle helper ────────────────────────────────
                    @Composable
                    fun SettingToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = Color.White, fontSize = 15.sp)
                            Switch(
                                checked = checked,
                                onCheckedChange = onToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2979FF),
                                    uncheckedThumbColor = Color(0xFF8A8D9B),
                                    uncheckedTrackColor = Color(0xFF2A2D35)
                                )
                            )
                        }
                    }

                    SettingToggle("Dark Mode", isDarkMode) { viewModel.setDarkMode(it) }
                    SettingToggle("Auto Connect", autoConnect) { viewModel.setAutoConnect(it) }

                    HorizontalDivider(color = Color(0xFF2A2D35), thickness = 1.dp)

                    // ── Action Buttons ───────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showManualIpDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3D45))
                        ) {
                            Text(
                                "Manual Connect",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                onPairNewComputer()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2979FF),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                "Scan QR Code",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // ── Disconnect ───────────────────────────────────
                    if (isConnected) {
                        TextButton(
                            onClick = { viewModel.disconnect() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect", color = Color(0xFFFF5252), fontSize = 14.sp)
                        }
                    }

                    // ── Footer ───────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("About SwipeX", color = Color(0xFF8A8D9B), fontSize = 13.sp)
                        Text("Version 1.0", color = Color(0xFF555870), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // ── Manual IP Dialog ─────────────────────────────────────────────
    if (showManualIpDialog) {
        var manualIp by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            containerColor = Color(0xFF1E2026),
            title = { Text("Manual Connection", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        placeholder = { Text("e.g. 192.168.1.5", color = Color(0xFF555870)) },
                        label = { Text("IP Address", color = Color(0xFF8A8D9B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF2979FF),
                            unfocusedBorderColor = Color(0xFF3A3D45)
                        )
                    )
                    if (connectionError != null) {
                        Text("⚠ $connectionError", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualIp.isNotBlank()) {
                            viewModel.connectToIp(manualIp)
                            showManualIpDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2979FF),
                        contentColor = Color.White
                    )
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text("Cancel", color = Color(0xFF8A8D9B))
                }
            }
        )
    }
}
