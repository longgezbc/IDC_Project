/**
 * WebSocket 通知客户端
 *
 * 基于 SockJS + STOMP 实现站内通知的实时推送。
 * 连接流程：
 *   1. 用户登录后，调用 connect(token) 建立 SockJS 连接并完成 STOMP 握手；
 *   2. 握手成功后订阅 /user/queue/notifications 接收个人通知；
 *   3. 收到通知时调用 onMessage 回调通知上层（store）；
 *   4. 用户登出或 token 失效时调用 disconnect() 断开。
 *
 * 重连策略：连接断开时自动重连（最多 10 次，指数退避 5s→160s）。
 * 在线检测：浏览器恢复在线时重置重连计数，避免短时断网耗尽重连次数。
 *
 * 升级说明：从旧版 stompjs@2.x 迁移到 @stomp/stompjs@7.x（旧版已停止维护）。
 *   - 旧 API: Stomp.over(socket) → stompClient.connect(headers, onConnect, onError)
 *   - 新 API: new Client({ webSocketFactory, connectHeaders, onConnect, ... }).activate()
 *   - 保留原有安全策略：token 通过 STOMP CONNECT 帧头传递，不出现在 URL 中
 *   - 修复 L6：保留自定义重连，改为指数退避（5/10/20/40/80/160s）且最大次数提升到 10，
 *     并在浏览器恢复在线时重置重连计数，避免短时断网耗尽重连次数后永久失联
 * @module utils/websocket
 */
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'

// 环境门控：生产环境静默 WS 日志，避免控制台噪音与潜在信息泄露
const isDev = import.meta.env.DEV
const wsLog = {
  // 环境门控日志：info 仅开发输出（豁免 no-console）；error 始终输出便于线上排查
  // eslint-disable-next-line no-console
  info: (...args) => isDev && console.info('[WS]', ...args),
  warn: (...args) => isDev && console.warn('[WS]', ...args),
  error: (...args) => console.error('[WS]', ...args)
}

// WebSocket 连接单例
let stompClient = null
let connected = false
let reconnectCount = 0
let reconnectTimer = null
let currentToken = null
let messageCallback = null
// 标记是否为主动断开（disconnect() 调用），避免 onWebSocketClose 触发重连
let isManualDisconnect = false
// 连接状态变更回调（供 store 实时同步 connected，替代一次性 setTimeout 轮询）
let statusCallback = null

/**
 * 注册连接状态变更回调（P2 修复）
 *
 * 背景：notification store 原先用一次性 setTimeout(3000) 读取 isConnected() 同步状态，
 * 存在两类失真：握手耗时 >3s 时永久误显"未连接"；会话中途断线时永久误显"已连接"。
 * 现改为在 connected 标记发生变化的所有路径（连接成功/断开/错误/主动断开）回调。
 * @param {(connected: boolean) => void} cb 状态回调
 */
export function onStatusChange(cb) {
  statusCallback = typeof cb === 'function' ? cb : null
}

/** 通知订阅者连接状态变更 */
function notifyStatus() {
  if (statusCallback) {
    try {
      statusCallback(connected)
    } catch (e) {
      wsLog.warn('状态回调执行失败:', e?.message)
    }
  }
}

// 修复 L6：原 MAX_RECONNECT=5、固定 5 秒间隔，网络抖动时易耗尽重连次数永久失联。
// 改为指数退避：5s→10s→20s→40s→80s→160s，最大重连次数提升到 10，
// 给后端短暂故障更长的恢复窗口；超出数组长度后取最后一档（160s）。
const MAX_RECONNECT = 10
const RECONNECT_INTERVALS = [5000, 10000, 20000, 40000, 80000, 160000]

/**
 * 建立 WebSocket 连接并订阅通知通道
 *
 * 安全策略：token 通过 STOMP CONNECT 帧头（connectHeaders）传递，而非 URL 查询参数，
 * 避免被服务端访问日志/浏览器历史/Referer 头泄露。
 * @param {string} token JWT 访问令牌
 * @param {Function} onMessage 收到通知时的回调，参数为通知对象
 */
