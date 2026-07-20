package com.swipex.app.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipex.app.R
import com.swipex.app.SwipeXViewModel
import com.swipex.app.touchpad.touchpadInput
import com.swipex.app.ui.SwipeXDark
import com.swipex.app.ui.SwipeXGreen
import com.swipex.app.ui.SwipeXRed
import com.swipex.app.ui.SwipeXSlate
import com.swipex.app.utils.HapticHelper

data class TouchTrailPoint(
    val position: Offset,
    val timestamp: Long = System.currentTimeMillis()
)

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

    // Touch Trail Glow Effect State
    val trailPoints = remember { mutableStateListOf<TouchTrailPoint>() }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Active frame ticker loop to continuously fade points over 1 second (1000ms)
    LaunchedEffect(trailPoints.size) {
        if (trailPoints.isNotEmpty()) {
            while (true) {
                withFrameNanos { _ ->
                    currentTime = System.currentTimeMillis()
                    val now = currentTime
                    trailPoints.removeAll { pt -> (now - pt.timestamp) > 1000L }
                }
                if (trailPoints.isEmpty()) break
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0E11))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // SwipeX logo PNG instead of generic Menu icon
                        Image(
                            painter = painterResource(id = R.drawable.swipex_logo),
                            contentDescription = "SwipeX",
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "SwipeX",
                            color = Color.White,
                            fontSize = 22.sp,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (hasCameraPermission.value) {
                                showScanner = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Connection Subheader line (Matching Image 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "Connected to ${serverIp ?: "10.12.43.207"}" else "Not Connected",
                        color = if (isConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF0D0E11))
        ) {
            // Touchpad Surface Box (Matching Image 1 Layout)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(Color(0xFF16181D), shape = RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF2A2D35), shape = RoundedCornerShape(24.dp))
                    .systemGestureExclusion()
                    .touchpadInput(
                        onMove = { dx, dy, dt -> viewModel.onTouchMove(dx, dy, dt) },
                        onClick = { button, action -> viewModel.onAction(button, action) },
                        onScroll = { dy, dx -> viewModel.onScroll(dy, dx) },
                        onZoom = { zoomType -> viewModel.onZoom(zoomType) },
                        onGesture = { gestureName -> viewModel.onGesture(gestureName) },
                        onPositionChange = { offset ->
                            trailPoints.add(TouchTrailPoint(offset))
                        },
                        vibrate = { HapticHelper.vibrate(context, 40) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Subtle Centered Touch Gesture Icon (Matching Image 1)
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                )

                // High-Performance 1-Second Fading Neon Glow Line Canvas Layer
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val now = currentTime
                    for (i in 0 until trailPoints.size - 1) {
                        val p1 = trailPoints[i]
                        val p2 = trailPoints[i + 1]

                        val age = now - p1.timestamp
                        if (age in 0L..1000L) {
                            val alpha = (1.0f - age.toFloat() / 1000.0f).coerceIn(0f, 1f)

                            // Outer Cyan Glow Line
                            drawLine(
                                color = Color(0xFF00E5FF).copy(alpha = alpha * 0.45f),
                                start = p1.position,
                                end = p2.position,
                                strokeWidth = 12.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Inner Bright White Core Line
                            drawLine(
                                color = Color.White.copy(alpha = alpha * 0.85f),
                                start = p1.position,
                                end = p2.position,
                                strokeWidth = 3.5f.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
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
