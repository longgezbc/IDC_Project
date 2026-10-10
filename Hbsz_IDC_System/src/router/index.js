/**
 * 前端路由模块
 *
 * 基于 vue-router（HTML5 History 模式）定义全站路由：登录页、以 MainLayout 为壳的
 * 业务子路由及 403/404 兜底页。通过全局前置守卫统一处理请求取消、页面标题、登录态校验
 * 与基于 meta.permission 的权限拦截。
 *
 * 路由分组（与 MainLayout.vue 侧边栏菜单结构保持一致）：
 *   - 工作台       顶级菜单
 *   - 项目管理      顶级菜单（含 create/edit/transfer-logs/detail 隐藏子路由）
 *   - 项目协作      子菜单：项目授权、项目组管理
 *   - 文档管理      顶级菜单
 *   - 进度任务      子菜单：进度管理、进度审核、风险预警看板、我的任务、我的填报历史
 *   - 客户管理      顶级菜单
 *   - 归档管理      顶级菜单
 *   - 数据分析      子菜单：统计分析、日报导出
 *   - 系统管理      子菜单：用户、角色、权限、岗位管理
 *   - 系统设置      顶级菜单
 *
 * meta 字段说明：
 *   - title       页面标题（拼接到 document.title）
 *   - icon        菜单图标名（Element Plus 图标组件名）
 *   - permission  路由所需权限码（数组表示满足任一即可，字符串表示必须满足）
 *   - hidden      是否在菜单中隐藏（详情/编辑页）
 *   - menuGroup   所属菜单分组（用于面包屑、自动菜单生成等扩展场景）
 *
 * @module router
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useSystemConfigStore } from '@/stores/systemConfig'
import { hasPermission, isAdmin } from '@/utils/permission'
import { cancelAllPending } from '@/utils/cancelToken'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      // ========== 工作台 ==========
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台', icon: 'Odometer', menuGroup: '工作台' }
      },

      // ========== 项目管理 ==========
      {
        path: 'projects',
        name: 'ProjectList',
        component: () => import('@/views/projects/ProjectList.vue'),
        meta: { title: '项目管理', icon: 'Folder', permission: 'project:query', menuGroup: '项目管理' }
      },
      {
        path: 'projects/create',
        name: 'ProjectCreate',
        component: () => import('@/views/projects/ProjectEdit.vue'),
        meta: { title: '新建项目', hidden: true, permission: 'project:add', menuGroup: '项目管理' }
      },
      {
        path: 'projects/:id/edit',
        name: 'ProjectEdit',
        component: () => import('@/views/projects/ProjectEdit.vue'),
        meta: { title: '编辑项目', hidden: true, permission: 'project:edit', menuGroup: '项目管理' }
      },
      {
        path: 'projects/transfer-logs',
        name: 'ProjectTransferLogs',
        component: () => import('@/views/projects/TransferLogs.vue'),
        meta: { title: '项目转移日志', hidden: true, permission: 'project:query', menuGroup: '项目管理' }
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/projects/ProjectDetail.vue'),
        meta: { title: '项目详情', hidden: true, permission: 'project:query', menuGroup: '项目管理' }
      },

      // ========== 项目协作（子菜单：项目授权 + 项目组管理） ==========
      {
        path: 'project-auth',
        name: 'ProjectAuthManagement',
        component: () => import('@/views/projectAuth/ProjectAuthManagement.vue'),
        meta: { title: '项目授权', icon: 'Connection', permission: 'project:auth:request', menuGroup: '项目协作' }
      },
      {
        path: 'project-teams',
        name: 'ProjectTeamManagement',
        component: () => import('@/views/projectTeam/ProjectTeamManagement.vue'),
        meta: { title: '项目组管理', icon: 'UserFilled', permission: 'project:team:manage', menuGroup: '项目协作' }
      },

      // ========== 文档管理 ==========
      {
        path: 'documents',
        name: 'DocumentManagement',
        component: () => import('@/views/documents/DocumentManagement.vue'),
        meta: { title: '文档管理', icon: 'Document', permission: 'document:query', menuGroup: '文档管理' }
      },

      // ========== 进度任务（子菜单：进度管理 + 进度审核 + 风险预警看板 + 我的任务 + 我的填报历史） ==========
      {
        path: 'progress',
        name: 'ProgressManagement',
        component: () => import('@/views/progress/ProgressManagement.vue'),
        meta: { title: '进度管理', icon: 'DataLine', permission: 'task:query', menuGroup: '进度任务' }
      },
      {
        path: 'progress/:projectId',
        name: 'ProjectProgress',
        component: () => import('@/views/progress/ProjectProgress.vue'),
        meta: { title: '项目进度', hidden: true, permission: 'task:query', menuGroup: '进度任务' }
      },
      {
        path: 'review/pending',
        name: 'PendingReview',
        component: () => import('@/views/review/PendingReview.vue'),
        meta: { title: '进度审核', icon: 'Bell', permission: 'task:progress:review', menuGroup: '进度任务' }
      },
      {
        path: 'my/risk-board',
        name: 'RiskBoard',
        component: () => import('@/views/my/RiskBoard.vue'),
        meta: { title: '风险预警看板', icon: 'Warning', permission: 'task:progress:review', menuGroup: '进度任务' }
      },
      {
        path: 'my/tasks',
        name: 'MyTasks',
        component: () => import('@/views/my/MyTasks.vue'),
        meta: { title: '我的任务', icon: 'List', permission: 'task:query', menuGroup: '进度任务' }
      },
      {
        path: 'my/submissions',
        name: 'MySubmissions',
        component: () => import('@/views/my/MySubmissions.vue'),
        meta: { title: '我的填报历史', icon: 'EditPen', permission: 'task:query', menuGroup: '进度任务' }
      },

      // ========== 客户管理 ==========
      {
        path: 'customers',
        name: 'CustomerManagement',
        component: () => import('@/views/customer/CustomerManagement.vue'),
        meta: { title: '客户管理', icon: 'OfficeBuilding', permission: 'customer:query', menuGroup: '客户管理' }
      },

      // ========== 归档管理 ==========
      {
        path: 'archive',
        name: 'ArchiveManagement',
        component: () => import('@/views/archive/ArchiveManagement.vue'),
        meta: { title: '归档管理', icon: 'Box', permission: 'archive:query', menuGroup: '归档管理' }
      },

      // ========== 数据分析（子菜单：统计分析 + 日报导出） ==========
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/Statistics.vue'),
        meta: { title: '统计分析', icon: 'TrendCharts', permission: 'statistics:query', menuGroup: '数据分析' }
      },
      {
        path: 'daily-report',
        name: 'DailyReport',
        component: () => import('@/views/dailyReport/DailyReport.vue'),
        meta: { title: '日报导出', icon: 'Document', permission: 'statistics:query', menuGroup: '数据分析' }
      },
      {
        path: 'workload',
        name: 'WorkloadStatistics',
        component: () => import('@/views/workload/WorkloadStatistics.vue'),
        meta: { title: '工作量统计', icon: 'Histogram', permission: 'workload:query', menuGroup: '数据分析' }
      },
      {
        path: 'approval-flow',
        name: 'ApprovalFlow',
        component: () => import('@/views/ApprovalFlowDemo.vue'),
        // 修复：演示页面无敏感数据，故不设置 meta.permission 权限标记，所有登录用户均可访问；如后续转为正式功能，需补充对应权限标识
        meta: { title: '审批流程图', icon: 'Share', menuGroup: '数据分析' }
      },

      // ========== 审批待办中心（四类审核聚合入口） ==========
      {
        path: 'approval/todo',
        name: 'TodoCenter',
        component: () => import('@/views/approval/TodoCenter.vue'),
        // 权限数组 = 任一满足即可见（与 hasPermission 数组语义一致）；
        // 后端按实际拥有的审核权限过滤来源，无任何审核权限时页面为空态
        meta: {
          title: '审批待办',
          icon: 'Bell',
          permission: ['task:progress:review', 'workload:review', 'document:review', 'project:auth:approve'],
          menuGroup: '审批中心'
        }
      },

      // ========== 系统管理（子菜单：用户 + 角色 + 权限 + 岗位） ==========
      {
        path: 'system/users',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagement.vue'),
        meta: { title: '用户管理', icon: 'User', permission: 'system:user:query', menuGroup: '系统管理' }
      },
      {
        path: 'system/roles',
        name: 'RoleManagement',
        component: () => import('@/views/system/RoleManagement.vue'),
        meta: { title: '角色管理', icon: 'UserFilled', permission: 'system:role:query', menuGroup: '系统管理' }
      },
      {
        path: 'system/permissions',
        name: 'PermissionManagement',
        component: () => import('@/views/system/PermissionManagement.vue'),
        meta: { title: '权限管理', icon: 'Key', permission: 'system:permission:query', menuGroup: '系统管理' }
      },
      {
        path: 'system/positions',
        name: 'PositionManagement',
        component: () => import('@/views/system/PositionManagement.vue'),
        meta: { title: '岗位管理', icon: 'Postcard', permission: 'position:query', menuGroup: '系统管理' }
      },
      {
        path: 'system/logs',
        name: 'OperationLog',
        component: () => import('@/views/system/OperationLog.vue'),
        meta: { title: '操作日志', icon: 'Document', permission: 'system:log:query', menuGroup: '系统管理' }
      },

      // ========== 系统设置 ==========
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/Settings.vue'),
        // 系统设置页包含个人设置（修改密码/个人信息），所有登录用户均可访问；
        // 系统配置修改由后端 @RequiresPermission("system:config:edit") 控制
        meta: { title: '系统设置', icon: 'Setting', menuGroup: '系统设置' }
      },

      // ========== App 下载中心 ==========
      {
        path: 'app-download',
        name: 'AppDownload',
        component: () => import('@/views/app/AppDownload.vue'),
        // 所有登录用户均可下载移动端（安装包由 Nginx /downloads/ 静态目录提供）
        meta: { title: 'App 下载', icon: 'Iphone', menuGroup: '系统设置' }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '无权限' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面未找到' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置路由守卫
 *
 * 执行顺序：
 *   1. 取消所有未完成请求，避免路由切换后无效请求与内存泄漏；
 *   2. 依据系统配置的系统名称拼接页面标题；
 *   3. 登录页：已登录则重定向首页，否则放行；
 *   4. 其他页面：未登录跳转登录页并携带 redirect 回跳地址；
 *   5. 校验目标路由 meta.permission，无权限且非管理员时跳转 403。
 *
 * 权限校验规则（与 MainLayout.vue 菜单 v-if 保持一致）：
 *   - meta.permission 为字符串时，必须满足该权限；
 *   - meta.permission 为数组时，满足任一权限即可通过；
 *   - meta.permission 缺省时，仅需登录态；
 *   - 管理员（isAdmin()）跳过所有权限校验。
 *
 * @param {import('vue-router').RouteLocationNormalized} to 目标路由
 * @param {import('vue-router').RouteLocationNormalized} from 来源路由
 * @param {import('vue-router').NavigationGuardNext} next 放行/重定向控制函数
 * @returns {void}
 */
router.beforeEach((to, from, next) => {
  // 路由切换时取消所有未完成的请求，避免无效请求和内存泄漏
  cancelAllPending('路由切换，取消未完成请求')
  // 使用系统配置中的系统名称作为页面标题后缀
  const systemConfigStore = useSystemConfigStore()
  const systemName = systemConfigStore.config.name || 'IDC数据中心项目管理系统'
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + systemName
  const userStore = useUserStore()

  // 登录页直接放行
  if (to.path === '/login') {
    if (userStore.isLogin) {
      next('/')
    } else {
      next()
    }
    return
  }

  // 其他页面需要登录态
  if (!userStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 权限校验：meta.permission（字符串或数组，与 MainLayout.vue hasPermission 逻辑一致）
  const required = to.meta?.permission
  if (required && !hasPermission(required) && !isAdmin()) {
    next({ path: '/403' })
    return
  }

  next()
})

export default router
