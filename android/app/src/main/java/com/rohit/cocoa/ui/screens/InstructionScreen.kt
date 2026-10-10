package com.rohit.cocoa.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.network.CocoaLinkClient
import com.rohit.cocoa.ui.components.GlassCard
import com.rohit.cocoa.ui.theme.*

@Composable
fun InstructionScreen(
    linkClient: CocoaLinkClient,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var promptText by remember { mutableStateOf("") }
    val models = listOf(
        "Claude 3.7 Sonnet (Thinking)",
        "Claude 3.5 Sonnet",
        "GPT-4.5 Preview",
        "Gemini 2.0 Flash",
        "DeepSeek R1"
    )
    var selectedModel by remember { mutableStateOf(models[0]) }
    var thinkingBudget by remember { mutableFloatStateOf(8000f) }

    Scaffold(
        containerColor = CocoaBgDark
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Dispatch Prompt",
                style = MaterialTheme.typography.headlineMedium,
                color = CocoaTextPrimary
            )
            Text(
                text = "Send complex coding tasks or instructions directly to your desktop workspace.",
                style = MaterialTheme.typography.bodyMedium,
                color = CocoaTextSecondary
            )

            // Prompt Editor
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                placeholder = { Text("What should Cocoa build, refactor, or debug next?", color = CocoaTextTertiary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clip(RoundedCornerShape(14.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CocoaAccent,
                    unfocusedBorderColor = CocoaCardBorder,
                    focusedContainerColor = CocoaSurfaceDark,
                    unfocusedContainerColor = CocoaSurfaceDark,
                    focusedTextColor = CocoaTextPrimary,
                    unfocusedTextColor = CocoaTextPrimary
                )
            )

            // Model Selection
            Text(
                text = "AI Engine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CocoaTextPrimary
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(models) { model ->
                    val isSelected = model == selectedModel
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) CocoaAccent else CocoaCardDark,
                        modifier = Modifier.clickable { selectedModel = model }
                    ) {
                        Text(
                            text = model,
                            color = if (isSelected) Color.White else CocoaTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Thinking Budget Slider (if thinking model)
            if (selectedModel.contains("Thinking") || selectedModel.contains("R1")) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = CocoaSurfaceDark
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thinking Budget",
                                style = MaterialTheme.typography.titleSmall,
                                color = CocoaTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(thinkingBudget / 1000).toInt()}k tokens",
                                color = CocoaAccentLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Slider(
                            value = thinkingBudget,
                            onValueChange = { thinkingBudget = it },
                            valueRange = 1000f..32000f,
                            steps = 30,
                            colors = SliderDefaults.colors(
                                thumbColor = CocoaAccent,
                                activeTrackColor = CocoaAccent,
                                inactiveTrackColor = CocoaCardDark
                            )
                        )
                    }
                }
            }

            // Quick Prompt Ideas / Templates
            Text(
                text = "Quick Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CocoaTextPrimary
            )

            val presets = listOf(
                "Run test suite and fix any failing tests",
                "Review git diff and check for security vulnerabilities",
                "Add full unit tests for the last modified component",
                "Clean up dependencies and optimize build config"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CocoaCardDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { promptText = preset }
                    ) {
                        Text(
                            text = "⚡ $preset",
                            color = CocoaTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Send Button
            Button(
                onClick = {
                    if (promptText.isNotBlank()) {
                        linkClient.sendPrompt(
                            prompt = promptText,
                            model = selectedModel,
                            thinkingBudget = thinkingBudget.toInt()
                        )
                        Toast.makeText(context, "Prompt sent to desktop workspace!", Toast.LENGTH_SHORT).show()
                        promptText = ""
                    }
                },
                enabled = promptText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CocoaAccent,
                    disabledContainerColor = CocoaCardDark
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = if (promptText.isNotBlank()) Color.White else CocoaTextTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Launch Agent on Desktop",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (promptText.isNotBlank()) Color.White else CocoaTextTertiary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
