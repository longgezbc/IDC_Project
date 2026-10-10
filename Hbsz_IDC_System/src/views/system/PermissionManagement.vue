<template>
  <div class="permission-management">
    <!-- P1-2.2：使用 CrudTable 通用布局组件（封装 el-card + toolbar + el-table）。
         本页为树形权限表格（无分页），通过 row-key / tree-props / default-expand-all 配置树形展开，
         关闭 stripe（树形表格父子行交替色影响层级阅读），并使用 :show-pagination="false" 隐藏分页器。 -->
    <CrudTable
      :data="systemStore.permissionTree"
      :loading="systemStore.permissionLoading"
      :show-pagination="false"
      :stripe="false"
      row-key="id"
      :tree-props="{ children: 'children' }"
      :default-expand-all="true"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <BaseButton
            variant="primary"
            icon-name="Plus"
            @click="handleAdd(null)"
          >
            新增权限
          </BaseButton>
        </div>
        <div class="toolbar-right">
          <BaseButton
            variant="plain"
            icon-name="Refresh"
            @click="fetchList"
          >
            刷新
          </BaseButton>
        </div>
      </template>

      <el-table-column
        prop="permissionName"
        label="权限名称"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="permissionCode"
        label="权限标识"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="类型"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="permTypeTag(row.permissionType)"
          >
            {{ permTypeText(row.permissionType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="path"
        label="路由路径"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="component"
        label="组件路径"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="icon"
        label="图标"
        width="100"
      />
      <el-table-column
        prop="sort"
        label="排序"
        width="80"
        align="center"
      />
      <el-table-column
        label="可见"
        width="80"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.isVisible === 1 ? 'success' : 'info'"
            size="small"
          >
            {{ row.isVisible === 1 ? '是' : '否' }}
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
        label="操作"
        width="240"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            icon-name="Plus"
            @click="handleAdd(row)"
          >
            新增子权限
          </BaseButton>
          <BaseButton
            variant="link"
            size="small"
            icon-name="Edit"
            @click="handleEdit(row)"
          >
            编辑
          </BaseButton>
          <BaseButton
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
      :title="isEdit ? '编辑权限' : '新增权限'"
      width="640px"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="父权限">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentTreeOptions"
            :props="{ label: 'permissionName', children: 'children', value: 'id' }"
            check-strictly
            default-expand-all
            placeholder="顶级权限（不选）"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              label="权限名称"
              prop="permissionName"
            >
              <el-input
                v-model="formData.permissionName"
                placeholder="如：用户管理"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="权限标识"
              prop="permissionCode"
            >
              <el-input
                v-model="formData.permissionCode"
                placeholder="如：system:user"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              label="权限类型"
              prop="permissionType"
            >
              <el-select
                v-model="formData.permissionType"
                placeholder="请选择"
                style="width: 100%"
              >
                <el-option
                  label="菜单"
                  :value="1"
                />
                <el-option
                  label="按钮"
                  :value="2"
                />
                <el-option
                  label="接口"
                  :value="3"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图标">
              <el-input
                v-model="formData.icon"
                placeholder="菜单图标"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="路由路径">
              <el-input
                v-model="formData.path"
                placeholder="如：/system/users"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组件路径">
              <el-input
                v-model="formData.component"
                placeholder="如：system/UserManagement"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number
                v-model="formData.sort"
                :min="0"
                :max="999"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否可见">
              <el-radio-group v-model="formData.isVisible">
                <el-radio :value="1">
                  是
                </el-radio>
                <el-radio :value="0">
                  否
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">
              启用
            </el-radio>
            <el-radio :value="0">
              禁用
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="权限说明"
          />
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
 * 权限管理页
 * 业务用途：以树形结构维护系统权限（菜单/按钮/接口），支持新增、编辑、删除权限节点，
 *          为角色授权提供基础权限数据。属于「系统管理」模块。
 * 主要功能区：顶部工具栏（新增/刷新）、权限树表格（展示层级与类型/可见/状态），
 *            以及新增/编辑权限对话框（含父权限选择、类型、路由与组件路径等）。
 * 数据来源：systemStore（权限树查询、增删改接口及加载状态）。
 *
 * P1-2.2：使用 CrudTable 通用布局组件（封装 el-card + toolbar + el-table）。
 *         本页为树形表格（无分页），通过 row-key / tree-props / default-expand-all 配置树形展开，
 *         关闭 stripe（树形表格父子行交替色影响层级阅读），并使用 :show-pagination="false" 隐藏分页器。
 */
// 注：BaseButton / TableSkeleton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, computed, onMounted } from 'vue'
import { feedback } from '@/utils/feedback'
import { useSystemStore } from '@/stores/system'
// P1-2.2：通用列表布局组件（支持树形表格）
import CrudTable from '@/components/CrudTable.vue'

const systemStore = useSystemStore() // 系统管理仓库：权限树数据与增删改接口

/* ============ 查询 ============ */
/** 拉取权限树数据（顶级权限及其子权限的树形结构） */
async function fetchList() {
  try {
    await systemStore.fetchPermissionTree()
  } catch (error) {
    console.error('查询权限树失败:', error)
  }
}

/** 父权限下拉选项（添加一个"顶级"虚拟节点） */
const parentTreeOptions = computed(() => {
  return systemStore.permissionTree
})

/* ============ 新增/编辑 ============ */
const showForm = ref(false)       // 新增/编辑对话框显隐
const isEdit = ref(false)         // 当前对话框是否为编辑模式（false 为新增）
const formRef = ref()             // 表单引用，用于校验与重置
const submitLoading = ref(false)  // 保存按钮加载状态
// 权限表单数据（新增/编辑共用）
const formData = reactive({
  id: null,
  parentId: null,
  permissionName: '',
  permissionCode: '',
  permissionType: 1,
  path: '',
  component: '',
  icon: '',
  sort: 0,
  isVisible: 1,
  status: 1,
  remark: ''
})

const formRules = {
  permissionName: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  permissionCode: [{ required: true, message: '请输入权限标识', trigger: 'blur' }],
  permissionType: [{ required: true, message: '请选择权限类型', trigger: 'change' }]
}

/** 重置表单为初始值并退出编辑模式 */
function resetForm() {
  Object.assign(formData, {
    id: null,
    parentId: null,
    permissionName: '',
    permissionCode: '',
    permissionType: 1,
    path: '',
    component: '',
    icon: '',
    sort: 0,
    isVisible: 1,
    status: 1,
    remark: ''
  })
  isEdit.value = false
  formRef.value?.resetFields()
}

/**
 * 打开新增权限对话框
 * @param {Object|null} row 传入行数据时表示为该权限新增子权限（自动带入 parentId 并默认按钮类型）；传 null 为新增顶级权限
 */
function handleAdd(row) {
  resetForm()
  // 如果传入row，表示新增子权限，设置parentId
  if (row) {
    formData.parentId = row.id
    formData.permissionType = 2 // 子权限默认按钮类型
  }
  showForm.value = true
}

/**
 * 打开编辑权限对话框并回显数据
 * @param {Object} row 待编辑的权限节点
 */
function handleEdit(row) {
  resetForm()
  isEdit.value = true
  Object.assign(formData, {
    id: row.id,
    parentId: row.parentId,
    permissionName: row.permissionName,
    permissionCode: row.permissionCode,
    permissionType: row.permissionType,
    path: row.path || '',
    component: row.component || '',
    icon: row.icon || '',
    sort: row.sort ?? 0,
    isVisible: row.isVisible ?? 1,
    status: row.status ?? 1,
    remark: row.remark || ''
  })
  showForm.value = true
}

/**
 * 提交表单：校验通过后根据模式调用新增或更新接口，成功后关闭对话框并刷新权限树
 */
async function confirmForm() {
  if (!formRef.value) return
  // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致 submitLoading 提前释放、防重复提交失效。改为 try-await 模式。
  try {
    await formRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  submitLoading.value = true
  try {
    const payload = { ...formData }
    // parentId 为空时设为0
    if (!payload.parentId) payload.parentId = 0
    if (isEdit.value) {
      await systemStore.updatePermission(formData.id, payload)
      feedback.success('修改权限成功')
    } else {
      await systemStore.createPermission(payload)
      feedback.success('新增权限成功')
    }
    showForm.value = false
    fetchList()
  } catch (error) {
    console.error('保存权限失败:', error)
  } finally {
    submitLoading.value = false
  }
}

/* ============ 删除 ============ */
/**
 * 删除权限：二次确认后调用删除接口并刷新权限树
 * @param {Object} row 待删除的权限节点
 */
async function handleDelete(row) {
  try {
    await feedback.confirmDelete(`确认删除权限「${row.permissionName}」吗？`)
    await systemStore.deletePermission(row.id)
    feedback.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') console.error('删除权限失败:', error)
  }
}

/* ============ 权限类型显示 ============ */
/** 将权限类型码转为中文文本 @param {number} type 1菜单/2按钮/3接口 @returns {string} */
function permTypeText(type) {
  const map = { 1: '菜单', 2: '按钮', 3: '接口' }
  return map[type] || '未知'
}

/** 将权限类型码转为对应的标签颜色类型 @param {number} type 权限类型码 @returns {string} el-tag type */
function permTypeTag(type) {
  const map = { 1: 'primary', 2: 'success', 3: 'warning' }
  return map[type] || 'info'
}

/* ============ 初始化 ============ */
// 进入页面：加载权限树
onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.permission-management {
  padding: 16px;
}
</style>
