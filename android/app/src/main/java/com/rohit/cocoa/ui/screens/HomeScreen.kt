package com.rohit.cocoa.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.model.ConnectionStatus
import com.rohit.cocoa.model.MochiMood
import com.rohit.cocoa.network.CocoaLinkClient
import com.rohit.cocoa.ui.components.GlassCard
import com.rohit.cocoa.ui.components.MochiAvatar
import com.rohit.cocoa.ui.components.StepItem
import com.rohit.cocoa.ui.theme.*

@Composable
fun HomeScreen(
    linkClient: CocoaLinkClient,
    onNavigateToApprovals: () -> Unit,
    onNavigateToQuestions: () -> Unit,
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionStatus by linkClient.connectionStatus.collectAsState()
    val session by linkClient.activeSession.collectAsState()
    val approvals by linkClient.approvals.collectAsState()
    val questions by linkClient.questions.collectAsState()

    var quickPromptText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = CocoaBgDark,
        bottomBar = {
            // Quick Send Input Bar
            Surface(
                color = CocoaSurfaceDark,
                tonalElevation = 8.dp,
                modifier = Modifier.imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = quickPromptText,
                        onValueChange = { quickPromptText = it },
                        placeholder = { Text("Send instruction to Cocoa...", color = CocoaTextTertiary, fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CocoaCardDark,
                            unfocusedContainerColor = CocoaCardDark,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = CocoaTextPrimary,
                            unfocusedTextColor = CocoaTextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (quickPromptText.isNotBlank()) {
                                linkClient.sendPrompt(quickPromptText, "Claude 3.7 Sonnet (Thinking)", 4000)
                                quickPromptText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (quickPromptText.isNotBlank()) CocoaAccent else CocoaCardDark),
                        enabled = quickPromptText.isNotBlank() && connectionStatus == ConnectionStatus.CONNECTED
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (quickPromptText.isNotBlank()) Color.White else CocoaTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
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
                // Top Header with Connection Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cocoa",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = CocoaTextPrimary
                        )
                        Text(
                            text = "Companion for Windows & macOS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CocoaTextSecondary
                        )
                    }

                    // Connection Badge
                    val (statusColor, statusLabel) = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> MochiGreen to "Connected"
                        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> MochiOrange to "Connecting..."
                        ConnectionStatus.DISCONNECTED -> CocoaTextTertiary to "Disconnected"
                        ConnectionStatus.ERROR -> MochiRed to "Error"
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onNavigateToPairing() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Pending Approvals Banner Alert
            if (approvals.isNotEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToApprovals() },
                        backgroundColor = MochiOrange.copy(alpha = 0.15f),
                        borderColor = MochiOrange.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MochiOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${approvals.size} Approval Request${if (approvals.size > 1) "s" else ""} Pending",
                                    color = CocoaTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = approvals.first().title,
                                    color = CocoaTextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "Review →",
                                color = MochiOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Pending Questions Banner Alert
            if (questions.isNotEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToQuestions() },
                        backgroundColor = CocoaAccent.copy(alpha = 0.15f),
                        borderColor = CocoaAccent.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "❓",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Agent Question Waiting",
                                    color = CocoaTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = questions.first().question,
                                    color = CocoaTextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "Answer →",
                                color = CocoaAccentLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Hero Mochi Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = CocoaSurfaceDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val currentMood = session?.mood ?: MochiMood.IDLE
                        MochiAvatar(mood = currentMood, size = 110.dp)

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = session?.currentAction ?: if (connectionStatus == ConnectionStatus.CONNECTED) "Mochi is ready for instructions" else "Not connected to PC",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CocoaTextPrimary
                        )

                        if (session != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = session?.taskTitle ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CocoaTextSecondary,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Model & Token usage pills
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CocoaAccent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = session?.activeModel ?: "Claude 3.7",
                                        color = CocoaAccentLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                if ((session?.tokenUsage?.totalTokens ?: 0) > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CocoaCardDark
                                    ) {
                                        Text(
                                            text = "${session?.tokenUsage?.totalTokens} tokens",
                                            color = CocoaTextSecondary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Activity Steps Timeline
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Activity",
                        style = MaterialTheme.typography.titleLarge,
                        color = CocoaTextPrimary
                    )

                    val count = session?.steps?.size ?: 0
                    Text(
                        text = "$count steps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CocoaTextTertiary
                    )
                }
            }

            val steps = session?.steps ?: emptyList()
            if (steps.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = CocoaCardDark.copy(alpha = 0.5f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No active steps yet. Prompt the agent to begin.",
                                color = CocoaTextTertiary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(steps.reversed(), key = { it.id }) { step ->
                    StepItem(step = step)
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
