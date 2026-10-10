<template>
  <div class="page-container">
    <!-- P1-2.1：欢迎横幅抽取为独立组件，父组件按角色计算 stats 数组传入，子组件不感知角色 -->
    <WelcomeBanner
      :nickname="userStore.nickname || userStore.username"
      :subtitle="welcomeSubtitle"
      :stats="welcomeStats"
    />

    <!-- 数据概览卡片：role-aware（StatCard 统一软色调图标芯片 + 可点击箭头暗示） -->
    <el-row
      class="stats-row"
      :gutter="20"
      style="margin-bottom: 20px"
    >
      <!-- 管理员卡片 -->
      <template v-if="userRole === 'admin'">
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Folder"
            tone="primary"
            :value="projectStore.activeProjects.length"
            label="进行中项目"
            role="region"
            aria-label="进行中项目"
            @click="router.push('/projects')"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Bell"
            tone="warning"
            :value="summary.pendingReviewCount"
            label="待审核进度"
            role="region"
            aria-label="待审核进度"
            @click="router.push('/review/pending')"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Document"
            tone="success"
            :value="documentStore.documents.length"
            label="文档总数"
            role="region"
            aria-label="文档总数"
            @click="router.push('/documents')"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Money"
            tone="danger"
            :value="'¥' + formatAmount(projectStore.totalContractAmount, true)"
            label="合同总额"
            role="region"
            aria-label="合同总额"
          />
        </el-col>
      </template>

      <!-- 项目经理卡片 -->
      <template v-else-if="userRole === 'pm'">
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Bell"
            tone="warning"
            :value="summary.pendingReviewCount"
            label="待我审核"
            sub="进度填报"
            role="region"
            aria-label="待我审核"
            @click="router.push('/review/pending')"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Folder"
            tone="primary"
            :value="summary.managedProjectCount"
            label="管理项目"
            role="region"
            aria-label="管理项目"
            @click="router.push('/projects')"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Warning"
            tone="danger"
            :value="remindList.length"
            label="关键节点"
            sub="待跟进"
            role="region"
            aria-label="关键节点"
            @click="router.push('/progress')"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="ChatDotRound"
            tone="purple"
            :value="summary.unreadNotificationCount"
            label="未读通知"
            role="region"
            aria-label="未读通知"
          />
        </el-col>
      </template>

      <!-- 工程师卡片 -->
      <template v-else-if="userRole === 'engineer'">
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="DataLine"
            tone="primary"
            :value="summary.myTaskCount"
            label="我的任务"
            role="region"
            aria-label="我的任务"
            @click="goMyTasksTab"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Clock"
            tone="warning"
            :value="summary.myPendingTaskCount"
            label="待办任务"
            role="region"
            aria-label="待办任务"
            @click="goMyTasksTab"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Warning"
            tone="danger"
            :value="summary.myOverdueTaskCount"
            label="逾期任务"
            role="region"
            aria-label="逾期任务"
            @click="goMyTasksTab"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="EditPen"
            tone="purple"
            :value="summary.myPendingSubmissionCount"
            label="待审核填报"
            :sub="`已通过 ${summary.myApprovedSubmissionCount} · 已驳回 ${summary.myRejectedSubmissionCount}`"
            role="region"
            aria-label="待审核填报"
            @click="goMySubmissionsTab"
          />
        </el-col>
      </template>

      <!-- 普通员工卡片 -->
      <template v-else>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="DataLine"
            tone="primary"
            :value="summary.myTaskCount"
            label="我的任务"
            role="region"
            aria-label="我的任务"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="ChatDotRound"
            tone="purple"
            :value="summary.unreadNotificationCount"
            label="未读通知"
            role="region"
            aria-label="未读通知"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Document"
            tone="success"
            :value="documentStore.documents.length"
            label="文档总数"
            role="region"
            aria-label="文档总数"
            @click="router.push('/documents')"
          />
        </el-col>
        <el-col
          :xs="24"
          :sm="12"
          :md="6"
        >
          <StatCard
            icon="Folder"
            tone="warning"
            :value="projectStore.activeProjects.length"
            label="进行中项目"
            role="region"
            aria-label="进行中项目"
            @click="router.push('/projects')"
          />
        </el-col>
      </template>
    </el-row>

    <!-- 角色差异化主内容区 -->
    <el-row :gutter="20">
      <!-- 左侧主区域 -->
      <el-col
        :xs="24"
        :lg="16"
      >
        <!-- Admin: 项目进度概览图表 + 文档分类图表 -->
        <template v-if="userRole === 'admin'">
          <el-card style="margin-bottom: 20px">
            <template #header>
              <div class="card-header">
                <div class="card-title">
                  <span class="title-chip title-chip--primary"><el-icon><DataAnalysis /></el-icon></span>
                  <span>项目进度概览</span>
                </div>
                <el-radio-group
                  v-model="chartType"
                  size="small"
                >
                  <el-radio-button value="bar">
                    柱状图
                  </el-radio-button>
                  <el-radio-button value="line">
                    折线图
                  </el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <el-empty
              v-if="!hasProgressChartData"
              description="暂无进行中项目"
              :image-size="80"
            />
            <div
              v-else
              ref="progressChartRef"
              role="img"
              aria-label="项目进度趋势图"
              style="height: 320px"
            />
          </el-card>
          <el-card>
            <template #header>
              <div class="card-header">
                <div class="card-title">
                  <span class="title-chip title-chip--success"><el-icon><PieChart /></el-icon></span>
                  <span>文档分类统计</span>
                </div>
                <BaseButton
                  variant="link"
                  @click="router.push('/documents')"
                >
                  查看详情
                </BaseButton>
              </div>
            </template>
            <el-empty
              v-if="!hasDocChartData"
              description="暂无文档数据"
              :image-size="80"
            />
            <div
              v-else
              ref="docChartRef"
              role="img"
              aria-label="文档类型分布图"
              style="height: 280px"
            />
          </el-card>
        </template>

        <!-- PM: 项目进度概览图表 + 待审核进度日志 -->
        <template v-else-if="userRole === 'pm'">
          <el-card style="margin-bottom: 20px">
            <template #header>
              <div class="card-header">
                <div class="card-title">
                  <span class="title-chip title-chip--primary"><el-icon><DataAnalysis /></el-icon></span>
                  <span>我管理的项目进度</span>
                </div>
                <el-radio-group
                  v-model="chartType"
                  size="small"
                >
                  <el-radio-button value="bar">
                    柱状图
                  </el-radio-button>
                  <el-radio-button value="line">
                    折线图
                  </el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <el-empty
              v-if="!hasProgressChartData"
              description="暂无进行中项目"
              :image-size="80"
            />
            <div
              v-else
              ref="progressChartRef"
              role="img"
              aria-label="项目进度趋势图"
              style="height: 320px"
            />
          </el-card>
          <el-card>
            <template #header>
              <div class="card-header">
                <div class="card-title">
                  <span class="title-chip title-chip--warning"><el-icon><Bell /></el-icon></span>
                  <span>待我审核的进度填报</span>
                  <el-tag
                    v-if="pendingReviewList.length > 0"
                    size="small"
                    type="danger"
                    style="margin-left: 8px"
                  >
                    {{ pendingReviewList.length }}
                  </el-tag>
                </div>
                <BaseButton
                  variant="link"
                  @click="router.push('/review/pending')"
                >
                  查看全部
                </BaseButton>
              </div>
            </template>
            <el-empty
              v-if="pendingReviewList.length === 0"
              description="暂无待审核进度"
              :image-size="80"
            />
            <el-table
              v-else
              :data="pendingReviewList.slice(0, 10)"
              style="width: 100%"
              stripe
            >
              <el-table-column
                prop="taskName"
                label="任务名称"
                min-width="160"
                show-overflow-tooltip
              />
              <el-table-column
                prop="projectName"
                label="所属项目"
                min-width="140"
                show-overflow-tooltip
              />
              <el-table-column
                prop="reporterName"
                label="填报人"
                width="100"
              />
              <el-table-column
                prop="progress"
                label="填报进度"
                width="100"
              >
                <template #default="{ row }">
                  <el-progress
                    :percentage="row.progress"
                    :stroke-width="6"
                    :show-text="false"
                  />
                </template>
              </el-table-column>
              <el-table-column
                prop="reportTime"
                label="填报时间"
                width="160"
              >
                <template #default="{ row }">
                  {{ formatTime(row.reportTime) }}
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="100"
                fixed="right"
              >
                <template #default>
                  <BaseButton
                    variant="link"
                    size="small"
                    @click="router.push('/review/pending')"
                  >
                    审核
                  </BaseButton>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>

        <!-- Engineer/Employee: 我的任务 + 我的填报 -->
        <template v-else>
          <el-tabs
            v-model="activeTab"
            class="dashboard-tabs"
          >
            <el-tab-pane
              label="我的任务"
              name="myTasks"
            >
              <el-card shadow="never">
                <template #header>
                  <div class="card-header">
                    <div class="card-title">
                      <span class="title-chip title-chip--primary"><el-icon><DataLine /></el-icon></span>
                      <span>我的任务</span>
                      <el-tag
                        size="small"
                        type="primary"
                        style="margin-left: 8px"
                      >
                        {{ myTasksTotal }}
                      </el-tag>
                    </div>
                    <el-radio-group
                      v-model="myTaskStatus"
                      size="small"
                      @change="fetchMyTasks"
                    >
                      <el-radio-button value="">
                        全部
                      </el-radio-button>
                      <el-radio-button value="待开始">
                        待开始
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
                <el-empty
                  v-if="myTasks.length === 0"
                  description="暂无任务"
                  :image-size="80"
                />
                <el-table
                  v-else
                  :data="myTasks"
                  style="width: 100%"
                  stripe
                >
                  <el-table-column
                    prop="name"
                    label="任务名称"
                    min-width="160"
                    show-overflow-tooltip
                  />
                  <el-table-column
                    prop="projectName"
                    label="所属项目"
                    min-width="140"
                    show-overflow-tooltip
                  />
                  <el-table-column
                    prop="status"
                    label="状态"
                    width="90"
                  >
                    <template #default="{ row }">
                      <el-tag
                        size="small"
                        :type="getStatusTagType(row.status)"
                      >
                        {{ row.status }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="progress"
                    label="进度"
                    width="120"
                  >
                    <template #default="{ row }">
                      <el-progress
                        :percentage="row.progress"
                        :stroke-width="6"
                        :color="getProgressColor(row.progress)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="endDate"
                    label="截止日期"
                    width="110"
                  >
                    <template #default="{ row }">
                      <span :style="{ color: isOverdue(row) ? '#f5222d' : '#606266' }">{{ row.endDate }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    label="操作"
                    width="100"
                    fixed="right"
                  >
                    <template #default="{ row }">
                      <BaseButton
                        variant="link"
                        size="small"
                        @click="goTaskProgress(row.projectId)"
                      >
                        查看
                      </BaseButton>
                    </template>
                  </el-table-column>
                </el-table>
                <div
                  v-if="myTasksTotal > myTasksQuery.pageSize"
                  class="pagination-wrap"
                >
                  <el-pagination
                    v-model:current-page="myTasksQuery.pageNum"
                    :page-size="myTasksQuery.pageSize"
                    :total="myTasksTotal"
                    layout="prev, pager, next"
                    @current-change="fetchMyTasks"
                  />
                </div>
              </el-card>
            </el-tab-pane>

            <el-tab-pane
              v-if="userRole === 'engineer'"
              label="我的填报"
              name="mySubmissions"
            >
              <el-card shadow="never">
                <template #header>
                  <div class="card-header">
                    <div class="card-title">
                      <span class="title-chip title-chip--purple"><el-icon><EditPen /></el-icon></span>
                      <span>我的进度填报</span>
                      <el-tag
                        size="small"
                        type="primary"
                        style="margin-left: 8px"
                      >
                        {{ mySubmissionsTotal }}
                      </el-tag>
                    </div>
                    <el-radio-group
                      v-model="mySubmissionStatus"
                      size="small"
                      @change="fetchMySubmissions"
                    >
                      <el-radio-button value="">
                        全部
                      </el-radio-button>
                      <el-radio-button value="pending">
                        待审核
                      </el-radio-button>
                      <el-radio-button value="approved">
                        已通过
                      </el-radio-button>
                      <el-radio-button value="rejected">
                        已驳回
                      </el-radio-button>
                    </el-radio-group>
                  </div>
                </template>
                <el-empty
                  v-if="mySubmissions.length === 0"
                  description="暂无填报记录"
                  :image-size="80"
                />
                <el-table
                  v-else
                  :data="mySubmissions"
                  style="width: 100%"
                  stripe
                >
                  <el-table-column
                    prop="taskName"
                    label="任务名称"
                    min-width="160"
                    show-overflow-tooltip
                  />
                  <el-table-column
                    prop="projectName"
                    label="所属项目"
                    min-width="140"
                    show-overflow-tooltip
                  />
                  <el-table-column
                    prop="progress"
                    label="填报进度"
                    width="100"
                  >
                    <template #default="{ row }">
                      {{ row.progress }}%
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="reviewStatus"
                    label="审核状态"
                    width="100"
                  >
                    <template #default="{ row }">
                      <el-tag
                        size="small"
                        :type="getReviewStatusTagType(row.reviewStatus)"
                      >
                        {{ getReviewStatusText(row.reviewStatus) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="reviewerName"
                    label="审核人"
                    width="100"
                  />
                  <el-table-column
                    prop="reportTime"
                    label="填报时间"
                    width="160"
                  >
                    <template #default="{ row }">
                      {{ formatTime(row.reportTime) }}
                    </template>
                  </el-table-column>
                </el-table>
                <div
                  v-if="mySubmissionsTotal > mySubmissionsQuery.pageSize"
                  class="pagination-wrap"
                >
                  <el-pagination
                    v-model:current-page="mySubmissionsQuery.pageNum"
                    :page-size="mySubmissionsQuery.pageSize"
                    :total="mySubmissionsTotal"
                    layout="prev, pager, next"
                    @current-change="fetchMySubmissions"
                  />
                </div>
              </el-card>
            </el-tab-pane>
          </el-tabs>
        </template>
      </el-col>

      <!-- P1-2.1：右侧栏抽取为独立组件，emit 事件交由父组件路由跳转 -->
      <el-col
        :xs="24"
        :lg="8"
      >
        <DashboardSidebar
          :remind-list="remindList"
          :recent-projects="recentProjects"
          @go-progress="goProgress"
          @go-detail="goDetail"
          @view-all-projects="() => router.push('/projects')"
        />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
/**
 * 首页仪表盘（角色聚合版）
 *
 * 业务用途：登录后的数据总览页，按用户角色差异化展示工作台数据：
 *   - admin（管理员）：进行中项目 + 待审核进度 + 文档总数 + 合同总额 + 项目进度图表 + 文档分类图表
 *   - project_manager（项目经理）：待我审核进度 + 管理项目 + 关键节点 + 未读通知 + 项目进度图表 + 待审核列表
 *   - engineer（工程师）：我的任务 + 待办任务 + 逾期任务 + 待审核填报 + 我的任务表 + 我的填报历史
 *   - 普通员工：我的任务 + 未读通知 + 文档总数 + 进行中项目 + 我的任务表
 *
 * 数据来源：
 *   - dashboardApi（汇总统计/我的任务/我的填报，后端按当前用户过滤）
 *   - projectStore（项目列表/合同总额/平均进度等通用统计）
 *   - documentStore（文档分类统计）
 *   - progressStore（关键节点提醒，跨项目）
 *   - userStore（当前用户角色判定）
 *   - 图表基于 echarts 渲染（仅 admin/PM 显示）
 *
 * P1-2.1：欢迎横幅与右侧栏已抽取到 ./components/ 下的 WelcomeBanner / DashboardSidebar 组件，
 *        本组件仅保留 stat 卡片、主内容区、图表逻辑、数据拉取与角色判定。
 */
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useDocumentStore } from '@/stores/document'
import { useProgressStore } from '@/stores/progress'
import { useUserStore } from '@/stores/user'
import { dashboardApi } from '@/api/dashboard'
import { taskApi } from '@/api/task'
// echarts 全量导入
// 说明：曾尝试按需导入（echarts/core + 各 Chart/Component），但项目使用了 echarts.graphic.LinearGradient，
// 该 API 在 echarts/core 命名空间下未导出，导致渲染时抛错触发 ErrorBoundary。
// 为保证功能稳定，回退为全量导入；后续如需优化打包体积，可将 LinearGradient 改为对象字面量声明后再切按需导入。
import * as echarts from 'echarts'
import { formatAmount, getProgressColor, formatDateTime } from '@/utils/format'
import { CHART_COLORS, SERIES_COLORS, barStops } from '@/utils/chartTheme'
import { getStatusTagType, getReviewStatusText, getReviewStatusTagType } from '@/utils/statusMap'
import dayjs from 'dayjs'
// P1-2.1：抽取的子组件（位于 ./dashboard/components/ 子目录）
// StatCard 已提升为全局组件（src/components/StatCard.vue），无需局部注册
import WelcomeBanner from './dashboard/components/WelcomeBanner.vue'
import DashboardSidebar from './dashboard/components/DashboardSidebar.vue'

const router = useRouter()
const projectStore = useProjectStore()
const documentStore = useDocumentStore()
const progressStore = useProgressStore()
const userStore = useUserStore()

// ===== 角色判定 =====
// roles[0] 决定主角色，admin > PM > engineer > employee
const userRole = computed(() => {
  const roles = userStore.roles || []
  if (roles.includes('admin')) return 'admin'
  if (roles.includes('project_manager')) return 'pm'
  if (roles.includes('engineer')) return 'engineer'
  return 'employee'
})

// 角色对应的欢迎语
const welcomeSubtitle = computed(() => {
  const today = dayjs().format('YYYY年MM月DD日 dddd')
  const map = {
    admin: `今天是 ${today}，系统管理员视角，全局数据概览`,
    pm: `今天是 ${today}，项目经理视角，关注待审核与项目进度`,
    engineer: `今天是 ${today}，工程师视角，关注我的任务与填报`,
    employee: `今天是 ${today}，祝您工作愉快！`
  }
  return map[userRole.value] || `今天是 ${today}`
})

/**
 * P1-2.1：欢迎横幅统计数据（按角色差异化）
 * 返回数组形式，每项 { value, label, color? }；颜色字段可选，用于突出待办/逾期类指标。
 */
const welcomeStats = computed(() => {
  if (userRole.value === 'admin') {
    return [
      { value: projectStore.activeProjects.length, label: '进行中项目' },
      { value: remindList.value.length, label: '关键节点' },
      { value: `${projectStore.avgProgress}%`, label: '平均进度' }
    ]
  }
  if (userRole.value === 'pm') {
    return [
      { value: summary.managedProjectCount, label: '管理项目' },
      { value: summary.pendingReviewCount, label: '待我审核', color: '#ffd666' },
      { value: summary.unreadNotificationCount, label: '未读通知' }
    ]
  }
  if (userRole.value === 'engineer') {
    return [
      { value: summary.myTaskCount, label: '我的任务' },
      { value: summary.myPendingTaskCount, label: '待办任务', color: '#ffd666' },
      { value: summary.myOverdueTaskCount, label: '逾期任务', color: '#ff7875' }
    ]
  }
  return [
    { value: summary.myTaskCount, label: '我的任务' },
    { value: summary.unreadNotificationCount, label: '未读通知' }
  ]
})

// ===== Dashboard Summary =====
const summary = reactive({
  role: '',
  myTaskCount: 0,
  myPendingTaskCount: 0,
  myOverdueTaskCount: 0,
  myUpcomingTaskCount: 0,
  mySubmissionCount: 0,
  myPendingSubmissionCount: 0,
  myApprovedSubmissionCount: 0,
  myRejectedSubmissionCount: 0,
  pendingReviewCount: 0,
  managedProjectCount: 0,
  unreadNotificationCount: 0
})

async function fetchSummary() {
  try {
    const res = await dashboardApi.getSummary()
    Object.assign(summary, res.data || {})
  } catch (e) {
    console.error('[Dashboard] 拉取汇总统计失败:', e?.message)
  }
}

// ===== 我的任务（engineer/employee） =====
const activeTab = ref('myTasks')
const myTaskStatus = ref('')
const myTasks = ref([])
const myTasksTotal = ref(0)
const myTasksQuery = reactive({ pageNum: 1, pageSize: 10 })

async function fetchMyTasks() {
  try {
    const res = await dashboardApi.getMyTasks({
      pageNum: myTasksQuery.pageNum,
      pageSize: myTasksQuery.pageSize,
      status: myTaskStatus.value
    })
    myTasks.value = res.data.records || []
    myTasksTotal.value = res.data.total || 0
  } catch (e) {
    console.error('[Dashboard] 拉取我的任务失败:', e?.message)
  }
}

function goMyTasksTab() {
  activeTab.value = 'myTasks'
}

// ===== 我的填报（engineer） =====
const mySubmissionStatus = ref('')
const mySubmissions = ref([])
const mySubmissionsTotal = ref(0)
const mySubmissionsQuery = reactive({ pageNum: 1, pageSize: 10 })

async function fetchMySubmissions() {
  try {
    const res = await dashboardApi.getMySubmissions({
      pageNum: mySubmissionsQuery.pageNum,
      pageSize: mySubmissionsQuery.pageSize,
      reviewStatus: mySubmissionStatus.value
    })
    mySubmissions.value = res.data.records || []
    mySubmissionsTotal.value = res.data.total || 0
  } catch (e) {
    console.error('[Dashboard] 拉取我的填报失败:', e?.message)
  }
}

function goMySubmissionsTab() {
  activeTab.value = 'mySubmissions'
}

// ===== 待审核进度（PM） =====
const pendingReviewList = ref([])

async function fetchPendingReviews() {
  // 复用进度审核列表接口（PM 权限）
  try {
    const res = await taskApi.listPendingProgress({})
    pendingReviewList.value = res.data || []
  } catch (e) {
    console.error('[Dashboard] 拉取待审核进度失败:', e?.message)
  }
}

// ===== 关键节点提醒 + 最近项目（所有角色） =====
const remindList = computed(() => progressStore.getAllUpcomingKeyTasks())
const recentProjects = computed(() => projectStore.projects.filter(p => !p.archived).slice(0, 5))

// ===== 图表空数据兜底（无数据时模板切换为 el-empty，避免渲染空白画布） =====
const hasProgressChartData = computed(() => projectStore.projects.filter(p => !p.archived).length > 0)
const hasDocChartData = computed(() => documentStore.documents.length > 0)

/**
 * 任务是否已逾期（未完成且截止日期早于今天）
 * 注意：右侧栏 DashboardSidebar 内部也有同名实现，此处保留供主内容区任务表格的截止日期颜色判断使用。
 * @param {Object} task 任务对象
 * @returns {boolean}
 */
function isOverdue(task) {
  return task.status !== '已完成' && dayjs(task.endDate).isBefore(dayjs())
}

// ===== 通用工具 =====
// P3 重构：改用 utils/format 统一实现（共享 @shared/format），别名保持模板引用不变
const formatTime = formatDateTime

// P3 重构：状态 tag/审核状态映射统一改用 utils/statusMap 共享实现，删除本地重复定义

function goDetail(id) {
  router.push(`/projects/${id}`)
}

function goProgress(id) {
  router.push(`/progress/${id}`)
}

function goTaskProgress(projectId) {
  router.push(`/progress/${projectId}`)
}

// ===== 图表（仅 admin/PM） =====
const progressChartRef = ref(null)
const docChartRef = ref(null)
const chartType = ref('bar')
let progressChart = null
let docChart = null

function initProgressChart() {
  if (!progressChartRef.value) return
  if (progressChart) progressChart.dispose()
  progressChart = echarts.init(progressChartRef.value)
  const projects = projectStore.projects.filter(p => !p.archived).slice(0, 6)
  // PM 只看自己管理的项目
  const targetProjects = userRole.value === 'pm'
    ? projects.filter(p => String(p.managerId) === String(userStore.userInfo?.id))
    : projects
  const finalProjects = targetProjects.length > 0 ? targetProjects : projects
  const xAxisData = finalProjects.map(p => p.name.length > 8 ? p.name.substring(0, 8) + '...' : p.name)

  progressChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      textStyle: { color: '#1f2937' }
    },
    legend: { data: ['计划进度', '实际进度'], top: 0, textStyle: { color: '#6b7280' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: xAxisData,
      axisLabel: { rotate: 20, fontSize: 11, color: '#6b7280' },
      axisLine: { lineStyle: { color: '#e5e7eb' } }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f3f4f6' } }
    },
    series: [
      {
        name: '计划进度',
        type: chartType.value,
        data: finalProjects.map(p => {
          const total = dayjs(p.endDate).diff(dayjs(p.startDate), 'day')
          const passed = dayjs().diff(dayjs(p.startDate), 'day')
          // 防御除零：起止日期相同（total=0）时，若已开始则视为 100%，否则 0%
          const percent = total > 0 ? passed / total * 100 : (passed >= 0 ? 100 : 0)
          return Math.min(100, Math.max(0, Math.round(percent)))
        }),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#d1d5db' },
            { offset: 1, color: '#e5e7eb' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        smooth: true
      },
      {
        name: '实际进度',
        type: chartType.value,
        data: finalProjects.map(p => p.progress),
        itemStyle: {
          // 实际进度 = 核心数据，用主色低饱和渐变（顶部亮 → 底部深）
          color: (() => {
            const s = barStops(CHART_COLORS.primary)
            return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: s.color1 },
              { offset: 1, color: s.color2 }
            ])
          })(),
          borderRadius: [4, 4, 0, 0]
        },
        smooth: true
      }
    ]
  })
}

