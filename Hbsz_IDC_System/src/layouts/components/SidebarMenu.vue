<template>
  <!--
    侧边栏菜单组件（P1-2.1 拆分自 MainLayout.vue）
    父组件通过 :is-collapse / :active-menu / :system-name 传入状态；
    菜单启用 router 模式，点击后由 vue-router 自动跳转，无需向父组件 emit 事件。
  -->
  <el-aside
    :width="isCollapse ? '64px' : '230px'"
    class="sidebar"
    role="navigation"
    aria-label="主导航栏"
  >
    <div class="logo">
      <div class="logo-icon-box">
        <img
          :src="logoImg"
          class="logo-img"
          alt="系统Logo"
        >
      </div>
      <transition name="fade">
        <div
          v-show="!isCollapse"
          class="logo-text-box"
        >
          <div class="logo-text">
            {{ systemName }}
          </div>
          <div class="logo-sub">
            IDC Project System
          </div>
        </div>
      </transition>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="isCollapse"
      :collapse-transition="false"
      router
      class="sidebar-menu"
      background-color="#001529"
      text-color="rgba(255,255,255,0.65)"
      active-text-color="#ffffff"
    >
      <el-menu-item
        v-if="hasPermission()"
        index="/dashboard"
      >
        <el-icon><Odometer /></el-icon>
        <template #title>
          工作台
        </template>
      </el-menu-item>
      <el-menu-item
        v-if="hasPermission('project:query')"
        index="/projects"
      >
        <el-icon><Folder /></el-icon>
        <template #title>
          项目管理
        </template>
      </el-menu-item>
      <!-- 项目模板：可复用的项目骨架配置（权限与路由 meta 一致，复用 project:query） -->
      <el-menu-item
        v-if="hasPermission('project:query')"
        index="/project-templates"
      >
        <el-icon><Files /></el-icon>
        <template #title>
          项目模板
        </template>
      </el-menu-item>
      <!-- 材料库存：入库/出库/库存预警（权限与路由 meta 一致） -->
      <el-menu-item
        v-if="hasPermission('material:query')"
        index="/materials"
      >
        <el-icon><Goods /></el-icon>
        <template #title>
          材料库存
        </template>
      </el-menu-item>
      <!-- 项目协作：项目授权 + 项目组管理（组织架构升级新增的协作模块） -->
      <el-sub-menu
        v-if="hasPermission(['project:auth:request', 'project:team:manage'])"
        index="/project-collab"
      >
        <template #title>
          <el-icon><Share /></el-icon>
          <span>项目协作</span>
        </template>
        <el-menu-item
          v-if="hasPermission('project:auth:request')"
          index="/project-auth"
        >
          <el-icon><Connection /></el-icon>
          <template #title>
            项目授权
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('project:team:manage')"
          index="/project-teams"
        >
          <el-icon><UserFilled /></el-icon>
          <template #title>
            项目组管理
          </template>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item
        v-if="hasPermission('document:query')"
        index="/documents"
      >
        <el-icon><Document /></el-icon>
        <template #title>
          文档管理
        </template>
      </el-menu-item>
      <!-- 进度任务：进度管理 + 进度审核 + 风险预警 + 我的工作（合并后减少顶级菜单数量） -->
      <el-sub-menu
        v-if="hasPermission(['task:query', 'task:progress:review'])"
        index="/progress-task"
      >
        <template #title>
          <el-icon><DataLine /></el-icon>
          <span>进度任务</span>
        </template>
        <el-menu-item
          v-if="hasPermission('task:query')"
          index="/progress"
        >
          <el-icon><DataLine /></el-icon>
          <template #title>
            进度管理
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('task:progress:review')"
          index="/review/pending"
        >
          <el-icon><Bell /></el-icon>
          <template #title>
            进度审核
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('task:risk-board')"
          index="/my/risk-board"
        >
          <el-icon><Warning /></el-icon>
          <template #title>
            风险预警看板
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('task:query')"
          index="/my/tasks"
        >
          <el-icon><List /></el-icon>
          <template #title>
            我的任务
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('task:query')"
          index="/my/submissions"
        >
          <el-icon><EditPen /></el-icon>
          <template #title>
            我的填报历史
          </template>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item
        v-if="hasPermission('customer:query')"
        index="/customers"
      >
        <el-icon><OfficeBuilding /></el-icon>
        <template #title>
          客户管理
        </template>
      </el-menu-item>
      <el-menu-item
        v-if="hasPermission('archive:query')"
        index="/archive"
      >
        <el-icon><Box /></el-icon>
        <template #title>
          归档管理
        </template>
      </el-menu-item>
      <!-- 数据分析：统计分析 + 日报导出 -->
      <el-sub-menu
        v-if="hasPermission('statistics:query')"
        index="/data-analysis"
      >
        <template #title>
          <el-icon><DataAnalysis /></el-icon>
          <span>数据分析</span>
        </template>
        <el-menu-item
          v-if="hasPermission('statistics:query')"
          index="/statistics"
        >
          <el-icon><TrendCharts /></el-icon>
          <template #title>
            统计分析
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('statistics:query')"
          index="/daily-report"
        >
          <el-icon><Document /></el-icon>
          <template #title>
            日报导出
          </template>
        </el-menu-item>
        <!-- 工作量统计：清单维护 + 每日填报 + 人员绩效（依据工作量统计模板数字化） -->
        <el-menu-item
          v-if="hasPermission('workload:query')"
          index="/workload"
        >
          <el-icon><Histogram /></el-icon>
          <template #title>
            工作量统计
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('approval:flow')"
          index="/approval-flow"
        >
          <el-icon><Share /></el-icon>
          <template #title>
            审批流程图
          </template>
        </el-menu-item>
      </el-sub-menu>
      <!-- 审批待办中心：四类审核权限任一即可见（数组 = 任一满足） -->
      <el-menu-item
        v-if="hasPermission(['task:progress:review','workload:review','document:review','project:auth:approve'])"
        index="/approval/todo"
      >
        <el-icon><Bell /></el-icon>
        <template #title>
          审批待办
        </template>
      </el-menu-item>
      <el-sub-menu
        v-if="hasPermission(['system:role:query','system:permission:query','position:query','system:log:query'])"
        index="/system"
      >
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item
          v-if="hasPermission('system:user:query')"
          index="/system/users"
        >
          <el-icon><User /></el-icon>
          <template #title>
            用户管理
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('system:role:query')"
          index="/system/roles"
        >
          <el-icon><UserFilled /></el-icon>
          <template #title>
            角色管理
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('system:permission:query')"
          index="/system/permissions"
        >
          <el-icon><Key /></el-icon>
          <template #title>
            权限管理
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('position:query')"
          index="/system/positions"
        >
          <el-icon><Postcard /></el-icon>
          <template #title>
            岗位管理
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="hasPermission('system:log:query')"
          index="/system/logs"
        >
          <el-icon><Document /></el-icon>
          <template #title>
            操作日志
          </template>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item index="/settings">
        <el-icon><Setting /></el-icon>
        <template #title>
          系统设置
        </template>
      </el-menu-item>
      <!-- App 下载：所有登录用户可见（工程队成员扫码安装移动端） -->
      <el-menu-item index="/app-download">
        <el-icon><Iphone /></el-icon>
        <template #title>
          App 下载
        </template>
      </el-menu-item>
    </el-menu>
  </el-aside>
