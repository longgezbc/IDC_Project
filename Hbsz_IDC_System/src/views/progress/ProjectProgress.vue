<template>
  <div
    v-if="project"
    class="page-container"
  >
    <div class="page-header">
      <div>
        <BaseButton
          variant="link"
          icon-name="ArrowLeft"
          @click="router.back()"
        >
          返回
        </BaseButton>
        <span
          class="page-title"
          style="margin-left: 8px"
        >{{ project.name }} - 进度管理</span>
      </div>
      <div>
        <BaseButton
          icon-name="Plus"
          @click="showTaskDialog(null)"
        >
          新增任务
        </BaseButton>
        <BaseButton
          variant="primary"
          icon-name="Edit"
          @click="showReportDialog"
        >
          进度填报
        </BaseButton>
        <BaseButton
          v-if="pendingReviewCount > 0"
          variant="warning"
          icon-name="Bell"
          @click="openReviewDialog"
        >
          待审核 ({{ pendingReviewCount }})
        </BaseButton>
      </div>
    </div>

    <!-- 进度概览卡片（StatCard 全局组件统一视觉） -->
    <el-row
      :gutter="20"
      style="margin-bottom: 20px"
    >
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="DataAnalysis"
          tone="primary"
          :value="overallProgress + '%'"
          label="整体进度"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Finished"
          tone="success"
          :value="completedCount + '/' + tasks.length"
          label="已完成任务"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Loading"
          tone="warning"
          :value="inProgressCount"
          label="进行中任务"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Bell"
          tone="danger"
          :value="keyTaskCount"
          label="关键节点"
        />
      </el-col>
    </el-row>

    <!-- 甘特图 -->
    <div
      class="card-box"
      style="margin-bottom: 20px"
    >
      <div class="card-header">
        <div class="card-title">
          <span class="title-chip title-chip--primary"><el-icon><DataLine /></el-icon></span>
          <span>项目甘特图</span>
        </div>
        <span class="header-info">
          <el-icon><Calendar /></el-icon>
          工期：{{ project.startDate }} ~ {{ project.endDate }}
        </span>
      </div>
      <GanttChart
        :tasks="tasks"
        :start-date="project.startDate"
        :end-date="project.endDate"
        @task-click="showTaskDialog"
      />
    </div>

    <!-- 任务列表 -->
    <div class="card-box">
      <div class="card-header">
        <div class="card-title">
          <span class="title-chip title-chip--success"><el-icon><List /></el-icon></span>
          <span>任务列表</span>
        </div>
        <div class="view-switch">
          <el-tooltip
            content="表格视图"
            placement="top"
          >
            <BaseButton
              :variant="viewMode === 'table' ? 'primary' : 'plain'"
              size="small"
              icon-name="Grid"
              aria-label="切换为表格视图"
              @click="viewMode = 'table'"
            />
          </el-tooltip>
          <el-tooltip
            content="卡片视图"
            placement="top"
          >
            <BaseButton
              :variant="viewMode === 'card' ? 'primary' : 'plain'"
              size="small"
              icon-name="Menu"
              aria-label="切换为卡片视图"
              @click="viewMode = 'card'"
            />
          </el-tooltip>
        </div>
      </div>

      <!-- 表格视图 -->
      <el-table
        v-if="viewMode === 'table'"
        :data="pagedTasks"
        stripe
      >
        <el-table-column
          type="index"
          label="#"
          width="50"
        />
        <el-table-column
          prop="name"
          label="任务名称"
          min-width="180"
        >
          <template #default="{ row }">
            <div class="task-name-cell">
              <el-icon
                v-if="row.isMilestone"
                style="color: #faad14"
              >
                <Flag />
              </el-icon>
              <el-icon
                v-else-if="row.isKey"
                style="color: #f5222d"
              >
                <Star />
              </el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="assignee"
          label="负责人"
          width="100"
        />
        <el-table-column
          prop="startDate"
          label="开始日期"
          width="120"
        />
        <el-table-column
          prop="endDate"
          label="结束日期"
          width="120"
        />
        <el-table-column
          label="进度"
          width="180"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :status="row.progress === 100 ? 'success' : ''"
              :color="getProgressColor(row.progress)"
            />
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="getTaskStatusType(row.status)"
              effect="light"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="节点"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.isMilestone"
              size="small"
              type="warning"
              effect="dark"
            >
              里程碑
            </el-tag>
            <el-tag
              v-else-if="row.isKey"
              size="small"
              type="danger"
              effect="dark"
            >
              关键
            </el-tag>
            <span
              v-else
              class="text-muted"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="remark"
          label="备注"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          label="操作"
          width="240"
          fixed="right"
        >
          <template #default="{ row }">
            <BaseButton
              variant="link"
              size="small"
              icon-name="Edit"
              @click="showTaskDialog(row)"
            >
              编辑
            </BaseButton>
            <BaseButton
              variant="link"
              size="small"
              icon-name="EditPen"
              @click="showReportForTask(row)"
            >
              填报
            </BaseButton>
            <BaseButton
              variant="link-danger"
              size="small"
              icon-name="Delete"
              @click="handleDeleteTask(row)"
            >
              删除
            </BaseButton>
          </template>
        </el-table-column>
      </el-table>

      <!-- 卡片视图 -->
      <div
        v-else
        class="task-cards"
      >
        <div
          v-for="task in pagedTasks"
          :key="task.id"
          class="task-card"
          :class="{ 'task-key': task.isKey, 'task-milestone': task.isMilestone }"
        >
          <div class="task-card-header">
            <div class="task-card-title">
              <el-icon
                v-if="task.isMilestone"
                style="color: #faad14"
              >
                <Flag />
              </el-icon>
              <el-icon
                v-else-if="task.isKey"
                style="color: #f5222d"
              >
                <Star />
              </el-icon>
              <span>{{ task.name }}</span>
            </div>
            <el-tag
              size="small"
              :type="getTaskStatusType(task.status)"
              effect="dark"
            >
              {{ task.status }}
            </el-tag>
          </div>
          <div class="task-card-body">
            <div class="task-card-info">
              <span><el-icon><User /></el-icon>{{ task.assignee }}</span>
              <span><el-icon><Calendar /></el-icon>{{ task.startDate }} ~ {{ task.endDate }}</span>
            </div>
            <div class="task-card-progress">
              <div class="progress-label">
                <span>完成进度</span>
                <span class="progress-num">{{ task.progress }}%</span>
              </div>
              <el-progress
                :percentage="task.progress"
                :stroke-width="8"
                :show-text="false"
                :color="getProgressColor(task.progress)"
              />
            </div>
          </div>
          <div class="task-card-footer">
            <BaseButton
              variant="link"
              size="small"
              @click="showTaskDialog(task)"
            >
              编辑
            </BaseButton>
            <BaseButton
              variant="link"
              size="small"
              @click="showReportForTask(task)"
            >
              填报
            </BaseButton>
            <BaseButton
              variant="link-danger"
              size="small"
              @click="handleDeleteTask(task)"
            >
              删除
            </BaseButton>
          </div>
        </div>
        <el-empty
          v-if="tasks.length === 0"
          description="暂无任务"
        />
      </div>

      <!-- 分页 -->
      <div
        v-if="tasks.length > 0"
        class="pagination-wrapper"
      >
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="tasks.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </div>

    <!-- P1-2.1：任务编辑对话框抽取为独立组件，父组件通过 :task 传入待编辑任务（null 表示新增） -->
    <TaskEditDialog
      v-model:visible="showTaskEdit"
      :task="currentTask"
      :team-members="teamMembers"
      :project-start-date="project?.startDate || ''"
      @success="onTaskSaved"
    />

    <!-- P1-2.1：进度填报对话框抽取为独立组件，:preselect-task 控制是否预选任务 -->
    <ProgressReportDialog
      v-model:visible="showReport"
      :tasks="tasks"
      :preselect-task="preselectTask"
      @success="onReportSuccess"
    />

    <!-- P1-2.1：进度审核对话框抽取为独立组件，:log 由父组件在审核完成后更新为下一条 -->
    <ProgressReviewDialog
      v-model:visible="showReview"
      :log="currentReviewLog"
      @reviewed="onReviewReviewed"
    />
  </div>
  <el-empty
    v-else
    description="项目不存在"
  />
