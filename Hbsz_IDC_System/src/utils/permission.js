import { useUserStore } from '@/stores/user'

/**
 * 权限工具：用于前端权限校验、菜单过滤、按钮控制
 *
 * 基于用户 store 中的 roles 与 permissions 进行判定，admin 角色视为拥有全部权限。
 * 前端校验仅用于交互与展示（如隐藏无权限按钮），真正的安全校验仍以后端为准。
 * @module utils/permission
 */

const ADMIN_ROLE = 'admin'

/**
 * 判断当前用户是否为管理员（拥有所有权限）
 * @returns {boolean} 拥有 admin 角色返回 true
 */
export function isAdmin() {
  const userStore = useUserStore()
  const roles = userStore.roles || []
  return roles.includes(ADMIN_ROLE)
}

/**
 * 校验当前用户是否拥有指定权限
 * - 管理员直接放行
 * - 普通用户检查 permissions 列表
 * @param {string|string[]} value 权限码，支持单个或数组（满足其一即可）
 * @returns {boolean}
 */
export function hasPermission(value) {
  // 仅 null/undefined 视为"无需权限"，空字符串 '' 不算有效权限码
  if (value === null || value === undefined) return true
  if (isAdmin()) return true

  const userStore = useUserStore()
  const userPerms = userStore.userInfo?.permissions || []
  const required = Array.isArray(value) ? value : [value]
  return required.some((perm) => userPerms.includes(perm))
}

/**
 * 校验当前用户是否同时拥有所有指定权限
 * - 管理员直接放行
 * @param {string|string[]} value 权限码，支持单个或数组（须全部满足）
 * @returns {boolean} 拥有全部权限返回 true
 */
export function hasAllPermissions(value) {
  // 仅 null/undefined 视为"无需权限"，空字符串 '' 不算有效权限码
  if (value === null || value === undefined) return true
  if (isAdmin()) return true

  const userStore = useUserStore()
  const userPerms = userStore.userInfo?.permissions || []
  const required = Array.isArray(value) ? value : [value]
  return required.every((perm) => userPerms.includes(perm))
}

/**
 * 校验当前用户是否拥有指定角色
 * @param {string|string[]} role 角色标识，支持单个或数组（满足其一即可）
 * @returns {boolean} 拥有任一角色返回 true
 */
export function hasRole(role) {
  // 仅 null/undefined 视为"无需角色限制"
  if (role === null || role === undefined) return true
  const userStore = useUserStore()
  const roles = userStore.roles || []
  const required = Array.isArray(role) ? role : [role]
  return required.some((r) => roles.includes(r))
}

/**
 * Vue 指令：v-permission
 * 用法：
 *   v-permission="'project:add'"          // 单个权限
 *   v-permission="['project:add','project:edit']"  // 任一权限
 *   v-permission.all="['project:add','project:edit']" // 同时拥有
 *   v-permission.role="'admin'"            // 角色判断
 *
 * 实现说明：使用 display:none 控制显隐，而非 removeChild。
 * removeChild 会导致权限变化（如切换角色）后元素无法恢复，display 方式可重复切换。
 * 元素被隐藏时同时设置 aria-hidden 与 inert，避免被 Tab 聚焦或读屏读出。
 * @param {import('vue').App} app Vue 应用实例
 * @returns {void}
 */
export function setupPermissionDirective(app) {
  app.directive('permission', {
    mounted(el, binding) {
      checkEl(el, binding)
    },
    updated(el, binding) {
      checkEl(el, binding)
    }
  })

  function checkEl(el, binding) {
    const { value, modifiers } = binding
    let ok = true
    if (modifiers.role) {
      ok = hasRole(value)
    } else if (modifiers.all) {
      ok = hasAllPermissions(value)
    } else {
      ok = hasPermission(value)
    }
    if (!ok) {
      el.style.display = 'none'
      el.setAttribute('aria-hidden', 'true')
      // inert 兼容性兜底，部分浏览器不支持也不影响 display 隐藏
      if ('inert' in el) el.inert = true
    } else {
      // 仅恢复被本指令设置过的隐藏，避免覆盖业务方设置的内联 display
      if (el.style.display === 'none' && el.getAttribute('aria-hidden') === 'true') {
        el.style.display = ''
        el.removeAttribute('aria-hidden')
        if ('inert' in el) el.inert = false
      }
    }
  }
}
