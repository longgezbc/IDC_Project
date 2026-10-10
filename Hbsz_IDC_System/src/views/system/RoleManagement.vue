<template>
  <div class="role-management">
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
            variant="primary"
            icon-name="Plus"
            @click="handleAdd"
          >
            新增角色
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
        type="index"
        label="#"
        width="50"
        align="center"
      />
      <el-table-column
        prop="roleName"
        label="角色名称"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="roleCode"
        label="角色编码"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="description"
        label="描述"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="sort"
        label="排序"
        width="80"
        align="center"
      />
      <el-table-column
        prop="userCount"
        label="用户数"
        width="90"
        align="center"
      />
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
        width="280"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            icon-name="Edit"
            @click="handleEdit(row)"
          >
            编辑
          </BaseButton>
          <BaseButton
            variant="link"
            size="small"
            icon-name="Key"
            @click="handlePermission(row)"
          >
            分配权限
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
      :title="isEdit ? '编辑角色' : '新增角色'"
      width="560px"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item
          label="角色名称"
          prop="roleName"
        >
          <el-input
            v-model="formData.roleName"
            placeholder="请输入角色名称"
          />
        </el-form-item>
        <el-form-item
          label="角色编码"
          prop="roleCode"
        >
          <el-input
            v-model="formData.roleCode"
            :disabled="isEdit && formData.roleCode === 'admin'"
            placeholder="如：project_manager"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="formData.sort"
            :min="0"
            :max="999"
          />
        </el-form-item>
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

    <!-- 分配权限对话框 -->
    <el-dialog
      v-model="showPermission"
      title="分配权限"
      width="640px"
      @open="handlePermissionDialogOpen"
    >
      <div class="assign-perm-tip">
        为角色 <strong>{{ currentRole?.roleName }}</strong>（{{ currentRole?.roleCode }}）分配权限：
      </div>
      <div class="perm-tree-toolbar">
        <el-checkbox
          v-model="permExpandAll"
          @change="handleExpandAll"
        >
          展开/折叠
        </el-checkbox>
        <el-checkbox
          v-model="permCheckAll"
          @change="handleCheckAll"
        >
          全选/全不选
        </el-checkbox>
        <BaseButton
          variant="link"
          size="small"
          @click="handleExpandAll(true)"
        >
          全部展开
        </BaseButton>
        <BaseButton
          variant="link"
          size="small"
          @click="handleExpandAll(false)"
        >
          全部折叠
        </BaseButton>
      </div>
      <div
        v-loading="systemStore.permissionLoading"
        class="perm-tree-wrapper"
      >
        <el-tree
          ref="permTreeRef"
          :data="systemStore.permissionTree"
          show-checkbox
          node-key="id"
          :props="{ label: 'permissionName', children: 'children' }"
          :default-expand-all="permExpandAll"
          @check="handlePermCheck"
        >
          <template #default="{ data }">
            <span class="perm-tree-node">
              <span>{{ data.permissionName }}</span>
              <el-tag
                size="small"
                :type="permTypeTag(data.permissionType)"
                class="perm-type-tag"
              >
                {{ permTypeText(data.permissionType) }}
              </el-tag>
              <span class="perm-code">{{ data.permissionCode }}</span>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <BaseButton @click="showPermission = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="assignPermLoading"
          @click="confirmAssignPermission"
        >
          确定
        </BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 角色管理页
 * 业务用途：维护系统角色，支持角色的增删改查、启用/禁用，以及为角色分配权限（关联权限树）。
 *          属于「系统管理」模块，是用户与权限之间的桥梁。
 * 主要功能区：顶部工具栏（新增/刷新）、角色列表表格与分页、新增/编辑角色对话框，
 *            以及分配权限对话框（基于权限树的多选，支持展开/折叠与全选）。
 * 数据来源：systemApi.queryRoles（列表查询）+ systemStore（角色增删改、权限树与角色授权接口）。
 *
 * P1-2.2：列表数据状态与 fetch 由 useCrudList 统一管理（直接调用 systemApi.queryRoles），
 *        布局由 CrudTable 通用组件封装，本组件仅保留角色特有的列定义、表单状态、
 *        增删改业务逻辑及分配权限对话框。
 */
// 注：BaseButton / TableSkeleton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, onMounted, nextTick } from 'vue'
import { feedback } from '@/utils/feedback'
import { useSystemStore } from '@/stores/system'
import { systemApi } from '@/api/system'
import { formatDateTime } from '@/utils/format'
// P1-2.2：通用列表布局组件 + 通用 CRUD 组合式函数
import CrudTable from '@/components/CrudTable.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'

const systemStore = useSystemStore() // 系统管理仓库：角色增删改、权限树与授权接口

/* ============ 查询 ============ */
// P1-2.2：列表数据状态与 fetch 由 useCrudList 统一管理（角色列表无业务筛选，仅分页）
const { list, total, loading, queryParams, fetchList } = useCrudList(systemApi.queryRoles, {})

/* ============ 新增/编辑 ============ */
const showForm = ref(false)       // 新增/编辑对话框显隐
const isEdit = ref(false)         // 是否为编辑模式
const formRef = ref()             // 表单引用，用于校验与重置
const submitLoading = ref(false)  // 保存按钮加载状态
// 角色表单数据（新增/编辑共用）
const formData = reactive({
  id: null,
  roleName: '',
  roleCode: '',
  description: '',
  sort: 0,
  status: 1
})

const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

/** 重置表单为初始值并退出编辑模式 */
function resetForm() {
  Object.assign(formData, {
    id: null,
    roleName: '',
    roleCode: '',
    description: '',
    sort: 0,
    status: 1
  })
  isEdit.value = false
  formRef.value?.resetFields()
}

