/**
 * 归档管理模块 API
 *
 * 与 Web 端共享后端接口契约（/projects/archived/*）。
 * 对应后端：ArchiveController（@RequestMapping("/projects/archived")）。
 *
 * 业务模块：归档中心 - 已归档项目查询、归档统计、恢复归档。
 *
 * 权限要求：
 *   - archive:query（查询归档列表/统计）
 *   - project:archive（恢复归档）
 *
 * @module api/archive
 */
import request from '@/utils/request'

export const archiveApi = {
	/**
	 * 已归档项目列表（分页）
	 * 对应后端：GET /projects/archived → PageResult<ProjectVO>，权限 archive:query
	 *
	 * @param {Object} params 查询参数
	 *   - pageNum, pageSize: number 分页
	 *   - keyword?: string 项目名称/编号关键字
	 *   - status?: string 项目状态
	 *   - customerId?: number 客户ID
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getList(params) {
		return request({ url: '/projects/archived', method: 'GET', params })
	},

	/**
	 * 归档统计
	 * 对应后端：GET /projects/archived/statistics → ArchiveStatisticsVO，权限 archive:query
	 *
	 * @returns {Promise<Object>} 归档统计
	 *   - archivedCount: number 已归档项目数
	 *   - completedCount: number 已完成项目数
	 *   - totalAmount: number 合同总金额
	 *   - documentCount: number 文档总数
	 */
	getStatistics() {
		return request({ url: '/projects/archived/statistics', method: 'GET' })
	},

	/**
	 * 恢复归档
	 * 对应后端：PUT /projects/{id}/unarchive → ProjectVO，权限 project:archive
	 * 后端 @OperationLog 记录操作日志
	 *
	 * @param {string|number} id 项目ID
	 * @returns {Promise<Object>} 恢复后的项目
	 */
	restore(id) {
		return request({ url: `/projects/${id}/unarchive`, method: 'PUT' })
	}
}