</template>

<script setup>
/**
 * 侧边栏菜单
 * 业务用途：依据用户权限动态渲染后台导航菜单；支持折叠模式。
 * 权限控制：通过 hasPermission 工具函数对每个菜单项 v-if 控制；
 *           传入数组表示「任一权限满足即显示」，传入字符串表示「需具备该权限」。
 */
import { hasPermission } from '@/utils/permission'
// 系统 Logo 图片（与父组件共享同一资源）
import logoImg from '@/static/logo/8da4edb7bc99f55c525d4722e1db8644.jpg'
import {
  Odometer, Folder, Share, Connection, UserFilled, Document, DataLine, Bell,
  Warning, List, EditPen, OfficeBuilding, Box, DataAnalysis, TrendCharts,
  Setting, User, Key, Postcard, Histogram, Iphone, Files, Goods
} from '@element-plus/icons-vue'

defineProps({
  /** 侧边栏是否折叠（控制宽度与 logo 文字显隐） */
  isCollapse: { type: Boolean, default: false },
  /** 当前激活的菜单 index（用于高亮当前路由对应的菜单项） */
  activeMenu: { type: String, default: '' },
  /** 系统名称（截短显示在 logo 区域） */
  systemName: { type: String, default: '' }
})
</script>

<style scoped>
.sidebar {
  background: linear-gradient(180deg, #001529 0%, #002140 100%);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  /* 改为 flex 列布局：logo 固定高度，菜单区域可滚动 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.logo-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  /* 移除渐变背景，使用纯图片 Logo */
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

/* Logo 图片：填满 logo-icon-box */
.logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}

.logo-icon {
  font-size: 22px;
  color: #fff;
}

.logo-text-box {
  margin-left: 12px;
  white-space: nowrap;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  line-height: 1.2;
}

.logo-sub {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.4);
  margin-top: 2px;
  letter-spacing: 0.5px;
}

.sidebar-menu {
  border-right: none;
  padding: 8px 0;
  /* 占满剩余高度，超出时滚动避免菜单被裁剪 */
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 自定义滚动条样式，与深色侧边栏协调 */
.sidebar-menu::-webkit-scrollbar {
  width: 6px;
}
.sidebar-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.18);
  border-radius: 3px;
}
.sidebar-menu::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.32);
}
.sidebar-menu::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 230px;
}

:deep(.el-menu--collapse) {
  width: 64px;
}

:deep(.el-menu-item) {
  margin: 4px 8px;
  border-radius: 6px;
  height: 44px;
  line-height: 44px;
}

:deep(.el-menu-item:hover) {
  background-color: rgba(255, 255, 255, 0.06) !important;
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #1890ff, #40a9ff) !important;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.4);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
