/**
 * 前端性能监控工具
 * 使用 Performance API 采集页面加载、接口请求、路由切换等性能指标
 * 可对接 Sentry、自建监控平台等
 *
 * 仅生产环境启用；各类指标限制存储条数（接口 100 条、路由 50 条）以控制内存占用。
 * @module utils/performance
 */

// 性能指标存储
const metrics = {
  pageLoad: {},      // 页面加载耗时
  apiRequests: [],   // 接口请求耗时
  routeChanges: [],  // 路由切换耗时
  resources: []      // 资源加载耗时
}

// 是否启用性能监控
const enabled = import.meta.env.PROD

// 模块级 PerformanceObserver 引用（getLCP 创建，destroyPerformanceObserver 释放）
let lcpObserver = null
// 页面 load 事件监听器引用（initPerformanceMonitor 注册，destroyPerformanceObserver 移除）
let loadListener = null
// beforeunload 事件监听器引用
let beforeunloadListener = null

/**
 * 采集页面加载性能指标
 * 应在页面加载完成后调用
 *
 * 优先使用 PerformanceNavigationTiming（现代浏览器），计算 DNS/TCP/请求/响应/
 * DOM 解析等分段耗时及 TTFB、FCP、LCP；不支持时回退旧版 PerformanceTiming。
 * @returns {void}
 */
export function capturePageLoad() {
  if (!enabled || !window.performance) return

  const timing = window.performance.timing
  const navigation = performance.getEntriesByType('navigation')[0]

  if (navigation) {
    // 使用 PerformanceNavigationTiming API（现代浏览器）
    metrics.pageLoad = {
      url: window.location.href,
      // DNS 解析耗时
      dns: Math.round(navigation.domainLookupEnd - navigation.domainLookupStart),
      // TCP 连接耗时
      tcp: Math.round(navigation.connectEnd - navigation.connectStart),
      // 请求耗时
      request: Math.round(navigation.responseStart - navigation.requestStart),
      // 响应耗时
      response: Math.round(navigation.responseEnd - navigation.responseStart),
      // DOM 解析耗时
      domParsing: Math.round(navigation.domInteractive - navigation.responseEnd),
      // 页面完全加载总耗时
      loadComplete: Math.round(navigation.loadEventEnd - navigation.startTime),
      // 首字节时间（TTFB）
      ttfb: Math.round(navigation.responseStart - navigation.requestStart),
      // 首次内容绘制（FCP）
      fcp: getFCP(),
      // 最大内容绘制（LCP）
      lcp: getLCP(),
      timestamp: Date.now()
    }
  } else if (timing) {
    // 兼容旧版 PerformanceTiming API
    metrics.pageLoad = {
      url: window.location.href,
      dns: timing.domainLookupEnd - timing.domainLookupStart,
      tcp: timing.connectEnd - timing.connectStart,
      request: timing.responseStart - timing.requestStart,
      response: timing.responseEnd - timing.responseStart,
      domParsing: timing.domInteractive - timing.responseEnd,
      loadComplete: timing.loadEventEnd - timing.navigationStart,
      ttfb: timing.responseStart - timing.requestStart,
      timestamp: Date.now()
    }
  }
}

/**
 * 获取首次内容绘制时间（FCP）
 * @returns {number|null} FCP 毫秒值；不支持或无数据时返回 null
 */
function getFCP() {
  if (!window.performance || !performance.getEntriesByName) return null
  const entries = performance.getEntriesByName('first-contentful-paint')
  return entries.length > 0 ? Math.round(entries[0].startTime) : null
}

/**
 * 获取最大内容绘制时间（LCP）
 *
 * 通过 PerformanceObserver 监听 largest-contentful-paint 条目。
 * 注意：LCP 为异步观测，返回值可能滞后于实际最终值。
 *
 * 实现细节：observer 实例保存到模块级变量 lcpObserver，供 destroyPerformanceObserver()
 * 在应用卸载时调用 disconnect() 释放，避免组件销毁后仍占用内存。
 * @returns {number|null} LCP 毫秒值；不支持时返回 null
 */
