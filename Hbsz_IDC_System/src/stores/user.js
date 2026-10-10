/**
 * 用户认证状态仓库（Pinia setup store）
 *
 * 集中管理登录态：访问令牌、刷新令牌与用户信息，并对外提供登录、登出、
 * 刷新令牌、拉取资料等动作。令牌与用户信息均使用轻量加密存储于 localStorage 以缓解 XSS 风险。
 * 所有状态在初始化时从本地存储恢复，实现会话持久化。
 * @module stores/user
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
// 修复 L9：profile/password 相关接口由 userApi 提供，避免与 authApi 重复定义
import { userApi } from '@/api/user'
import { cryptoStorage } from '@/utils/cryptoStorage'

export const useUserStore = defineStore('user', () => {
  // ===== state =====
  // 修复 M23：token 与 userInfo 均使用 cryptoStorage 加密存储，避免明文泄露
  /** @type {import('vue').Ref<string>} 访问令牌，用于请求鉴权 */
  const token = ref(cryptoStorage.getItem('hbsz_token') || '')
  /** @type {import('vue').Ref<string>} 刷新令牌，用于访问令牌过期后换取新令牌 */
  const refreshToken = ref(cryptoStorage.getItem('hbsz_refresh_token') || '')
  /** @type {import('vue').Ref<Object|null>} 当前用户信息（含角色、权限等） */
  // cryptoStorage.getItem 返回解密后的字符串，需 JSON.parse 还原为对象；解密失败返回 null
  const userInfo = ref((() => {
    const raw = cryptoStorage.getItem('hbsz_user')
    if (!raw) return null
    try {
      return JSON.parse(raw)
    } catch (e) {
      // 解密成功但 JSON 解析失败（旧版明文数据或损坏数据），返回 null 触发重新登录拉取
      return null
    }
  })())

  // ===== getters =====
  /** 是否已登录（存在有效 token） */
  const isLogin = computed(() => !!token.value)
  /** 用户名 */
  const username = computed(() => userInfo.value?.username || '')
  /** 昵称，缺省回退为用户名 */
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  /** 当前用户角色列表 */
  const roles = computed(() => userInfo.value?.roles || [])

  /**
   * 登录
   *
   * 副作用：写入内存态并持久化 token/refreshToken（加密）与 userInfo（明文）到本地存储。
   * @param {{ username: string, password: string, [key: string]: any }} loginForm 登录表单
   * @returns {Promise<Object>} 登录响应数据（含令牌与用户信息）
   */
  async function login(loginForm) {
    const res = await authApi.login(loginForm)
    token.value = res.data.token
    refreshToken.value = res.data.refreshToken || ''
    userInfo.value = res.data.userInfo
    cryptoStorage.setItem('hbsz_token', res.data.token)
    if (res.data.refreshToken) {
      cryptoStorage.setItem('hbsz_refresh_token', res.data.refreshToken)
    }
    // 修复：统一使用 cryptoStorage 加密存储 userInfo，避免 login/refresh 明文写入与初始化解密读取不一致导致刷新页面丢失用户信息
    cryptoStorage.setItem('hbsz_user', JSON.stringify(res.data.userInfo))
    return res.data
  }

  /**
   * 登出：通知后端销毁 Token，并清理本地状态
   *
   * 副作用：即使后端接口调用失败，也保证清理本地登录态，避免残留脏数据。
   * @returns {Promise<void>}
   */
  async function logout() {
    // 即使后端调用失败，也要清理本地状态
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch (error) {
      console.error('调用登出接口失败，仍将清理本地状态:', error)
    } finally {
      clearAuth()
    }
  }

  /**
   * 清理本地登录态
   *
   * 副作用：清空内存中的令牌与用户信息，并删除本地存储对应项。
   * @returns {void}
   */
  function clearAuth() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    cryptoStorage.removeItem('hbsz_token')
    cryptoStorage.removeItem('hbsz_refresh_token')
    // 修复：与写入侧保持一致，使用 cryptoStorage 移除 userInfo，避免残留明文
    cryptoStorage.removeItem('hbsz_user')
  }

  /**
   * 拉取当前用户资料并更新本地缓存
   *
   * 副作用：更新 userInfo 内存态并写入 localStorage。
   * @returns {Promise<Object>} 最新的用户资料
   */
  async function fetchProfile() {
    // 修复 L9：原调用 authApi.getProfile，现统一用 userApi.getProfile
    const res = await userApi.getProfile()
    userInfo.value = res.data
    // 修复 M23：userInfo 改用 cryptoStorage 加密存储
    cryptoStorage.setItem('hbsz_user', JSON.stringify(res.data))
    return res.data
  }

  /**
   * 使用 refreshToken 刷新 accessToken
   *
   * 并发去重（P1 修复）：拦截器中 skipAuthRedirect 路径与 TokenRefreshCoordinator
   * 均会调用本方法。后端 refreshToken 为一次性使用（Redis SETNX），若两个并发刷新
   * 同时发起，必有一个因 SETNX 失败而走 catch 分支 clearAuth()，把另一个已成功的
   * 新 token 一并清掉，导致页面刷新时偶发被踢回登录页。
   * 这里用"在途 Promise 复用"保证同一时刻只有一个真实刷新请求，后到调用者共享结果。
   *
   * 副作用：刷新成功后同步通知 WebSocket 模块更新内部持有的 token，
   *         避免断线重连时使用旧 token 握手失败导致通知永久断连。
   *         采用动态 import 获取 notification store，打破 user → notification → user 的循环依赖。
   * @returns {Promise<boolean>} 是否刷新成功
   */
  let refreshInFlight = null
  function refresh() {
    if (refreshInFlight) {
      return refreshInFlight
    }
    refreshInFlight = (async () => {
      if (!refreshToken.value) {
        return false
      }
      try {
        const res = await authApi.refreshToken(refreshToken.value)
        token.value = res.data.token
        if (res.data.refreshToken) {
          refreshToken.value = res.data.refreshToken
          cryptoStorage.setItem('hbsz_refresh_token', res.data.refreshToken)
        }
        cryptoStorage.setItem('hbsz_token', res.data.token)
        // 若返回了新的 userInfo，同步更新
        if (res.data.userInfo) {
          userInfo.value = res.data.userInfo
          // 修复：统一使用 cryptoStorage 加密存储 userInfo，避免明文写入与初始化解密读取不一致
          cryptoStorage.setItem('hbsz_user', JSON.stringify(res.data.userInfo))
        }
        // 通知 WebSocket 更新 token，确保后续断线重连使用最新令牌
        // 动态 import 规避循环依赖：user.js → notification.js → user.js
        try {
          const { useNotificationStore } = await import('@/stores/notification')
          useNotificationStore().updateWebSocketToken(res.data.token)
        } catch (e) {
          // WebSocket 更新失败不影响刷新流程，仅记录日志
          console.warn('[user] 通知 WebSocket 更新 token 失败:', e?.message || 'unknown')
        }
        return true
      } catch (error) {
        // 仅打印 message，避免泄露含 token 的请求对象
        console.error('刷新 Token 失败:', error?.message || 'unknown')
        clearAuth()
        return false
      } finally {
        refreshInFlight = null
      }
    })()
    return refreshInFlight
  }

  return {
    token,
    refreshToken,
    userInfo,
    isLogin,
    username,
    nickname,
    roles,
    login,
    logout,
    fetchProfile,
    refresh,
    clearAuth
  }
})
