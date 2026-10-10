/**
 * Axios 请求封装模块
 *
 * 统一构建全局 axios 实例，并集中处理以下横切关注点：
 *   1. 请求拦截：自动注入 Bearer Token，将请求登记到取消队列（AbortController）。
 *   2. 响应拦截：统一解包业务响应体、按业务码分流（成功/无权限/鉴权失效）。
 *   3. Token 失效协调：401/2001/2002 时委托 TokenRefreshCoordinator，
 *      通过"锁 + 队列"避免并发刷新，刷新成功后自动重发挂起请求。
 *   4. HTTP 层错误映射：将 404/403/500/超时等转换为友好中文提示。
 *
 * 安全/性能考量：Token 仅在拦截器中附加，避免散落各处；重复请求由取消队列
 * 抑制；被取消的请求静默处理，不打扰用户。
 * @module utils/request
 */
import axios from 'axios'
import { feedback } from '@/utils/feedback'
import { useUserStore } from '@/stores/user'
import { addPending, removePending, isCancelError } from '@/utils/cancelToken'
import { TokenRefreshCoordinator } from '@shared/tokenRefreshCoordinator'

/** 全局请求超时（毫秒）：15s，覆盖大多数列表查询场景；文件上传/导出等长耗时接口在各自 api 层覆盖 */
const REQUEST_TIMEOUT = 15000

