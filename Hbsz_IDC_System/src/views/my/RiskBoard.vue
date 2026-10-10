<template>
  <div
    v-loading="loading"
    class="page-container"
  >
    <div class="page-header">
      <div>
        <div class="page-title">
          风险预警看板
        </div>
        <div class="page-subtitle">
          PM 视角的项目风险聚合：逾期任务、即将到期任务、待审核填报
        </div>
      </div>
      <div class="header-actions">
        <BaseButton
          icon-name="Refresh"
          @click="fetchData"
        >
          刷新
        </BaseButton>
      </div>
    </div>

    <!-- 风险等级横幅 -->
    <div
      v-if="board"
      class="risk-banner"
      :class="riskLevelClass"
    >
      <div class="banner-icon">
        <el-icon v-if="riskLevel === 'high'">
          <CircleClose />
        </el-icon>
        <el-icon v-else-if="riskLevel === 'medium'">
          <Warning />
        </el-icon>
        <el-icon v-else>
          <CircleCheck />
        </el-icon>
      </div>
      <div class="banner-body">
        <div class="banner-title">
          <!-- 红色呼吸灯预警（UI设计标准：异常数据红色呼吸灯，快速吸引注意力） -->
          <span
            v-if="riskLevel === 'high'"
            class="breath-dot"
            aria-hidden="true"
          />
          整体风险等级：{{ riskLevelText }}
        </div>
        <div class="banner-desc">
          {{ riskLevelDesc }}
        </div>
      </div>
    </div>

    <!-- 汇总统计卡片 -->
    <div
      v-if="board"
      class="stat-cards"
    >
      <div
        class="stat-card danger"
        :class="{ 'has-alert': board.overdueTaskCount > 0 }"
        @click="scrollToSection('overdue')"
      >
        <div
          class="stat-icon"
          style="background: #fff1f0; color: #ff4d4f"
        >
          <el-icon><CircleClose /></el-icon>
        </div>
        <div class="stat-body">
          <div
            class="stat-value"
            style="color: #ff4d4f"
          >
            {{ board.overdueTaskCount }}
          </div>
          <div class="stat-label">
            逾期任务
          </div>
        </div>
      </div>
      <div
        class="stat-card warning"
        @click="scrollToSection('upcoming')"
      >
        <div
          class="stat-icon"
          style="background: #fff7e6; color: #fa8c16"
        >
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-body">
          <div
            class="stat-value"
            style="color: #fa8c16"
          >
            {{ board.upcomingTaskCount }}
          </div>
          <div class="stat-label">
            3天内到期
          </div>
        </div>
      </div>
      <div
        class="stat-card"
        @click="goPendingReview"
      >
        <div
          class="stat-icon"
          style="background: #fffbe6; color: #faad14"
        >
          <el-icon><Bell /></el-icon>
        </div>
        <div class="stat-body">
          <div
            class="stat-value"
            style="color: #faad14"
          >
            {{ board.pendingReviewCount }}
          </div>
          <div class="stat-label">
            待审核填报
          </div>
        </div>
      </div>
      <div class="stat-card danger">
        <div
          class="stat-icon"
          style="background: #fff1f0; color: #f5222d"
        >
          <el-icon><Key /></el-icon>
        </div>
        <div class="stat-body">
          <div
            class="stat-value"
            style="color: #ff4d4f"
          >
            {{ board.overdueKeyTaskCount }}
          </div>
          <div class="stat-label">
            关键节点逾期
          </div>
        </div>
      </div>
      <div class="stat-card">
        <div
          class="stat-icon"
          style="background: #e6f7ff; color: #1890ff"
        >
          <el-icon><Folder /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">
            {{ board.totalProjects }}
          </div>
          <div class="stat-label">
            监控项目数
          </div>
        </div>
      </div>
    </div>

    <!-- 逾期任务列表 -->
    <div
      v-if="board && board.overdueTasks && board.overdueTasks.length > 0"
      id="overdue"
      class="card-box"
    >
      <div class="section-header">
        <span
          class="section-title"
          style="color: #ff4d4f"
        >逾期任务（{{ board.overdueTaskCount }}）</span>
        <span class="section-sub">点击任务可跳转到项目进度页处理</span>
      </div>
      <el-table
        :data="board.overdueTasks"
        stripe
        size="small"
      >
        <el-table-column
          label="任务名称"
          min-width="200"
        >
          <template #default="{ row }">
            <el-icon
              v-if="row.isKeyTask === 1"
              style="color: #ff4d4f; margin-right: 4px"
              title="关键节点"
            >
              <Key />
            </el-icon>
            <el-icon
              v-if="row.isMilestone === 1"
              style="color: #faad14; margin-right: 4px"
              title="里程碑"
            >
              <Flag />
            </el-icon>
            <el-link
              type="primary"
              @click="goTask(row)"
            >
              {{ row.taskName }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="projectName"
          label="所属项目"
          min-width="160"
        />
        <el-table-column
          prop="assigneeName"
          label="负责人"
          width="100"
        >
          <template #default="{ row }">
            {{ row.assigneeName || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="progress"
          label="进度"
          width="100"
        >
          <template #default="{ row }">
            {{ row.progress || 0 }}%
          </template>
        </el-table-column>
        <el-table-column
          prop="endDate"
          label="截止日期"
          width="120"
        />
        <el-table-column
          label="逾期天数"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              type="danger"
              effect="dark"
              size="small"
            >
              {{ row.overdueDays }}天
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 即将到期任务列表 -->
    <div
      v-if="board && board.upcomingTasks && board.upcomingTasks.length > 0"
      id="upcoming"
      class="card-box"
    >
      <div class="section-header">
        <span
          class="section-title"
          style="color: #fa8c16"
        >即将到期任务（{{ board.upcomingTaskCount }}）</span>
        <span class="section-sub">3 天内到期的未完成任务</span>
      </div>
      <el-table
        :data="board.upcomingTasks"
        stripe
        size="small"
      >
        <el-table-column
          label="任务名称"
          min-width="200"
        >
          <template #default="{ row }">
            <el-icon
              v-if="row.isKeyTask === 1"
              style="color: #ff4d4f; margin-right: 4px"
              title="关键节点"
            >
              <Key />
            </el-icon>
            <el-icon
              v-if="row.isMilestone === 1"
              style="color: #faad14; margin-right: 4px"
              title="里程碑"
            >
              <Flag />
            </el-icon>
            <el-link
              type="primary"
              @click="goTask(row)"
            >
              {{ row.taskName }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="projectName"
          label="所属项目"
          min-width="160"
        />
        <el-table-column
          prop="assigneeName"
          label="负责人"
          width="100"
        >
          <template #default="{ row }">
            {{ row.assigneeName || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="progress"
          label="进度"
          width="100"
        >
          <template #default="{ row }">
            {{ row.progress || 0 }}%
          </template>
        </el-table-column>
        <el-table-column
          prop="endDate"
          label="截止日期"
          width="120"
        />
        <el-table-column
          label="剩余天数"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              type="warning"
              effect="plain"
              size="small"
            >
              {{ remainingDays(row.endDate) }}天
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 各项目风险摘要 -->
    <div
      v-if="board && board.projectRisks && board.projectRisks.length > 0"
      class="card-box"
    >
      <div class="section-header">
        <span class="section-title">各项目风险摘要</span>
        <span class="section-sub">按风险等级排序，点击项目名可跳转</span>
      </div>
      <el-table
        :data="board.projectRisks"
        stripe
      >
        <el-table-column
          label="项目名称"
          min-width="200"
        >
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="goProject(row.projectId)"
            >
              {{ row.projectName }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="totalTasks"
          label="总任务"
          width="80"
          align="center"
        />
        <el-table-column
          label="风险等级"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="getRiskTagType(row.riskLevel)"
              effect="dark"
              size="small"
            >
              {{ getRiskLevelText(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="overdueTasks"
          label="逾期"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <span :style="{ color: row.overdueTasks > 0 ? '#ff4d4f' : '#909399', fontWeight: row.overdueTasks > 0 ? 'bold' : 'normal' }">
              {{ row.overdueTasks }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="overdueKeyTasks"
          label="关键逾期"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span :style="{ color: row.overdueKeyTasks > 0 ? '#ff4d4f' : '#909399', fontWeight: row.overdueKeyTasks > 0 ? 'bold' : 'normal' }">
              {{ row.overdueKeyTasks }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="upcomingTasks"
          label="临期"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <span :style="{ color: row.upcomingTasks > 0 ? '#fa8c16' : '#909399' }">{{ row.upcomingTasks }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="pendingReviews"
          label="待审核"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <span :style="{ color: row.pendingReviews > 0 ? '#faad14' : '#909399' }">{{ row.pendingReviews }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 空状态 -->
    <div
      v-if="board && !loading && board.overdueTaskCount === 0 && board.upcomingTaskCount === 0 && board.pendingReviewCount === 0"
      class="card-box"
    >
      <el-empty description="暂无风险预警，所有项目运行良好" />
    </div>
  </div>
</template>

<script setup>
/**
 * 风险预警看板 - PM 视角独立页面
 * 复用 GET /dashboard/risk-board 接口（聚合逾期/临期/待审核/各项目风险）
 * 仅 PM / admin 角色可访问（后端强制校验，路由层 meta.permission 控制）
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api/dashboard'
import { getRiskTagType, getRiskLevelText } from '@/utils/statusMap'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const board = ref(null)

const riskLevel = computed(() => board.value?.riskLevel || 'low')

const riskLevelClass = computed(() => ({
  'risk-high': riskLevel.value === 'high',
  'risk-medium': riskLevel.value === 'medium',
  'risk-low': riskLevel.value === 'low'
}))

const riskLevelText = computed(() => {
  const map = { high: '高风险', medium: '中等风险', low: '低风险' }
  return map[riskLevel.value] || '低风险'
})

const riskLevelDesc = computed(() => {
  const map = {
    high: '存在逾期关键节点或大量逾期任务，需要立即处理！',
    medium: '存在逾期任务或临期任务较多，建议尽快关注。',
    low: '当前风险可控，无逾期或临期任务。'
  }
  return map[riskLevel.value] || ''
})

/** 计算剩余天数（今天到截止日） */
function remainingDays(endDate) {
  if (!endDate) return 0
  return dayjs(endDate).diff(dayjs().startOf('day'), 'day')
}

// P3 重构：风险等级 tag/文本映射统一改用 utils/statusMap 共享实现，删除本地重复定义

/** 跳转到项目进度页 */
function goTask(row) {
  router.push(`/progress/${row.projectId}`)
}

/** 跳转到项目详情 */
function goProject(projectId) {
  router.push(`/projects/${projectId}`)
}

/** 跳转到待审核页 */
function goPendingReview() {
  router.push('/review/pending')
}

/** 平滑滚动到指定区块 */
function scrollToSection(id) {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth' })
}

/** 拉取风险看板数据 */
async function fetchData() {
  loading.value = true
  try {
    const res = await dashboardApi.getRiskBoard()
    board.value = res.data
  } catch (e) {
    console.error('加载风险看板失败:', e?.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.risk-banner {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  border-radius: 8px;
  margin-bottom: 20px;
}
.risk-banner.risk-high {
  background: linear-gradient(135deg, #fff1f0 0%, #ffe6e6 100%);
  border-left: 4px solid #ff4d4f;
}
.risk-banner.risk-medium {
  background: linear-gradient(135deg, #fff7e6 0%, #fff2e8 100%);
  border-left: 4px solid #fa8c16;
}
.risk-banner.risk-low {
  background: linear-gradient(135deg, #f6ffed 0%, #f0fff0 100%);
  border-left: 4px solid #52c41a;
}
.banner-icon {
  font-size: 36px;
  margin-right: 16px;
}
.risk-high .banner-icon { color: #ff4d4f; }
.risk-medium .banner-icon { color: #fa8c16; }
.risk-low .banner-icon { color: #52c41a; }
.banner-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
}
/* 红色呼吸灯：涟漪式光圈扩散（仅高风险横幅渲染），吸引对异常状态的注意 */
.breath-dot {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ff4d4f;
  box-shadow: 0 0 0 0 rgba(255, 77, 79, 0.45);
  animation: riskBreath 2s ease-in-out infinite;
}
@keyframes riskBreath {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 77, 79, 0.45); opacity: 0.85; }
  50% { box-shadow: 0 0 0 8px rgba(255, 77, 79, 0); opacity: 1; }
}
/* 逾期任务数 > 0 时，danger 卡片图标同步呼吸预警（与横幅呼吸灯同一语义） */
.stat-card.danger.has-alert .stat-icon {
  animation: riskBreath 2s ease-in-out infinite;
}
.banner-desc {
  font-size: 13px;
  color: #606266;
}
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
  cursor: pointer;
}
.stat-card:hover {
  /* 光晕 Token（Glow+Grain 铺开）：hover 泛出与语义 tone 一致的辉光 */
  box-shadow: var(--glow-primary);
  transform: translateY(-2px);
}

.stat-card.danger:hover {
  box-shadow: var(--glow-danger);
}

.stat-card.warning:hover {
  box-shadow: var(--glow-warning);
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
.stat-body { flex: 1; }
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
.section-header {
  display: flex;
  align-items: baseline;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.section-sub {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
/* 减弱动态：呼吸灯降级为静态红点（保留颜色语义，去除动效） */
@media (prefers-reduced-motion: reduce) {
  .breath-dot,
  .stat-card.danger.has-alert .stat-icon {
    animation: none;
  }
}
</style>
