import request from '@/utils/request'

/**
 * 认证模块 API
 *
 * 封装登录、登出、Token 刷新及当前用户资料等与身份认证相关的后端接口。
 * 所有方法均返回 request 实例产生的 Promise，响应体已由拦截器统一解包。
 * @module api/auth
 */
export const authApi = {
  /**
   * 用户登录
   * @param {{ username: string, password: string, [key: string]: any }} data 登录凭证（用户名、密码等）
   * @returns {Promise<any>} 解析后返回包含访问令牌与用户信息的响应数据
   */
  login(data) {
    return request({
      url: '/auth/login',
      method: 'post',
      data
    })
  },

  /**
   * 用户登出
   * @returns {Promise<any>} 服务端注销当前会话后的响应结果
   */
  logout() {
    return request({
      url: '/auth/logout',
      method: 'post'
    })
  },

  /**
   * 刷新 Token
   *
   * 安全/可靠性说明：
   *   - skipCancel: true：豁免请求取消机制。Token 刷新通常发生在 401 后的并发场景，
   *     若被路由切换（cancelAllPending）或重复点击（写操作自动取消）中止，
   *     会抛出 CanceledError → stores/user.js refresh() catch 分支 clearAuth() → 用户被意外登出。
   *     刷新请求必须保证完整执行，不纳入 pending 队列。
   *   - skipAuth: true：豁免请求拦截器自动注入 Authorization 头。刷新时 access token 已过期，
   *     携带无效令牌是无意义流量，且可能触发后端额外的 401 处理路径。
   *     拦截器需配合判断：if (config.skipAuth) 不附加 Authorization 头。
   * @param {string} refreshToken 用于换取新访问令牌的刷新令牌
   * @returns {Promise<any>} 包含新访问令牌（及可能的新刷新令牌）的响应数据
   */
  refreshToken(refreshToken) {
    return request({
      url: '/auth/refresh',
      method: 'post',
      data: { refreshToken },
      skipCancel: true,
      skipAuth: true
    })
  },

  /**
   * 获取图片验证码
   * <p>
   * P0-4.1：登录验证码强制启用。后端返回 captchaId（标识）+ image（base64 data URL 图片），
   * 答案存于 Redis，前端不持有答案。提交登录时携带 captchaId + 用户输入的 captcha 即可。
   * 验证码一次性使用，无论成功失败均失效，故登录失败后需重新获取。
   * @returns {Promise<{captchaId: string, image: string}>} 验证码标识与 base64 图片
   */
  getCaptcha() {
    return request({
      url: '/captcha',
      method: 'get'
    })
  }

  // 修复 L9：删除 getProfile / updateProfile / changePassword 三个方法。
  // 这三个方法与 user.js 中的实现完全重复（URL、method、data 一致），
  // 维护时容易只改一处导致行为不一致。统一由 userApi 提供，auth.js 只保留
  // 认证相关接口（login/logout/refreshToken/getCaptcha）。原 authApi.getProfile 的调用方
  // 已迁移到 userApi.getProfile（见 stores/user.js）。
}
