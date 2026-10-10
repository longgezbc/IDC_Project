import request from '@/utils/request'

/**
 * 站内通知模块 API
 *
 * 提供通知列表查询、未读数统计、标记已读、删除等接口。
 * 所有方法均返回 request 产生的 Promise，响应体已由拦截器统一解包。
 * @module api/notification
 */
export const notificationApi = {
  /**
   * 通知列表查询（分页）
   * @param {Object} params 查询参数，如 { pageNum, pageSize, notifyType, isRead }
   * @returns {Promise<any>} 分页通知列表
   */
  getList(params) {
    return request({
      url: '/notifications',
      method: 'get',
      params
    })
  },

  /**
   * 获取当前用户未读通知数
   * @returns {Promise<any>} 未读数量
   */
  getUnreadCount() {
    return request({
      url: '/notifications/unread-count',
      method: 'get'
    })
  },

  /**
   * 标记单条通知为已读
   * @param {number|string} id 通知ID
   * @returns {Promise<any>}
   */
  markAsRead(id) {
    return request({
      url: `/notifications/${id}/read`,
      method: 'put'
    })
  },

  /**
   * 将全部未读通知标记为已读
   * @returns {Promise<any>}
   */
  markAllAsRead() {
    return request({
      url: '/notifications/read-all',
      method: 'put'
    })
  },

  /**
   * 删除单条通知
   * @param {number|string} id 通知ID
   * @returns {Promise<any>}
   */
  remove(id) {
    return request({
      url: `/notifications/${id}`,
      method: 'delete'
    })
  }
}