function getLCP() {
  if (!window.PerformanceObserver) return null
  let lcp = null
  try {
    // 保存 observer 引用，便于后续 disconnect
    lcpObserver = new PerformanceObserver((list) => {
      const entries = list.getEntries()
      if (entries.length > 0) {
        lcp = Math.round(entries[entries.length - 1].startTime)
      }
    })
    lcpObserver.observe({ type: 'largest-contentful-paint', buffered: true })
  } catch (e) {
    // 部分浏览器不支持 LCP
  }
  return lcp
}

/**
 * 销毁性能监控占用的资源
 *
 * 释放以下资源，避免组件卸载后仍占用内存：
 *   1. PerformanceObserver：disconnect 取消 LCP 观测
 *   2. load 事件监听器：移除未触发的 capturePageLoad 监听
 *   3. beforeunload 事件监听器：移除卸载前上报监听
 *
 * 应在 MainLayout 的 onBeforeUnmount 中调用。
 * @returns {void}
 */
export function destroyPerformanceObserver() {
  // 1. 断开 PerformanceObserver
  if (lcpObserver) {
    try {
      lcpObserver.disconnect()
    } catch (e) {
      // disconnect 失败不影响其他清理
    }
    lcpObserver = null
  }
  // 2. 移除 load 事件监听（页面未加载完时注册的监听）
  if (loadListener) {
    window.removeEventListener('load', loadListener)
    loadListener = null
  }
  // 3. 移除 beforeunload 监听
  if (beforeunloadListener) {
    window.removeEventListener('beforeunload', beforeunloadListener)
    beforeunloadListener = null
  }
}

/**
 * 记录接口请求耗时
 *
 * 追加一条请求耗时记录；耗时超过 3 秒时打印慢请求告警；队列超过 100 条时丢弃最旧记录。
 * @param {Object} info 请求信息
 * @param {string} info.url 请求地址
 * @param {string} [info.method] 请求方法（默认 GET）
 * @param {number} info.duration 耗时（毫秒）
 * @param {number} [info.status] HTTP 状态码
 * @param {boolean} [info.success] 是否成功
 * @returns {void}
 */
export function captureApiRequest(info) {
  if (!enabled) return
  metrics.apiRequests.push({
    url: info.url,
    method: info.method || 'GET',
    duration: info.duration,
    status: info.status,
    success: info.success,
    timestamp: Date.now()
  })
  // 慢请求告警（超过3秒）
  if (info.duration > 3000) {
    console.warn(`[性能监控] 慢请求告警: ${info.method} ${info.url} 耗时 ${info.duration}ms`)
  }
  // 限制存储数量
  if (metrics.apiRequests.length > 100) {
    metrics.apiRequests.shift()
  }
}

/**
 * 记录路由切换耗时
 * @param {string} from 来源路由
 * @param {string} to 目标路由
 * @param {number} duration 耗时(ms)
 * @returns {void}
 */
export function captureRouteChange(from, to, duration) {
  if (!enabled) return
  metrics.routeChanges.push({
    from,
    to,
    duration,
    timestamp: Date.now()
  })
  if (metrics.routeChanges.length > 50) {
    metrics.routeChanges.shift()
  }
}

/**
 * 获取所有性能指标
 * @returns {Object} 指标快照（页面加载、接口请求、路由切换、资源加载）
 */
export function getMetrics() {
  return { ...metrics }
}

/**
 * 上报性能指标到监控平台
 * 实际项目中可对接 Sentry、自建平台等
 * @returns {void}
 */
export function reportMetrics() {
  if (!enabled) return
  // 实际项目中可发送到监控平台，例如：
  // navigator.sendBeacon('/api/monitor/performance', JSON.stringify(metrics))
}

/**
 * 初始化性能监控
 *
 * 页面已加载则立即采集，否则监听 load 事件；并在 beforeunload 时上报。仅生产环境启用。
 * @returns {void}
 */
export function initPerformanceMonitor() {
  if (!enabled) {
    return
  }

  // 页面加载完成后采集
  if (document.readyState === 'complete') {
    capturePageLoad()
  } else {
    // 保存监听器引用，供 destroyPerformanceObserver 移除
    loadListener = capturePageLoad
    window.addEventListener('load', loadListener)
  }

  // 页面卸载前上报
  beforeunloadListener = reportMetrics
  window.addEventListener('beforeunload', beforeunloadListener)
}
