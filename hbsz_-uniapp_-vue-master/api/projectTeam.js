/**
 * 项目组管理模块 API
 *
 * 与 Web 端共享后端接口契约（/project-teams/*）。
 * 对应后端：ProjectTeamController（@RequestMapping("/project-teams")）。
 *
 * 业务模块：组织架构升级 阶段4 - 项目组跨部门协作。
 * 提供项目组成员的添加、移除、查询、转让组长等接口。
 * 添加成员时会自动创建授权记录，移除成员时会同步撤销授权。
 *
 * 权限要求：
 *   - 添加/移除/转让组长/查询成员列表：project:team:manage
 *   - 查询我参与的项目组：登录用户均可访问（无需权限码）
 *
 * @module api/projectTeam
 */
import request from '@/utils/request'

export const projectTeamApi = {
	/**
	 * 添加项目组成员
	 * 对应后端：POST /project-teams/members ← TeamMemberAddDTO → TeamMemberVO
	 *   - 权限 project:team:manage
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 业务逻辑：添加成员同时创建该项目的授权记录（默认有效期）
	 *
	 * @param {Object} data 添加参数
	 *   - projectId: number 项目ID
	 *   - userId: number 被添加的用户ID
	 *   - roleInTeam?: string 组内角色（leader/member，默认 member）
	 *   - remark?: string 备注
	 * @returns {Promise<Object>} 添加后的成员 VO
	 */
	addMember(data) {
		return request({ url: '/project-teams/members', method: 'POST', data })
	},

	/**
	 * 分页查询项目组成员列表
	 * 对应后端：GET /project-teams/members → PageResult<TeamMemberVO>
	 *   - 权限 project:team:manage
	 *
	 * @param {Object} params 查询参数
	 *   - pageNum, pageSize: number 分页
	 *   - projectId?: number 项目ID
	 *   - userId?: number 用户ID
	 *   - roleInTeam?: string 组内角色（leader/member）
	 *   - isActive?: boolean 是否在组（true=在职/false=已移除）
	 * @returns {Promise<Object>} 分页结果 { records, total }
	 */
	listMembers(params) {
		return request({ url: '/project-teams/members', method: 'GET', params })
	},

	/**
	 * 移除项目组成员
	 * 对应后端：DELETE /project-teams/members/{id} → Void
	 *   - 权限 project:team:manage
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 业务逻辑：移除成员同时撤销该项目对该工程师的授权
	 *
	 * @param {number|string} id 记录ID（注意：是 team_member 记录 ID，不是 userId）
	 * @param {string} [reason] 移除原因（query 参数，记入审计日志）
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	removeMember(id, reason) {
		return request({
			url: `/project-teams/members/${id}`,
			method: 'DELETE',
			params: reason ? { reason } : undefined
		})
	},

	/**
	 * 查询当前用户参与的项目组
	 * 对应后端：GET /project-teams/my → List<TeamMemberVO>
	 *   - 无权限码要求，登录用户均可访问（由 JWT 拦截器统一鉴权）
	 *   - 用于「我的项目组」页面，自动按当前用户过滤
	 *
	 * @returns {Promise<Array>} 当前用户参与的项目组成员记录列表
	 *   - 每条记录含 projectId、projectName、roleInTeam、isActive 等
	 */
	myTeams() {
		return request({ url: '/project-teams/my', method: 'GET' })
	},

	/**
	 * 转让项目组长
	 * 对应后端：POST /project-teams/{projectId}/transfer-leader → Void
	 *   - 权限 project:team:manage
	 *   - 后端 @OperationLog 记录操作日志
	 *   - 业务逻辑：转让组长同时同步更新项目负责人（project.leader_id）
	 *
	 * @param {number|string} projectId 项目ID（注意：是 projectId，不是 team_member ID）
	 * @param {number|string} newLeaderId 新组长用户ID（query 参数）
	 * @returns {Promise<any>} 操作结果（无业务数据）
	 */
	transferLeader(projectId, newLeaderId) {
		return request({
			url: `/project-teams/${projectId}/transfer-leader`,
			method: 'POST',
			params: { newLeaderId }
		})
	}
}
