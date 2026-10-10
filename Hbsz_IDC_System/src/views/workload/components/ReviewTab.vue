<template>
  <div>
    <div class="tab-actions review-actions">
      <div class="review-filters">
        <el-radio-group
          v-model="reviewStatus"
          size="small"
          @change="fetchReviewList"
        >
          <el-radio-button value="pending">
            待审核
          </el-radio-button>
          <el-radio-button value="approved">
            已通过
          </el-radio-button>
          <el-radio-button value="rejected">
            已驳回
          </el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="reviewDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="全部日期"
          clearable
          size="small"
          style="width: 150px"
          @change="fetchReviewList"
        />
      </div>
      <template v-if="reviewStatus === 'pending'">
        <el-button
          type="success"
          size="small"
          :disabled="reviewSelection.length === 0"
          :loading="reviewing"
          @click="handleReview(true)"
        >
          批量通过{{ reviewSelection.length ? `（${reviewSelection.length}）` : '' }}
        </el-button>
        <el-button
          type="danger"
          size="small"
          :disabled="reviewSelection.length === 0"
          :loading="reviewing"
          @click="handleReview(false)"
        >
          批量驳回{{ reviewSelection.length ? `（${reviewSelection.length}）` : '' }}
        </el-button>
      </template>
    </div>
    <el-table
      v-loading="reviewLoading"
      :data="reviewRecords"
      border
      stripe
      max-height="620"
      size="small"
      @selection-change="reviewSelection = $event"
    >
      <el-table-column
        type="selection"
        width="44"
        :selectable="(row) => row.reviewStatus === 'pending'"
      />
      <el-table-column
        prop="recordDate"
        label="填报日期"
        width="100"
        align="center"
      />
      <el-table-column
        prop="workerName"
        label="施工人员"
        width="90"
      />
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
        label="完成数量"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          <span class="qty-cell">{{ formatQty(row.quantity) }}</span>
          <span class="unit-cell">{{ row.unit }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="88"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="getReviewStatusTagType(row.reviewStatus)"
            size="small"
          >
            {{ getReviewStatusText(row.reviewStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="审核意见"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span :class="{ 'reject-remark': row.reviewStatus === 'rejected' }">{{ row.reviewRemark || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="reviewerName"
        label="审核人"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          {{ row.reviewerName || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="审核时间"
        width="140"
        align="center"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.reviewTime) || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="填报时间"
        width="140"
        align="center"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) || '-' }}
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          :description="reviewEmptyText"
          :image-size="80"
        />
      </template>
    </el-table>
  </div>
</template>

<script setup>
/**
 * 填报审核 Tab（WorkloadStatistics 拆分，仅项目经理/管理员可见——由容器控制 pane）
 *
 * 批量通过或驳回填报（驳回需填原因），仅 pending 可审核。
 * 数据自管理：active 变为激活 / 项目或统计日期变化（激活期间）时拉取列表；
 * 审核完成后 emit('changed') 通知容器刷新矩阵（待审核角标/汇总卡片即时更新）。
 */
import { computed, ref, watch } from 'vue'
import { workloadApi } from '@/api/workload'
import { feedback } from '@/utils/feedback'
import { formatDateTime, formatQty } from '@/utils/format'
import { getReviewStatusText, getReviewStatusTagType } from '@/utils/statusMap'

const props = defineProps({
  /** 当前项目 ID（审核列表查询用） */
  projectId: { type: Number, default: null },
  /** 统计日期 YYYY-MM-DD（激活期间变化时刷新列表，与容器共享日期上下文） */
  statDate: { type: String, required: true },
  /** Tab 是否激活（el-tabs 急切渲染所有 pane，拉数必须以 active 守卫） */
  active: { type: Boolean, default: false }
})

const emit = defineEmits(['changed'])

// ===== 审核状态（组件内部） =====
const reviewStatus = ref('pending')
const reviewDate = ref('')
const reviewRecords = ref([])
const reviewLoading = ref(false)
const reviewSelection = ref([])
const reviewing = ref(false)

/** 审核列表空态文案（按当前筛选给出指引） */
const reviewEmptyText = computed(() => {
  if (reviewStatus.value === 'pending') return '暂无待审核的填报'
  return reviewStatus.value === 'approved' ? '暂无已通过的填报' : '暂无已驳回的填报'
})

/**
 * 激活/项目/统计日期联动（数组 watch：同 tick 内多源变化只触发一次拉取）：
 *  - 切换项目：清空勾选（勾选为项目维度数据，无论是否激活都清）；
 *  - 激活且已选项目时拉取列表（未选项目由容器自动选中后经本 watch 触发）。
 */
watch(
  [() => props.active, () => props.projectId, () => props.statDate],
  (newVals, oldVals) => {
    if (oldVals[1] !== newVals[1]) reviewSelection.value = []
    if (!newVals[0] || !newVals[1]) return
    fetchReviewList()
  }
)

async function fetchReviewList() {
  if (!props.projectId) return
  reviewLoading.value = true
  try {
    const params = { projectId: props.projectId, reviewStatus: reviewStatus.value }
    if (reviewDate.value) params.recordDate = reviewDate.value
    const res = await workloadApi.listPendingRecords(params)
    reviewRecords.value = res.data || []
  } catch (e) {
    feedback.error(e?.message || '审核列表加载失败')
  } finally {
    reviewLoading.value = false
  }
}

/**
 * 批量审核勾选的待审核记录。
 * 通过：直接生效计入统计；驳回：强制填写原因（展示给填报人）。
 */
async function handleReview(approve) {
  const ids = reviewSelection.value.map(r => r.id)
  if (ids.length === 0) {
    feedback.warning('请先勾选待审核记录')
    return
  }
  let remark = ''
  if (!approve) {
    const { ElMessageBox } = await import('element-plus')
    try {
      const { value } = await ElMessageBox.prompt(
        `将驳回 ${ids.length} 条填报，请输入驳回原因（填报人修改后重新提交时可见）：`,
        '驳回填报',
        {
          type: 'warning',
          confirmButtonText: '驳回',
          cancelButtonText: '取消',
          inputPlaceholder: '如：数量与现场实际不符',
          inputValidator: (v) => (v && v.trim()) ? true : '驳回原因不能为空'
        }
      )
      remark = value.trim()
    } catch {
      return
    }
  }
  reviewing.value = true
  try {
    const res = await workloadApi.reviewRecords({ ids, approve, remark })
    feedback.success(res?.message || '审核完成')
    emit('changed')
    await fetchReviewList()
  } catch (e) {
    feedback.error(e?.message || '审核失败')
  } finally {
    reviewing.value = false
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

.review-actions {
  justify-content: space-between;
}

.review-filters {
  display: flex;
  align-items: center;
  gap: 10px;
}

.qty-cell {
  font-weight: 600;
}

.unit-cell {
  margin-left: 4px;
  color: #999;
  font-size: 12px;
}

.reject-remark {
  color: #f56c6c;
}
</style>
