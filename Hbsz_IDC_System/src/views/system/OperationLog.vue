<template>
  <div class="operation-log">
    <!-- 工具栏：筛选区（操作人/模块/类型/状态/时间范围） -->
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
          <span class="table-title">操作日志审计</span>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="queryParams.username"
            placeholder="操作人"
            clearable
            style="width: 130px"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="queryParams.module"
            placeholder="操作模块"
            clearable
            filterable
            style="width: 140px"
          >
            <el-option
              v-for="m in filterOptions.modules"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
          <el-select
            v-model="queryParams.businessType"
            placeholder="业务类型"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="t in filterOptions.businessTypes"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
          <el-select
            v-model="queryParams.status"
            placeholder="状态"
            clearable
            style="width: 100px"
          >
            <el-option
              label="成功"
              :value="1"
            />
            <el-option
              label="失败"
              :value="0"
            />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
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
        width="55"
        align="center"
      />
      <el-table-column
        prop="module"
        label="操作模块"
        width="110"
        show-overflow-tooltip
      />
      <el-table-column
        prop="businessType"
        label="业务类型"
        width="90"
        align="center"
      />
      <el-table-column
        prop="username"
        label="操作人"
        width="110"
        show-overflow-tooltip
      />
      <el-table-column
        prop="ip"
        label="IP 地址"
        width="130"
        show-overflow-tooltip
      />
      <el-table-column
        prop="requestUrl"
        label="请求地址"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="状态"
        width="80"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.status === 1 ? 'success' : 'danger'"
            size="small"
          >
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="耗时"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <span :class="{ 'slow-cost': row.costTime > 3000 }">{{ row.costTime ?? '-' }} ms</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        label="操作时间"
        width="170"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="90"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            icon-name="View"
            @click="showDetail(row)"
          >
            详情
          </BaseButton>
        </template>
      </el-table-column>
    </CrudTable>

    <!-- 详情抽屉：请求参数/响应结果/错误信息 JSON 格式化展示 -->
    <el-drawer
      v-model="detailVisible"
      title="操作日志详情"
      size="480px"
    >
      <el-descriptions
        :column="1"
        border
        size="small"
      >
        <el-descriptions-item label="日志ID">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="操作模块">
          {{ detail.module }}
        </el-descriptions-item>
        <el-descriptions-item label="业务类型">
          {{ detail.businessType }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ detail.username }}（ID: {{ detail.userId ?? '-' }}）
        </el-descriptions-item>
        <el-descriptions-item label="IP 地址">
          {{ detail.ip }}
        </el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag
            :type="detail.status === 1 ? 'success' : 'danger'"
            size="small"
          >
            {{ detail.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ detail.costTime ?? '-' }} ms
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">
          {{ formatDateTime(detail.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="请求方法">
          <code class="method-code">{{ detail.method }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="请求地址">
          <code class="method-code">{{ detail.requestUrl }}</code>
        </el-descriptions-item>
      </el-descriptions>

      <template v-if="prettyParams">
        <div class="detail-section-title">
          请求参数
        </div>
        <pre class="json-block">{{ prettyParams }}</pre>
      </template>
      <template v-if="prettyResult">
        <div class="detail-section-title">
          响应结果
        </div>
        <pre class="json-block">{{ prettyResult }}</pre>
      </template>
      <template v-if="detail.errorMsg">
        <div class="detail-section-title error-title">
          错误信息
        </div>
        <pre class="json-block error-block">{{ detail.errorMsg }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
/**
 * 操作日志（审计查询）
 *
 * 业务用途：检索 OperationLogAspect 落库的审计数据（此前只写不查），
 * 支持按操作人/模块/业务类型/执行状态/时间范围组合筛选，时间倒序分页。
 * 筛选下拉选项从后端 distinct 查询，避免前端硬编码模块枚举。
 * 权限：system:log:query（菜单与路由双控，默认仅 admin）。
 *
 * 数据流：useCrudList 管理分页与竞态保护；dateRange（本地状态）
 *        在 fetchFn 包装层拆解为 startDate/endDate 后透传后端。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { systemApi } from '@/api/system'
import { formatDateTime } from '@/utils/format'
import CrudTable from '@/components/CrudTable.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'

defineOptions({ name: 'OperationLog' })

/** 筛选下拉选项（模块/业务类型，来自后端 distinct） */
const filterOptions = reactive({ modules: [], businessTypes: [] })

/** 时间范围（daterange 数组，fetch 时拆解） */
const dateRange = ref(null)

/**
 * fetchFn 包装：把 dateRange 拆解为 startDate/endDate，
 * 避免 axios 将数组序列化为 dateRange[0]=... 导致后端无法绑定。
 */
const fetchLogs = (params) => {
  const [startDate, endDate] = dateRange.value || []
  return systemApi.queryOperationLogs({ ...params, startDate, endDate })
}

const { list, total, loading, queryParams, fetchList, handleSearch, handleReset } = useCrudList(
  fetchLogs,
  { username: '', module: undefined, businessType: undefined, status: undefined }
)

/** 详情抽屉 */
const detailVisible = ref(false)
const detail = ref({})
/** JSON 美化（解析失败时原样展示，截断标记保持可读） */
const prettyJson = (str) => {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}
const prettyParams = computed(() => prettyJson(detail.value.requestParams))
const prettyResult = computed(() => prettyJson(detail.value.responseResult))

const showDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}

/** 拉取筛选下拉选项（失败静默：下拉为空不影响主列表） */
const fetchFilterOptions = async () => {
  try {
    const res = await systemApi.getOperationLogFilters()
    filterOptions.modules = res.data?.modules || []
    filterOptions.businessTypes = res.data?.businessTypes || []
  } catch (e) {
    console.error('[OperationLog] 拉取筛选选项失败:', e?.message)
  }
}

onMounted(() => {
  fetchList()
  fetchFilterOptions()
})

useKeepAliveRefresh(fetchList)
</script>

<style scoped>
.operation-log {
  padding: 16px;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* 耗时超 3 秒标红提示性能异常 */
.slow-cost {
  color: var(--el-color-danger);
  font-weight: 600;
}

.method-code {
  font-family: var(--el-font-family-mono, 'JetBrains Mono', Consolas, monospace);
  font-size: 12px;
  word-break: break-all;
}

.detail-section-title {
  margin: 16px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.error-title {
  color: var(--el-color-danger);
}

.json-block {
  margin: 0;
  padding: 10px;
  max-height: 240px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-family: var(--el-font-family-mono, 'JetBrains Mono', Consolas, monospace);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-block {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
</style>
