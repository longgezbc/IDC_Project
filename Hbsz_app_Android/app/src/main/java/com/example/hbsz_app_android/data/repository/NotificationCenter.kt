package com.example.hbsz_app_android.data.repository

import android.util.Log
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.local.UserPreferences
import com.example.hbsz_app_android.data.remote.StompNotificationClient
import com.example.hbsz_app_android.data.remote.dto.NotificationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局通知中枢（通知触达专项核心）。
 *
 * 职责：
 * 1. **生命周期接线**：监听 DataStore tokenFlow（distinctUntilChanged）——
 *    登录（token 出现）自动建立 WebSocket 订阅并拉取初始未读数；
 *    登出（token 清空）自动断开连接并清零未读角标；
 * 2. **未读角标**：[unreadCount] 供底部导航 BadgedBox 订阅；
 *    实时消息到达 +1，消息页加载/已读操作后调 [refreshUnreadCount] 以服务端为准重算；
 * 3. **实时消息广播**：[incomingNotifications] 供消息中心页订阅，
 *    新通知到达时实时插入列表头部（免轮询刷新）。
 *
 * 消息解析失败静默忽略（协议演进容错），不影响未读数与其他订阅者。
 */
@Singleton
class NotificationCenter @Inject constructor(
    private val stompClient: StompNotificationClient,
    private val notificationRepository: NotificationRepository,
    userPreferences: UserPreferences,
    private val json: Json
) {

    companion object {
        private const val TAG = "NotificationCenter"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _unreadCount = MutableStateFlow(0)
    /** 全局未读通知数（底部导航角标数据源） */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _incomingNotifications = MutableSharedFlow<NotificationDto>(extraBufferCapacity = 16)
    /** 实时到达的新通知（消息中心页订阅以实现免刷新插入） */
    val incomingNotifications: SharedFlow<NotificationDto> = _incomingNotifications.asSharedFlow()

    init {
        // token 生命周期：登录建连 / 登出断连（AppViewModel 的登录态切换同源驱动）
        scope.launch {
            userPreferences.tokenFlow.distinctUntilChanged().collect { token ->
                if (token.isNullOrBlank()) {
                    stompClient.stop()
                    _unreadCount.value = 0
                } else {
                    stompClient.start(
                        token,
                        messageListener = ::onNotificationBody,
                        onSubscribed = ::onSubscribed
                    )
                    refreshUnreadCount()
                }
            }
        }
    }

    /**
     * STOMP 订阅完成回调（首连与每次重连成功均触发）。
     * gap detection：断线期间漏收的推送不会重放，此处以服务端未读数对账补齐。
     */
    private fun onSubscribed() {
        refreshUnreadCount()
    }

    /** WebSocket 通知消息回调（OkHttp 回调线程 → 解析后分发） */
    private fun onNotificationBody(body: String) {
        val dto = runCatching {
            json.decodeFromString(NotificationDto.serializer(), body)
        }.getOrElse {
            Log.w(TAG, "通知消息解析失败: ${it.message}")
            return
        }
        // 未读 +1（服务端已落库，本地乐观累加；消息页加载后会以服务端计数校准）
        _unreadCount.value += 1
        if (!_incomingNotifications.tryEmit(dto)) {
            Log.w(TAG, "实时通知广播缓冲已满，消息页将靠刷新兜底: id=${dto.id}")
        }
    }

    /** 以服务端为准重算未读数（消息页加载完成 / 标记已读后调用） */
    fun refreshUnreadCount() {
        scope.launch {
            when (val res = notificationRepository.getUnreadCount()) {
                is Resource.Success -> _unreadCount.value = res.data
                is Resource.Error -> Log.w(TAG, "未读数刷新失败: ${res.message}")
                Resource.Loading -> Unit
            }
        }
    }

    /** 本地即时减一（单条已读成功后的快速反馈，随后可由 refreshUnreadCount 校准） */
    fun decrementUnread() {
        if (_unreadCount.value > 0) {
            _unreadCount.value -= 1
        }
    }
}
