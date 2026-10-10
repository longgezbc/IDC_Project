<template>
  <div class="gantt-container">
    <div class="gantt-toolbar">
      <el-radio-group
        v-model="viewMode"
        size="small"
        @change="handleViewChange"
      >
        <el-radio-button value="day">
          日
        </el-radio-button>
        <el-radio-button value="week">
          周
        </el-radio-button>
        <el-radio-button value="month">
          月
        </el-radio-button>
      </el-radio-group>
      <div class="gantt-legend">
        <span class="legend-item"><i class="legend-bar completed" />已完成</span>
        <span class="legend-item"><i class="legend-bar progressing" />进行中</span>
        <span class="legend-item"><i class="legend-bar pending" />未开始</span>
        <span class="legend-item"><i class="legend-bar milestone">◆</i>里程碑</span>
        <span class="legend-item"><i class="legend-bar key" />关键节点</span>
      </div>
    </div>

    <div
      ref="wrapperRef"
      class="gantt-wrapper"
    >
      <!-- 左侧任务列表 -->
      <div class="gantt-task-list">
        <div class="gantt-header-row">
          <div class="task-cell task-index">
            #
          </div>
          <div class="task-cell task-name">
            任务名称
          </div>
          <div class="task-cell task-assignee">
            负责人
          </div>
          <div class="task-cell task-progress">
            进度
          </div>
        </div>
        <div
          v-for="(task, i) in tasks"
          :key="task.id"
          class="gantt-task-row"
        >
          <div class="task-cell task-index">
            {{ i + 1 }}
          </div>
          <div
            class="task-cell task-name"
            :title="task.name"
          >
            <el-icon
              v-if="task.isMilestone"
              style="color: #e6a23c"
            >
              <Flag />
            </el-icon>
            <el-icon
              v-else-if="task.isKey"
              style="color: #f56c6c"
            >
              <Star />
            </el-icon>
            {{ task.name }}
          </div>
          <div class="task-cell task-assignee">
            {{ task.assignee }}
          </div>
          <div class="task-cell task-progress">
            <el-progress
              :percentage="task.progress"
              :stroke-width="6"
              :show-text="false"
            />
            <span class="progress-text">{{ task.progress }}%</span>
          </div>
        </div>
      </div>

      <!-- 右侧甘特图区域 -->
      <div
        ref="chartAreaRef"
        class="gantt-chart-area"
      >
        <!-- 日期表头 -->
        <div class="gantt-header-row gantt-date-header">
          <div
            v-for="(col, i) in dateColumns"
            :key="i"
            class="date-cell"
            :class="{ weekend: col.isWeekend, today: col.isToday }"
            :style="{ width: colWidth + 'px' }"
          >
            <div class="date-main">
              {{ col.mainLabel }}
            </div>
            <div
              v-if="col.subLabel"
              class="date-sub"
            >
              {{ col.subLabel }}
            </div>
          </div>
        </div>

        <!-- 任务条区域 -->
        <div
          class="gantt-body"
          :style="{ width: contentWidth + 'px', height: contentHeight + 'px' }"
        >
          <!-- 网格背景 -->
          <div class="gantt-grid">
            <div
              v-for="(col, i) in dateColumns"
              :key="i"
              class="grid-col"
              :class="{ weekend: col.isWeekend, today: col.isToday }"
              :style="{ width: colWidth + 'px' }"
            />
          </div>

          <!-- 今日竖线 -->
          <div
            v-if="todayPosition >= 0"
            class="today-line"
            :style="{ left: todayPosition + 'px' }"
          >
            <div class="today-label">
              今日
            </div>
          </div>

          <!-- 任务条 -->
          <div
            v-for="(task, i) in tasks"
            :key="task.id"
            class="gantt-task-bar"
            :style="{ top: (i + 1) * rowHeight + 'px' }"
          >
            <template v-if="!task.isMilestone">
              <div
                class="task-bar"
                :class="getBarClass(task)"
                :style="getBarStyle(task)"
                @click="$emit('task-click', task)"
              >
                <div
                  class="task-bar-progress"
                  :style="{ width: task.progress + '%' }"
                />
                <span class="task-bar-label">{{ task.name }}</span>
              </div>
            </template>
            <template v-else>
              <div
                class="milestone-marker"
                :style="getMilestoneStyle(task)"
                @click="$emit('task-click', task)"
              >
                <el-icon><Flag /></el-icon>
                <span class="milestone-label">{{ task.name }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * GanttChart 甘特图组件
 *
 * 基于 dayjs 与纯 CSS 定位渲染任务甘特图，左侧为任务列表、右侧为按日/周/月三种视图
 * 切换的时间轴。核心是将任务起止日期换算为像素偏移与宽度：以时间范围起点为基准，
 * 按视图粒度计算列偏移（日=天差、周=天差/7、月=年月差）再乘以列宽。
 * 支持里程碑标记、关键节点高亮、今日竖线，并在挂载/切换/任务变化时自动横向滚动到今日。
 * @prop {Array} tasks 任务列表（含 name、assignee、progress、startDate、endDate、isKey、isMilestone 等）
 * @prop {string} startDate 时间范围起始日期
 * @prop {string} endDate 时间范围结束日期
 * @emits task-click 点击任务条或里程碑时触发，回传对应任务对象
 */
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import dayjs from 'dayjs'

const props = defineProps({
  tasks: { type: Array, default: () => [] },
  startDate: { type: String, default: '' },
  endDate: { type: String, default: '' }
})

defineEmits(['task-click'])

const viewMode = ref('day')
// 列宽根据视图模式自动计算（computed，避免在 dateColumns 中产生副作用）
const colWidth = computed(() => {
  if (viewMode.value === 'day') return 40
  if (viewMode.value === 'week') return 60
  if (viewMode.value === 'month') return 100
  return 40
})
const rowHeight = ref(40)
const wrapperRef = ref(null)
const chartAreaRef = ref(null)

// today 改为 computed：组件长时间挂载（如不刷新页面跨天）后"今日"竖线位置会自动更新
const today = computed(() => dayjs())

// 计算时间范围
const timeRange = computed(() => {
  if (!props.startDate || !props.endDate) return { start: today.value, end: today.value.add(1, 'month') }
  let start = dayjs(props.startDate)
  let end = dayjs(props.endDate)
  // 留出边距
  start = start.subtract(2, 'day')
  end = end.add(2, 'day')
  return { start, end }
})

// 生成日期列
const dateColumns = computed(() => {
  const cols = []
  let current = timeRange.value.start.clone()
  const end = timeRange.value.end

  if (viewMode.value === 'day') {
    while (current.isBefore(end) || current.isSame(end, 'day')) {
      cols.push({
        date: current.format('YYYY-MM-DD'),
        mainLabel: current.format('MM/DD'),
        subLabel: current.date().toString(),
        isWeekend: current.day() === 0 || current.day() === 6,
        isToday: current.isSame(today.value, 'day')
      })
      current = current.add(1, 'day')
    }
  } else if (viewMode.value === 'week') {
    while (current.isBefore(end) || current.isSame(end, 'day')) {
      const weekEnd = current.add(6, 'day')
      cols.push({
        date: current.format('YYYY-MM-DD'),
        mainLabel: current.format('MM月') + '第' + Math.ceil(current.date() / 7) + '周',
        subLabel: current.format('MM/DD'),
        isWeekend: false,
        isToday: today.value.isAfter(current) && today.value.isBefore(weekEnd.add(1, 'day'))
      })
      current = current.add(7, 'day')
    }
  } else if (viewMode.value === 'month') {
    while (current.isBefore(end) || current.isSame(end, 'month')) {
      cols.push({
        date: current.format('YYYY-MM-DD'),
        mainLabel: current.format('YYYY年MM月'),
        subLabel: '',
        isWeekend: false,
        isToday: current.isSame(today.value, 'month')
      })
      current = current.add(1, 'month').startOf('month')
    }
  }
  return cols
})

// 今日位置（三种视图模式计算方式相同，合并为单一逻辑）
const todayPosition = computed(() => {
  const cols = dateColumns.value
  const index = cols.findIndex(c => c.isToday)
  return index >= 0 ? index * colWidth.value + colWidth.value / 2 : -1
})

// 内容总宽度（确保 gantt-body 撑开，任务条不被裁剪）
const contentWidth = computed(() => {
  return dateColumns.value.length * colWidth.value
})

// 内容总高度（确保 gantt-body 撑开，任务条不被垂直裁剪）
const contentHeight = computed(() => {
  return (props.tasks.length + 1) * rowHeight.value
})

/**
 * 计算任务条的 CSS 类名
 *
 * 依据进度映射完成/进行中/未开始三种底色，关键节点追加 bar-key 高亮边框类。
 * @param {Object} task 任务对象
 * @returns {string} 空格分隔的类名字符串
 */
function getBarClass(task) {
  const classes = []
  if (task.progress === 100) classes.push('bar-completed')
  else if (task.progress > 0) classes.push('bar-progressing')
  else classes.push('bar-pending')
  if (task.isKey) classes.push('bar-key')
  return classes.join(' ')
}

/**
 * 计算任务条的定位样式（left/width/height）
 *
 * 按当前视图粒度将起止日期换算为相对时间范围起点的列偏移与跨度：
 * 日视图用天差、周视图用天差/7、月视图用年月差；宽度不小于一个列宽避免过窄不可见。
 * @param {Object} task 任务对象（含 startDate、endDate）
 * @returns {{left:string,width:string,height:string}} 内联样式对象
 */
function getBarStyle(task) {
  const start = dayjs(task.startDate)
  const end = dayjs(task.endDate)
  const rangeStart = timeRange.value.start

  let offsetDays, durationDays
  if (viewMode.value === 'day') {
    offsetDays = start.diff(rangeStart, 'day')
    durationDays = end.diff(start, 'day') + 1
  } else if (viewMode.value === 'week') {
    offsetDays = Math.floor(start.diff(rangeStart, 'day') / 7)
    durationDays = Math.ceil((end.diff(start, 'day') + 1) / 7)
  } else {
    offsetDays = (start.year() - rangeStart.year()) * 12 + (start.month() - rangeStart.month())
    durationDays = Math.max(1, (end.year() - start.year()) * 12 + (end.month() - start.month()) + 1)
  }

  const left = offsetDays * colWidth.value
  const width = Math.max(durationDays * colWidth.value, colWidth.value)

  return {
    left: left + 'px',
    width: width + 'px',
    height: (rowHeight.value - 12) + 'px'
  }
}

/**
 * 计算里程碑标记的水平定位样式
 *
 * 以里程碑日期相对时间范围起点的列偏移定位到列中心（减去半个标记宽度居中）。
 * @param {Object} task 里程碑任务对象（含 startDate）
 * @returns {{left:string}} 内联样式对象
 */
function getMilestoneStyle(task) {
  const date = dayjs(task.startDate)
  const rangeStart = timeRange.value.start
  let offsetDays
  if (viewMode.value === 'day') {
    offsetDays = date.diff(rangeStart, 'day')
  } else if (viewMode.value === 'week') {
    offsetDays = Math.floor(date.diff(rangeStart, 'day') / 7)
  } else {
    offsetDays = (date.year() - rangeStart.year()) * 12 + (date.month() - rangeStart.month())
  }
  return {
    left: (offsetDays * colWidth.value + colWidth.value / 2 - 10) + 'px'
  }
}

/**
 * 视图模式切换处理：等待 DOM 更新后滚动到今日
 * @returns {void}
 */
function handleViewChange() {
  nextTick(() => {
    scrollToToday()
  })
}

/**
 * 将图表区域横向滚动到今日位置（今日尽量居中）
 * @returns {void}
 */
function scrollToToday() {
  if (chartAreaRef.value && todayPosition.value > 0) {
    chartAreaRef.value.scrollLeft = Math.max(0, todayPosition.value - chartAreaRef.value.clientWidth / 2)
  }
}

// 窗口 resize 时重新滚动到今日（窗口宽度变化后 clientWidth 改变，滚动位置需重算）
function handleResize() {
  scrollToToday()
}

onMounted(() => {
  nextTick(() => {
    scrollToToday()
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

// 监听 tasks 数组长度变化时滚动到今日（比 deep watch 更高效）
watch(() => props.tasks?.length, () => {
  nextTick(() => scrollToToday())
})
</script>

<style scoped>
.gantt-container {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.gantt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.gantt-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #606266;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-bar {
  display: inline-block;
  width: 16px;
  height: 10px;
  border-radius: 2px;
}

.legend-bar.completed { background: #67c23a; }
.legend-bar.progressing { background: #409eff; }
.legend-bar.pending { background: #dcdfe6; }
.legend-bar.milestone { color: #e6a23c; width: auto; height: auto; }
.legend-bar.key { background: #f56c6c; }

.gantt-wrapper {
  display: flex;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.gantt-task-list {
  flex-shrink: 0;
  width: 380px;
  border-right: 1px solid #ebeef5;
  background: #fafafa;
}

.gantt-chart-area {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
}

.gantt-header-row {
  display: flex;
  height: 50px;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 2;
}

.gantt-task-list .gantt-header-row {
  width: 380px;
}

.task-cell {
  display: flex;
  align-items: center;
  padding: 0 8px;
  font-size: 13px;
  color: #606266;
  border-right: 1px solid #ebeef5;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.task-index { width: 40px; justify-content: center; }
.task-name { flex: 1; min-width: 0; }
.task-assignee { width: 80px; }
.task-progress { width: 100px; flex-direction: column; gap: 2px; }

.progress-text {
  font-size: 11px;
  color: #909399;
}

.gantt-task-row {
  display: flex;
  height: 40px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.gantt-task-row:hover {
  background: #f5f7fa;
}

.gantt-date-header {
  min-width: 100%;
}

.date-cell {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #ebeef5;
  font-size: 12px;
}

.date-cell.weekend {
  background: #fef0f0;
}

.date-cell.today {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.date-main {
  color: #303133;
}

.date-sub {
  font-size: 11px;
  color: #909399;
}

.gantt-body {
  position: relative;
  min-height: 40px;
}

.gantt-grid {
  display: flex;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  z-index: 0;
}

.grid-col {
  flex-shrink: 0;
  height: 100%;
  border-right: 1px solid #f5f5f5;
}

.grid-col.weekend {
  background: #fafafa;
}

.grid-col.today {
  background: #f0f7ff;
}

.today-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #f56c6c;
  z-index: 3;
  pointer-events: none;
}

.today-label {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
}

.gantt-task-bar {
  position: absolute;
  left: 0;
  height: 40px;
  width: 100%;
  pointer-events: none;
}

.task-bar {
  position: absolute;
  top: 6px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  pointer-events: auto;
  display: flex;
  align-items: center;
  padding: 0 8px;
  font-size: 12px;
  color: #fff;
  white-space: nowrap;
  transition: all 0.2s;
}

.task-bar:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.bar-completed {
  background: #67c23a;
}

.bar-completed.bar-key {
  box-shadow: 0 0 0 2px #f56c6c inset;
}

.bar-progressing {
  background: #409eff;
}

.bar-progressing.bar-key {
  box-shadow: 0 0 0 2px #f56c6c inset;
}

.bar-pending {
  background: #c0c4cc;
}

.bar-pending.bar-key {
  box-shadow: 0 0 0 2px #f56c6c inset;
}

.task-bar-progress {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.3);
}

.bar-completed .task-bar-progress {
  background: rgba(255, 255, 255, 0.2);
}

.task-bar-label {
  position: relative;
  z-index: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.milestone-marker {
  position: absolute;
  top: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  pointer-events: auto;
  color: #e6a23c;
  font-size: 20px;
  z-index: 3;
  max-width: 120px;
  overflow: hidden;
}

.milestone-label {
  font-size: 12px;
  color: #e6a23c;
  white-space: nowrap;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 90px;
}

/* 关键节点边框（使用 box-shadow inset 避免影响布局尺寸） */
.task-bar.bar-key {
  box-shadow: 0 0 0 2px #f56c6c inset;
}
</style>