</template>

<script setup>
/**
 * 项目进度管理 - 单项目详情页
 * 业务用途：管理单个项目的任务与进度，支持甘特图查看、任务增删改、进度填报。
 * 主要功能区：进度概览卡片（整体进度/已完成/进行中/关键节点）、甘特图、任务列表（表格/卡片视图 + 分页）。
 * 数据来源：projectStore（项目详情）、progressStore（任务列表、进度计算、增删改与填报接口）。
 *
 * P1-2.1：任务编辑、进度填报、进度审核三个对话框已抽取到 ./components/ 下的独立组件，
 *        本组件仅保留 stat 卡片、甘特图、任务列表、对话框显隐与数据拉取逻辑。
 */
// 注：BaseButton 已在 main.js 全局注册，无需在此导入
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useProgressStore } from '@/stores/progress'
import { feedback } from '@/utils/feedback'
import GanttChart from '@/components/GanttChart.vue'
import { getTaskStatusType, getProgressColor } from '@/utils/format'
import { hasPermission } from '@/utils/permission'
import { projectTeamApi } from '@/api/projectTeam'
// P1-2.1：抽取的子组件
import TaskEditDialog from './components/TaskEditDialog.vue'
import ProgressReportDialog from './components/ProgressReportDialog.vue'
import ProgressReviewDialog from './components/ProgressReviewDialog.vue'

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()
const progressStore = useProgressStore()

