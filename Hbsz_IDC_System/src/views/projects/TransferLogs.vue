<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          项目转移/授权日志
        </div>
        <div class="page-subtitle">
          审计追踪：负责人移交、工程师转移 / 授权 / 撤销等全部协作操作记录
        </div>
      </div>
      <BaseButton
        icon-name="Back"
        @click="router.back()"
      >
        返回
      </BaseButton>
    </div>

    <!-- P1-2.2：使用 CrudTable 通用布局组件；本页 buildQueryParams 合并 activeTab 与 searchForm.actionType，
         且分栏（tabs）会覆盖操作类型筛选，逻辑较复杂，因此保留原有数据流，
         仅复用 CrudTable 的 el-card + toolbar + el-table + 分页布局，不使用 useCrudList -->
    <CrudTable
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :data="logs"
      :loading="loading"
      :total="total"
      @change="fetchLogs"
    >
      <template #toolbar>
        <!-- 工具栏：操作类型分栏 + 搜索表单（分栏覆盖 searchForm.actionType） -->
        <div class="transfer-toolbar">
          <el-tabs
            v-model="activeTab"
            class="transfer-tabs"
            @tab-change="handleTabChange"
          >
            <el-tab-pane
              label="全部"
              name="all"
            />
            <el-tab-pane
              v-for="t in ACTION_TABS"
              :key="t.value"
              :label="t.label"
              :name="t.value"
            />
          </el-tabs>
          <el-form
            :inline="true"
            :model="searchForm"
            class="search-form"
          >
            <el-form-item label="项目">
              <el-select
                v-model="searchForm.projectId"
                placeholder="全部项目"
                clearable
                filterable
                style="width: 220px"
              >
                <el-option
                  v-for="p in projectOptions"
                  :key="p.id"
                  :label="p.name"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="操作类型">
              <el-select
                v-model="searchForm.actionType"
                placeholder="全部类型"
                clearable
                style="width: 140px"
              >
                <el-option
                  v-for="t in ACTION_TABS"
                  :key="t.value"
                  :label="t.label"
                  :value="t.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 240px"
                @change="handleDateChange"
              />
            </el-form-item>
            <el-form-item>
              <BaseButton
                variant="primary"
                icon-name="Search"
                @click="handleSearch"
              >
                查询
              </BaseButton>
              <BaseButton
                icon-name="Refresh"
                @click="resetSearch"
              >
                重置
              </BaseButton>
            </el-form-item>
          </el-form>
        </div>
      </template>

      <el-table-column
        prop="operatedAt"
        label="操作时间"
        width="160"
      >
        <template #default="{ row }">
          {{ formatTime(row.operatedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="projectName"
        label="项目名称"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="actionType"
        label="操作类型"
        width="100"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="getActionTagType(row.actionType)"
            effect="dark"
          >
            {{ getActionText(row.actionType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="fromEngineerName"
        label="原人员"
        width="120"
      >
        <template #default="{ row }">
          <span v-if="row.fromEngineerName">{{ row.fromEngineerName }}</span>
          <span
            v-else
            style="color: #c0c4cc"
          >—</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="toEngineerName"
        label="目标人员"
        width="120"
      >
        <template #default="{ row }">
          <span v-if="row.toEngineerName">{{ row.toEngineerName }}</span>
          <span
            v-else
            style="color: #c0c4cc"
          >—</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="operatorName"
        label="操作人（PM）"
        width="140"
      />
      <el-table-column
        prop="remark"
        label="备注"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span v-if="row.remark">{{ row.remark }}</span>
          <span
            v-else
            style="color: #c0c4cc"
          >—</span>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState description="暂无转移日志" />
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
/**
 * 项目转移/授权审计日志页
 *
 * 业务用途：审计追踪 PM 对项目授权的所有变更记录（assign/revoke/transfer）。
 * 展示字段：操作时间、项目名、操作类型、原工程师、目标工程师、操作人、备注。
 * 权限：project:query（管理员与项目经理可见，工程师仅能查询自己相关的项目）。
 *
 * P1-2.2：使用 CrudTable 通用布局组件封装 el-card + toolbar + el-table + 分页的模板与 CSS。
 *        本页 buildQueryParams 合并 activeTab 与 searchForm.actionType（分栏覆盖操作类型），
 *        逻辑较复杂，因此保留原有数据流（fetchLogs/buildQueryParams/handleTabChange），
 *        仅复用 CrudTable 布局，不使用 useCrudList。分页变化由 CrudTable 的 @change 事件触发 fetchLogs。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { feedback } from '@/utils/feedback'
import { projectAuthApi } from '@/api/projectAuthorization'
import { useProjectStore } from '@/stores/project'
import { formatDateTime } from '@/utils/format'
import { getActionText, getActionTagType } from '@/utils/statusMap'
// P1-2.2：通用列表布局组件
import CrudTable from '@/components/CrudTable.vue'

const router = useRouter()
const projectStore = useProjectStore()

/**
 * 操作类型分栏/筛选统一数据源（tabs 与下拉选项由此派生，新增类型只需在此追加一行）。
 * 注：直接授权现走申请-审批链路（产生 auth_request + approve 审计），
 * "直接授权(旧)"仅用于查询历史存量 assign 日志；文本/标签色映射见 statusMap.js。
 */
const ACTION_TABS = [
  { label: '负责人移交', value: 'transfer_leader' },
  { label: '转移', value: 'transfer' },
  { label: '授权申请', value: 'auth_request' },
  { label: '审批通过', value: 'approve' },
  { label: '审批驳回', value: 'reject' },
  { label: '延期', value: 'extend' },
  { label: '直接授权(旧)', value: 'assign' },
  { label: '撤销', value: 'revoke' }
]

// 筛选表单
const searchForm = reactive({
  projectId: null,
  actionType: '',
  dateBegin: '',
  dateEnd: ''
})
const dateRange = ref([])
const activeTab = ref('all')

// 分页：绑定到 CrudTable 的 v-model，分页变化时 @change 触发 fetchLogs
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 数据
const logs = ref([])
const loading = ref(false)
const projectOptions = ref([])

/**
 * 处理日期范围变化
 */
function handleDateChange(val) {
  if (val && val.length === 2) {
    searchForm.dateBegin = val[0]
    searchForm.dateEnd = val[1]
  } else {
    searchForm.dateBegin = ''
    searchForm.dateEnd = ''
  }
}

/**
 * 构造查询参数
 */
function buildQueryParams() {
  const params = {
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (searchForm.projectId) params.projectId = searchForm.projectId
  // 分栏覆盖操作类型
  const typeFilter = activeTab.value !== 'all' ? activeTab.value : searchForm.actionType
  if (typeFilter) params.actionType = typeFilter
  if (searchForm.dateBegin) params.dateBegin = searchForm.dateBegin
  if (searchForm.dateEnd) params.dateEnd = searchForm.dateEnd
  return params
}

/**
 * 拉取审计日志
 */
async function fetchLogs() {
  loading.value = true
  try {
    const res = await projectAuthApi.transferLogs(buildQueryParams())
    logs.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    console.error('[转移日志] 拉取失败:', e?.message)
    feedback.error('拉取日志失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchLogs()
}

function resetSearch() {
  searchForm.projectId = null
  searchForm.actionType = ''
  searchForm.dateBegin = ''
  searchForm.dateEnd = ''
  dateRange.value = []
  activeTab.value = 'all'
  currentPage.value = 1
  fetchLogs()
}

function handleTabChange() {
  currentPage.value = 1
  fetchLogs()
}

/* ===== 工具方法 ===== */

// P3 重构：改用 utils/format 统一实现（秒级格式），别名保持模板引用不变
const formatTime = (timeStr) => formatDateTime(timeStr, 'YYYY-MM-DD HH:mm:ss')

// P3 重构：操作类型文本/tag 映射统一改用 utils/statusMap 共享实现，删除本地重复定义

onMounted(async () => {
  // 加载项目下拉（PM/admin 看全部，工程师看自己的可见项目）
  try {
    await projectStore.fetchList({ pageSize: 100 })
    projectOptions.value = projectStore.projects
  } catch (e) {
    console.warn('[转移日志] 加载项目下拉失败:', e?.message)
  }
  fetchLogs()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

/* 工具栏：分栏 tabs + 搜索表单纵向堆叠（覆盖 .crud-toolbar 的 flex 横向布局） */
.transfer-toolbar {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.transfer-tabs {
  margin-bottom: 0;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}
</style>
