<template>
  <div>
    <div class="tab-actions report-actions">
      <div class="worker-manager">
        <span class="worker-label">施工人员：</span>
        <el-select
          v-model="workerAddId"
          filterable
          placeholder="从项目组成员添加"
          size="small"
          style="width: 200px"
          @change="addWorkerColumn"
        >
          <el-option
            v-for="m in addableMembers"
            :key="m.userId"
            :label="m.name + (m.roleInTeam === 'leader' ? '（组长）' : '')"
            :value="m.userId"
          />
        </el-select>
        <el-tag
          v-if="teamWorkers.length === 0"
          type="info"
          size="small"
          class="worker-tag"
        >
          项目组暂无成员，请先在项目组管理中添加
        </el-tag>
        <el-tag
          v-for="col in reportColumns"
          :key="col.name"
          :type="columnTagType(col)"
          :closable="removableNames.includes(col.name)"
          class="worker-tag"
          @close="removeWorkerColumn(col.name)"
        >
          {{ col.name }}{{ col.roleInTeam === 'leader' ? '·组长' : '' }}{{ col.legacy ? '·历史' : '' }}
        </el-tag>
      </div>
      <el-button
        v-if="hasPermission('workload:report')"
        type="primary"
        :icon="Check"
        :loading="saving"
        :disabled="!projectId || items.length === 0"
        @click="handleSaveRecords"
      >
        提交审核
      </el-button>
    </div>
    <el-table
      v-loading="loading"
      :data="items"
      border
      stripe
      max-height="620"
      size="small"
    >
      <el-table-column
        prop="category"
        label="作业类型"
        width="130"
        show-overflow-tooltip
      />
      <el-table-column
        prop="workScope"
        label="作业范围"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="unit"
        label="单位"
        width="60"
        align="center"
      />
      <el-table-column
        prop="ratedWorkload"
        label="额定"
        width="70"
        align="right"
      />
      <el-table-column
        v-for="col in reportColumns"
        :key="col.name"
        min-width="112"
        align="center"
      >
        <template #header>
          <span>{{ col.name }}</span>
          <el-tag
            v-if="col.roleInTeam === 'leader'"
            size="small"
            type="warning"
            class="col-sub-tag"
          >
            组长
          </el-tag>
          <el-tag
            v-else-if="col.legacy"
            size="small"
            type="info"
            class="col-sub-tag"
          >
            历史
          </el-tag>
        </template>
        <template #default="{ row }">
          <el-tooltip
            :disabled="!cellTip(row.id, col.name)"
            :content="cellTip(row.id, col.name)"
            placement="top"
          >
            <el-input-number
              :model-value="editMatrix[row.id]?.[col.name]"
              :min="0"
              :controls="false"
              size="small"
              :disabled="col.legacy || isCellLocked(row.id, col.name)"
              :class="cellClass(row.id, col.name)"
              style="width: 90px"
              @update:model-value="(val) => onCellChange(row.id, col.name, val)"
            />
          </el-tooltip>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          description="暂无清单项，请先在「统计总览」导入或新增清单"
          :image-size="80"
        />
      </template>
    </el-table>
    <!-- 单元格状态图例 -->
    <div class="cell-legend">
      <span class="legend-item"><i class="legend-dot dot-approved" />已审核（锁定，计入统计）</span>
      <span class="legend-item"><i class="legend-dot dot-pending" />待审核（可修改）</span>
      <span class="legend-item"><i class="legend-dot dot-rejected" />已驳回（可修改后重新提交）</span>
      <span class="legend-item"><i class="legend-dot dot-legacy" />历史自由填报（只读）</span>
    </div>
    <!-- 底部绩效行：已审核绩效（计入统计） + 待审核绩效预览 -->
    <div
      v-if="reportColumns.length"
      class="perf-footer"
    >
      <div class="perf-row">
        <span class="perf-label">已审核绩效：</span>
        <template v-if="approvedPerfEntries.length">
          <span
            v-for="[name, val] in approvedPerfEntries"
            :key="'a-' + name"
            class="perf-item"
          >{{ name }} {{ val }}</span>
        </template>
        <span
          v-else
          class="perf-muted"
        >暂无（审核通过后计入统计）</span>
      </div>
      <div class="perf-row">
        <span class="perf-label">待审核绩效：</span>
        <template v-if="pendingPerfEntries.length">
          <span
            v-for="[name, val] in pendingPerfEntries"
            :key="'p-' + name"
            class="perf-item perf-pending"
          >{{ name }} {{ val }}</span>
        </template>
        <span
          v-else
          class="perf-muted"
        >暂无待审核填报</span>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 矩阵代填 Tab（WorkloadStatistics 拆分）
 *
 * 项目组成员×清单项矩阵编辑（PM 批量代填多人场景），提交后进入待审核；
 * 单元格按审核状态着色（approved 锁定 / pending 待审 / rejected 驳回可改）。
 *
 * 矩阵数据（items/matrixWorkers/matrixCells/workerPerformance/teamWorkers）由容器
 * 通过 props 下发；matrixCells 每次整体赋新值，本组件 watch 引用替换重建编辑矩阵
 * （回显各状态单元格的数量）；提交成功后 emit('changed') 通知容器重新拉取。
 */
