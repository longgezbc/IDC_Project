import request from '@/utils/request'

/**
 * 用户设置模块 API
 *
 * 面向当前登录用户，提供个人资料、密码与通知偏好设置的读取与更新接口。
 * @module api/user
 */
export const userApi = {
  /**
   * 获取个人资料
   *
   * skipAuthRedirect: 页面刷新时 MainLayout 会静默调用此接口刷新用户信息。
   * 如果 token 过期且 refresh 失败，不应直接跳登录页（用户只是刷新页面，
   * 不是主动操作），而是使用本地缓存的 userInfo 兜底，等用户下次主动操作
   * 时再触发 401 跳转。这样避免"刷新页面就被踢到登录页"的糟糕体验。
   * @returns {Promise<any>} 当前用户的个人资料
   */
  getProfile() {
    return request({
      url: '/user/profile',
      method: 'get',
      skipAuthRedirect: true
    })
  },

  /**
   * 更新个人资料
   * @param {Object} data 待更新的用户资料字段
   * @returns {Promise<any>} 更新后的用户资料
   */
  updateProfile(data) {
    return request({
      url: '/user/profile',
      method: 'put',
      data
    })
  },

  /**
   * 修改密码
   * @param {{ oldPassword: string, newPassword: string, [key: string]: any }} data 旧密码与新密码
   * @returns {Promise<any>} 密码修改结果
   */
  changePassword(data) {
    return request({
      url: '/user/password',
      method: 'put',
      data
    })
  },

  /**
   * 获取通知设置
   * @returns {Promise<any>} 当前用户的通知偏好配置
   */
  getNotificationSettings() {
    return request({
      url: '/user/notifications',
      method: 'get'
    })
  },

  /**
   * 更新通知设置
   * @param {Object} data 待更新的通知偏好配置
   * @returns {Promise<any>} 更新后的通知设置
   */
  updateNotificationSettings(data) {
    return request({
      url: '/user/notifications',
      method: 'put',
      data
    })
  }
}
