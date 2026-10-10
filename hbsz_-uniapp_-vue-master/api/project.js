/**
 * 项目管理模块 API
 *
 * 与 Web 端共享后端接口契约（/projects/*）。
 * 对应后端：
 *   - ProjectController（@RequestMapping("/projects")）：CRUD、归档、统计、关联文档
 *   - ArchiveController（@RequestMapping("/projects")，归档相关子路径 /archived/*）
 *
 * 业务模块：项目全生命周期管理 + 归档中心。
 * 移动端以查看、归档操作为主；新建/删除为可选能力（受权限码控制）。
 */
import request from '@/utils/request'

export const projectApi = {
	/**
	 * 项目列表（分页/筛选）
	 * 对应后端：GET /projects → PageResult<ProjectVO>，权限 project:query
	 *
	 * @param {Object} params 查询参数
	 *   - pageNum, pageSize: number 分页
	 *   - keyword?: string 项目名称/编号关键字
	 *   - status?: string 项目状态
	 *   - customerId?: number 客户ID
	 *   - ... 其他筛选条件由后端 ProjectQueryDTO 决定
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	getList(params) {
		return request({ url: '/projects', method: 'GET', params })
	},

	/**
	 * 项目详情
	 * 对应后端：GET /projects/{id} → ProjectVO，权限 project:query
	 *
	 * @param {string|number} id 项目ID
	 * @returns {Promise<Object>} 项目详情
	 */
	getDetail(id) {
		return request({ url: `/projects/${id}`, method: 'GET' })
	},

	/**
	 * 新建项目
	 * 对应后端：POST /projects ← ProjectSaveDTO → ProjectVO
	 *   - 权限 project:add
	 *   - 后端 @Idempotent(key="project:create", expire=300) 防重复提交（300秒内）
	 *   - 后端 @OperationLog 记录操作日志
	 *
	 * @param {Object} data 项目保存字段（ProjectSaveDTO）
	 *   - name, code, customerId, startDate, endDate, description, ...
	 * @returns {Promise<Object>} 新建后的项目
	 */
	create(data) {
		return request({ url: '/projects', method: 'POST', data })
	},

	/**
	 * 更新项目
	 * 对应后端：PUT /projects/{id} ← ProjectSaveDTO → ProjectVO
	 *   - 权限 project:edit
	 *   - 后端 @OperationLog 记录操作日志
	 *
	 * @param {string|number} id 项目ID
	 * @param {Object} data 待更新字段
	 * @returns {Promise<Object>} 更新后的项目
	 */
	update(id, data) {
		return request({ url: `/projects/${id}`, method: 'PUT', data })
	},

	/**
	 * 删除项目
	 * 对应后端：DELETE /projects/{id} → Void
	 *   - 权限 project:remove
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 业务约束：有关联任务/文档时后端可能禁止删除
	 *
	 * @param {string|number} id 项目ID
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	remove(id) {
		return request({ url: `/projects/${id}`, method: 'DELETE' })
	},

	/**
	 * 归档项目
	 * 对应后端：PUT /projects/{id}/archive → ProjectVO
	 *   - 权限 project:archive
	 *   - 后端 @OperationLog 记录操作日志
	 *
	 * @param {string|number} id 项目ID
	 * @returns {Promise<Object>} 归档后的项目
	 */
	archive(id) {
		return request({ url: `/projects/${id}/archive`, method: 'PUT' })
	},

	/**
	 * 取消归档
	 * 对应后端：PUT /projects/{id}/unarchive → ProjectVO
	 *   - 权限 project:archive
	 *   - 后端 @OperationLog 记录操作日志
	 *
	 * @param {string|number} id 项目ID
	 * @returns {Promise<Object>} 取消归档后的项目
	 */
	unarchive(id) {
		return request({ url: `/projects/${id}/unarchive`, method: 'PUT' })
	},

	/**
	 * 项目统计概览（工作台首页用）
	 * 对应后端：GET /projects/statistics → ProjectStatisticsVO，权限 project:query
	 *
	 * @returns {Promise<Object>} 统计数据
	 *   - totalCount, activeCount, completedCount, archivedCount, ...
	 */
	getStatistics() {
		return request({ url: '/projects/statistics', method: 'GET' })
	},

	/**
	 * 已归档项目列表（归档中心）
	 * 对应后端：GET /projects/archived → PageResult<ProjectVO>，权限 archive:query
	 *
	 * @param {Object} params 查询参数（同 getList，可加 keyword、status 等筛选条件）
	 * @returns {Promise<Object>} 已归档项目的分页结果 { records, total }
	 */
	getArchivedList(params) {
		return request({ url: '/projects/archived', method: 'GET', params })
	},

	/**
	 * 归档统计
	 * 对应后端：GET /projects/archived/statistics → ArchiveStatisticsVO，权限 archive:query
	 *
	 * @returns {Promise<Object>} 归档统计（已归档项目数、按状态/客户分布等）
	 */
	getArchivedStatistics() {
		return request({ url: '/projects/archived/statistics', method: 'GET' })
	},

	/**
	 * 指定项目下的文档列表
	 * 对应后端：GET /projects/{projectId}/documents → List<DocumentVO>，权限 document:query
	 *
	 * @param {string|number} projectId 项目ID
	 * @returns {Promise<Array>} 文档列表
	 */
	getDocumentsByProject(projectId) {
		return request({ url: `/projects/${projectId}/documents`, method: 'GET' })
	}
}
