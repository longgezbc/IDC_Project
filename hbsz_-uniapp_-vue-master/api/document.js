/**
 * 文档管理模块 API
 * 封装文档列表/详情/更新/删除、分类统计，以及带鉴权的预览与下载能力。
 * 说明：预览/下载走 uni.downloadFile 而非通用 request，需手动注入 Authorization。
 */
import request from '@/utils/request'
import { API_BASE_URL } from '@/utils/config'
import { auth } from '@/utils/auth'

export const documentApi = {
	/**
	 * 文档列表（分页）
	 * @param {Object} params 查询参数（page、size、keyword、category、projectId 等）
	 * @returns {Promise<Object>} 分页结果
	 */
	getList(params) {
		return request({ url: '/documents', method: 'GET', params })
	},

	/**
	 * 文档分类统计
	 * @returns {Promise<Array>} 各分类的数量统计
	 */
	getCategoryStats() {
		return request({ url: '/documents/category-stats', method: 'GET' })
	},

	/**
	 * 文档详情
	 * @param {string|number} id 文档ID
	 * @returns {Promise<Object>} 文档详情
	 */
	getDetail(id) {
		return request({ url: `/documents/${id}`, method: 'GET' })
	},

	/**
	 * 更新文档信息
	 * @param {string|number} id 文档ID
	 * @param {Object} data 待更新字段
	 * @returns {Promise<Object>} 更新后的文档
	 */
	update(id, data) {
		return request({ url: `/documents/${id}`, method: 'PUT', data })
	},

	/**
	 * 删除文档
	 * @param {string|number} id 文档ID
	 * @returns {Promise<any>}
	 */
	remove(id) {
		return request({ url: `/documents/${id}`, method: 'DELETE' })
	},

	/**
	 * 获取带鉴权的预览/下载完整 URL
	 * @param {string|number} id 文档ID
	 * @param {'preview'|'download'} type
	 */
	getFileUrl(id, type = 'preview') {
		return `${API_BASE_URL}/documents/${id}/${type}`
	},

	/**
	 * 审核文档（通过/驳回）
	 * 对应后端：POST /documents/{id}/review ← DocumentReviewDTO → DocumentVO，权限 document:review
	 * 后端 @OperationLog 记录操作日志
	 *
	 * @param {string|number} id 文档ID
	 * @param {Object} data 审核参数
	 *   - approve: boolean true=通过 false=驳回
	 *   - remark: string 审核意见（驳回时必填）
	 * @returns {Promise<Object>} 审核后的文档信息
	 */
	review(id, data) {
		return request({ url: `/documents/${id}/review`, method: 'POST', data })
	},

	/**
	 * 下载文档到本地（小程序保存文件）
	 * @returns {Promise<string>} 临时文件路径
	 */
	download(id) {
		const token = auth.getToken()
		return new Promise((resolve, reject) => {
			uni.showLoading({ title: '下载中...', mask: true })
			const task = uni.downloadFile({
				url: `${API_BASE_URL}/documents/${id}/download`,
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
			return task
		})
	}
}
