<template>
  <!--
    文档编辑对话框组件（P1-2.1 拆分自 DocumentManagement.vue）
    父组件通过 v-model:visible 控制显隐，通过 :doc 传入待编辑文档；
    通过 :categories / :projects 传入下拉选项；保存成功后触发 success 事件。
  -->
  <el-dialog
    :model-value="visible"
    title="编辑文档"
    width="560px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form
      ref="editFormRef"
      :model="editForm"
      :rules="editRules"
      label-width="90px"
    >
      <el-form-item
        label="文档名称"
        prop="name"
      >
        <el-input
          v-model="editForm.name"
          placeholder="请输入文档名称"
        />
      </el-form-item>
      <el-form-item
        label="分类"
        prop="category"
      >
        <el-select
          v-model="editForm.category"
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
      <el-form-item
        label="所属项目"
        prop="projectId"
      >
        <el-select
          v-model="editForm.projectId"
          placeholder="请选择项目"
          filterable
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
      <el-form-item
        label="版本号"
        prop="version"
      >
        <el-input
          v-model="editForm.version"
          placeholder="例如：1.0.0"
        />
      </el-form-item>
      <el-form-item
        label="标签"
        prop="tags"
      >
        <el-input
          v-model="editForm.tagsInput"
          placeholder="多个标签用逗号分隔"
        />
      </el-form-item>
      <el-form-item
        label="描述"
        prop="description"
      >
        <el-input
          v-model="editForm.description"
          type="textarea"
          :rows="3"
          placeholder="请输入文档描述"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <BaseButton @click="close">
        取消
      </BaseButton>
      <BaseButton
        variant="primary"
        @click="confirmEdit"
      >
        保存修改
      </BaseButton>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 文档编辑对话框
 * 业务用途：编辑文档的名称、分类、所属项目、版本号、标签、描述等元信息。
 * 数据来源：documentStore.updateDocument；分类与项目下拉由父组件传入。
 */
import { ref, watch } from 'vue'
import { useDocumentStore } from '@/stores/document'
// 注：BaseButton 已在 main.js 全局注册，无需在此导入

const props = defineProps({
  /** 对话框显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 待编辑的文档对象（父组件已通过 fetchDetail 拉取完整字段） */
  doc: { type: Object, default: null },
  /** 文档分类选项 */
  categories: { type: Array, default: () => [] },
  /** 项目下拉列表 */
  projects: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:visible', 'success'])

const documentStore = useDocumentStore()

const editFormRef = ref()
const editForm = ref({
  id: null,
  name: '',
  category: '',
  projectId: '',
  version: '',
  tagsInput: '',
  description: ''
})
const editRules = {
  name: [{ required: true, message: '请输入文档名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }]
}

/** 文档变化时填充编辑表单（标签数组转逗号分隔字符串） */
watch(() => props.doc, (doc) => {
  if (!doc) return
  editForm.value = {
    id: doc.id,
    name: doc.name || '',
    category: doc.category || '',
    projectId: doc.projectId || '',
    version: doc.version || '',
    tagsInput: Array.isArray(doc.tags) ? doc.tags.join(',') : (doc.tags || ''),
    description: doc.description || ''
  }
}, { immediate: true })

/** 关闭对话框 */
function close() {
  emit('update:visible', false)
}

/** 确认编辑保存 */
async function confirmEdit() {
  if (!editFormRef.value) return
  // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致 loading 状态失效、防重复提交失效。改为 try-await 模式。
  try {
    await editFormRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  try {
    // 构造更新数据，将标签字符串转为数组
    const tags = editForm.value.tagsInput
      ? editForm.value.tagsInput.split(',').map(t => t.trim()).filter(Boolean)
      : []
    const payload = {
      name: editForm.value.name,
      category: editForm.value.category,
      projectId: editForm.value.projectId,
      version: editForm.value.version,
      tags,
      description: editForm.value.description
    }
    await documentStore.updateDocument(editForm.value.id, payload)
    emit('update:visible', false)
    emit('success')
  } catch (error) {
    console.error('更新文档失败:', error)
  }
}
</script>
