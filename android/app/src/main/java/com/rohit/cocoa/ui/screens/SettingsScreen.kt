package com.rohit.cocoa.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.network.CocoaLinkClient
import com.rohit.cocoa.service.MochiNotificationService
import com.rohit.cocoa.ui.components.GlassCard
import com.rohit.cocoa.ui.theme.*

@Composable
fun SettingsScreen(
    linkClient: CocoaLinkClient,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cocoa_settings", Context.MODE_PRIVATE) }

    var liveNotificationEnabled by remember {
        mutableStateOf(prefs.getBoolean("live_notif", true))
    }
    var biometricApprovalEnabled by remember {
        mutableStateOf(prefs.getBoolean("bio_approval", true))
    }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(prefs.getBoolean("haptics", true))
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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CocoaTextPrimary
                )
                Text(
                    text = "Preferences and companion configuration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocoaTextSecondary
                )
            }

            // Notifications Section
            item {
                Text(
                    text = "Live Activity & Notifications",
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ongoing Status Bar Mochi",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CocoaTextPrimary
                                )
                                Text(
                                    text = "Show live agent thoughts and actions in Android status bar & lock screen",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CocoaTextSecondary
                                )
                            }
                            Switch(
                                checked = liveNotificationEnabled,
                                onCheckedChange = {
                                    liveNotificationEnabled = it
                                    prefs.edit().putBoolean("live_notif", it).apply()
                                    if (it) {
                                        MochiNotificationService.start(context)
                                    } else {
                                        MochiNotificationService.stop(context)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CocoaAccent,
                                    checkedTrackColor = CocoaAccent.copy(alpha = 0.5f)
                                )
                            )
                        }

                        Divider(
                            color = CocoaCardBorder,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Biometric Lock on Sensitive Actions",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CocoaTextPrimary
                                )
                                Text(
                                    text = "Require fingerprint / face recognition before approving destructive terminal commands",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CocoaTextSecondary
                                )
                            }
                            Switch(
                                checked = biometricApprovalEnabled,
                                onCheckedChange = {
                                    biometricApprovalEnabled = it
                                    prefs.edit().putBoolean("bio_approval", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CocoaAccent,
                                    checkedTrackColor = CocoaAccent.copy(alpha = 0.5f)
                                )
                            )
                        }

                        Divider(
                            color = CocoaCardBorder,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Haptic Feedback",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CocoaTextPrimary
                                )
                                Text(
                                    text = "Vibrate on task completion and approval triggers",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CocoaTextSecondary
                                )
                            }
                            Switch(
                                checked = hapticFeedbackEnabled,
                                onCheckedChange = {
                                    hapticFeedbackEnabled = it
                                    prefs.edit().putBoolean("haptics", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CocoaAccent,
                                    checkedTrackColor = CocoaAccent.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            // About section
            item {
                Text(
                    text = "About Cocoa",
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Cocoa Android Companion v1.0.0",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CocoaTextPrimary
                        )
                        Text(
                            text = "Matching CocoaPhone iOS companion app with native Jetpack Compose & live Mochi notifications.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CocoaTextSecondary
                        )
                        Text(
                            text = "Built with Kotlin 2.1, Jetpack Compose, Ktor Client, and Material Design 3.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CocoaTextTertiary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
