<template>
  <div
    v-if="project"
    class="page-container"
  >
    <div class="page-header">
      <div>
        <BaseButton
          variant="link"
          icon-name="ArrowLeft"
          @click="router.back()"
        >
          返回
        </BaseButton>
        <span
          class="page-title"
          style="margin-left: 8px"
        >{{ project.name }}</span>
        <el-tag
          :type="getStatusType(project.status)"
          style="margin-left: 12px"
        >
          {{ project.status }}
        </el-tag>
      </div>
      <div>
        <BaseButton
          v-if="canEdit"
          icon-name="Edit"
          :disabled="project.archived"
          @click="router.push(`/projects/${project.id}/edit`)"
        >
          编辑
        </BaseButton>
        <BaseButton
          variant="primary"
          icon-name="DataLine"
          @click="router.push(`/progress/${project.id}`)"
        >
          查看进度
        </BaseButton>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane
        label="基本信息"
        name="info"
      >
        <el-row :gutter="20">
          <el-col :span="16">
            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  项目基本信息
                </div>
              </template>
              <el-descriptions
                :column="2"
                border
              >
                <el-descriptions-item label="项目编号">
                  {{ project.code }}
                </el-descriptions-item>
                <el-descriptions-item label="项目类型">
                  <el-tag
                    size="small"
                    :type="project.type === '新建' ? 'success' : 'warning'"
                  >
                    {{ project.type }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="优先级">
                  <el-tag
                    size="small"
                    :type="project.priority === '高' ? 'danger' : project.priority === '中' ? 'warning' : 'info'"
                  >
                    {{ project.priority }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="项目状态">
                  {{ project.status }}
                </el-descriptions-item>
                <el-descriptions-item label="开始日期">
                  {{ project.startDate }}
                </el-descriptions-item>
                <el-descriptions-item label="结束日期">
                  {{ project.endDate }}
                </el-descriptions-item>
                <el-descriptions-item label="合同金额">
                  ¥{{ formatAmount(project.contractAmount) }}
                </el-descriptions-item>
                <el-descriptions-item label="预算金额">
                  ¥{{ formatAmount(project.budget) }}
                </el-descriptions-item>
                <el-descriptions-item
                  label="项目地址"
                  :span="2"
                >
                  {{ project.address }}
                </el-descriptions-item>
                <el-descriptions-item
                  label="项目描述"
                  :span="2"
                >
                  {{ project.description }}
                </el-descriptions-item>
                <el-descriptions-item label="创建时间">
                  {{ project.createdAt }}
                </el-descriptions-item>
                <el-descriptions-item label="更新时间">
                  {{ project.updatedAt }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  项目进度
                </div>
              </template>
              <div class="progress-box">
                <el-progress
                  type="dashboard"
                  :percentage="project.progress"
                  :width="140"
                />
                <div class="progress-info">
                  <p>当前进度：<strong>{{ project.progress }}%</strong></p>
                  <p v-if="project.status === '进行中'">
                    项目工期：{{ project.startDate }} 至 {{ project.endDate }}
                  </p>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-row
          :gutter="20"
          style="margin-top: 20px"
        >
          <el-col :span="12">
            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  客户资料
                </div>
              </template>
              <el-descriptions
                :column="1"
                border
              >
                <el-descriptions-item label="客户名称">
                  {{ project.customerName }}
                </el-descriptions-item>
                <el-descriptions-item label="联系人">
                  {{ project.customerContact }}
                </el-descriptions-item>
                <el-descriptions-item label="联系电话">
                  {{ project.customerPhone }}
                </el-descriptions-item>
                <el-descriptions-item label="联系邮箱">
                  {{ project.customerEmail || '-' }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card class="detail-card">
              <template #header>
                <div class="card-header">
                  项目团队
                </div>
              </template>
              <el-descriptions
                :column="1"
                border
              >
                <el-descriptions-item label="项目经理">
                  {{ project.manager }}
                  <el-tooltip
                    v-if="managerLinkWarning"
                    :content="managerLinkWarning.content"
                    placement="top"
                  >
                    <el-tag
                      :type="managerLinkWarning.type"
                      size="small"
                      style="margin-left: 8px"
                    >
                      <el-icon style="margin-right: 4px">
                        <Warning />
                      </el-icon>
                      {{ managerLinkWarning.label }}
                    </el-tag>
                  </el-tooltip>
                </el-descriptions-item>
                <el-descriptions-item label="经理电话">
                  {{ project.managerPhone }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>

        <el-card
          class="detail-card"
          style="margin-top: 20px"
        >
          <template #header>
            <div class="card-header">
              <span>已授权工程师</span>
              <span class="header-count">共 {{ authorizedEngineers.length }} 位</span>
              <BaseButton
                v-if="canTransfer"
                variant="primary"
                size="small"
                icon-name="Plus"
                style="float: right"
                @click="openAddDialog"
              >
                添加工程师
              </BaseButton>
            </div>
          </template>
          <el-table
            :data="authorizedEngineers"
            stripe
          >
            <el-table-column
              type="index"
              label="序号"
              width="70"
            />
            <el-table-column
              label="工程师"
              min-width="180"
            >
              <template #default="{ row }">
                <el-icon style="vertical-align: middle; color: #409eff">
                  <User />
                </el-icon>
                <span style="margin-left: 6px">{{ row.engineerName }}（{{ row.engineerUsername }}）</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="engineerPhone"
              label="联系电话"
              width="140"
            >
              <template #default="{ row }">
                {{ row.engineerPhone || '-' }}
              </template>
            </el-table-column>
            <el-table-column
              prop="assignedByName"
              label="授权人"
              width="120"
            >
              <template #default="{ row }">
                {{ row.assignedByName || '-' }}
              </template>
            </el-table-column>
            <el-table-column
              prop="assignedAt"
              label="授权时间"
              width="180"
            >
              <template #default="{ row }">
                {{ formatAssignedAt(row.assignedAt) }}
              </template>
            </el-table-column>
            <el-table-column
              prop="remark"
              label="备注"
              min-width="160"
            >
              <template #default="{ row }">
                {{ row.remark || '-' }}
              </template>
            </el-table-column>
            <el-table-column
              v-if="canTransfer"
              label="操作"
              width="100"
              fixed="right"
            >
              <template #default="{ row }">
                <BaseButton
                  variant="link-danger"
                  size="small"
                  @click="handleRemoveEngineer(row)"
                >
                  移除
                </BaseButton>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="authorizedEngineers.length === 0"
            description="暂无授权工程师"
          />
        </el-card>

        <el-card
          class="detail-card"
          style="margin-top: 20px"
        >
          <template #header>
            <div class="card-header">
              <span>设计图纸</span>
              <span class="header-count">共 {{ project.designDrawings.length }} 份</span>
            </div>
          </template>
          <el-table
            :data="project.designDrawings"
            stripe
          >
            <el-table-column
              type="index"
              label="序号"
              width="70"
            />
            <el-table-column
              prop="name"
              label="图纸名称"
              min-width="240"
            >
              <template #default="{ row }">
                <el-icon style="vertical-align: middle; color: #409eff">
                  <Document />
                </el-icon>
                <span style="margin-left: 6px">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="type"
              label="类型"
              width="100"
            />
            <el-table-column
              prop="size"
              label="文件大小"
              width="120"
            />
            <el-table-column
              prop="uploadDate"
              label="上传日期"
              width="140"
            />
            <el-table-column
              label="操作"
              width="160"
            >
              <template #default>
                <BaseButton
                  variant="link"
                  size="small"
                >
                  预览
                </BaseButton>
                <BaseButton
                  variant="link"
                  size="small"
                >
                  下载
                </BaseButton>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane
        label="项目文档"
        name="documents"
      >
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <span>相关文档</span>
              <BaseButton
                variant="primary"
                size="small"
                style="float: right"
                @click="router.push('/documents')"
              >
                前往文档管理
              </BaseButton>
            </div>
          </template>
          <el-table
            :data="projectDocuments"
            stripe
          >
            <el-table-column
              prop="name"
              label="文档名称"
              min-width="240"
            />
            <el-table-column
              prop="category"
              label="分类"
              width="120"
            >
              <template #default="{ row }">
                <el-tag size="small">
                  {{ row.category }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="version"
              label="版本"
              width="80"
            />
            <el-table-column
              prop="format"
              label="格式"
              width="80"
            />
            <el-table-column
              prop="size"
              label="大小"
              width="100"
            />
            <el-table-column
              prop="uploader"
              label="上传人"
              width="100"
            />
            <el-table-column
              prop="uploadDate"
              label="上传日期"
              width="120"
            />
            <el-table-column
              prop="status"
              label="状态"
              width="100"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.status === '已审核' ? 'success' : row.status === '待审核' ? 'warning' : 'danger'"
                >
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="projectDocuments.length === 0"
            description="暂无相关文档"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane
        label="任务进度"
        name="progress"
      >
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <span>任务列表</span>
              <BaseButton
                variant="primary"
                size="small"
                style="float: right"
                @click="router.push(`/progress/${project.id}`)"
              >
                查看甘特图
              </BaseButton>
            </div>
          </template>
          <el-table
            :data="projectTasks"
            stripe
          >
            <el-table-column
              prop="name"
              label="任务名称"
              min-width="180"
            />
            <el-table-column
              prop="assignee"
              label="负责人"
              width="100"
            />
            <el-table-column
              prop="startDate"
              label="开始日期"
              width="120"
            />
            <el-table-column
              prop="endDate"
              label="结束日期"
              width="120"
            />
            <el-table-column
              label="进度"
              width="180"
            >
              <template #default="{ row }">
                <el-progress
                  :percentage="row.progress"
                  :status="row.progress === 100 ? 'success' : ''"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="status"
              label="状态"
              width="100"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="getTaskStatusType(row.status)"
                >
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="关键节点"
              width="100"
            >
              <template #default="{ row }">
                <el-tag
                  v-if="row.isKey"
                  size="small"
                  type="danger"
                >
                  关键
                </el-tag>
                <el-tag
                  v-else-if="row.isMilestone"
                  size="small"
                  type="warning"
                >
                  里程碑
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="projectTasks.length === 0"
            description="暂无任务"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加工程师授权对话框（仅 PM/admin） -->
    <el-dialog
      v-model="addDialogVisible"
      title="添加授权工程师"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form
        :model="addForm"
        label-width="100px"
      >
        <el-form-item
          label="目标工程师"
          required
        >
          <el-select
            v-model="addForm.toEngineerId"
            placeholder="请选择工程师"
            filterable
            style="width: 100%"
            :loading="engineerLoading"
          >
            <el-option
              v-for="e in availableEngineers"
              :key="e.id"
              :label="`${e.name}（${e.username}）${e.phone ? ' · ' + e.phone : ''}`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="addForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入授权备注（选填，将记录到审计日志）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="addDialogVisible = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="addLoading"
          @click="confirmAddEngineer"
        >
          确认授权
        </BaseButton>
      </template>
    </el-dialog>
  </div>
  <el-empty
    v-else
    description="项目不存在"
  />
</template>

<script setup>
/**
 * 项目管理 - 详情页
 * 业务用途：展示单个项目的完整信息，供查看与跳转编辑/进度。
 * 主要功能区：三个标签页——基本信息（含项目信息、进度仪表盘、客户资料、团队、设计图纸）、
 *             项目文档（关联文档列表）、任务进度（关联任务列表）。
 * 数据来源：projectStore（项目详情）、documentStore（关联文档）、progressStore（关联任务），
 *          均按路由 id 拉取。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useDocumentStore } from '@/stores/document'
import { useProgressStore } from '@/stores/progress'
import { formatAmount, getStatusType, getTaskStatusType } from '@/utils/format'
import { hasPermission } from '@/utils/permission'
import { projectAuthApi } from '@/api/projectAuthorization'
import { feedback } from '@/utils/feedback'
import { isCancelError } from '@/utils/cancelToken'

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()
const documentStore = useDocumentStore()
const progressStore = useProgressStore()

const activeTab = ref('info')  // 当前激活标签页：info 基本信息 / documents 文档 / progress 任务
const loading = ref(false)     // 详情数据加载状态

const project = computed(() => projectStore.currentProject)                              // 当前项目详情
const projectDocuments = computed(() => documentStore.getDocumentsByProject(route.params.id)) // 该项目的关联文档
const projectTasks = computed(() => progressStore.getProjectTasks(route.params.id))       // 该项目的关联任务

// ===== 负责人关联健康度（后端 detail 接口下发 managerLinkStatus，前端纯展示） =====
// mismatch=姓名与关联用户分叉（审批/通知发给关联用户而非显示姓名）/ unlinked=未关联用户（通知断链）/
// disabled=关联用户已停用 / missing=关联用户不存在；ok 或无值不展示
const managerLinkWarning = computed(() => {
  const status = project.value?.managerLinkStatus
  const linkedName = project.value?.managerLinkedName
  if (!status || status === 'ok') return null
  if (status === 'mismatch') {
    return {
      type: 'warning',
      label: '归属分叉',
      content: `审批与通知将发送给关联用户「${linkedName || '?'}」，与显示姓名不一致；请通过项目转移或编辑项目经理修正`
    }
  }
  if (status === 'unlinked') {
    return {
      type: 'danger',
      label: '未关联用户',
      content: '该项目未关联负责人用户，审批通知与待办将无法送达；请在项目编辑中重新选择项目经理'
    }
  }
  if (status === 'disabled') {
    return {
      type: 'warning',
      label: '账号已停用',
      content: `关联用户「${linkedName || '?'}」已停用，审批通知将无法送达；请转移项目至有效负责人`
    }
  }
  if (status === 'missing') {
    return {
      type: 'warning',
      label: '关联失效',
      content: '关联的负责人用户不存在，请重新选择项目经理'
    }
  }
  return null
})

// ===== 角色权限判定 =====
// admin / project_manager 可执行编辑/转移；工程师仅查看授权范围内的项目
const canEdit = computed(() => hasPermission('project:edit'))
const canTransfer = computed(() => hasPermission('project:transfer'))

// ===== 已授权工程师列表 =====
const authorizedEngineers = ref([])  // 当前项目已授权的工程师列表

/** 加载项目已授权工程师列表 */
async function loadAuthorizedEngineers() {
  try {
    const res = await projectAuthApi.listProjectEngineers(route.params.id)
    authorizedEngineers.value = res.data || []
  } catch (error) {
    if (isCancelError(error)) return
    console.error('加载授权工程师列表失败:', error)
  }
}

/** 格式化授权时间显示（兼容字符串与 ISO 时间） */
function formatAssignedAt(value) {
  if (!value) return '-'
  // 后端返回 LocalDateTime 默认格式形如 'yyyy-MM-ddTHH:mm:ss.SSS'，仅保留到分钟
  const str = String(value).replace('T', ' ')
  return str.length > 16 ? str.slice(0, 16) : str
}

/** 加载项目详情，并并发拉取该项目的关联文档与任务 */
async function loadDetail() {
  loading.value = true
  try {
    await projectStore.fetchDetail(route.params.id)
    // 加载相关文档、任务、授权工程师
    await Promise.all([
      documentStore.fetchList({ projectId: route.params.id, pageSize: 100 }),
      progressStore.fetchList({ projectId: route.params.id, pageSize: 100 }),
      loadAuthorizedEngineers()
    ])
  } catch (error) {
    console.error('加载项目详情失败:', error)
  } finally {
    loading.value = false
  }
}

// ===== 添加工程师授权对话框 =====
const addDialogVisible = ref(false)
const addForm = ref({ toEngineerId: null, remark: '' })
const addLoading = ref(false)
const activeEngineers = ref([])   // 所有活跃工程师下拉数据
const engineerLoading = ref(false)

/** 当前可选工程师：从活跃工程师列表中排除已授权的，避免重复授权 */
const availableEngineers = computed(() => {
  const assignedIds = new Set(authorizedEngineers.value.map((e) => e.engineerId))
  return activeEngineers.value.filter((e) => !assignedIds.has(e.id))
})

/** 打开添加工程师对话框：懒加载活跃工程师下拉 */
async function openAddDialog() {
  addForm.value = { toEngineerId: null, remark: '' }
  addDialogVisible.value = true
  if (activeEngineers.value.length === 0) {
    engineerLoading.value = true
    try {
      const res = await projectAuthApi.listActiveEngineers()
      activeEngineers.value = res.data || []
    } catch (e) {
      feedback.error('加载工程师列表失败')
    } finally {
      engineerLoading.value = false
    }
  }
}

/** 确认授权：调用 assign 接口，成功后刷新授权列表 */
async function confirmAddEngineer() {
  if (!addForm.value.toEngineerId) {
    feedback.warning('请选择目标工程师')
    return
  }
  addLoading.value = true
  try {
    await projectAuthApi.assign(route.params.id, addForm.value)
    feedback.success('授权成功')
    addDialogVisible.value = false
    await loadAuthorizedEngineers()
  } catch (e) {
    console.error('[项目授权] 失败:', e?.message)
  } finally {
    addLoading.value = false
  }
}

/** 移除工程师授权：二次确认后调用 revoke 接口 */
function handleRemoveEngineer(row) {
  feedback.confirm(
    `确认撤销工程师「${row.engineerName}」对该项目的授权吗？撤销后该工程师将无法继续访问此项目。`,
    { title: '撤销授权确认', type: 'warning' }
  ).then(async () => {
    try {
      await projectAuthApi.revoke(route.params.id, row.engineerId, '项目经理在项目详情页撤销授权')
      feedback.success('已撤销授权')
      await loadAuthorizedEngineers()
    } catch (e) {
      console.error('[撤销授权] 失败:', e?.message)
    }
  }).catch(() => {})
}

// 进入页面时加载详情数据
onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.detail-card {
  margin-bottom: 0;
}

.card-header {
  font-weight: 600;
  font-size: 15px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-count {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}

.progress-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.progress-info {
  margin-top: 20px;
  text-align: center;
}

.progress-info p {
  margin: 6px 0;
  color: #606266;
  font-size: 13px;
}
</style>
