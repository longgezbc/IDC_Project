<template>
  <div class="user-management">
    <!-- P1-2.2：使用 CrudTable 通用布局组件，搜索表单 + 操作按钮通过 #toolbar 插槽传入，
         列定义通过默认插槽传入，空状态通过 #empty 插槽透传到 el-table -->
    <CrudTable
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :data="list"
      :loading="loading"
      :total="total"
      :skeleton-cols="6"
      @change="fetchList"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <BaseButton
            variant="primary"
            icon-name="Plus"
            @click="handleAdd"
          >
            新增用户
          </BaseButton>
          <BaseButton
            v-if="hasPermission('system:user:add')"
            variant="success"
            icon-name="Upload"
            @click="showImportDialog = true"
          >
            批量导入
          </BaseButton>
          <BaseButton
            v-if="hasPermission('system:user:add')"
            variant="plain"
            icon-name="Download"
            @click="handleDownloadTemplate"
          >
            下载模板
          </BaseButton>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="queryParams.username"
            placeholder="用户名"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model="queryParams.name"
            placeholder="姓名"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model="queryParams.phone"
            placeholder="手机号"
            clearable
            style="width: 160px"
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
            icon-name="RefreshLeft"
            @click="handleReset"
          >
            重置
          </BaseButton>
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
        prop="username"
        label="用户名"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="name"
        label="姓名"
        min-width="100"
        show-overflow-tooltip
      />
      <el-table-column
        prop="nickname"
        label="昵称"
        min-width="100"
        show-overflow-tooltip
      />
      <el-table-column
        prop="phone"
        label="手机号"
        min-width="130"
      />
      <el-table-column
        prop="email"
        label="邮箱"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="角色"
        min-width="160"
      >
        <template #default="{ row }">
          <el-tag
            v-for="role in (row.roleNames || [])"
            :key="role"
            size="small"
            class="role-tag"
          >
            {{ role }}
          </el-tag>
          <span
            v-if="!row.roleNames || row.roleNames.length === 0"
            class="text-muted"
          >未分配</span>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            :loading="row._statusLoading"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="lastLoginTime"
        label="最后登录"
        min-width="160"
      >
        <template #default="{ row }">
          {{ row.lastLoginTime ? formatDateTime(row.lastLoginTime) : '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="340"
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
            @click="handleResetPwd(row)"
          >
            重置密码
          </BaseButton>
          <BaseButton
            variant="link"
            size="small"
            icon-name="UserFilled"
            @click="handleAssignRole(row)"
          >
            分配角色
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

      <template #empty>
        <EmptyState
          icon-name="User"
          title="暂无用户"
          description="点击新增用户添加系统成员"
        />
      </template>
    </CrudTable>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="showForm"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="640px"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              label="用户名"
              prop="username"
            >
              <el-input
                v-model="formData.username"
                :disabled="isEdit"
                placeholder="3-20位，字母、数字、下划线"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="姓名"
              prop="name"
            >
              <el-input
                v-model="formData.name"
                placeholder="请输入真实姓名"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              v-if="!isEdit"
              label="密码"
              prop="password"
            >
              <el-input
                v-model="formData.password"
                type="password"
                show-password
                placeholder="8-32位，需含大小写字母、数字和特殊字符"
              />
            </el-form-item>
            <el-form-item
              v-else
              label="新密码"
              prop="password"
            >
              <el-input
                v-model="formData.password"
                type="password"
                show-password
                placeholder="留空表示不修改；填写需8-32位含大小写字母、数字和特殊字符"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称">
              <el-input
                v-model="formData.nickname"
                placeholder="请输入昵称（选填）"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              label="手机号"
              prop="phone"
            >
              <el-input
                v-model="formData.phone"
                placeholder="请输入手机号，如13800138000"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="邮箱"
              prop="email"
            >
              <el-input
                v-model="formData.email"
                placeholder="请输入邮箱（选填）"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="性别">
              <el-radio-group v-model="formData.gender">
                <el-radio :value="1">
                  男
                </el-radio>
                <el-radio :value="0">
                  女
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="岗位"
              prop="positionId"
            >
              <el-select
                v-model="formData.positionId"
                placeholder="请选择岗位"
                filterable
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="pos in positionOptions"
                  :key="pos.id"
                  :label="pos.name"
                  :value="pos.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色">
          <el-select
            v-model="formData.roleIds"
            multiple
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in systemStore.allRoles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
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
          :loading="submitting"
          @click="confirmForm"
        >
          确定
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="showResetPwd"
      title="重置密码"
      width="440px"
    >
      <el-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="90px"
      >
        <el-form-item label="用户名">
          <el-input
            :model-value="currentUser?.username"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="新密码"
          prop="newPassword"
        >
          <el-input
            v-model="resetPwdForm.newPassword"
            type="password"
            show-password
            placeholder="请输入新密码"
          />
        </el-form-item>
        <el-form-item
          label="确认密码"
          prop="confirmPassword"
        >
          <el-input
            v-model="resetPwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showResetPwd = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="resetPwdLoading"
          @click="confirmResetPwd"
        >
          确定
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog
      v-model="showAssignRole"
      title="分配角色"
      width="480px"
    >
      <div class="assign-role-tip">
        为用户 <strong>{{ currentUser?.username }}</strong>（{{ currentUser?.name }}）分配角色：
      </div>
      <el-checkbox-group
        v-model="assignRoleForm.roleIds"
        class="role-checkbox-group"
      >
        <el-checkbox
          v-for="role in systemStore.allRoles"
          :key="role.id"
          :value="role.id"
          :label="role.roleName"
        >
          {{ role.roleName }}
          <span class="role-code">（{{ role.roleCode }}）</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <BaseButton @click="showAssignRole = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="assignRoleLoading"
          @click="confirmAssignRole"
        >
          确定
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="showImportDialog"
      title="批量导入用户"
      width="500px"
      @closed="resetImportDialog"
    >
      <!-- 关键填写要点前置（详细规则见模板内「填写说明」sheet） -->
      <el-alert
        type="info"
        :closable="false"
        class="import-tips"
      >
        <p>1. 密码必须 8-32 位，同时包含大写字母、小写字母、数字和特殊字符（如 Abc@2026）</p>
        <p>2. 岗位编码可选：gm / pm / senior_engineer / engineer / junior_engineer / outsourcing / viewer</p>
        <p>3. 角色编码可选：admin / project_manager / engineer / user</p>
        <p>4. 模板第 2-3 行为示例数据，请修改或删除后再上传</p>
      </el-alert>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        drag
      >
        <el-icon class="el-icon--upload">
          <UploadFilled />
        </el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            仅支持 .xlsx / .xls 格式，请先下载模板填写（模板含示例数据与「填写说明」页）
          </div>
        </template>
      </el-upload>
      <template #footer>
        <BaseButton @click="showImportDialog = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="importLoading"
          @click="handleImport"
        >
          确认导入
        </BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 用户管理页
 * 业务用途：维护系统用户账号，支持按条件查询、增删改、启用/禁用、重置密码及分配角色。
 *          属于「系统管理」模块，与角色管理协同完成 RBAC 权限体系。
 * 主要功能区：工具栏（新增 + 批量导入 + 模板下载 + 搜索表单）、用户列表表格与分页、
 *            新增/编辑用户对话框，以及重置密码、分配角色、批量导入三个独立对话框。
 * 数据来源：systemApi.queryUsers（列表查询）+ systemStore（用户增删改、状态切换、重置密码、
 *          角色分配及全部角色下拉）。
 *
 * P1-2.2：列表数据状态与 fetch/search/reset 由 useCrudList 统一管理（直接调用 systemApi.queryUsers），
 *        布局由 CrudTable 通用组件封装，搜索表单从独立 el-card 合并到 CrudTable 的 #toolbar 插槽，
 *        本组件仅保留用户特有的列定义、表单状态、增删改业务逻辑及三个对话框。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, reactive, onMounted } from 'vue'
import { useSystemStore } from '@/stores/system'
import { systemApi } from '@/api/system'
import { positionApi } from '@/api/position'
import { formatDateTime } from '@/utils/format'
import { hasPermission } from '@/utils/permission'
import { feedback } from '@/utils/feedback'
// P1-2.2：通用列表布局组件 + 通用 CRUD 组合式函数
import CrudTable from '@/components/CrudTable.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useImport } from '@/composables/useImport'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'

const systemStore = useSystemStore() // 系统管理仓库：用户增删改、状态切换、角色分配及全部角色下拉

/* ============ 查询 ============ */
// P1-2.2：列表数据状态与 fetch/search/reset 由 useCrudList 统一管理
// initialQuery 声明业务筛选字段，handleReset 会将这些字段重置为初始值
const { list, total, loading, queryParams, fetchList, handleSearch, handleReset } = useCrudList(
  systemApi.queryUsers,
  { username: '', name: '', phone: '', status: null }
)

/* ============ 新增/编辑 ============ */
const showForm = ref(false)       // 新增/编辑对话框显隐
const isEdit = ref(false)         // 是否为编辑模式
const formRef = ref()             // 表单引用，用于校验与重置
const submitting = ref(false)     // 保存按钮加载状态（含防重复提交）
// 用户表单数据（新增/编辑共用）
const formData = reactive({
  id: null,
  username: '',
  password: '',
  name: '',
  nickname: '',
  gender: 1,
  phone: '',
  email: '',
  positionId: null,
  roleIds: [],
  status: 1
})

// 岗位下拉选项
const positionOptions = ref([])

// 表单校验规则：与后端 UserSaveDTO 约束保持一致
const formRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '只能包含字母、数字、下划线，长度3-20位', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过50个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}|[\]:";'<>?,./]).{8,32}$/,
      message: '8-32位，需包含大小写字母、数字和特殊字符',
      trigger: 'blur'
    }
  ],
  phone: [
    { pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

/** 重置表单为初始值并退出编辑模式 */
function resetForm() {
  Object.assign(formData, {
    id: null,
    username: '',
    password: '',
    name: '',
    nickname: '',
    gender: 1,
    phone: '',
    email: '',
    positionId: null,
    roleIds: [],
    status: 1
  })
  isEdit.value = false
  formRef.value?.resetFields()
}

/** 打开新增用户对话框 */
function handleAdd() {
  resetForm()
  showForm.value = true
}

/** 打开编辑用户对话框并回显数据（编辑时用户名禁改、密码非必填） @param {Object} row 待编辑的用户 */
function handleEdit(row) {
  resetForm()
  isEdit.value = true
  Object.assign(formData, {
    id: row.id,
    username: row.username,
    name: row.name,
    nickname: row.nickname,
    gender: row.gender ?? 1,
    phone: row.phone,
    email: row.email,
    positionId: row.positionId ?? null,
    roleIds: [...(row.roleIds || [])],
    status: row.status ?? 1
  })
  // 编辑时密码非必填，但若填写仍需满足复杂度校验
  formRules.password = [
    {
      pattern: /^$|^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}|[\]:";'<>?,./]).{8,32}$/,
      message: '8-32位，需包含大小写字母、数字和特殊字符',
      trigger: 'blur'
    }
  ]
  showForm.value = true
}

/**
 * 提交用户表单：校验通过后按模式新增或更新，编辑且未填密码时不提交 password 字段；
 * 完成后恢复密码必填规则（供下次新增使用）
 */
async function confirmForm() {
  if (submitting.value) return
  submitting.value = true
  try {
    if (!formRef.value) return
    // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致 submitLoading 提前释放、防重复提交失效。改为 try-await 模式。
    try {
      await formRef.value.validate()
    } catch {
      return // 校验失败，直接返回
    }
    try {
      const payload = { ...formData }
      // 编辑时密码为空则不传
      if (isEdit.value && !payload.password) {
        delete payload.password
      }
      if (isEdit.value) {
        await systemStore.updateUser(formData.id, payload)
        feedback.success('修改用户成功')
      } else {
        await systemStore.createUser(payload)
        feedback.success('新增用户成功')
      }
      showForm.value = false
      fetchList()
    } catch (error) {
      console.error('保存用户失败:', error)
    } finally {
      // 恢复密码必填规则（含复杂度校验）
      formRules.password = [
        { required: true, message: '请输入密码', trigger: 'blur' },
        {
          pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}|[\]:";'<>?,./]).{8,32}$/,
          message: '8-32位，需包含大小写字母、数字和特殊字符',
          trigger: 'blur'
        }
      ]
    }
  } finally {
    submitting.value = false
  }
}

