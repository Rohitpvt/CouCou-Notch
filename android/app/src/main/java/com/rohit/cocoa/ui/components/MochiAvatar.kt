package com.rohit.cocoa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.model.MochiMood
import com.rohit.cocoa.ui.theme.*

@Composable
fun MochiAvatar(
    mood: MochiMood,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    // Breathing & pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "mochi_infinite")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val blinkState by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1f at 0
                1f at 3100
                0.1f at 3250
                1f at 3400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    val glowColor by animateColorAsState(
        targetValue = when (mood) {
            MochiMood.IDLE -> CocoaAccent.copy(alpha = 0.4f)
            MochiMood.HAPPY -> MochiGreen.copy(alpha = 0.6f)
            MochiMood.THINKING -> CocoaAccentDark.copy(alpha = 0.7f)
            MochiMood.CODING -> MochiOrange.copy(alpha = 0.7f)
            MochiMood.SLEEPING -> CocoaTextTertiary.copy(alpha = 0.3f)
            MochiMood.WARNING -> MochiYellow.copy(alpha = 0.8f)
            MochiMood.ERROR -> MochiRed.copy(alpha = 0.8f)
            MochiMood.CELEBRATING -> MochiPurple.copy(alpha = 0.8f)
        },
        animationSpec = tween(400),
        label = "glow_color"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(if (mood == MochiMood.THINKING || mood == MochiMood.CODING) pulseScale else 1f),
        contentAlignment = Alignment.Center
    ) {
        // Outer aura glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent)
                    )
                )
        )

        // Mochi Face Canvas
        Canvas(
            modifier = Modifier
                .size(size * 0.82f)
        ) {
            val width = this.size.width
            val height = this.size.height

            // Mochi Body Pill / Oval
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF8A6FF0),
                        Color(0xFF5E3CE6)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                ),
                topLeft = Offset(0f, 0f),
                size = Size(width, height)
            )

            // Inner Shadow / Highlight Ring
            drawOval(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(width * 0.08f, height * 0.05f),
                size = Size(width * 0.84f, height * 0.45f)
            )

            // Cheeks
            val cheekColor = Color(0xFFFF88A3).copy(alpha = 0.7f)
            drawOval(
                color = cheekColor,
                topLeft = Offset(width * 0.12f, height * 0.58f),
                size = Size(width * 0.18f, height * 0.10f)
            )
            drawOval(
                color = cheekColor,
                topLeft = Offset(width * 0.70f, height * 0.58f),
                size = Size(width * 0.18f, height * 0.10f)
            )

            // Eyes
            val eyeHeight = (height * 0.14f) * (if (mood == MochiMood.SLEEPING) 0.15f else blinkState)
            val eyeWidth = width * 0.13f
            val eyeTop = height * 0.42f + ((height * 0.14f - eyeHeight) / 2f)

            // Left Eye
            drawOval(
                color = Color.White,
                topLeft = Offset(width * 0.28f, eyeTop),
                size = Size(eyeWidth, eyeHeight)
            )
            // Left Pupil
            if (mood != MochiMood.SLEEPING) {
                drawOval(
                    color = Color(0xFF18181E),
                    topLeft = Offset(width * 0.31f, eyeTop + eyeHeight * 0.2f),
                    size = Size(eyeWidth * 0.65f, eyeHeight * 0.65f)
                )
            }

            // Right Eye
            drawOval(
                color = Color.White,
                topLeft = Offset(width * 0.59f, eyeTop),
                size = Size(eyeWidth, eyeHeight)
            )
            // Right Pupil
            if (mood != MochiMood.SLEEPING) {
                drawOval(
                    color = Color(0xFF18181E),
                    topLeft = Offset(width * 0.62f, eyeTop + eyeHeight * 0.2f),
                    size = Size(eyeWidth * 0.65f, eyeHeight * 0.65f)
                )
            }

            // Mouth / Expression
            when (mood) {
                MochiMood.HAPPY, MochiMood.CELEBRATING -> {
                    drawArc(
                        color = Color.White,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(width * 0.42f, height * 0.56f),
                        size = Size(width * 0.16f, height * 0.14f)
                    )
                }
                MochiMood.THINKING -> {
                    drawOval(
                        color = Color.White,
                        topLeft = Offset(width * 0.46f, height * 0.60f),
                        size = Size(width * 0.08f, height * 0.08f)
                    )
                }
                MochiMood.WARNING, MochiMood.ERROR -> {
                    drawLine(
                        color = Color.White,
                        start = Offset(width * 0.42f, height * 0.64f),
                        end = Offset(width * 0.58f, height * 0.64f),
                        strokeWidth = 4f
                    )
                }
                else -> {
                    drawArc(
                        color = Color.White,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(width * 0.42f, height * 0.54f),
                        size = Size(width * 0.16f, height * 0.12f),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
    }
}
