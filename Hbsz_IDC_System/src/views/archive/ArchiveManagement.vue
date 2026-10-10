<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          归档管理
        </div>
        <div class="page-subtitle">
          管理已归档项目，支持归档检索与恢复
        </div>
      </div>
    </div>

    <!-- 统计卡片（StatCard 全局组件统一视觉） -->
    <el-row
      :gutter="20"
      style="margin-bottom: 20px"
    >
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Box"
          tone="primary"
          :value="archivedProjects.length"
          label="归档项目总数"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Finished"
          tone="success"
          :value="completedArchived"
          label="已完成归档"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Money"
          tone="danger"
          :value="'¥' + formatAmount(totalArchivedAmount)"
          label="归档项目总额"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Document"
          tone="warning"
          :value="archivedDocCount"
          label="归档文档数"
        />
      </el-col>
    </el-row>

    <!-- P1-2.2：使用 CrudTable 通用布局组件；本页采用客户端筛选 + 客户端分页（数据量小，即时反馈优于服务端往返），
         因此不使用 useCrudList，仅复用 CrudTable 的 el-card + toolbar + el-table + 分页布局 -->
    <CrudTable
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :data="pagedArchivedProjects"
      :total="filteredArchivedProjects.length"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="title-chip title-chip--primary"><el-icon><Box /></el-icon></span>
          <span class="toolbar-title">归档项目列表</span>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="searchForm.keyword"
            placeholder="项目名称/编号/客户"
            clearable
            prefix-icon="Search"
            style="width: 280px"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="searchForm.type"
            placeholder="项目类型"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="t in typeOptions"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="归档开始日期"
            end-placeholder="归档结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
          <BaseButton
            variant="primary"
            icon-name="Search"
            @click="handleSearch"
          >
            查询
          </BaseButton>
          <BaseButton
            icon-name="RefreshLeft"
            @click="resetSearch"
          >
            重置
          </BaseButton>
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
            @click="goDetail(row.id)"
          >
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        prop="customerName"
        label="客户名称"
        min-width="160"
      />
      <el-table-column
        prop="type"
        label="类型"
        width="80"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.type === '新建' ? 'success' : 'warning'"
          >
            {{ row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="合同金额"
        width="120"
        align="right"
      >
        <template #default="{ row }">
          ¥{{ formatAmount(row.contractAmount) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="progress"
        label="完成进度"
        width="100"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            type="success"
          >
            {{ row.progress }}%
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="manager"
        label="项目经理"
        width="100"
      />
      <el-table-column
        prop="endDate"
        label="完工日期"
        width="120"
      />
      <el-table-column
        prop="archiveDate"
        label="归档日期"
        width="120"
      >
        <template #default="{ row }">
          {{ row.archiveDate || row.endDate }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            icon-name="View"
            @click="goDetail(row.id)"
          >
            查看
          </BaseButton>
          <BaseButton
            variant="link-success"
            size="small"
            icon-name="RefreshLeft"
            @click="handleUnarchive(row)"
          >
            恢复
          </BaseButton>
          <BaseButton
            variant="link-danger"
            size="small"
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
 * 归档管理页
 * 业务用途：管理已归档项目，支持按关键词/类型/归档日期检索，并可查看、恢复或彻底删除。
 * 主要功能区：顶部统计卡片（归档项目数/已完成归档/归档总额/归档文档数）、搜索筛选栏、
 *             归档项目表格与分页。
 * 数据来源：projectStore（归档项目列表、恢复/删除接口）、documentStore（统计归档文档数），
 *          类型选项来自 @/constants/options。
 *
 * P1-2.2：使用 CrudTable 通用布局组件封装表格 + 分页模板。
 *         本页采用客户端筛选 + 客户端分页（归档项目数据量小，即时筛选体验优于服务端往返），
 *         因此未使用 useCrudList —— queryParams/currentPage/pageSize 由本组件管理，
 *         filteredArchivedProjects/pagedArchivedProjects 为 computed，分页参数变化自动重算。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useDocumentStore } from '@/stores/document'
import { formatAmount } from '@/utils/format'
import { projectTypeOptions } from '@/constants/options'
import { feedback } from '@/utils/feedback'
// P1-2.2：通用列表布局组件
import CrudTable from '@/components/CrudTable.vue'

const router = useRouter()
const projectStore = useProjectStore()
const documentStore = useDocumentStore()

const typeOptions = projectTypeOptions  // 项目类型筛选选项
const searchForm = ref({ keyword: '', type: '', dateRange: [] })  // 搜索筛选表单（关键词/类型/归档日期范围）
// 分页参数：绑定到 CrudTable 的 v-model，分页变化时 pagedArchivedProjects computed 自动重算
const currentPage = ref(1)
const pageSize = ref(10)

const archivedProjects = computed(() => projectStore.archivedProjects)  // 全部已归档项目

// 前端过滤：按关键词（名称/编号/客户）、类型、归档日期范围筛选
const filteredArchivedProjects = computed(() => {
  return archivedProjects.value.filter(p => {
    let match = true
    if (searchForm.value.keyword) {
      const kw = searchForm.value.keyword.toLowerCase()
      match = match && (
        p.name.toLowerCase().includes(kw) ||
        (p.code || '').toLowerCase().includes(kw) ||
        (p.customerName || '').toLowerCase().includes(kw)
      )
    }
    if (searchForm.value.type) match = match && p.type === searchForm.value.type
    // 日期范围过滤（基于归档日期，若无归档日期则使用完工日期）
    if (searchForm.value.dateRange && searchForm.value.dateRange.length === 2) {
      const [start, end] = searchForm.value.dateRange
      const archiveDate = p.archiveDate || p.endDate
      match = match && archiveDate >= start && archiveDate <= end
    }
    return match
  })
})

// 分页后的数据：基于 filteredArchivedProjects 切片，分页参数变化时自动重算
const pagedArchivedProjects = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredArchivedProjects.value.slice(start, start + pageSize.value)
})

// P2 修复：删除最后一页最后一条（或筛选后结果变少）时 currentPage 越界显示空页，
// watch 筛选结果长度并回退到最后一个有效页
watch(() => filteredArchivedProjects.value.length, () => {
  const totalPages = Math.ceil(filteredArchivedProjects.value.length / pageSize.value)
  if (currentPage.value > totalPages && totalPages > 0) {
    currentPage.value = totalPages
  }
})

const completedArchived = computed(() => archivedProjects.value.filter(p => p.status === '已完成').length)  // 已完成的归档项目数
const totalArchivedAmount = computed(() => archivedProjects.value.reduce((sum, p) => sum + (Number(p.contractAmount) || 0), 0))  // 归档项目合同总额
// 归档项目关联的文档数量
const archivedDocCount = computed(() => {
  const ids = archivedProjects.value.map(p => String(p.id))
  return documentStore.documents.filter(d => ids.includes(String(d.projectId))).length
})

/** 点击查询：回到第 1 页（客户端筛选，computed 自动重算） */
function handleSearch() {
  currentPage.value = 1
}

/** 重置筛选并回到第 1 页（客户端筛选，computed 自动重算） */
function resetSearch() {
  searchForm.value = { keyword: '', type: '', dateRange: [] }
  currentPage.value = 1
}

/** 拉取已归档项目列表（archived=true，一次性拉取 pageSize=100 条用于客户端筛选） */
async function fetchArchivedList() {
  try {
    await projectStore.fetchList({ archived: true, pageSize: 100, ...searchForm.value })
  } catch (error) {
    console.error('加载归档项目失败:', error)
  }
}

/** 跳转项目详情页 @param {number|string} id 项目 ID */
function goDetail(id) {
  router.push(`/projects/${id}`)
}

/** 恢复归档：二次确认后调用取消归档接口并刷新列表 @param {Object} row 项目对象 */
function handleUnarchive(row) {
  feedback.confirm(`确认恢复项目「${row.name}」为正常状态吗？`, { title: '恢复确认', type: 'info' })
    .then(async () => {
      await projectStore.unarchiveProject(row.id)
      fetchArchivedList()
    }).catch(() => {})
}

/** 彻底删除项目：强警告二次确认后调用删除接口并刷新列表（不可恢复）@param {Object} row 项目对象 */
function handleDelete(row) {
  feedback.confirmDelete(`确认彻底删除项目「${row.name}」吗？此操作不可恢复！`).then(async () => {
    await projectStore.deleteProject(row.id)
    fetchArchivedList()
  }).catch(() => {})
}

// 进入页面：加载归档项目列表，并加载文档用于归档文档数统计
onMounted(() => {
  fetchArchivedList()
  documentStore.fetchList({ pageSize: 100 }).catch(() => {})
})
</script>

<style scoped>
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
