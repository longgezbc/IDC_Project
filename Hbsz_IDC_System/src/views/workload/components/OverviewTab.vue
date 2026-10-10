<template>
  <div>
    <div
      v-if="hasPermission('workload:edit')"
      class="tab-actions"
    >
      <el-button
        type="primary"
        :icon="Plus"
        @click="openItemDialog()"
      >
        新增清单项
      </el-button>
      <el-button
        :icon="Download"
        :loading="importing"
        @click="handleImportTemplate"
      >
        导入标准模板（106 项）
      </el-button>
    </div>
    <el-table
      v-loading="loading"
      :data="items"
      border
      stripe
      :span-method="categorySpanMethod"
      max-height="620"
      size="small"
    >
      <el-table-column
        label="序号"
        width="56"
        align="center"
      >
        <template #default="{ $index }">
          {{ $index + 1 }}
        </template>
      </el-table-column>
      <el-table-column
        prop="category"
        label="作业类型"
        width="150"
        class-name="category-cell"
      />
      <el-table-column
        prop="workScope"
        label="作业范围"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="ratedWorkload"
        label="额定工作量"
        width="96"
        align="right"
      />
      <el-table-column
        prop="unit"
        label="单位"
        width="64"
        align="center"
      />
      <el-table-column
        prop="remark"
        label="备注"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="totalQuantity"
        label="工作总量"
        width="90"
        align="right"
      />
      <el-table-column
        prop="completedQuantity"
        label="已完成"
        width="90"
        align="right"
      >
        <template #default="{ row }">
          <span :class="{ 'qty-done': Number(row.completedQuantity) > 0 }">{{ formatQty(row.completedQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="剩余工作量"
        width="96"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQtyOrDash(row.remainingQuantity) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="performanceScore"
        label="绩效分"
        width="76"
        align="right"
      />
      <el-table-column
        label="今日工作量"
        width="92"
        align="right"
      >
        <template #default="{ row }">
          <span :class="{ 'qty-today': Number(row.todayQuantity) > 0 }">{{ formatQty(row.todayQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="hasPermission('workload:edit')"
        label="操作"
        width="110"
        fixed="right"
        align="center"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            @click="openItemDialog(row)"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            @click="handleRemoveItem(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          description="暂无清单，请点击「导入标准模板」或「新增清单项」"
          :image-size="80"
        />
      </template>
    </el-table>

    <!-- 清单项编辑弹窗 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="editingItem ? '编辑清单项' : '新增清单项'"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="itemFormRef"
        :model="itemForm"
        :rules="itemRules"
        label-width="90px"
      >
        <el-form-item
          label="作业类型"
          prop="category"
        >
          <el-input
            v-model="itemForm.category"
            maxlength="100"
            placeholder="如：24/48/96芯光缆"
          />
        </el-form-item>
        <el-form-item
          label="作业范围"
          prop="workScope"
        >
          <el-input
            v-model="itemForm.workScope"
            maxlength="200"
            placeholder="如：光缆上架（30-60米）"
          />
        </el-form-item>
        <el-form-item
          label="额定工作量"
          prop="ratedWorkload"
        >
          <el-input-number
            v-model="itemForm.ratedWorkload"
            :min="0.01"
            :precision="2"
            style="width: 180px"
          />
          <span class="form-tip">每人每日配额（绩效折算基数）</span>
        </el-form-item>
        <el-form-item
          label="单位"
          prop="unit"
        >
          <el-input
            v-model="itemForm.unit"
            maxlength="20"
            placeholder="米/条/箱/芯/台/张/柜/组/个"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item
          label="绩效分权重"
          prop="performanceScore"
        >
          <el-input-number
            v-model="itemForm.performanceScore"
            :min="0"
            :precision="2"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item
          label="工作总量"
          prop="totalQuantity"
        >
          <el-input-number
            v-model="itemForm.totalQuantity"
            :min="0"
            :precision="2"
            style="width: 180px"
          />
          <span class="form-tip">计划工程量（用于计算剩余）</span>
        </el-form-item>
        <el-form-item
          label="备注"
          prop="remark"
        >
          <el-input
            v-model="itemForm.remark"
            maxlength="255"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="savingItem"
          @click="handleSaveItem"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 统计总览 Tab（WorkloadStatistics 拆分）
 *
 * 模板同构表格（作业类型合并单元格 + 已完成/剩余/今日量聚合）+ 清单维护
 * （新增/编辑/删除/导入标准模板）。清单与聚合数据由容器通过 props 下发，
 * 增删改成功后 emit('changed') 通知容器重新拉取矩阵。
 */
import { ref } from 'vue'
import { workloadApi } from '@/api/workload'
import { hasPermission } from '@/utils/permission'
import { feedback } from '@/utils/feedback'
import { formatQty, formatQtyOrDash } from '@/utils/format'
import { Download, Plus } from '@element-plus/icons-vue'

const props = defineProps({
  /** 清单项列表（含后端聚合的已完成/剩余/今日量） */
  items: { type: Array, default: () => [] },
  /** 矩阵加载中标记（控制表格 v-loading） */
  loading: { type: Boolean, default: false },
  /** 当前项目 ID（清单维护 payload 用） */
  projectId: { type: Number, default: null }
})

const emit = defineEmits(['changed'])

// ===== 清单维护 =====
const itemDialogVisible = ref(false)
const editingItem = ref(null)
const savingItem = ref(false)
const importing = ref(false)
const itemFormRef = ref(null)
const itemForm = ref(defaultItemForm())
const itemRules = {
  category: [{ required: true, message: '请输入作业类型', trigger: 'blur' }],
  workScope: [{ required: true, message: '请输入作业范围', trigger: 'blur' }],
  ratedWorkload: [{ required: true, message: '请输入额定工作量', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }]
}

function defaultItemForm() {
  return {
    category: '',
    workScope: '',
    ratedWorkload: 1,
    unit: '条',
    performanceScore: 1,
    totalQuantity: 0,
    remark: ''
  }
}

/** 作业类型列纵向合并（模板样式）：相邻同类目合并为一个单元格 */
function categorySpanMethod({ rowIndex, columnIndex }) {
  if (columnIndex !== 1) return
  const list = props.items
  const current = list[rowIndex]?.category
  if (rowIndex === 0 || list[rowIndex - 1]?.category !== current) {
    let span = 1
    while (rowIndex + span < list.length && list[rowIndex + span]?.category === current) span++
    return { rowspan: span, colspan: 1 }
  }
  return { rowspan: 0, colspan: 0 }
}

function openItemDialog(row) {
  editingItem.value = row || null
  if (row) {
    itemForm.value = {
      projectId: props.projectId,
      category: row.category,
      workScope: row.workScope,
      ratedWorkload: Number(row.ratedWorkload),
      unit: row.unit,
      performanceScore: Number(row.performanceScore ?? 1),
      totalQuantity: Number(row.totalQuantity ?? 0),
      remark: row.remark || ''
    }
  } else {
    itemForm.value = { ...defaultItemForm(), projectId: props.projectId }
  }
  itemDialogVisible.value = true
}

async function handleSaveItem() {
  try {
    await itemFormRef.value?.validate()
  } catch {
    return
  }
  savingItem.value = true
  try {
    const payload = { ...itemForm.value, projectId: props.projectId }
    if (editingItem.value) {
      await workloadApi.updateItem(editingItem.value.id, payload)
      feedback.success('清单项更新成功')
    } else {
      await workloadApi.createItem(payload)
      feedback.success('清单项新增成功')
    }
    itemDialogVisible.value = false
    emit('changed')
  } catch (e) {
    feedback.error(e?.message || '保存失败')
  } finally {
    savingItem.value = false
  }
}

async function handleRemoveItem(row) {
  const { ElMessageBox } = await import('element-plus')
  try {
    await ElMessageBox.confirm(
      `确定删除清单项「${row.workScope}」？其全部填报明细（含待审核）将一并删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await workloadApi.removeItem(row.id)
    feedback.success('清单项删除成功')
    emit('changed')
  } catch (e) {
    feedback.error(e?.message || '删除失败')
  }
}

async function handleImportTemplate() {
  // 前置校验：未选项目时 projectId 为空会被 axios 省略，后端报"请求参数缺失"
  if (!props.projectId) {
    feedback.warning('请先选择项目')
    return
  }
  const { ElMessageBox } = await import('element-plus')
  try {
    await ElMessageBox.confirm(
      '将导入 106 项标准作业清单（15 类）。项目已有清单时无法导入，是否继续？',
      '导入标准模板',
      { type: 'info', confirmButtonText: '导入', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  importing.value = true
  try {
    const res = await workloadApi.importTemplate(props.projectId)
    feedback.success(res?.message || '导入成功')
    emit('changed')
  } catch (e) {
    feedback.error(e?.message || '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.tab-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 10px;
}

:deep(.category-cell) {
  font-weight: 600;
  color: #303133;
}

.qty-done {
  color: #67c23a;
  font-weight: 600;
}

.qty-today {
  color: #1890ff;
  font-weight: 600;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #999;
}
</style>
