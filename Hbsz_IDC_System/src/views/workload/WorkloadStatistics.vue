<template>
  <div class="workload-statistics">
    <!-- ===== 工具栏：项目 + 日期 + 导出（快速填报 Tab 自带上下文选择，隐藏共享工具栏） ===== -->
    <div
      v-show="activeTab !== 'quick'"
      class="toolbar"
    >
      <div class="toolbar-left">
        <el-select
          v-model="projectId"
          placeholder="选择项目"
          filterable
          style="width: 240px"
          @change="handleProjectChange"
        >
          <el-option
            v-for="p in projectOptions"
            :key="p.id"
            :label="p.name"
            :value="p.id"
          />
        </el-select>
        <el-date-picker
          v-model="statDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="统计日期"
          :clearable="false"
          style="width: 160px"
          @change="handleDateChange"
        />
      </div>
      <div class="toolbar-right">
        <el-button
          v-if="hasPermission('workload:edit')"
          :icon="Download"
          :disabled="!projectId || items.length === 0"
          :loading="exporting"
          @click="handleExport"
        >
          导出 Excel（已审核）
        </el-button>
      </div>
    </div>

    <!-- ===== 汇总卡片（已审核口径 + 待审核提醒） ===== -->
    <div
      v-if="projectId && activeTab !== 'quick'"
      class="summary-cards"
    >
      <div class="summary-card">
        <div class="summary-value">
          {{ items.length }}
        </div>
        <div class="summary-label">
          清单项
        </div>
      </div>
      <div class="summary-card">
        <div class="summary-value">
          {{ matrixWorkers.length }}
        </div>
        <div class="summary-label">
          今日填报人数
        </div>
      </div>
      <div class="summary-card">
        <div class="summary-value">
          {{ summaryTodayQuantity }}
        </div>
        <div class="summary-label">
          今日完成量（已审核）
        </div>
      </div>
      <div class="summary-card">
        <div class="summary-value">
          {{ summaryTodayPerformance }}
        </div>
        <div class="summary-label">
          今日绩效（已审核）
        </div>
      </div>
      <div
        class="summary-card"
        :class="{ 'card-pending': pendingCount > 0 }"
      >
        <div
          class="summary-value"
          :class="{ 'value-pending': pendingCount > 0 }"
        >
          {{ pendingCount }}
        </div>
        <div class="summary-label">
          待审核填报
        </div>
      </div>
    </div>

    <!-- ===== 主内容 Tab ===== -->
    <el-tabs
      v-model="activeTab"
      class="workload-tabs"
      @tab-change="handleTabChange"
    >
      <!-- Tab 0：快速填报（卡片流，为普通工人设计，30 秒完成当日填报） -->
      <el-tab-pane
        v-if="hasPermission('workload:report')"
        label="快速填报"
        name="quick"
      >
        <QuickReportPanel @submitted="onQuickSubmitted" />
      </el-tab-pane>

      <!-- Tab 1：统计总览（模板同构表格，已完成/今日量仅计已审核） -->
      <el-tab-pane
        label="统计总览"
        name="overview"
      >
        <OverviewTab
          :items="items"
          :loading="loadingMatrix"
          :project-id="projectId"
          @changed="fetchMatrix"
        />
      </el-tab-pane>

      <!-- Tab 2：矩阵代填（项目组成员×清单项矩阵，供 PM 批量代填或总览对比） -->
      <el-tab-pane name="report">
        <template #label>
          <el-tooltip
            content="项目经理批量代填多人的场景；个人日常填报请用「快速填报」"
            placement="top"
          >
            <span>矩阵代填</span>
          </el-tooltip>
        </template>
        <ReportTab
          :project-id="projectId"
          :stat-date="statDate"
          :items="items"
          :matrix-workers="matrixWorkers"
          :matrix-cells="matrixCells"
          :worker-performance="workerPerformance"
          :team-workers="teamWorkers"
          :loading="loadingMatrix"
          @changed="fetchMatrix"
        />
      </el-tab-pane>

      <!-- Tab 3：填报审核（仅项目经理/管理员） -->
      <el-tab-pane
        v-if="hasPermission('workload:review')"
        name="review"
      >
        <template #label>
          <span>填报审核</span>
          <el-badge
            v-if="pendingCount > 0"
            :value="pendingCount"
            class="tab-badge"
          />
        </template>
        <ReviewTab
          :project-id="projectId"
          :stat-date="statDate"
          :active="activeTab === 'review'"
          @changed="fetchMatrix"
        />
      </el-tab-pane>

      <!-- Tab 4：人员绩效（区间汇总排名，仅计已审核） -->
      <el-tab-pane
        label="人员绩效"
        name="performance"
      >
        <PerformanceTab
          :project-id="projectId"
          :active="activeTab === 'performance'"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/**
 * 工作量统计页面（容器）
 *
 * 数字化《工作量统计模板.xlsx》，五个视图（各 Tab 拆分为子组件，本容器持有
 * 项目/日期上下文与共享统计矩阵，数据职责分工如下）：
 *  0. 快速填报（QuickReportPanel，自管理上下文）：常用项卡片流 + 一键复制昨天
 *     —— 为普通工人设计的 30 秒填报路径；提交成功后通知容器刷新矩阵；
 *  1. 统计总览（OverviewTab）：模板同构表格 + 清单维护，props 下发 / changed 上行；
 *  2. 矩阵代填（ReportTab）：项目组授权成员×清单项矩阵编辑（PM 批量代填多人场景），
 *     单元格按审核状态着色（approved 锁定 / pending 待审 / rejected 驳回可改）；
 *  3. 填报审核（ReviewTab，active 守卫自拉数）：项目经理/管理员批量通过或驳回；
 *  4. 人员绩效（PerformanceTab，active 守卫自拉数）：日期区间按人汇总绩效排名。
 *
 * 共享矩阵（useWorkloadMatrix）同时驱动：汇总卡片、统计总览、矩阵代填、审核角标；
 * 子组件数据变更后 emit('changed') 由容器统一重拉。审核/绩效列表与矩阵数据无关，
 * 由子组件在激活时自行拉取（el-tabs 急切渲染所有 pane，必须以 active 守卫）。
 *
 * 统计口径：已完成/今日量/绩效均只计 approved 记录；填报人员只能从项目组
 * 在组成员中选择（后端强校验），历史自由文本记录以只读"历史"列展示。
 */
