<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          我的任务
        </div>
        <div class="page-subtitle">
          查看分配给我的所有任务，支持按状态筛选与逾期标识
        </div>
      </div>
    </div>

    <!-- 汇总卡片 -->
    <div class="stat-cards">
      <div
        class="stat-card clickable"
        @click="filterByStatus('')"
      >
        <div
          class="stat-icon"
          style="background: #e6f7ff; color: #1890ff"
        >
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">
            {{ summary.total }}
          </div>
          <div class="stat-label">
            全部任务
          </div>
        </div>
      </div>
      <div
        class="stat-card clickable"
        title="基于当前页数据统计"
        @click="filterByStatus('进行中')"
      >
        <div
          class="stat-icon"
          style="background: #fff7e6; color: #fa8c16"
        >
          <el-icon><Loading /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">
            {{ summary.inProgress }}
          </div>
          <div class="stat-label">
            进行中
          </div>
        </div>
      </div>
      <div
        class="stat-card clickable"
        title="基于当前页数据统计"
        @click="filterByStatus('未开始')"
      >
        <div
          class="stat-icon"
          style="background: #f0f0f0; color: #8c8c8c"
        >
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">
            {{ summary.pending }}
          </div>
          <div class="stat-label">
            待开始
          </div>
        </div>
      </div>
      <div
        class="stat-card clickable"
        title="基于当前页数据统计"
        @click="filterByStatus('已完成')"
      >
        <div
          class="stat-icon"
          style="background: #f6ffed; color: #52c41a"
        >
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">
            {{ summary.completed }}
          </div>
          <div class="stat-label">
            已完成
          </div>
        </div>
      </div>
      <div
        class="stat-card clickable"
        @click="filterOverdue"
      >
        <div
          class="stat-icon"
          style="background: #fff1f0; color: #ff4d4f"
        >
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-body">
          <div
            class="stat-value"
            style="color: #ff4d4f"
          >
            {{ summary.overdue }}
          </div>
          <div class="stat-label">
            逾期任务
          </div>
        </div>
      </div>
    </div>

    <!-- P1-2.2：使用 CrudTable 通用布局组件 + useCrudList 组合式函数。
         本页混合筛选模式：status 为服务端筛选（发送到后端），keyword 与 timeFilter 为客户端筛选
         （仅对当前页数据二次过滤）。fetchFn 包装层仅向后端传递 status，keyword/timeFilter 由
         filteredTasks computed 在前端应用，保留原有行为。 -->
    <CrudTable
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :page-sizes="[10, 20, 50]"
      :data="filteredTasks"
      :loading="loading"
      :total="total"
      @change="fetchList"
    >
      <template #toolbar>
        <div class="toolbar-left" />
        <el-form
          :inline="true"
          :model="queryParams"
          class="search-form"
        >
          <el-form-item label="任务状态">
            <el-radio-group
              v-model="queryParams.status"
              @change="handleSearch"
            >
              <el-radio-button value="">
                全部
              </el-radio-button>
              <el-radio-button value="未开始">
                未开始
              </el-radio-button>
              <el-radio-button value="进行中">
                进行中
              </el-radio-button>
              <el-radio-button value="已完成">
                已完成
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="时间筛选">
            <el-radio-group
              v-model="queryParams.timeFilter"
              @change="handleSearch"
            >
              <el-radio-button value="">
                全部
              </el-radio-button>
              <el-radio-button value="overdue">
                逾期
              </el-radio-button>
              <el-radio-button value="upcoming">
                3天内到期
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input
              v-model="queryParams.keyword"
              placeholder="任务名称/项目名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
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
              @click="handleReset"
            >
              重置
            </BaseButton>
          </el-form-item>
        </el-form>
      </template>

      <el-table-column
        label="任务名称"
        min-width="220"
      >
        <template #default="{ row }">
          <el-icon
            v-if="row.isMilestone === 1"
            style="color: #faad14; margin-right: 4px"
            title="里程碑"
          >
            <Flag />
          </el-icon>
          <el-icon
            v-else-if="row.isKeyTask === 1"
            style="color: #ff4d4f; margin-right: 4px"
            title="关键节点"
          >
            <Key />
          </el-icon>
          <el-link
            type="primary"
            @click="goTaskDetail(row)"
          >
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        prop="projectName"
        label="所属项目"
        min-width="180"
      />
      <el-table-column
        prop="status"
        label="状态"
        width="100"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            type="task"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="进度"
        width="160"
      >
        <template #default="{ row }">
          <el-progress
            :percentage="row.progress || 0"
            :color="getProgressColor(row.progress || 0)"
            :stroke-width="8"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="endDate"
        label="截止日期"
        width="140"
      >
        <template #default="{ row }">
          <span :style="{ color: isOverdue(row) ? '#ff4d4f' : '', fontWeight: isOverdue(row) ? 'bold' : 'normal' }">
            {{ row.endDate || '-' }}
            <el-tag
              v-if="isOverdue(row)"
              size="small"
              type="danger"
              effect="dark"
              style="margin-left: 4px"
            >逾期{{ overdueDays(row) }}天</el-tag>
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="120"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            icon-name="View"
            @click="goTaskDetail(row)"
          >
            查看进度
          </BaseButton>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState description="暂无任务" />
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
/**
 * 我的任务 - 独立页面
 * 复用 GET /dashboard/my-tasks 接口（按 assignee_id 过滤当前用户的任务）
 * 增强：状态筛选 + 时间筛选（逾期/临期）+ 关键字搜索 + 逾期标识
 *
 * P1-2.2：使用 useCrudList 组合式函数封装列表数据状态与 fetch/search/reset 逻辑，
 *        使用 CrudTable 通用布局组件封装 el-card + toolbar + el-table + 分页的模板与 CSS。
 *        混合筛选模式：status 为服务端筛选（发送后端），keyword 与 timeFilter 为客户端筛选
 *        （filteredTasks computed 对当前页数据二次过滤），fetchFn 包装层仅传递 status。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api/dashboard'
import { getProgressColor } from '@/utils/format'
import dayjs from 'dayjs'
// P1-2.2：通用列表布局组件 + 通用 CRUD 组合式函数
import CrudTable from '@/components/CrudTable.vue'
import { useCrudList } from '@/composables/useCrudList'

const router = useRouter()

/**
 * P1-2.2：列表数据状态与 fetch/search/reset 由 useCrudList 统一管理。
 * fetchFn 包装层仅向后端传递 status（keyword/timeFilter 为客户端筛选，不发送后端）。
 */
