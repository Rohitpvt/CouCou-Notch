package com.rohit.cocoa.network

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.rohit.cocoa.model.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DiscoveredHost(
    val name: String,
    val host: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

class CocoaLinkClient(private val context: Context) {

    private val tag = "CocoaLinkClient"
    private val prefs: SharedPreferences = context.getSharedPreferences("cocoa_link_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingIntervalMillis = 15_000
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State Flows
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeSession = MutableStateFlow<AgentSession?>(null)
    val activeSession: StateFlow<AgentSession?> = _activeSession.asStateFlow()

    private val _approvals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val approvals: StateFlow<List<ApprovalRequest>> = _approvals.asStateFlow()

    private val _questions = MutableStateFlow<List<QuestionRequest>>(emptyList())
    val questions: StateFlow<List<QuestionRequest>> = _questions.asStateFlow()

    private val _services = MutableStateFlow<List<ServiceStatus>>(emptyList())
    val services: StateFlow<List<ServiceStatus>> = _services.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredHost>> = _discoveredDevices.asStateFlow()

    private var activeSessionWs: DefaultClientWebSocketSession? = null
    private var reconnectJob: Job? = null
    private var isIntentionalDisconnect = false

    // mDNS Discovery
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val serviceType = "_cocoa-companion._tcp."

    init {
        // Load saved device settings and optionally autoconnect
        val savedHost = getSavedHost()
        val savedPort = getSavedPort()
        val savedToken = getSavedToken()

        if (savedHost.isNotEmpty() && savedPort > 0) {
            connect(savedHost, savedPort, savedToken)
        }
    }

    fun getSavedHost(): String = prefs.getString("host", "192.168.1.100") ?: ""
    fun getSavedPort(): Int = prefs.getInt("port", 47910)
    fun getSavedToken(): String = prefs.getString("auth_token", "default_cocoa_key") ?: ""

    fun saveConnectionInfo(host: String, port: Int, token: String) {
        prefs.edit()
            .putString("host", host)
            .putInt("port", port)
            .putString("auth_token", token)
            .apply()
    }

    fun connect(host: String, port: Int, token: String) {
        saveConnectionInfo(host, port, token)
        isIntentionalDisconnect = false
        reconnectJob?.cancel()

        scope.launch {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            try {
                val url = "ws://$host:$port/ws"
                Log.d(tag, "Connecting to Cocoa Desktop at $url")

                client.webSocket(host = host, port = port, path = "/ws") {
                    activeSessionWs = this
                    _connectionStatus.value = ConnectionStatus.AUTHENTICATING

                    // Send Auth handshake
                    val authMsg = """{"type":"AUTH","token":"$token","deviceName":"${Build.MODEL} (Android)","os":"Android ${Build.VERSION.RELEASE}"}"""
                    send(Frame.Text(authMsg))

                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    Log.d(tag, "Successfully connected to Cocoa Desktop!")

                    // Listen for incoming frames
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            handleIncomingMessage(text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "WebSocket connection error", e)
                _connectionStatus.value = ConnectionStatus.ERROR
            } finally {
                activeSessionWs = null
                if (!isIntentionalDisconnect) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    scheduleReconnect(host, port, token)
                }
            }
        }
    }

    private fun scheduleReconnect(host: String, port: Int, token: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(4000)
            if (!isIntentionalDisconnect && _connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                Log.d(tag, "Attempting reconnect...")
                connect(host, port, token)
            }
        }
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        scope.launch {
            try {
                activeSessionWs?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            } catch (ignored: Exception) {
            } finally {
                activeSessionWs = null
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun handleIncomingMessage(payload: String) {
        try {
            val root = json.parseToJsonElement(payload).jsonObject
            val type = root["type"]?.jsonPrimitive?.content ?: return

            when (type) {
                "SESSION_SYNC", "SESSION_UPDATE" -> {
                    val sessionJson = root["session"]?.toString()
                    if (sessionJson != null && sessionJson != "null") {
                        val session = json.decodeFromString<AgentSession>(sessionJson)
                        _activeSession.value = session
                    } else {
                        _activeSession.value = null
                    }
                }
                "STEP_ADD", "STEP_UPDATE" -> {
                    val stepJson = root["step"]?.toString() ?: return
                    val step = json.decodeFromString<AgentStep>(stepJson)
                    val current = _activeSession.value
                    if (current != null) {
                        val updatedSteps = (current.steps.filter { it.id != step.id } + step).sortedBy { it.timestamp }
                        _activeSession.value = current.copy(steps = updatedSteps)
                    }
                }
                "APPROVALS_SYNC" -> {
                    val listJson = root["approvals"]?.toString() ?: "[]"
                    val list = json.decodeFromString<List<ApprovalRequest>>(listJson)
                    _approvals.value = list
                }
                "QUESTIONS_SYNC" -> {
                    val listJson = root["questions"]?.toString() ?: "[]"
                    val list = json.decodeFromString<List<QuestionRequest>>(listJson)
                    _questions.value = list
                }
                "SERVICES_SYNC" -> {
                    val listJson = root["services"]?.toString() ?: "[]"
                    val list = json.decodeFromString<List<ServiceStatus>>(listJson)
                    _services.value = list
                }
                "PING" -> {
                    sendMessage("""{"type":"PONG","timestamp":${System.currentTimeMillis()}}""")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing message: $payload", e)
        }
    }

    private fun sendMessage(msg: String) {
        scope.launch {
            try {
                activeSessionWs?.send(Frame.Text(msg))
            } catch (e: Exception) {
                Log.e(tag, "Failed to send message: $msg", e)
            }
        }
    }

    fun sendApproval(id: String, approved: Boolean, reason: String? = null) {
        // Remove from local queue immediately for instant UI feedback
        _approvals.value = _approvals.value.filter { it.id != id }

        val reasonEscaped = reason?.let { "\"$it\"" } ?: "null"
        sendMessage("""{"type":"APPROVAL_DECISION","id":"$id","approved":$approved,"reason":$reasonEscaped}""")
    }

    fun sendAnswer(id: String, selectedOptions: List<String>, customAnswer: String? = null) {
        // Remove from local queue immediately
        _questions.value = _questions.value.filter { it.id != id }

        val optionsJson = json.encodeToString(selectedOptions)
        val customEscaped = customAnswer?.let { "\"$it\"" } ?: "null"
        sendMessage("""{"type":"QUESTION_ANSWER","id":"$id","selectedOptionIds":$optionsJson,"customAnswer":$customEscaped}""")
    }

    fun sendPrompt(prompt: String, model: String, thinkingBudget: Int = 0) {
        val promptEscaped = Json.encodeToString(prompt)
        sendMessage("""{"type":"SEND_PROMPT","prompt":$promptEscaped,"model":"$model","thinkingBudget":$thinkingBudget}""")
    }

    fun sendServiceAction(serviceId: String, action: String) {
        sendMessage("""{"type":"SERVICE_COMMAND","serviceId":"$serviceId","action":"$action"}""")
    }

    // --- mDNS Zero-Config Discovery ---
    fun startMdnsDiscovery() {
        if (discoveryListener != null) return
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "mDNS Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "mDNS Service found: ${service.serviceName}")
                try {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(tag, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            val port = serviceInfo.port
                            val discovered = DiscoveredHost(serviceInfo.serviceName, host, port)
                            val current = _discoveredDevices.value.filter { it.host != host }
                            _discoveredDevices.value = current + discovered
                        }
                    })
                } catch (e: Exception) {
                    Log.e(tag, "Error resolving service", e)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "mDNS Service lost: ${service.serviceName}")
                _discoveredDevices.value = _discoveredDevices.value.filter { it.name != service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "mDNS Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "mDNS Discovery start failed: $errorCode")
                stopMdnsDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "mDNS Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error starting discoverServices", e)
        }
    }

    fun stopMdnsDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (ignored: Exception) {
            }
            discoveryListener = null
        }
    }
}
