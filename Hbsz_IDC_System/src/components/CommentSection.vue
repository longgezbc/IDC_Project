<template>
  <div class="comment-section">
    <div class="comment-header">
      <span class="comment-title">评论 ({{ total }})</span>
    </div>

    <!-- 评论输入 -->
    <div class="comment-input-area">
      <el-input
        v-model="newComment"
        type="textarea"
        :rows="2"
        :placeholder="placeholder"
        maxlength="500"
        show-word-limit
      />
      <div class="comment-input-actions">
        <span class="mention-hint">输入 @用户名 可提及并通知对方</span>
        <BaseButton variant="primary" size="small" :loading="submitting" :disabled="!newComment.trim()" @click="handleSubmit">
          发表评论
        </BaseButton>
      </div>
    </div>

    <!-- 评论列表 -->
    <div class="comment-list">
      <div v-for="c in comments" :key="c.id" class="comment-item">
        <el-avatar :size="32" class="comment-avatar">{{ c.authorName?.charAt(0) || '?' }}</el-avatar>
        <div class="comment-body">
          <div class="comment-meta">
            <span class="comment-author">{{ c.authorName }}</span>
            <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
          </div>
          <div class="comment-content" v-html="renderContent(c.content)"></div>
        </div>
      </div>
      <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="60" />
    </div>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="comment-pagination">
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" layout="prev, pager, next" small @current-change="fetchList" />
    </div>
  </div>
</template>

<script setup>
/**
 * 通用评论组件（含 @提及）
 *
 * 业务用途：嵌入项目详情/文档详情等页面，提供评论列表与发表评论能力。
 *          输入 @用户名 后后端自动解析并发送提及通知。
 * 数据来源：commentApi.getList / commentApi.create
 */
import { ref, onMounted, watch } from 'vue'
import { commentApi } from '@/api/comment'
import { feedback } from '@/utils/feedback'

const props = defineProps({
  /** 业务类型：project/document/task */
  bizType: { type: String, required: true },
  /** 业务对象ID */
  bizId: { type: [Number, String], required: true },
  /** 占位提示 */
  placeholder: { type: String, default: '发表评论...（@用户名 提及）' }
})

const comments = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const newComment = ref('')
const submitting = ref(false)

async function fetchList() {
  try {
    const res = await commentApi.getList(props.bizType, props.bizId, pageNum.value, pageSize.value)
    // request 拦截器返回完整响应包 {code,message,data}，分页数据在 data 字段
    const data = res.data || {}
    comments.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    console.error('[评论] 查询失败', e)
  }
}

async function handleSubmit() {
  if (!newComment.value.trim()) return
  submitting.value = true
  try {
    await commentApi.create({
      bizType: props.bizType,
      bizId: Number(props.bizId),
      content: newComment.value.trim()
    })
    newComment.value = ''
    feedback.success('评论成功')
    fetchList()
  } catch (e) {
    feedback.error(e.message || '评论失败')
  } finally {
    submitting.value = false
  }
}

/** 将 @用户名 高亮渲染 */
function renderContent(content) {
  if (!content) return ''
  // 转义 HTML 防 XSS
  const escaped = content.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  // @用户名 高亮
  return escaped.replace(/@([\u4e00-\u9fa5\w]+)/g, '<span class="mention-tag">@$1</span>')
}

function formatTime(t) {
  if (!t) return ''
  return typeof t === 'string' ? t.replace('T', ' ').substring(0, 16) : ''
}

watch(() => props.bizId, () => { pageNum.value = 1; fetchList() })
onMounted(fetchList)
</script>

<style scoped>
.comment-section { padding: 0; }
.comment-header { margin-bottom: 12px; }
.comment-title { font-size: 15px; font-weight: 600; color: #303133; }
.comment-input-area { margin-bottom: 16px; }
.comment-input-actions { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.mention-hint { font-size: 12px; color: #909399; }
.comment-list { }
.comment-item { display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.comment-avatar { flex-shrink: 0; background: #1890ff; color: #fff; font-size: 13px; }
.comment-body { flex: 1; }
.comment-meta { display: flex; gap: 10px; align-items: center; margin-bottom: 4px; }
.comment-author { font-size: 13px; font-weight: 600; color: #303133; }
.comment-time { font-size: 12px; color: #909399; }
.comment-content { font-size: 14px; color: #303133; line-height: 1.6; word-break: break-word; }
.comment-content :deep(.mention-tag) { color: #1890ff; background: #e6f7ff; padding: 0 4px; border-radius: 2px; font-weight: 500; }
.comment-pagination { margin-top: 12px; display: flex; justify-content: center; }
</style>
