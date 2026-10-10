/**
 * 项目授权与转移 API
 *
 * 与 Web 端共享后端接口契约（/projects/{id}/authorization/*）。
 * 仅 PM / admin 持有 project:transfer 权限可调用；每次操作均写入审计日志。
 * @module api/projectAuthorization
 */
import request from '@/utils/request'

export const projectAuthApi = {
	/**
	 * 项目转移：撤销当前所有授权，授权给目标工程师（替换模式）
	 * @param {number|string} projectId 项目ID
	 * @param {Object} data { toEngineerId, remark? }
	 * @returns {Promise<Object>} 新授权记录
	 */
	transfer(projectId, data) {
		return request({
			url: `/projects/${projectId}/authorization/transfer`,
			method: 'POST',
			data
		})
	},

	/**
	 * 项目授权：为项目新增一名工程师（新增模式，不影响已有授权）
	 * @param {number|string} projectId 项目ID
	 * @param {Object} data { toEngineerId, remark? }
	 * @returns {Promise<Object>} 新授权记录
	 */
	assign(projectId, data) {
		return request({
			url: `/projects/${projectId}/authorization`,
			method: 'POST',
			data
		})
	},

	/**
	 * 撤销授权
	 * @param {number|string} projectId 项目ID
	 * @param {number|string} engineerId 工程师ID
	 * @param {string} [remark] 操作备注（记入审计日志）
	 * @returns {Promise<any>}
	 */
	revoke(projectId, engineerId, remark) {
		return request({
			url: `/projects/${projectId}/authorization/${engineerId}`,
			method: 'DELETE',
			params: remark ? { remark } : undefined
		})
	},

	/**
	 * 查询项目下所有有效授权工程师
	 * @param {number|string} projectId 项目ID
	 * @returns {Promise<Array>} 工程师授权列表
	 */
	listProjectEngineers(projectId) {
		return request({ url: `/projects/${projectId}/authorization`, method: 'GET' })
	},

	/**
	 * 查询所有启用状态的工程师（转移/授权下拉用）
	 * @returns {Promise<Array>} 工程师列表
	 */
	listActiveEngineers() {
		return request({ url: '/projects/authorization/engineers', method: 'GET' })
	},

	/**
	 * 分页查询转移审计日志
	 * @param {Object} params { pageNum, pageSize, projectId?, operatedBy?, actionType?, dateBegin?, dateEnd? }
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	transferLogs(params) {
		return request({ url: '/projects/authorization/logs', method: 'GET', params })
	}
}