const fetchTasks = (params) => dashboardApi.getMyTasks({
  pageNum: params.pageNum,
  pageSize: params.pageSize,
  status: params.status || undefined
})
const { list, total, loading, queryParams, fetchList, handleSearch, handleReset } = useCrudList(
  fetchTasks,
  { status: '', timeFilter: '', keyword: '' }
)

/** 汇总统计（前端基于已加载页计算，快速预览）
 *  口径统一：所有指标均基于当前页 list 计算，避免 total 跨页而其余当前页导致的数字不一致。
 *  total 使用分页器 total（跨页总数），并在 tooltip 中说明；
 *  inProgress/pending/completed/overdue 基于当前页 list（仅当前筛选条件下的可见数据）。
 */
const summary = computed(() => {
  const all = list.value
  return {
    total: total.value, // 后端返回的总任务数（跨页统计，用于"全部任务"卡片）
    inProgress: all.filter(t => t.status === '进行中').length, // 当前页进行中
    pending: all.filter(t => t.status === '未开始').length,    // 当前页待开始
    completed: all.filter(t => t.status === '已完成').length, // 当前页已完成
    overdue: all.filter(t => isOverdue(t)).length             // 当前页逾期
  }
})

/** 前端二次过滤：关键字 + 时间筛选（逾期/临期）
 *  后端接口已按 assignee_id + status 过滤，前端补充关键字与时间维度（仅对当前页数据生效）
 */
const filteredTasks = computed(() => {
  let result = list.value

  // 关键字过滤
  if (queryParams.keyword) {
    const kw = queryParams.keyword.toLowerCase()
    result = result.filter(t =>
      (t.name || '').toLowerCase().includes(kw) ||
      (t.projectName || '').toLowerCase().includes(kw)
    )
  }

  // 时间筛选
  if (queryParams.timeFilter === 'overdue') {
    result = result.filter(t => isOverdue(t))
  } else if (queryParams.timeFilter === 'upcoming') {
    result = result.filter(t => isUpcoming(t))
  }

  return result
})

/** 判断任务是否逾期：未完成且截止日期早于今天 */
function isOverdue(task) {
  if (task.status === '已完成') return false
  if (!task.endDate) return false
  return dayjs(task.endDate).isBefore(dayjs().startOf('day'))
}

/** 判断任务是否即将到期：3 天内到期且未完成 */
function isUpcoming(task) {
  if (task.status === '已完成') return false
  if (!task.endDate) return false
  const end = dayjs(task.endDate)
  const today = dayjs().startOf('day')
  const deadline = today.add(3, 'day')
  return !end.isBefore(today) && !end.isAfter(deadline)
}

/** 计算逾期天数 */
function overdueDays(task) {
  if (!task.endDate) return 0
  return dayjs().diff(dayjs(task.endDate), 'day')
}

/** 状态筛选：点击卡片快速过滤（重置 timeFilter，服务端按 status 拉取） */
function filterByStatus(status) {
  queryParams.status = status
  queryParams.timeFilter = ''
  handleSearch()
}

/** 仅看逾期（客户端筛选：后端 getMyTasks 不支持时间维度参数，filteredTasks 对当前页二次过滤）
 *  P2 修复：与 filterByStatus 保持一致——重置到第 1 页并重新拉取，
 *  避免停留在第 N 页时仅看到该页的逾期任务且页码不重置，行为与其他筛选卡片不一致
 */
function filterOverdue() {
  queryParams.status = ''
  queryParams.timeFilter = 'overdue'
  handleSearch()
}

/** 跳转任务所属项目的进度页 */
function goTaskDetail(row) {
  router.push(`/progress/${row.projectId}`)
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: all 0.2s;
}
.stat-card.clickable {
  cursor: pointer;
}
.stat-card.clickable:hover {
  /* 光晕 Token（Glow+Grain 铺开）：品牌蓝辉光替代中性灰阴影 */
  box-shadow: var(--glow-primary);
  transform: translateY(-2px);
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-right: 16px;
  flex-shrink: 0;
}
.stat-body {
  flex: 1;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}
</style>
