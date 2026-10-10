<template>
  <!--
    站内通知抽屉组件（P1-2.1 拆分自 MainLayout.vue）
    父组件通过 v-model:visible 控制显隐。对话框打开时自动拉取通知列表与未读数；
    支持按已读/未读筛选、加载更多、点击跳转业务页、标记单条/全部已读、删除单条通知。
  -->
  <el-drawer
    :model-value="visible"
    title="站内通知"
    size="500px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <template #header>
      <div class="drawer-header">
        <el-icon style="color: #1890ff; font-size: 18px">
          <ChatDotRound />
        </el-icon>
        <span>站内通知</span>
        <el-tag
          v-if="notificationStore.unreadCount > 0"
          size="small"
          type="danger"
          style="margin-left: 8px"
        >
          {{ notificationStore.unreadCount }} 未读
        </el-tag>
        <BaseButton
          v-if="notificationStore.unreadCount > 0"
          variant="link"
          style="margin-left: auto"
          @click="handleMarkAllRead"
        >
          全部已读
        </BaseButton>
      </div>
    </template>
    <div class="notification-toolbar">
      <el-radio-group
        v-model="notifFilter"
        size="small"
        @change="handleNotifFilterChange"
      >
        <el-radio-button value="">
          全部
        </el-radio-button>
        <el-radio-button value="0">
          未读
        </el-radio-button>
        <el-radio-button value="1">
          已读
        </el-radio-button>
      </el-radio-group>
    </div>
    <el-empty
      v-if="!notificationStore.loading && notificationStore.list.length === 0"
      description="暂无通知"
      :image-size="80"
    />
    <div
      v-else
      v-loading="notificationStore.loading"
      class="notification-list"
    >
      <div
        v-for="item in notificationStore.list"
        :key="item.id"
        class="notification-item"
        :class="{ 'is-unread': !item.isRead }"
        @click="handleNotifClick(item)"
      >
        <div
          class="notification-dot"
          :class="getPriorityClass(item.priority)"
        />
        <div class="notification-main">
          <div class="notification-title-row">
            <span class="notification-title">{{ item.title }}</span>
            <el-tag
              size="small"
              :type="getNotifyTypeTagType(item.notifyType)"
            >
              {{ getNotifyTypeText(item.notifyType) }}
            </el-tag>
          </div>
          <div class="notification-content">
            {{ item.content }}
          </div>
          <div class="notification-footer">
            <span class="notification-time">{{ formatNotifTime(item.createdAt) }}</span>
            <el-button
              v-if="!item.isRead"
              type="primary"
              link
              size="small"
              @click.stop="handleMarkRead(item)"
            >
              标记已读
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              @click.stop="handleDeleteNotif(item)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
      <div
        v-if="notificationStore.list.length > 0 && notificationStore.list.length < notificationStore.total"
        class="notification-load-more"
      >
        <BaseButton
          variant="link"
          :loading="notificationStore.loading"
          @click="loadMoreNotifications"
        >
          加载更多
        </BaseButton>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
/**
 * 站内通知抽屉
 * 业务用途：集中展示当前用户的通知列表，支持已读/未读筛选、分页加载、点击跳转业务详情、
 *          标记单条/全部已读、删除单条通知。
 * 数据来源：notificationStore（list / unreadCount / loading / total）；WebSocket 推送由父组件连接。
 * 路由跳转：根据通知 bizType + bizId 跳转对应业务页面（progress/task/project/archive）。
 */
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import { feedback } from '@/utils/feedback'
import { getNotifyTypeText, getNotifyTypeTagType } from '@/utils/statusMap'
import { ChatDotRound } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

