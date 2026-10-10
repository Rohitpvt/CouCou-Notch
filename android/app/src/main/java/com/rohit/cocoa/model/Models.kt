package com.rohit.cocoa.model

import kotlinx.serialization.Serializable

@Serializable
enum class MochiMood {
    IDLE,
    HAPPY,
    THINKING,
    CODING,
    SLEEPING,
    WARNING,
    ERROR,
    CELEBRATING
}

@Serializable
enum class StepType {
    READ,
    EDIT,
    WRITE,
    BASH,
    SEARCH,
    THINK,
    BROWSER,
    SUBAGENT,
    QUESTION,
    GENERATE_IMAGE,
    INFO
}

@Serializable
enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    WAITING_APPROVAL
}

@Serializable
data class AgentStep(
    val id: String,
    val type: StepType,
    val title: String,
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: StepStatus = StepStatus.SUCCESS,
    val durationMs: Long = 0,
    val targetFile: String? = null,
    val command: String? = null,
    val diff: String? = null
)

@Serializable
data class AgentSession(
    val sessionId: String,
    val taskTitle: String,
    val prompt: String,
    val activeModel: String = "Claude 3.7 Sonnet (Thinking)",
    val mood: MochiMood = MochiMood.THINKING,
    val currentAction: String = "Analyzing workspace...",
    val steps: List<AgentStep> = emptyList(),
    val isRunning: Boolean = true,
    val startedAt: Long = System.currentTimeMillis(),
    val tokenUsage: TokenUsage = TokenUsage(),
    val costEstimateUsd: Double = 0.0
)

@Serializable
data class TokenUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0
)

@Serializable
data class ApprovalRequest(
    val id: String,
    val type: String, // "BASH", "FILE_OVERWRITE", "BROWSER_NAVIGATE", "SYSTEM"
    val title: String,
    val description: String,
    val command: String? = null,
    val targetFile: String? = null,
    val diff: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val requiresBiometrics: Boolean = false
)

@Serializable
data class QuestionOption(
    val id: String,
    val label: String,
    val description: String? = null
)

@Serializable
data class QuestionRequest(
    val id: String,
    val question: String,
    val context: String? = null,
    val options: List<QuestionOption> = emptyList(),
    val isMultiSelect: Boolean = false,
    val allowCustomAnswer: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ServiceStatus(
    val id: String,
    val name: String,
    val type: String, // "VITE", "RUST", "DOCKER", "DATABASE", "API"
    val isRunning: Boolean,
    val port: Int? = null,
    val url: String? = null,
    val cpuUsagePct: Float = 0f,
    val memoryUsageMb: Long = 0,
    val uptimeSeconds: Long = 0,
    val logs: List<String> = emptyList()
)

@Serializable
sealed class CocoaSocketMessage {
    @Serializable
    data class AuthRequest(val token: String, val deviceName: String, val os: String = "Android") : CocoaSocketMessage()
    
    @Serializable
    data class AuthResponse(val success: Boolean, val message: String = "") : CocoaSocketMessage()

    @Serializable
    data class SessionSync(val session: AgentSession?) : CocoaSocketMessage()

    @Serializable
    data class StepUpdate(val step: AgentStep) : CocoaSocketMessage()

    @Serializable
    data class ApprovalQueueUpdate(val approvals: List<ApprovalRequest>) : CocoaSocketMessage()

    @Serializable
    data class QuestionQueueUpdate(val questions: List<QuestionRequest>) : CocoaSocketMessage()

    @Serializable
    data class ServicesSync(val services: List<ServiceStatus>) : CocoaSocketMessage()

    @Serializable
    data class ApprovalDecision(val id: String, val approved: Boolean, val reason: String? = null) : CocoaSocketMessage()

    @Serializable
    data class QuestionAnswer(val id: String, val selectedOptionIds: List<String>, val customAnswer: String? = null) : CocoaSocketMessage()

    @Serializable
    data class SendPrompt(val prompt: String, val model: String, val thinkingBudget: Int = 0) : CocoaSocketMessage()

    @Serializable
    data class ServiceCommand(val serviceId: String, val action: String) : CocoaSocketMessage() // "START", "STOP", "RESTART"
    
    @Serializable
    data class Ping(val timestamp: Long) : CocoaSocketMessage()

    @Serializable
    data class Pong(val timestamp: Long) : CocoaSocketMessage()
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}
