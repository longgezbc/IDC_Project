/**
 * 日报导出模块 API
 *
 * 与 Web 端共享后端接口契约（/daily-report/*）。
 * 对应后端：DailyReportController（@RequestMapping("/daily-report")）。
 *
 * 业务模块：日报 - 项目进展、任务进展、延期任务预览与导出。
 * 注意：小程序不适合 Excel 导出，仅做预览；导出/下载走 uni.downloadFile，需手动注入 Authorization。
 *
 * 权限要求：
 *   - statistics:query
 *
 * @module api/dailyReport
 */
import request from '@/utils/request'
import { API_BASE_URL } from '@/utils/config'
import { auth } from '@/utils/auth'

export const dailyReportApi = {
	/**
	 * 预览日报数据
	 * 对应后端：GET /daily-report/preview → DailyReportVO，权限 statistics:query
	 *
	 * 后端契约：startDate + endDate 均为 @NotBlank 必填参数
	 *
	 * @param {Object} params 查询参数
	 *   - startDate: string 必填，开始日期 yyyy-MM-dd
	 *   - endDate: string 必填，结束日期 yyyy-MM-dd
	 *   - projectId?: number 可选，项目ID
	 * @returns {Promise<Object>} 日报数据
	 *   - overview: Object 概览
	 *   - projectProgress: Array 项目进展
	 *   - taskProgress: Array 任务进展（含 startDate/endDate/actualStartDate/actualEndDate）
	 *   - delayedTasks: Array 延期任务
	 */
	preview(params) {
		return request({ url: '/daily-report/preview', method: 'GET', params })
	},

	/**
	 * 导出日报
	 * 对应后端：POST /daily-report/export → Void（后端生成 Excel 并写入历史），权限 statistics:query
	 * 后端 @OperationLog 记录操作日志
	 *
	 * 后端契约：startDate + endDate 均为 @NotBlank 必填参数
	 *
	 * @param {Object} params 导出参数
	 *   - startDate: string 必填，开始日期
	 *   - endDate: string 必填，结束日期
	 *   - projectId?: number 可选
	 * @returns {Promise<any>} 操作结果
	 */
	export(params) {
		return request({ url: '/daily-report/export', method: 'POST', params })
	},

	/**
	 * 导出历史（分页）
	 * 对应后端：GET /daily-report/history → PageResult<DailyReportHistoryVO>，权限 statistics:query
	 *
	 * @param {Object} params 分页参数
	 *   - pageNum, pageSize: number 分页
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getHistory(params) {
		return request({ url: '/daily-report/history', method: 'GET', params })
	},

	/**
	 * 下载报表文件
	 * 走 uni.downloadFile（需手动注入 Authorization），返回临时文件路径
	 *
	 * @param {string|number} id 历史记录ID
	 * @returns {Promise<string>} 临时文件路径
	 */
	download(id) {
		const token = auth.getToken()
		return new Promise((resolve, reject) => {
			uni.showLoading({ title: '下载中...', mask: true })
			uni.downloadFile({
				url: `${API_BASE_URL}/daily-report/download/${id}`,
				header: { Authorization: token ? `Bearer ${token}` : '' },
				success: (res) => {
					uni.hideLoading()
					if (res.statusCode === 200) {
						resolve(res.tempFilePath)
					} else {
						uni.showToast({ title: '下载失败', icon: 'none' })
						reject(new Error('下载失败: ' + res.statusCode))
					}
				},
				fail: (err) => {
					uni.hideLoading()
					uni.showToast({ title: '下载失败', icon: 'none' })
					reject(err)
				}
			})
		})
	},

	/**
	 * 删除导出历史
	 * 对应后端：DELETE /daily-report/history/{id} → Void，权限 statistics:query
	 * 后端 @OperationLog 记录操作日志
	 *
	 * @param {string|number} id 历史记录ID
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	delete(id) {
		return request({ url: `/daily-report/history/${id}`, method: 'DELETE' })
	}
}
