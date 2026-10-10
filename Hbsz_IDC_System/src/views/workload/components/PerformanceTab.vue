<template>
  <div>
    <div class="tab-actions perf-actions">
      <el-date-picker
        v-model="perfRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
        style="width: 260px"
        @change="fetchPerformance"
      />
      <span class="perf-scope-tip">仅统计已审核通过的填报</span>
    </div>
    <el-table
      v-loading="loadingPerf"
      :data="performances"
      border
      stripe
      max-height="620"
    >
      <el-table-column
        label="排名"
        width="64"
        align="center"
      >
        <template #default="{ $index }">
          {{ $index + 1 }}
        </template>
      </el-table-column>
      <el-table-column
        prop="workerName"
        label="施工人员"
        min-width="120"
      />
      <el-table-column
        prop="totalQuantity"
        label="完成总量"
        width="110"
        align="right"
      />
      <el-table-column
        prop="performanceScore"
        label="绩效分"
        width="110"
        align="right"
        sortable
      >
        <template #default="{ row }">
          <span class="perf-score">{{ row.performanceScore }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="recordDays"
        label="填报天数"
        width="96"
        align="center"
      />
      <el-table-column
        prop="avgDailyPerformance"
        label="日均绩效"
        width="96"
        align="right"
      />
      <template #empty>
        <el-empty
          description="该区间暂无已审核的填报记录"
          :image-size="80"
        />
      </template>
    </el-table>
  </div>
</template>

<script setup>
/**
 * 人员绩效 Tab（WorkloadStatistics 拆分）
 *
 * 日期区间按人汇总绩效排名（仅计已审核通过）。
 * 数据自管理：active 变为激活 / 项目变化（激活期间）时拉取，区间选择变化即时刷新。
 */
import { ref, watch } from 'vue'
import { workloadApi } from '@/api/workload'
import { feedback } from '@/utils/feedback'

const props = defineProps({
  /** 当前项目 ID（绩效查询用） */
  projectId: { type: Number, default: null },
  /** Tab 是否激活（el-tabs 急切渲染所有 pane，拉数必须以 active 守卫） */
  active: { type: Boolean, default: false }
})

// ===== 绩效状态（组件内部） =====
const perfRange = ref([new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10), new Date().toISOString().slice(0, 10)])
const performances = ref([])
const loadingPerf = ref(false)

/** 激活/项目联动（数组 watch：同 tick 内多源变化只触发一次拉取） */
watch(
  [() => props.active, () => props.projectId],
  ([active, projectId]) => {
    if (active && projectId) fetchPerformance()
  }
)

async function fetchPerformance() {
  if (!props.projectId || !perfRange.value?.[0]) return
  loadingPerf.value = true
  try {
    const res = await workloadApi.getPerformance({
      projectId: props.projectId,
      startDate: perfRange.value[0],
      endDate: perfRange.value[1]
    })
    performances.value = res.data || []
  } catch (e) {
    feedback.error(e?.message || '绩效数据加载失败')
  } finally {
    loadingPerf.value = false
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

.perf-actions {
  justify-content: flex-start;
}

.perf-scope-tip {
  font-size: 12px;
  color: #999;
}

.perf-score {
  font-weight: 600;
  color: #1890ff;
}
</style>
