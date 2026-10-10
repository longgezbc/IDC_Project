<!--
  ApprovalFlowDemo.vue - 审批流程图页面（接入真实 API）

  业务用途：
    从后端查询真实审批业务数据，通过 ApprovalFlowChart 组件渲染流程图。
    支持三种审批场景切换：项目授权审批、任务进度审核、项目转移审批。

  交互流程：
    1. 用户选择审批场景（tab 切换）
    2. 从后端加载该场景的业务记录列表
    3. 用户选择某条记录
    4. 调用后端接口将该记录转换为流程图 {nodes, links} 数据
    5. ApprovalFlowChart 组件渲染流程图

  @author hbsz
-->
<template>
  <div class="approval-flow-page">
    <!-- 场景切换 tab -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>审批流程图</span>
        </div>
      </template>

      <el-tabs
        v-model="currentScene"
        @tab-change="handleSceneChange"
      >
        <el-tab-pane
          label="项目授权审批"
          name="auth"
        />
        <el-tab-pane
          label="任务进度审核"
          name="progress"
        />
        <el-tab-pane
          label="项目转移审批"
          name="transfer"
        />
      </el-tabs>

      <!-- 业务记录选择 -->
      <div class="record-selector">
        <el-select
          v-model="selectedRecordId"
          placeholder="请选择业务记录查看流程图"
          style="width: 100%"
          @change="handleRecordChange"
        >
          <el-option
            v-for="item in recordList"
            :key="item.id"
            :label="formatRecordLabel(item)"
            :value="item.id"
          />
        </el-select>
      </div>

      <!-- 流程图渲染区 -->
      <div
        v-if="flowData"
        class="flow-chart-wrapper"
      >
        <ApprovalFlowChart
          :nodes="flowData.nodes"
          :links="flowData.links"
          :title="flowData.title"
          height="300px"
          @node-click="handleNodeClick"
        />
        <!-- 业务信息摘要 -->
        <div class="biz-info">
          <el-descriptions
            :column="2"
            size="small"
            border
          >
            <el-descriptions-item label="流程类型">
              {{ flowTypeLabel }}
            </el-descriptions-item>
            <el-descriptions-item label="关联项目">
              {{ flowData.projectName || '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 无数据提示 -->
      <el-empty
        v-else-if="!loading"
        description="请选择业务记录查看审批流程图"
      />

      <!-- 加载状态 -->
      <div
        v-if="loading"
        class="loading-wrapper"
      >
        <el-skeleton
          :rows="5"
          animated
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 审批流程图页面
 *
 * 通过后端 ApprovalFlowController 接口获取真实审批数据，
 * 转换为流程图组件所需的 {nodes, links} 数据结构后渲染。
 */
import { ref, computed, onMounted } from 'vue'
import { feedback } from '@/utils/feedback'
import ApprovalFlowChart from '@/components/ApprovalFlowChart.vue'
import { approvalFlowApi } from '@/api/approvalFlow'

/** 当前选中的场景 */
const currentScene = ref('auth')

/** 业务记录列表 */
const recordList = ref([])

/** 选中的业务记录 ID */
const selectedRecordId = ref(null)

/** 流程图数据（后端返回的 VO） */
const flowData = ref(null)

/** 加载状态 */
const loading = ref(false)

/** 流程类型中文标签 */
const flowTypeLabel = computed(() => {
  if (!flowData.value) return ''
  const labels = {
    auth_request: '项目授权审批',
    progress_review: '任务进度审核',
    project_transfer: '项目转移审批'
  }
  return labels[flowData.value.flowType] || flowData.value.flowType
})

/**
 * 场景切换时：加载对应的业务记录列表
 */
async function handleSceneChange(scene) {
  selectedRecordId.value = null
  flowData.value = null
  recordList.value = []
  loading.value = true

  try {
    if (scene === 'auth') {
      const res = await approvalFlowApi.listAuthRequests()
      recordList.value = res.data || []
    } else if (scene === 'progress') {
      const res = await approvalFlowApi.listProgressReviews()
      recordList.value = res.data || []
    } else {
      const res = await approvalFlowApi.listProjectTransfers()
      recordList.value = res.data || []
    }

    // 自动选中第一条记录
    if (recordList.value.length > 0) {
      selectedRecordId.value = recordList.value[0].id
      await loadFlowData()
    }
  } catch (e) {
    feedback.error('加载记录列表失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

/**
 * 选择业务记录后：加载流程图数据
 */
async function handleRecordChange(id) {
  if (!id) return
  await loadFlowData()
}

/**
 * 调用后端接口获取流程图数据
 */
async function loadFlowData() {
  const id = selectedRecordId.value
  if (!id) return

  loading.value = true
  flowData.value = null

  try {
    if (currentScene.value === 'auth') {
      const res = await approvalFlowApi.getAuthRequestFlow(id)
      flowData.value = res.data
    } else if (currentScene.value === 'progress') {
      const res = await approvalFlowApi.getProgressReviewFlow(id)
      flowData.value = res.data
    } else {
      const res = await approvalFlowApi.getProjectTransferFlow(id)
      flowData.value = res.data
    }
  } catch (e) {
    feedback.error('加载流程图失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

/**
 * 格式化业务记录标签（下拉列表展示用）
 */
function formatRecordLabel(item) {
  if (currentScene.value === 'auth') {
    return `${item.projectName || '项目' + item.projectId} - ${item.applicantName || '?'} → ${item.targetName || '?'}（${item.status}）`
  } else if (currentScene.value === 'progress') {
    return `${item.projectName || '项目' + item.projectId} - 进度${item.progress}%（${item.reviewStatus}）`
  } else {
    const actionLabels = { assign: '授权', revoke: '撤销', transfer: '转移' }
    return `${item.projectName || '项目' + item.projectId} - ${actionLabels[item.actionType] || item.actionType} - ${item.operatorName || '?'}`
  }
}

/** 节点点击事件（演示页：预留交互挂点，暂无业务动作） */
function handleNodeClick(node) {
  void node
}

/** 页面加载时：默认加载项目授权审批列表 */
onMounted(() => {
  handleSceneChange('auth')
})
</script>

<style scoped>
.approval-flow-page {
  padding: 20px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
}

.record-selector {
  margin: 16px 0;
}

.flow-chart-wrapper {
  margin-top: 8px;
}

.biz-info {
  margin-top: 16px;
}

.loading-wrapper {
  padding: 24px 0;
}
</style>
