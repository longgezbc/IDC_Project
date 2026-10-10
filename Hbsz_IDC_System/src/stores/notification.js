/**
 * 通知状态仓库（Pinia setup store）
 *
 * 集中管理站内通知的列表、未读数与 WebSocket 实时连接生命周期。
 * 设计要点：
 *   1. 用户登录后调用 connectWebSocket() 建立连接，登出时 disconnectWebSocket()；
 *   2. WebSocket 收到新通知时静默插入到列表头部并自增未读数，同时 ElNotification 弹窗提示；
 *   3. 标记已读 / 全部已读 / 删除 等操作后自动同步未读数；
 *   4. fetchUnreadCount 定时轮询兜底（30s），防止 WS 断连漏消息。
 * @module stores/notification
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElNotification } from 'element-plus'
import { feedback } from '@/utils/feedback'
import { notificationApi } from '@/api/notification'
import { isCancelError } from '@/utils/cancelToken'
import { connect as wsConnect, disconnect as wsDisconnect, onStatusChange as wsOnStatusChange, updateToken as wsUpdateToken } from '@/utils/websocket'
import { useUserStore } from '@/stores/user'

// WebSocket 兜底轮询间隔（毫秒）
const UNREAD_POLL_INTERVAL = 30000

export const useNotificationStore = defineStore('notification', () => {
  // ===== state =====
  /** 通知列表（按 createdAt 倒序） */
  const list = ref([])
  /** 未读数 */
  const unreadCount = ref(0)
  /** WebSocket 是否已连接 */
  const connected = ref(false)
  /** 分页查询参数 */
  const query = ref({ pageNum: 1, pageSize: 20, notifyType: '', isRead: '' })
  /** 总条数 */
  const total = ref(0)
  /** 是否正在加载列表 */
  const loading = ref(false)
  /** 兜底轮询定时器 */
  let pollTimer = null
  /** fetchList 竞态保护序列号：每次请求递增，响应返回时比对，非最新则丢弃 */
  let fetchListSeq = 0

  // ===== actions =====

  /**
   * 拉取通知列表
   *
   * 竞态保护：用户在 NotificationDrawer 中快速切换筛选时会触发多次 fetchList，
   *          先发的慢请求可能后返回覆盖新数据。使用序列号机制确保只有最后一次
   *          请求的结果被写入。
   * @param {Object} [params] 查询参数覆盖；为空时复用 store 中的 query
   * @returns {Promise<void>}
   */
  async function fetchList(params) {
    if (params) {
      Object.assign(query.value, params)
    }
    const currentSeq = ++fetchListSeq
    loading.value = true
    try {
      const res = await notificationApi.getList(query.value)
      // 竞态保护：若期间又发起了新请求，丢弃本次过期响应
      if (currentSeq !== fetchListSeq) return
      // 修复 M26：仅兜底 res.data.records，未兼容 res.data.list，
      // 与其他 store（customer/project/document）的写法不一致。
      // 后端不同接口可能返回 records 或 list 字段，统一兜底保持一致性。
      list.value = res.data.records || res.data.list || []
      total.value = res.data.total || 0
    } catch (e) {
      // 竞态保护：过期的请求失败也不影响最新状态
      if (currentSeq !== fetchListSeq) return
      console.error('[通知] 拉取列表失败:', e?.message)
    } finally {
      // 仅当本次请求是最新请求时才关闭 loading
      if (currentSeq === fetchListSeq) {
        loading.value = false
      }
    }
  }

  /**
   * 拉取未读数
   * @returns {Promise<void>}
   */
  async function fetchUnreadCount() {
    try {
      const res = await notificationApi.getUnreadCount()
      unreadCount.value = Number(res.data) || 0
    } catch (e) {
      // 静默失败：未读数查询失败不打扰用户
      console.warn('[通知] 未读数查询失败:', e?.message)
    }
  }

  /**
   * 标记单条已读
   * @param {number|string} id 通知ID
   * @returns {Promise<void>}
   */
  async function markAsRead(id) {
    const item = list.value.find((n) => n.id === id)
    if (item && !item.isRead) {
      item.isRead = 1
      item.readTime = new Date().toISOString().replace('T', ' ').substring(0, 19)
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    try {
      await notificationApi.markAsRead(id)
    } catch (e) {
      // P2 修复：回滚后需提示用户，否则乐观更新已回滚但界面无任何反馈，用户误以为操作成功
      // 请求被取消（如连点触发写操作自动取消）时静默处理
      if (!isCancelError(e)) {
        console.error('[通知] 标记已读失败:', e?.message)
        feedback.error('标记已读失败，请重试')
      }
      // 回滚
      if (item) {
        item.isRead = 0
        item.readTime = null
        unreadCount.value += 1
      }
    }
  }

  /**
   * 全部标记已读
   * @returns {Promise<void>}
   */
  async function markAllAsRead() {
    if (unreadCount.value === 0) return
    const before = unreadCount.value
    // 乐观更新
    list.value.forEach((n) => {
      if (!n.isRead) {
        n.isRead = 1
        n.readTime = new Date().toISOString().replace('T', ' ').substring(0, 19)
      }
    })
    unreadCount.value = 0
    try {
      await notificationApi.markAllAsRead()
    } catch (e) {
      // P2 修复：失败需提示用户并回滚/重拉真实状态
      if (!isCancelError(e)) {
        console.error('[通知] 全部已读失败:', e?.message)
        feedback.error('全部标记已读失败，请重试')
      }
      // 回滚
      unreadCount.value = before
      // 重新拉取以恢复真实状态
      await fetchList()
      await fetchUnreadCount()
    }
  }

  /**
   * 删除单条通知
   * @param {number|string} id 通知ID
   * @returns {Promise<void>}
   */
  async function removeNotification(id) {
    const idx = list.value.findIndex((n) => n.id === id)
    const removed = idx >= 0 ? list.value.splice(idx, 1)[0] : null
    if (removed && !removed.isRead) {
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    if (removed) total.value = Math.max(0, total.value - 1)
    try {
      await notificationApi.remove(id)
    } catch (e) {
      // P2 修复：回滚后需提示用户，否则通知重新出现但界面无任何反馈
      if (!isCancelError(e)) {
        console.error('[通知] 删除失败:', e?.message)
        feedback.error('删除通知失败，请重试')
      }
      // 回滚
      if (removed) {
        list.value.splice(idx, 0, removed)
        if (!removed.isRead) unreadCount.value += 1
        total.value += 1
      }
    }
  }

  /**
   * 处理 WebSocket 收到的实时通知
   *
   * P1 修复：推送插入需尊重当前筛选条件。list/total 是"当前筛选条件下的结果集"，
   * 若用户正按 notifyType 或 isRead 筛选（如只看未读），不匹配的新通知不能塞进列表，
   * 否则列表出现越筛选项、total 与实际不符、分页错乱。
   * 另外仅在第一页时插入头部（新通知属于第一页），翻页中仅修正计数。
   * unreadCount 是全局未读数，与筛选无关，无条件自增。
   * @param {Object} notification 通知对象
   */
  function handleIncomingNotification(notification) {
    // 全局未读数：无条件更新
    if (!notification.isRead) {
      unreadCount.value += 1
    }
    // 列表插入：需匹配当前筛选条件（类型 + 已读状态）
    const matchesFilter =
      (!query.value.notifyType || String(notification.notifyType) === String(query.value.notifyType)) &&
      (query.value.isRead === '' || query.value.isRead === undefined ||
        Number(notification.isRead) === Number(query.value.isRead))
    if (matchesFilter) {
      total.value += 1
      // 仅在第一页时插入头部；翻页状态下新通知属于第一页，此处只修正 total
      if (query.value.pageNum === 1) {
        list.value.unshift(notification)
      }
    }
    // 浏览器原生通知弹窗（5 秒自动关闭）
    const priorityMap = { 1: 'success', 2: 'warning', 3: 'error' }
    const type = priorityMap[notification.priority] || 'info'
    ElNotification({
      title: notification.title || '新通知',
      message: notification.content || '',
      type,
      duration: 5000,
      position: 'bottom-right'
    })
  }

  /**
   * 建立 WebSocket 连接（用户登录后调用）
   * @returns {void}
   */
  function connectWebSocket() {
    const userStore = useUserStore()
    if (!userStore.token) {
      console.warn('[通知] 无 token，跳过 WebSocket 连接')
      return
    }
    if (connected.value) return
    wsConnect(userStore.token, handleIncomingNotification)
    // P2 修复（替代原一次性 setTimeout(3000) 轮询）：注册状态回调实时同步。
    // 原实现存在两类失真：① 握手耗时 >3s 时，3s 检查到的仍是未连接，之后
    // 即使真正连上 store 也不再更新；② 会话中途断线/重连耗尽后，store 仍停留
    // 在"已连接"，用户收不到实时通知却无感知。改为由 websocket 模块在
    // 连接成功/断开/错误/主动断开的所有路径回调本 store 同步 connected。
    wsOnStatusChange((isConnected) => {
      connected.value = isConnected
    })
    // 启动兜底轮询（防止 WS 断连漏消息）
    startPolling()
  }

  /**
   * 断开 WebSocket 连接（用户登出时调用）
   * @returns {void}
   */
  function disconnectWebSocket() {
    wsDisconnect()
    connected.value = false
    stopPolling()
    // 重置状态（补充 loading：原代码遗漏，fetchList 进行中登出会导致 loading 永久为 true）
    list.value = []
    unreadCount.value = 0
    total.value = 0
    loading.value = false
    query.value = { pageNum: 1, pageSize: 20, notifyType: '', isRead: '' }
  }

  /**
   * 更新 WebSocket 连接使用的 Token（token 刷新后调用）
   *
   * 背景：access token 过期刷新后，WebSocket 模块内部持有的 currentToken 仍是旧值，
   *      断线重连会用旧 token 握手失败。userStore.refresh() 成功后需调用本方法
   *      同步更新，确保后续重连使用最新 token。
   * @param {string} newToken 刷新后的新 access token
   * @returns {void}
   */
  function updateWebSocketToken(newToken) {
    wsUpdateToken(newToken)
  }

  /**
   * 启动未读数兜底轮询
   */
  function startPolling() {
    stopPolling()
    pollTimer = setInterval(() => {
      fetchUnreadCount()
    }, UNREAD_POLL_INTERVAL)
  }

  /**
   * 停止兜底轮询
   */
  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    list,
    unreadCount,
    connected,
    query,
    total,
    loading,
    fetchList,
    fetchUnreadCount,
    markAsRead,
    markAllAsRead,
    removeNotification,
    connectWebSocket,
    disconnectWebSocket,
    updateWebSocketToken
  }
})
