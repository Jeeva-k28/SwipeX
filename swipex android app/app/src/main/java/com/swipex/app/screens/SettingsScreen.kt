package com.swipex.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swipex.app.SwipeXViewModel
import com.swipex.app.ui.SwipeXSlate

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = Color.White) },
        containerColor = SwipeXSlate,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sensitivity Slider
                Column {
                    Text("Sensitivity: ${"%.2f".format(sensitivity)}", color = Color.White)
                    Slider(
                        value = sensitivity,
                        onValueChange = { viewModel.setSensitivity(it) },
                        valueRange = 0.1f..5.0f
                    )
                }

                // Cursor Speed Slider
                Column {
                    Text("Cursor Speed: ${"%.2f".format(cursorSpeed)}", color = Color.White)
                    Slider(
                        value = cursorSpeed,
                        onValueChange = { viewModel.setCursorSpeed(it) },
                        valueRange = 0.1f..5.0f
                    )
                }

                // Scroll Speed Slider
                Column {
                    Text("Scroll Speed: ${"%.2f".format(scrollSpeed)}", color = Color.White)
                    Slider(
                        value = scrollSpeed,
                        onValueChange = { viewModel.setScrollSpeed(it) },
                        valueRange = 0.1f..5.0f
                    )
                }

                // Dark Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode", color = Color.White)
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }

                // Auto Connect Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Connect", color = Color.White)
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { viewModel.setAutoConnect(it) }
                    )
                }

                // Action Buttons
                var manualIp by remember { mutableStateOf("") }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Manual Connection", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            placeholder = { Text("Enter IP (e.g. 192.168.1.5)", color = Color.LightGray) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        
                        Button(
                            onClick = {
                                if (manualIp.isNotBlank()) {
                                    viewModel.connectToIp(manualIp)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Connect")
                        }
                    }
                    
                    if (connectionError != null) {
                        Text(
                            text = "Error: $connectionError",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onPairNewComputer()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Pair New Computer")
                    }

                    if (isConnected) {
                        Button(
                            onClick = { viewModel.disconnect() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Disconnect", color = Color.White)
                        }
                    }
                }

                HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)

                // About section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("SwipeX Touchpad Client", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("Version 1.0", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        }
    )
}
