<template>
  <!--
    文档上传对话框组件（P1-2.1 拆分自 DocumentManagement.vue）
    父组件通过 v-model:visible 控制显隐，通过 :categories / :projects 传入下拉选项，
    上传完成后触发 success 事件，由父组件刷新列表与分类计数。
  -->
  <el-dialog
    :model-value="visible"
    title="上传文档"
    width="500px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-upload
      aria-label="文件上传"
      drag
      multiple
      :auto-upload="false"
      :on-change="handleFileChange"
      :before-upload="beforeUpload"
      accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png,.gif,.dwg,.zip,.rar"
    >
      <el-icon class="el-icon--upload">
        <UploadFilled />
      </el-icon>
      <div class="el-upload__text">
        将文件拖到此处，或<em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持 PDF、Word、Excel、图片、DWG、ZIP 等格式，单个文件不超过 100MB
        </div>
      </template>
    </el-upload>
    <el-form
      :model="uploadForm"
      label-width="80px"
      style="margin-top: 16px"
    >
      <el-form-item label="分类">
        <el-select
          v-model="uploadForm.category"
          placeholder="请选择分类"
          style="width: 100%"
        >
          <el-option
            v-for="c in categories"
            :key="c"
            :label="c"
            :value="c"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="所属项目">
        <el-select
          v-model="uploadForm.projectId"
          placeholder="请选择项目"
          style="width: 100%"
        >
          <el-option
            v-for="p in projects"
            :key="p.id"
            :label="p.name"
            :value="p.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <BaseButton
        :disabled="uploading"
        @click="close"
      >
        取消
      </BaseButton>
      <BaseButton
        variant="primary"
        :loading="uploading"
        @click="confirmUpload"
      >
        {{ uploading ? '上传中...' : '确认上传' }}
      </BaseButton>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 文档上传对话框
 * 业务用途：支持多文件拖拽上传，统一选择分类与所属项目后批量调用上传接口。
 * 数据来源：documentStore.addDocument；分类与项目下拉由父组件传入。
 */
import { ref, watch } from 'vue'
import { feedback } from '@/utils/feedback'
import { UploadFilled } from '@element-plus/icons-vue'
import { useDocumentStore } from '@/stores/document'
// 注：BaseButton 已在 main.js 全局注册，无需在此导入

const props = defineProps({
  /** 对话框显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 文档分类选项（来自 @/constants/options） */
  categories: { type: Array, default: () => [] },
  /** 项目下拉列表（来自 projectStore.projects） */
  projects: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:visible', 'success'])

const documentStore = useDocumentStore()

const uploadForm = ref({ category: '', projectId: '' })
const uploadFileList = ref([])
const uploading = ref(false)

/** 对话框打开时重置表单与文件列表，避免上次残留 */
watch(() => props.visible, (val) => {
  if (val) {
    uploadForm.value = { category: '', projectId: '' }
    uploadFileList.value = []
  }
})

/** 关闭对话框 */
function close() {
  emit('update:visible', false)
}

/** 上传文件大小上限（字节）：文档库统一限制 100MB */
const MAX_FILE_SIZE = 100 * 1024 * 1024

/** 多选文件变化时的处理：过滤掉超过 100MB 的文件并更新待上传列表 */
function handleFileChange(file, fileList) {
  // 前端文件大小校验（100MB）
  const validFiles = fileList.filter(f => {
    if (f.size && f.size > MAX_FILE_SIZE) {
      feedback.warning(`文件 "${f.name}" 超过 100MB，已自动移除`)
      return false
    }
    return true
  })
  uploadFileList.value = validFiles
}

/** 上传前校验（el-upload 的 before-upload 钩子） */
function beforeUpload(file) {
  if (file.size > MAX_FILE_SIZE) {
    feedback.error(`文件 "${file.name}" 超过 100MB 限制`)
    return false
  }
  return true
}

/** 确认上传：循环调用单文件接口，统计成功/失败数量并提示 */
async function confirmUpload() {
  if (!uploadForm.value.category) {
    feedback.warning('请选择文档分类')
    return
  }
  if (uploadFileList.value.length === 0) {
    feedback.warning('请选择要上传的文件')
    return
  }

  // 批量上传：循环调用单文件接口
  const files = uploadFileList.value
  const total = files.length
  let successCount = 0
  let failCount = 0

  // 上传中状态（禁用按钮、显示进度）
  uploading.value = true

  for (let i = 0; i < total; i++) {
    const file = files[i]
    const formData = new FormData()
    formData.append('file', file.raw)
    formData.append('projectId', uploadForm.value.projectId || '')
    formData.append('category', uploadForm.value.category)
    try {
      await documentStore.addDocument(formData)
      successCount++
    } catch (error) {
      failCount++
      console.error(`文件 ${file.name} 上传失败:`, error)
    }
  }

  uploading.value = false

  // 上传结果提示
  if (failCount === 0) {
    feedback.success(`成功上传 ${successCount} 个文件`)
  } else if (successCount === 0) {
    feedback.error(`全部 ${failCount} 个文件上传失败`)
  } else {
    feedback.warning(`上传完成：成功 ${successCount} 个，失败 ${failCount} 个`)
  }

  // 全部失败则保留文件列表便于重试
  if (successCount > 0) {
    emit('update:visible', false)
    emit('success')
  }
}
</script>
