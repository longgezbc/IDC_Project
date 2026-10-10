<template>
  <div class="position-management">
    <!-- P1-2.2：使用 CrudTable 通用布局组件，工具栏通过 #toolbar 插槽传入，列定义通过默认插槽传入 -->
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
          <BaseButton
            v-if="hasPermission('position:add')"
            variant="primary"
            icon-name="Plus"
            @click="handleAdd"
          >
            新增岗位
          </BaseButton>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="queryParams.code"
            placeholder="岗位编码"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model="queryParams.name"
            placeholder="岗位名称"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="queryParams.status"
            placeholder="状态"
            clearable
            style="width: 110px"
          >
            <el-option
              label="启用"
              :value="1"
            />
            <el-option
              label="禁用"
              :value="0"
            />
          </el-select>
          <BaseButton
            variant="primary"
            icon-name="Search"
            @click="handleSearch"
          >
            查询
          </BaseButton>
          <BaseButton
            variant="plain"
            icon-name="Refresh"
            @click="handleReset"
          >
            重置
          </BaseButton>
        </div>
      </template>

      <el-table-column
        type="index"
        label="#"
        width="50"
        align="center"
      />
      <el-table-column
        prop="code"
        label="岗位编码"
        width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="name"
        label="岗位名称"
        width="120"
      />
      <el-table-column
        label="职级"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="levelTagType(row.level)"
            size="small"
          >
            L{{ row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="description"
        label="描述"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column
        label="关联用户"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            type="info"
            size="small"
          >
            {{ row.userCount || 0 }} 人
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.status === 1 ? 'success' : 'danger'"
            size="small"
          >
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        label="创建时间"
        min-width="160"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            v-if="hasPermission('position:edit')"
            variant="link"
            size="small"
            icon-name="Edit"
            @click="handleEdit(row)"
          >
            编辑
          </BaseButton>
          <BaseButton
            v-if="hasPermission('position:remove')"
            variant="link-danger"
            size="small"
            icon-name="Delete"
            @click="handleDelete(row)"
          >
            删除
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="showForm"
      :title="isEdit ? '编辑岗位' : '新增岗位'"
      width="520px"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item
          label="岗位编码"
          prop="code"
        >
          <el-input
            v-model="formData.code"
            placeholder="如 pm/engineer/viewer（2-50 字符）"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item
          label="岗位名称"
          prop="name"
        >
          <el-input
            v-model="formData.name"
            placeholder="如 项目经理 / 工程师"
          />
        </el-form-item>
        <el-form-item
          label="职级"
          prop="level"
        >
          <el-input-number
            v-model="formData.level"
            :min="1"
            :max="9"
            controls-position="right"
          />
          <span class="form-tip">数字越小职级越高（1 最高，9 最低）</span>
        </el-form-item>
        <el-form-item
          label="岗位描述"
          prop="description"
        >
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="选填，不超过 200 字"
          />
        </el-form-item>
        <el-form-item
          label="状态"
          prop="status"
        >
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">
              启用
            </el-radio>
            <el-radio :value="0">
              禁用
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showForm = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="submitLoading"
          @click="confirmForm"
        >
          确定
        </BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 岗位管理
 *
 * 业务用途：维护系统岗位字典（编码/名称/职级/状态），作为用户关联岗位的数据源。
 * 主要功能区：工具栏（新增 + 搜索）+ 列表（职级标签/状态标签/操作）+ 新增/编辑对话框。
 *
 * P1-2.2：使用 useCrudList 组合式函数封装列表数据状态与 fetch/search/reset 逻辑，
 *        使用 CrudTable 通用布局组件封装 el-card + toolbar + el-table + 分页的模板与 CSS，
 *        本组件仅保留岗位特有的列定义、表单状态与增删改业务逻辑。
 */
// 注：BaseButton / TableSkeleton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, onMounted } from 'vue'
import { feedback } from '@/utils/feedback'
import { positionApi } from '@/api/position'
import { hasPermission } from '@/utils/permission'
import { formatDateTime } from '@/utils/format'
// P1-2.2：通用列表布局组件 + 通用 CRUD 组合式函数
import CrudTable from '@/components/CrudTable.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'

defineOptions({ name: 'PositionManagement' })

// P1-2.2：列表数据状态与 fetch/search/reset 由 useCrudList 统一管理
const { list, total, loading, queryParams, fetchList, handleSearch, handleReset } = useCrudList(
  positionApi.query,
  { code: '', name: '', status: undefined }
)

// 表单状态（新增/编辑共用，页面特有）
const showForm = ref(false)
const isEdit = ref(false)
const formRef = ref()
const submitLoading = ref(false)
const formData = reactive({ id: null, code: '', name: '', level: 4, description: '', status: 1 })
const formRules = {
  code: [
    { required: true, message: '请输入岗位编码', trigger: 'blur' },
    { min: 2, max: 50, message: '长度需在 2-50 个字符之间', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  level: [{ required: true, message: '请选择职级', trigger: 'change' }]
}

function levelTagType(level) {
  if (level <= 2) return 'danger'
  if (level <= 4) return 'warning'
  if (level <= 6) return 'primary'
  return 'info'
}

function resetForm() {
  isEdit.value = false
  Object.assign(formData, { id: null, code: '', name: '', level: 4, description: '', status: 1 })
  formRef.value?.clearValidate()
}

function handleAdd() {
  resetForm()
  showForm.value = true
}

function handleEdit(row) {
  resetForm()
  isEdit.value = true
  Object.assign(formData, {
    id: row.id,
    code: row.code,
    name: row.name,
    level: row.level,
    description: row.description || '',
    status: row.status
  })
  showForm.value = true
}

async function confirmForm() {
  // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致 submitLoading 提前释放、防重复提交失效。改为 try-await 模式。
  try {
    await formRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await positionApi.update(formData.id, {
        code: formData.code,
        name: formData.name,
        level: formData.level,
        description: formData.description,
        status: formData.status
      })
      feedback.success('修改岗位成功')
    } else {
      await positionApi.create({
        code: formData.code,
        name: formData.name,
        level: formData.level,
        description: formData.description,
        status: formData.status
      })
      feedback.success('新增岗位成功')
    }
    showForm.value = false
    fetchList()
  } catch (e) {
    console.error('保存岗位失败', e)
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  const userCount = row.userCount || 0
  const tip = userCount > 0
    ? `岗位「${row.name}」当前关联 ${userCount} 个用户，删除后这些用户的岗位将变为未设置。确认删除吗？`
    : `确认删除岗位「${row.name}」吗？`
  // P1 修复：ElMessageBox.confirm 用户点"取消"时会 reject，未捕获会产生 unhandled rejection
  try {
    await feedback.confirmDelete(tip)
  } catch {
    return // 用户取消删除
  }
  try {
    await positionApi.remove(row.id)
    feedback.success('删除岗位成功')
    fetchList()
  } catch (e) {
    console.error('删除岗位失败', e)
  }
}

// 进入页面加载列表；keep-alive 重新激活时刷新（首次激活自动跳过，避免请求双发）
onMounted(fetchList)
useKeepAliveRefresh(() => fetchList())
</script>

<style scoped>
.position-management {
  padding: 16px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.form-tip {
  margin-left: 10px;
  color: #999;
  font-size: 12px;
}
</style>