// 创建 axios 实例：统一 baseURL、超时时间与默认请求头
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// 请求拦截器：自动附加 Token，并加入 pending 队列
// - config.skipCancel 为 true 时不纳入取消队列（如 Token 刷新、系统配置等公开请求）
// - config.skipAuth 为 true 时不注入 Authorization 头（如 Token 刷新接口本身，
//   调用时 access token 已过期，携带无效令牌属无意义流量且可能触发后端额外 401 处理）
// - 存在登录态且未豁免鉴权时统一注入 Authorization 头
request.interceptors.request.use(
  (config) => {
    // FormData 上传：删除默认 Content-Type，让浏览器自动设置 multipart/form-data + boundary
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
      delete config.headers['content-type']
    }
    // 跳过取消机制的请求（如 token 刷新）
    if (!config.skipCancel) {
      addPending(config)
    }
    // 豁免鉴权头注入的请求（如 token 刷新）不附加 Authorization
    if (!config.skipAuth) {
      const userStore = useUserStore()
      if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ===== Token 自动刷新机制 =====
// 使用跨端共享的 TokenRefreshCoordinator 处理并发 401 时的刷新与重发
// 仅"锁 + 队列"算法在 shared 中复用，refresh 调用/重发/跳登录由本文件注入
let refreshCoordinator = null

/**
 * 惰性获取 Token 刷新协调器（单例）
 *
 * 首次调用时注入四个平台相关回调：
 *   - doRefresh：调用用户 store 执行刷新，返回新 Token
 *   - retry：用新 Token 重写 Authorization 后重发原请求
 *   - onFail：刷新失败时跳转登录页
 *   - getToken：读取当前 Token 供队列请求复用
 * 协调器内部通过锁与等待队列保证并发 401 只触发一次刷新。
 * @returns {TokenRefreshCoordinator} 全局唯一的刷新协调器实例
 */
function getRefreshCoordinator() {
  if (!refreshCoordinator) {
    refreshCoordinator = new TokenRefreshCoordinator({
      doRefresh: () => useUserStore().refresh(),
      retry: (config, newToken) => {
        config.headers.Authorization = `Bearer ${newToken}`
        return request(config)
      },
      onFail: () => redirectToLogin(),
      getToken: () => useUserStore().token
    })
  }
  return refreshCoordinator
}

/**
 * 跳转登录页
 *
 * 清空本地鉴权状态并提示用户，延迟片刻后跳转，让提示信息有机会展示。
 *
 * 跳转策略：优先使用 SPA 路由跳转（router.push）保留应用状态、避免整页刷新；
 * 若动态 import router 失败（极端情况），回退到 window.location.href 整页跳转，
 * 确保用户始终能回到登录页。动态 import 用于打破 request.js → router → request.js
 * 的潜在循环依赖。
 * @returns {void}
 */
function redirectToLogin() {
  const userStore = useUserStore()
  userStore.clearAuth()
  feedback.error('登录状态已失效，请重新登录')
  setTimeout(() => {
    // 优先 SPA 路由跳转，保留应用状态；动态 import 避免循环依赖
    import('@/router')
      .then((module) => {
        const router = module.default
        router.push('/login')
      })
      .catch((e) => {
        // 极端情况下动态 import 失败（如构建产物异常），回退到整页跳转
        console.error('[request] 动态加载路由失败，回退整页跳转:', e)
        window.location.href = '/login'
      })
  }, 800)
}

// 响应拦截器：统一处理业务码与 HTTP 错误
// 成功回调按业务码分流；失败回调映射 HTTP 状态并处理取消/401 重试
request.interceptors.response.use(
  // 修复 M24：blob 分支需 await response.data.text() 读取错误体，回调声明为 async
  async (response) => {
    removePending(response.config)
    const res = response.data
    // 二进制流（文件下载等）直接返回
    if (response.config.responseType === 'blob') {
      // 修复 M24：后端在错误时（如鉴权失败、参数错误）会返回 JSON 错误体而非文件流，
      // 此时 blob 分支直接返回会让调用方拿到一个包含错误信息的 Blob，
      // 调用方无法识别错误（例如导出报表失败时仍尝试保存为 xlsx 文件导致文件损坏）。
      // 这里检测响应类型，若为 application/json 则解析错误体并走错误提示分支。
      if (response.data && response.data.type && response.data.type.includes('application/json')) {
        const text = await response.data.text()
        try {
          const errorData = JSON.parse(text)
          if (errorData.code !== 200) {
            feedback.error(errorData.message || '下载失败')
            return Promise.reject(new Error(errorData.message || '下载失败'))
          }
        } catch (e) {
          // JSON 解析失败，按原始 blob 返回由调用方处理
        }
      }
      return response
    }
    if (res.code === 200) {
      return res
    }
    // 业务错误
    const message = res.message || '请求失败'
    // 401 / Token 过期：尝试刷新 Token
    if (res.code === 401 || res.code === 2001 || res.code === 2002) {
      const userStore = useUserStore()
      const config = response.config

      // 登出/刷新接口本身失败：直接跳转登录
      if (config.url.includes('/auth/logout') || config.url.includes('/auth/refresh')) {
        if (!config.skipAuthRedirect) redirectToLogin()
        return Promise.reject(new Error(message))
      }

      // __retried 守卫（P0 修复：业务码 401 死循环）：
      // 业务码 401 与 HTTP 401 不同——HTTP 拦截器分支已有 __retried 守卫，但业务码分支缺失。
      // 若刷新 token 后业务码仍为 401（例如后端再次拒绝刷新后的 token），会再次进入此分支，
      // 形成"刷新→401→刷新→401"无限循环，最终堆栈溢出。
      // 加入守卫：仅允许重试一次，第二次仍 401 直接跳登录页。
      if (config.__retried) {
        if (!config.skipAuthRedirect) redirectToLogin()
        return Promise.reject(new Error(message))
      }
      config.__retried = true

      // skipAuthRedirect：静默刷新，失败不跳登录（如 MainLayout 刷新时的 fetchProfile）
      if (config.skipAuthRedirect) {
        return userStore.refresh().then((ok) => {
          if (!ok) return Promise.reject(new Error(message))
          config.headers.Authorization = `Bearer ${userStore.token}`
          return request(config)
        })
      }

      // 委托给 TokenRefreshCoordinator：内部处理并发锁、队列与重发
      return getRefreshCoordinator().handle(config)
    }
    // 2003: 无操作权限
    if (res.code === 2003) {
      feedback.warning('无操作权限')
      return Promise.reject(new Error('无操作权限'))
    }
    feedback.error(message)
    return Promise.reject(new Error(message))
  },
  // 回调声明为 async：blob 错误体需 await text() 解析（与成功回调的 blob 分支对称）
  async (error) => {
    // 请求被取消：静默处理，不提示
    if (isCancelError(error)) {
      return Promise.reject(error)
    }
    if (error.config) {
      removePending(error.config)
    }
    const status = error.response?.status
    let message = error.message || '网络异常'
    // 优先使用后端返回的业务错误消息（HTTP 400/405/415/500 等均可能携带 JSON 错误体）
    if (error.response?.data?.message) {
      message = error.response.data.message
    }
    // P2 修复：blob 请求（下载/导出）收到 HTTP 4xx/5xx 时，error.response.data 是 Blob
    // 而非 JSON，上面的 data.message 读取不到。与成功回调的 blob 分支保持对称：
    // 检测到 JSON 类型错误体时解析出后端返回的具体消息，否则用户只能看到
    // 按 HTTP 状态码映射的笼统提示（如"服务器内部错误"）。
    if (
      error.config?.responseType === 'blob' &&
      error.response?.data instanceof Blob &&
      error.response.data.type.includes('application/json')
    ) {
      try {
        const text = await error.response.data.text()
        const errorData = JSON.parse(text)
        if (errorData.message) {
          message = errorData.message
        }
      } catch {
        // JSON 解析失败，保持原 message
      }
    }
    if (status === 401) {
      // HTTP 401：同样委托给 TokenRefreshCoordinator
      // __retried 标记防止同一请求无限重试（刷新后仍 401 则跳登录）
      const config = error.config
      // 修复 H5：refresh token 过期时 /auth/refresh 接口本身返回 401，
      // 若不在此拦截会再次进入 refresh 协调器，调用已过期的 refreshToken 形成死锁，
      // 请求队列永久挂起。与业务码 401 分支保持一致的 URL 检查，直接跳登录。
      if (config && (config.url.includes('/auth/logout') || config.url.includes('/auth/refresh'))) {
        if (!config.skipAuthRedirect) redirectToLogin()
        return Promise.reject(error)
      }
      if (config && !config.__retried) {
        config.__retried = true
        // skipAuthRedirect：静默刷新，失败不跳登录（如 MainLayout 刷新时的 fetchProfile）
        if (config.skipAuthRedirect) {
          return useUserStore().refresh().then((ok) => {
            if (!ok) return Promise.reject(error)
            config.headers.Authorization = `Bearer ${useUserStore().token}`
            return request(config)
          })
        }
        return getRefreshCoordinator().handle(config)
      }
      if (!config.skipAuthRedirect) redirectToLogin()
      return Promise.reject(error)
    }
    if (status === 404) {
      message = '请求的资源不存在'
    } else if (status === 403) {
      message = '无权限访问'
    } else if (status === 500) {
      message = '服务器内部错误'
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    }
    feedback.error(message)
    return Promise.reject(error)
  }
)

export default request
