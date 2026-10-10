<template>
  <div class="project-team-management">
    <!-- P1-2.2：tabs 提至 CrudTable 外层（避免 CrudTable 的 el-card 与外层 el-card 嵌套），
         通过 v-show 切换两个 CrudTable 的显隐 -->
    <el-tabs
      v-model="activeTab"
      class="team-tabs"
      @tab-change="handleTabChange"
    >
      <el-tab-pane
        label="项目组成员"
        name="members"
      />
      <el-tab-pane
        label="我参与的项目组"
        name="my"
      />
    </el-tabs>

    <!-- ====== Tab 1：项目组成员管理 ====== -->
    <CrudTable
      v-show="activeTab === 'members'"
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :page-sizes="[10, 20, 50]"
      :data="list"
      :loading="loading"
      :total="total"
      @change="fetchList"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <BaseButton
            v-if="hasPermission('project:team:manage')"
            variant="primary"
            icon-name="Plus"
            @click="openAddDialog"
          >
            添加成员
          </BaseButton>
        </div>
        <div class="toolbar-right">
          <el-select
            v-model="query.projectId"
            placeholder="选择项目"
            filterable
            clearable
            style="width: 220px"
            @change="handleSearch"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
          <el-select
            v-model="query.roleInTeam"
            placeholder="组内角色"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option
              label="组长"
              value="leader"
            />
            <el-option
              label="成员"
              value="member"
            />
            <el-option
              label="观察者"
              value="observer"
            />
          </el-select>
          <el-select
            v-model="query.isActive"
            placeholder="状态"
            clearable
            style="width: 110px"
            @change="handleSearch"
          >
            <el-option
              label="在组"
              :value="1"
            />
            <el-option
              label="已离组"
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
        label="项目"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.projectName || '项目#' + row.projectId }}
        </template>
      </el-table-column>
      <el-table-column
        label="成员"
        width="140"
      >
        <template #default="{ row }">
          {{ row.userName || '用户#' + row.userId }}
        </template>
      </el-table-column>
      <el-table-column
        label="组内角色"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="roleTagType(row.roleInTeam)"
          >
            {{ roleText(row.roleInTeam) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.isActive === 1 ? 'success' : 'info'"
          >
            {{ row.isActive === 1 ? '在组' : '已离组' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="加入时间"
        width="160"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.joinedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="离组时间"
        width="160"
      >
        <template #default="{ row }">
          {{ row.leftAt ? formatDateTime(row.leftAt) : '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="remark"
        label="备注"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        label="操作"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            v-if="hasPermission('project:team:manage') && row.isActive === 1 && row.roleInTeam !== 'leader'"
            variant="link-success"
            size="small"
            icon-name="Key"
            @click="openTransferDialog(row)"
          >
            转组长
          </BaseButton>
          <BaseButton
            v-if="hasPermission('project:team:manage') && row.isActive === 1"
            variant="link-danger"
            size="small"
            icon-name="CircleClose"
            @click="openRemoveDialog(row)"
          >
            移除
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- ====== Tab 2：我参与的项目组（无分页） ====== -->
    <CrudTable
      v-show="activeTab === 'my'"
      :data="myTeams"
      :loading="myLoading"
      :show-pagination="false"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="toolbar-tip">查看当前账号已加入的所有项目组（所有登录用户可访问）</span>
        </div>
        <div class="toolbar-right">
          <BaseButton
            variant="plain"
            icon-name="Refresh"
            @click="fetchMyTeams"
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
        label="项目"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.projectName || '项目#' + row.projectId }}
        </template>
      </el-table-column>
      <el-table-column
        label="我的角色"
        width="120"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="roleTagType(row.roleInTeam)"
          >
            {{ roleText(row.roleInTeam) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.isActive === 1 ? 'success' : 'info'"
          >
            {{ row.isActive === 1 ? '在组' : '已离组' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="加入时间"
        width="160"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.joinedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="remark"
        label="备注"
        min-width="160"
        show-overflow-tooltip
      />
    </CrudTable>

    <!-- 添加成员对话框 -->
    <el-dialog
      v-model="showAdd"
      title="添加项目组成员"
      width="520px"
      @closed="resetAddForm"
    >
      <el-form
        ref="addFormRef"
        :model="addForm"
        :rules="addFormRules"
        label-width="100px"
      >
        <el-form-item
          label="项目"
          prop="projectId"
        >
          <el-select
            v-model="addForm.projectId"
            placeholder="选择项目"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="成员"
          prop="userId"
        >
          <el-select
            v-model="addForm.userId"
            placeholder="选择用户"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.realName || u.username"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="组内角色"
          prop="roleInTeam"
        >
          <el-radio-group v-model="addForm.roleInTeam">
            <el-radio value="leader">
              组长
            </el-radio>
            <el-radio value="member">
              成员
            </el-radio>
            <el-radio value="observer">
              观察者
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="备注"
          prop="remark"
        >
          <el-input
            v-model="addForm.remark"
            type="textarea"
            :rows="3"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showAdd = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="addLoading"
          @click="confirmAdd"
        >
          确认添加
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 移除成员对话框 -->
    <el-dialog
      v-model="showRemove"
      title="移除项目组成员"
      width="480px"
    >
      <el-form label-width="80px">
        <el-form-item label="移除原因">
          <el-input
            v-model="removeReason"
            type="textarea"
            :rows="3"
            placeholder="请输入移除原因（选填），移除后会同步撤销其授权"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showRemove = false">
          取消
        </BaseButton>
        <BaseButton
          variant="danger"
          :loading="removeLoading"
          @click="confirmRemove"
        >
          确认移除
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 转让组长对话框 -->
    <el-dialog
      v-model="showTransfer"
      title="转让组长"
      width="480px"
    >
      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      >
        转让后，新组长将成为项目负责人，原组长降为普通成员。此操作会同步更新项目表 manager_id。
      </el-alert>
      <el-form label-width="100px">
        <el-form-item label="当前成员">
          <span>{{ transferRow?.userName || '用户#' + transferRow?.userId }}</span>
        </el-form-item>
        <el-form-item label="项目">
          <span>{{ transferRow?.projectName || '项目#' + transferRow?.projectId }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showTransfer = false">
          取消
        </BaseButton>
        <BaseButton
          variant="warning"
          :loading="transferLoading"
          @click="confirmTransfer"
        >
          确认转让
        </BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 项目组成员管理
 *
 * 业务用途：管理项目组成员（添加/移除/转组长）并查看当前用户参与的项目组。
 * 主要功能区：双 Tab 切换 ——「项目组成员」（分页 + 多条件筛选 + 增删改）与
 *             「我参与的项目组」（当前用户视角的只读列表）。
 * 权限：project:team:manage（管理操作）、所有登录用户可访问「我参与」Tab。
 *
 * P1-2.2：使用 CrudTable 通用布局组件封装两个 Tab 的表格布局。
 *         由于本页为双 Tab 结构（各自独立的 list/total/loading/query），
 *         不适合使用 useCrudList（其设计为单一列表数据流），因此采用 CrudTable 布局-only 迁移，
 *         保留原有 fetchList/fetchMyTeams/handleTabChange 数据逻辑。
 *         两个 CrudTable 通过 v-show 切换显隐，避免 el-card 嵌套。
 */
// 注：BaseButton / TableSkeleton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, onMounted } from 'vue'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'
import { feedback } from '@/utils/feedback'
import { projectTeamApi } from '@/api/projectTeam'
import { projectApi } from '@/api/project'
import { systemApi } from '@/api/system'
import { hasPermission } from '@/utils/permission'
import { formatDateTime } from '@/utils/format'
// P1-2.2：通用列表布局组件
import CrudTable from '@/components/CrudTable.vue'

defineOptions({ name: 'ProjectTeamManagement' })

const activeTab = ref('members')

/* ====== 成员列表 ====== */
const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, projectId: undefined, roleInTeam: '', isActive: undefined })
const projectOptions = ref([])
const userOptions = ref([])

async function fetchList() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.projectId) params.projectId = query.projectId
    if (query.roleInTeam) params.roleInTeam = query.roleInTeam
    if (query.isActive !== undefined && query.isActive !== null) params.isActive = query.isActive
    const res = await projectTeamApi.listMembers(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('查询项目组成员失败', e)
  } finally {
    loading.value = false
  }
}
function handleSearch() { query.pageNum = 1; fetchList() }
function handleReset() {
  query.projectId = undefined; query.roleInTeam = ''; query.isActive = undefined; query.pageNum = 1
  fetchList()
}

/* ====== 我参与的项目组 ====== */
const myTeams = ref([])
const myLoading = ref(false)
async function fetchMyTeams() {
  myLoading.value = true
  try {
    const res = await projectTeamApi.myTeams()
    myTeams.value = res.data || []
  } catch (e) {
    console.error('查询我参与的项目组失败', e)
  } finally {
    myLoading.value = false
  }
}

function handleTabChange(tab) {
  if (tab === 'members') fetchList()
  else if (tab === 'my') fetchMyTeams()
}

/* ====== 加载下拉数据 ====== */
async function loadOptions() {
  try {
    const [projRes, userRes] = await Promise.all([
      projectApi.getList({ pageNum: 1, pageSize: 200 }),
      systemApi.queryUsers({ pageNum: 1, pageSize: 200 })
    ])
    projectOptions.value = projRes.data?.records || []
    userOptions.value = (userRes.data?.records || []).filter(u => u.status === 1)
  } catch (e) {
    console.error('加载下拉数据失败', e)
  }
}

/* ====== 添加成员 ====== */
const showAdd = ref(false)
const addLoading = ref(false)
const addFormRef = ref()
const addForm = reactive({ projectId: null, userId: null, roleInTeam: 'member', remark: '' })
const addFormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  userId: [{ required: true, message: '请选择成员', trigger: 'change' }]
}
async function openAddDialog() {
  resetAddForm()
  if (projectOptions.value.length === 0) await loadOptions()
  showAdd.value = true
}
function resetAddForm() {
  Object.assign(addForm, { projectId: null, userId: null, roleInTeam: 'member', remark: '' })
  addFormRef.value?.clearValidate()
}
async function confirmAdd() {
  // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致 addLoading 提前释放、防重复提交失效。改为 try-await 模式。
  try {
    await addFormRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  addLoading.value = true
  try {
    await projectTeamApi.addMember({
      projectId: addForm.projectId,
      userId: addForm.userId,
      roleInTeam: addForm.roleInTeam,
      remark: addForm.remark
    })
    feedback.success('成员添加成功')
    showAdd.value = false
    fetchList()
  } catch (e) {
    console.error('添加成员失败', e)
  } finally {
    addLoading.value = false
  }
}

/* ====== 移除成员 ====== */
const showRemove = ref(false)
const removeLoading = ref(false)
const removeReason = ref('')
const removeRow = ref(null)
function openRemoveDialog(row) {
  removeRow.value = row
  removeReason.value = ''
  showRemove.value = true
}
async function confirmRemove() {
  removeLoading.value = true
  try {
    await projectTeamApi.removeMember(removeRow.value.id, removeReason.value)
    feedback.success('成员已移除')
    showRemove.value = false
    fetchList()
  } catch (e) {
    console.error('移除成员失败', e)
  } finally {
    removeLoading.value = false
  }
}

/* ====== 转让组长 ====== */
const showTransfer = ref(false)
const transferLoading = ref(false)
const transferRow = ref(null)
function openTransferDialog(row) {
  transferRow.value = row
  showTransfer.value = true
}
async function confirmTransfer() {
  // P1 修复：ElMessageBox.confirm 用户点"取消"时会 reject，未捕获会产生 unhandled rejection
  try {
    await feedback.confirm(
      `确认将「${transferRow.value.projectName || '项目#' + transferRow.value.projectId}」的组长转让给「${transferRow.value.userName || '用户#' + transferRow.value.userId}」吗？`,
      { title: '转让确认', type: 'warning' }
    )
  } catch {
    return // 用户取消转让
  }
  transferLoading.value = true
  try {
    await projectTeamApi.transferLeader(transferRow.value.projectId, transferRow.value.userId)
    feedback.success('组长已转让')
    showTransfer.value = false
    fetchList()
  } catch (e) {
    console.error('转让组长失败', e)
  } finally {
    transferLoading.value = false
  }
}

/* ====== 工具函数 ====== */
function roleText(role) {
  return { leader: '组长', member: '成员', observer: '观察者' }[role] || role || '-'
}
function roleTagType(role) {
  return { leader: 'danger', member: 'primary', observer: 'info' }[role] || 'info'
}

onMounted(async () => {
  await loadOptions()
  fetchList()
})
// keep-alive 重新激活时按当前 Tab 刷新（首次激活自动跳过，避免与 onMounted 请求双发）
useKeepAliveRefresh(() => {
  if (activeTab.value === 'members') fetchList()
  else fetchMyTeams()
})
</script>

<style scoped>
.project-team-management {
  padding: 16px;
}

.team-tabs {
  margin-bottom: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-tip {
  color: #999;
  font-size: 13px;
}
</style>
