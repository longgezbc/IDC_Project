/**
 * 客户管理模块 API
 *
 * 与 Web 端共享后端接口契约（/customers/*）。
 * 移动端以查看为主，新增/编辑/删除为可选能力（受权限码控制）。
 * @module api/customer
 */
import request from '@/utils/request'

export const customerApi = {
	/**
	 * 客户列表（分页/筛选）
	 * @param {Object} params { pageNum, pageSize, keyword?, status? }
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getList(params) {
		return request({ url: '/customers', method: 'GET', params })
	},

	/**
	 * 所有启用客户（下拉用，不分页）
	 * @returns {Promise<Array>} 客户数组
	 */
	listAll() {
		return request({ url: '/customers/all', method: 'GET' })
	},

	/**
	 * 客户详情
	 * @param {number|string} id 客户ID
	 * @returns {Promise<Object>} 客户详情
	 */
	getDetail(id) {
		return request({ url: `/customers/${id}`, method: 'GET' })
	},

	/**
	 * 新建客户
	 * @param {Object} data 客户字段
	 * @returns {Promise<Object>} 新建后的客户
	 */
	create(data) {
		return request({ url: '/customers', method: 'POST', data })
	},

	/**
	 * 更新客户
	 * @param {number|string} id 客户ID
	 * @param {Object} data 待更新字段
	 * @returns {Promise<Object>} 更新后的客户
	 */
	update(id, data) {
		return request({ url: `/customers/${id}`, method: 'PUT', data })
	},

	/**
	 * 删除客户（有关联项目时后端禁止）
	 * @param {number|string} id 客户ID
	 * @returns {Promise<any>}
	 */
	remove(id) {
		return request({ url: `/customers/${id}`, method: 'DELETE' })
	}
}