function initDocChart() {
  if (!docChartRef.value) return
  if (docChart) docChart.dispose()
  docChart = echarts.init(docChartRef.value)
  const categoryData = {}
  documentStore.documents.forEach(d => {
    categoryData[d.category] = (categoryData[d.category] || 0) + 1
  })
  // 低饱和序列色板（主色/辅助/点缀 + 明度变体，色盲友好——见 utils/chartTheme.js）
  const colors = SERIES_COLORS
  docChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', top: 'center', textStyle: { color: '#6b7280' } },
    color: colors,
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['60%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold', color: '#1f2937' },
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.2)' }
        },
        data: Object.entries(categoryData).map(([name, value]) => ({ name, value }))
      }
    ]
  })
}

function handleResize() {
  progressChart?.resize()
  docChart?.resize()
}

watch(chartType, () => {
  initProgressChart()
})

// ===== 生命周期 =====
onMounted(async () => {
  // 并发拉取首页所需数据
  const tasks = [
    fetchSummary(),
    projectStore.fetchList({ pageSize: 100 }),
    progressStore.fetchAllUpcomingKeyTasks()
  ]

  // 工程师/员工：拉取我的任务
  if (userRole.value === 'engineer' || userRole.value === 'employee') {
    tasks.push(fetchMyTasks())
  }
  // 工程师：拉取我的填报
  if (userRole.value === 'engineer') {
    tasks.push(fetchMySubmissions())
  }
  // PM：拉取待审核进度
  if (userRole.value === 'pm') {
    tasks.push(fetchPendingReviews())
  }
  // admin/PM：拉取文档（图表需要）
  if (userRole.value === 'admin' || userRole.value === 'pm') {
    tasks.push(documentStore.fetchList({ pageSize: 100 }))
  } else {
    // 其他角色用于"文档总数"卡片
    tasks.push(documentStore.fetchList({ pageSize: 100 }).catch(() => {}))
  }

  try {
    await Promise.all(tasks)
  } catch (error) {
    console.error('[Dashboard] 加载首页数据失败:', error)
  }

  // admin/PM 数据就绪后初始化图表
  if (userRole.value === 'admin' || userRole.value === 'pm') {
    nextTick(() => {
      initProgressChart()
      if (userRole.value === 'admin') initDocChart()
    })
    window.addEventListener('resize', handleResize)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  progressChart?.dispose()
  docChart?.dispose()
  progressChart = null
  docChart = null
})
</script>

<style scoped>
/* 统计卡入场编排：按列位错峰（StatCard 自带 fadeUpBlur backwards 动画），
   横幅 → 卡1..4 依次落定，营造"数据点亮"的节奏感 */
.stats-row :deep(.el-col:nth-child(1) .stat-card) { animation-delay: 60ms; }
.stats-row :deep(.el-col:nth-child(2) .stat-card) { animation-delay: 140ms; }
.stats-row :deep(.el-col:nth-child(3) .stat-card) { animation-delay: 220ms; }
.stats-row :deep(.el-col:nth-child(4) .stat-card) { animation-delay: 300ms; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

/* title-chip 样式已全局化到 global.css（各列表页统一复用） */

/* 统计卡片入场错峰：四列依次延迟（StatCard 内动画，此处只调 delay） */
.stats-row .el-col:nth-child(1) .stat-card { animation-delay: 0ms; }
.stats-row .el-col:nth-child(2) .stat-card { animation-delay: 60ms; }
.stats-row .el-col:nth-child(3) .stat-card { animation-delay: 120ms; }
.stats-row .el-col:nth-child(4) .stat-card { animation-delay: 180ms; }

.dashboard-tabs {
  margin-bottom: 0;
}

.dashboard-tabs :deep(.el-tabs__header) {
  margin: 0 0 12px 0;
  padding: 0 4px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