const project = computed(() => projectStore.currentProject)  // 当前项目详情
// 直接基于 store 的 tasks ref 过滤，确保响应式追踪
const tasks = computed(() => {
  const pid = route.params.projectId
  return progressStore.tasks.filter(t => String(t.projectId) === String(pid))
})

// 整体进度直接基于当前任务列表实时计算，确保更新任务后立即刷新
const overallProgress = computed(() => {
  if (tasks.value.length === 0) return 0
  const total = tasks.value.reduce((sum, t) => sum + (Number(t.progress) || 0), 0)
  return Math.round(total / tasks.value.length)
})
const completedCount = computed(() => tasks.value.filter(t => t.status === '已完成').length)   // 已完成任务数
const inProgressCount = computed(() => tasks.value.filter(t => t.status === '进行中').length)   // 进行中任务数
const keyTaskCount = computed(() => tasks.value.filter(t => t.isKey).length)                    // 关键节点任务数

const viewMode = ref('table')     // 任务列表视图：table 表格 / card 卡片
const currentPage = ref(1)        // 任务列表当前页
const pageSize = ref(10)          // 任务列表每页条数

// P1-2.1：三个对话框的显隐状态
const showTaskEdit = ref(false)   // 任务编辑对话框显隐
const showReport = ref(false)     // 进度填报对话框显隐
const showReview = ref(false)     // 进度审核对话框显隐

// P1-2.1：三个对话框的数据源（由父组件管理，传给子组件作为 props）
const currentTask = ref(null)         // 当前编辑的任务（null 表示新增）
const preselectTask = ref(null)       // 进度填报预选任务（null 表示不预选）
const currentReviewLog = ref(null)    // 当前审核的日志对象

// 进度审核相关状态
const pendingReviewCount = ref(0)     // 当前项目待审核数量
const canReview = computed(() => hasPermission('task:progress:review'))  // 是否可审核

// 项目组成员列表（用于任务负责人下拉选择，传给 TaskEditDialog）
const teamMembers = ref([])

/** 加载项目组成员 */
async function loadTeamMembers() {
  try {
    const res = await projectTeamApi.listMembers({
      projectId: route.params.projectId,
      isActive: 1,
      pageNum: 1,
      pageSize: 200
    })
    teamMembers.value = res.data?.records || res.data || []
  } catch (e) {
    console.error('加载项目组成员失败:', e)
    teamMembers.value = []
  }
}

// 分页后的任务数据
const pagedTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return tasks.value.slice(start, start + pageSize.value)
})

/**
 * P1-2.1：打开任务编辑对话框
 * 父组件仅设置 currentTask 并打开对话框，表单回填由 TaskEditDialog 内部 watch visible 处理。
 * @param {Object|null} task 传入任务对象则为编辑，传 null 则为新增
 */
function showTaskDialog(task) {
  currentTask.value = task
  showTaskEdit.value = true
}

/**
 * P1-2.1：针对指定任务打开进度填报对话框
 * 设置 preselectTask 后打开对话框，表单回填由 ProgressReportDialog 内部 watch visible 处理。
 * @param {Object} task 任务对象
 */
function showReportForTask(task) {
  preselectTask.value = task
  showReport.value = true
}

/** P1-2.1：打开空白进度填报对话框（未预选任务） */
function showReportDialog() {
  preselectTask.value = null
  showReport.value = true
}

/**
 * P1-2.1：任务保存成功回调：刷新任务列表
 */
