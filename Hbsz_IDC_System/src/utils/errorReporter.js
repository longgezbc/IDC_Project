/**
 * 前端错误上报工具
 * 采集 JS 错误、Promise 异常、资源加载失败等，统一上报到后端监控接口
 * 支持错误聚合、采样率控制、离线缓存
 *
 * 安全/性能考量：URL 上报前脱敏，采样率 + 指纹去重抑制海量重复错误，
 * 上报优先使用 sendBeacon 以保证页面卸载时不丢失，失败时回退入队。
 * @module utils/errorReporter
 */
// 修复 H7：从 userStore 读取用户 ID 替代从未写入的 localStorage 'userId'
import { useUserStore } from '@/stores/user'

// 错误上报开关（仅生产环境启用）
const enabled = import.meta.env.PROD

// 错误上报接口地址
const REPORT_URL = '/api/monitor/error'

// 错误上报采样率（0-1，1表示全部上报）
// 默认 0.3：生产环境大量重复错误采样上报即可，避免上报请求压垮监控接口
// 可通过 VITE_ERROR_SAMPLE_RATE 环境变量覆盖
const SAMPLE_RATE = Number(import.meta.env.VITE_ERROR_SAMPLE_RATE) || 0.3

// URL 中需要脱敏的 query 参数名（不区分大小写）
// 这些参数可能含 token、用户ID 等敏感信息，上报前需移除
const SENSITIVE_QUERY_KEYS = ['token', 'authorization', 'userid', 'uid', 'phone', 'email']

// 本地缓存队列（用于离线时暂存）
const errorQueue = []

// 错误指纹去重（相同错误短时间内只上报一次）
const errorFingerprints = new Map()
const FINGERPRINT_TTL = 60 * 1000 // 1分钟内相同错误只上报一次

// 上报中标记，防止上报本身失败导致循环
let isReporting = false

/**
 * 生成错误指纹（用于去重）
 *
 * 以错误消息 + 堆栈前 200 字符组合作为指纹，使同类错误映射到同一 key，
 * 便于在 TTL 窗口内抑制重复上报。
 * @param {Error|Object|string} error 错误对象或描述
 * @returns {string} 错误指纹字符串
 */
function generateFingerprint(error) {
  const message = error.message || error.msg || String(error)
  const stack = error.stack || ''
  // 取堆栈前200字符作为指纹
  return `${message}::${stack.substring(0, 200)}`
}

/**
 * 判断是否应该上报（采样率 + 去重）
 *
 * 先按 SAMPLE_RATE 随机采样，降低海量重复错误对监控接口的冲击；
 * 再按指纹在 FINGERPRINT_TTL 窗口内去重。指纹表超过 100 条时顺带清理过期项，
 * 防止 Map 无限增长导致内存泄漏。
 * @param {string} fingerprint 错误指纹
 * @returns {boolean} 是否允许本次上报
 */
function shouldReport(fingerprint) {
  // 采样率控制
  if (Math.random() > SAMPLE_RATE) {
    return false
  }
  // 指纹去重
  const now = Date.now()
  const lastReport = errorFingerprints.get(fingerprint)
  if (lastReport && now - lastReport < FINGERPRINT_TTL) {
    return false
  }
  errorFingerprints.set(fingerprint, now)
  // 清理过期指纹
  if (errorFingerprints.size > 100) {
    for (const [key, time] of errorFingerprints) {
      if (now - time > FINGERPRINT_TTL) {
        errorFingerprints.delete(key)
      }
    }
  }
  return true
}

/**
 * URL 脱敏：移除 query 中敏感参数的值，仅保留 key
 * 避免将 token、手机号等敏感信息上报到监控系统
 * @param {string} url 原始 URL
 * @returns {string} 脱敏后的 URL
 */
function sanitizeUrl(url) {
  if (!url) return url
  try {
    const u = new URL(url, window.location.origin)
    const params = u.searchParams
    SENSITIVE_QUERY_KEYS.forEach((key) => {
      // URLSearchParams keys 区分大小写，需手动遍历匹配
      params.forEach((value, k) => {
        if (k.toLowerCase() === key.toLowerCase()) {
          params.set(k, '[REDACTED]')
        }
      })
    })
    return u.toString()
  } catch (e) {
    // URL 解析失败，回退为字符串截断（仅保留前 200 字符）
    return String(url).substring(0, 200)
  }
}

/**
 * 获取用户环境信息
 *
 * 采集 UA、脱敏后的 URL、用户 ID、视口与屏幕尺寸等上下文，随错误一并上报。
 * @returns {Object} 环境上下文信息
 */
function getEnvironment() {
  // 修复 H7：原代码读取 localStorage.getItem('userId')，但全项目从未写入该键，
  // 导致上报的 userId 恒为 null，无法关联错误与用户。改为从 userStore 读取，
  // 在函数内部调用 useUserStore()（而非模块加载期），确保 Pinia 已初始化
  // （reportError 触发时机一定在应用启动之后，此时 Pinia 已就绪）。
  let userId = null
  try {
    const userStore = useUserStore()
    userId = userStore.userInfo?.id || null
  } catch (e) {
    // Pinia 未初始化或 store 不可用时保持 null，不影响错误上报主流程
  }
  return {
    userAgent: navigator.userAgent,
    // URL 脱敏后再上报，避免泄露 query 中的 token 等敏感参数
    url: sanitizeUrl(window.location.href),
    timestamp: Date.now(),
    // 用户信息（如有）
    userId,
    // 视口尺寸
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    // 屏幕尺寸
    screen: `${window.screen.width}x${window.screen.height}`
  }
}

