/**
 * 进度管理模块 API
 * 封装任务的增删改查、进度填报、进度审核、文件上传、项目整体进度及关键节点提醒等接口。
 * 与 Web 端共享后端接口契约（/tasks/*、/projects/{id}/tasks/*）。
 */
import request from '@/utils/request'
import { API_BASE_URL } from '@/utils/config'
import { auth } from '@/utils/auth'

export const taskApi = {
	/**
	 * 项目任务列表
	 * @param {string|number} projectId 项目ID
	 * @param {Object} [params] 额外查询参数
	 * @returns {Promise<Array>} 任务列表
	 */
	getList(projectId, params) {
		return request({ url: `/projects/${projectId}/tasks`, method: 'GET', params })
	},

	/**
	 * 任务详情
	 * @param {string|number} id 任务ID
	 * @returns {Promise<Object>} 任务详情
	 */
	getDetail(id) {
		return request({ url: `/tasks/${id}`, method: 'GET' })
	},

	/**
	 * 新建任务
	 * @param {string|number} projectId 所属项目ID
	 * @param {Object} data 任务字段
	 * @returns {Promise<Object>} 新建的任务
	 */
	create(projectId, data) {
		return request({ url: `/projects/${projectId}/tasks`, method: 'POST', data })
	},

	/**
	 * 更新任务
	 * @param {string|number} id 任务ID
	 * @param {Object} data 待更新字段
	 * @returns {Promise<Object>} 更新后的任务
	 */
	update(id, data) {
		return request({ url: `/tasks/${id}`, method: 'PUT', data })
	},

	/**
	 * 删除任务
	 * @param {string|number} id 任务ID
	 * @returns {Promise<any>}
	 */
	remove(id) {
		return request({ url: `/tasks/${id}`, method: 'DELETE' })
	},

	/**
	 * 提交进度填报
	 * @param {string|number} id 任务ID
	 * @param {Object} data 进度数据
	 *   - progress: number 进度百分比
	 *   - remark?: string 进度说明
	 *   - images?: string[] 施工图片路径数组
	 * @returns {Promise<Object>} 更新后的任务
	 */
	reportProgress(id, data) {
		return request({ url: `/tasks/${id}/progress`, method: 'PUT', data })
	},

	/**
	 * 审核进度填报（PM 视角）
	 * @param {string|number} logId 进度日志ID
	 * @param {Object} data { approve: boolean, remark?: string }
	 * @returns {Promise<Object>} 审核后的日志对象
	 */
	reviewProgress(logId, data) {
		return request({ url: `/tasks/progress/${logId}/review`, method: 'PUT', data })
	},

	/**
	 * 待审核进度日志列表（PM 视角）
	 * @param {Object} [params] { projectId? }
	 * @returns {Promise<Array>} 待审核日志列表
	 */
	listPendingProgress(params) {
		return request({ url: '/tasks/progress/pending', method: 'GET', params })
	},

	/**
	 * 任务进度日志（含审核状态）
	 * @param {string|number} id 任务ID
	 * @returns {Promise<Array>} 进度日志列表
	 */
	getProgressLogs(id) {
		return request({ url: `/tasks/${id}/logs`, method: 'GET' })
	},

	/**
	 * 上传文件（施工图片）
	 * 使用 uni.uploadFile 直传 multipart/form-data，
	 * 后端返回 { code, message, data: { url, previewUrl } }，本方法统一解包 data。
	 * @param {string} filePath 临时文件路径（uni.chooseImage 返回的 tempFilePaths[i]）
	 * @returns {Promise<Object>} 上传结果 { url, previewUrl }
	 */
	uploadFile(filePath) {
		return new Promise((resolve, reject) => {
			const token = auth.getToken()
			uni.uploadFile({
				url: API_BASE_URL + '/files/upload',
				filePath,
				name: 'file',
				header: token ? { Authorization: `Bearer ${token}` } : {},
				success: (res) => {
					try {
						const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
						if (body && body.code === 200 && body.data) {
							resolve(body.data)
						} else {
							uni.showToast({ title: (body && body.message) || '上传失败', icon: 'none' })
							reject(new Error((body && body.message) || '上传失败'))
						}
					} catch (e) {
						uni.showToast({ title: '上传响应解析失败', icon: 'none' })
						reject(e)
					}
				},
				fail: (err) => {
					uni.showToast({ title: '上传失败', icon: 'none' })
					reject(err)
				}
			})
		})
	},

	/**
	 * 项目整体进度
	 * @param {string|number} projectId 项目ID
	 * @returns {Promise<Object>} 进度汇总
	 */
	getProjectProgress(projectId) {
		return request({ url: `/projects/${projectId}/progress`, method: 'GET' })
	},

	/**
	 * 指定项目的关键节点提醒（临近截止的任务）
	 * @param {string|number} projectId 项目ID
	 * @returns {Promise<Array>} 临近任务列表
	 */
	getUpcomingByProject(projectId) {
		return request({ url: `/projects/${projectId}/tasks/upcoming`, method: 'GET' })
	},

	/**
	 * 全部关键节点提醒（首页汇总用）
	 * @returns {Promise<Array>} 临近任务列表
	 */
	getUpcomingAll() {
		return request({ url: '/tasks/upcoming', method: 'GET' })
	}
}
