<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          日报导出
        </div>
        <div class="page-subtitle">
          按日期范围预览甘特图进度日报数据，并导出 Excel 报表
        </div>
      </div>
    </div>

    <!-- A. 顶部筛选区 -->
    <el-card
      shadow="never"
      class="filter-card"
    >
      <div class="filter-bar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 320px"
        />
        <el-select
          v-model="selectedProjectId"
          placeholder="全部项目"
          clearable
          filterable
          style="width: 280px"
          :loading="projectLoading"
        >
          <el-option
            v-for="item in projectOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <BaseButton
          variant="primary"
          icon-name="View"
          :loading="previewLoading"
          @click="handlePreview"
        >
          预览数据
        </BaseButton>
        <BaseButton
          variant="success"
          icon-name="Download"
          :loading="exportLoading"
          @click="handleExport"
        >
          导出Excel
        </BaseButton>
      </div>
    </el-card>

    <!-- B. 数据预览区（含多个无分页报表表格，保留原布局） -->
    <el-card
      v-if="hasPreviewed"
      shadow="never"
      class="section-card"
    >
      <template #header>
        <div class="section-header">
          <span class="title-chip title-chip--primary"><el-icon><Document /></el-icon></span>
          <span class="section-title">{{ reportTitle || '日报数据预览' }}</span>
          <span
            v-if="reportDate"
            class="section-sub"
          >报表日期：{{ reportDate }}</span>
        </div>
      </template>

      <!-- 数据为空时展示空状态 -->
      <EmptyState
        v-if="isEmptyPreview"
        description="所选日期范围内暂无日报数据"
      />

      <template v-else>
        <!-- 概览指标：9 项 -->
        <el-row
          :gutter="16"
          class="summary-row"
        >
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="报告日期"
              :value="reportDate || '-'"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="项目总数"
              :value="summary.totalProjects"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="任务总数"
              :value="summary.totalTasks"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="已完成任务"
              :value="summary.completedTasks"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="进行中任务"
              :value="summary.inProgressTasks"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="未开始任务"
              :value="summary.pendingTasks"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="延期任务数"
              :value="summary.delayedTasks"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="里程碑数"
              :value="summary.milestoneCount"
            />
          </el-col>
          <el-col
            :xs="12"
            :sm="6"
            :md="3"
          >
            <el-statistic
              title="关键任务数"
              :value="summary.keyTaskCount"
            />
          </el-col>
        </el-row>

        <!-- 项目进度总览表格 -->
        <div class="sub-section">
          <div class="sub-title">
            项目进度总览
          </div>
          <el-table
            :data="projectSummary"
            stripe
            size="default"
          >
            <el-table-column
              type="index"
              label="序号"
              width="70"
              align="center"
            />
            <el-table-column
              prop="projectCode"
              label="项目编号"
              width="130"
            />
            <el-table-column
              prop="projectName"
              label="项目名称"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column
              prop="managerName"
              label="项目经理"
              width="110"
            />
            <el-table-column
              label="进度"
              width="180"
            >
              <template #default="{ row }">
                <el-progress
                  :percentage="Number(row.progress) || 0"
                  :color="getProgressColor(Number(row.progress) || 0)"
                />
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="getStatusTagType(row.status)"
                >
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="startDate"
              label="计划开始"
              width="120"
            />
            <el-table-column
              prop="endDate"
              label="计划结束"
              width="120"
            />
            <el-table-column
              prop="taskTotal"
              label="任务总数"
              width="90"
              align="center"
            />
            <el-table-column
              prop="taskCompleted"
              label="已完成"
              width="90"
              align="center"
            />
            <el-table-column
              prop="taskInProgress"
              label="进行中"
              width="90"
              align="center"
            />
            <el-table-column
              prop="taskPending"
              label="未开始"
              width="90"
              align="center"
            />
            <el-table-column
              label="延期数"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  v-if="row.taskDelayed > 0"
                  size="small"
                  type="danger"
                  effect="dark"
                >
                  {{ row.taskDelayed }}
                </el-tag>
                <span v-else>{{ row.taskDelayed }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 任务进度明细表格 -->
        <div class="sub-section">
          <div class="sub-title">
            任务进度明细
          </div>
          <el-table
            :data="taskProgress"
            stripe
            size="default"
            max-height="480"
          >
            <el-table-column
              type="index"
              label="序号"
              width="70"
              align="center"
              fixed="left"
            />
            <el-table-column
              prop="projectName"
              label="项目名称"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="taskName"
              label="任务名称"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="assigneeName"
              label="负责人"
              width="100"
            />
            <el-table-column
              label="任务类型"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  v-if="row.isMilestone === 1"
                  size="small"
                  type="warning"
                  effect="dark"
                >
                  里程碑
                </el-tag>
                <el-tag
                  v-else-if="row.isKeyTask === 1"
                  size="small"
                  type="primary"
                >
                  关键
                </el-tag>
                <el-tag
                  v-else
                  size="small"
                  type="info"
                >
                  普通
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="startDate"
              label="计划开始"
              width="120"
            />
            <el-table-column
              prop="endDate"
              label="计划结束"
              width="120"
            />
            <el-table-column
              prop="actualStartDate"
              label="实际开始"
              width="120"
            >
              <template #default="{ row }">
                {{ row.actualStartDate || '-' }}
              </template>
            </el-table-column>
            <el-table-column
              prop="actualEndDate"
              label="实际结束"
              width="120"
            >
              <template #default="{ row }">
                {{ row.actualEndDate || '-' }}
              </template>
            </el-table-column>
            <el-table-column
              label="进度"
              width="180"
            >
              <template #default="{ row }">
                <el-progress
                  :percentage="Number(row.progress) || 0"
                  :color="getProgressColor(Number(row.progress) || 0)"
                />
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="getStatusTagType(row.status)"
                >
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="是否延期"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <span
                  v-if="row.isDelayed === 1"
                  style="color: #f5222d; font-weight: 600"
                >是</span>
                <span
                  v-else
                  style="color: #52c41a"
                >否</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="delayDays"
              label="延期天数"
              width="100"
              align="center"
            />
            <el-table-column
              prop="remark"
              label="备注"
              min-width="160"
              show-overflow-tooltip
            />
          </el-table>
        </div>

        <!-- 延期任务预警 -->
        <div class="sub-section">
          <div class="sub-title">
            延期任务预警
          </div>
          <el-table
            :data="delayedTasks"
            stripe
            size="default"
          >
            <el-table-column
              type="index"
              label="序号"
              width="70"
              align="center"
            />
            <el-table-column
              prop="projectName"
              label="项目名称"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="taskName"
              label="任务名称"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="assigneeName"
              label="负责人"
              width="100"
            />
            <el-table-column
              prop="endDate"
              label="计划结束"
              width="120"
            />
            <el-table-column
              prop="actualEndDate"
              label="实际结束"
              width="120"
            >
              <template #default="{ row }">
                {{ row.actualEndDate || '未完成' }}
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="getStatusTagType(row.status)"
                >
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="delayDays"
              label="延期天数"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  type="danger"
                  effect="dark"
                >
                  {{ row.delayDays }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="remark"
              label="备注"
              min-width="160"
              show-overflow-tooltip
            />
          </el-table>
          <EmptyState
            v-if="delayedTasks.length === 0"
            description="暂无延期任务，项目进度正常"
          />
        </div>
      </template>
    </el-card>

    <!-- C. 导出历史区 - P1-2.2：使用 CrudTable 通用布局组件（本页唯一的分页列表区域）。
         其余筛选区与预览区的报表表格为无分页展示，保留原 el-card + el-table 布局。 -->
    <CrudTable
      v-model:current-page="historyPage"
      v-model:page-size="historySize"
      :page-sizes="[10, 20, 50]"
      :data="historyRecords"
      :loading="historyLoading"
      :total="historyTotal"
      @change="loadHistory"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="title-chip title-chip--success"><el-icon><Files /></el-icon></span>
          <span class="toolbar-title">导出历史</span>
        </div>
        <div class="toolbar-right">
          <BaseButton
            variant="text"
            icon-name="Refresh"
            @click="loadHistory"
          >
            刷新
          </BaseButton>
        </div>
      </template>

      <el-table-column
        label="日期区间"
        min-width="200"
      >
        <template #default="{ row }">
          {{ row.startDate }} ~ {{ row.endDate }}
        </template>
      </el-table-column>
      <el-table-column
        label="格式"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.format === 'pdf' ? 'danger' : 'success'"
          >
            {{ row.format }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="fileName"
        label="文件名"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="fileSizeText"
        label="大小"
        width="100"
        align="right"
      />
      <el-table-column
        label="状态"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="getHistoryStatusType(row.status)"
          >
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        label="创建时间"
        width="170"
      />
      <el-table-column
        label="操作"
        width="160"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            icon-name="Download"
            @click="handleDownload(row)"
          >
            下载
          </BaseButton>
          <BaseButton
            variant="link-danger"
            icon-name="Delete"
            @click="handleDelete(row)"
          >
            删除
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>
  </div>
</template>

<script setup>
/**
 * 日报导出页
 * 业务用途：按日期范围预览甘特图进度日报（概览指标 + 项目进度总览 + 任务进度明细 + 延期任务预警），
 *           支持导出 Excel 报表，并管理导出历史（下载、删除）。
 * 数据来源：dailyReportApi（/daily-report/preview、/export、/history、/download、DELETE）。
 * 交互：导出时全屏遮罩提示，导出成功后刷新历史；删除前二次确认。
 *
 * P1-2.2：本页为报表预览页（非标准 CRUD 列表），仅「导出历史」区域有分页列表。
 *         因此仅将导出历史区域迁移至 CrudTable，其余筛选区与预览区的报表表格（无分页）保留原 el-card + el-table 布局。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, reactive, computed, onMounted } from 'vue'
import { ElLoading } from 'element-plus'
import { feedback } from '@/utils/feedback'
import dayjs from 'dayjs'
import { dailyReportApi } from '@/api/dailyReport'
import { projectApi } from '@/api/project'
import { downloadBlob } from '@/utils/download'
import { getProgressColor } from '@/utils/format'
import { getStatusTagType, getStatusText } from '@/utils/statusMap'
// P1-2.2：通用列表布局组件（仅用于导出历史区域）
import CrudTable from '@/components/CrudTable.vue'

// 日期范围，默认最近 7 天（含今天）
const dateRange = ref([
  dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD')
])

// 项目筛选：selectedProjectId 为 null 表示全部项目
const selectedProjectId = ref(null)
const projectOptions = ref([])
const projectLoading = ref(false)

// 预览数据加载中状态
const previewLoading = ref(false)
// 是否已执行过预览（用于控制预览区显隐）
const hasPreviewed = ref(false)
// 预览返回的报表标题与报表日期
const reportTitle = ref('')
const reportDate = ref('')
// 预览返回的概览指标（默认全 0，避免渲染时 undefined）
const summary = reactive({
  reportDate: '',
  totalProjects: 0,
  totalTasks: 0,
  completedTasks: 0,
  inProgressTasks: 0,
  pendingTasks: 0,
  delayedTasks: 0,
  milestoneCount: 0,
  keyTaskCount: 0
})
// 项目进度总览列表
const projectSummary = ref([])
// 任务进度明细列表
const taskProgress = ref([])

// 当前导出中状态（true=正在导出 Excel），用于按钮 loading
const exportLoading = ref(false)

// 导出历史相关状态
const historyLoading = ref(false)
const historyRecords = ref([])
const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)

// 延期任务预警：从 taskProgress 中筛选 isDelayed === 1 的任务
const delayedTasks = computed(() => {
  return taskProgress.value.filter(item => Number(item.isDelayed) === 1)
})

// 预览数据是否全部为空（用于显示空状态）
const isEmptyPreview = computed(() => {
  return (
    summary.totalProjects === 0 &&
    summary.totalTasks === 0 &&
    projectSummary.value.length === 0 &&
    taskProgress.value.length === 0
  )
})

/**
 * 校验日期范围是否有效，并组装查询参数（含可选 projectId）
 * @returns {Object|null} 解析后的 { startDate, endDate, projectId? }，无效时返回 null
 */
function getDateParams() {
  if (!dateRange.value || dateRange.value.length !== 2) {
    feedback.warning('请选择日期范围')
    return null
  }
  const params = {
    startDate: dateRange.value[0],
    endDate: dateRange.value[1]
  }
  // projectId 为 null 时不传，由后端查询全部项目
  if (selectedProjectId.value !== null && selectedProjectId.value !== undefined) {
    params.projectId = selectedProjectId.value
  }
  return params
}

/**
 * 预览日报数据
 * 步骤：1. 校验日期 → 2. 调用 preview 接口 → 3. 回填响应式数据 → 4. 标记已预览
 */
async function handlePreview() {
  const params = getDateParams()
  if (!params) return
  previewLoading.value = true
  try {
    const res = await dailyReportApi.preview(params)
    const data = res.data || {}
    reportTitle.value = data.reportTitle || ''
    // 回填概览指标
    const s = data.summary || {}
    reportDate.value = s.reportDate || ''
    summary.reportDate = s.reportDate || ''
    summary.totalProjects = s.totalProjects || 0
    summary.totalTasks = s.totalTasks || 0
    summary.completedTasks = s.completedTasks || 0
    summary.inProgressTasks = s.inProgressTasks || 0
    summary.pendingTasks = s.pendingTasks || 0
    summary.delayedTasks = s.delayedTasks || 0
    summary.milestoneCount = s.milestoneCount || 0
    summary.keyTaskCount = s.keyTaskCount || 0
    projectSummary.value = data.projectSummary || []
    taskProgress.value = data.taskProgress || []
    hasPreviewed.value = true
  } catch (error) {
    // 网络错误由 request.js 拦截器统一提示
    console.error('预览日报数据失败:', error)
  } finally {
    previewLoading.value = false
  }
}

/**
 * 导出 Excel 报表
 * 步骤：1. 校验日期 → 2. 全屏遮罩 → 3. 调用 export 接口 → 4. 成功提示并刷新历史
 */
async function handleExport() {
  const params = getDateParams()
  if (!params) return
  exportLoading.value = true
  const loading = ElLoading.service({
    fullscreen: true,
    lock: true,
    text: '正在生成报表，请稍候...',
    background: 'rgba(0, 0, 0, 0.5)'
  })
  try {
    await dailyReportApi.export({ ...params })
    feedback.success('报表导出成功')
    // 导出成功后刷新历史列表
    await loadHistory()
  } catch (error) {
    console.error('导出报表失败:', error)
  } finally {
    loading.close()
    exportLoading.value = false
  }
}

/**
 * 加载导出历史列表
 */
async function loadHistory() {
  historyLoading.value = true
  try {
    // 修复 M25：分页参数命名与其他模块统一为 pageNum/pageSize，原 page/size 与 project/document 等模块不一致
    const res = await dailyReportApi.getHistory({
      pageNum: historyPage.value,
      pageSize: historySize.value
    })
    const data = res.data || {}
    historyRecords.value = data.records || []
    historyTotal.value = data.total || 0
  } catch (error) {
    console.error('加载导出历史失败:', error)
  } finally {
    historyLoading.value = false
  }
}

/**
 * 下载导出文件
 * 步骤：1. 调用 download 接口获取 blob → 2. 创建临时 URL → 3. 触发浏览器下载 → 4. 释放 URL
 * @param {Object} row 历史记录行
 */
async function handleDownload(row) {
  try {
    const res = await dailyReportApi.download(row.id)
    downloadBlob(res.data, row.fileName || `daily-report-${row.id}.${row.format}`)
  } catch (error) {
    console.error('下载文件失败:', error)
    feedback.error('下载失败，请稍后重试')
  }
}

/**
 * 删除导出历史记录
 * 步骤：1. 二次确认 → 2. 调用 delete 接口 → 3. 成功提示并刷新列表
 * @param {Object} row 历史记录行
 */
function handleDelete(row) {
  feedback.confirmDelete(`确认删除导出记录「${row.fileName}」吗？`)
    .then(async () => {
      try {
        await dailyReportApi.remove(row.id)
        feedback.success('删除成功')
        // 删除后若当前页已无数据且非第一页，回退一页
        if (historyRecords.value.length === 1 && historyPage.value > 1) {
          historyPage.value--
        }
        await loadHistory()
      } catch (error) {
        console.error('删除导出记录失败:', error)
      }
    })
    .catch(() => {})
}

// P3 重构：进度颜色/状态 tag/状态文本映射统一改用 utils/format 与 utils/statusMap
// 的共享实现（进度色阈值随之对齐项目标准 100/50/25），删除本地重复定义

/**
 * 导出历史状态 → el-tag 类型映射
 * @param {string} status 历史状态（成功/失败/处理中等）
 * @returns {string} el-tag type
 */
function getHistoryStatusType(status) {
  if (!status) return 'info'
  if (status === '成功' || status === 'SUCCESS' || status === 'COMPLETED') return 'success'
  if (status === '失败' || status === 'FAILED' || status === 'ERROR') return 'danger'
  if (status === '处理中' || status === 'PROCESSING' || status === 'PENDING') return 'warning'
  return 'info'
}

/**
 * 加载项目下拉选项
 * 拉取较大分页以填充项目选择器（排除已归档项目）。
 */
async function loadProjectOptions() {
  projectLoading.value = true
  try {
    // 修复 M25：分页参数统一为 pageNum/pageSize，原 page/size 与项目模块 getList 约定不一致
    const res = await projectApi.getList({ pageNum: 1, pageSize: 200, archived: 0 })
    projectOptions.value = (res.data && res.data.records) || []
  } catch (error) {
    console.error('加载项目列表失败:', error)
    projectOptions.value = []
  } finally {
    projectLoading.value = false
  }
}

onMounted(() => {
  // 进入页面即加载项目下拉与导出历史，便于用户查看历史报表
  loadProjectOptions()
  loadHistory()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 20px;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.section-card {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-weight: 600;
  font-size: 15px;
  flex: 1;
}

.section-sub {
  font-size: 13px;
  color: #909399;
}

.summary-row {
  margin-bottom: 24px;
  padding: 12px 0;
  border-bottom: 1px dashed #e5e7eb;
}

.summary-row :deep(.el-statistic) {
  text-align: center;
}

.sub-section {
  margin-top: 20px;
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #1890ff;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
