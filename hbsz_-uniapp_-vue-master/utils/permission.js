/**
 * 权限工具（移动端简化版）
 *
 * 小程序端不维护完整的 permissionCode 列表（与 Web 端 RBAC 不同），
 * 改为基于角色码判断功能可见性，足以覆盖移动端场景：
 *   - admin：全部功能可见
 *   - project_manager（PM）：除系统管理外均可见，含审核/转移/风险看板/项目组管理
 *   - engineer / user：仅可见自身相关功能（我的任务/填报/通知/我的项目组）
 *
 * 角色码来自 userStore.roles（数组），与后端 sys_role.code 保持一致。
 *
 * 权限码到角色的映射规则（hasPermission）：
 *   - 仅 PM/admin 可用：项目管理类写操作（转移/审核/客户CRUD/项目组管理/授权审批）
 *   - 已登录用户可用：申请类操作（项目授权申请、岗位查询）
 *   - 其他权限码默认放行（已登录即可）
 */
import { userStore } from '@/store/user'

/** 角色常量（与后端 sys_role.code 一致） */
export const ROLE_ADMIN = 'admin'
export const ROLE_PROJECT_MANAGER = 'project_manager'
export const ROLE_ENGINEER = 'engineer'
export const ROLE_USER = 'user'

/**
 * 当前用户是否拥有指定角色
 * @param {string} roleCode 角色码
 * @returns {boolean}
 */
export function hasRole(roleCode) {
	const roles = userStore.roles || []
	return roles.includes(roleCode)
}

/**
 * 当前用户是否为管理员
 * @returns {boolean}
 */
export function isAdmin() {
	return hasRole(ROLE_ADMIN)
}

/**
 * 当前用户是否为项目经理（PM 含管理员）
 * @returns {boolean}
 */
export function isProjectManager() {
	return hasRole(ROLE_PROJECT_MANAGER) || hasRole(ROLE_ADMIN)
}

/**
 * 当前用户是否为工程师（普通成员）
 * 仅当 roles 数组包含 'engineer' 时返回 true；roles 为空或未加载时返回 false（默认拒绝）
 * @returns {boolean}
 */
export function isEngineer() {
	return hasRole(ROLE_ENGINEER)
}

/**
 * 仅 PM/admin 可用的权限码集合
 * 包含：
 *   - 项目管理类：project:transfer（转移）、project:archive（归档）、project:remove（删除）
 *   - 进度审核类：task:progress:review
 *   - 客户管理类：customer:add、customer:edit、customer:remove
 *   - 项目授权审批类：project:auth:approve（审批/撤销/延期）
 *   - 项目组管理类：project:team:manage（添加/移除/转让组长/查询成员列表）
 */
const PM_ONLY_PERMS = [
	// 项目管理
	'project:transfer',
	'project:archive',
	'project:remove',
	// 进度审核
	'task:progress:review',
	// 工作量审核
	'workload:review',
	// 客户管理
	'customer:add',
	'customer:edit',
	'customer:remove',
	// 项目授权审批
	'project:auth:approve',
	// 项目组管理
	'project:team:manage'
]

/**
 * 权限码判断（移动端按角色简化映射）
 * 移动端不维护完整权限码列表，将常用权限码映射到角色判断：
 *   - PM_ONLY_PERMS 集合中的权限码：仅 PM/admin 可用
 *   - 申请/查询类权限码（project:auth:request、position:query、archive:query 等）：已登录即可
 *   - 其他权限码默认放行（已登录即可）
 * @param {string} permCode 权限码
 * @returns {boolean}
 */
export function hasPermission(permCode) {
	if (isAdmin()) return true
	if (PM_ONLY_PERMS.includes(permCode)) {
		return hasRole(ROLE_PROJECT_MANAGER)
	}
	return true
}

/**
 * 是否可审核进度（PM / admin）
 * @returns {boolean}
 */
export function canReviewProgress() {
	return isProjectManager()
}

/**
 * 是否可审核工作量填报（PM / admin）
 * 仅 PM/admin 持有 workload:review 权限
 * @returns {boolean}
 */
export function canReviewWorkload() {
	return isProjectManager()
}

/**
 * 是否可管理客户（PM / admin；工程师只读）
 * @returns {boolean}
 */
export function canManageCustomer() {
	return isProjectManager()
}

/**
 * 是否可转移项目授权（PM / admin）
 * @returns {boolean}
 */
export function canTransferProject() {
	return isProjectManager()
}

/**
 * 是否可查看风险预警看板（PM / admin）
 * @returns {boolean}
 */
export function canViewRiskBoard() {
	return isProjectManager()
}

/**
 * 是否可审批项目授权申请（PM / admin）
 * 仅 PM/admin 持有 project:auth:approve 权限
 * @returns {boolean}
 */
export function canApproveAuthRequest() {
	return isProjectManager()
}

/**
 * 是否可管理项目组（PM / admin）
 * 仅 PM/admin 持有 project:team:manage 权限
 * @returns {boolean}
 */
export function canManageProjectTeam() {
	return isProjectManager()
}
