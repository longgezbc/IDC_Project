<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          统计分析
        </div>
        <div class="page-subtitle">
          项目数据统计与分析报表
        </div>
      </div>
      <div>
        <el-radio-group
          v-model="dateRange"
          size="default"
        >
          <el-radio-button value="month">
            本月
          </el-radio-button>
          <el-radio-button value="quarter">
            本季度
          </el-radio-button>
          <el-radio-button value="year">
            本年
          </el-radio-button>
          <el-radio-button value="all">
            全部
          </el-radio-button>
        </el-radio-group>
        <BaseButton
          variant="primary"
          icon-name="Download"
          style="margin-left: 12px"
          @click="handleExport"
        >
          导出报表
        </BaseButton>
      </div>
    </div>

    <!-- 概览数据（来自后端 /statistics/overview 聚合查询，StatCard 全局组件统一视觉） -->
    <el-row
      :gutter="20"
      style="margin-bottom: 20px"
    >
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Folder"
          tone="primary"
          :value="overview.totalProjects"
          label="项目总数"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Finished"
          tone="success"
          :value="overview.completedProjects"
          label="已完成项目"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="TrendCharts"
          tone="warning"
          :value="overview.avgProgress + '%'"
          label="平均完成率"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="6"
      >
        <StatCard
          icon="Warning"
          tone="danger"
          :value="overdueCount"
          label="逾期项目数"
        />
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 项目状态分布 -->
      <el-col
        :xs="24"
        :lg="12"
      >
        <div
          class="card-box"
          style="margin-bottom: 20px"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--primary"><el-icon><PieChart /></el-icon></span>
              <span>项目状态分布</span>
            </div>
          </div>
          <div
            ref="statusChartRef"
            style="height: 300px"
          />
        </div>
      </el-col>

      <!-- 项目类型分布 -->
      <el-col
        :xs="24"
        :lg="12"
      >
        <div
          class="card-box"
          style="margin-bottom: 20px"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--success"><el-icon><DataAnalysis /></el-icon></span>
              <span>项目类型分布</span>
            </div>
          </div>
          <div
            ref="typeChartRef"
            style="height: 300px"
          />
        </div>
      </el-col>

      <!-- 月度项目趋势 -->
      <el-col
        :xs="24"
        :lg="16"
      >
        <div
          class="card-box"
          style="margin-bottom: 20px"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--warning"><el-icon><TrendCharts /></el-icon></span>
              <span>月度项目趋势</span>
            </div>
          </div>
          <div
            ref="trendChartRef"
            style="height: 320px"
          />
        </div>
      </el-col>

      <!-- 客户分布TOP5 -->
      <el-col
        :xs="24"
        :lg="8"
      >
        <div
          class="card-box"
          style="margin-bottom: 20px"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--danger"><el-icon><Histogram /></el-icon></span>
              <span>客户项目数 TOP5</span>
            </div>
          </div>
          <div
            ref="customerChartRef"
            style="height: 320px"
          />
        </div>
      </el-col>

      <!-- 项目经理绩效 -->
      <el-col
        :xs="24"
        :lg="12"
      >
        <div class="card-box">
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--purple"><el-icon><User /></el-icon></span>
              <span>项目经理绩效统计</span>
            </div>
          </div>
          <el-table
            :data="managerStats"
            stripe
          >
            <el-table-column
              type="index"
              label="排名"
              width="70"
            />
            <el-table-column
              prop="name"
              label="项目经理"
              width="100"
            />
            <el-table-column
              prop="total"
              label="项目总数"
              width="90"
              align="center"
            />
            <el-table-column
              prop="completed"
              label="已完成"
              width="80"
              align="center"
            />
            <el-table-column
              prop="inProgress"
              label="进行中"
              width="80"
              align="center"
            />
            <el-table-column
              label="完成率"
              min-width="160"
            >
              <template #default="{ row }">
                <el-progress
                  :percentage="row.completionRate"
                  :color="getProgressColor(row.completionRate)"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 文档分类统计 -->
      <el-col
        :xs="24"
        :lg="12"
      >
        <div class="card-box">
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--primary"><el-icon><Document /></el-icon></span>
              <span>文档分类统计</span>
            </div>
          </div>
          <el-table
            :data="docStats"
            stripe
          >
            <el-table-column
              prop="category"
              label="文档分类"
              width="140"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="getCategoryTagType(row.category)"
                >
                  {{ row.category }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="count"
              label="文档数量"
              width="100"
              align="center"
            />
            <el-table-column
              label="占比"
              min-width="200"
            >
              <template #default="{ row }">
                <el-progress
                  :percentage="row.percentage"
                  :color="getCategoryColor(row.category)"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="size"
              label="总大小"
              width="100"
              align="right"
            >
              <template #default="{ row }">
                {{ formatSize(row.totalSize) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
/**
 * 统计分析页
 * 业务用途：项目数据的多维度统计与可视化报表，支持按时间范围切换与报表导出。
 * 主要功能区：概览指标卡片、项目状态/类型分布图、月度趋势图、客户 TOP5 图、
 *             项目经理绩效表、文档分类统计表。
 * 数据来源：statisticsStore（后端聚合概览/状态/类型/趋势、导出接口）、
 *          projectStore 与 documentStore（前端降级计算经理绩效、客户分布、文档分类等）；图表基于 echarts。
 */
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useDocumentStore } from '@/stores/document'
import { useStatisticsStore } from '@/stores/statistics'
// echarts 全量导入
// 说明：曾尝试按需导入（echarts/core + 各 Chart/Component），但项目使用了 echarts.graphic.LinearGradient，
// 该 API 在 echarts/core 命名空间下未导出，导致渲染时抛错触发 ErrorBoundary。
// 为保证功能稳定，回退为全量导入；后续如需优化打包体积，可将 LinearGradient 改为对象字面量声明后再切按需导入。
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { feedback } from '@/utils/feedback'
import { getProgressColor, getCategoryTagType, getCategoryColor, formatSize, parseSize } from '@/utils/format'
import { downloadBlobFromResponse } from '@/utils/download'
import { CHART_COLORS, STATUS_SEMANTIC, STATUS_SERIES, areaStops, barStops } from '@/utils/chartTheme'

const projectStore = useProjectStore()
const documentStore = useDocumentStore()
const statisticsStore = useStatisticsStore()

const dateRange = ref('all')
const statusChartRef = ref(null)
const typeChartRef = ref(null)
const trendChartRef = ref(null)
const customerChartRef = ref(null)

let statusChart = null
let typeChart = null
let trendChart = null
let customerChart = null

// 概览数据（来自后端聚合查询）
const overview = computed(() => statisticsStore.overview || {
  totalProjects: 0,
  activeProjects: 0,
  completedProjects: 0,
  archivedProjects: 0,
  totalContractAmount: 0,
  avgProgress: 0
})

// 逾期项目数（仍需前端计算，后端未提供该指标）
const overdueCount = computed(() => {
  return projectStore.projects.filter(p =>
    !p.archived && p.status !== '已完成' && dayjs(p.endDate).isBefore(dayjs())
  ).length
})

// 项目经理绩效（降级方案：基于分页拉取的前 50 条项目计算，待后端补聚合接口后替换）
const managerStats = computed(() => {
  const map = {}
  projectStore.projects.forEach(p => {
    if (!map[p.manager]) {
      map[p.manager] = { name: p.manager, total: 0, completed: 0, inProgress: 0 }
    }
    map[p.manager].total++
    if (p.status === '已完成') map[p.manager].completed++
    else if (p.status === '进行中') map[p.manager].inProgress++
  })
  return Object.values(map).map(m => ({
    ...m,
    completionRate: m.total ? Math.round(m.completed / m.total * 100) : 0
  })).sort((a, b) => b.total - a.total)
})

// 文档分类统计（仍需前端计算，后端未提供该维度统计）
const docStats = computed(() => {
  const map = {}
  documentStore.documents.forEach(d => {
    if (!map[d.category]) {
      map[d.category] = { category: d.category, count: 0, totalSize: 0 }
    }
    map[d.category].count++
    map[d.category].totalSize += parseSize(d.size)
  })
  const total = documentStore.documents.length
  return Object.values(map).map(m => ({
    ...m,
    percentage: total ? Math.round(m.count / total * 100) : 0
  })).sort((a, b) => b.count - a.count)
})

// 客户分布数据（仍需前端计算，后端未提供该维度统计）
const customerDistribution = computed(() => {
  const map = {}
  projectStore.projects.forEach(p => {
    map[p.customerName] = (map[p.customerName] || 0) + 1
  })
  return Object.entries(map).sort((a, b) => b[1] - a[1]).slice(0, 5)
})

/** 导出报表：向后端提交当前时间范围的导出请求 */
async function handleExport() {
  // 修复 H13：原实现仅调用 exportReport 但不处理返回的 blob 数据，
  // 后端设置了 responseType:'blob' 直接返回二进制流，前端不下载会导致用户点击导出后无任何文件生成。
  // 参考 DailyReport.vue 的 handleDownload 实现：创建 Blob URL → 触发浏览器下载 → 释放 URL。
  try {
    const res = await statisticsStore.exportReport({ dateRange: dateRange.value })
    // res 为 axios 响应对象，res.data 为 Blob 文件流（接口未带类型时补齐 xlsx MIME）
    downloadBlobFromResponse(
      res,
      `统计报表_${new Date().getTime()}.xlsx`,
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    )
    feedback.success('报表导出成功')
  } catch (error) {
    console.error('导出报表失败:', error)
    feedback.error('导出失败，请稍后重试')
  }
}

/** 加载所有统计数据 */
async function loadStatistics() {
  try {
    // 并行加载：后端聚合统计 + 项目列表（用于补充维度） + 文档列表（用于文档分类）
    // 优化：后端尚无 manager/customer/document 聚合接口，前端拉取分页数据降级计算。
    //       pageSize 限制为 50，避免数据量增长后一次性拉全表导致卡顿。
    //       待后端补 /statistics/manager、/statistics/customer、/statistics/document 后改为直接消费聚合结果。
    await Promise.all([
      statisticsStore.fetchAllStatistics(dateRange.value),
      projectStore.fetchList({ pageNum: 1, pageSize: 50 }),
      documentStore.fetchList({ pageNum: 1, pageSize: 50 })
    ])
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    nextTick(() => {
      initStatusChart()
      initTypeChart()
      initTrendChart()
      initCustomerChart()
    })
  }
}

onMounted(() => {
  loadStatistics()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  statusChart?.dispose()
  typeChart?.dispose()
  trendChart?.dispose()
  customerChart?.dispose()
  statusChart = null
  typeChart = null
  trendChart = null
  customerChart = null
})

function handleResize() {
  statusChart?.resize()
  typeChart?.resize()
  trendChart?.resize()
  customerChart?.resize()
}

// 监听日期范围变化，重新加载后端统计数据并刷新图表
watch(dateRange, () => {
  loadStatistics()
})

function initStatusChart() {
  if (!statusChartRef.value) return
  if (!statusChart) {
    statusChart = echarts.init(statusChartRef.value)
  }
  // 优先使用后端聚合查询的状态分布数据
  const statusData = statisticsStore.statusDistribution.length > 0
    ? statisticsStore.statusDistribution
    : []
  // 兼容后端返回的 Map<String, Object> 格式：[{ name: '进行中', value: 5 }, ...]
  const chartData = statusData.map(item => ({
    name: item.name || item.status || '未知',
    value: Number(item.value || item.count || 0)
  }))
  statusChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#6b7280' } },
    // 低饱和状态语义色（蓝=进行中/绿=完成/黄=注意/红=风险/灰=其他），色盲友好
    color: STATUS_SERIES,
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: chartData
    }]
  }, true)
}

