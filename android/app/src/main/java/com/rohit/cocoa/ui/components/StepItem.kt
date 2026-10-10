package com.rohit.cocoa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.model.AgentStep
import com.rohit.cocoa.model.StepStatus
import com.rohit.cocoa.model.StepType
import com.rohit.cocoa.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StepItem(
    step: AgentStep,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (typeBadgeBg, typeLabel) = when (step.type) {
        StepType.READ -> StepReadColor to "READ"
        StepType.EDIT -> StepEditColor to "EDIT"
        StepType.WRITE -> StepEditColor to "WRITE"
        StepType.BASH -> StepBashColor to "BASH"
        StepType.SEARCH -> StepSearchColor to "SEARCH"
        StepType.THINK -> StepThinkColor to "THINK"
        StepType.BROWSER -> StepBrowserColor to "BROWSE"
        StepType.SUBAGENT -> CocoaAccent to "SUBAGENT"
        StepType.QUESTION -> MochiOrange to "QUESTION"
        StepType.GENERATE_IMAGE -> MochiPurple to "IMAGE"
        StepType.INFO -> CocoaTextTertiary to "INFO"
    }

    val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(step.timestamp))

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = CocoaCardDark.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon / Indicator
            when (step.status) {
                StepStatus.RUNNING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = typeBadgeBg
                    )
                }
                StepStatus.SUCCESS -> {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MochiGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = MochiGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StepStatus.FAILED -> {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MochiRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = MochiRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StepStatus.WAITING_APPROVAL -> {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MochiOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            color = MochiOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StepStatus.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(CocoaTextTertiary.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Step Type Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(typeBadgeBg.copy(alpha = 0.18f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = typeLabel,
                    color = typeBadgeBg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Detail
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CocoaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (step.detail.isNotEmpty() || step.command != null || step.targetFile != null) {
                    val subtitle = step.command ?: step.targetFile ?: step.detail
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CocoaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = if (step.command != null || step.targetFile != null) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Timestamp / Duration
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = CocoaTextTertiary
                )
                if (step.durationMs > 0) {
                    Text(
                        text = "${step.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = CocoaTextTertiary
                    )
                }
            }
        }
    }
}
