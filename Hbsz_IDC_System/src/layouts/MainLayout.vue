<template>
  <a
    href="#main-content"
    class="skip-link"
  >跳到主内容</a>
  <el-container class="main-layout">
    <!-- P1-2.1：侧边栏菜单抽取为独立组件，仅传入折叠状态、激活菜单与系统名称 -->
    <SidebarMenu
      :is-collapse="isCollapse"
      :active-menu="activeMenu"
      :system-name="systemName"
    />

    <el-container>
      <el-header
        class="header"
        role="banner"
      >
        <div class="header-left">
          <el-icon
            class="collapse-btn"
            role="button"
            aria-label="折叠/展开侧边栏"
            tabindex="0"
            @click="toggleCollapse"
            @keyup.enter="toggleCollapse"
          >
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">
              <el-icon style="vertical-align: middle">
                <HomeFilled />
              </el-icon> 首页
            </el-breadcrumb-item>
            <el-breadcrumb-item
              v-if="currentTitle && currentTitle !== '工作台'"
              class="current-breadcrumb"
            >
              {{ currentTitle }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tooltip
            content="全屏"
            placement="bottom"
          >
            <el-icon
              class="header-icon"
              role="button"
              aria-label="全屏"
              tabindex="0"
              @click="toggleFullscreen"
              @keyup.enter="toggleFullscreen"
            >
              <FullScreen />
            </el-icon>
          </el-tooltip>
          <el-badge
            :value="notificationStore.unreadCount"
            :hidden="notificationStore.unreadCount === 0"
            :max="99"
            class="remind-badge"
          >
            <el-tooltip
              content="站内通知"
              placement="bottom"
            >
              <el-icon
                class="header-icon"
                role="button"
                aria-label="站内通知"
                tabindex="0"
                @click="openNotificationDrawer"
                @keyup.enter="openNotificationDrawer"
              >
                <ChatDotRound />
              </el-icon>
            </el-tooltip>
          </el-badge>
          <el-badge
            :value="remindCount"
            :hidden="remindCount === 0"
            class="remind-badge"
          >
            <el-tooltip
              content="关键节点提醒"
              placement="bottom"
            >
              <el-icon
                class="header-icon"
                role="button"
                aria-label="关键节点提醒"
                tabindex="0"
                @click="showRemind = true"
                @keyup.enter="showRemind = true"
              >
                <Bell />
              </el-icon>
            </el-tooltip>
          </el-badge>
          <el-dropdown>
            <div
              class="user-info"
              role="button"
              tabindex="0"
              aria-haspopup="true"
              aria-label="用户菜单"
            >
              <el-avatar
                :size="34"
                class="user-avatar"
                :src="userStore.userInfo?.avatar || ''"
              >
                {{ avatarText }}
              </el-avatar>
              <div class="user-text">
                <div class="user-name">
                  {{ userStore.nickname || '未登录' }}
                </div>
                <div class="user-role">
                  {{ primaryRole }}
                </div>
              </div>
              <el-icon class="arrow-icon">
                <ArrowDown />
              </el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/settings')">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item @click="router.push('/settings')">
                  <el-icon><Setting /></el-icon>系统设置
                </el-dropdown-item>
                <el-dropdown-item
                  divided
                  @click="handleLogout"
                >
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main
        id="main-content"
        class="main-content"
        role="main"
        @scroll="handleMainScroll"
      >
        <router-view v-slot="{ Component, route: viewRoute }">
          <transition
            name="fade"
            mode="out-in"
          >
            <keep-alive :include="cachedViews">
              <component
                :is="Component"
                :key="viewRoute.fullPath"
              />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- P1-2.1：站内通知抽屉抽取为独立组件，内部自动拉取列表与未读数 -->
    <NotificationDrawer v-model:visible="showNotifications" />

    <!-- P1-2.1：关键节点提醒抽屉抽取为独立组件，点击卡片触发 navigate 事件由父组件路由跳转 -->
    <RemindDrawer
      v-model:visible="showRemind"
      :remind-list="remindList"
      @navigate="handleRemindNavigate"
    />

    <!-- 回顶部按钮：主内容区滚动超过 300px 时显示 -->
    <div
      class="scroll-top-btn"
      :class="{ visible: scrollTopVisible }"
      @click="scrollToTop"
    >
      <el-icon><ArrowUp /></el-icon>
    </div>
  </el-container>
</template>

<script setup>
/**
 * MainLayout 主布局
 *
 * 后台主框架：左侧可折叠侧边栏菜单（SidebarMenu 组件，依据权限动态显隐）、
 * 顶部导航栏（面包屑、全屏、通知/提醒徽章、用户下拉），主区通过 keep-alive 缓存指定列表页。
 * 挂载时启动会话超时监控（超时登出、活动续期），建立通知 WebSocket；卸载时停止监控与断开 WS。
 *
 * P1-2.1：SidebarMenu / NotificationDrawer / RemindDrawer 三个子组件已抽取到 ./components/ 目录下，
 *        本组件仅保留布局骨架、头部导航、路由出口、回顶部按钮与会话/登出/全屏等顶层编排逻辑。
 */
import { ref, computed, onMounted, onUnmounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProgressStore } from '@/stores/progress'
import { useUserStore } from '@/stores/user'
import { useSystemConfigStore } from '@/stores/systemConfig'
import { useNotificationStore } from '@/stores/notification'
// 修复 M21：登出时需重置各业务 store，避免下一个登录用户看到上一个用户的缓存数据
import { useCustomerStore } from '@/stores/customer'
import { useProjectStore } from '@/stores/project'
import { useDocumentStore } from '@/stores/document'
import { useStatisticsStore } from '@/stores/statistics'
import { useSystemStore } from '@/stores/system'
import { feedback } from '@/utils/feedback'
import { startSessionMonitor, stopSessionMonitor } from '@/utils/sessionMonitor'
// 销毁性能监控（释放 PerformanceObserver 等资源，避免内存泄漏）
import { destroyPerformanceObserver } from '@/utils/performance'
// 取消请求错误判定（用于区分登出请求被取消与真实异常）
import { isCancelError } from '@/utils/cancelToken'
// P1-2.1：抽取的子组件
import SidebarMenu from './components/SidebarMenu.vue'
import NotificationDrawer from './components/NotificationDrawer.vue'
import RemindDrawer from './components/RemindDrawer.vue'
import {
  Fold, Expand, HomeFilled, FullScreen, ChatDotRound, Bell,
  ArrowDown, User, Setting, SwitchButton, ArrowUp
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
// P1-5.6：侧边栏折叠状态持久化到 localStorage，刷新后保留用户偏好
const isCollapse = ref(localStorage.getItem('hbsz:sidebar-collapsed') === 'true')
const showRemind = ref(false)
const showNotifications = ref(false)
// 回顶部按钮可见性（主内容区滚动超过 300px 时显示）
const scrollTopVisible = ref(false)
// 主内容区滚动容器 DOM 引用（用于平滑滚动到顶部）
const mainContentEl = ref(null)
const progressStore = useProgressStore()
const userStore = useUserStore()
const systemConfigStore = useSystemConfigStore()
const notificationStore = useNotificationStore()
// 修复 M21：登出时重置业务 store 所需的实例
const customerStore = useCustomerStore()
const projectStore = useProjectStore()
const documentStore = useDocumentStore()
const statisticsStore = useStatisticsStore()
const systemStore = useSystemStore()

// 侧边栏系统名称（截短显示，避免过长）
const systemName = computed(() => {
  const name = systemConfigStore.config.name || 'IDC数据中心项目管理系统'
  return name.length > 10 ? name.substring(0, 10) + '...' : name
})

// 需要缓存的路由组件名列表（对应路由配置中的 name）
// 缓存列表页等不常变化的页面，提升切换体验
const cachedViews = computed(() => {
  return ['ProjectList', 'DocumentManagement', 'UserManagement', 'RoleManagement', 'PositionManagement', 'ProjectAuthManagement', 'ProjectTeamManagement']
})

// 用户头像首字（取昵称或用户名第一个字符）
const avatarText = computed(() => {
  const name = userStore.nickname || userStore.username || ''
  return name ? name.charAt(0).toUpperCase() : '?'
})

// 用户主角色（取第一个非空角色）
const primaryRole = computed(() => {
  const roles = userStore.roles || []
  if (roles.length === 0) return '普通用户'
  // 角色码转中文显示
  const roleMap = {
    admin: '超级管理员',
    project_manager: '项目经理',
    engineer: '工程师',
    document_manager: '文档管理员',
    viewer: '访客'
  }
  return roleMap[roles[0]] || roles[0]
})

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/projects')) return '/projects'
  if (path.startsWith('/documents')) return '/documents'
  if (path.startsWith('/progress')) return '/progress'
  if (path.startsWith('/review')) return '/review/pending'
  if (path.startsWith('/my/tasks')) return '/my/tasks'
  if (path.startsWith('/my/submissions')) return '/my/submissions'
  if (path.startsWith('/my/risk-board')) return '/my/risk-board'
  if (path.startsWith('/customers')) return '/customers'
  if (path.startsWith('/archive')) return '/archive'
  if (path.startsWith('/statistics')) return '/statistics'
  if (path.startsWith('/daily-report')) return '/daily-report'
  if (path.startsWith('/settings')) return '/settings'
  return path
})