export function connect(token, onMessage) {
  if (connected) {
    return
  }
  // 如果已有正在连接的旧 client，先断开避免连接泄漏
  if (stompClient) {
    try { stompClient.deactivate() } catch (e) { /* ignore */ }
  }
  currentToken = token
  messageCallback = onMessage
  isManualDisconnect = false

  // 创建 @stomp/stompjs 7.x 客户端
  stompClient = new Client({
    // SockJS 工厂：@stomp/stompjs 不内置 SockJS，通过 webSocketFactory 提供
    webSocketFactory: () => new SockJS('/ws'),
    // 安全：通过 STOMP CONNECT 帧头传递 token，而非 URL 查询参数
    // STOMP 握手时由后端 ChannelInterceptor 在 CONNECT 帧中解析 Authorization 头完成鉴权，
    // token 仅存在于 WebSocket 帧载荷中，不会被服务端访问日志/浏览器历史/Referer 头记录
    connectHeaders: { Authorization: 'Bearer ' + token },
    // 生产环境关闭调试日志
    debug: () => {},
    // 禁用内置自动重连，使用自定义重连逻辑（保留 MAX_RECONNECT=5 限制）
    reconnectDelay: 0,

    // STOMP 连接成功回调
    onConnect: () => {
      connected = true
      reconnectCount = 0
      notifyStatus()
      wsLog.info('通知连接已建立')
      // 订阅个人通知通道
      stompClient.subscribe('/user/queue/notifications', (frame) => {
        try {
          const notification = JSON.parse(frame.body)
          if (messageCallback) {
            messageCallback(notification)
          }
        } catch (e) {
          wsLog.error('解析通知消息失败:', e?.message)
        }
      })
    },

    // WebSocket 关闭回调（含主动断开与异常断开）
    onWebSocketClose: () => {
      connected = false
      notifyStatus()
      // 主动断开时不触发重连
      if (!isManualDisconnect) {
        // 修复 L6：浏览器已恢复在线时重置重连计数，
        // 避免短时断网耗尽重连次数后即使网络恢复也无法重连
        if (typeof navigator !== 'undefined' && navigator.onLine) {
          reconnectCount = 0
        }
        scheduleReconnect()
      }
    },

    // WebSocket 错误回调
    onWebSocketError: (evt) => {
      wsLog.warn('连接错误:', evt)
      connected = false
      notifyStatus()
    },

    // STOMP 协议错误回调
    onStompError: (frame) => {
      wsLog.warn('STOMP 协议错误:', frame.headers['message'] || '')
    }
  })

  // 激活客户端，开始连接
  stompClient.activate()
}

/**
 * 安排自动重连
 * 修复 L6：使用指数退避（RECONNECT_INTERVALS），避免固定 5 秒间隔在网络抖动时
 * 快速耗尽重连次数后永久失联；超出数组长度后取最后一档间隔。
 */
function scheduleReconnect() {
  if (reconnectCount >= MAX_RECONNECT) {
    wsLog.warn('已达最大重连次数，停止重连')
    return
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }
  // 取当前重连档位对应的间隔，超出数组长度后取最后一档（160s）
  const interval = RECONNECT_INTERVALS[Math.min(reconnectCount, RECONNECT_INTERVALS.length - 1)]
  reconnectCount++
  wsLog.info(`${interval / 1000}秒后第 ${reconnectCount} 次重连...`)
  reconnectTimer = setTimeout(() => {
    // 已主动断开或 token 已清空时不再重连
    if (!currentToken || isManualDisconnect) {
      return
    }
    // 清理旧客户端后再重新连接
    if (stompClient) {
      try {
        stompClient.deactivate()
      } catch (e) {
        // 旧客户端清理失败不影响重连
      }
      stompClient = null
    }
    connect(currentToken, messageCallback)
  }, interval)
}

/**
 * 断开 WebSocket 连接
 */
export function disconnect() {
  // 标记主动断开，阻止 onWebSocketClose 触发重连
  isManualDisconnect = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  // 修复 L7：删除 reconnectCount = MAX_RECONNECT 死代码。
  // 原代码意图"阻止自动重连"，但 isManualDisconnect=true 已在 onWebSocketClose 中
  // 拦截重连；且下方紧接 reconnectCount = 0 会立即覆盖该赋值，使其毫无作用。
  // 保留 isManualDisconnect 拦截与末尾 reconnectCount = 0 重置即可。
  if (stompClient) {
    // @stomp/stompjs 7.x 使用 deactivate() 断开（异步，返回 Promise）
    stompClient.deactivate()
    wsLog.info('通知连接已断开')
  }
  connected = false
  notifyStatus()
  stompClient = null
  currentToken = null
  messageCallback = null
  reconnectCount = 0
}

/**
 * 更新 WebSocket 连接使用的 Token
 *
 * 背景：access token TTL 较短（30min），过期后由 userStore.refresh() 换取新 token。
 *      但 WebSocket 模块内部持有 currentToken 用于重连，若不同步更新，
 *      断线重连时会用旧 token 握手 → 后端 401 → 重连失败，最多重试 10 次后永久断连。
 *
 * 调用时机：userStore.refresh() 成功后调用，传入新 token。
 * 副作用：仅更新 currentToken，不主动重连。若当前已断开且在重连队列中，
 *        下次重连会使用新 token；若当前已连接，连接保持不变（旧 token 在握手时
 *        已通过后端校验，连接本身不会因 token 过期而失效，仅影响后续重连）。
 * @param {string} newToken 刷新后的新 access token
 * @returns {void}
 */
export function updateToken(newToken) {
  if (!newToken) return
  currentToken = newToken
}

/**
 * 检查 WebSocket 是否已连接
 * @returns {boolean}
 */
export function isConnected() {
  return connected
}
