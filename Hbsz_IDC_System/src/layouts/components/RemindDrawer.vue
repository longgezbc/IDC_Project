<template>
  <!--
    关键节点提醒抽屉组件（P1-2.1 拆分自 MainLayout.vue）
    父组件通过 v-model:visible 控制显隐，通过 :remind-list 传入跨项目关键节点列表；
    点击提醒卡片触发 navigate 事件，由父组件跳转到对应项目的进度页。
  -->
  <el-drawer
    :model-value="visible"
    title="关键节点提醒"
    size="450px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <template #header>
      <div class="drawer-header">
        <el-icon style="color: #f56c6c; font-size: 18px">
          <Bell />
        </el-icon>
        <span>关键节点提醒</span>
        <el-tag
          size="small"
          type="danger"
          style="margin-left: 8px"
        >
          {{ remindList.length }}
        </el-tag>
      </div>
    </template>
    <el-empty
      v-if="remindList.length === 0"
      description="暂无关键节点提醒"
      :image-size="80"
    />
    <el-timeline v-else>
      <el-timeline-item
        v-for="item in remindList"
        :key="item.id"
        :type="getRemindType(item)"
        :timestamp="item.endDate"
        placement="top"
        size="large"
      >
        <el-card
          class="remind-card"
          shadow="hover"
          @click="handleCardClick(item)"
        >
          <div class="remind-title">
            <el-tag
              v-if="isOverdue(item)"
              size="small"
              type="danger"
              effect="dark"
            >
              已逾期
            </el-tag>
            <el-tag
              v-else-if="isUrgent(item)"
              size="small"
              type="warning"
              effect="dark"
            >
              紧急
            </el-tag>
            <el-tag
              v-else
              size="small"
              type="info"
              effect="plain"
            >
              即将开始
            </el-tag>
            <span class="remind-name">{{ item.name }}</span>
          </div>
          <div class="remind-project">
            <el-icon><Folder /></el-icon>
            {{ item.projectName }}
          </div>
          <div class="remind-info">
            <span><el-icon><User /></el-icon> {{ item.assignee }}</span>
            <span><el-icon><Calendar /></el-icon> {{ item.startDate }} ~ {{ item.endDate }}</span>
          </div>
        </el-card>
      </el-timeline-item>
    </el-timeline>
  </el-drawer>
</template>

<script setup>
/**
 * 关键节点提醒抽屉
 * 业务用途：聚合展示跨项目的关键节点任务，按状态（已逾期/紧急/即将开始）进行视觉标记。
 * 数据来源：父组件从 progressStore.getAllUpcomingKeyTasks() 计算后通过 :remind-list 传入。
 */
import dayjs from 'dayjs'
import { Bell, Folder, User, Calendar } from '@element-plus/icons-vue'

defineProps({
  /** 抽屉显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 关键节点任务列表（跨项目聚合，来自 progressStore.getAllUpcomingKeyTasks） */
  remindList: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:visible', 'navigate'])

/** 判断任务是否已逾期（未完成且截止日期早于今天） @param {Object} task 任务对象 @returns {boolean} */
function isOverdue(task) {
  return task.status !== '已完成' && dayjs(task.endDate).isBefore(dayjs())
}

/** 判断任务是否紧急（距截止日期 0~3 天内） @param {Object} task 任务对象 @returns {boolean} */
function isUrgent(task) {
  const end = dayjs(task.endDate)
  const now = dayjs()
  const days = end.diff(now, 'day')
  return days >= 0 && days <= 3
}

/** 获取提醒时间线节点的展示类型 @param {Object} task 任务对象 @returns {'danger'|'warning'|'primary'} */
function getRemindType(task) {
  if (isOverdue(task)) return 'danger'
  if (isUrgent(task)) return 'warning'
  return 'primary'
}

/** 点击提醒卡片：关闭抽屉并通知父组件跳转 @param {Object} item 任务对象 */
function handleCardClick(item) {
  emit('update:visible', false)
  emit('navigate', item.projectId)
}
</script>

<style scoped>
.drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
}

.remind-card {
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 0;
}

.remind-card:hover {
  transform: translateX(4px);
}

.remind-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.remind-name {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.remind-project {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.remind-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin-top: 6px;
}

.remind-info span {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