const currentTitle = computed(() => route.meta.title || '')

const remindList = computed(() => progressStore.getAllUpcomingKeyTasks())
const remindCount = computed(() => remindList.value.length)

/**
 * P1-5.6：切换侧边栏折叠状态并持久化到 localStorage
 */
function toggleCollapse() {
  isCollapse.value = !isCollapse.value
  localStorage.setItem('hbsz:sidebar-collapsed', String(isCollapse.value))
}

/**
 * 切换浏览器全屏状态
 */
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

/**
 * 主内容区滚动处理：超过 300px 显示回顶部按钮，并缓存滚动容器引用
 */
function handleMainScroll(e) {
  const el = e.target
  mainContentEl.value = el
  scrollTopVisible.value = el.scrollTop > 300
}

/**
 * 平滑滚动到主内容区顶部
 */
function scrollToTop() {
  const el = mainContentEl.value || document.querySelector('.main-content')
  if (el) {
    el.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

/**
 * 打开通知抽屉：仅切换显隐，列表与未读数由 NotificationDrawer 内部 watch 自动拉取
 */
function openNotificationDrawer() {
  showNotifications.value = true
}

/**
 * P1-2.1：处理 RemindDrawer 卡片点击事件，跳转到对应项目的进度页
 * @param {number|string} projectId 项目 ID
 */
function handleRemindNavigate(projectId) {
  router.push(`/progress/${projectId}`)
}

/**
 * 退出登录：二次确认后停止会话监控、断开通知 WS、调用登出并跳转登录页
 *
 * 异常处理策略：
 *   - 用户取消确认对话框：静默处理，不打扰用户
 *   - 登出接口失败（网络异常等）：提示「登出失败，请重试」，但本地登录态已被 userStore.logout()
 *     内部清理，仍跳转登录页避免卡死
 *   - 请求被取消（如快速重复点击）：静默处理
 */
async function handleLogout() {
  try {
    await feedback.confirm('确认退出登录吗？', { title: '提示', type: 'warning' })
  } catch (e) {
    // 用户点击取消，静默处理
    return
  }

  // 用户已确认，停止会话监控并断开通知 WebSocket
  stopSessionMonitor()
  notificationStore.disconnectWebSocket()
  // 修复 M21：登出时重置各业务 store 状态，避免下一个登录用户看到上一个用户缓存的数据
  // （如项目列表、客户列表、任务列表等），防止越权访问与数据错乱
  resetBusinessStores()

  try {
    await userStore.logout()
    feedback.success('已退出登录')
  } catch (error) {
    // 区分请求被取消与真实异常：
    // - 被取消（如切换页面、快速重复点击）：静默处理
    // - 真实异常（网络错误、服务端 5xx 等）：提示用户，但本地登录态已清理，仍允许跳转登录页
    if (isCancelError(error)) {
      return
    }
    console.error('登出失败:', error)
    feedback.error('登出失败，请重试')
  } finally {
    // 无论登出接口成功或失败，均跳转登录页，避免用户卡在已无登录态的页面
    router.push('/login')
  }
}

/**
 * 重置全部业务 store（P2 修复：抽取公共函数）
 *
 * handleLogout 与 handleSessionTimeout 都必须在离开登录态时调用，
 * 此前会话超时路径未清理业务 store，共享电脑上用户 B 登录后会先看到
 * 用户 A 缓存的列表/详情数据。systemConfig 一并 reset，避免跨用户
 * 继承主题色/分页大小等偏好且 loaded 短路导致配置不重新拉取。
 */
function resetBusinessStores() {
  progressStore.reset()
  customerStore.reset()
  projectStore.reset()
  documentStore.reset()
  statisticsStore.reset()
  systemStore.reset()
  systemConfigStore.reset()
}

/**
 * 会话超时处理：提示、清理登录态并跳转登录页
 */
function handleSessionTimeout() {
  feedback.warning('会话已超时，请重新登录')
  stopSessionMonitor()
  notificationStore.disconnectWebSocket()
  resetBusinessStores()
  userStore.clearAuth()
  router.push('/login')
}

/**
 * 会话续期处理：刷新访问令牌
 */
async function handleSessionRefresh() {
  await userStore.refresh()
}

// 启动会话超时监控 + 建立 WebSocket 通知连接
onMounted(() => {
  startSessionMonitor({
    onTimeout: handleSessionTimeout,
    onRefresh: handleSessionRefresh
  })
  // 建立通知 WebSocket 连接 + 拉取未读数
  notificationStore.connectWebSocket()
  notificationStore.fetchUnreadCount()
  // 安全：挂载时拉取最新用户信息与权限，防止本地缓存的旧权限导致越权访问
  // 场景：管理员撤销某用户权限后，该用户本地 localStorage 仍保留旧 userInfo，
  //       若不刷新则前端 hasPermission() 仍判定有权限，存在越权风险
  // 静默失败：网络异常或 token 过期（skipAuthRedirect）时不阻塞页面加载，
  //          沿用本地缓存的权限兜底。用户下次主动操作时若 token 失效会正常跳登录。
  userStore.fetchProfile().catch(() => {})
})

onUnmounted(() => {
  stopSessionMonitor()
  notificationStore.disconnectWebSocket()
})

// 组件卸载前销毁性能监控（断开 PerformanceObserver、移除 load/beforeunload 监听）
// 性能监控在 main.js 中 initPerformanceMonitor() 初始化，但观察者随组件卸载应一并释放，
// 避免组件销毁后 PerformanceObserver 仍占用内存
onBeforeUnmount(() => {
  destroyPerformanceObserver()
})
</script>

<style scoped>
.skip-link {
  position: absolute;
  top: -100px;
  left: 0;
  background: #1890ff;
  color: #fff;
  padding: 8px 16px;
  z-index: 9999;
  border-radius: 0 0 4px 0;
  font-size: 14px;
  text-decoration: none;
  transition: top 0.2s;
}

.skip-link:focus {
  top: 0;
}

.main-layout {
  height: 100vh;
}

.header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 24px;
  height: 60px;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.collapse-btn {
  font-size: 26px !important;
  cursor: pointer;
  color: #545c64;
  padding: 8px !important;
  border-radius: 8px;
  transition: all 0.2s;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  width: 42px !important;
  height: 42px !important;
  box-sizing: border-box;
  line-height: 1;
}

.collapse-btn :deep(svg) {
  width: 1em !important;
  height: 1em !important;
  font-size: inherit !important;
}

.collapse-btn:hover {
  color: #1890ff;
  background: #e6f7ff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  font-size: 24px !important;
  cursor: pointer;
  color: #545c64;
  padding: 8px !important;
  border-radius: 8px;
  transition: all 0.2s;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  width: 42px !important;
  height: 42px !important;
  box-sizing: border-box;
  line-height: 1;
}

.header-icon :deep(svg) {
  width: 1em !important;
  height: 1em !important;
  font-size: inherit !important;
}

/* tooltip 触发器就是 .header-icon 本身，确保尺寸不被覆盖 */
.header-right :deep(.el-tooltip__trigger) {
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

/* badge 内部的 tooltip 触发器也要保持尺寸 */
.remind-badge :deep(.el-tooltip__trigger) {
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.header-icon:hover {
  color: #1890ff;
  background: #e6f7ff;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.user-avatar {
  background: linear-gradient(135deg, #1890ff, #40a9ff);
  font-weight: 600;
}

.user-text {
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.user-role {
  font-size: 11px;
  color: #909399;
}

.arrow-icon {
  color: #909399;
  font-size: 12px;
}

.main-content {
  background: #f5f7fa;
  padding: 0;
  overflow-y: auto;
}

/* router-view 切换过渡（SidebarMenu 内部也独立定义了同名过渡，互不影响） */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== 回顶部按钮 ===== */
.scroll-top-btn {
  position: fixed;
  right: 32px;
  bottom: 32px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.4);
  opacity: 0;
  visibility: hidden;
  transform: translateY(20px);
  transition: all 0.3s ease;
  z-index: 999;
}

.scroll-top-btn:hover {
  background: #40a9ff;
  box-shadow: 0 6px 16px rgba(24, 144, 255, 0.5);
}

.scroll-top-btn.visible {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.scroll-top-btn .el-icon {
  font-size: 20px;
}

/* ===== 面包屑当前页高亮 ===== */
:deep(.current-breadcrumb .el-breadcrumb__inner) {
  font-weight: 600;
  color: #1890ff !important;
}
</style>
