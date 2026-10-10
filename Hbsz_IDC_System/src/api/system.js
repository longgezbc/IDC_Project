import request from '@/utils/request'
import { uploadFile } from '@/utils/upload'

/**
 * 系统管理模块 API（用户/角色/权限管理）
 *
 * 覆盖 RBAC 权限体系的三类资源：用户、角色、权限，以及系统级配置。
 * 系统配置接口标记 skipCancel，避免在路由切换时被 AbortController 取消。
 * @module api/system
 */
export const systemApi = {
  /* ============ 用户管理 ============ */

  /**
   * 分页查询用户列表
   * @param {Object} params 分页与筛选参数（关键字、状态、角色等）
   * @returns {Promise<any>} 分页后的用户列表
   */
  queryUsers(params) {
    return request({ url: '/system/users', method: 'get', params })
  },

  /**
   * 查询所有启用用户（下拉选择用）
   * @returns {Promise<any>} 启用状态的用户精简列表
   */
  listAllUsers() {
    return request({ url: '/system/users/all', method: 'get' })
  },

  /**
   * 查询用户详情
   * @param {number|string} id 用户 ID
   * @returns {Promise<any>} 指定用户的详细信息（含角色）
   */
  getUserById(id) {
    return request({ url: `/system/users/${id}`, method: 'get' })
  },

  /**
   * 新增用户
   * @param {Object} data 用户字段数据
   * @returns {Promise<any>} 新增后的用户信息
   */
  createUser(data) {
    return request({ url: '/system/users', method: 'post', data })
  },

  /**
   * 修改用户
   * @param {number|string} id 用户 ID
   * @param {Object} data 待更新的用户字段
   * @returns {Promise<any>} 更新后的用户信息
   */
  updateUser(id, data) {
    return request({ url: `/system/users/${id}`, method: 'put', data })
  },

  /**
   * 删除用户
   * @param {number|string} id 用户 ID
   * @returns {Promise<any>} 删除操作结果
   */
  deleteUser(id) {
    return request({ url: `/system/users/${id}`, method: 'delete' })
  },

  /**
   * 重置用户密码
   * @param {number|string} id 用户 ID
   * @param {{ password: string, [key: string]: any }} data 新密码数据
   * @returns {Promise<any>} 重置操作结果
   */
  resetUserPassword(id, data) {
    return request({ url: `/system/users/${id}/password`, method: 'put', data })
  },

  /**
   * 修改用户状态（启用/禁用）
   * @param {number|string} id 用户 ID
   * @param {number|string} status 目标状态（如启用/禁用标识）
   * @returns {Promise<any>} 状态变更结果
   */
  updateUserStatus(id, status) {
    return request({ url: `/system/users/${id}/status`, method: 'put', params: { status } })
  },

  /**
   * 分配用户角色
   * @param {number|string} id 用户 ID
   * @param {Array<number|string>} roleIds 角色 ID 列表
   * @returns {Promise<any>} 角色分配结果
   */
  assignUserRoles(id, roleIds) {
    return request({ url: `/system/users/${id}/roles`, method: 'put', data: { roleIds } })
  },

  /**
   * 下载用户导入模板（返回 xlsx 文件流）
   * @returns {Promise<any>} 包含 blob 数据的响应对象
   */
  downloadUserImportTemplate() {
    return request({ url: '/system/users/import-template', method: 'get', responseType: 'blob' })
  },

  /**
   * 批量导入用户
   * @param {File} file Excel 文件
   * @returns {Promise<any>} 导入结果（成功/失败条数及错误详情）
   */
  importUsers(file) {
    return uploadFile(file, '/system/users/import')
  },

  /* ============ 角色管理 ============ */

  /**
   * 分页查询角色列表
   * @param {Object} params 分页与筛选参数
   * @returns {Promise<any>} 分页后的角色列表
   */
  queryRoles(params) {
    return request({ url: '/system/roles', method: 'get', params })
  },

  /**
   * 查询所有启用角色（下拉选择用）
   * @returns {Promise<any>} 启用状态的角色精简列表
   */
  listAllRoles() {
    return request({ url: '/system/roles/all', method: 'get' })
  },

  /**
   * 查询角色详情
   * @param {number|string} id 角色 ID
   * @returns {Promise<any>} 指定角色的详细信息（含权限）
   */
  getRoleById(id) {
    return request({ url: `/system/roles/${id}`, method: 'get' })
  },

  /**
   * 新增角色
   * @param {Object} data 角色字段数据
   * @returns {Promise<any>} 新增后的角色信息
   */
  createRole(data) {
    return request({ url: '/system/roles', method: 'post', data })
  },

  /**
   * 修改角色
   * @param {number|string} id 角色 ID
   * @param {Object} data 待更新的角色字段
   * @returns {Promise<any>} 更新后的角色信息
   */
  updateRole(id, data) {
    return request({ url: `/system/roles/${id}`, method: 'put', data })
  },

  /**
   * 删除角色
   * @param {number|string} id 角色 ID
   * @returns {Promise<any>} 删除操作结果
   */
  deleteRole(id) {
    return request({ url: `/system/roles/${id}`, method: 'delete' })
  },

  /**
   * 分配角色权限
   * @param {number|string} id 角色 ID
   * @param {Array<number|string>} permissionIds 权限 ID 列表
   * @returns {Promise<any>} 权限分配结果
   */
  assignRolePermissions(id, permissionIds) {
    return request({ url: `/system/roles/${id}/permissions`, method: 'put', data: { permissionIds } })
  },

  /* ============ 权限管理 ============ */

  /**
   * 查询所有权限（平铺列表）
   * @returns {Promise<any>} 所有权限项的平铺列表
   */
  listAllPermissions() {
    return request({ url: '/system/permissions', method: 'get' })
  },

  /**
   * 查询权限树形结构
   * @returns {Promise<any>} 按父子关系组织的权限树
   */
  listPermissionTree() {
    return request({ url: '/system/permissions/tree', method: 'get' })
  },

  /**
   * 新增权限
   * @param {Object} data 权限字段数据
   * @returns {Promise<any>} 新增后的权限信息
   */
  createPermission(data) {
    return request({ url: '/system/permissions', method: 'post', data })
  },

  /**
   * 修改权限
   * @param {number|string} id 权限 ID
   * @param {Object} data 待更新的权限字段
   * @returns {Promise<any>} 更新后的权限信息
   */
  updatePermission(id, data) {
    return request({ url: `/system/permissions/${id}`, method: 'put', data })
  },

  /**
   * 删除权限
   * @param {number|string} id 权限 ID
   * @returns {Promise<any>} 删除操作结果
   */
  deletePermission(id) {
    return request({ url: `/system/permissions/${id}`, method: 'delete' })
  },

  /* ============ 系统配置 ============ */

  /**
   * 获取系统配置（公开接口，跳过取消机制，避免路由切换时被取消）
   * @returns {Promise<any>} 系统全局配置数据
   */
  getSystemConfig() {
    return request({ url: '/system/config', method: 'get', skipCancel: true })
  },

  /**
   * 更新系统配置
   * @param {Object} data 待更新的系统配置字段
   * @returns {Promise<any>} 更新后的系统配置
   */
  updateSystemConfig(data) {
    return request({ url: '/system/config', method: 'put', data, skipCancel: true })
  },

  /* ============ 操作日志（审计） ============ */

  /**
   * 分页查询操作日志
   * @param {Object} params 筛选参数（username/module/businessType/status/startDate/endDate + 分页）
   * @returns {Promise<any>} 操作日志分页结果（时间倒序）
   */
  queryOperationLogs(params) {
    return request({ url: '/system/logs', method: 'get', params })
  },

  /**
   * 查询日志筛选下拉选项（去重后的模块与业务类型）
   * @returns {Promise<any>} { modules: [], businessTypes: [] }
   */
  getOperationLogFilters() {
    return request({ url: '/system/logs/filters', method: 'get' })
  }
}
