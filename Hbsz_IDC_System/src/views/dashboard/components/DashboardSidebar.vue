<template>
  <!--
    仪表盘右侧栏组件（P1-2.1 拆分自 Dashboard.vue）
    父组件通过 :remind-list / :recent-projects 传入数据；
    卡片点击通过 emit 通知父组件路由跳转，不在子组件内直接访问 router，保持纯粹。
  -->
  <div>
    <!-- 关键节点提醒卡片 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <div class="card-title">
            <span class="title-chip title-chip--danger"><el-icon><Bell /></el-icon></span>
            <span>关键节点提醒</span>
          </div>
          <el-badge
            :value="remindList.length"
            :hidden="remindList.length === 0"
          />
        </div>
      </template>
      <el-empty
        v-if="remindList.length === 0"
        description="暂无提醒"
        :image-size="60"
      />
      <div
        v-else
        class="remind-list"
      >
        <div
          v-for="item in remindList.slice(0, 5)"
          :key="item.id"
          class="remind-item"
          @click="$emit('go-progress', item.projectId)"
        >
          <div class="remind-item-left">
            <div
              class="remind-dot"
              :class="isOverdue(item) ? 'dot-danger' : 'dot-warning'"
            />
          </div>
          <div class="remind-item-content">
            <div class="remind-item-header">
              <span class="remind-item-name">{{ item.name }}</span>
              <el-tag
                size="small"
                :type="isOverdue(item) ? 'danger' : 'warning'"
                effect="dark"
              >
                {{ isOverdue(item) ? '逾期' : '紧急' }}
              </el-tag>
            </div>
            <div class="remind-item-project">
              {{ item.projectName }}
            </div>
            <div class="remind-item-info">
              <span><el-icon><User /></el-icon>{{ item.assignee }}</span>
              <span :class="{ 'is-overdue': isOverdue(item) }"><el-icon><Calendar /></el-icon>{{ item.endDate }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 最近项目卡片 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="card-title">
            <span class="title-chip title-chip--primary"><el-icon><Clock /></el-icon></span>
            <span>最近项目</span>
          </div>
          <BaseButton
            variant="link"
            @click="$emit('view-all-projects')"
          >
            更多
          </BaseButton>
        </div>
      </template>
      <el-empty
        v-if="recentProjects.length === 0"
        description="暂无项目"
        :image-size="60"
      />
      <div
        v-else
        class="recent-list"
      >
        <div
          v-for="p in recentProjects"
          :key="p.id"
          class="recent-item"
          @click="$emit('go-detail', p.id)"
        >
          <div class="recent-item-top">
            <span class="recent-name">{{ p.name }}</span>
            <el-tag
              size="small"
              :type="p.status === '进行中' ? 'primary' : 'success'"
            >
              {{ p.status }}
            </el-tag>
          </div>
          <div class="recent-item-info">
            <span><el-icon><OfficeBuilding /></el-icon>{{ p.customerName }}</span>
            <span><el-icon><User /></el-icon>{{ p.manager }}</span>
          </div>
          <div class="recent-progress">
            <el-progress
              :percentage="p.progress"
              :stroke-width="6"
              :show-text="false"
              :color="getProgressColor(p.progress)"
            />
            <span class="progress-num">{{ p.progress }}%</span>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 仪表盘右侧栏
 * 业务用途：聚合展示「关键节点提醒」（最多 5 条）与「最近项目」（最多 5 条）两个卡片。
 * 数据来源：父组件传入的 remindList（来自 progressStore.getAllUpcomingKeyTasks）与
 *          recentProjects（来自 projectStore.projects 过滤后切片）。
 * 路由跳转：通过 emit 事件交由父组件处理，不在子组件内访问 router。
 */
import dayjs from 'dayjs'
import { getProgressColor } from '@/utils/format'
import { Bell, User, Calendar, Clock, OfficeBuilding } from '@element-plus/icons-vue'

defineProps({
  /** 关键节点提醒列表（跨项目聚合） */
  remindList: { type: Array, default: () => [] },
  /** 最近项目列表（已过滤未归档，最多 5 条） */
  recentProjects: { type: Array, default: () => [] }
})

defineEmits(['go-progress', 'go-detail', 'view-all-projects'])

/** 判断任务是否已逾期（未完成且截止日期早于今天） @param {Object} task 任务对象 @returns {boolean} */
function isOverdue(task) {
  return task.status !== '已完成' && dayjs(task.endDate).isBefore(dayjs())
}
</script>

<style scoped>
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

/* title-chip 样式已全局化到 global.css */

.remind-list {
  max-height: 360px;
  overflow-y: auto;
}

.remind-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafbfc;
  border: 1px solid #f3f4f6;
}

.remind-item:hover {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transform: translateX(4px);
}

.remind-item-left {
  display: flex;
  align-items: flex-start;
  padding-top: 4px;
}

.remind-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-danger {
  background: #f5222d;
  box-shadow: 0 0 0 4px rgba(245, 34, 45, 0.1);
}

.dot-warning {
  background: #faad14;
  box-shadow: 0 0 0 4px rgba(250, 173, 20, 0.1);
}

.remind-item-content {
  flex: 1;
  min-width: 0;
}

.remind-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.remind-item-name {
  font-weight: 600;
  color: #1f2937;
  font-size: 13px;
}

.remind-item-project {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remind-item-info {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #6b7280;
}

.remind-item-info span {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 逾期任务的截止日期红色高亮，与状态标签形成双重警示 */
.remind-item-info span.is-overdue {
  color: var(--danger-text, #d4380d);
  font-weight: 600;
}

.recent-list {
  max-height: 360px;
  overflow-y: auto;
}

.recent-item {
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  background: #fafbfc;
  border: 1px solid #f3f4f6;
  cursor: pointer;
  transition: all 0.2s;
}

.recent-item:hover {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transform: translateX(4px);
}

.recent-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.recent-name {
  font-weight: 600;
  color: #1f2937;
  font-size: 13px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}

.recent-item-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.recent-item-info span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.recent-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-num {
  font-size: 12px;
  color: #6b7280;
  font-weight: 600;
  min-width: 36px;
  text-align: right;
}
</style>
