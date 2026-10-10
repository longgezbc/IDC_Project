/**
 * 审批流程图模块 API（小程序端）
 *
 * 与 Web 端共享后端接口契约（/approval-flow/*）。
 * 将后端业务审批数据转换为流程图组件所需的通用 {nodes, links} 数据结构。
 * @module api/approvalFlow
 */
import request from '@/utils/request'

export const approvalFlowApi = {
	/**
	 * 获取项目授权申请的审批流程图数据
	 * @param {number|string} id 授权申请ID
	 * @returns {Promise<Object>} 流程图 VO { flowType, bizId, nodes, links, ... }
	 */
	getAuthRequestFlow(id) {
		return request({ url: `/approval-flow/auth-request/${id}`, method: 'GET' })
	},

	/**
	 * 获取任务进度审核的审批流程图数据
	 * @param {number|string} id 进度填报记录ID
	 * @returns {Promise<Object>} 流程图 VO
	 */
	getProgressReviewFlow(id) {
		return request({ url: `/approval-flow/progress-review/${id}`, method: 'GET' })
	},

	/**
	 * 获取项目转移的审批流程图数据
	 * @param {number|string} id 转移日志ID
	 * @returns {Promise<Object>} 流程图 VO
	 */
	getProjectTransferFlow(id) {
		return request({ url: `/approval-flow/project-transfer/${id}`, method: 'GET' })
	},

	/**
	 * 获取授权申请列表
	 * @returns {Promise<Array>} 授权申请简要列表
	 */
	listAuthRequests() {
		return request({ url: '/approval-flow/auth-requests', method: 'GET' })
	},

	/**
	 * 获取进度填报列表
	 * @returns {Promise<Array>} 进度填报简要列表
	 */
	listProgressReviews() {
		return request({ url: '/approval-flow/progress-reviews', method: 'GET' })
	},

	/**
	 * 获取项目转移日志列表
	 * @returns {Promise<Array>} 转移日志简要列表
	 */
	listProjectTransfers() {
		return request({ url: '/approval-flow/project-transfers', method: 'GET' })
	}
}
