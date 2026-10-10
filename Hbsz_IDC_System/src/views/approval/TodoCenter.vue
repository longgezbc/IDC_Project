<template>
  <div class="todo-center">
    <!-- 统计卡片：四类待办计数 + 合计 -->
    <el-row
      :gutter="12"
      class="stat-row"
    >
      <el-col
        v-for="card in statCards"
        :key="card.key"
        :span="5"
      >
        <div
          class="stat-card"
          :class="{ active: activeSource === card.key }"
          role="button"
          tabindex="0"
          @click="switchSource(card.key)"
          @keyup.enter="switchSource(card.key)"
        >
          <div
            class="stat-icon"
            :style="{ backgroundColor: card.bg, color: card.color }"
          >
            <el-icon :size="20">
              <component :is="card.icon" />
            </el-icon>
          </div>
          <div class="stat-info">
            <div
              class="stat-value"
              :style="{ color: counts[card.key] > 0 ? card.color : undefined }"
            >
              {{ counts[card.key] }}
            </div>
            <div class="stat-label">
              {{ card.label }}
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div
          class="stat-card total-card"
          :class="{ active: activeSource === null }"
          role="button"
          tabindex="0"
          @click="switchSource(null)"
          @keyup.enter="switchSource(null)"
        >
          <div class="stat-info">
            <div
              class="stat-value"
              :class="{ 'has-todo': counts.total > 0 }"
            >
              {{ counts.total }}
            </div>
            <div class="stat-label">
              待办合计
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 统一待办列表 -->
    <CrudTable
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :data="list"
      :loading="loading"
      :total="total"
      @change="fetchList"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="table-title">审批待办</span>
          <el-tag
            v-if="activeSource"
            closable
            size="small"
            @close="switchSource(null)"
          >
            {{ sourceMeta(activeSource).label }}
          </el-tag>
        </div>
        <div class="toolbar-right">
          <span
            v-if="lastUpdated"
            class="update-time"
          >更新于 {{ lastUpdated }}</span>
          <BaseButton
            variant="plain"
            icon-name="Refresh"
            @click="refreshAll"
          >
            刷新
          </BaseButton>
        </div>
      </template>

      <el-table-column
        label="类型"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="sourceMeta(row.source).tagType"
            size="small"
            effect="light"
          >
            <el-icon style="vertical-align: -2px; margin-right: 2px">
              <component :is="sourceMeta(row.source).icon" />
            </el-icon>
            {{ sourceMeta(row.source).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="projectName"
        label="所属项目"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="title"
        label="待办事项"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column
        prop="detail"
        label="详情"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="submitter"
        label="提交人"
        width="100"
        show-overflow-tooltip
      />
      <el-table-column
        prop="submitTime"
        label="提交时间"
        width="165"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.submitTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="150"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link-success"
            size="small"
            @click="openReview(row, true)"
          >
            通过
          </BaseButton>
          <BaseButton
            variant="link-danger"
            size="small"
            @click="openReview(row, false)"
          >
            驳回
          </BaseButton>
          <BaseButton
            variant="link"
            size="small"
            @click="goModule(row)"
          >
            详情
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- 快捷审核对话框（复用各模块后端 CAS 审核接口） -->
    <el-dialog
      v-model="reviewVisible"
      :title="reviewForm.approve ? '审核通过' : '驳回待办'"
      width="440px"
      @closed="reviewForm.remark = ''"
    >
      <div class="review-target">
        <el-tag
          :type="sourceMeta(reviewTodoItem.source)?.tagType"
          size="small"
        >
          {{ sourceMeta(reviewTodoItem.source)?.label }}
        </el-tag>
        <span class="review-title">{{ reviewTodoItem.title }}</span>
      </div>
      <el-form label-width="70px">
        <el-form-item :label="reviewForm.approve ? '审批意见' : '驳回原因'">
          <el-input
            v-model="reviewForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="reviewForm.approve ? '选填，不超过 200 字' : '必填，请说明驳回原因（提交人可见）'"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="reviewVisible = false">
          取消
        </BaseButton>
        <BaseButton
          :variant="reviewForm.approve ? 'primary' : 'danger'"
          :loading="reviewLoading"
          @click="confirmReview"
        >
          确定{{ reviewForm.approve ? '通过' : '驳回' }}
        </BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 审批待办中心
 *
 * 业务用途：聚合进度填报/工作量填报/文档上传/项目授权申请四类审核待办，
 * 提供统一入口（统计卡片 + 统一列表 + 快捷审核），免去审核人在四个模块间来回切换。
 *
 * 设计要点：
 *   - 后端按用户审核权限过滤来源（无权限的来源不返回），非管理员限定其负责项目；
 *   - 快捷审核复用各业务模块已有接口（approvalApi.reviewTodo 分发），
 *     后端 CAS 状态机/归属校验/通知联动零重复；
 *   - 「详情」跳转到对应业务模块页（携带上下文由各页面自行加载）。
 * 权限：四类审核权限任一即可见（路由 meta.permission 数组 = 任一满足）。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { approvalApi } from '@/api/approval'
import { feedback } from '@/utils/feedback'
import { formatDateTime } from '@/utils/format'
import { Odometer, DataLine, Document, Connection } from '@element-plus/icons-vue'
import CrudTable from '@/components/CrudTable.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'

defineOptions({ name: 'TodoCenter' })

const router = useRouter()

/** 来源元数据：标签/图标/配色/跳转路径（详情按钮用） */
const SOURCE_META = {
  progress: { label: '进度填报', tagType: 'primary', icon: Odometer, path: '/review/pending' },
  workload: { label: '工作量填报', tagType: 'warning', icon: DataLine, path: '/workload' },
  document: { label: '文档审核', tagType: 'info', icon: Document, path: '/documents' },
  auth: { label: '授权申请', tagType: 'success', icon: Connection, path: '/project-auth' }
}
const sourceMeta = (s) => SOURCE_META[s] || { label: s, tagType: 'info' }

/** 统计卡片配置（icon 引用组件对象，模板 <component :is> 直接解析） */
const statCards = [
  { key: 'progress', label: '进度填报', icon: Odometer, color: '#409EFF', bg: 'rgba(64,158,255,.1)' },
  { key: 'workload', label: '工作量填报', icon: DataLine, color: '#E6A23C', bg: 'rgba(230,162,60,.1)' },
  { key: 'document', label: '文档审核', icon: Document, color: '#909399', bg: 'rgba(144,147,153,.1)' },
  { key: 'auth', label: '授权申请', icon: Connection, color: '#67C23A', bg: 'rgba(103,194,58,.1)' }
]

/** 计数（统计卡片） */
const counts = reactive({ progress: 0, workload: 0, document: 0, auth: 0, total: 0 })
const lastUpdated = ref('')

/** 来源筛选（null = 全部）；点卡片切换 */
const activeSource = ref(null)

const fetchTodos = (params) => {
  const { source, ...rest } = params
  return approvalApi.listTodos({ ...rest, source: source ?? undefined })
}
const { list, total, loading, queryParams, fetchList } = useCrudList(fetchTodos, {
  source: null
})

/** 拉取分类计数（失败静默，卡片显示 0） */
const fetchCounts = async () => {
  try {
    const res = await approvalApi.getTodoCounts()
    Object.assign(counts, {
      progress: res.data?.progress || 0,
      workload: res.data?.workload || 0,
      document: res.data?.document || 0,
      auth: res.data?.auth || 0,
      total: res.data?.total || 0
    })
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (e) {
    console.error('[TodoCenter] 拉取待办计数失败:', e?.message)
  }
}

/** 切换来源筛选（同源再点一次取消） */
const switchSource = (key) => {
  activeSource.value = activeSource.value === key ? null : key
  queryParams.source = activeSource.value
  queryParams.pageNum = 1
  fetchList()
}

const refreshAll = () => {
  fetchList()
  fetchCounts()
}

/** 快捷审核 */
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewTodoItem = ref({})
const reviewForm = reactive({ approve: true, remark: '' })

const openReview = (row, approve) => {
  reviewTodoItem.value = row
  reviewForm.approve = approve
  reviewForm.remark = ''
  reviewVisible.value = true
}

const confirmReview = async () => {
  if (!reviewForm.approve && !reviewForm.remark?.trim()) {
    feedback.warning('驳回时必须填写原因（提交人可见）')
    return
  }
  reviewLoading.value = true
  try {
    await approvalApi.reviewTodo(reviewTodoItem.value, {
      approve: reviewForm.approve,
      remark: reviewForm.remark?.trim() || undefined
    })
    feedback.success(`已${reviewForm.approve ? '通过' : '驳回'}：${reviewTodoItem.value.title}`)
    reviewVisible.value = false
    refreshAll()
  } catch (e) {
    // request 拦截器已弹错误提示，这里仅记录
    console.error('[TodoCenter] 审核失败:', e?.message)
  } finally {
    reviewLoading.value = false
  }
}

/** 跳转到对应业务模块（完整审核上下文在各模块页内） */
const goModule = (row) => {
  router.push(sourceMeta(row.source).path)
}

onMounted(() => {
  fetchList()
  fetchCounts()
})

useKeepAliveRefresh(refreshAll)
</script>

<style scoped>
.todo-center {
  padding: 16px;
}

.stat-row {
  margin-bottom: 12px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.stat-card:hover,
.stat-card.active {
  border-color: var(--el-color-primary);
  /* 光晕 Token（Glow+Grain 铺开）：品牌蓝辉光替代浅色中性阴影 */
  box-shadow: var(--glow-primary);
}

.stat-card:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  flex-shrink: 0;
}

.stat-info {
  min-width: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

.stat-value.has-todo {
  color: var(--el-color-danger);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.total-card {
  justify-content: center;
  text-align: center;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.update-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.review-target {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.review-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
