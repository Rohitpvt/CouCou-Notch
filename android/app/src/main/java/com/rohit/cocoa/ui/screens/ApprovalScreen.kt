package com.rohit.cocoa.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.rohit.cocoa.model.ApprovalRequest
import com.rohit.cocoa.network.CocoaLinkClient
import com.rohit.cocoa.ui.components.DiffViewer
import com.rohit.cocoa.ui.components.GlassCard
import com.rohit.cocoa.ui.theme.*

@Composable
fun ApprovalScreen(
    linkClient: CocoaLinkClient,
    modifier: Modifier = Modifier
) {
    val approvals by linkClient.approvals.collectAsState()
    val context = LocalContext.current

    fun authenticateAndApprove(request: ApprovalRequest) {
        if (request.requiresBiometrics) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            linkClient.sendApproval(request.id, approved = true)
                            Toast.makeText(context, "Approved via Biometrics", Toast.LENGTH_SHORT).show()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            Toast.makeText(context, "Biometric failed: $errString", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Approve Command Execution")
                    .setSubtitle(request.title)
                    .setNegativeButtonText("Cancel")
                    .build()
                prompt.authenticate(promptInfo)
                return
            }
        }
        linkClient.sendApproval(request.id, approved = true)
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
                    text = "Approval Requests",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CocoaTextPrimary
                )
                Text(
                    text = "Confirm sensitive terminal commands, file changes, or external actions requested by Cocoa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocoaTextSecondary
                )
            }

            if (approvals.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        backgroundColor = CocoaCardDark.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🛡️", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No pending approvals",
                                style = MaterialTheme.typography.titleLarge,
                                color = CocoaTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "When Cocoa requires permission to run bash commands or modify critical files, they will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CocoaTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(approvals, key = { it.id }) { request ->
                    ApprovalCard(
                        request = request,
                        onApprove = { authenticateAndApprove(request) },
                        onDeny = { linkClient.sendApproval(request.id, approved = false, reason = "Rejected by user") }
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
fun ApprovalCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CocoaSurfaceDark,
        borderColor = MochiOrange.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Type Pill & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MochiOrange.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = request.type,
                        color = MochiOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CocoaTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (request.requiresBiometrics) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Requires Biometrics",
                        tint = CocoaAccentLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (request.description.isNotEmpty()) {
                Text(
                    text = request.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocoaTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Command Preview Box
            if (!request.command.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101015))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "$ ${request.command}",
                        color = StepBashColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Diff Viewer Box
            if (!request.diff.isNullOrEmpty()) {
                Text(
                    text = "Changes Preview:",
                    style = MaterialTheme.typography.labelSmall,
                    color = CocoaTextTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                DiffViewer(diffText = request.diff)
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDeny,
                    colors = ButtonDefaults.buttonColors(containerColor = MochiRed.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MochiRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Reject", color = MochiRed, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = MochiGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Allow", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