// 启用 dayjs 相对时间插件（用于"几分钟前"格式化）
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const props = defineProps({
  /** 抽屉显隐（v-model:visible） */
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible'])

const router = useRouter()
const notificationStore = useNotificationStore()

const notifFilter = ref('')

/** 抽屉打开时拉取通知列表与未读数 */
watch(() => props.visible, (val) => {
  if (val) {
    notifFilter.value = ''
    Promise.all([
      notificationStore.fetchList({ pageNum: 1, pageSize: 20, isRead: '' }),
      notificationStore.fetchUnreadCount()
    ]).catch((err) => console.error('拉取通知失败:', err))
  }
})

/** 切换通知筛选条件（全部/未读/已读） */
async function handleNotifFilterChange() {
  await notificationStore.fetchList({ pageNum: 1, pageSize: 20, isRead: notifFilter.value })
}

/** 加载更多通知（按当前筛选条件翻页） */
async function loadMoreNotifications() {
  const nextPage = notificationStore.query.pageNum + 1
  await notificationStore.fetchList({ pageNum: nextPage })
}

/** 点击通知项：标记已读，若有 bizType+bizId 则跳转对应业务页 */
async function handleNotifClick(item) {
  if (!item.isRead) {
    await notificationStore.markAsRead(item.id)
  }
  // 按业务类型路由跳转
  if (!item.bizType || !item.bizId) return
  const routes = {
    progress: () => router.push(`/progress/${item.bizId}`),
    task: () => router.push(`/progress/${item.bizId}`),
    project: () => router.push(`/projects/${item.bizId}`),
    archive: () => router.push('/archive')
  }
  const fn = routes[item.bizType]
  if (fn) {
    emit('update:visible', false)
    fn()
  }
}

/** 标记单条通知为已读 */
async function handleMarkRead(item) {
  await notificationStore.markAsRead(item.id)
}

/** 全部标记已读 */
async function handleMarkAllRead() {
  await notificationStore.markAllAsRead()
  feedback.success('已全部标记为已读')
}

/** 删除单条通知（二次确认） */
async function handleDeleteNotif(item) {
  try {
    await feedback.confirmDelete('确认删除该通知？')
    await notificationStore.removeNotification(item.id)
    feedback.success('已删除')
  } catch (e) {
    // 用户取消
  }
}

/** 根据通知优先级返回圆点样式类 @param {number} priority 优先级（1低/2中/3高） @returns {string} 样式类名 */
function getPriorityClass(priority) {
  if (priority === 3) return 'dot-danger'
  if (priority === 2) return 'dot-warning'
  return 'dot-info'
}

// P3 重构：通知类型文本/tag 映射统一改用 utils/statusMap 共享实现，删除本地重复定义

/** 格式化通知时间为相对时间（如"5分钟前"），超过 7 天显示绝对时间 @param {string} timeStr 时间字符串 @returns {string} 格式化后的时间 */
function formatNotifTime(timeStr) {
  if (!timeStr) return ''
  const t = dayjs(timeStr)
  const diffDays = dayjs().diff(t, 'day')
  if (diffDays < 7) {
    return t.fromNow()
  }
  return t.format('YYYY-MM-DD HH:mm')
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

.notification-toolbar {
  padding: 0 4px 14px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
}

.notification-list {
  min-height: 200px;
  padding: 0 4px;
}

.notification-item {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
  margin-bottom: 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 4px solid transparent;
}

.notification-item:last-child {
  margin-bottom: 0;
}

.notification-item:hover {
  background: #f5f7fa;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.notification-item.is-unread {
  background: #ecf5ff;
  border-left-color: #1890ff;
}

.notification-item.is-unread:hover {
  background: #e0efff;
}

.notification-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.4);
}

.notification-dot.dot-info {
  background: #909399;
}

.notification-dot.dot-warning {
  background: #e6a23c;
}

.notification-dot.dot-danger {
  background: #f56c6c;
}

.notification-main {
  flex: 1;
  min-width: 0;
}

.notification-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.notification-title {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item.is-unread .notification-title {
  color: #1890ff;
}

.notification-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notification-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 4px;
  border-top: 1px dashed #f0f0f0;
}

.notification-time {
  font-size: 12px;
  color: #909399;
}

.notification-load-more {
  text-align: center;
  padding: 16px 0 8px;
}
</style>