/* ============ 状态切换 ============ */
/**
 * 切换用户启用/禁用状态（列表内 el-switch 触发）
 * @param {Object} row 目标用户行（借用 _statusLoading 控制单行开关加载态）
 * @param {boolean} val 开关的新值，true 启用 / false 禁用
 */
async function handleStatusChange(row, val) {
  row._statusLoading = true
  try {
    await systemStore.updateUserStatus(row.id, val ? 1 : 0)
    row.status = val ? 1 : 0
    feedback.success(val ? '已启用' : '已禁用')
  } catch (error) {
    console.error('修改状态失败:', error)
  } finally {
    row._statusLoading = false
  }
}

/* ============ 删除 ============ */
/** 删除用户：二次确认后调用删除接口并刷新列表（不可恢复） @param {Object} row 待删除的用户 */
function handleDelete(row) {
  feedback.confirmDelete(`确认删除用户「${row.name}」？`)
    .then(async () => {
      await systemStore.deleteUser(row.id)
      feedback.success('删除成功')
      fetchList()
    })
    .catch(error => {
      if (error !== 'cancel') console.error('删除用户失败:', error)
    })
}

/* ============ 重置密码 ============ */
const showResetPwd = ref(false)      // 重置密码对话框显隐
const resetPwdFormRef = ref()        // 重置密码表单引用
const resetPwdLoading = ref(false)   // 重置密码确定按钮加载状态
const currentUser = ref(null)        // 当前操作对象（重置密码/分配角色共用）
const resetPwdForm = reactive({ newPassword: '', confirmPassword: '' }) // 重置密码表单
// 重置密码校验规则：新密码至少 6 位，确认密码需与新密码一致
const resetPwdRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== resetPwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

