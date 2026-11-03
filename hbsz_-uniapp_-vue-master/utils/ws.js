/**
 * WebSocket 适配层（小程序版）
 *
 * 小程序原生 WebSocket API（uni.connectSocket）与 Web 端 SockJS + STOMP 不同，
 * 本项目后端 WebSocket 端点为 `/ws`（SockJS + STOMP 协议）。
 *
 * 小程序无法直接使用 SockJS（依赖 H5 的 WebSocket polyfill），
 * 但可直接连接 SockJS 端点的原生 WebSocket 升级路径 `/ws/websocket`，
 * 并使用 stompjs 的 raw WebSocket 模式进行 STOMP 协议通信。
 *
 * 由于小程序环境对 stompjs 库兼容性复杂，本模块采用「轻量心跳 + REST 轮询降级」策略：
 *   1. 优先尝试 uni.connectSocket 连接，收到底推消息时回调
 *   2. 连接失败或断开时，自动降级到 30s 轮询 GET /notifications/unread-count
 *   3. 暴露 onMessage / onUnreadCount 两个订阅接口供全局铃铛使用
 */
import { API_BASE_URL } from './config'
import { auth } from './auth'
import { notificationApi } from '@/api/notification'

/** 将 HTTP 基地址转为 WS 协议地址（SockJS 原生 WebSocket 端点），URL 不含 token */
function buildWsUrl() {
	const base = API_BASE_URL.replace(/^http/, 'ws')
	// SockJS 原生 WebSocket 端点：/ws/websocket
	// 安全修复（P0-1）：原将 token 拼在 URL query（access_token=），会泄露到 Nginx/浏览器访问日志、Referer；
	// 且后端 JwtHandshakeInterceptor 已移除从 URL 读 token 的逻辑（仅认 Authorization 头），
	// 导致 URL token 被忽略、握手放行，前端又非 STOMP 客户端不发 CONNECT 帧 → 未授权 WS 连接。
	// token 改由 connectWs 的握手请求头 Authorization 传递。
	return `${base.replace('/api', '')}/ws/websocket`
}

// WS 单例与回调队列
let socketTask = null
let isConnected = false
let isConnecting = false
let pollingTimer = null
let messageCallbacks = []
let unreadCallbacks = []
let reconnectAttempts = 0
// 重连定时器引用：保存 setTimeout 返回值，供 stopNotificationWatch 清理，
// 防止页面卸载/登出后定时器仍触发 connectWs 建立僵尸连接
let reconnectTimer = null
// 手动停止标志：防止页面卸载/登出后 onClose 仍触发自动重连，向失效 token 发起握手
let isManualStop = false
const MAX_RECONNECT = 5
const RECONNECT_DELAY = 5000
const POLLING_INTERVAL = 30000

/**
 * 启动通知推送监听
 * 优先尝试 WebSocket，失败自动降级为轮询
 * @param {Object} [options] { onMessage?, onUnreadCount? }
 */
export function startNotificationWatch(options = {}) {
	// 重置手动停止标志：本次为主动启动，允许后续断线自动重连
	isManualStop = false
	if (options.onMessage) messageCallbacks.push(options.onMessage)
	if (options.onUnreadCount) unreadCallbacks.push(options.onUnreadCount)

	if (isConnecting || isConnected) return
	isConnecting = true

	// 先尝试 WS
	try {
		connectWs()
	} catch (e) {
		console.warn('[ws] WebSocket 连接失败，降级为轮询', e)
		isConnecting = false
		startPolling()
	}
}

