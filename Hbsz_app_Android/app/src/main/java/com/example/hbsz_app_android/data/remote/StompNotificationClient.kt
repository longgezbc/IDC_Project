package com.example.hbsz_app_android.data.remote

import android.util.Log
import com.example.hbsz_app_android.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * 站内通知 STOMP over SockJS 轻量客户端（零第三方依赖）。
 *
 * 协议对接（与 Web 端 SockJS + @stomp/stompjs 行为一致，后端见 WebSocketConfig）：
 * - 传输层：SockJS 原生 WebSocket transport，URL `ws://<host>/ws/{server-id}/{session-id}/websocket`
 *   （Nginx `^~ /ws/` 已反代到后端 /api/ws/）；
 * - SockJS 帧：`o` 开启 → 可发应用数据；`a["<stomp-frame>",...]` 消息数组；
 *   `h` 心跳忽略；`c[code,"reason"]` 服务端关闭 → 走重连；
 * - STOMP 帧：CONNECT（Authorization 帧头鉴权）→ CONNECTED → SUBSCRIBE
 *   `/user/queue/notifications` → MESSAGE（body 为通知 JSON）；
 * - 鉴权：握手 HTTP Authorization 头 + STOMP CONNECT 帧头双重传递
 *   （后端帧头优先、握手回退），token 不出现在 URL。
 *
 * 保活：OkHttp WebSocket 协议层 ping（30s）；服务端 STOMP 心跳 \n 帧直接跳过。
 *
 * 重连：指数退避 5s→160s + 随机抖动 0~2s（对齐 Web 端 L6 策略并防集中重连），
 * 最多 10 次；连接成功后重置计数，给后端故障更长的恢复窗口。
 * 重连成功（CONNECTED）时通过 onSubscribed 回调通知上层做未读数对账（gap detection）。
 *
 * 线程模型：OkHttp 回调线程 + 定时线程池，全部状态经 lock 保护；
 * 消息通过 [NotificationListener] 回调抛给上层（NotificationCenter）。
 */
@Singleton
class StompNotificationClient @Inject constructor() {

