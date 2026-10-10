/**
 * 系统用户管理模块 API
 *
 * 与 Web 端共享后端接口契约（/system/users/*）。
 * 对应后端：UserManageController（@RequestMapping("/system/users")）。
 *
 * 业务模块：系统管理 - 用户账号管理。
 * 小程序端仅支持紧急场景的查询、状态切换、重置密码、详情查看，
 * 完整 CRUD、批量导入、角色分配等复杂操作保留在 Web 端。
 *
 * 权限要求：
 *   - system:user:query（查询）
 *   - system:user:status（启用/禁用）
 *   - system:user:reset（重置密码）
 *
 * @module api/systemUser
 */
import request from '@/utils/request'

export const systemUserApi = {
	/**
	 * 用户列表（分页）
	 * 对应后端：GET /system/users → PageResult<UserVO>，权限 system:user:query
	 *
	 * @param {Object} params 查询参数
	 *   - pageNum, pageSize: number 分页
	 *   - keyword?: string 用户名/姓名/手机号关键字
	 *   - status?: number 状态（1=启用 0=禁用）
	 *   - roleId?: number 角色 ID
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getList(params) {
		return request({ url: '/system/users', method: 'GET', params })
	},

	/**
	 * 用户详情
	 * 对应后端：GET /system/users/{id} → UserVO，权限 system:user:query
	 *
	 * @param {string|number} id 用户ID
	 * @returns {Promise<Object>} 用户详情
	 */
	getDetail(id) {
		return request({ url: `/system/users/${id}`, method: 'GET' })
	},

	/**
	 * 修改用户状态（启用/禁用）
	 * 对应后端：PUT /system/users/{id}/status?status={status} → Void，权限 system:user:status
	 * 后端 @OperationLog 记录操作日志
	 *
	 * @param {string|number} id 用户ID
	 * @param {number} status 目标状态（1=启用 0=禁用）
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	updateStatus(id, status) {
		// 注意：后端使用 @RequestParam，需通过 params 传递，避免 body 解析失败
		return request({ url: `/system/users/${id}/status`, method: 'PUT', params: { status } })
	},

	/**
	 * 重置用户密码
	 * 对应后端：PUT /system/users/{id}/password ← ResetPasswordDTO → Void，权限 system:user:reset
	 * 后端 @OperationLog 记录操作日志（不记录参数，避免密码泄露）
	 *
	 * @param {string|number} id 用户ID
	 * @param {Object} data 重置参数
	 *   - newPassword: string 新密码
	 *   - confirmPassword?: string 确认密码（如后端要求）
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	resetPassword(id, data) {
		return request({ url: `/system/users/${id}/password`, method: 'PUT', data })
	}
}
