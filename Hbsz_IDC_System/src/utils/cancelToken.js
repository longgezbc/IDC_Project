import axios from 'axios'

/**
 * 请求取消管理器
 * 用于在路由切换、组件卸载等场景取消未完成的请求，避免内存泄漏和无效请求
 *
 * 实现说明：axios v1 已废弃 CancelToken，推荐使用原生 AbortController。
 * 兼容性：现代浏览器/Node 均支持 AbortController。
 *
 * 使用方式：
 *   import { createCancelSignal, cancelPending, withCancel } from '@/utils/cancelToken'
 *
 *   // 方式1：手动管理
 *   const { signal, cancel } = createCancelSignal('项目列表')
 *   api.getProjects({ signal })
 *   cancel()
 *
 *   // 方式2：自动取消（推荐在组件中使用）
 *   const fetchList = withCancel('项目列表', (params, signal) => {
 *     return api.getProjects({ ...params, signal })
 *   })
 *   fetchList(params) // 重复调用会自动取消上一次
 * @module utils/cancelToken
 */

// 存储所有进行中的请求，key 为请求标识
const pendingMap = new Map()

/**
 * FormData 请求的自增序号
 * FormData 无法稳定序列化为 key，用序号保证同 URL 并发上传各自拥有唯一 key
 */
let formDataSeq = 0

/**
 * 生成请求唯一标识
 *
 * 由 method、url、序列化后的 params 与 data 拼接而成，
 * 用于在 pendingMap 中区分"相同请求"，是重复请求识别的依据。
 * @param {Object} config axios 请求配置
 * @returns {string} 请求的唯一 key
 */
function generateKey(config) {
  const { method, url, params, data } = config || {}
  // 修复 H6/P2：JSON.stringify(new FormData()) 始终返回 '{}'，
  // 导致相同 URL 的并发上传请求 key 完全一致。而上传接口为 POST，
  // 会命中写操作的自动取消策略，同 URL 多文件并发上传只剩最后一个。
  // 之前的 'formData' 占位方案仍是共用 key，并未解决该问题。
  // 现改为给每个 FormData 请求分配自增序号：key 唯一 → 不会被误取消，
  // 且每个上传在 pendingMap 中独立登记，可被 cancelAllPending 统一管理。
  if (data instanceof FormData) {
    formDataSeq += 1
    return [method, url, JSON.stringify(params), `formData-${formDataSeq}`].join('&')
  }
  return [method, url, JSON.stringify(params), JSON.stringify(data)].join('&')
}

/**
 * 添加请求到 pending 队列
 *
 * 自动取消策略（P0 修复：cancelToken 并发破坏）：
 *   - 默认仅对写操作（POST/PUT/PATCH/DELETE）自动取消相同 key 的旧请求，
 *     用于抑制用户连点导致的重复提交；
 *   - GET 请求默认不自动取消，避免破坏合法并发场景
 *     （如多 Tab 同时打开相同项目、首页与详情页同时订阅同一接口）；
 *   - GET 请求若需要去重（例如搜索框输入），可在调用方显式设置 `config.dedup = true` 启用。
 *
 * 无论是否自动取消，新请求都会登记到 pendingMap，便于路由切换 / 退出登录时统一取消。
 * @param {Object} config axios 请求配置
 * @returns {string} 该请求的唯一 key
 */
export function addPending(config) {
  // FormData 请求的 key 含自增序号，非幂等：必须缓存到 config 上，
  // 否则 removePending 重新生成 key 会得到不同值，登记项永远无法移除
  const key = (config.__cancelKey = generateKey(config))
  const method = (config.method || 'get').toLowerCase()
  // 仅写操作或显式 dedup=true 时启用自动取消
  const shouldAutoCancel =
    ['post', 'put', 'patch', 'delete'].includes(method) || config.dedup === true

  if (shouldAutoCancel && pendingMap.has(key)) {
    // 已存在相同请求，取消前一个
    pendingMap.get(key).abort('请求被取消：重复提交')
    pendingMap.delete(key)
  }
  // 始终登记新请求到 pendingMap，便于 cancelAllPending 统一取消
  const controller = new AbortController()
  // 如果调用方已通过 withCancel 设置了 signal，不覆盖，而是监听其 abort 事件清理 pendingMap
  if (config.signal) {
    config.signal.addEventListener('abort', () => pendingMap.delete(key), { once: true })
  } else {
    config.signal = controller.signal
  }
  pendingMap.set(key, controller)
  return key
}

/**
 * 从 pending 队列移除
 *
 * 请求完成（成功或失败）后调用，避免已结束的请求残留在队列中被误取消。
 * @param {Object} config axios 请求配置
 * @returns {void}
 */
export function removePending(config) {
  // 优先使用 addPending 缓存的 key（FormData 请求 key 非幂等，见 addPending 注释）
  const key = config?.__cancelKey || generateKey(config)
  if (pendingMap.has(key)) {
    pendingMap.delete(key)
  }
}

/**
 * 取消指定 key 的请求
 * @param {string} key 请求唯一标识
 * @param {string} [reason='请求被取消'] 取消原因
 * @returns {void}
 */
export function cancelByKey(key, reason = '请求被取消') {
  if (pendingMap.has(key)) {
    pendingMap.get(key).abort(reason)
    pendingMap.delete(key)
  }
}

/**
 * 取消所有进行中的请求（常用于路由切换、退出登录时清理）
 * @param {string} [reason='请求被取消'] 取消原因
 * @returns {void}
 */
export function cancelAllPending(reason = '请求被取消') {
  pendingMap.forEach((controller) => controller.abort(reason))
  pendingMap.clear()
}

/**
 * 创建可取消的请求 signal
 * @param {string} tag 请求标签，用于标识
 * @returns {{ signal: AbortSignal, cancel: Function, key: string }}
 */
export function createCancelSignal(tag = '') {
  const controller = new AbortController()
  return {
    signal: controller.signal,
    cancel: (reason) => controller.abort(reason || `请求被取消: ${tag}`),
    key: tag
  }
}

/**
 * 高阶函数：包装请求函数，重复调用时自动取消上一次请求
 * 适用于搜索框、列表筛选等频繁请求场景
 *
 * @param {string} tag 请求标签
 * @param {Function} fn 请求函数，签名 (params, signal) => Promise
 * @returns {Function} 包装后的请求函数
 */
export function withCancel(tag, fn) {
  let currentController = null
  return function (...args) {
    // 取消上一次请求
    if (currentController) {
      currentController.abort(`自动取消上一次请求: ${tag}`)
      currentController = null
    }
    const controller = new AbortController()
    currentController = controller
    return fn.apply(this, [...args, controller.signal]).finally(() => {
      // 仅当 currentController 仍是当前 controller 时才清空，
      // 防止新请求的 controller 被旧请求的 finally 误清
      if (currentController === controller) {
        currentController = null
      }
    })
  }
}

/**
 * 判断错误是否为取消请求导致的
 * axios v1 中 AbortController 取消会抛出 axios.Cancel
 * @param {*} error 捕获到的错误对象
 * @returns {boolean} 是取消错误返回 true，否则 false
 */
export function isCancelError(error) {
  return axios.isCancel(error) || (error?.name === 'CanceledError')
}