/**
 * 上报错误到后端
 *
 * 开发环境仅打印到控制台；生产环境补充环境信息、经采样与指纹去重后入队并触发批量上报。
 * @param {Object} errorInfo 错误信息（type、message、stack 等）
 * @returns {void}
 */
export function reportError(errorInfo) {
  if (!enabled) {
    console.error('[错误上报-开发环境]', errorInfo)
    return
  }

  // 补充环境信息
  const reportData = {
    ...errorInfo,
    ...getEnvironment()
  }

  // 生成指纹并判断是否上报
  const fingerprint = generateFingerprint(errorInfo)
  if (!shouldReport(fingerprint)) {
    return
  }

  // 加入队列
  errorQueue.push(reportData)

  // 触发上报
  flushErrors()
}

/**
 * 批量上报错误队列
 *
 * 一次性清空队列并发送；优先 sendBeacon（页面卸载可靠上报），不支持则用 fetch(keepalive)。
 * isReporting 标记防止上报本身失败引发的重入循环；发送失败时将错误重新放回队列。
 *
 * 健壮性保障：
 *   - 校验上报地址（REPORT_URL）与数据（errors）非空，避免空请求
 *   - 特性检测 navigator.sendBeacon，不支持时降级到 fetch(keepalive)
 *   - 全程 try-catch，防止 sendBeacon 抛错（如跨域、数据过大）导致脚本中断
 * @returns {void}
 */
function flushErrors() {
  if (isReporting || errorQueue.length === 0) {
    return
  }
  isReporting = true
  const errors = errorQueue.splice(0, errorQueue.length)

  // 校验上报地址与数据非空，任一为空则放弃本次上报
  if (!REPORT_URL || !errors || errors.length === 0) {
    isReporting = false
    return
  }

  let payload
  try {
    payload = JSON.stringify({ errors })
  } catch (e) {
    // JSON 序列化失败（如循环引用），丢弃本次数据避免阻塞
    console.error('[错误上报] 序列化失败:', e)
    isReporting = false
    return
  }

  try {
    // 特性检测：navigator.sendBeacon 存在且为函数时使用，否则降级到 fetch
    if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
      const blob = new Blob([payload], { type: 'application/json' })
      // sendBeacon 返回 false 表示浏览器拒绝排队（如数据过大），此时降级到 fetch
      const queued = navigator.sendBeacon(REPORT_URL, blob)
      if (!queued) {
        // 浏览器拒绝排队，降级到 fetch
        fetch(REPORT_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload,
          keepalive: true
        }).catch(() => {
          // 上报失败，重新放回队列
          errorQueue.unshift(...errors)
        })
      }
    } else {
      // 不支持 sendBeacon，降级使用 fetch
      fetch(REPORT_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: payload,
        keepalive: true
      }).catch(() => {
        // 上报失败，重新放回队列
        errorQueue.unshift(...errors)
      })
    }
  } catch (e) {
    // 上报失败（如跨域、数据过大抛错），重新放回队列
    console.error('[错误上报] 发送失败:', e)
    errorQueue.unshift(...errors)
  } finally {
    isReporting = false
  }
}

/**
 * 上报 JS 运行时错误
 * @param {Error} error 错误对象
 * @param {string} [source='runtime'] 错误来源标识
 * @returns {void}
 */
export function reportJsError(error, source = 'runtime') {
  reportError({
    type: 'js_error',
    source,
    message: error.message || String(error),
    stack: error.stack || '',
    filename: error.filename || '',
    lineno: error.lineno || 0,
    colno: error.colno || 0
  })
}

/**
 * 上报 Promise 未捕获异常
 * @param {any} reason rejection 原因
 * @returns {void}
 */
export function reportPromiseError(reason) {
  reportError({
    type: 'promise_error',
    source: 'unhandledrejection',
    message: reason?.message || String(reason),
    stack: reason?.stack || ''
  })
}

/**
 * 上报资源加载失败
 * @param {Event} event 错误事件（target 为加载失败的元素）
 * @returns {void}
 */
export function reportResourceError(event) {
  const target = event.target
  if (!target || !target.tagName) return

  reportError({
    type: 'resource_error',
    source: 'resource',
    message: `资源加载失败: ${target.tagName}`,
    // 资源 URL 可能含签名 token，上报前脱敏
    filename: sanitizeUrl(target.src || target.href || ''),
    tagName: target.tagName.toLowerCase()
  })
}

/**
 * 上报 Vue 组件错误
 * @param {Error} error 错误对象
 * @param {string} info Vue 错误信息（错误发生的生命周期/上下文）
 * @returns {void}
 */
export function reportVueError(error, info) {
  reportError({
    type: 'vue_error',
    source: 'vue',
    message: error.message || String(error),
    stack: error.stack || '',
    info
  })
}

/**
 * 初始化全局错误监听
 *
 * 注册 window error（含资源加载错误）、unhandledrejection 与 beforeunload 监听。仅生产环境启用。
 * @returns {void}
 */
export function initErrorReporter() {
  if (!enabled) {
    return
  }

  // 监听全局 JS 错误
  window.addEventListener('error', (event) => {
    // 资源加载错误（target 为元素）
    if (event.target && event.target.tagName) {
      reportResourceError(event)
    } else {
      reportJsError(event.error || event, 'window.onerror')
    }
  }, true)

  // 监听 Promise 未捕获异常
  window.addEventListener('unhandledrejection', (event) => {
    reportPromiseError(event.reason)
  })

  // 页面卸载前上报剩余错误
  window.addEventListener('beforeunload', flushErrors)
}
