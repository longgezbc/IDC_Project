/**
 * 会话超时提醒工具
 * 监听用户操作（鼠标、键盘、滚动），长时间无操作时弹窗提醒即将登出
 * 支持自动续期和手动续期
 *
 * 优化点：
 *   - 高频事件（mousemove/scroll）使用 rAF 节流，避免每像素触发
 *   - 日志仅开发环境输出，生产环境静默
 * @module utils/sessionMonitor
 */
import { feedback } from '@/utils/feedback'

const isProd = import.meta.env.PROD
/**
 * 环境感知日志：仅开发环境输出，生产环境静默
 * @param {...*} args 透传给 console.log 的日志参数
 * @returns {void}
 */
function log(...args) {
  // 环境门控日志：开发输出、生产静默；豁免 no-console（有意为之的基础设施工具）
  // eslint-disable-next-line no-console
  if (!isProd) console.log(...args)
}

// 超时配置（毫秒）
const TIMEOUT_CONFIG = {
  // 总超时时间：30分钟无操作自动登出
  totalTimeout: 30 * 60 * 1000,
  // 提前提醒时间：超时前5分钟提醒
  warnBefore: 5 * 60 * 1000,
  // 检查间隔：每30秒检查一次
  checkInterval: 30 * 1000
}

let lastActivityTime = Date.now()
let warningShown = false
let checkTimer = null
let onTimeoutCallback = null
let onRefreshCallback = null
// rAF 节流标记，避免高频事件每次都写时间
let rafScheduled = false
// rAF 返回的 id（用于 stopSessionMonitor 中 cancelAnimationFrame 取消未执行的回调）
let rafId = null

/**
 * 记录用户活动（rAF 节流）
 *
 * mousemove/scroll 等高频事件可能每像素触发一次，直接更新时间戳开销大。
 * 通过 requestAnimationFrame 合并同一帧内的多次触发，一帧最多更新一次，
 * 显著降低主线程压力。若提醒弹窗已显示且用户重新操作，则关闭提醒并触发续期。
 *
 * 资源管理：rAF 返回的 id 保存到 rafId，供 stopSessionMonitor 在监控停止时
 * cancelAnimationFrame 取消未执行的回调，避免组件卸载后回调仍触发。
 * @returns {void}
 */
function recordActivity() {
  if (rafScheduled) return
  rafScheduled = true
  rafId = requestAnimationFrame(() => {
    rafScheduled = false
    rafId = null
    lastActivityTime = Date.now()
    // 如果已显示提醒，用户有操作则关闭提醒并重置
    if (warningShown) {
      warningShown = false
      // 自动续期
      if (onRefreshCallback) {
        onRefreshCallback()
      }
    }
  })
}

/**
 * 检查会话是否超时（定时器周期调用）
 *
 * 剩余时间进入提醒窗口（warnBefore）时弹出续期提示；空闲达到 totalTimeout 时停止监控并触发登出回调。
 * @returns {void}
 */
function checkTimeout() {
  const now = Date.now()
  const idleTime = now - lastActivityTime
  const remainingTime = TIMEOUT_CONFIG.totalTimeout - idleTime

  // 即将超时，显示提醒
  if (remainingTime <= TIMEOUT_CONFIG.warnBefore && remainingTime > 0 && !warningShown) {
    warningShown = true
    showWarningDialog(Math.ceil(remainingTime / 1000 / 60))
  }

  // 已超时
  if (idleTime >= TIMEOUT_CONFIG.totalTimeout) {
    stopSessionMonitor()
    if (onTimeoutCallback) {
      onTimeoutCallback()
    }
  }
}

/**
 * 显示超时提醒弹窗
 *
 * 用户点击"继续操作"则重置计时并触发续期回调；点击"立即登出"则停止监控并触发超时回调。
 * @param {number} remainingMinutes 距离自动登出的剩余分钟数
 * @returns {void}
 */
function showWarningDialog(remainingMinutes) {
  feedback.confirm(
    `您已长时间未操作，系统将在 ${remainingMinutes} 分钟后自动登出。是否继续操作？`,
    {
      title: '会话即将过期',
      type: 'warning',
      confirmText: '继续操作',
      cancelText: '立即登出'
    }
  )
    .then(() => {
      // 用户选择继续操作，重置计时
      warningShown = false
      lastActivityTime = Date.now()
      if (onRefreshCallback) {
        onRefreshCallback()
      }
      feedback.success('会话已续期')
    })
    .catch(() => {
      // 用户选择登出
      warningShown = false
      stopSessionMonitor()
      if (onTimeoutCallback) {
        onTimeoutCallback()
      }
    })
}

/**
 * 启动会话超时监控
 *
 * 副作用：注册用户活动事件监听并启动周期检查定时器（重复启动会先清除旧定时器）。
 * @param {Object} [callbacks={}] 回调函数
 * @param {Function} callbacks.onTimeout 超时回调（通常执行登出）
 * @param {Function} callbacks.onRefresh 续期回调（通常刷新token）
 * @returns {void}
 */
export function startSessionMonitor(callbacks = {}) {
  onTimeoutCallback = callbacks.onTimeout
  onRefreshCallback = callbacks.onRefresh

  // P1 修复：重置会话状态。startSessionMonitor 在 MainLayout onMounted 时调用，
  // 会话超时登出后用户重新登录会再次挂载。若不重置，lastActivityTime 仍是上一次
  // 会话的旧值（idleTime 立即超限），首个 30s 检查即触发二次登出；
  // warningShown 残留为 true 也会导致新一轮超时不再弹出提醒弹窗。
  lastActivityTime = Date.now()
  warningShown = false

  // 监听用户活动事件
  document.addEventListener('mousemove', recordActivity, { passive: true })
  document.addEventListener('keydown', recordActivity, { passive: true })
  document.addEventListener('click', recordActivity, { passive: true })
  document.addEventListener('scroll', recordActivity, { passive: true })

  // 启动定时检查
  if (checkTimer) {
    clearInterval(checkTimer)
  }
  checkTimer = setInterval(checkTimeout, TIMEOUT_CONFIG.checkInterval)

  log('[会话监控] 已启动，超时时间:', TIMEOUT_CONFIG.totalTimeout / 60000, '分钟')
}

/**
 * 停止会话超时监控
 *
 * 副作用：清除检查定时器并移除所有活动事件监听。
 * @returns {void}
 */
export function stopSessionMonitor() {
  if (checkTimer) {
    clearInterval(checkTimer)
    checkTimer = null
  }
  // 取消未执行的 requestAnimationFrame 回调，避免监控停止后回调仍触发
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  rafScheduled = false
  document.removeEventListener('mousemove', recordActivity)
  document.removeEventListener('keydown', recordActivity)
  document.removeEventListener('click', recordActivity)
  document.removeEventListener('scroll', recordActivity)
  log('[会话监控] 已停止')
}

/**
 * 重置活动时间（手动续期）
 * @returns {void}
 */
export function resetActivity() {
  lastActivityTime = Date.now()
  warningShown = false
}

/**
 * 获取剩余会话时间（秒）
 * @returns {number} 距离自动登出的剩余秒数（不小于 0）
 */
export function getRemainingTime() {
  const idleTime = Date.now() - lastActivityTime
  return Math.max(0, Math.floor((TIMEOUT_CONFIG.totalTimeout - idleTime) / 1000))
}
