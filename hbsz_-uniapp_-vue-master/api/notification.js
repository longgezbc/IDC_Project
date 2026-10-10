/**
 * 站内通知模块 API
 *
 * 与 Web 端共享后端接口契约（/notifications/*）。
 * 通知实时推送由 WebSocket 适配层（utils/ws.js）独立处理，本模块仅提供 REST 查询与操作。
 * @module api/notification
 */
import request from '@/utils/request'

export const notificationApi = {
	/**
	 * 通知列表（分页/筛选）
	 * @param {Object} params { pageNum, pageSize, notifyType?, isRead? }
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getList(params) {
		return request({ url: '/notifications', method: 'GET', params })
	},

	/**
	 * 当前用户未读通知数（用于铃铛角标）
	 * @returns {Promise<number>} 未读数
	 */
	getUnreadCount() {
		return request({ url: '/notifications/unread-count', method: 'GET' })
	},

	/**
	 * 标记单条已读
	 * @param {number|string} id 通知ID
	 * @returns {Promise<any>}
	 */
	markAsRead(id) {
		return request({ url: `/notifications/${id}/read`, method: 'PUT' })
	},

	/**
	 * 全部标记已读
	 * @returns {Promise<any>}
	 */
	markAllAsRead() {
		return request({ url: '/notifications/read-all', method: 'PUT' })
	},

	/**
	 * 删除单条通知
	 * @param {number|string} id 通知ID
	 * @returns {Promise<any>}
	 */
	remove(id) {
		return request({ url: `/notifications/${id}`, method: 'DELETE' })
	}
}