/** 打开新增角色对话框 */
function handleAdd() {
  resetForm()
  showForm.value = true
}

/** 打开编辑角色对话框并回显数据 @param {Object} row 待编辑的角色 */
function handleEdit(row) {
  resetForm()
  isEdit.value = true
  Object.assign(formData, {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    description: row.description || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  showForm.value = true
}

/** 提交表单：校验通过后按模式调用新增或更新接口，成功后关闭对话框并刷新列表 */
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
    if (isEdit.value) {
      await systemStore.updateRole(formData.id, payload)
      feedback.success('修改角色成功')
    } else {
      await systemStore.createRole(payload)
      feedback.success('新增角色成功')
    }
    showForm.value = false
    fetchList()
  } catch (error) {
    console.error('保存角色失败:', error)
  } finally {
    submitLoading.value = false
  }
}

/* ============ 删除 ============ */
/** 删除角色：二次确认后调用删除接口并刷新列表 @param {Object} row 待删除的角色 */
async function handleDelete(row) {
  try {
    await feedback.confirmDelete(`确认删除角色「${row.roleName}」吗？`)
    await systemStore.deleteRole(row.id)
    feedback.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') console.error('删除角色失败:', error)
  }
}

/* ============ 分配权限 ============ */
const showPermission = ref(false)     // 分配权限对话框显隐
const currentRole = ref(null)         // 当前正在分配权限的角色
const permTreeRef = ref()             // 权限树组件引用
const assignPermLoading = ref(false)  // 分配权限确定按钮加载状态
const permExpandAll = ref(true)       // 权限树是否全部展开
const permCheckAll = ref(false)       // 权限树是否全选状态

/** 打开分配权限对话框（重置展开/勾选状态） @param {Object} row 目标角色 */
function handlePermission(row) {
  currentRole.value = row
  permCheckAll.value = false
  permExpandAll.value = true
  showPermission.value = true
}

/** 对话框打开回调：懒加载权限树并回显该角色已勾选的权限 */
async function handlePermissionDialogOpen() {
  // 加载权限树
  if (!systemStore.permissionTree.length) {
    await systemStore.fetchPermissionTree()
  }
  await nextTick()
  // 回显已选中的权限
  const checkedIds = currentRole.value?.permissionIds || []
  permTreeRef.value?.setCheckedKeys(checkedIds)
}

/** 树节点勾选变化时同步「全选」复选框状态 */
function handlePermCheck() {
  const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
  const halfCheckedKeys = permTreeRef.value?.getHalfCheckedKeys() || []
  permCheckAll.value = checkedKeys.length > 0 || halfCheckedKeys.length > 0
}

/** 展开/折叠全部节点 @param {boolean} val 是否展开 */
function handleExpandAll(val) {
  permExpandAll.value = val
  // 修复 M18: 原函数体为空，仅注释未实现，无法展开/折叠树节点。遍历所有树节点设置展开状态。
  const tree = permTreeRef.value
  if (!tree) return
  // 遍历所有树节点，设置展开状态
  const nodes = tree.store?.nodesMap || {}
  Object.values(nodes).forEach(node => {
    node.expanded = val
  })
}

/** 全选/全不选：勾选或清空所有叶子节点 @param {boolean} val true 全选 / false 全不选 */
function handleCheckAll(val) {
  const tree = permTreeRef.value
  if (!tree) return
  if (val) {
    // 全选：选中所有叶子节点（el-tree会自动勾选父节点）
    const allKeys = getAllLeafKeys(systemStore.permissionTree)
    tree.setCheckedKeys(allKeys)
  } else {
    tree.setCheckedKeys([])
  }
}

/** 递归收集树的所有叶子节点 id @param {Array} nodes 权限树节点数组 @returns {Array} 叶子节点 id 列表 */
function getAllLeafKeys(nodes) {
  const keys = []
  const walk = (list) => {
    for (const n of list) {
      if (!n.children || n.children.length === 0) {
        keys.push(n.id)
      } else {
        walk(n.children)
      }
    }
  }
  walk(nodes)
  return keys
}

/** 确认分配权限：二次确认后合并完全选中与半选中的权限 id 提交，成功后刷新列表 */
async function confirmAssignPermission() {
  const tree = permTreeRef.value
  if (!tree) return
  const checkedKeys = tree.getCheckedKeys()
  const halfCheckedKeys = tree.getHalfCheckedKeys()
  // 合并完全选中和半选中的权限ID（半选中表示父权限被部分选中，也需要保存）
  const permissionIds = [...new Set([...checkedKeys, ...halfCheckedKeys])]

  try {
    await feedback.confirm(`确认将 ${permissionIds.length} 个权限分配给角色「${currentRole.value.roleName}」吗？`,
      { title: '权限分配确认', type: 'warning' })
  } catch {
    return // 用户取消
  }

  assignPermLoading.value = true
  try {
    await systemStore.assignRolePermissions(currentRole.value.id, permissionIds)
    feedback.success('权限分配成功')
    showPermission.value = false
    fetchList()
  } catch (error) {
    console.error('分配权限失败:', error)
  } finally {
    assignPermLoading.value = false
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
// 进入页面：加载角色列表
onMounted(fetchList)

// 缓存页面被重新激活时刷新列表，避免展示旧数据
// （首次激活由 useKeepAliveRefresh 自动跳过，避免与 onMounted 请求双发）
useKeepAliveRefresh(() => fetchList())
</script>

<style scoped>
.role-management {
  padding: 16px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.assign-perm-tip {
  margin-bottom: 12px;
  color: #606266;
}
.perm-tree-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.perm-tree-wrapper {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
}
.perm-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}
.perm-type-tag {
  margin-left: 8px;
}
.perm-code {
  color: #999;
  font-size: 12px;
}
</style>
