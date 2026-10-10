<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <span class="page-title">客户管理</span>
        <span class="page-subtitle">维护客户档案信息，便于项目立项时关联选择</span>
      </div>
      <div>
        <BaseButton
          v-if="hasPermission('customer:add')"
          variant="success"
          icon-name="Upload"
          @click="showImportDialog = true"
        >
          批量导入
        </BaseButton>
        <BaseButton
          v-if="hasPermission('customer:add')"
          variant="plain"
          icon-name="Download"
          @click="handleDownloadTemplate"
        >
          下载模板
        </BaseButton>
        <BaseButton
          v-if="hasPermission('customer:add')"
          variant="primary"
          icon-name="Plus"
          @click="openCreateDialog"
        >
          新增客户
        </BaseButton>
      </div>
    </div>

    <!-- 统计卡片（StatCard 全局组件统一视觉） -->
    <el-row
      :gutter="20"
      style="margin-bottom: 20px"
    >
      <el-col
        :xs="12"
        :sm="8"
      >
        <StatCard
          icon="OfficeBuilding"
          tone="primary"
          :value="total"
          label="客户总数"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="8"
      >
        <StatCard
          icon="CircleCheck"
          tone="success"
          :value="enabledCount"
          label="启用客户"
        />
      </el-col>
      <el-col
        :xs="12"
        :sm="8"
      >
        <StatCard
          icon="Folder"
          tone="warning"
          :value="totalProjectCount"
          label="关联项目总数"
        />
      </el-col>
    </el-row>

    <!-- P1-2.2：使用 CrudTable 通用布局组件；本页数据流经 customerStore 管理（customers/total/loading 均为 store 的 computed），
         不适合直接替换为 useCrudList（需保持 store 缓存与统计 computed 一致性），因此采用 CrudTable 布局-only 迁移 -->
    <CrudTable
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :data="customers"
      :loading="loading"
      :total="total"
      @change="loadList"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <span class="title-chip title-chip--primary"><el-icon><OfficeBuilding /></el-icon></span>
          <span class="toolbar-title">客户列表</span>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="queryParams.keyword"
            placeholder="客户名称/编号/联系人/电话"
            clearable
            style="width: 280px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select
            v-model="queryParams.status"
            placeholder="状态"
            clearable
            style="width: 140px"
            @change="handleSearch"
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
      />
      <el-table-column
        prop="customerCode"
        label="客户编号"
        width="140"
      />
      <el-table-column
        label="客户名称"
        min-width="200"
      >
        <template #default="{ row }">
          <div class="customer-name-cell">
            <span class="customer-name">{{ row.name }}</span>
            <el-tag
              v-if="row.status === 1"
              size="small"
              type="success"
              effect="light"
            >
              启用
            </el-tag>
            <el-tag
              v-else
              size="small"
              type="info"
              effect="light"
            >
              禁用
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="联系人"
        width="120"
      >
        <template #default="{ row }">
          <div>{{ row.contactPerson || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column
        label="联系电话"
        width="140"
      >
        <template #default="{ row }">
          {{ row.contactPhone || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="邮箱"
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.contactEmail || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="地址"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.address || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="关联项目"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.projectCount > 0 ? 'warning' : 'info'"
            effect="plain"
          >
            {{ row.projectCount || 0 }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="remark"
        label="备注"
        min-width="150"
        show-overflow-tooltip
      />
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
            @click="handleViewDetail(row)"
          >
            详情
          </BaseButton>
          <BaseButton
            v-if="hasPermission('customer:edit')"
            variant="link"
            size="small"
            icon-name="Edit"
            @click="openEditDialog(row)"
          >
            编辑
          </BaseButton>
          <BaseButton
            v-if="hasPermission('customer:remove')"
            variant="link-danger"
            size="small"
            icon-name="Delete"
            :disabled="row.projectCount > 0"
            :title="row.projectCount > 0 ? '存在关联项目，无法删除' : '删除'"
            @click="handleDelete(row)"
          >
            删除
          </BaseButton>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState description="暂无客户数据" />
      </template>
    </CrudTable>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="showDialog"
      :title="editingForm.id ? '编辑客户' : '新增客户'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="editingForm"
        :rules="formRules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              label="客户编号"
              prop="code"
            >
              <el-input
                v-model="editingForm.code"
                placeholder="如 HB-KH-2024-001"
                :disabled="!!editingForm.id"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="客户名称"
              prop="name"
            >
              <el-input
                v-model="editingForm.name"
                placeholder="请输入客户名称"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              label="联系人"
              prop="contactPerson"
            >
              <el-input
                v-model="editingForm.contactPerson"
                placeholder="请输入联系人"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="联系电话"
              prop="contactPhone"
            >
              <el-input
                v-model="editingForm.contactPhone"
                placeholder="请输入联系电话"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item
          label="联系邮箱"
          prop="contactEmail"
        >
          <el-input
            v-model="editingForm.contactEmail"
            placeholder="请输入邮箱（可选）"
          />
        </el-form-item>
        <el-form-item
          label="客户地址"
          prop="address"
        >
          <el-input
            v-model="editingForm.address"
            placeholder="请输入客户地址（可选）"
          />
        </el-form-item>
        <el-form-item
          label="状态"
          prop="status"
        >
          <el-radio-group v-model="editingForm.status">
            <el-radio :value="1">
              启用
            </el-radio>
            <el-radio :value="0">
              禁用
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="备注"
          prop="remark"
        >
          <el-input
            v-model="editingForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showDialog = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          保存
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 客户详情对话框 -->
    <el-dialog
      v-model="showDetail"
      title="客户详情"
      width="640px"
    >
      <el-descriptions
        v-if="currentDetail"
        :column="2"
        border
        size="small"
      >
        <el-descriptions-item label="客户编号">
          {{ currentDetail.customerCode || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="客户名称">
          {{ currentDetail.name || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="联系人">
          {{ currentDetail.contactPerson || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="联系电话">
          {{ currentDetail.contactPhone || '-' }}
        </el-descriptions-item>
        <el-descriptions-item
          label="联系邮箱"
          :span="2"
        >
          {{ currentDetail.contactEmail || '-' }}
        </el-descriptions-item>
        <el-descriptions-item
          label="客户地址"
          :span="2"
        >
          {{ currentDetail.address || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag
            v-if="currentDetail.status === 1"
            size="small"
            type="success"
          >
            启用
          </el-tag>
          <el-tag
            v-else
            size="small"
            type="info"
          >
            禁用
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联项目数">
          <el-tag
            :type="currentDetail.projectCount > 0 ? 'warning' : 'info'"
            effect="plain"
          >
            {{ currentDetail.projectCount || 0 }} 个
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item
          label="备注"
          :span="2"
        >
          {{ currentDetail.remark || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDateTime(currentDetail.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ formatDateTime(currentDetail.updatedAt) }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <BaseButton
          v-if="hasPermission('customer:edit')"
          variant="primary"
          @click="openEditDialog(currentDetail)"
        >
          编辑
        </BaseButton>
        <BaseButton @click="showDetail = false">
          关闭
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="showImportDialog"
      title="批量导入客户"
      width="500px"
      @closed="resetImportDialog"
    >
      <!-- 关键填写要点前置（详细规则见模板内「填写说明」sheet） -->
      <el-alert
        type="info"
        :closable="false"
        class="import-tips"
      >
        <p>1. 客户编号、客户名称、联系人为必填项</p>
        <p>2. 客户编号系统内唯一（建议格式：CUST-年份-序号，如 CUST-2026-001）</p>
        <p>3. 模板第 2-3 行为示例数据，请修改或删除后再上传</p>
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
 * 客户管理页面
 * 业务用途：维护客户档案信息（名称/编号/联系人/电话/邮箱/地址/状态/备注），
 *          支持新增、编辑、删除、按关键字与状态筛选、查看详情。
 * 权限要求：
 *   - 列表查看：customer:query（路由 meta 控制）
 *   - 新增：customer:add
 *   - 编辑：customer:edit
 *   - 删除：customer:remove
 * 业务规则：
 *   - 客户编号唯一，新建后不可修改
 *   - 存在关联项目的客户不可删除（按钮 disabled 并提示原因）
 *
 * P1-2.2：使用 CrudTable 通用布局组件封装 el-card + toolbar + el-table + 分页的模板与 CSS。
 *         本页数据流经 customerStore 管理（customers/total/loading 均为 store 的 computed，
 *         enabledCount/totalProjectCount 统计也依赖 store 数据），不适合直接替换为 useCrudList，
 *         因此采用 CrudTable 布局-only 迁移，保留 store-based 数据流。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, reactive, computed, onMounted } from 'vue'
import { useCustomerStore } from '@/stores/customer'
import { customerApi } from '@/api/customer'
import { hasPermission } from '@/utils/permission'
import { feedback } from '@/utils/feedback'
import { formatDateTime } from '@/utils/format'
// P1-2.2：通用列表布局组件
import CrudTable from '@/components/CrudTable.vue'
import { useImport } from '@/composables/useImport'

const customerStore = useCustomerStore()

// 列表数据（来自 store 的 computed）
const customers = computed(() => customerStore.customers)
const total = computed(() => customerStore.total)
const loading = computed(() => customerStore.loading)
const enabledCount = computed(() => customers.value.filter(c => c.status === 1).length)
const totalProjectCount = computed(() => customers.value.reduce((sum, c) => sum + (Number(c.projectCount) || 0), 0))

// 查询参数：绑定到 CrudTable 的 v-model，分页变化时 @change 触发 loadList
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: null
})

// 对话框状态
const showDialog = ref(false)
const showDetail = ref(false)
const submitting = ref(false)
const currentDetail = ref(null)
const formRef = ref(null)

// 编辑表单
const editingForm = reactive({
  id: null,
  code: '',
  name: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  address: '',
  status: 1,
  remark: ''
})

// 表单校验规则
const formRules = {
  code: [
    { required: true, message: '请输入客户编号', trigger: 'blur' },
    { max: 50, message: '客户编号长度不能超过50', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入客户名称', trigger: 'blur' },
    { max: 200, message: '客户名称长度不能超过200', trigger: 'blur' }
  ],
  contactPerson: [
    { required: true, message: '请输入联系人', trigger: 'blur' },
    { max: 50, message: '联系人长度不能超过50', trigger: 'blur' }
  ],
  contactPhone: [
    { max: 20, message: '联系电话长度不能超过20', trigger: 'blur' }
  ],
  contactEmail: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

/** 加载客户列表（通过 store 拉取数据） */
async function loadList() {
  try {
    await customerStore.fetchList({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword || undefined,
      status: queryParams.status === null || queryParams.status === undefined ? undefined : queryParams.status
    })
  } catch (error) {
    console.error('加载客户列表失败:', error)
  }
}

/** 搜索：重置到第 1 页并加载 */
function handleSearch() {
  queryParams.pageNum = 1
  loadList()
}

/** 重置筛选条件并回到第 1 页 */
function handleReset() {
  queryParams.keyword = ''
  queryParams.status = null
  queryParams.pageNum = 1
  loadList()
}

/** 打开新增对话框 */
function openCreateDialog() {
  Object.assign(editingForm, {
    id: null,
    code: '',
    name: '',
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    address: '',
    status: 1,
    remark: ''
  })
  showDialog.value = true
}

/** 打开编辑对话框，加载已有数据 */
function openEditDialog(row) {
  Object.assign(editingForm, {
    id: row.id,
    code: row.customerCode || '',
    name: row.name || '',
    contactPerson: row.contactPerson || '',
    contactPhone: row.contactPhone || '',
    contactEmail: row.contactEmail || '',
    address: row.address || '',
    status: row.status === 0 ? 0 : 1,
    remark: row.remark || ''
  })
  showDetail.value = false
  showDialog.value = true
}

/** 查看详情 */
async function handleViewDetail(row) {
  try {
    const data = await customerStore.fetchDetail(row.id)
    currentDetail.value = data
    showDetail.value = true
  } catch (error) {
    console.error('加载客户详情失败:', error)
  }
}

/** 提交表单（新增/编辑共用） */
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    const payload = {
      code: editingForm.code.trim(),
      name: editingForm.name.trim(),
      contactPerson: editingForm.contactPerson.trim(),
      contactPhone: editingForm.contactPhone ? editingForm.contactPhone.trim() : undefined,
      contactEmail: editingForm.contactEmail || undefined,
      address: editingForm.address || undefined,
      status: editingForm.status,
      remark: editingForm.remark || undefined
    }
    if (editingForm.id) {
      await customerStore.updateCustomer(editingForm.id, payload)
    } else {
      await customerStore.addCustomer(payload)
    }
    showDialog.value = false
    loadList()
  } catch (error) {
    console.error('保存客户失败:', error)
  } finally {
    submitting.value = false
  }
}

/** 删除客户（带二次确认） */
async function handleDelete(row) {
  try {
    await feedback.confirmDelete(`确定要删除客户「${row.name}」吗？该操作不可恢复。`)
  } catch (e) {
    return // 用户取消
  }
  try {
    await customerStore.deleteCustomer(row.id)
    // 删除后若当前页空了，回到上一页
    if (customers.value.length === 0 && queryParams.pageNum > 1) {
      queryParams.pageNum--
    }
    loadList()
  } catch (error) {
    console.error('删除客户失败:', error)
  }
}

/* ===== 批量导入 / 模板下载 ===== */

// P3 重构：公共导入流程统一由 useImport 提供，此处仅注入客户模块差异项
const {
  showImportDialog, importLoading, uploadRef,
  handleFileChange, handleExceed, resetImportDialog,
  handleDownloadTemplate, handleImport
} = useImport({
  downloadTemplate: () => customerApi.downloadImportTemplate(),
  importFn: (file) => customerApi.importCustomers(file),
  templateName: 'customer_import_template.xlsx',
  onSuccess: () => loadList()
})

// P3 重构：formatDateTime 改用 @/utils/format（@shared/format）统一实现，
// 删除本地手写 padStart 版本

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.page-subtitle {
  margin-left: 12px;
  color: #999;
  font-size: 13px;
}
/* 统计卡样式已由全局 StatCard 组件接管 */
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.customer-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.customer-name {
  font-weight: 500;
  color: #303133;
}
/* 批量导入对话框：前置要点提示区（紧凑行距） */
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
