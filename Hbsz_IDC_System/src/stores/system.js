/**
 * 系统管理状态仓库（Pinia setup store）
 *
 * 面向 RBAC 权限体系，集中管理用户、角色、权限三类资源的列表数据、分页总数与加载状态，
 * 并封装分页查询、增删改、状态变更及授权（用户分配角色、角色分配权限）等动作。
 * 各动作以对应 loading 标记包裹请求，写操作直接透传后端结果由调用方处理提示与刷新。
 * @module stores/system
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { systemApi } from '@/api/system'
import { isCancelError } from '@/utils/cancelToken'

export const useSystemStore = defineStore('system', () => {
  /* ============ 用户管理 ============ */
  /** @type {import('vue').Ref<Array>} 当前页的用户列表 */
  const userList = ref([])
  /** @type {import('vue').Ref<number>} 用户总记录数（用于分页） */
  const userTotal = ref(0)
  /** @type {import('vue').Ref<boolean>} 用户列表加载中标记 */
  const userLoading = ref(false)
  /** fetchUsers 竞态保护序列号（P2 修复） */
  let fetchUsersSeq = 0

  /**
   * 分页查询用户列表
   *
   * 竞态保护（P2 修复）：快速翻页/搜索时丢弃乱序返回的过期响应。
   * 副作用：更新 userList、userTotal 与 userLoading。
   * @param {Object} params 分页与筛选参数（关键字、状态、角色等）
   * @returns {Promise<Object>} 分页响应数据
   */
  async function fetchUsers(params) {
    const currentSeq = ++fetchUsersSeq
    userLoading.value = true
    try {
      const res = await systemApi.queryUsers(params)
      if (currentSeq !== fetchUsersSeq) return res.data
      userList.value = res.data.records || []
      userTotal.value = res.data.total || 0
      return res.data
    } catch (error) {
      if (currentSeq !== fetchUsersSeq) return null
      if (isCancelError(error)) return null
      throw error
    } finally {
      if (currentSeq === fetchUsersSeq) {
        userLoading.value = false
      }
    }
  }

  /**
   * 新增用户
   * @param {Object} data 用户字段数据
   * @returns {Promise<Object>} 新增后的用户数据
   */
  async function createUser(data) {
    const res = await systemApi.createUser(data)
    return res.data
  }

  /**
   * 修改用户
   * @param {number|string} id 用户 ID
   * @param {Object} data 待更新的用户字段
   * @returns {Promise<Object>} 更新后的用户数据
   */
  async function updateUser(id, data) {
    const res = await systemApi.updateUser(id, data)
    return res.data
  }

  /**
   * 删除用户
   * @param {number|string} id 用户 ID
   * @returns {Promise<void>}
   */
  async function deleteUser(id) {
    await systemApi.deleteUser(id)
  }

  /**
   * 重置用户密码
   * @param {number|string} id 用户 ID
   * @param {string} newPassword 新密码
   * @returns {Promise<void>}
   */
  async function resetUserPassword(id, newPassword) {
    await systemApi.resetUserPassword(id, { newPassword })
  }

  /**
   * 修改用户状态（启用/禁用）
   * @param {number|string} id 用户 ID
   * @param {number|string} status 目标状态标识
   * @returns {Promise<void>}
   */
  async function updateUserStatus(id, status) {
    await systemApi.updateUserStatus(id, status)
  }

  /**
   * 分配用户角色
   * @param {number|string} id 用户 ID
   * @param {Array<number|string>} roleIds 角色 ID 列表
   * @returns {Promise<void>}
   */
  async function assignUserRoles(id, roleIds) {
    await systemApi.assignUserRoles(id, roleIds)
  }

  /* ============ 角色管理 ============ */
  /** @type {import('vue').Ref<Array>} 当前页的角色列表 */
  const roleList = ref([])
  /** @type {import('vue').Ref<number>} 角色总记录数（用于分页） */
  const roleTotal = ref(0)
  /** @type {import('vue').Ref<boolean>} 角色列表加载中标记 */
  const roleLoading = ref(false)
  /** fetchRoles 竞态保护序列号（P2 修复） */
  let fetchRolesSeq = 0
  /** @type {import('vue').Ref<Array>} 所有启用角色（下拉选择用） */
  const allRoles = ref([])

  /**
   * 分页查询角色列表
   *
   * 副作用：更新 roleList、roleTotal 与 roleLoading。
   * @param {Object} params 分页与筛选参数
   * @returns {Promise<Object>} 分页响应数据
   */
  async function fetchRoles(params) {
    const currentSeq = ++fetchRolesSeq
    roleLoading.value = true
    try {
      const res = await systemApi.queryRoles(params)
      if (currentSeq !== fetchRolesSeq) return res.data
      roleList.value = res.data.records || []
      roleTotal.value = res.data.total || 0
      return res.data
    } catch (error) {
      if (currentSeq !== fetchRolesSeq) return null
      if (isCancelError(error)) return null
      throw error
    } finally {
      if (currentSeq === fetchRolesSeq) {
        roleLoading.value = false
      }
    }
  }

  /**
   * 查询所有启用角色
   *
   * 副作用：更新 allRoles。
   * @returns {Promise<Array>} 启用角色精简列表
   */
  async function fetchAllRoles() {
    const res = await systemApi.listAllRoles()
    allRoles.value = res.data || []
    return res.data
  }

  /**
   * 新增角色
   * @param {Object} data 角色字段数据
   * @returns {Promise<Object>} 新增后的角色数据
   */
  async function createRole(data) {
    const res = await systemApi.createRole(data)
    return res.data
  }

  /**
   * 修改角色
   * @param {number|string} id 角色 ID
   * @param {Object} data 待更新的角色字段
   * @returns {Promise<Object>} 更新后的角色数据
   */
  async function updateRole(id, data) {
    const res = await systemApi.updateRole(id, data)
    return res.data
  }

  /**
   * 删除角色
   * @param {number|string} id 角色 ID
   * @returns {Promise<void>}
   */
  async function deleteRole(id) {
    await systemApi.deleteRole(id)
  }

  /**
   * 分配角色权限
   * @param {number|string} id 角色 ID
   * @param {Array<number|string>} permissionIds 权限 ID 列表
   * @returns {Promise<void>}
   */
  async function assignRolePermissions(id, permissionIds) {
    await systemApi.assignRolePermissions(id, permissionIds)
  }

  /* ============ 权限管理 ============ */
  /** @type {import('vue').Ref<Array>} 权限平铺列表 */
  const permissionList = ref([])
  /** @type {import('vue').Ref<Array>} 权限树形结构 */
  const permissionTree = ref([])
  /** @type {import('vue').Ref<boolean>} 权限数据加载中标记 */
  const permissionLoading = ref(false)

  /**
   * 查询所有权限（平铺）
   *
   * 副作用：更新 permissionList 与 permissionLoading。
   * @returns {Promise<Array>} 权限平铺列表
   */
  async function fetchAllPermissions() {
    permissionLoading.value = true
    try {
      const res = await systemApi.listAllPermissions()
      permissionList.value = res.data || []
      return res.data
    } finally {
      permissionLoading.value = false
    }
  }

  /**
   * 查询权限树
   *
   * 副作用：更新 permissionTree 与 permissionLoading。
   * @returns {Promise<Array>} 权限树形结构数据
   */
  async function fetchPermissionTree() {
    permissionLoading.value = true
    try {
      const res = await systemApi.listPermissionTree()
      permissionTree.value = res.data || []
      return res.data
    } finally {
      permissionLoading.value = false
    }
  }

  /**
   * 新增权限
   * @param {Object} data 权限字段数据
   * @returns {Promise<Object>} 新增后的权限数据
   */
  async function createPermission(data) {
    const res = await systemApi.createPermission(data)
    return res.data
  }

  /**
   * 修改权限
   * @param {number|string} id 权限 ID
   * @param {Object} data 待更新的权限字段
   * @returns {Promise<Object>} 更新后的权限数据
   */
  async function updatePermission(id, data) {
    const res = await systemApi.updatePermission(id, data)
    return res.data
  }

  /**
   * 删除权限
   * @param {number|string} id 权限 ID
   * @returns {Promise<void>}
   */
  async function deletePermission(id) {
    await systemApi.deletePermission(id)
  }

  /**
   * 重置 store 状态到初始值
   * 修复 M21：登出时调用，避免下一个用户看到上一个用户缓存的用户/角色/权限列表
   * @returns {void}
   */
  function reset() {
    // 用户
    userList.value = []
    userTotal.value = 0
    userLoading.value = false
    // 角色
    roleList.value = []
    roleTotal.value = 0
    roleLoading.value = false
    allRoles.value = []
    // 权限
    permissionList.value = []
    permissionTree.value = []
    permissionLoading.value = false
  }

  return {
    // 用户
    userList,
    userTotal,
    userLoading,
    fetchUsers,
    createUser,
    updateUser,
    deleteUser,
    resetUserPassword,
    updateUserStatus,
    assignUserRoles,
    // 角色
    roleList,
    roleTotal,
    roleLoading,
    allRoles,
    fetchRoles,
    fetchAllRoles,
    createRole,
    updateRole,
    deleteRole,
    assignRolePermissions,
    // 权限
    permissionList,
    permissionTree,
    permissionLoading,
    fetchAllPermissions,
    fetchPermissionTree,
    createPermission,
    updatePermission,
    deletePermission,
    reset
  }
})
