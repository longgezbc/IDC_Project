/**
 * 工作台模块 API
 *
 * 提供工作台汇总统计、我的任务、我的填报、风险看板等接口。
 * 与 Web 端共享后端接口契约（/dashboard/*），数据隔离由后端按当前用户 ID 完成。
 * @module api/dashboard
 */
import request from '@/utils/request'

export const dashboardApi = {
	/**
	 * 工作台汇总统计
	 * 返回当前用户的任务数、填报数、待审核数、未读通知数等聚合字段。
	 * @returns {Promise<Object>} 汇总数据
	 */
	getSummary() {
		return request({ url: '/dashboard/summary', method: 'GET' })
	},

	/**
	 * 我的任务列表（分页）
	 * @param {Object} params { pageNum, pageSize, status }
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getMyTasks(params) {
		return request({ url: '/dashboard/my-tasks', method: 'GET', params })
	},

	/**
	 * 我的填报列表（分页）
	 * @param {Object} params { pageNum, pageSize, reviewStatus }
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getMySubmissions(params) {
		return request({ url: '/dashboard/my-submissions', method: 'GET', params })
	},

	/**
	 * PM 风险预警看板
	 * 聚合 PM 负责项目的风险信息：逾期任务、即将到期任务、待审核填报数、各项目风险等级。
	 * 仅 PM / admin 角色可访问（后端强制校验）。
	 * @returns {Promise<Object>} 风险预警看板 VO
	 */
	getRiskBoard() {
		return request({ url: '/dashboard/risk-board', method: 'GET' })
	}
}
