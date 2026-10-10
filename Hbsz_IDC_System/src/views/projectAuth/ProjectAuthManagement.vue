<template>
  <div class="project-auth-management">
    <!-- P1-2.2：tabs 提至 CrudTable 外层（避免 el-card 嵌套），通过 v-show 切换两个 CrudTable -->
    <el-tabs
      v-model="activeTab"
      class="auth-tabs"
      @tab-change="handleTabChange"
    >
      <el-tab-pane
        label="授权申请"
        name="requests"
      />
      <el-tab-pane
        label="授权关系"
        name="assignments"
      />
    </el-tabs>

    <!-- ====== Tab 1：授权申请 ====== -->
    <CrudTable
      v-show="activeTab === 'requests'"
      v-model:current-page="reqQuery.pageNum"
      v-model:page-size="reqQuery.pageSize"
      :page-sizes="[10, 20, 50]"
      :data="reqList"
      :loading="reqLoading"
      :total="reqTotal"
      @change="fetchRequests"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <BaseButton
            v-if="hasPermission('project:auth:request')"
            variant="primary"
            icon-name="Plus"
            @click="openCreateDialog"
          >
            发起新申请
          </BaseButton>
        </div>
        <div class="toolbar-right">
          <el-select
            v-model="reqQuery.status"
            placeholder="状态"
            clearable
            style="width: 130px"
            @change="handleReqSearch"
          >
            <el-option
              label="待审批"
              value="pending"
            />
            <el-option
              label="已通过"
              value="approved"
            />
            <el-option
              label="已驳回"
              value="rejected"
            />
          </el-select>
          <BaseButton
            variant="primary"
            icon-name="Search"
            @click="handleReqSearch"
          >
            查询
          </BaseButton>
          <BaseButton
            variant="plain"
            icon-name="Refresh"
            @click="resetReqQuery"
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
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.projectName || '项目#' + row.projectId }}
        </template>
      </el-table-column>
      <el-table-column
        label="目标工程师"
        width="120"
      >
        <template #default="{ row }">
          {{ row.targetEngineerName || '用户#' + row.targetEngineerId }}
        </template>
      </el-table-column>
      <el-table-column
        label="申请人"
        width="120"
      >
        <template #default="{ row }">
          {{ row.applicantName || '用户#' + row.applicantId }}
        </template>
      </el-table-column>
      <el-table-column
        label="范围"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="scopeTagType(row.scope)"
          >
            {{ scopeText(row.scope) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="有效期"
        width="160"
      >
        <template #default="{ row }">
          {{ row.expireAt ? formatDateTime(row.expireAt) : '永久' }}
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
            :type="reqStatusTagType(row.status)"
          >
            {{ reqStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        label="申请时间"
        width="160"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="200"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            icon-name="View"
            @click="viewRequest(row)"
          >
            详情
          </BaseButton>
          <BaseButton
            v-if="hasPermission('project:auth:approve') && row.status === 'pending'"
            variant="link-success"
            size="small"
            icon-name="CircleCheck"
            @click="openApproveDialog(row, true)"
          >
            通过
          </BaseButton>
          <BaseButton
            v-if="hasPermission('project:auth:approve') && row.status === 'pending'"
            variant="link-danger"
            size="small"
            icon-name="CircleClose"
            @click="openApproveDialog(row, false)"
          >
            驳回
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- ====== Tab 2：授权关系 ====== -->
    <CrudTable
      v-show="activeTab === 'assignments'"
      v-model:current-page="assignQuery.pageNum"
      v-model:page-size="assignQuery.pageSize"
      :page-sizes="[10, 20, 50]"
      :data="assignList"
      :loading="assignLoading"
      :total="assignTotal"
      @change="fetchAssignments"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="toolbar-tip">管理项目-工程师的授权记录，支持撤销和延期</span>
        </div>
        <div class="toolbar-right">
          <el-select
            v-model="assignQuery.isActive"
            placeholder="有效性"
            clearable
            style="width: 120px"
            @change="handleAssignSearch"
          >
            <el-option
              label="有效"
              :value="1"
            />
            <el-option
              label="已撤销"
              :value="0"
            />
          </el-select>
          <el-checkbox
            v-model="assignQuery.expireSoon"
            @change="handleAssignSearch"
          >
            仅看即将过期
          </el-checkbox>
          <BaseButton
            variant="primary"
            icon-name="Search"
            @click="handleAssignSearch"
          >
            查询
          </BaseButton>
          <BaseButton
            variant="plain"
            icon-name="Refresh"
            @click="resetAssignQuery"
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
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.projectName || '项目#' + row.projectId }}
        </template>
      </el-table-column>
      <el-table-column
        label="工程师"
        width="120"
      >
        <template #default="{ row }">
          {{ row.engineerName || '用户#' + row.engineerId }}
        </template>
      </el-table-column>
      <el-table-column
        label="范围"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="scopeTagType(row.permissionScope)"
          >
            {{ scopeText(row.permissionScope) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="有效期"
        width="160"
      >
        <template #default="{ row }">
          {{ row.expireAt ? formatDateTime(row.expireAt) : '永久' }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="assignStatusTagType(row)"
          >
            {{ assignStatusText(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        label="授权时间"
        width="160"
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
            v-if="hasPermission('project:auth:approve') && row.isActive === 1"
            variant="link-warning"
            size="small"
            icon-name="Clock"
            @click="openExtendDialog(row)"
          >
            延期
          </BaseButton>
          <BaseButton
            v-if="hasPermission('project:auth:approve') && row.isActive === 1"
            variant="link-danger"
            size="small"
            icon-name="CircleClose"
            @click="openRevokeDialog(row)"
          >
            撤销
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- 创建申请对话框 -->
    <el-dialog
      v-model="showCreate"
      title="发起项目授权申请"
      width="560px"
      @closed="resetCreateForm"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createFormRules"
        label-width="100px"
      >
        <el-form-item
          label="项目"
          prop="projectId"
        >
          <el-select
            v-model="createForm.projectId"
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
          label="目标工程师"
          prop="targetEngineerId"
        >
          <el-select
            v-model="createForm.targetEngineerId"
            placeholder="选择工程师"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="u in engineerOptions"
              :key="u.id"
              :label="u.name || u.username"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="授权范围"
          prop="scope"
        >
          <el-radio-group v-model="createForm.scope">
            <el-radio value="read_only">
              只读
            </el-radio>
            <el-radio value="read_write">
              读写
            </el-radio>
            <el-radio value="review">
              可审核
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="有效期"
          prop="expireAt"
        >
          <el-date-picker
            v-model="createForm.expireAt"
            type="datetime"
            placeholder="留空表示永久"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="申请理由"
          prop="reason"
        >
          <el-input
            v-model="createForm.reason"
            type="textarea"
            :rows="3"
            placeholder="简要说明申请理由"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showCreate = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="createLoading"
          @click="confirmCreate"
        >
          提交申请
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 审批对话框（通过/驳回通用） -->
    <el-dialog
      v-model="showApprove"
      :title="approveForm.approve ? '审批通过' : '驳回申请'"
      width="480px"
    >
      <el-form
        :model="approveForm"
        label-width="80px"
      >
        <el-form-item label="审批意见">
          <el-input
            v-model="approveForm.comment"
            type="textarea"
            :rows="3"
            placeholder="选填，输入审批意见"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showApprove = false">
          取消
        </BaseButton>
        <BaseButton
          :variant="approveForm.approve ? 'success' : 'danger'"
          :loading="approveLoading"
          @click="confirmApprove"
        >
          确认
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 撤销授权对话框 -->
    <el-dialog
      v-model="showRevoke"
      title="撤销授权"
      width="480px"
    >
      <el-form label-width="80px">
        <el-form-item label="撤销原因">
          <el-input
            v-model="revokeReason"
            type="textarea"
            :rows="3"
            placeholder="请输入撤销原因（选填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showRevoke = false">
          取消
        </BaseButton>
        <BaseButton
          variant="danger"
          :loading="revokeLoading"
          @click="confirmRevoke"
        >
          确认撤销
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 延期对话框 -->
    <el-dialog
      v-model="showExtend"
      title="延期授权"
      width="480px"
    >
      <el-form label-width="80px">
        <el-form-item label="新有效期">
          <el-date-picker
            v-model="extendTime"
            type="datetime"
            placeholder="选择新的过期时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showExtend = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="extendLoading"
          @click="confirmExtend"
        >
          确认延期
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 申请详情对话框 -->
    <el-dialog
      v-model="showDetail"
      title="申请详情"
      width="560px"
    >
      <el-descriptions
        v-if="currentRow"
        :column="2"
        border
      >
        <el-descriptions-item label="项目">
          {{ currentRow.projectName || '项目#' + currentRow.projectId }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag
            size="small"
            :type="reqStatusTagType(currentRow.status)"
          >
            {{ reqStatusText(currentRow.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">
          {{ currentRow.applicantName || '用户#' + currentRow.applicantId }}
        </el-descriptions-item>
        <el-descriptions-item label="目标工程师">
          {{ currentRow.targetEngineerName || '用户#' + currentRow.targetEngineerId }}
        </el-descriptions-item>
        <el-descriptions-item label="范围">
          <el-tag
            size="small"
            :type="scopeTagType(currentRow.scope)"
          >
            {{ scopeText(currentRow.scope) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="有效期">
          {{ currentRow.expireAt ? formatDateTime(currentRow.expireAt) : '永久' }}
        </el-descriptions-item>
        <el-descriptions-item label="申请时间">
          {{ formatDateTime(currentRow.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="审批人">
          {{ currentRow.approverName || (currentRow.approverId ? '用户#' + currentRow.approverId : '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="审批时间">
          {{ currentRow.approvedAt ? formatDateTime(currentRow.approvedAt) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item
          label="申请理由"
          :span="2"
        >
          {{ currentRow.reason || '-' }}
        </el-descriptions-item>
        <el-descriptions-item
          label="审批意见"
          :span="2"
        >
          {{ currentRow.comment || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 项目授权管理
 *
 * 业务用途：管理项目授权申请（发起新申请、审批通过/驳回）与授权关系（延期、撤销）。
 * 主要功能区：双 Tab 切换 ——「授权申请」（分页 + 状态筛选 + 申请/审批）与
 *             「授权关系」（分页 + 有效性筛选 + 延期/撤销）。
 * 权限：project:auth:request（申请）、project:auth:approve（审批/延期/撤销）。
 *
 * P1-2.2：使用 CrudTable 通用布局组件封装两个 Tab 的表格布局。
 *         由于本页为双 Tab 结构（各自独立的 list/total/loading/query），
 *         不适合使用 useCrudList（其设计为单一列表数据流），因此采用 CrudTable 布局-only 迁移，
 *         保留原有 fetchRequests/fetchAssignments/handleTabChange 数据逻辑。
 *         两个 CrudTable 通过 v-show 切换显隐，避免 el-card 嵌套。
 */
// 注：BaseButton / TableSkeleton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, onMounted } from 'vue'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'
import { feedback } from '@/utils/feedback'
// 修复 L10：projectAuth.js 导出名改为 projectAuthRequestApi，避免与 projectAuthorization.js 同名冲突
import { projectAuthRequestApi as projectAuthApi } from '@/api/projectAuth'
import { projectApi } from '@/api/project'
import { systemApi } from '@/api/system'
import { hasPermission } from '@/utils/permission'
import { formatDateTime } from '@/utils/format'
// P1-2.2：通用列表布局组件
import CrudTable from '@/components/CrudTable.vue'

defineOptions({ name: 'ProjectAuthManagement' })

const activeTab = ref('requests')

/* ====== 授权申请列表 ====== */
const reqList = ref([])
const reqTotal = ref(0)
const reqLoading = ref(false)
const reqQuery = reactive({ pageNum: 1, pageSize: 10, status: '', projectId: undefined, applicantId: undefined })

async function fetchRequests() {
  reqLoading.value = true
  try {
    const params = { pageNum: reqQuery.pageNum, pageSize: reqQuery.pageSize }
    if (reqQuery.status) params.status = reqQuery.status
    if (reqQuery.projectId) params.projectId = reqQuery.projectId
    if (reqQuery.applicantId) params.applicantId = reqQuery.applicantId
    const res = await projectAuthApi.listAuthRequests(params)
    reqList.value = res.data?.records || []
    reqTotal.value = res.data?.total || 0
  } catch (e) {
    console.error('查询授权申请失败', e)
  } finally {
    reqLoading.value = false
  }
}
function handleReqSearch() { reqQuery.pageNum = 1; fetchRequests() }
function resetReqQuery() {
  reqQuery.status = ''; reqQuery.projectId = undefined; reqQuery.applicantId = undefined; reqQuery.pageNum = 1
  fetchRequests()
}

/* ====== 授权关系列表 ====== */
const assignList = ref([])
const assignTotal = ref(0)
const assignLoading = ref(false)
const assignQuery = reactive({ pageNum: 1, pageSize: 10, projectId: undefined, engineerId: undefined, isActive: undefined, expireSoon: false })

async function fetchAssignments() {
  assignLoading.value = true
  try {
    const params = { pageNum: assignQuery.pageNum, pageSize: assignQuery.pageSize }
    if (assignQuery.projectId) params.projectId = assignQuery.projectId
    if (assignQuery.engineerId) params.engineerId = assignQuery.engineerId
    if (assignQuery.isActive !== undefined && assignQuery.isActive !== null) params.isActive = assignQuery.isActive
    if (assignQuery.expireSoon) params.expireSoon = true
    const res = await projectAuthApi.listAssignments(params)
    assignList.value = res.data?.records || []
    assignTotal.value = res.data?.total || 0
  } catch (e) {
    console.error('查询授权关系失败', e)
  } finally {
    assignLoading.value = false
  }
}
function handleAssignSearch() { assignQuery.pageNum = 1; fetchAssignments() }
function resetAssignQuery() {
  assignQuery.projectId = undefined; assignQuery.engineerId = undefined
  assignQuery.isActive = undefined; assignQuery.expireSoon = false; assignQuery.pageNum = 1
  fetchAssignments()
}

function handleTabChange(tab) {
  if (tab === 'requests') fetchRequests()
  else if (tab === 'assignments') fetchAssignments()
}

/* ====== 创建申请 ====== */
const showCreate = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const projectOptions = ref([])
const engineerOptions = ref([])
const createForm = reactive({ projectId: null, targetEngineerId: null, scope: 'read_write', expireAt: null, reason: '' })
const createFormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  targetEngineerId: [{ required: true, message: '请选择工程师', trigger: 'change' }]
}

async function openCreateDialog() {
  resetCreateForm()
  // 加载项目和工程师下拉
  try {
    const [projRes, userRes] = await Promise.all([
      projectApi.getList({ pageNum: 1, pageSize: 200 }),
      systemApi.queryUsers({ pageNum: 1, pageSize: 200 })
    ])
    projectOptions.value = projRes.data?.records || []
    engineerOptions.value = (userRes.data?.records || []).filter(u => u.status === 1)
  } catch (e) {
    console.error('加载下拉数据失败', e)
  }
  showCreate.value = true
}
function resetCreateForm() {
  Object.assign(createForm, { projectId: null, targetEngineerId: null, scope: 'read_write', expireAt: null, reason: '' })
  createFormRef.value?.clearValidate()
}
async function confirmCreate() {
  // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致 createLoading 提前释放、防重复提交失效。改为 try-await 模式。
  try {
    await createFormRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  createLoading.value = true
  try {
    await projectAuthApi.createAuthRequest({
      projectId: createForm.projectId,
      targetEngineerId: createForm.targetEngineerId,
      scope: createForm.scope,
      expireAt: createForm.expireAt || null,
      reason: createForm.reason
    })
    feedback.success('授权申请已创建')
    showCreate.value = false
    fetchRequests()
  } catch (e) {
    console.error('创建授权申请失败', e)
  } finally {
    createLoading.value = false
  }
}

/* ====== 审批 ====== */
const showApprove = ref(false)
const approveLoading = ref(false)
const approveForm = reactive({ id: null, approve: true, comment: '' })
function openApproveDialog(row, approve) {
  approveForm.id = row.id
  approveForm.approve = approve
  approveForm.comment = ''
  showApprove.value = true
}
async function confirmApprove() {
  approveLoading.value = true
  try {
    if (approveForm.approve) {
      await projectAuthApi.approveAuthRequest(approveForm.id, { approve: true, comment: approveForm.comment })
      feedback.success('审批通过')
    } else {
      await projectAuthApi.rejectAuthRequest(approveForm.id, { approve: false, comment: approveForm.comment })
      feedback.success('已驳回')
    }
    showApprove.value = false
    fetchRequests()
  } catch (e) {
    console.error('审批失败', e)
  } finally {
    approveLoading.value = false
  }
}

/* ====== 撤销授权 ====== */
const showRevoke = ref(false)
const revokeLoading = ref(false)
const revokeReason = ref('')
const revokeRow = ref(null)
function openRevokeDialog(row) {
  revokeRow.value = row
  revokeReason.value = ''
  showRevoke.value = true
}
async function confirmRevoke() {
  revokeLoading.value = true
  try {
    await projectAuthApi.revokeAssignment(revokeRow.value.id, revokeReason.value)
    feedback.success('已撤销授权')
    showRevoke.value = false
    fetchAssignments()
  } catch (e) {
    console.error('撤销授权失败', e)
  } finally {
    revokeLoading.value = false
  }
}

/* ====== 延期授权 ====== */
const showExtend = ref(false)
const extendLoading = ref(false)
const extendTime = ref('')
const extendRow = ref(null)
function openExtendDialog(row) {
  extendRow.value = row
  extendTime.value = ''
  showExtend.value = true
}
async function confirmExtend() {
  if (!extendTime.value) {
    feedback.warning('请选择新的过期时间')
    return
  }
  extendLoading.value = true
  try {
    await projectAuthApi.extendAssignment(extendRow.value.id, extendTime.value)
    feedback.success('授权已延期')
    showExtend.value = false
    fetchAssignments()
  } catch (e) {
    console.error('延期授权失败', e)
  } finally {
    extendLoading.value = false
  }
}

/* ====== 详情 ====== */
const showDetail = ref(false)
const currentRow = ref(null)
function viewRequest(row) {
  currentRow.value = row
  showDetail.value = true
}

/* ====== 工具函数 ====== */
function scopeText(scope) {
  return { read_only: '只读', read_write: '读写', review: '可审核' }[scope] || scope || '-'
}
function scopeTagType(scope) {
  return { read_only: 'info', read_write: 'success', review: 'warning' }[scope] || 'info'
}
function reqStatusText(s) {
  return { pending: '待审批', approved: '已通过', rejected: '已驳回' }[s] || s
}
function reqStatusTagType(s) {
  return { pending: 'warning', approved: 'success', rejected: 'danger' }[s] || 'info'
}
function assignStatusText(row) {
  if (row.isActive === 0) return '已撤销'
  if (row.expireAt && new Date(row.expireAt) < new Date()) return '已过期'
  if (row.expireAt && (new Date(row.expireAt) - new Date()) < 7 * 24 * 3600 * 1000) return '即将过期'
  return '有效'
}
function assignStatusTagType(row) {
  if (row.isActive === 0) return 'danger'
  if (row.expireAt && new Date(row.expireAt) < new Date()) return 'danger'
  if (row.expireAt && (new Date(row.expireAt) - new Date()) < 7 * 24 * 3600 * 1000) return 'warning'
  return 'success'
}

onMounted(() => {
  fetchRequests()
})
// keep-alive 重新激活时按当前 Tab 刷新（首次激活自动跳过，避免与 onMounted 请求双发）
useKeepAliveRefresh(() => {
  if (activeTab.value === 'requests') fetchRequests()
  else fetchAssignments()
})
</script>

<style scoped>
.project-auth-management {
  padding: 16px;
}

.auth-tabs {
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