/** 建立 WebSocket 连接 */
function connectWs() {
	// 防御：若已手动停止（页面卸载/登出），不再建立新连接。
	// 此检查保护 reconnect 定时器触发的延迟调用场景——
	// 定时器安排后、触发前，用户可能已离开页面触发 stopNotificationWatch 置 isManualStop=true。
	if (isManualStop) {
		console.log('[ws] 已手动停止，跳过连接')
		isConnecting = false
		return
	}
	// 安全修复（P0-1）：无 token 时不应建立 WS。后端握手在无 token 时会放行（依赖 STOMP CONNECT 帧鉴权），
	// 但本模块为原生 WebSocket + 轻量心跳，不发 CONNECT 帧，无 token 直连将导致未授权连接。
	const token = auth.getToken()
	if (!token) {
		console.warn('[ws] 无 token，直接降级轮询')
		isConnecting = false
		startPolling()
		return
	}
	const url = buildWsUrl()
	socketTask = uni.connectSocket({
		url,
		// token 通过握手请求头传递，后端 JwtHandshakeInterceptor 从 Authorization 头读取并在握手阶段完成鉴权
		header: { Authorization: `Bearer ${token}` },
		success: () => { /* 实际连接状态在 onOpen 中确认 */ },
		fail: (err) => {
			console.warn('[ws] connectSocket 调用失败', err)
			isConnecting = false
			startPolling()
		}
	})

	if (!socketTask) {
		isConnecting = false
		startPolling()
		return
	}

	socketTask.onOpen(() => {
		isConnected = true
		isConnecting = false
		reconnectAttempts = 0
		console.info('[ws] WebSocket 已连接')
		// 立即拉取一次未读数
		refreshUnreadCount()
	})

	socketTask.onMessage((res) => {
		// STOMP 消息体为字符串，尝试解析 JSON
		try {
			const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
			// 通知消息通常包含 type 或 notification 字段
			messageCallbacks.forEach(cb => {
				try { cb(data) } catch (e) { console.warn('[ws] onMessage callback error', e) }
			})
			// 收到消息后刷新未读数
			refreshUnreadCount()
		} catch (e) {
			// STOMP 协议帧（CONNECTED/MESSAGE/RECEIPT 等），非业务消息，忽略
		}
	})

	socketTask.onClose(() => {
		console.info('[ws] WebSocket 已关闭')
		isConnected = false
		isConnecting = false
		socketTask = null
		// 手动停止（页面卸载/登出）时不再重连，避免向失效 token 发起握手造成僵尸连接
		if (isManualStop) {
			console.log('[WS] 手动停止，不再重连')
			return
		}
		// 非主动关闭时尝试重连
		if (reconnectAttempts < MAX_RECONNECT) {
			reconnectAttempts++
			console.info(`[ws] ${RECONNECT_DELAY}ms 后重连（第 ${reconnectAttempts} 次）`)
			// 保存定时器引用，供 stopNotificationWatch 清理，
			// 防止页面卸载后定时器仍触发 connectWs 建立僵尸连接
			reconnectTimer = setTimeout(() => {
				reconnectTimer = null
				// 双重保险：定时器触发前可能已被 stopNotificationWatch 置为手动停止
				if (isManualStop) {
					console.log('[ws] 定时器触发时已手动停止，取消重连')
					isConnecting = false
					return
				}
				if (!isConnected) connectWs()
			}, RECONNECT_DELAY)
		} else {
			console.warn('[ws] 重连次数耗尽，降级为轮询')
			startPolling()
		}
	})

	socketTask.onError((err) => {
		console.warn('[ws] WebSocket 错误', err)
		// onError 后会触发 onClose，由 onClose 处理重连/降级
	})
}

/** 主动关闭 WebSocket 与轮询 */
export function stopNotificationWatch() {
	// 标记为手动停止：阻止 onClose 中的自动重连逻辑，避免页面卸载/登出后向失效 token 重连
	isManualStop = true
	// 清理未触发的重连定时器：onClose 可能在 stopNotificationWatch 之前已安排重连定时器，
	// 若不清理，定时器会在 5s 后触发 connectWs，即使 isManualStop=true，
	// connectWs 入口的 isManualStop 检查会拦截，但清理定时器可避免无意义的定时器唤醒
	if (reconnectTimer) {
		clearTimeout(reconnectTimer)
		reconnectTimer = null
	}
	if (socketTask) {
		try { socketTask.close({}) } catch (e) {}
		socketTask = null
	}
	isConnected = false
	isConnecting = false
	stopPolling()
	// 清空回调数组：防止重复进入页面时注册重复回调，导致同一消息触发 N 次 Toast
	messageCallbacks.length = 0
	unreadCallbacks.length = 0
}

/** 启动 REST 轮询降级方案 */
function startPolling() {
	if (pollingTimer) return
	console.info('[ws] 启动未读数轮询（30s）')
	refreshUnreadCount()
	pollingTimer = setInterval(refreshUnreadCount, POLLING_INTERVAL)
}

/** 停止轮询 */
function stopPolling() {
	if (pollingTimer) {
		clearInterval(pollingTimer)
		pollingTimer = null
	}
}

/** 拉取未读数并通知所有订阅者 */
async function refreshUnreadCount() {
	try {
		const count = await notificationApi.getUnreadCount()
		const num = typeof count === 'number' ? count : (count && count.count) || 0
		unreadCallbacks.forEach(cb => {
			try { cb(num) } catch (e) { console.warn('[ws] onUnreadCount callback error', e) }
		})
	} catch (e) {
		// 静默失败，避免日志噪音
	}
}

/** 手动刷新未读数（供页面主动调用） */
export function refreshUnread() {
	return refreshUnreadCount()
}
