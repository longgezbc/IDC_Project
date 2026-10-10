<template>
  <!--
    文档审核对话框组件（P1-2.1 拆分自 DocumentManagement.vue）
    父组件通过 v-model:visible 控制显隐，通过 :doc 传入待审核文档；
    审核提交后触发 success 事件，由父组件刷新列表。
  -->
  <el-dialog
    :model-value="visible"
    title="文档审核"
    width="500px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form
      :model="reviewForm"
      label-width="80px"
    >
      <el-form-item label="文档名称">
        <span>{{ reviewForm.docName }}</span>
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
          v-model="reviewForm.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入审核意见（可选）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <BaseButton @click="close">
        取消
      </BaseButton>
      <BaseButton
        variant="primary"
        :loading="reviewSubmitting"
        @click="confirmReview"
      >
        确认审核
      </BaseButton>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 文档审核对话框
 * 业务用途：对状态为「待审核」的文档进行通过/驳回审核，可填写审核意见。
 * 数据来源：documentApi.review 接口。
 */
import { ref, watch } from 'vue'
import { documentApi } from '@/api/document'
import { feedback } from '@/utils/feedback'
// 注：BaseButton 已在 main.js 全局注册，无需在此导入

const props = defineProps({
  /** 对话框显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 待审核的文档对象（包含 id、name） */
  doc: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'success'])

const reviewSubmitting = ref(false)
const reviewForm = ref({ docId: null, docName: '', approve: true, remark: '' })

/** 文档变化时重置审核表单（默认通过、无意见） */
watch(() => props.doc, (doc) => {
  if (doc) {
    reviewForm.value = {
      docId: doc.id,
      docName: doc.name,
      approve: true,
      remark: ''
    }
  }
}, { immediate: true })

/** 关闭对话框 */
function close() {
  emit('update:visible', false)
}

/** 确认审核：调用审核接口并通知父组件刷新 */
async function confirmReview() {
  reviewSubmitting.value = true
  try {
    await documentApi.review(reviewForm.value.docId, {
      approve: reviewForm.value.approve,
      remark: reviewForm.value.remark
    })
    emit('update:visible', false)
    emit('success')
    feedback.success(reviewForm.value.approve ? '审核通过' : '已驳回')
  } catch (error) {
    console.error('文档审核失败:', error)
  } finally {
    reviewSubmitting.value = false
  }
}
</script>
