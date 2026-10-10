<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-title">
        实施进度管理
      </div>
    </div>

    <el-row
      :gutter="20"
      style="margin-bottom: 20px"
    >
      <el-col :span="6">
        <StatCard
          icon="Folder"
          tone="primary"
          :value="projectStore.activeProjects.length"
          label="进行中项目"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          icon="Finished"
          tone="success"
          :value="totalTaskCount"
          label="总任务数"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          icon="Loading"
          tone="warning"
          :value="inProgressTaskCount"
          label="进行中任务"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          icon="Bell"
          tone="danger"
          :value="progressStore.getAllUpcomingKeyTasks().length"
          label="关键节点提醒"
        />
      </el-col>
    </el-row>

    <!-- P1-2.2：使用 CrudTable 通用布局组件（封装 el-card + toolbar + el-table）。
         本页无分页（项目总数有限，前端一次拉取），通过 :show-pagination="false" 隐藏分页器。
         原 el-card 的 #header（标题 + 状态筛选）迁移至 CrudTable 的 #toolbar 槽。 -->
    <CrudTable
      :data="filteredProjects"
      :show-pagination="false"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="title-chip title-chip--primary"><el-icon><DataLine /></el-icon></span>
          <span class="toolbar-title">项目进度总览</span>
        </div>
        <div class="toolbar-right">
          <el-radio-group
            v-model="filterStatus"
            size="small"
          >
            <el-radio-button value="">
              全部
            </el-radio-button>
            <el-radio-button value="进行中">
              进行中
            </el-radio-button>
            <el-radio-button value="已完成">
              已完成
            </el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table-column
        prop="code"
        label="项目编号"
        width="160"
      />
      <el-table-column
        label="项目名称"
        min-width="220"
      >
        <template #default="{ row }">
          <el-link
            type="primary"
            @click="goProgress(row.id)"
          >
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        prop="manager"
        label="项目经理"
        width="100"
      />
      <el-table-column
        label="工期"
        width="220"
      >
        <template #default="{ row }">
          {{ row.startDate }} ~ {{ row.endDate }}
        </template>
      </el-table-column>
      <el-table-column
        label="任务进度"
        width="240"
      >
        <template #default="{ row }">
          <div class="task-progress-box">
            <el-progress
              :percentage="calcProgress(row.id)"
              :status="calcProgress(row.id) === 100 ? 'success' : ''"
            />
            <span class="task-count">{{ getTaskStat(row.id) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="关键节点"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            type="danger"
          >
            {{ getKeyTaskCount(row.id) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        label="状态"
        width="100"
      >
        <template #default="{ row }">
          <el-tag :type="row.status === '进行中' ? 'primary' : 'success'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="160"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            @click="goProgress(row.id)"
          >
            查看甘特图
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>
  </div>
</template>

<script setup>
/**
 * 实施进度管理 - 总览页
 * 业务用途：汇总展示所有未归档项目的实施进度与任务统计，作为进度模块入口。
 * 主要功能区：顶部统计卡片（进行中项目/总任务/进行中任务/关键节点提醒）、
 *             项目进度总览表格（按状态筛选，可跳转单项目甘特图）。
 * 数据来源：projectStore（项目）、progressStore（任务及进度计算）、taskApi（批量拉取各项目任务）。
 *
 * P1-2.2：使用 CrudTable 通用布局组件（封装 el-card + toolbar + el-table）。
 *         本页无分页（项目总数有限，前端一次拉取），通过 :show-pagination="false" 隐藏分页器。
 *         原 el-card 的 #header（标题 + 状态筛选 radio）迁移至 CrudTable 的 #toolbar 槽。
 */
// 注：BaseButton / TableSkeleton 已在 main.js 全局注册，无需在此导入
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useProgressStore } from '@/stores/progress'
import { taskApi } from '@/api/task'
// P1-2.2：通用列表布局组件
import CrudTable from '@/components/CrudTable.vue'

const router = useRouter()
const projectStore = useProjectStore()
const progressStore = useProgressStore()

const filterStatus = ref('')  // 状态筛选：''全部 / '进行中' / '已完成'

// 按筛选状态过滤后的未归档项目列表
const filteredProjects = computed(() => {
  if (!filterStatus.value) return projectStore.projects.filter(p => !p.archived)
  return projectStore.projects.filter(p => !p.archived && p.status === filterStatus.value)
})

const totalTaskCount = computed(() => progressStore.tasks.length)  // 任务总数

// 进行中任务数
const inProgressTaskCount = computed(() => {
  return progressStore.tasks.filter(t => t.status === '进行中').length
})

/** 计算指定项目的整体进度百分比 @param {number|string} projectId 项目 ID @returns {number} 进度百分比 */
function calcProgress(projectId) {
  return progressStore.calcProjectProgress(projectId)
}

/** 获取项目任务完成情况文本，格式「已完成/总数」 @param {number|string} projectId 项目 ID @returns {string} */
function getTaskStat(projectId) {
  const tasks = progressStore.getProjectTasks(projectId)
  const completed = tasks.filter(t => t.status === '已完成').length
  return `${completed}/${tasks.length}`
}

/** 统计指定项目的关键节点任务数量 @param {number|string} projectId 项目 ID @returns {number} */
function getKeyTaskCount(projectId) {
  return progressStore.getProjectTasks(projectId).filter(t => t.isKey).length
}

/** 跳转到指定项目的进度甘特图页 @param {number|string} id 项目 ID */
function goProgress(id) {
  router.push(`/progress/${id}`)
}

// 进入页面：拉取项目列表与关键节点提醒，并批量加载各项目任务用于统计
onMounted(async () => {
  try {
    await projectStore.fetchList({ pageSize: 100 })
    // 拉取全部关键节点提醒（来自后端 /tasks/upcoming）
    await progressStore.fetchAllUpcomingKeyTasks()
    // 批量加载所有项目的任务，确保任务统计正确
    const projectIds = projectStore.projects
      .filter(p => !p.archived)
      .map(p => p.id)
    if (projectIds.length > 0) {
      const results = await Promise.all(
        projectIds.map(pid => taskApi.getList(pid, { pageSize: 1000 }))
      )
      // 修复 M17: 原代码直接赋值 progressStore.tasks 破坏 store 封装，改为调用 setTasks action。
      // 同时修正 res.data 解包：兼容数组直接返回与 { records: [] } 包装两种格式。
      const allTasks = []
      results.forEach(res => {
        // 兼容 res.data 为数组或 { records: [] } 的情况
        const data = Array.isArray(res.data) ? res.data : (res.data?.records || [])
        allTasks.push(...data)
      })
      progressStore.setTasks(allTasks)
    }
  } catch (error) {
    console.error('加载进度数据失败:', error)
  }
})
</script>

<style scoped>
/* P1-2.2：CrudTable 的 #toolbar 槽内标题样式（与其他列表页保持一致） */
.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.task-progress-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-count {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
</style>
