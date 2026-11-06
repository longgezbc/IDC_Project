<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">材料库存管理</div>
        <div class="page-subtitle">材料入库/出库/库存查询/预警管理</div>
      </div>
      <div class="header-actions">
        <BaseButton v-if="canInbound" variant="success" icon-name="Top" @click="openInbound">入库</BaseButton>
        <BaseButton v-if="canOutbound" variant="warning" icon-name="Bottom" @click="openOutbound">出库</BaseButton>
        <BaseButton variant="primary" icon-name="Plus" @click="openCreate">新建材料</BaseButton>
        <BaseButton variant="plain" icon-name="Upload" @click="showImportDialog = true">批量导入</BaseButton>
        <BaseButton variant="plain" icon-name="Download" @click="handleDownloadTemplate">下载模板</BaseButton>
      </div>
    </div>

    <!-- 预警提示条 -->
    <el-alert v-if="alerts.length > 0" type="warning" :closable="false" show-icon
      :title="`${alerts.length} 个材料库存预警待处理`" style="margin-bottom: 12px">
      <template #default>
        <span v-for="a in alerts.slice(0,3)" :key="a.id" class="alert-item">
          {{ a.materialName }}（库存{{ a.currentStock }} 低于安全库存{{ a.safetyStock }}）
        </span>
        <BaseButton variant="link" size="small" @click="showAlertsDialog = true">查看全部</BaseButton>
      </template>
    </el-alert>

    <div class="card-box">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="名称">
          <el-input v-model="searchForm.keyword" placeholder="材料名称/编号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="全部分类" clearable style="width:140px">
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
      <template #toolbar><div class="toolbar-left"><span class="page-total-text">共 {{ total }} 种材料</span></div></template>
      <el-table-column prop="code" label="编号" width="120" />
      <el-table-column prop="name" label="材料名称" min-width="160" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="spec" label="规格" width="120" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column label="当前库存" width="100" align="right">
        <template #default="{ row }">
          <span :class="{ 'stock-warning': row.currentStock < row.safetyStock }">{{ row.currentStock }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="safetyStock" label="安全库存" width="90" align="right" />
      <el-table-column prop="unitPrice" label="单价" width="80" align="right">
        <template #default="{ row }">{{ row.unitPrice ? '¥' + row.unitPrice : '-' }}</template>
      </el-table-column>
      <el-table-column prop="warehouse" label="仓库" width="80" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <BaseButton variant="link" size="small" @click="openTransactions(row)">流水</BaseButton>
          <BaseButton variant="link" size="small" @click="openEdit(row)">编辑</BaseButton>
          <BaseButton variant="link-warning" size="small" @click="openInboundFor(row)">入库</BaseButton>
          <BaseButton variant="link-danger" size="small" @click="openOutboundFor(row)">出库</BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- 新建/编辑材料 -->
    <el-dialog v-model="materialDialog" :title="editingId ? '编辑材料' : '新建材料'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编号" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="form.category" allow-create filterable style="width:100%"><el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" /></el-select></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" /></el-form-item>
        <el-form-item label="单位" prop="unit"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="安全库存"><el-input-number v-model="form.safetyStock" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="form.unitPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="仓库"><el-input v-model="form.warehouse" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="materialDialog = false">取消</BaseButton>
        <BaseButton variant="primary" :loading="submitting" @click="handleSubmitMaterial">确定</BaseButton>
      </template>
    </el-dialog>

    <!-- 入库/出库对话框 -->
    <el-dialog v-model="stockDialog" :title="stockType === 'in' ? '材料入库' : '材料出库'" width="480px">
      <div v-if="stockTarget" class="stock-target-info">
        <span>{{ stockTarget.name }}</span>
        <span class="stock-current">当前库存：{{ stockTarget.currentStock }} {{ stockTarget.unit }}</span>
      </div>
      <el-form :model="stockForm" label-width="80px" style="margin-top:16px">
        <el-form-item label="数量" required>
          <el-input-number v-model="stockForm.quantity" :min="0.01" :precision="2" style="width:100%" />
        </el-form-item>
        <!-- 出库必须记录去向项目（业务规则：材料以项目为单位领用，流水需可追溯） -->
        <el-form-item v-if="stockType === 'out'" label="去向项目" required>
          <el-select
            v-model="stockForm.projectId"
            placeholder="必选，材料领用的去向项目"
            filterable
            style="width:100%"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="stockForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="stockDialog = false">取消</BaseButton>
        <BaseButton variant="primary" :loading="submitting" @click="handleSubmitStock">确定</BaseButton>
      </template>
    </el-dialog>

    <!-- 批量导入对话框（出库物料导入模板：标准型号/物料类型/厂商/当前状态/数量） -->
    <el-dialog v-model="showImportDialog" title="批量导入材料" width="500px" @closed="resetImportDialog">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        <p>1. 表头须为：标准型号 / 物料类型 / 厂商 / 当前状态 / 数量</p>
        <p>2. 同一标准型号多行会自动合并累加数量</p>
        <p>3. 导入后数量登记为初始库存（台账状态记入备注），实际领用去向可在项目内按出库补录</p>
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
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx / .xls 格式，可先下载模板填写</div>
        </template>
      </el-upload>
      <template #footer>
        <BaseButton @click="showImportDialog = false">取消</BaseButton>
        <BaseButton variant="primary" :loading="importLoading" @click="handleImport">确认导入</BaseButton>
      </template>
    </el-dialog>

    <!-- 变更流水对话框 -->
    <el-dialog v-model="txnDialog" title="库存变更流水" width="800px">
      <el-table :data="txnList" border size="small" max-height="400">
        <el-table-column prop="transactionType" label="类型" width="70">
          <template #default="{ row }">
            <el-tag :type="row.transactionType === 'in' ? 'success' : row.transactionType === 'out' ? 'danger' : 'warning'" size="small">
              {{ row.transactionType === 'in' ? '入库' : row.transactionType === 'out' ? '出库' : '调整' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="beforeStock" label="变更前" width="80" align="right" />
        <el-table-column prop="afterStock" label="变更后" width="80" align="right" />
        <el-table-column prop="operatorName" label="操作人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="150" />
      </el-table>
    </el-dialog>

    <!-- 预警列表对话框 -->
    <el-dialog v-model="showAlertsDialog" title="库存预警" width="600px">
      <el-table :data="alerts" border size="small">
        <el-table-column prop="materialName" label="材料" min-width="140" />
        <el-table-column prop="currentStock" label="当前库存" width="90" align="right" />
        <el-table-column prop="safetyStock" label="安全库存" width="90" align="right" />
        <el-table-column prop="createdAt" label="预警时间" width="150" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <BaseButton variant="link" size="small" @click="handleResolveAlert(row)">处理</BaseButton>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { materialApi } from '@/api/material'
import { projectApi } from '@/api/project'
import { hasPermission } from '@/utils/permission'
import { feedback } from '@/utils/feedback'
import { useImport } from '@/composables/useImport'
import { UploadFilled } from '@element-plus/icons-vue'
import CrudTable from '@/components/CrudTable.vue'

const canInbound = hasPermission('material:inbound')
const canOutbound = hasPermission('material:outbound')

// 批量导入（出库物料导入模板）
const {
  showImportDialog, importLoading, uploadRef,
  handleFileChange, handleExceed, resetImportDialog,
  handleDownloadTemplate, handleImport
} = useImport({
  downloadTemplate: () => materialApi.downloadImportTemplate(),
  importFn: (file) => materialApi.importMaterials(file),
  templateName: 'material_import_template.xlsx',
  onSuccess: () => { fetchList(); fetchAlerts() }
})

const list = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchForm = reactive({ keyword: '', category: '' })
const categoryOptions = ref(['光缆', '网线', '模块', '配线架', '机柜', '其他'])
const alerts = ref([])
const showAlertsDialog = ref(false)

// 材料表单
const materialDialog = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ code: '', name: '', category: '', spec: '', unit: '个', safetyStock: 0, unitPrice: null, warehouse: '', remark: '' })
const rules = { code: [{ required: true, message: '必填', trigger: 'blur' }], name: [{ required: true, message: '必填', trigger: 'blur' }], unit: [{ required: true, message: '必填', trigger: 'blur' }] }

// 入库/出库
const stockDialog = ref(false)
const stockType = ref('in')
const stockTarget = ref(null)
const stockForm = reactive({ quantity: 1, projectId: null, remark: '' })
// 出库去向项目选项（可搜索下拉）
const projectOptions = ref([])

async function fetchProjectOptions() {
  try {
    const res = await projectApi.getList({ pageNum: 1, pageSize: 200 })
    projectOptions.value = ((res.data && res.data.records) || []).map(p => ({ id: p.id, name: p.name }))
  } catch (e) { /* silent */ }
}

// 流水
const txnDialog = ref(false)
const txnList = ref([])

async function fetchList() {
  loading.value = true
  try {
    // request 拦截器返回完整响应包 {code,message,data}，分页数据在 data 字段
    const res = await materialApi.getList({ pageNum: currentPage.value, pageSize: pageSize.value, keyword: searchForm.keyword, category: searchForm.category })
    const data = res.data || {}
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) { feedback.error(e.message || '查询失败') }
  finally { loading.value = false }
}

async function fetchAlerts() {
  // 信封解包：预警列表在响应包 data 字段
  try {
    const resp = await materialApi.getAlerts()
    alerts.value = resp.data || []
  } catch (e) { /* silent */ }
}

function handleSearch() { currentPage.value = 1; fetchList() }
function resetSearch() { searchForm.keyword = ''; searchForm.category = ''; handleSearch() }

function openCreate() {
  editingId.value = null
  Object.assign(form, { code: '', name: '', category: '', spec: '', unit: '个', safetyStock: 0, unitPrice: null, warehouse: '', remark: '' })
  materialDialog.value = true
}
function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, { code: row.code, name: row.name, category: row.category, spec: row.spec, unit: row.unit, safetyStock: row.safetyStock, unitPrice: row.unitPrice, warehouse: row.warehouse, remark: row.remark })
  materialDialog.value = true
}

