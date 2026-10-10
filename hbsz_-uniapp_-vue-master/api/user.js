/**
 * 用户设置模块 API
 *
 * 与 Web 端共享后端接口契约（/user/*）。
 * 业务模块：个人中心（当前登录用户的自助设置）。
 * 对应后端：UserController（@RequestMapping("/user")）。
 *
 * 提供当前登录用户的：
 *   - 个人资料查询与更新（GET/PUT /user/profile）
 *   - 登录密码修改（PUT /user/password）
 *   - 通知偏好设置查询与更新（GET/PUT /user/notifications）
 *
 * 注意：本模块均为「当前登录用户」自身操作，无需传 userId，
 *       后端通过 JWT 解析当前登录用户。
 *
 * @module api/user
 */
import request from '@/utils/request'

export const userApi = {
	/**
	 * 获取当前用户信息
	 * 对应后端：GET /user/profile → UserInfoVO
	 *
	 * @returns {Promise<Object>} 当前登录用户详细信息
	 *   - id, username, realName, avatar, email, phone, ...
	 *   - roles: string[] 角色码列表
	 *   - permissions: string[] 权限码列表（如有）
	 */
	getProfile() {
		return request({ url: '/user/profile', method: 'GET' })
	},

	/**
	 * 更新个人资料
	 * 对应后端：PUT /user/profile ← UserProfileDTO → UserInfoVO
	 *
	 * @param {Object} data 个人资料字段（realName、avatar、email、phone 等）
	 * @returns {Promise<Object>} 更新后的用户信息
	 */
	updateProfile(data) {
		return request({ url: '/user/profile', method: 'PUT', data })
	},

	/**
	 * 修改登录密码
	 * 对应后端：PUT /user/password ← ChangePasswordDTO → Void
	 *
	 * 业务规则：
	 *   - 校验原密码后修改为新密码
	 *   - 修改成功后后端不会主动失效 Token，前端应在调用成功后引导重新登录
	 *
	 * @param {Object} data 修改密码参数
	 *   - oldPassword: string 原密码
	 *   - newPassword: string 新密码（需符合后端复杂度校验）
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	changePassword(data) {
		return request({ url: '/user/password', method: 'PUT', data })
	},

	/**
	 * 获取通知偏好设置
	 * 对应后端：GET /user/notifications → NotificationSettingDTO
	 *
	 * @returns {Promise<Object>} 通知设置
	 *   - emailNotify: boolean 邮件通知
	 *   - smsNotify: boolean 短信通知
	 *   - pushNotify: boolean 应用内推送
	 *   - dailyReportNotify: boolean 日报通知
	 *   - taskOverdueNotify: boolean 任务逾期通知
	 *   - ... 其他可由后端扩展
	 */
	getNotificationSettings() {
		return request({ url: '/user/notifications', method: 'GET' })
	},

	/**
	 * 更新通知偏好设置
	 * 对应后端：PUT /user/notifications ← NotificationSettingDTO → NotificationSettingDTO
	 *
	 * @param {Object} data 通知设置字段（结构同 getNotificationSettings 返回值）
	 * @returns {Promise<Object>} 更新后的通知设置
	 */
	updateNotificationSettings(data) {
		return request({ url: '/user/notifications', method: 'PUT', data })
	}
}
