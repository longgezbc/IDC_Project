<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          我的填报历史
        </div>
        <div class="page-subtitle">
          查看已提交的进度填报记录
        </div>
      </div>
    </div>

    <!-- P1-2.2：使用 CrudTable 通用布局组件，审核状态筛选通过 #toolbar 插槽传入 -->
    <CrudTable
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :page-sizes="[10, 20, 50]"
      :data="list"
      :loading="loading"
      :total="total"
      @change="fetchList"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <el-radio-group
            v-model="queryParams.reviewStatus"
            @change="handleSearch"
          >
            <el-radio-button value="">
              全部
            </el-radio-button>
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
        </div>
        <div class="toolbar-right">
          <BaseButton
            icon-name="RefreshLeft"
            @click="handleReset"
          >
            重置
          </BaseButton>
        </div>
      </template>

      <el-table-column
        label="任务名称"
        min-width="200"
      >
        <template #default="{ row }">
          <el-link
            type="primary"
            @click="goTaskProgress(row)"
          >
            {{ row.taskName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        prop="projectName"
        label="所属项目"
        min-width="160"
      />
      <el-table-column
        label="进度变化"
        width="140"
      >
        <template #default="{ row }">
          <span>{{ row.previousProgress ?? 0 }}%</span>
          <el-icon style="margin: 0 4px">
            <ArrowRight />
          </el-icon>
          <span style="color: #1890ff; font-weight: 600">{{ row.progress ?? 0 }}%</span>
        </template>
      </el-table-column>
      <el-table-column
        label="审核状态"
        width="120"
      >
        <template #default="{ row }">
          <el-tag
            :type="getReviewTagType(row.reviewStatus)"
            size="small"
          >
            {{ getReviewStatusText(row.reviewStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="reviewerName"
        label="审核人"
        width="100"
      >
        <template #default="{ row }">
          {{ row.reviewerName || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="reportTime"
        label="填报时间"
        width="160"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.reportTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="100"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            @click="openDetail(row)"
          >
            详情
          </BaseButton>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState description="暂无填报记录" />
      </template>
    </CrudTable>

    <!-- 填报详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="填报详情"
      width="640px"
    >
      <template v-if="currentDetail">
        <el-descriptions
          :column="2"
          border
          size="small"
        >
          <el-descriptions-item
            label="任务名称"
            :span="2"
          >
            {{ currentDetail.taskName }}
          </el-descriptions-item>
          <el-descriptions-item label="所属项目">
            {{ currentDetail.projectName }}
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag
              :type="getReviewTagType(currentDetail.reviewStatus)"
              size="small"
            >
              {{ getReviewStatusText(currentDetail.reviewStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度变化">
            {{ currentDetail.previousProgress ?? 0 }}% → {{ currentDetail.progress ?? 0 }}%
          </el-descriptions-item>
          <el-descriptions-item
            label="填报备注"
            :span="2"
          >
            {{ currentDetail.remark || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="审核人">
            {{ currentDetail.reviewerName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="审核时间">
            {{ formatDateTime(currentDetail.reviewTime) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item
            label="审核意见"
            :span="2"
          >
            {{ currentDetail.reviewRemark || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 施工图片 -->
        <div
          v-if="currentDetail.images && currentDetail.images.length > 0"
          style="margin-top: 16px"
        >
          <div style="margin-bottom: 8px; font-weight: 600">
            施工图片（{{ currentDetail.images.length }}张）
          </div>
          <div class="image-grid">
            <el-image
              v-for="(img, idx) in currentDetail.images"
              :key="img + '-' + idx"
              :src="resolveImageUrl(img)"
              :alt="`提交图片${idx + 1}`"
              :preview-src-list="currentDetail.images.map(resolveImageUrl)"
              :initial-index="idx"
              fit="cover"
              style="width: 100px; height: 100px; border-radius: 4px; margin-right: 8px; margin-bottom: 8px"
              :preview-teleported="true"
            />
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 我的填报历史 - 独立页面
 * 复用 GET /dashboard/my-submissions 接口（按 reporter_id 过滤）
 * 增强：审核状态筛选 + 详情对话框（含图片预览）
 *
 * P1-2.2：使用 useCrudList 组合式函数封装列表数据状态与 fetch/search/reset 逻辑，
 *        使用 CrudTable 通用布局组件封装 el-card + toolbar + el-table + 分页的模板与 CSS，
 *        本组件仅保留审核状态特有列定义、详情对话框与图片预览逻辑。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api/dashboard'
import { formatDateTime } from '@/utils/format'
import { resolveFileUrl } from '@/utils/fileUrl'
import { getReviewStatusText, getReviewStatusTagType } from '@/utils/statusMap'
// P1-2.2：通用列表布局组件 + 通用 CRUD 组合式函数
import CrudTable from '@/components/CrudTable.vue'
import { useCrudList } from '@/composables/useCrudList'

const router = useRouter()

/**
 * P1-2.2：列表数据状态与 fetch/search/reset 由 useCrudList 统一管理。
 * reviewStatus 为业务筛选字段；空字符串表示「全部」需转为 undefined 避免后端按空串过滤。
 * fetchFn 包装层将 reviewStatus 空值转为 undefined，与原逻辑保持一致。
 */
const fetchSubmissions = (params) => dashboardApi.getMySubmissions({
  ...params,
  reviewStatus: params.reviewStatus || undefined
})
const { list, total, loading, queryParams, fetchList, handleSearch, handleReset } = useCrudList(
  fetchSubmissions,
  { reviewStatus: '' }
)

// 详情对话框状态（页面特有）
const detailVisible = ref(false)
const currentDetail = ref(null)

// P3 重构：审核状态映射统一改用 utils/statusMap 共享实现，
// 别名 getReviewTagType 保持模板引用不变，删除本地重复定义
const getReviewTagType = getReviewStatusTagType

// P3 重构：图片 URL 解析改用 utils/fileUrl 统一实现，别名保持模板引用不变
const resolveImageUrl = resolveFileUrl

/** 打开详情对话框 @param {Object} row 填报记录 */
function openDetail(row) {
  currentDetail.value = row
  detailVisible.value = true
}

/** 跳转到任务进度页 @param {Object} row 填报记录 */
function goTaskProgress(row) {
  router.push(`/progress/${row.projectId}`)
}

onMounted(fetchList)
</script>

<style scoped>
.image-grid {
  display: flex;
  flex-wrap: wrap;
}
</style>
