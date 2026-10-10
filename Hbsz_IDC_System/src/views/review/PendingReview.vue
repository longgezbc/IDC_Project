<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <span class="page-title">进度审核中心</span>
        <span class="page-subtitle">集中处理所有项目的进度填报审核</span>
      </div>
      <div>
        <BaseButton
          icon-name="Refresh"
          @click="loadData"
        >
          刷新
        </BaseButton>
      </div>
    </div>

    <!-- 统计卡片（StatCard 全局组件统一视觉） -->
    <el-row
      :gutter="20"
      style="margin-bottom: 20px"
    >
      <el-col
        :xs="12"
        :sm="8"
      >
        <StatCard
          icon="Bell"
          tone="warning"
          :value="pendingList.length"
          label="待审核总数"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="8"
      >
        <StatCard
          icon="OfficeBuilding"
          tone="primary"
          :value="involvedProjectCount"
          label="涉及项目数"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="8"
      >
        <StatCard
          icon="Clock"
          tone="success"
          :value="todayCount"
          label="今日新增"
        />
      </el-col>
    </el-row>

    <!-- P1-2.2：使用 CrudTable 通用布局组件；本页采用客户端筛选 + 客户端分页（全量加载后前端切片），
         因此不使用 useCrudList，仅复用 CrudTable 的 el-card + toolbar + el-table + 分页布局 -->
    <CrudTable
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      :data="pagedList"
      :total="filteredList.length"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="title-chip title-chip--warning"><el-icon><Bell /></el-icon></span>
          <span class="toolbar-title">待审核进度填报</span>
        </div>
        <div class="toolbar-right">
          <el-select
            v-model="filterProjectId"
            placeholder="按项目筛选"
            clearable
            style="width: 280px"
            @change="onFilterChange"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
          <el-input
            v-model="keyword"
            placeholder="搜索任务/项目/填报人"
            clearable
            style="width: 240px"
            @input="onFilterChange"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <span class="filter-info">共 {{ filteredList.length }} 条待审核</span>
        </div>
      </template>

      <el-table-column
        type="index"
        label="#"
        width="50"
      />
      <el-table-column
        label="项目 / 任务"
        min-width="220"
      >
        <template #default="{ row }">
          <div class="task-info">
            <div class="task-info-name">
              {{ row.taskName || '-' }}
            </div>
            <div class="task-info-project">
              {{ row.projectName || '-' }}
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="填报人 / 时间"
        width="160"
      >
        <template #default="{ row }">
          <div class="reporter-name">
            {{ row.reporterName || '-' }}
          </div>
          <div class="task-info-project">
            {{ formatDateTime(row.reportTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="进度变化"
        width="150"
      >
        <template #default="{ row }">
          <span class="progress-change">
            <span class="old">{{ row.previousProgress ?? 0 }}%</span>
            <el-icon class="arrow-icon"><ArrowDown /></el-icon>
            <span class="new">{{ row.progress ?? 0 }}%</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="施工图片"
        width="130"
      >
        <template #default="{ row }">
          <span
            v-if="!row.images || row.images.length === 0"
            class="text-muted"
          >无</span>
          <div
            v-else
            class="thumb-list"
          >
            <el-image
              v-for="(img, idx) in row.images.slice(0, 3)"
              :key="img + '-' + idx"
              :src="resolveImageUrl(img)"
              :alt="`进度图片${idx + 1}`"
              :preview-src-list="row.images.map(i => resolveImageUrl(i))"
              :initial-index="idx"
              fit="cover"
              class="thumb-item"
              :preview-teleported="true"
            />
            <span
              v-if="row.images.length > 3"
              class="thumb-more"
            >+{{ row.images.length - 3 }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="110"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="primary"
            size="small"
            icon-name="View"
            @click="openReview(row)"
          >
            审核
          </BaseButton>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState description="暂无待审核记录" />
      </template>
    </CrudTable>

    <!-- 审核对话框 -->
    <el-dialog
      v-model="showReview"
      title="进度审核"
      width="720px"
      top="5vh"
    >
      <el-form
        :model="reviewForm"
        label-width="100px"
      >
        <el-descriptions
          :column="2"
          border
          size="small"
          style="margin-bottom: 16px"
        >
          <el-descriptions-item label="项目">
            {{ reviewForm.projectName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="任务">
            {{ reviewForm.taskName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="填报人">
            {{ reviewForm.reporterName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="填报时间">
            {{ formatDateTime(reviewForm.reportTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="原进度">
            {{ reviewForm.previousProgress ?? 0 }}%
          </el-descriptions-item>
          <el-descriptions-item label="本次进度">
            <span class="new-progress">{{ reviewForm.progress ?? 0 }}%</span>
          </el-descriptions-item>
          <el-descriptions-item label="填报备注">
            {{ reviewForm.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item
          v-if="reviewForm.images && reviewForm.images.length"
          label="施工图片"
        >
          <div class="review-images">
            <el-image
              v-for="(img, idx) in reviewForm.images"
              :key="img + '-' + idx"
              :src="resolveImageUrl(img)"
              :alt="`审核图片${idx + 1}`"
              :preview-src-list="reviewForm.images.map(i => resolveImageUrl(i))"
              :initial-index="idx"
              fit="cover"
              class="review-image-item"
              :preview-teleported="true"
            />
          </div>
        </el-form-item>
        <el-form-item label="审核结果">
          <el-radio-group v-model="reviewForm.approve">
            <el-radio :value="true">
              通过
            </el-radio>
            <el-radio :value="false">
              驳回
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input
            v-model="reviewForm.reviewRemark"
            type="textarea"
            :rows="3"
            :placeholder="reviewForm.approve ? '审核通过意见（可选）' : '请输入驳回原因'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showReview = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="reviewSubmitting"
          @click="handleReview"
        >
          提交审核
        </BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 进度审核中心 - 独立页面
 * 业务用途：集中展示所有项目的待审核进度填报，便于项目经理统一审核，无需逐个进入项目页。
 * 主要功能：统计卡片（待审核总数/涉及项目数/今日新增）、按项目与关键字筛选、
 *          待审核列表（含施工图片缩略图预览）、审核对话框（通过/驳回 + 意见）。
 * 数据来源：progressStore.fetchPendingLogs（不传 projectId 时返回全部待审核记录）。
 * 权限要求：task:progress:review（在路由 meta.permission 配置）
 *
 * P1-2.2：使用 CrudTable 通用布局组件封装 el-card + toolbar + el-table + 分页的模板与 CSS。
 *        本页采用客户端筛选 + 客户端分页（全量加载后前端切片），因此未使用 useCrudList ——
 *        pendingList/filteredList/pagedList 由本组件管理，currentPage/pageSize 绑定到 CrudTable 的 v-model，
 *        分页参数变化时 pagedList computed 自动重算。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, reactive, computed, onMounted } from 'vue'
import { useProgressStore } from '@/stores/progress'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { feedback } from '@/utils/feedback'
import { formatDateTime } from '@/utils/format'
import { resolveFileUrl } from '@/utils/fileUrl'
// P1-2.2：通用列表布局组件
import CrudTable from '@/components/CrudTable.vue'

const progressStore = useProgressStore()
const projectStore = useProjectStore()
const userStore = useUserStore()

const pendingList = ref([])         // 后端返回的待审核列表（全量）
const filterProjectId = ref(null)   // 项目筛选
const keyword = ref('')             // 关键字搜索（任务名/项目名/填报人）
const currentPage = ref(1)
const pageSize = ref(10)
const showReview = ref(false)
const reviewSubmitting = ref(false)

// 审核表单
const reviewForm = reactive({
  logId: null,
  projectName: '',
  taskName: '',
  reporterName: '',
  reportTime: '',
  previousProgress: 0,
  progress: 0,
  remark: '',
  images: [],
  approve: true,
  reviewRemark: ''
})

// 项目下拉选项：合并 projectStore 列表与待审核列表中出现但未加载的项目
const projectOptions = computed(() => {
  const all = projectStore.projects || []
  const seen = new Set()
  const result = []
  all.forEach(p => {
    if (!seen.has(String(p.id))) {
      seen.add(String(p.id))
      result.push({ id: p.id, name: p.name })
    }
  })
  pendingList.value.forEach(p => {
    if (!seen.has(String(p.projectId))) {
      seen.add(String(p.projectId))
      result.push({ id: p.projectId, name: p.projectName })
    }
  })
  return result
})

// 涉及项目数（去重统计）
const involvedProjectCount = computed(() => {
  const set = new Set(pendingList.value.map(p => String(p.projectId)))
  return set.size
})

// 今日新增数（按 reportTime 日期前缀匹配）
const todayCount = computed(() => {
  const today = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const todayStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
  return pendingList.value.filter(p => p.reportTime && String(p.reportTime).startsWith(todayStr)).length
})

// 应用筛选条件后的列表
const filteredList = computed(() => {
  let list = pendingList.value
  if (filterProjectId.value) {
    list = list.filter(p => String(p.projectId) === String(filterProjectId.value))
  }
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(p => {
      const taskName = (p.taskName || '').toLowerCase()
      const reporter = (p.reporterName || '').toLowerCase()
      const projectName = (p.projectName || '').toLowerCase()
      return taskName.includes(kw) || reporter.includes(kw) || projectName.includes(kw)
    })
  }
  return list
})

// 当前页数据（前端分页）
const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

/** 加载待审核列表（不传 projectId 拉取全部） */
async function loadData() {
  try {
    const list = await progressStore.fetchPendingLogs()
    pendingList.value = list || []
  } catch (error) {
    console.error('加载待审核列表失败:', error)
    feedback.error('加载待审核列表失败')
  }
}

/** 筛选条件变化时重置到第一页 */
function onFilterChange() {
  currentPage.value = 1
}

// P3 重构：图片 URL 解析改用 utils/fileUrl 统一实现，别名保持模板引用不变
const resolveImageUrl = resolveFileUrl

/** 打开审核对话框，加载当前日志到表单 */
function openReview(log) {
  reviewForm.logId = log.id
  reviewForm.projectName = log.projectName
  reviewForm.taskName = log.taskName
  reviewForm.reporterName = log.reporterName
  reviewForm.reportTime = log.reportTime
  reviewForm.previousProgress = log.previousProgress
  reviewForm.progress = log.progress
  reviewForm.remark = log.remark
  reviewForm.images = log.images || []
  reviewForm.approve = true
  reviewForm.reviewRemark = ''
  showReview.value = true
}

/** 提交审核：调用 store，成功后从列表中移除并自动翻页 */
async function handleReview() {
  if (!reviewForm.logId) {
    feedback.warning('未选择待审核日志')
    return
  }
  const userInfo = userStore.userInfo || {}
  const operationType = reviewForm.approve ? '通过' : '驳回'
  const startTime = new Date()
  reviewSubmitting.value = true
  try {
    await progressStore.reviewProgress(reviewForm.logId, {
      approve: reviewForm.approve,
      remark: reviewForm.reviewRemark
    })
    // 从列表中移除已审核记录
    pendingList.value = pendingList.value.filter(p => String(p.id) !== String(reviewForm.logId))
    showReview.value = false
    // 当前页已空时回退到上一页
    const totalPages = Math.ceil(filteredList.value.length / pageSize.value)
    if (currentPage.value > totalPages && totalPages > 0) {
      currentPage.value = totalPages
    }
  } catch (error) {
    // 失败日志：记录错误信息便于排查
    // 修复：生产环境不输出审计日志，避免泄露用户敏感信息（reviewerId/reviewerName 等）
    if (!import.meta.env.PROD) {
      const endTime = new Date()
      console.error('[进度审核][前端-失败]', {
        logId: reviewForm.logId,
        operation: operationType,
        reviewerId: userInfo.id,
        reviewerName: userInfo.username,
        clientStartTime: formatDateTime(startTime),
        clientEndTime: formatDateTime(endTime),
        costMs: endTime - startTime,
        errorMessage: error?.message || String(error),
        errorCode: error?.response?.data?.code,
        errorMsg: error?.response?.data?.message,
        httpStatus: error?.response?.status
      })
    }
  } finally {
    reviewSubmitting.value = false
  }
}

onMounted(async () => {
  await loadData()
  // 加载项目列表用于下拉筛选（失败不影响主流程）
  try {
    await projectStore.fetchList({ pageSize: 100 })
  } catch (e) {
    // 忽略：项目列表加载失败时，下拉框仍会展示待审核列表中涉及的项目
  }
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.page-subtitle {
  margin-left: 12px;
  color: #999;
  font-size: 13px;
}
/* 统计卡样式已由全局 StatCard 组件接管 */
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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
.filter-info {
  color: #999;
  font-size: 13px;
  margin-left: auto;
}
.task-info-name {
  font-weight: 500;
  color: #303133;
}
.task-info-project {
  color: #999;
  font-size: 12px;
  margin-top: 2px;
}
.reporter-name {
  color: #303133;
}
.progress-change {
  display: flex;
  align-items: center;
  gap: 6px;
}
.progress-change .old {
  color: #999;
}
.progress-change .new {
  color: #1890ff;
  font-weight: 600;
}
.arrow-icon {
  color: #999;
}
.new-progress {
  color: #1890ff;
  font-weight: 600;
}
.thumb-list {
  display: flex;
  align-items: center;
  gap: 4px;
}
.thumb-item {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  border: 1px solid #e0e0e0;
}
.thumb-more {
  color: #999;
  font-size: 12px;
  margin-left: 4px;
}
.text-muted {
  color: #999;
}
.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.review-image-item {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid #e0e0e0;
}
</style>
