/**
 * 工作量统计模块 API（小程序/App 端）
 *
 * 与 Web 端共享后端接口契约（/workload/*）。
 * 业务规则：项目组授权成员每日填报（保存后进入待审核状态）、
 * 项目经理审核工作流（批量通过/驳回，CAS 语义）、
 * 统计矩阵与绩效汇总仅计算已审核通过的数据。
 *
 * 移动端主要使用场景：
 *   - 工程师：listItems + saveRecords（每日填报）
 *   - PM：listPendingRecords + reviewRecords（审核）
 *   - 其余方法（矩阵/绩效/导出/清单维护）与 Web 端契约对齐，供后续扩展
 *
 * @module api/workload
 */
import request from '@/utils/request'

export const workloadApi = {
	/**
	 * 查询项目可填报人员列表（项目组在组成员，组长置前）
	 * 填报人员只能从项目组授权成员中选择（业务硬约束）
	 * @param {Object} params 查询参数
	 * @param {number|string} params.projectId 项目ID
	 * @returns {Promise<Array>} 成员列表（userId/name/roleInTeam）
	 */
	listWorkers(params) {
		return request({ url: '/workload/workers', method: 'GET', params })
	},

	/**
	 * 查询填报明细审核列表（按项目/日期/状态筛选，供项目经理审核）
	 * @param {Object} params 查询参数
	 * @param {number|string} params.projectId 项目ID（必填）
	 * @param {string} [params.recordDate] 填报日期 YYYY-MM-DD（不传查全部日期）
	 * @param {string} [params.reviewStatus] 审核状态 pending/approved/rejected，默认 pending
	 * @returns {Promise<Array>} 填报明细列表（含作业上下文与审核信息）
	 */
	listPendingRecords(params) {
		return request({ url: '/workload/records/pending', method: 'GET', params })
	},

	/**
	 * 批量审核填报明细（通过/驳回，仅项目负责人或管理员）
	 * CAS 语义：仅待审核记录可审核，返回实际成功条数
	 * @param {Object} data 审核数据
	 * @param {Array<number>} data.ids 待审核记录ID列表
	 * @param {boolean} data.approve true=通过 false=驳回
	 * @param {string} [data.remark] 审核意见（驳回时建议填写原因）
	 * @returns {Promise<number>} 实际审核成功的记录数
	 */
	reviewRecords(data) {
		return request({ url: '/workload/records/review', method: 'POST', data })
	},

	/**
	 * 查询项目工作量清单（含实时聚合统计）
	 * @param {Object} params 查询参数
	 * @param {number|string} params.projectId 项目ID
	 * @param {string} [params.statisticsDate] 统计日期 YYYY-MM-DD（默认今天，决定"今日工作量"归属日）
	 * @returns {Promise<Array>} 清单列表（每项含 completedQuantity/remainingQuantity/todayQuantity）
	 */
	listItems(params) {
		return request({ url: '/workload/items', method: 'GET', params })
	},

	/**
	 * 新增清单项
	 * @param {Object} data 清单项数据（projectId/category/workScope/ratedWorkload/unit/...）
	 * @returns {Promise<Object>} 新增后的清单项
	 */
	createItem(data) {
		return request({ url: '/workload/items', method: 'POST', data })
	},

	/**
	 * 更新清单项
	 * @param {number|string} id 清单项ID
	 * @param {Object} data 清单项数据
	 * @returns {Promise<Object>} 更新后的清单项
	 */
	updateItem(id, data) {
		return request({ url: `/workload/items/${id}`, method: 'PUT', data })
	},

	/**
	 * 删除清单项（级联删除其填报明细）
	 * @param {number|string} id 清单项ID
	 * @returns {Promise<Object>} 删除结果
	 */
	removeItem(id) {
		return request({ url: `/workload/items/${id}`, method: 'DELETE' })
	},

	/**
	 * 导入标准模板清单（106 项 / 15 类作业目录）
	 * 项目已有清单时后端拒绝导入
	 * @param {number|string} projectId 项目ID
	 * @returns {Promise<number>} 导入数量
	 */
	importTemplate(projectId) {
		return request({ url: '/workload/items/import-template', method: 'POST', params: { projectId } })
	},

	/**
	 * 查询某日统计矩阵：清单行 × 人员列 + 每人当日绩效
	 * 矩阵单元格含审核状态（approved 锁定 / pending 待审 / rejected 驳回）；
	 * 统计列与绩效仅计算已审核通过的记录
	 * @param {Object} params 查询参数
	 * @param {number|string} params.projectId 项目ID
	 * @param {string} [params.date] 统计日期 YYYY-MM-DD（默认今天）
	 * @returns {Promise<Object>} 矩阵数据（items/workers/matrix/workerPerformance/pendingCount）
	 */
	getMatrix(params) {
		return request({ url: '/workload/matrix', method: 'GET', params })
	},

	/**
	 * 批量保存某日填报明细（填报人员必须是项目组在组成员）
	 * 保存后进入待审核状态；已审核通过的记录不可直接修改
	 * @param {Object} data 填报数据（projectId/recordDate/records[{itemId,workerUserId,quantity}]）
	 * @returns {Promise<Object>} 保存结果
	 */
	saveRecords(data) {
		return request({ url: '/workload/records', method: 'POST', data })
	},

	/**
	 * 查询人员绩效区间汇总（按绩效分降序）
	 * @param {Object} params 查询参数
	 * @param {number|string} params.projectId 项目ID
	 * @param {string} params.startDate 开始日期 YYYY-MM-DD
	 * @param {string} params.endDate 结束日期 YYYY-MM-DD
	 * @returns {Promise<Array>} 人员绩效列表（workerName/totalQuantity/performanceScore/recordDays/avgDailyPerformance）
	 */
	getPerformance(params) {
		return request({ url: '/workload/performance', method: 'GET', params })
	},

	/**
	 * 导出某日工作量统计 Excel（模板样式）
	 * 导出文件下载复用日报下载通道（/daily-report/download/{id}）
	 * @param {Object} params 导出参数
	 * @param {number|string} params.projectId 项目ID
	 * @param {string} [params.date] 统计日期 YYYY-MM-DD（默认今天）
	 * @returns {Promise<Object>} 导出记录（id/fileName/fileSizeText）
	 */
	exportExcel(params) {
		return request({ url: '/workload/export', method: 'POST', params })
	}
}