import { computed, onMounted, ref } from 'vue'
import QuickReportPanel from './QuickReportPanel.vue'
import OverviewTab from './components/OverviewTab.vue'
import ReportTab from './components/ReportTab.vue'
import ReviewTab from './components/ReviewTab.vue'
import PerformanceTab from './components/PerformanceTab.vue'
import { useWorkloadMatrix } from '@/composables/useWorkloadMatrix'
import { workloadApi } from '@/api/workload'
import { dailyReportApi } from '@/api/dailyReport'
import { useProjectStore } from '@/stores/project'
import { hasPermission } from '@/utils/permission'
import { feedback } from '@/utils/feedback'
import { downloadBlob } from '@/utils/download'
import { Download } from '@element-plus/icons-vue'

const projectStore = useProjectStore()

// ===== 顶部筛选 =====
const projectId = ref(null)
const statDate = ref(new Date().toISOString().slice(0, 10))
// 默认 Tab：可填报用户直达"快速填报"（30 秒卡片流），其余用户进入统计总览
const activeTab = ref(hasPermission('workload:report') ? 'quick' : 'overview')

// ===== 共享统计矩阵（汇总卡片 + 统计总览 + 矩阵代填 + 审核角标） =====
const {
  items, matrixWorkers, matrixCells, workerPerformance,
  pendingCount, teamWorkers, loadingMatrix, fetchMatrix
} = useWorkloadMatrix(projectId, statDate)

// ===== 导出 =====
const exporting = ref(false)

/** 可选项目：未归档 */
const projectOptions = computed(() => projectStore.projects.filter(p => !p.archived))

/** 今日完成量合计（已审核口径，来自后端聚合列） */
const summaryTodayQuantity = computed(() =>
  items.value.reduce((sum, it) => sum + Number(it.todayQuantity || 0), 0).toFixed(1)
)

/** 今日绩效合计（已审核口径，来自后端每人绩效） */
const summaryTodayPerformance = computed(() =>
  Object.values(workerPerformance.value)
    .reduce((s, v) => s + Number(v || 0), 0)
    .toFixed(2)
)

/** 当前 Tab 需要的共享矩阵加载（审核/绩效 Tab 由子组件响应 active 自行拉取） */
function fetchCurrentTab() {
  if (activeTab.value === 'overview' || activeTab.value === 'report') fetchMatrix()
}

/** Tab 切换：快速填报面板自管理数据，其余 Tab 走共享矩阵加载 */
function handleTabChange(tab) {
  if (tab === 'quick') return
  // 从快速填报首次进入共享 Tab：自动选中第一个项目再加载
  if (!projectId.value) {
    const first = projectOptions.value[0]
    if (first) {
      projectId.value = first.id
    }
  }
  fetchCurrentTab()
}

/** 快速填报提交成功：同步刷新矩阵（待审核角标/汇总卡片即时更新） */
function onQuickSubmitted() {
  fetchMatrix()
}

/** 切换项目：重拉共享矩阵（人员列/勾选清空与列表刷新由各子组件响应 projectId 变化） */
function handleProjectChange() {
  fetchMatrix()
}

/** 切换统计日期：重拉共享矩阵（审核 Tab 激活期间由子组件响应 statDate 变化刷新） */
function handleDateChange() {
  fetchMatrix()
}

onMounted(async () => {
  // 快速填报 Tab：面板自加载上下文（项目列表由后端"我的项目组"提供），仅预取项目清单供其他 Tab
  if (activeTab.value === 'quick') {
    projectStore.fetchList({ pageNum: 1, pageSize: 100 })
    return
  }
  await projectStore.fetchList({ pageNum: 1, pageSize: 100 })
  const first = projectOptions.value[0]
  if (first) {
    projectId.value = first.id
    fetchCurrentTab()
  }
})

async function handleExport() {
  exporting.value = true
  try {
    const res = await workloadApi.exportExcel({ projectId: projectId.value, date: statDate.value })
    const record = res.data
    if (record?.id) {
      const dl = await dailyReportApi.download(record.id)
      downloadBlob(dl.data, record.fileName || '工作量统计.xlsx')
      feedback.success('导出成功，已开始下载')
    }
  } catch (e) {
    feedback.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.workload-statistics {
  padding: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-cards {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.summary-card {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  text-align: center;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.card-pending {
  background: #fdf6ec;
}

.summary-value {
  font-size: 22px;
  font-weight: 600;
  color: #1890ff;
}

.value-pending {
  color: #e6a23c;
}

.summary-label {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.tab-badge {
  margin-left: 6px;
  transform: translateY(-2px);
}
</style>
