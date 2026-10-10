<template>
  <!--
    进度审核对话框（P1-2.1 拆分自 ProjectProgress.vue）
    父组件通过 :visible 控制显隐，:log 传入待审核日志对象。
    审核提交后 emit('reviewed')，父组件拉取下一条待审核日志并更新 :log；
    若已无待审核日志，父组件关闭对话框。
  -->
  <el-dialog
    :model-value="visible"
    title="进度审核"
    width="720px"
    top="5vh"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form
      :model="reviewForm"
      label-width="100px"
    >
      <el-descriptions
        :column="2"
        border
        size="small"
        style="margin-bottom: 16px"
      >
        <el-descriptions-item label="项目">
          {{ reviewForm.projectName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="任务">
          {{ reviewForm.taskName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="填报人">
          {{ reviewForm.reporterName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="填报时间">
          {{ formatDateTime(reviewForm.reportTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="原进度">
          {{ reviewForm.previousProgress ?? 0 }}%
        </el-descriptions-item>
        <el-descriptions-item label="本次进度">
          {{ reviewForm.progress ?? 0 }}%
        </el-descriptions-item>
        <el-descriptions-item label="实际开工时间">
          {{ reviewForm.actualStartDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="实际结束时间">
          {{ reviewForm.actualEndDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="填报备注">
          {{ reviewForm.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form-item
        v-if="reviewForm.images && reviewForm.images.length"
        label="施工图片"
      >
        <div class="review-images">
          <el-image
            v-for="(img, idx) in reviewForm.images"
            :key="img + '-' + idx"
            :src="resolveImageUrl(img)"
            :alt="`施工图片${idx + 1}`"
            :preview-src-list="reviewForm.images.map(i => resolveImageUrl(i))"
            :initial-index="idx"
            fit="cover"
            class="review-image-item"
            :preview-teleported="true"
          />
        </div>
      </el-form-item>
      <el-form-item label="审核结果">
        <el-radio-group v-model="reviewForm.approve">
          <el-radio :value="true">
            通过
          </el-radio>
          <el-radio :value="false">
            驳回
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="审核意见">
        <el-input
          v-model="reviewForm.reviewRemark"
          type="textarea"
          :rows="3"
          placeholder="请输入审核意见（可选）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <BaseButton @click="$emit('update:visible', false)">
        取消
      </BaseButton>
      <BaseButton
        variant="primary"
        :loading="reviewSubmitting"
        @click="handleReview"
      >
        提交审核
      </BaseButton>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 进度审核对话框
 *
 * 业务用途：项目经理审核工程师提交的进度填报，可选通过/驳回，填写审核意见。
 * 数据来源：log 由父组件传入（待审核日志对象），父组件在审核完成后更新为下一条。
 * 副作用：提交审核后调用 progressStore.reviewProgress，并 emit('reviewed') 通知父组件拉取下一条。
 *
 * P1-2.1：从 ProjectProgress.vue 抽取，含审核表单、图片预览与操作日志记录。
 */
// 注：BaseButton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, watch } from 'vue'
import { useProgressStore } from '@/stores/progress'
import { useUserStore } from '@/stores/user'
import { feedback } from '@/utils/feedback'
import { formatDateTime } from '@/utils/format'
import { resolveFileUrl } from '@/utils/fileUrl'

const props = defineProps({
  /** 对话框显隐 */
  visible: { type: Boolean, default: false },
  /** 待审核日志对象（由父组件在审核完成后更新为下一条） */
  log: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'reviewed'])

const progressStore = useProgressStore()
const userStore = useUserStore()

const reviewSubmitting = ref(false)

// 审核表单
const reviewForm = reactive({
  logId: null,
  projectName: '',
  taskName: '',
  reporterName: '',
  reportTime: '',
  previousProgress: 0,
  progress: 0,
  remark: '',
  images: [],
  actualStartDate: '',
  actualEndDate: '',
  approve: true,
  reviewRemark: ''
})

/**
 * 监听 log prop 变化：日志对象更新时回填审核表单
 * 父组件在审核完成后会更新 currentReviewLog 为下一条，触发本 watch 更新表单内容。
 */
watch(() => props.log, (log) => {
  if (!log) return
  reviewForm.logId = log.id
  reviewForm.projectName = log.projectName
  reviewForm.taskName = log.taskName
  reviewForm.reporterName = log.reporterName
  reviewForm.reportTime = log.reportTime
  reviewForm.previousProgress = log.previousProgress
  reviewForm.progress = log.progress
  reviewForm.remark = log.remark
  reviewForm.images = log.images || []
  reviewForm.actualStartDate = log.actualStartDate || ''
  reviewForm.actualEndDate = log.actualEndDate || ''
  reviewForm.approve = true
  reviewForm.reviewRemark = ''
}, { immediate: true })

// P3 重构：formatDateTime / 图片 URL 解析分别改用 utils/format 与
// utils/fileUrl 统一实现，别名保持模板引用不变
const resolveImageUrl = resolveFileUrl

/** 提交审核：调用审核接口，成功后通知父组件拉取下一条待审核日志 */
async function handleReview() {
  if (!reviewForm.logId) {
    feedback.warning('未选择待审核日志')
    return
  }
  const userInfo = userStore.userInfo || {}
  const operationType = reviewForm.approve ? '通过' : '驳回'
  const startTime = new Date()
  reviewSubmitting.value = true
  try {
    await progressStore.reviewProgress(reviewForm.logId, {
      approve: reviewForm.approve,
      remark: reviewForm.reviewRemark
    })
    emit('reviewed')
  } catch (error) {
    // 失败日志：记录错误信息便于排查
    const endTime = new Date()
    console.error('[进度审核][前端-失败]', {
      logId: reviewForm.logId,
      operation: operationType,
      reviewerId: userInfo.id,
      reviewerName: userInfo.username,
      clientStartTime: formatDateTime(startTime),
      clientEndTime: formatDateTime(endTime),
      costMs: endTime - startTime,
      errorMessage: error?.message || String(error),
      errorCode: error?.response?.data?.code,
      errorMsg: error?.response?.data?.message,
      httpStatus: error?.response?.status
    })
  } finally {
    reviewSubmitting.value = false
  }
}
</script>

<style scoped>
.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-image-item {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  cursor: pointer;
}
</style>