import { computed, reactive, ref, watch } from 'vue'
import { workloadApi } from '@/api/workload'
import { hasPermission } from '@/utils/permission'
import { feedback } from '@/utils/feedback'
import { Check } from '@element-plus/icons-vue'

const props = defineProps({
  /** 当前项目 ID（提交填报 payload 用） */
  projectId: { type: Number, default: null },
  /** 填报日期 YYYY-MM-DD（提交填报 payload 用） */
  statDate: { type: String, required: true },
  /** 清单项列表（矩阵行） */
  items: { type: Array, default: () => [] },
  /** 当日有填报的人员（含历史自由文本，userId 可为 null） */
  matrixWorkers: { type: Array, default: () => [] },
  /** itemId → workerName → {quantity, reviewStatus, reviewRemark}（整体赋新值） */
  matrixCells: { type: Object, default: () => ({}) },
  /** workerName → 已审核绩效（后端口径） */
  workerPerformance: { type: Object, default: () => ({}) },
  /** 项目组在组成员（可填报人员来源） */
  teamWorkers: { type: Array, default: () => [] },
  /** 矩阵加载中标记（控制表格 v-loading） */
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['changed'])

// ===== 编辑状态（组件内部） =====
const addedWorkerIds = ref([])     // 用户手动添加的人员列
const workerAddId = ref(null)
const editMatrix = reactive({})    // itemId → workerName → 当前编辑值（>0 才保留）
const saving = ref(false)

/** 切换项目后清空手动添加的人员列（人员列为项目维度） */
watch(() => props.projectId, () => {
  addedWorkerIds.value = []
})

/** matrixCells 整体替换时重建编辑矩阵：回显各状态单元格的数量（approved 只读由模板层控制） */
watch(() => props.matrixCells, (cells) => {
  for (const k of Object.keys(editMatrix)) delete editMatrix[k]
  for (const [itemId, rowCells] of Object.entries(cells || {})) {
    editMatrix[itemId] = {}
    for (const [name, cell] of Object.entries(rowCells)) {
      editMatrix[itemId][name] = Number(cell.quantity || 0)
    }
  }
}, { immediate: true })

/**
 * 填报矩阵展示列 = 当日有记录的授权成员 + 手动添加的成员（组长置前），尾接历史遗留列。
 * 历史遗留列：矩阵中存在、但不在项目组在册名单中的人员（旧版自由文本填报）。
 */
const reportColumns = computed(() => {
  const matrixNames = new Set(props.matrixWorkers.map(w => w.name))
  const shown = props.teamWorkers.filter(m =>
    matrixNames.has(m.name) || addedWorkerIds.value.includes(m.userId))
  const shownNames = new Set(shown.map(m => m.name))
  const legacy = props.matrixWorkers.filter(w => !shownNames.has(w.name))
  return [
    ...shown.map(m => ({ userId: m.userId, name: m.name, roleInTeam: m.roleInTeam, legacy: false })),
    ...legacy.map(w => ({ userId: w.userId, name: w.name, roleInTeam: w.roleInTeam || 'member', legacy: true }))
  ]
})

/** 下拉可添加的成员：尚未展示为列的在组成员 */
const addableMembers = computed(() => {
  const shownNames = new Set(reportColumns.value.map(c => c.name))
  return props.teamWorkers.filter(m => !shownNames.has(m.name))
})

/** 可移除列：手动添加且当日无记录（有记录的列承载真实数据，不允许隐藏） */
const removableNames = computed(() => {
  const matrixNames = new Set(props.matrixWorkers.map(w => w.name))
  return reportColumns.value
    .filter(c => !c.legacy && addedWorkerIds.value.includes(c.userId) && !matrixNames.has(c.name))
    .map(c => c.name)
})

/** 已审核绩效条目（仅展示非零人员） */
const approvedPerfEntries = computed(() =>
  Object.entries(props.workerPerformance)
    .filter(([, v]) => Number(v) > 0)
    .map(([name, v]) => [name, Number(v).toFixed(2)])
)

/** 待审核绩效预览条目：编辑矩阵中非 approved 单元格的 Σ(数量÷额定×权重) */
const pendingPerfEntries = computed(() => {
  const result = {}
  for (const item of props.items) {
    const rated = Number(item.ratedWorkload) > 0 ? Number(item.ratedWorkload) : 1
    const weight = Number(item.performanceScore ?? 1)
    const row = editMatrix[item.id] || {}
    for (const [worker, qty] of Object.entries(row)) {
      if (props.matrixCells[item.id]?.[worker]?.reviewStatus === 'approved') continue
      const q = Number(qty || 0)
      if (q > 0) result[worker] = (result[worker] || 0) + (q / rated) * weight
    }
  }
  return Object.entries(result).map(([name, v]) => [name, v.toFixed(2)])
})

/** 人员列 tag 样式：组长橙 / 历史灰 / 普通蓝 */
function columnTagType(col) {
  if (col.legacy) return 'info'
  if (col.roleInTeam === 'leader') return 'warning'
  return 'primary'
}

/** 从下拉添加一个成员列（可连续添加，选后自动清空） */
function addWorkerColumn(userId) {
  if (userId && !addedWorkerIds.value.includes(userId)) {
    addedWorkerIds.value.push(userId)
  }
  workerAddId.value = null
}

/** 移除手动添加且无记录的人员列（同时清理该列未保存的编辑值） */
function removeWorkerColumn(name) {
  const m = props.teamWorkers.find(t => t.name === name)
  if (m) addedWorkerIds.value = addedWorkerIds.value.filter(id => id !== m.userId)
  for (const row of Object.values(editMatrix)) delete row[name]
}

/** 矩阵单元格数据（含审核状态） */
function getCell(itemId, workerName) {
  return props.matrixCells[itemId]?.[workerName] || null
}

/** approved 单元格锁定（已计入统计，需驳回后才可修改） */
function isCellLocked(itemId, workerName) {
  return getCell(itemId, workerName)?.reviewStatus === 'approved'
}

/** 单元格审核状态样式类 */
function cellClass(itemId, workerName) {
  const status = getCell(itemId, workerName)?.reviewStatus
  if (status === 'approved') return 'cell-approved'
  if (status === 'pending') return 'cell-pending'
  if (status === 'rejected') return 'cell-rejected'
  return ''
}

/** 单元格悬浮提示（驳回原因 / 锁定说明） */
function cellTip(itemId, workerName) {
  const cell = getCell(itemId, workerName)
  if (!cell) return ''
  if (cell.reviewStatus === 'rejected') {
    return cell.reviewRemark ? `驳回原因：${cell.reviewRemark}` : '已被驳回，可修改后重新提交'
  }
  if (cell.reviewStatus === 'approved') {
    return '已审核通过，不可修改；如需调整请联系项目经理驳回后重新填报'
  }
  return ''
}

/** 单元格变化：>0 写入矩阵，0/null 清理，避免提交无效记录 */
function onCellChange(itemId, worker, val) {
  const q = (val === null || val === undefined) ? 0 : Number(val)
  if (q > 0) {
    if (!editMatrix[itemId]) editMatrix[itemId] = {}
    editMatrix[itemId][worker] = q
  } else if (editMatrix[itemId]) {
    delete editMatrix[itemId][worker]
    if (Object.keys(editMatrix[itemId]).length === 0) delete editMatrix[itemId]
  }
}

/** 提交填报：仅提交可编辑（非 approved、非历史遗留）单元格，进入待审核 */
async function handleSaveRecords() {
  const nameToUserId = {}
  props.teamWorkers.forEach(m => { nameToUserId[m.name] = m.userId })
  const records = []
  for (const [itemId, cells] of Object.entries(editMatrix)) {
    for (const [workerName, qty] of Object.entries(cells)) {
      const q = Number(qty)
      if (q <= 0) continue
      if (getCell(itemId, workerName)?.reviewStatus === 'approved') continue // 锁定单元格不提交
      const workerUserId = nameToUserId[workerName]
      if (!workerUserId) continue // 历史遗留列无对应用户，不可提交
      records.push({ itemId: Number(itemId), workerUserId, quantity: q })
    }
  }
  if (records.length === 0) {
    feedback.warning('请先填写工作量（大于 0 且未被审核锁定）')
    return
  }
  saving.value = true
  try {
    await workloadApi.saveRecords({ projectId: props.projectId, recordDate: props.statDate, records })
    feedback.success(`已提交 ${records.length} 条填报，待项目经理审核后计入统计`)
    emit('changed')
  } catch (e) {
    feedback.error(e?.message || '提交失败')
  } finally {
    saving.value = false
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

.report-actions {
  justify-content: space-between;
}

.worker-manager {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.worker-label {
  font-size: 13px;
  color: #666;
}

.worker-tag {
  margin-right: 2px;
}

.col-sub-tag {
  margin-left: 4px;
  transform: scale(0.9);
}

/* ===== 单元格审核状态着色 ===== */
:deep(.cell-approved .el-input__inner) {
  background: #f0f9eb;
  color: #67c23a;
  font-weight: 600;
}

:deep(.cell-pending .el-input__inner) {
  background: #fdf6ec;
}

:deep(.cell-rejected .el-input__inner) {
  background: #fef0f0;
  color: #f56c6c;
}

.cell-legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.dot-approved {
  background: #f0f9eb;
  border: 1px solid #67c23a;
}

.dot-pending {
  background: #fdf6ec;
  border: 1px solid #e6a23c;
}

.dot-rejected {
  background: #fef0f0;
  border: 1px solid #f56c6c;
}

.dot-legacy {
  background: #f4f4f5;
  border: 1px solid #909399;
}

.perf-footer {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f0f7ff;
  border-radius: 6px;
  font-size: 13px;
  color: #303133;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.perf-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 0;
}

.perf-label {
  font-weight: 600;
}

.perf-item {
  display: inline-block;
  margin-right: 14px;
}

.perf-pending {
  color: #e6a23c;
}

.perf-muted {
  color: #999;
}
</style>
