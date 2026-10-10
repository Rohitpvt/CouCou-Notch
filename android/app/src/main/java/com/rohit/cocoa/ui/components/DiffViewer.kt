package com.rohit.cocoa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.ui.theme.*

@Composable
fun DiffViewer(
    diffText: String,
    modifier: Modifier = Modifier
) {
    val lines = diffText.lines()
    val hScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101015))
            .padding(vertical = 6.dp)
            .horizontalScroll(hScroll)
    ) {
        lines.forEachIndexed { index, line ->
            val isAdded = line.startsWith("+") && !line.startsWith("+++")
            val isRemoved = line.startsWith("-") && !line.startsWith("---")
            val isHeader = line.startsWith("@@") || line.startsWith("diff ") || line.startsWith("index ")

            val rowBg = when {
                isAdded -> MochiGreen.copy(alpha = 0.15f)
                isRemoved -> MochiRed.copy(alpha = 0.15f)
                isHeader -> CocoaAccent.copy(alpha = 0.1f)
                else -> Color.Transparent
            }

            val textColor = when {
                isAdded -> Color(0xFF7CE38B)
                isRemoved -> Color(0xFFFA7970)
                isHeader -> CocoaAccentLight
                else -> CocoaTextSecondary
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${index + 1}".padStart(3, ' '),
                    color = CocoaTextTertiary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp)
                )

                Text(
                    text = line,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
