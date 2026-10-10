/**
 * 统计分析模块 API
 *
 * 与 Web 端共享后端接口契约（/statistics/*）。
 * 对应后端：StatisticsController（@RequestMapping("/statistics")）。
 *
 * 业务模块：统计分析 - 项目概览、状态/类型/优先级分布、月度趋势、金额统计及报表导出。
 *
 * 权限要求：
 *   - statistics:query（admin/PM 可查全局，工程师仅查自己负责的项目）
 *
 * @module api/statistics
 */
import request from '@/utils/request'

export const statisticsApi = {
	/**
	 * 综合统计概览
	 * 对应后端：GET /statistics/overview → OverviewVO，权限 statistics:query
	 *
	 * @returns {Promise<Object>} 概览数据
	 *   - totalProjects: number 项目总数
	 *   - activeProjects: number 进行中项目数
	 *   - completedProjects: number 已完成项目数
	 *   - archivedProjects: number 已归档项目数
	 *   - totalAmount: number 合同总金额
	 *   - avgProgress: number 平均进度
	 */
	getOverview() {
		return request({ url: '/statistics/overview', method: 'GET' })
	},

	/**
	 * 项目状态分布
	 * 对应后端：GET /statistics/project-status → List<NameValueVO>，权限 statistics:query
	 *
	 * @param {Object} [params] 查询参数（可选筛选条件）
	 * @returns {Promise<Array>} 状态分布 [{ name, value }]
	 */
	getStatusDistribution(params) {
		return request({ url: '/statistics/project-status', method: 'GET', params })
	},

	/**
	 * 项目类型分布
	 * 对应后端：GET /statistics/project-type → List<NameValueVO>，权限 statistics:query
	 *
	 * @param {Object} [params] 查询参数（可选筛选条件）
	 * @returns {Promise<Array>} 类型分布 [{ name, value }]
	 */
	getTypeDistribution(params) {
		return request({ url: '/statistics/project-type', method: 'GET', params })
	},

	/**
	 * 项目优先级分布
	 * 对应后端：GET /statistics/project-priority → List<NameValueVO>，权限 statistics:query
	 *
	 * @param {Object} [params] 查询参数（可选筛选条件）
	 * @returns {Promise<Array>} 优先级分布 [{ name, value }]
	 */
	getPriorityDistribution(params) {
		return request({ url: '/statistics/project-priority', method: 'GET', params })
	},

	/**
	 * 项目趋势统计（月度）
	 * 对应后端：GET /statistics/project-trend → List<TrendVO>，权限 statistics:query
	 *
	 * @param {Object} params 查询参数
	 *   - startMonth?: string 起始月份（如 '2026-01'）
	 *   - endMonth?: string 结束月份（如 '2026-07'）
	 * @returns {Promise<Array>} 月度趋势数据
	 */
	getProjectTrend(params) {
		return request({ url: '/statistics/project-trend', method: 'GET', params })
	},

	/**
	 * 金额统计
	 * 对应后端：GET /statistics/amount → AmountVO，权限 statistics:query
	 * 返回合同总额、已收金额、未收金额等
	 *
	 * @param {Object} [params] 查询参数（可选筛选条件）
	 * @returns {Promise<Object>} 金额统计
	 */
	getAmountStatistics(params) {
		return request({ url: '/statistics/amount', method: 'GET', params })
	},

	/**
	 * 导出统计报表
	 * 对应后端：GET /statistics/export → byte[]（Excel），权限 statistics:query
	 * 注意：小程序端不适合大文件导出，建议仅 Web 端使用
	 *
	 * @param {Object} params 导出参数
	 * @returns {Promise<ArrayBuffer>} 报表二进制数据
	 */
	exportReport(params) {
		return request({ url: '/statistics/export', method: 'GET', params, responseType: 'arraybuffer' })
	}
}
