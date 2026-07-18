package com.swipex.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swipex.app.SwipeXViewModel
import com.swipex.app.touchpad.touchpadInput
import com.swipex.app.ui.SwipeXCard
import com.swipex.app.ui.SwipeXDark
import com.swipex.app.ui.SwipeXGreen
import com.swipex.app.ui.SwipeXRed
import com.swipex.app.ui.SwipeXSlate
import com.swipex.app.utils.HapticHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchpadScreen(viewModel: SwipeXViewModel) {
    val isConnected by viewModel.isConnected.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    val hasCameraPermission = remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission.value = isGranted
        if (isGranted) {
            showScanner = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SwipeX") },
                actions = {
                    IconButton(onClick = {
                        if (hasCameraPermission.value) {
                            showScanner = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(if (isDarkMode) SwipeXDark else Color.White)
        ) {
            // Connection Status Header
            ConnectionStatusHeader(isConnected, serverIp)

            // Touchpad Area: ~85% of weight, clean, empty slate box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(SwipeXSlate, shape = MaterialTheme.shapes.large)
                    .touchpadInput(
                        onMove = { dx, dy -> viewModel.onTouchMove(dx, dy) },
                        onClick = { button, action -> viewModel.onAction(button, action) },
                        onScroll = { dy -> viewModel.onScroll(dy) },
                        vibrate = { HapticHelper.vibrate(context, 40) }
                    )
            )

            // Bottom Buttons: Left and Right Mouse Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Mouse Button supporting hold
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(SwipeXCard, shape = MaterialTheme.shapes.medium)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent()
                                    viewModel.onAction("l", "d") // Left click down
                                    
                                    // Wait for release
                                    while (true) {
                                        val move = awaitPointerEvent()
                                        if (move.changes.none { it.pressed }) {
                                            viewModel.onAction("l", "u") // Left click up
                                            break
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("LEFT", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }

                // Right Mouse Button supporting hold
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(SwipeXCard, shape = MaterialTheme.shapes.medium)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent()
                                    viewModel.onAction("r", "d") // Right click down
                                    
                                    // Wait for release
                                    while (true) {
                                        val move = awaitPointerEvent()
                                        if (move.changes.none { it.pressed }) {
                                            viewModel.onAction("r", "u") // Right click up
                                            break
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("RIGHT", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            viewModel = viewModel,
            onPairNewComputer = { showScanner = true },
            onDismiss = { showSettings = false }
        )
    }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showScanner = false }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.background
            ) {
                ScannerScreen(onQrScanned = { payload ->
                    if (showScanner) {
                        showScanner = false
                        viewModel.connectToIp(payload)
                    }
                })
            }
        }
    }
}

@Composable
fun ConnectionStatusHeader(isConnected: Boolean, serverIp: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (isConnected) SwipeXGreen else SwipeXRed,
                    shape = MaterialTheme.shapes.small
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isConnected) "Connected to $serverIp" else "Not Connected",
            color = if (isConnected) SwipeXGreen else SwipeXRed,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