/** 打开重置密码对话框并清空表单 @param {Object} row 目标用户 */
function handleResetPwd(row) {
  currentUser.value = row
  resetPwdForm.newPassword = ''
  resetPwdForm.confirmPassword = ''
  showResetPwd.value = true
}

/** 确认重置密码：校验通过后调用接口重置当前用户密码 */
async function confirmResetPwd() {
  if (!resetPwdFormRef.value) return
  // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致 resetPwdLoading 提前释放。改为 try-await 模式。
  try {
    await resetPwdFormRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  resetPwdLoading.value = true
  try {
    await systemStore.resetUserPassword(currentUser.value.id, resetPwdForm.newPassword)
    feedback.success('密码重置成功')
    showResetPwd.value = false
  } catch (error) {
    console.error('重置密码失败:', error)
  } finally {
    resetPwdLoading.value = false
  }
}

/* ============ 分配角色 ============ */
const showAssignRole = ref(false)     // 分配角色对话框显隐
const assignRoleLoading = ref(false)  // 分配角色确定按钮加载状态
const assignRoleForm = reactive({ roleIds: [] }) // 待提交的角色 id 集合

/** 打开分配角色对话框并回显用户已有角色 @param {Object} row 目标用户 */
function handleAssignRole(row) {
  currentUser.value = row
  assignRoleForm.roleIds = [...(row.roleIds || [])]
  showAssignRole.value = true
}

/** 确认分配角色：调用接口保存用户-角色关系，成功后刷新列表 */
async function confirmAssignRole() {
  assignRoleLoading.value = true
  try {
    await systemStore.assignUserRoles(currentUser.value.id, assignRoleForm.roleIds)
    feedback.success('角色分配成功')
    showAssignRole.value = false
    fetchList()
  } catch (error) {
    console.error('分配角色失败:', error)
  } finally {
    assignRoleLoading.value = false
  }
}

/* ============ 批量导入 / 模板下载 ============ */
// P3 重构：公共导入流程（状态、el-upload 回调、模板下载、导入提交与错误详情
// 转义展示）统一由 useImport 提供，此处仅注入用户模块差异项
const {
  showImportDialog, importLoading, uploadRef,
  handleFileChange, handleExceed, resetImportDialog,
  handleDownloadTemplate, handleImport
} = useImport({
  downloadTemplate: () => systemApi.downloadUserImportTemplate(),
  importFn: (file) => systemApi.importUsers(file),
  templateName: 'user_import_template.xlsx',
  onSuccess: () => fetchList()
})

/* ============ 初始化 ============ */
// 进入页面：并发加载用户列表、全部角色与启用岗位（角色用于下拉，岗位用于下拉）
onMounted(async () => {
  await Promise.all([
    fetchList(),
    systemStore.fetchAllRoles(),
    positionApi.listAll().then(res => { positionOptions.value = res.data || [] }).catch(() => {})
  ])
})

// 缓存页面被重新激活时刷新用户列表（首次挂载由 onMounted 处理，避免重复调用）
useKeepAliveRefresh(() => fetchList())
</script>

<style scoped>
.user-management {
  padding: 16px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.role-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}
.text-muted {
  color: #999;
  font-size: 12px;
}
.assign-role-tip {
  margin-bottom: 16px;
  color: #606266;
}
.role-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.role-code {
  color: #999;
  font-size: 12px;
}
/* 批量导入对话框：前置要点提示区（紧凑行距，避免提示比上传区还高） */
.import-tips {
  margin-bottom: 12px;
}

.import-tips :deep(p) {
  margin: 0 0 4px;
  line-height: 1.6;
}

.import-tips :deep(p:last-child) {
  margin-bottom: 0;
}
</style>
