package com.rohit.cocoa.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.model.ConnectionStatus
import com.rohit.cocoa.network.CocoaLinkClient
import com.rohit.cocoa.ui.components.GlassCard
import com.rohit.cocoa.ui.theme.*

@Composable
fun PairingScreen(
    linkClient: CocoaLinkClient,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionStatus by linkClient.connectionStatus.collectAsState()
    val discoveredDevices by linkClient.discoveredDevices.collectAsState()

    var hostInput by remember { mutableStateOf(linkClient.getSavedHost()) }
    var portInput by remember { mutableStateOf(linkClient.getSavedPort().toString()) }
    var tokenInput by remember { mutableStateOf(linkClient.getSavedToken()) }

    LaunchedEffect(Unit) {
        linkClient.startMdnsDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            linkClient.stopMdnsDiscovery()
        }
    }

    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            Toast.makeText(context, "Connected to Cocoa Desktop!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = CocoaBgDark
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pair with Desktop",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CocoaTextPrimary
                )
                Text(
                    text = "Connect Cocoa Android Companion to your Windows PC or Mac running Cocoa over Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocoaTextSecondary
                )
            }

            // Discovered Devices on local Wi-Fi
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered on Wi-Fi (mDNS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CocoaTextPrimary
                    )

                    IconButton(
                        onClick = {
                            linkClient.stopMdnsDiscovery()
                            linkClient.startMdnsDiscovery()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CocoaAccentLight
                        )
                    }
                }
            }

            if (discoveredDevices.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = CocoaCardDark.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CocoaAccent
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Searching for Cocoa Desktop on local network...",
                                color = CocoaTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(discoveredDevices) { device ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                hostInput = device.host
                                portInput = device.port.toString()
                                linkClient.connect(device.host, device.port, tokenInput)
                            },
                        backgroundColor = CocoaSurfaceDark,
                        borderColor = CocoaAccent.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CocoaAccent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "💻", fontSize = 18.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CocoaTextPrimary
                                )
                                Text(
                                    text = "${device.host}:${device.port}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CocoaTextTertiary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = "Connect",
                                color = CocoaAccentLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Manual Connection Form
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Manual Connection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CocoaTextPrimary
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = CocoaSurfaceDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("PC / Host IP Address", color = CocoaTextSecondary) },
                            placeholder = { Text("192.168.1.100", color = CocoaTextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CocoaAccent,
                                unfocusedBorderColor = CocoaCardBorder,
                                focusedTextColor = CocoaTextPrimary,
                                unfocusedTextColor = CocoaTextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port", color = CocoaTextSecondary) },
                            placeholder = { Text("47910", color = CocoaTextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CocoaAccent,
                                unfocusedBorderColor = CocoaCardBorder,
                                focusedTextColor = CocoaTextPrimary,
                                unfocusedTextColor = CocoaTextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Security Pairing Token", color = CocoaTextSecondary) },
                            placeholder = { Text("default_cocoa_key", color = CocoaTextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CocoaAccent,
                                unfocusedBorderColor = CocoaCardBorder,
                                focusedTextColor = CocoaTextPrimary,
                                unfocusedTextColor = CocoaTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val isConnected = connectionStatus == ConnectionStatus.CONNECTED

                        Button(
                            onClick = {
                                if (isConnected) {
                                    linkClient.disconnect()
                                } else {
                                    val port = portInput.toIntOrNull() ?: 47910
                                    linkClient.connect(hostInput, port, tokenInput)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) MochiRed else CocoaAccent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Disconnect" else "Save & Connect",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
