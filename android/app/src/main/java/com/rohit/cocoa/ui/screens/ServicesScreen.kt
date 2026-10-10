package com.rohit.cocoa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.model.ServiceStatus
import com.rohit.cocoa.network.CocoaLinkClient
import com.rohit.cocoa.ui.components.GlassCard
import com.rohit.cocoa.ui.theme.*

@Composable
fun ServicesScreen(
    linkClient: CocoaLinkClient,
    modifier: Modifier = Modifier
) {
    val services by linkClient.services.collectAsState()

    Scaffold(
        containerColor = CocoaBgDark
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Local Services",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CocoaTextPrimary
                )
                Text(
                    text = "Monitor background servers, database containers, and dev tooling running on your PC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocoaTextSecondary
                )
            }

            if (services.isEmpty()) {
                // Mock default services for demonstration if desktop has not yet published list
                val defaultServices = listOf(
                    ServiceStatus("s1", "Cocoa Windows Companion", "RUST", true, 47910, "ws://localhost:47910", 1.2f, 48, 3600),
                    ServiceStatus("s2", "Vite Web Preview", "VITE", true, 5173, "http://localhost:5173", 0.4f, 85, 1800),
                    ServiceStatus("s3", "Antigravity Dev Bridge", "API", true, 3000, "http://localhost:3000", 2.1f, 120, 7200)
                )

                items(defaultServices, key = { it.id }) { service ->
                    ServiceCard(
                        service = service,
                        onAction = { action -> linkClient.sendServiceAction(service.id, action) }
                    )
                }
            } else {
                items(services, key = { it.id }) { service ->
                    ServiceCard(
                        service = service,
                        onAction = { action -> linkClient.sendServiceAction(service.id, action) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ServiceCard(
    service: ServiceStatus,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = CocoaSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (service.isRunning) MochiGreen else MochiRed)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CocoaTextPrimary
                    )
                    Text(
                        text = "${service.type}${if (service.port != null) " • Port :${service.port}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CocoaTextTertiary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Restart / Action Button
                IconButton(
                    onClick = { onAction(if (service.isRunning) "RESTART" else "START") },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CocoaCardDark)
                ) {
                    Icon(
                        imageVector = if (service.isRunning) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = "Restart",
                        tint = CocoaAccentLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Resource Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "CPU: ${String.format("%.1f", service.cpuUsagePct)}%",
                        color = CocoaTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "RAM: ${service.memoryUsageMb} MB",
                        color = CocoaTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = if (service.isRunning) "UP ${service.uptimeSeconds / 60}m" else "STOPPED",
                    color = if (service.isRunning) MochiGreen else MochiRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