    companion object {
        private const val TAG = "StompClient"
        private const val SUBSCRIBE_DESTINATION = "/user/queue/notifications"
        private val RECONNECT_INTERVALS_MS =
            longArrayOf(5_000, 10_000, 20_000, 40_000, 80_000, 160_000)
        private const val MAX_RECONNECT = 10
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 独立 WebSocket 客户端：协议层 ping 保活；不挂业务拦截器（token 由本类显式携带） */
    private val wsClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "stomp-reconnect").apply { isDaemon = true }
        }

    /** 通知消息监听器（消息 body 为通知 JSON 字符串） */
    fun interface NotificationListener {
        fun onNotification(body: String)
    }

    private val lock = Any()
    private var socket: WebSocket? = null
    private var activeToken: String? = null
    private var subscribed = false
    private var manualStop = false
    private var reconnectAttempts = 0
    private var reconnectTask: ScheduledFuture<*>? = null
    private var listener: NotificationListener? = null

    /** 订阅完成（含每次重连成功）回调（OkHttp 回调线程触发，上层自行切线程） */
    @Volatile
    private var onSubscribedCallback: (() -> Unit)? = null

    /**
     * 建立连接（幂等）。
     * - 同 token 且连接存活：直接返回；
     * - 不同 token（换号登录）：先断开旧连接再重连；
     * - 未订阅成功前断线由内部自动重连（指数退避 + 抖动），成功后回调重置。
     *
     * @param token JWT 访问令牌
     * @param messageListener 通知消息回调（在 OkHttp 回调线程触发，上层自行切线程）
     * @param onSubscribed STOMP 订阅完成回调（首连与每次重连成功均触发，
     *        供上层做断线期间漏收消息的对账刷新，即 gap detection）
     */
    fun start(
        token: String,
        messageListener: NotificationListener,
        onSubscribed: (() -> Unit)? = null
    ) {
        synchronized(lock) {
            listener = messageListener
            onSubscribedCallback = onSubscribed
            manualStop = false
            if (socket != null && activeToken == token && subscribed) {
                return // 已连接且同 token：复用，仅更新监听器
            }
            closeSocketLocked("restart")
            activeToken = token
            subscribed = false
            reconnectAttempts = 0
            openSocketLocked(token)
            Log.i(TAG, "STOMP 连接发起")
        }
    }

    /** 主动断开并停止重连（登出时调用） */
    fun stop() {
        synchronized(lock) {
            manualStop = true
            reconnectTask?.cancel(false)
            reconnectTask = null
            closeSocketLocked("manual stop")
            activeToken = null
            listener = null
            onSubscribedCallback = null
            Log.i(TAG, "STOMP 连接已断开（主动）")
        }
    }

    /** 当前是否完成 STOMP 握手并处于订阅状态 */
    val isSubscribed: Boolean
        get() = synchronized(lock) { subscribed }

    // ==================================================================
    // 连接与重连
    // ==================================================================

    /** 发起 SockJS WebSocket 连接（须持锁调用） */
    private fun openSocketLocked(token: String) {
        val request = Request.Builder()
            .url(buildSockJsUrl())
            .header("Authorization", "Bearer $token")
            .build()
        socket = wsClient.newWebSocket(request, StompListener(token))
    }

    /**
     * SockJS WebSocket transport URL。
     * 从 API_BASE_URL（http://host/api/）推导通知服务地址（ws://host/ws/），
     * server-id 固定 "000"、session-id 随机 UUID（SockJS 规范：客户端生成，服务端不校验）。
     */
    private fun buildSockJsUrl(): String {
        val base = BuildConfig.API_BASE_URL // 形如 http://106.53.54.22/api/
        val wsScheme = if (base.startsWith("https")) "wss" else "ws"
        val host = base.removePrefix("http://").removePrefix("https://").substringBefore('/')
        return "$wsScheme://$host/ws/000/${UUID.randomUUID()}/websocket"
    }

    /** 安排重连（须持锁调用；主动停止或已达上限时跳过） */
    private fun scheduleReconnectLocked(token: String) {
        if (manualStop) return
        if (reconnectAttempts >= MAX_RECONNECT) {
            Log.w(TAG, "已达最大重连次数（$MAX_RECONNECT），停止重连")
            return
        }
        // 指数退避 + 随机抖动（0~2s）：避免多设备在同一时刻集中重连造成服务端瞬时压力
        val base = RECONNECT_INTERVALS_MS[min(reconnectAttempts, RECONNECT_INTERVALS_MS.size - 1)]
        val interval = base + Random.nextLong(0, 2_000)
        reconnectAttempts++
        reconnectTask?.cancel(false)
        reconnectTask = scheduler.schedule({
            synchronized(lock) {
                if (manualStop) return@synchronized
                Log.i(TAG, "第 $reconnectAttempts 次重连…")
                openSocketLocked(token)
            }
        }, interval, TimeUnit.MILLISECONDS)
    }

    /** 关闭当前 socket 并复位订阅标记（须持锁调用） */
    private fun closeSocketLocked(reason: String) {
        socket?.close(1000, reason)
        socket = null
        subscribed = false
    }

    // ==================================================================
    // SockJS / STOMP 帧处理
    // ==================================================================

    private inner class StompListener(private val token: String) : WebSocketListener() {

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(lock) {
                if (socket !== webSocket) return // 旧连接的迟到回调，忽略
                subscribed = false
                socket = null
                Log.w(TAG, "连接失败: ${t.message}")
                scheduleReconnectLocked(token)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(lock) {
                if (socket !== webSocket) return
                subscribed = false
                socket = null
                Log.i(TAG, "连接关闭: code=$code")
                scheduleReconnectLocked(token)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "o") {
                // SockJS 开启帧：发送 STOMP CONNECT（Authorization 帧头鉴权，token 不进 URL）
                webSocket.send(sockJsWrap(buildConnectFrame(token)))
                return
            }
            val messages: List<String> = synchronized(lock) {
                if (socket !== webSocket) return
                parseSockJsPayload(text) ?: run {
                    // `c` 关闭帧：立即断开本连接，由 onClosed 走重连
                    webSocket.close(1000, "sockjs close frame")
                    return
                }
            }
            for (message in messages) {
                handleStompMessage(webSocket, message)
            }
        }
    }

    /** 构建 STOMP CONNECT 帧（heart-beat 0,0：保活交给 OkHttp 协议层 ping） */
    private fun buildConnectFrame(token: String): String =
        "CONNECT\naccept-version:1.2\nhost:host\nAuthorization:Bearer $token\nheart-beat:0,0\n\n\u0000"

    /**
     * 解析 SockJS 数据帧。
     * @return 应用消息列表；`o`/`h`/未知帧返回空列表；`c`（关闭帧）返回 null
     */
    private fun parseSockJsPayload(text: String): List<String>? {
        return when {
            text == "o" || text == "h" -> emptyList()
            text.startsWith("a") -> runCatching {
                (json.parseToJsonElement(text.substring(1)) as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
            }.getOrNull() ?: emptyList()
            text.startsWith("c") -> null
            else -> emptyList()
        }
    }

    /** 处理单条 STOMP 应用消息 */
    private fun handleStompMessage(webSocket: WebSocket, message: String) {
        if (message.isBlank()) return // STOMP 心跳 \n
        val frame = parseStompFrame(message) ?: return

        when (frame.command) {
            "CONNECTED" -> {
                val callback = synchronized(lock) {
                    subscribed = true
                    reconnectAttempts = 0
                    // 订阅个人通知通道（对齐 Web 端 destination）
                    webSocket.send(sockJsWrap(
                        "SUBSCRIBE\nid:sub-0\ndestination:$SUBSCRIBE_DESTINATION\n\n\u0000"
                    ))
                    Log.i(TAG, "STOMP 握手完成，已订阅通知通道")
                    onSubscribedCallback
                }
                // 锁外触发对账回调（gap detection：重连后上层刷新未读数补齐断线漏收），
                // 避免持锁调用上层代码引入死锁风险
                callback?.let { cb ->
                    runCatching(cb).onFailure {
                        Log.w(TAG, "onSubscribed 回调异常: ${it.message}")
                    }
                }
            }
            "MESSAGE" -> frame.body?.takeIf { it.isNotBlank() }?.let { body ->
                listener?.let { l ->
                    runCatching { l.onNotification(body) }
                        .onFailure { Log.w(TAG, "通知消息回调异常: ${it.message}") }
                }
            }
            "ERROR" -> Log.w(TAG, "STOMP 协议错误: ${frame.headers["message"]}")
            else -> Unit // RECEIPT 等忽略
        }
    }

    /** SockJS 消息包装：应用数据以 JSON 字符串数组发送（SockJS 帧协议） */
    private fun sockJsWrap(stompFrame: String): String {
        val sb = StringBuilder("[\"")
        for (ch in stompFrame) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u0000' -> sb.append("\\u0000")
                else ->
                    if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append("\"]")
        return sb.toString()
    }
}

/** STOMP 帧解析结果 */
private data class StompFrame(
    val command: String,
    val headers: Map<String, String>,
    val body: String?
)

/** 解析 STOMP 帧：命令行 + 头行们 + 空行 + body + U+0000 结束符 */
private fun parseStompFrame(frame: String): StompFrame? {
    val nullIndex = frame.indexOf('\u0000')
    if (nullIndex < 0) return null
    val lines = frame.substring(0, nullIndex).split('\n')
    if (lines.isEmpty() || lines[0].isBlank()) return null

    val command = lines[0].trim()
    val headers = mutableMapOf<String, String>()
    var i = 1
    while (i < lines.size && lines[i].isNotEmpty()) {
        val idx = lines[i].indexOf(':')
        if (idx > 0) headers[lines[i].substring(0, idx)] = lines[i].substring(idx + 1)
        i++
    }
    // 空行之后全部为 body（可能为空）
    val body = if (i + 1 < lines.size) {
        lines.subList(i + 1, lines.size).joinToString("\n")
    } else null
    return StompFrame(command, headers, body?.takeIf { it.isNotBlank() })
}
