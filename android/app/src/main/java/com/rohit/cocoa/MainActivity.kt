package com.rohit.cocoa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import com.rohit.cocoa.service.MochiNotificationService
import com.rohit.cocoa.ui.screens.*
import com.rohit.cocoa.ui.theme.*

enum class NavigationTab(val label: String, val icon: ImageVector) {
    HOME("Live", Icons.Default.Home),
    APPROVALS("Approvals", Icons.Default.CheckCircle),
    PROMPT("Prompt", Icons.Default.Send),
    SERVICES("Services", Icons.Default.List),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : FragmentActivity() {

    private val linkClient by lazy { CocoaApplication.linkClient }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            MochiNotificationService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                MochiNotificationService.start(this)
            }
        } else {
            MochiNotificationService.start(this)
        }

        handleIntent(intent)

        setContent {
            CocoaCompanionTheme {
                MainAppScaffold(
                    linkClient = linkClient
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "cocoa" && data.host == "pair") {
            val host = data.getQueryParameter("host") ?: ""
            val port = data.getQueryParameter("port")?.toIntOrNull() ?: 47910
            val token = data.getQueryParameter("token") ?: ""

            if (host.isNotEmpty()) {
                linkClient.connect(host, port, token)
                Toast.makeText(this, "Pairing with $host:$port", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    linkClient: com.rohit.cocoa.network.CocoaLinkClient
) {
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var isPairingModalOpen by remember { mutableStateOf(false) }
    var isQuestionsModalOpen by remember { mutableStateOf(false) }

    val approvals by linkClient.approvals.collectAsState()
    val questions by linkClient.questions.collectAsState()

    Scaffold(
        containerColor = CocoaBgDark,
        bottomBar = {
            NavigationBar(
                containerColor = CocoaSurfaceDark,
                tonalElevation = 8.dp
            ) {
                NavigationTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val badgeCount = when (tab) {
                        NavigationTab.APPROVALS -> approvals.size
                        else -> 0
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (badgeCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MochiOrange,
                                            contentColor = Color.White
                                        ) {
                                            Text("$badgeCount")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CocoaAccentLight,
                            indicatorColor = CocoaAccent,
                            unselectedIconColor = CocoaTextTertiary,
                            unselectedTextColor = CocoaTextTertiary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isPairingModalOpen -> {
                    PairingScreen(
                        linkClient = linkClient,
                        onConnected = { isPairingModalOpen = false }
                    )
                }
                isQuestionsModalOpen -> {
                    QuestionScreen(
                        linkClient = linkClient
                    )
                }
                else -> {
                    when (currentTab) {
                        NavigationTab.HOME -> {
                            HomeScreen(
                                linkClient = linkClient,
                                onNavigateToApprovals = { currentTab = NavigationTab.APPROVALS },
                                onNavigateToQuestions = { isQuestionsModalOpen = true },
                                onNavigateToPairing = { isPairingModalOpen = true }
                            )
                        }
                        NavigationTab.APPROVALS -> {
                            ApprovalScreen(
                                linkClient = linkClient
                            )
                        }
                        NavigationTab.PROMPT -> {
                            InstructionScreen(
                                linkClient = linkClient
                            )
                        }
                        NavigationTab.SERVICES -> {
                            ServicesScreen(
                                linkClient = linkClient
                            )
                        }
                        NavigationTab.SETTINGS -> {
                            SettingsScreen(
                                linkClient = linkClient
                            )
                        }
                    }
                }
            }
        }
    }
}
