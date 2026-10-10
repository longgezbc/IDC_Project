/**
 * 项目授权申请与审批模块 API（新版）
 *
 * 与 Web 端共享后端接口契约（/project-auth/*）。
 * 对应后端：ProjectAuthController（@RequestMapping("/project-auth")）。
 *
 * 业务模块：组织架构升级 阶段3 - 项目授权规范化。
 * 提供项目授权申请的创建、查询、审批，以及授权关系的查询、撤销、延期等接口。
 *
 * 注意：本模块与 api/projectAuthorization.js 是两套并行机制：
 *   - api/projectAuthorization.js（旧版）：基于 /projects/{id}/authorization/* 路径，
 *     提供「项目转移/直接授权/撤销授权」等运维向接口，仅 PM/admin 可用。
 *   - api/projectAuth.js（本模块，新版）：基于 /project-auth/* 路径，
 *     提供「申请-审批」流程（PENDING → APPROVED/REJECTED），支持任意工程师发起申请、PM 审批，
 *     并保留授权关系的延期、撤销等审批后操作。
 *
 * 权限要求：
 *   - 申请/查询授权：project:auth:request
 *   - 审批/撤销/延期：project:auth:approve
 *
 * @module api/projectAuth
 */
import request from '@/utils/request'

export const projectAuthApi = {
	/* ====== 授权申请 ====== */

	/**
	 * 创建项目授权申请
	 * 对应后端：POST /project-auth/requests ← AuthRequestCreateDTO → AuthRequestVO
	 *   - 权限 project:auth:request
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 申请创建后状态为 pending，等待审批
	 *
	 * @param {Object} data 申请参数
	 *   - projectId: number 项目ID
	 *   - reason?: string 申请理由
	 *   - expireAt?: string 期望授权有效期（ISO 字符串，如 '2026-08-01T00:00:00'）
	 *   - remark?: string 备注
	 * @returns {Promise<Object>} 创建后的申请 VO（含 id、status='pending'）
	 */
	createAuthRequest(data) {
		return request({ url: '/project-auth/requests', method: 'POST', data })
	},

	/**
	 * 分页查询项目授权申请列表
	 * 对应后端：GET /project-auth/requests → PageResult<AuthRequestVO>
	 *   - 权限 project:auth:request
	 *
	 * @param {Object} params 查询参数
	 *   - pageNum, pageSize: number 分页
	 *   - status?: string 申请状态（pending/approved/rejected）
	 *   - projectId?: number 项目ID
	 *   - applicantId?: number 申请人ID
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	listAuthRequests(params) {
		return request({ url: '/project-auth/requests', method: 'GET', params })
	},

	/**
	 * 查询当前用户发起的授权申请列表
	 * 对应后端：GET /project-auth/requests/my → List<AuthRequestVO>
	 *   - 权限 project:auth:request
	 *   - 用于「我的授权申请」页面，自动按当前用户过滤
	 *
	 * @returns {Promise<Array>} 当前用户的申请列表
	 */
	myAuthRequests() {
		return request({ url: '/project-auth/requests/my', method: 'GET' })
	},

	/**
	 * 查询待当前用户审批的申请列表
	 * 对应后端：GET /project-auth/requests/pending → List<AuthRequestVO>
	 *   - 权限 project:auth:request
	 *   - 用于「待我审批」页面，自动按当前用户所在角色/部门过滤
	 *
	 * @returns {Promise<Array>} 待审批申请列表
	 */
	pendingAuthRequests() {
		return request({ url: '/project-auth/requests/pending', method: 'GET' })
	},

	/**
	 * 审批授权申请（通过/驳回二合一）
	 * 对应后端：POST /project-auth/requests/{id}/approve ← AuthRequestApproveDTO → AuthRequestVO
	 *   - 权限 project:auth:approve
	 *   - 后端 @OperationLog 记录操作日志
	 *   - dto.approve=true 时审批通过，dto.approve=false 时驳回
	 *
	 * @param {number|string} id 申请ID
	 * @param {Object} data 审批参数
	 *   - approve: boolean 是否通过
	 *   - opinion?: string 审批意见
	 *   - newExpireAt?: string 通过时指定授权有效期
	 * @returns {Promise<Object>} 审批后的申请 VO（status='approved' 或 'rejected'）
	 */
	approveAuthRequest(id, data) {
		return request({ url: `/project-auth/requests/${id}/approve`, method: 'POST', data })
	},

	/**
	 * 驳回授权申请
	 * 对应后端：POST /project-auth/requests/{id}/reject ← AuthRequestApproveDTO → AuthRequestVO
	 *   - 权限 project:auth:approve
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 与 approveAuthRequest(id, { approve:false }) 等价，但语义更明确
	 *
	 * @param {number|string} id 申请ID
	 * @param {Object} data 审批参数
	 *   - opinion?: string 驳回理由
	 * @returns {Promise<Object>} 驳回后的申请 VO（status='rejected'）
	 */
	rejectAuthRequest(id, data) {
		return request({ url: `/project-auth/requests/${id}/reject`, method: 'POST', data })
	},

	/* ====== 授权关系管理 ====== */

	/**
	 * 分页查询项目授权关系列表
	 * 对应后端：GET /project-auth/assignments → PageResult<AssignmentVO>
	 *   - 权限 project:auth:request
	 *   - 用于查看「项目-工程师」当前授权关系（已通过的申请产生）
	 *
	 * @param {Object} params 查询参数
	 *   - pageNum, pageSize: number 分页
	 *   - projectId?: number 项目ID
	 *   - engineerId?: number 工程师ID
	 *   - isActive?: boolean 是否有效
	 *   - expireSoon?: boolean 即将过期
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	listAssignments(params) {
		return request({ url: '/project-auth/assignments', method: 'GET', params })
	},

	/**
	 * 撤销授权
	 * 对应后端：DELETE /project-auth/assignments/{id} → Void
	 *   - 权限 project:auth:approve
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 撤销后授权关系置为 inactive，对应工程师失去该项目权限
	 *
	 * @param {number|string} id 授权记录ID（注意：是 assignment ID，不是申请 ID）
	 * @param {string} [reason] 撤销原因（query 参数，记入审计日志）
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	revokeAssignment(id, reason) {
		return request({
			url: `/project-auth/assignments/${id}`,
			method: 'DELETE',
			params: reason ? { reason } : undefined
		})
	},

	/**
	 * 延期授权
	 * 对应后端：PUT /project-auth/assignments/{id}/extend → Void
	 *   - 权限 project:auth:approve
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 仅可延期未过期或即将过期的有效授权
	 *
	 * @param {number|string} id 授权记录ID
	 * @param {string} newExpireAt 新的过期时间（query 参数，格式 'yyyy-MM-ddTHH:mm:ss'）
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	extendAssignment(id, newExpireAt) {
		return request({
			url: `/project-auth/assignments/${id}/extend`,
			method: 'PUT',
			params: { newExpireAt }
		})
	}
}