async function handleSubmitMaterial() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (editingId.value) { await materialApi.update(editingId.value, { ...form }); feedback.success('更新成功') }
    else { await materialApi.create({ ...form }); feedback.success('创建成功') }
    materialDialog.value = false; fetchList()
  } catch (e) { feedback.error(e.message || '操作失败') }
  finally { submitting.value = false }
}

function openInbound() { openStock('in', null) }
function openOutbound() { openStock('out', null) }
function openInboundFor(row) { openStock('in', row) }
function openOutboundFor(row) { openStock('out', row) }
function openStock(type, row) {
  stockType.value = type
  stockTarget.value = row
  Object.assign(stockForm, { quantity: 1, projectId: null, remark: '' })
  stockDialog.value = true
}

async function handleSubmitStock() {
  if (!stockTarget.value) { feedback.warning('请先选择材料'); return }
  submitting.value = true
  try {
    const data = { quantity: stockForm.quantity, remark: stockForm.remark }
    if (stockType.value === 'in') {
      await materialApi.stockIn(stockTarget.value.id, data)
      feedback.success('入库成功')
    } else {
      // 业务规则：出库必须记录去向项目，流水才可追溯到具体项目
      if (!stockForm.projectId) { feedback.warning('出库必须选择去向项目'); submitting.value = false; return }
      data.projectId = stockForm.projectId
      await materialApi.stockOut(stockTarget.value.id, data)
      feedback.success('出库成功')
    }
    stockDialog.value = false; fetchList(); fetchAlerts()
  } catch (e) { feedback.error(e.message || '操作失败') }
  finally { submitting.value = false }
}

async function openTransactions(row) {
  try {
    const res = await materialApi.getTransactions(row.id)
    txnList.value = (res.data && res.data.records) || []
    txnDialog.value = true
  } catch (e) { feedback.error(e.message || '查询流水失败') }
}

async function handleResolveAlert(alert) {
  try {
    await materialApi.resolveAlert(alert.id)
    feedback.success('预警已处理')
    fetchAlerts()
  } catch (e) { feedback.error(e.message || '处理失败') }
}

onMounted(() => { fetchList(); fetchAlerts(); fetchProjectOptions() })
</script>

<style scoped>
.page-total-text { font-size: 13px; color: #909399; }
.stock-warning { color: #e6a23c; font-weight: 600; }
.alert-item { margin-right: 12px; font-size: 13px; }
.stock-target-info { display: flex; justify-content: space-between; padding: 8px 12px; background: #f5f7fa; border-radius: 4px; }
.stock-current { color: #909399; font-size: 13px; }
</style>