async function onTaskSaved() {
  await refreshTasks()
}

/**
 * P1-2.1：进度填报成功回调：刷新任务列表与待审核计数
 */
async function onReportSuccess() {
  await refreshTasks()
  await refreshPendingReviewCount()
}

/**
 * P1-2.1：审核提交成功回调：刷新任务列表、待审核列表与计数；
 * 若仍有待审核日志，更新 currentReviewLog 触发 ProgressReviewDialog watch 重新回填表单；
 * 否则关闭对话框。
 */
async function onReviewReviewed() {
  await refreshTasks()
  await fetchPendingReviews()
  pendingReviewCount.value = progressStore.pendingLogs.length
  if (progressStore.pendingLogs.length > 0) {
    currentReviewLog.value = progressStore.pendingLogs[0]
  } else {
    showReview.value = false
  }
}

/**
 * 打开审核对话框：拉取当前项目的待审核列表，弹窗逐条审核
 */
async function openReviewDialog() {
  await fetchPendingReviews()
  if (progressStore.pendingLogs.length === 0) {
    feedback.info('当前没有待审核的进度填报')
    return
  }
  // 加载第一条到审核表单（通过 currentReviewLog 传给 ProgressReviewDialog）
  currentReviewLog.value = progressStore.pendingLogs[0]
  showReview.value = true
}

/** 拉取当前项目的待审核进度日志列表 */
async function fetchPendingReviews() {
  try {
    await progressStore.fetchPendingLogs(route.params.projectId)
  } catch (error) {
    console.error('拉取待审核列表失败:', error)
  }
}

/** 刷新待审核数量（仅在有审核权限时调用） */
async function refreshPendingReviewCount() {
  if (!canReview.value) return
  try {
    await fetchPendingReviews()
    pendingReviewCount.value = progressStore.pendingLogs.length
  } catch (error) {
    console.error('刷新待审核数量失败:', error)
  }
}

/** 重新拉取当前项目的任务列表 */
async function refreshTasks() {
  await progressStore.fetchList({ projectId: route.params.projectId, pageSize: 100 })
  // P2 修复：删除最后一页最后一条任务后 currentPage 越界显示空页，回退到最后一个有效页
  const totalPages = Math.ceil(tasks.value.length / pageSize.value)
  if (currentPage.value > totalPages && totalPages > 0) {
    currentPage.value = totalPages
  }
}

/** 删除任务：二次确认后调用删除接口并刷新列表 @param {Object} task 任务对象 */
function handleDeleteTask(task) {
  feedback.confirmDelete(`确认删除任务「${task.name}」吗？`)
    .then(async () => {
      await progressStore.deleteTask(task.id)
      await refreshTasks()
    }).catch(() => {})
}

// 进入页面：并行加载项目详情、任务列表、整体进度与关键节点提醒，项目经理额外加载待审核数量
onMounted(async () => {
  try {
    // 并行加载：项目详情、任务列表、项目整体进度（来自后端聚合接口）、项目关键节点提醒、项目组成员（用于任务负责人下拉）
    await Promise.all([
      projectStore.fetchDetail(route.params.projectId),
      refreshTasks(),
      progressStore.fetchProjectProgress(route.params.projectId),
      progressStore.fetchUpcomingKeyTasksByProject(route.params.projectId),
      loadTeamMembers()
    ])
    // 项目经理额外加载待审核数量
    if (canReview.value) {
      await refreshPendingReviewCount()
    }
  } catch (error) {
    console.error('加载项目进度失败:', error)
  }
})
</script>

<style scoped>
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 12px 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.header-info {
  font-size: 13px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.view-switch {
  display: flex;
  gap: 4px;
}

.task-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.text-muted {
  color: #c0c4cc;
}

.task-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.task-card {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.3s;
  border-left: 4px solid #1890ff;
}

.task-card.task-key {
  border-left-color: #f5222d;
}

.task-card.task-milestone {
  border-left-color: #faad14;
}

.task-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.task-card-header {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f3f4f6;
  background: #fafbfc;
}

.task-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #1f2937;
  font-size: 14px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-card-body {
  padding: 16px;
}

.task-card-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 12px;
}

.task-card-info span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.task-card-progress {
  margin-bottom: 8px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.progress-num {
  font-weight: 600;
  color: #1f2937;
}

.task-card-footer {
  padding: 10px 16px;
  border-top: 1px solid #f3f4f6;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: #fafbfc;
}
</style>
