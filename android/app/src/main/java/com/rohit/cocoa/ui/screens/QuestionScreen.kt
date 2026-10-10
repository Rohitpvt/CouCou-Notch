package com.rohit.cocoa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.cocoa.model.QuestionRequest
import com.rohit.cocoa.network.CocoaLinkClient
import com.rohit.cocoa.ui.components.GlassCard
import com.rohit.cocoa.ui.theme.*

@Composable
fun QuestionScreen(
    linkClient: CocoaLinkClient,
    modifier: Modifier = Modifier
) {
    val questions by linkClient.questions.collectAsState()

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
                    text = "Agent Questions",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CocoaTextPrimary
                )
                Text(
                    text = "Provide answers or select options to guide the agent when multiple choices or clarifications are needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocoaTextSecondary
                )
            }

            if (questions.isEmpty()) {
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
                            Text(text = "💬", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No questions waiting",
                                style = MaterialTheme.typography.titleLarge,
                                color = CocoaTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "When Cocoa needs clarification or your feedback on architectural decisions, the prompt will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CocoaTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(questions, key = { it.id }) { req ->
                    QuestionCard(
                        request = req,
                        onSubmit = { selected, custom ->
                            linkClient.sendAnswer(req.id, selected, custom)
                        }
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
fun QuestionCard(
    request: QuestionRequest,
    onSubmit: (List<String>, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedOptions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customText by remember { mutableStateOf("") }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CocoaSurfaceDark,
        borderColor = CocoaAccent.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = request.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CocoaTextPrimary
            )

            if (!request.context.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = request.context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocoaTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option selection chips
            if (request.options.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    request.options.forEach { option ->
                        val isSelected = selectedOptions.contains(option.id)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) CocoaAccent.copy(alpha = 0.25f) else CocoaCardDark,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CocoaAccent) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedOptions = if (request.isMultiSelect) {
                                        if (isSelected) selectedOptions - option.id else selectedOptions + option.id
                                    } else {
                                        setOf(option.id)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "●" else "○",
                                    color = if (isSelected) CocoaAccentLight else CocoaTextTertiary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) CocoaTextPrimary else CocoaTextSecondary
                                    )
                                    if (!option.description.isNullOrEmpty()) {
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CocoaTextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Custom Text Input if allowed
            if (request.allowCustomAnswer) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("Write additional notes or custom response...", color = CocoaTextTertiary, fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CocoaAccent,
                        unfocusedBorderColor = CocoaCardBorder,
                        focusedTextColor = CocoaTextPrimary,
                        unfocusedTextColor = CocoaTextPrimary,
                        focusedContainerColor = CocoaCardDark,
                        unfocusedContainerColor = CocoaCardDark
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Submit Button
            val canSubmit = selectedOptions.isNotEmpty() || customText.isNotBlank()

            Button(
                onClick = {
                    onSubmit(selectedOptions.toList(), customText.ifBlank { null })
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CocoaAccent,
                    disabledContainerColor = CocoaCardDark
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = if (canSubmit) Color.White else CocoaTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Submit Answer",
                    color = if (canSubmit) Color.White else CocoaTextTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
