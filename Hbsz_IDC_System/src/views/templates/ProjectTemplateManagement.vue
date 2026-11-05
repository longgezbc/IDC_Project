<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">项目模板管理</div>
        <div class="page-subtitle">创建和管理可复用的项目模板，含结构化配置</div>
      </div>
      <div class="header-actions">
        <BaseButton v-if="canEdit" variant="primary" icon-name="Plus" @click="openCreate">新建模板</BaseButton>
      </div>
    </div>

    <div class="card-box">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="名称">
          <el-input v-model="searchForm.keyword" placeholder="模板名称/编号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="全部分类" clearable style="width:160px">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <BaseButton variant="primary" icon-name="Search" @click="handleSearch">查询</BaseButton>
          <BaseButton icon-name="Refresh" @click="resetSearch">重置</BaseButton>
        </el-form-item>
      </el-form>
    </div>

    <CrudTable v-model:current-page="currentPage" v-model:page-size="pageSize" :data="list" :loading="loading" :total="total">
      <template #toolbar>
        <div class="toolbar-left"><span class="page-total-text">共 {{ total }} 个模板</span></div>
      </template>
      <el-table-column prop="code" label="编号" width="140" />
      <el-table-column prop="name" label="模板名称" min-width="200" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <BaseButton variant="link" size="small" @click="openEdit(row)">编辑</BaseButton>
          <BaseButton variant="link-danger" size="small" @click="handleDelete(row)">删除</BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模板' : '新建模板'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编号" prop="code">
          <el-input v-model="form.code" placeholder="如 STD-LAYOUT-001" />
        </el-form-item>
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="如 标准综合布线项目" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="选择分类" allow-create filterable style="width:100%">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="模板用途说明" />
        </el-form-item>
        <el-form-item label="配置JSON">
          <el-input v-model="form.configJson" type="textarea" :rows="8" placeholder='{"stages":[...],"documents":[...],"defaultFields":{...}}' />
          <div class="form-tip">结构化配置：默认字段值、阶段定义、文档清单、岗位职责等</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="dialogVisible = false">取消</BaseButton>
        <BaseButton variant="primary" :loading="submitting" @click="handleSubmit">确定</BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { projectTemplateApi } from '@/api/projectTemplate'
import { hasPermission } from '@/utils/permission'
import { feedback } from '@/utils/feedback'
import CrudTable from '@/components/CrudTable.vue'

const canEdit = hasPermission('project:add')

const list = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchForm = reactive({ keyword: '', category: '' })
const categoryOptions = ref(['标准布线', '机房改造', '网络升级', '其他'])

const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ code: '', name: '', category: '', description: '', configJson: '', status: 1 })
const rules = {
  code: [{ required: true, message: '请输入编号', trigger: 'blur' }],
  // 结构化配置须为合法 JSON 对象/数组（留空表示无配置）
  configJson: [
    {
      validator: (rule, value, callback) => {
        const text = (value || '').trim()
        if (!text) return callback()
        try {
          const parsed = JSON.parse(text)
          if (parsed === null || typeof parsed !== 'object') {
            return callback(new Error('须为 JSON 对象或数组（如 {"stages": [...]}）'))
          }
          callback()
        } catch (e) {
          callback(new Error('不是合法的 JSON 格式'))
        }
      },
      trigger: 'blur'
    }
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    // request 拦截器返回完整响应包 {code,message,data}，分页数据在 data 字段
    const res = await projectTemplateApi.getList({ pageNum: currentPage.value, pageSize: pageSize.value, keyword: searchForm.keyword, category: searchForm.category })
    const data = res.data || {}
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    feedback.error(e.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() { currentPage.value = 1; fetchList() }
function resetSearch() { searchForm.keyword = ''; searchForm.category = ''; handleSearch() }

function openCreate() {
  editingId.value = null
  Object.assign(form, { code: '', name: '', category: '', description: '', configJson: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, { code: row.code, name: row.name, category: row.category, description: row.description, configJson: row.configJson || '', status: row.status })
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await projectTemplateApi.update(editingId.value, { ...form })
      feedback.success('更新成功')
    } else {
      await projectTemplateApi.create({ ...form })
      feedback.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    feedback.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await feedback.confirmDelete(`确认删除模板「${row.name}」？`)
    await projectTemplateApi.remove(row.id)
    feedback.success('删除成功')
    fetchList()
  } catch (e) { /* cancel */ }
}

onMounted(fetchList)
</script>

<style scoped>
.page-total-text { font-size: 13px; color: #909399; }
.form-tip { font-size: 12px; color: #909399; line-height: 1.4; margin-top: 4px; }
</style>