/** 初始化「项目类型分布」柱状图（优先使用后端聚合数据） */
function initTypeChart() {
  if (!typeChartRef.value) return
  if (!typeChart) {
    typeChart = echarts.init(typeChartRef.value)
  }
  // 优先使用后端聚合查询的类型分布数据
  const typeData = statisticsStore.typeDistribution.length > 0
    ? statisticsStore.typeDistribution
    : []
  const categories = typeData.map(item => item.name || item.type || '未知')
  const values = typeData.map(item => Number(item.value || item.count || 0))
  typeChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: categories, axisLabel: { color: '#6b7280' } },
    yAxis: { type: 'value', axisLabel: { color: '#6b7280' }, splitLine: { lineStyle: { color: '#f3f4f6' } } },
    series: [{
      type: 'bar',
      data: values,
      barWidth: '40%',
      itemStyle: {
        // 主色低饱和渐变（顶部亮 → 底部深）
        color: (() => {
          const s = barStops(CHART_COLORS.primary)
          return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: s.color1 },
            { offset: 1, color: s.color2 }
          ])
        })(),
        borderRadius: [6, 6, 0, 0]
      },
      label: { show: true, position: 'top', color: '#6b7280' }
    }]
  }, true)
}

function initTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  // 优先使用后端聚合查询的月度趋势数据
  const trendData = statisticsStore.projectTrend.length > 0
    ? statisticsStore.projectTrend
    : []
  const months = trendData.map(item => item.month || item.date || '')
  const newProjects = trendData.map(item => Number(item.newCount || item.newProjects || 0))
  const completedProjects = trendData.map(item => Number(item.completedCount || item.completedProjects || 0))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增项目', '完成项目'], top: 0, textStyle: { color: '#6b7280' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: months, axisLabel: { color: '#6b7280' } },
    yAxis: { type: 'value', axisLabel: { color: '#6b7280' }, splitLine: { lineStyle: { color: '#f3f4f6' } } },
    series: [
      {
        name: '新增项目',
        type: 'line',
        data: newProjects,
        smooth: true,
        itemStyle: { color: CHART_COLORS.primary },
        areaStyle: {
          // 渐变透明度 25%→0（标准：渐变叠加 5%-25%）
          color: (() => {
            const s = areaStops(CHART_COLORS.primary)
            return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: s.color1 },
              { offset: 1, color: s.color2 }
            ])
          })()
        }
      },
      {
        name: '完成项目',
        type: 'line',
        data: completedProjects,
        smooth: true,
        itemStyle: { color: STATUS_SEMANTIC.done },
        areaStyle: {
          color: (() => {
            const s = areaStops(STATUS_SEMANTIC.done)
            return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: s.color1 },
              { offset: 1, color: s.color2 }
            ])
          })()
        }
      }
    ]
  }, true)
}

function initCustomerChart() {
  if (!customerChartRef.value) return
  if (!customerChart) {
    customerChart = echarts.init(customerChartRef.value)
  }
  // 客户分布后端未提供，使用前端计算
  const sorted = customerDistribution.value
  customerChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: '#6b7280' }, splitLine: { lineStyle: { color: '#f3f4f6' } } },
    yAxis: {
      type: 'category',
      data: sorted.map(s => s[0]).reverse(),
      axisLabel: { color: '#6b7280', fontSize: 11 }
    },
    series: [{
      type: 'bar',
      data: sorted.map(s => s[1]).reverse(),
      barWidth: '50%',
      itemStyle: {
        // 客户分布属中性对比数据，改用辅助色；红色保留给异常数据专用（标准：点缀色用于高亮异常）
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: CHART_COLORS.primary },
          { offset: 1, color: CHART_COLORS.secondary }
        ]),
        borderRadius: [0, 6, 6, 0]
      },
      label: { show: true, position: 'right', color: '#6b7280' }
    }]
  }, true)
}
</script>

<style scoped>
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
</style>
