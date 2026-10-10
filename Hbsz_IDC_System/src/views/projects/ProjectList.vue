<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          项目管理
        </div>
        <div class="page-subtitle">
          管理所有IDC数据中心项目，包括创建、编辑、归档等操作
        </div>
      </div>
      <div class="header-actions">
        <BaseButton
          v-if="canCreate"
          variant="success"
          icon-name="Upload"
          @click="showImportDialog = true"
        >
          批量导入
        </BaseButton>
        <BaseButton
          v-if="canCreate"
          variant="plain"
          icon-name="Download"
          @click="handleDownloadTemplate"
        >
          下载模板
        </BaseButton>
        <BaseButton
          v-if="canTransfer"
          variant="plain"
          icon-name="Share"
          @click="router.push('/projects/transfer-logs')"
        >
          转移日志
        </BaseButton>
        <BaseButton
          v-if="canCreate"
          variant="primary"
          icon-name="Plus"
          @click="router.push('/projects/create')"
        >
          新建项目
        </BaseButton>
      </div>
    </div>

    <!-- 搜索筛选（独立卡片，列表与卡片视图共用） -->
    <div class="card-box">
      <el-form
        :inline="true"
        :model="searchForm"
        class="search-form"
        aria-label="项目搜索"
      >
        <el-form-item label="项目名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入项目名称"
            clearable
            prefix-icon="Search"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="项目状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="s in statusOptions"
              :key="s"
              :label="s"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select
            v-model="searchForm.type"
            placeholder="全部类型"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="t in typeOptions"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select
            v-model="searchForm.priority"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="p in priorityOptions"
              :key="p"
              :label="p"
              :value="p"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <BaseButton
            variant="primary"
            icon-name="Search"
            @click="handleSearch"
          >
            查询
          </BaseButton>
          <BaseButton
            icon-name="Refresh"
            @click="resetSearch"
          >
            重置
          </BaseButton>
        </el-form-item>
      </el-form>
    </div>

    <!-- P1-2.2：表格视图使用 CrudTable 通用布局组件（封装 el-card + toolbar + el-table + 分页）。
         卡片视图为自定义卡片布局，保留原 card-box 结构；两者通过 v-if/v-else 切换。
         状态分栏 tabs + 视图切换按钮放在 CrudTable 的 #toolbar 槽中（表格视图）或 card-box 内（卡片视图）。
         分页参数 currentPage/pageSize 由 watch 监听，统一触发 fetchList，两个视图共享。 -->
    <CrudTable
      v-if="viewMode === 'table'"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :data="pagedProjects"
      :loading="loading"
      :total="total"
    >
      <template #toolbar>
        <div class="toolbar-left">
          <el-tabs
            v-model="activeTab"
            @tab-change="handleTabChange"
          >
            <el-tab-pane
              label="全部项目"
              name="all"
            />
            <el-tab-pane
              label="进行中"
              name="active"
            />
            <el-tab-pane
              label="已完成"
              name="completed"
            />
            <el-tab-pane
              label="已归档"
              name="archived"
            />
          </el-tabs>
        </div>
        <div class="toolbar-right">
          <div class="view-switch">
            <el-tooltip
              content="表格视图"
              placement="top"
            >
              <BaseButton
                :variant="viewMode === 'table' ? 'primary' : 'plain'"
                size="small"
                icon-name="Grid"
                aria-label="切换为表格视图"
                @click="viewMode = 'table'"
              />
            </el-tooltip>
            <el-tooltip
              content="卡片视图"
              placement="top"
            >
              <BaseButton
                :variant="viewMode === 'card' ? 'primary' : 'plain'"
                size="small"
                icon-name="Menu"
                aria-label="切换为卡片视图"
                @click="viewMode = 'card'"
              />
            </el-tooltip>
          </div>
        </div>
      </template>

      <el-table-column
        prop="code"
        label="项目编号"
        width="160"
      />
      <el-table-column
        label="项目名称"
        min-width="240"
      >
        <template #default="{ row }">
          <el-link
            type="primary"
            @click="goDetail(row.id)"
          >
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        prop="customerName"
        label="客户名称"
        min-width="180"
      />
      <el-table-column
        prop="manager"
        label="项目经理"
        width="100"
      />
      <el-table-column
        prop="type"
        label="类型"
        width="80"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.type === '新建' ? 'success' : row.type === '改造' ? 'warning' : 'info'"
          >
            {{ row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="priority"
        label="优先级"
        width="80"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.priority === '高' ? 'danger' : row.priority === '中' ? 'warning' : 'info'"
          >
            {{ row.priority }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="合同金额"
        width="120"
        align="right"
      >
        <template #default="{ row }">
          ¥{{ formatAmount(row.contractAmount) }}
        </template>
      </el-table-column>
      <el-table-column
        label="进度"
        width="160"
      >
        <template #default="{ row }">
          <el-progress
            :percentage="row.progress"
            :status="row.progress === 100 ? 'success' : ''"
            :color="getProgressColor(row.progress)"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        label="状态"
        width="100"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            type="project"
            effect="light"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="280"
        fixed="right"
      >
        <template #default="{ row }">
          <BaseButton
            variant="link"
            size="small"
            @click="goDetail(row.id)"
          >
            详情
          </BaseButton>
          <BaseButton
            v-if="canEdit"
            variant="link"
            size="small"
            :disabled="row.archived"
            @click="goEdit(row.id)"
          >
            编辑
          </BaseButton>
          <BaseButton
            variant="link"
            size="small"
            @click="goProgress(row.id)"
          >
            进度
          </BaseButton>
          <BaseButton
            v-if="canTransfer"
            variant="link"
            size="small"
            @click="openTransferDialog(row)"
          >
            转移
          </BaseButton>
          <BaseButton
            v-if="canArchive && !row.archived"
            variant="link-warning"
            size="small"
            @click="handleArchive(row)"
          >
            归档
          </BaseButton>
          <BaseButton
            v-if="canArchive && row.archived"
            variant="link-success"
            size="small"
            @click="handleUnarchive(row)"
          >
            取消归档
          </BaseButton>
          <BaseButton
            v-if="canDelete"
            variant="link-danger"
            size="small"
            @click="handleDelete(row)"
          >
            删除
          </BaseButton>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState
          icon-name="Folder"
          title="暂无项目"
          description="点击右上角新建项目开始管理"
        >
          <template #action>
            <BaseButton
              v-if="canCreate"
              variant="primary"
              icon-name="Plus"
              @click="router.push('/projects/create')"
            >
              新建项目
            </BaseButton>
          </template>
        </EmptyState>
      </template>
    </CrudTable>

    <!-- 卡片视图：保留原 card-box 布局（CrudTable 仅适用于表格视图） -->
    <div
      v-else
      class="card-box"
    >
      <div class="list-header">
        <el-tabs
          v-model="activeTab"
          @tab-change="handleTabChange"
        >
          <el-tab-pane
            label="全部项目"
            name="all"
          />
          <el-tab-pane
            label="进行中"
            name="active"
          />
          <el-tab-pane
            label="已完成"
            name="completed"
          />
          <el-tab-pane
            label="已归档"
            name="archived"
          />
        </el-tabs>
        <div class="view-switch">
          <el-tooltip
            content="表格视图"
            placement="top"
          >
            <BaseButton
              :variant="viewMode === 'table' ? 'primary' : 'plain'"
              size="small"
              icon-name="Grid"
              aria-label="切换为表格视图"
              @click="viewMode = 'table'"
            />
          </el-tooltip>
          <el-tooltip
            content="卡片视图"
            placement="top"
          >
            <BaseButton
              :variant="viewMode === 'card' ? 'primary' : 'plain'"
              size="small"
              icon-name="Menu"
              aria-label="切换为卡片视图"
              @click="viewMode = 'card'"
            />
          </el-tooltip>
        </div>
      </div>

      <TableSkeleton
        v-if="loading"
        :rows="5"
        :cols="6"
      />

      <div
        v-else
        class="project-cards"
      >
        <el-row :gutter="16">
          <el-col
            v-for="project in pagedProjects"
            :key="project.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <div
              class="project-card"
              @click="goDetail(project.id)"
            >
              <div
                class="project-card-header"
                :class="getCardHeaderClass(project)"
              >
                <div class="project-card-type">
                  <el-tag
                    size="small"
                    :type="project.type === '新建' ? 'success' : 'warning'"
                    effect="dark"
                  >
                    {{ project.type }}
                  </el-tag>
                  <el-tag
                    size="small"
                    :type="project.priority === '高' ? 'danger' : project.priority === '中' ? 'warning' : 'info'"
                    effect="dark"
                  >
                    {{ project.priority }}优先级
                  </el-tag>
                </div>
                <StatusTag
                  :value="project.status"
                  type="project"
                  effect="dark"
                  size="small"
                />
              </div>
              <div class="project-card-body">
                <div class="project-code num">
                  {{ project.code }}
                </div>
                <div
                  class="project-name"
                  :title="project.name"
                >
                  {{ project.name }}
                </div>
                <div class="project-customer">
                  <el-icon><OfficeBuilding /></el-icon>
                  {{ project.customerName }}
                </div>
                <div class="project-info-row">
                  <span><el-icon><User /></el-icon>{{ project.manager }}</span>
                  <span class="num"><el-icon><Money /></el-icon>¥{{ formatAmount(project.contractAmount) }}</span>
                </div>
                <div class="project-progress">
                  <div class="progress-label">
                    <span>项目进度</span>
                    <span class="progress-num num">{{ project.progress }}%</span>
                  </div>
                  <el-progress
                    :percentage="project.progress"
                    :stroke-width="8"
                    :show-text="false"
                    :color="getProgressColor(project.progress)"
                  />
                </div>
                <div class="project-date">
                  <el-icon><Calendar /></el-icon>
                  {{ project.startDate }} ~ {{ project.endDate }}
                </div>
              </div>
              <div
                class="project-card-footer"
                @click.stop
              >
                <BaseButton
                  variant="link"
                  size="small"
                  icon-name="View"
                  @click="goDetail(project.id)"
                >
                  详情
                </BaseButton>
                <BaseButton
                  v-if="canEdit"
                  variant="link"
                  size="small"
                  icon-name="Edit"
                  :disabled="project.archived"
                  @click="goEdit(project.id)"
                >
                  编辑
                </BaseButton>
                <BaseButton
                  variant="link"
                  size="small"
                  icon-name="DataLine"
                  @click="goProgress(project.id)"
                >
                  进度
                </BaseButton>
                <BaseButton
                  v-if="canTransfer"
                  variant="link"
                  size="small"
                  icon-name="Share"
                  @click="openTransferDialog(project)"
                >
                  转移
                </BaseButton>
              </div>
            </div>
          </el-col>
        </el-row>
        <EmptyState
          v-if="filteredProjects.length === 0"
          description="暂无项目"
        />
      </div>

      <!-- 分页 -->
      <div
        v-if="total > 0"
        class="pagination-wrapper"
      >
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </div>

    <!-- 项目转移/移交/授权对话框 -->
    <el-dialog
      v-model="transferDialogVisible"
      title="项目协作 / 授权"
      width="600px"
      :close-on-click-modal="false"
    >
      <template v-if="transferTarget">
        <el-descriptions
          :column="1"
          border
          size="small"
          style="margin-bottom: 16px"
        >
          <el-descriptions-item label="项目名称">
            {{ transferTarget.name }}
          </el-descriptions-item>
          <el-descriptions-item label="项目编号">
            {{ transferTarget.code }}
          </el-descriptions-item>
          <el-descriptions-item label="当前负责人">
            {{ transferTarget.manager || '未指定' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="transferMode === 'manager'"
          title="移交模式：将项目所有权及全部审批责任（进度 / 工作量 / 文档 / 授权申请）移交给新负责人，待审批办自动流向新负责人"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-alert
          v-else-if="transferMode === 'transfer'"
          title="转移模式：将撤销项目当前所有工程师授权，仅授权给目标工程师（不改变项目负责人）"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-alert
          v-else
          title="授权模式：保留项目当前所有授权，新增一名工程师授权"
          type="info"
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-form
          :model="transferForm"
          label-width="100px"
        >
          <el-form-item label="操作模式">
            <el-radio-group v-model="transferMode">
              <el-radio-button value="manager">
                移交负责人
              </el-radio-button>
              <el-radio-button value="transfer">
                转移（替换）
              </el-radio-button>
              <el-radio-button value="assign">
                授权（新增）
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item
            v-if="transferMode === 'manager'"
            label="新负责人"
            required
          >
            <el-select
              v-model="transferForm.toManagerId"
              placeholder="请选择新负责人（项目经理）"
              filterable
              style="width: 100%"
              :loading="managerLoading"
            >
              <el-option
                v-for="m in managerOptions"
                :key="m.id"
                :label="`${m.name}（${m.username}）${m.phone ? ' · ' + m.phone : ''}`"
                :value="m.id"
              />
            </el-select>
            <div class="dialog-tip">
              移交后您将不再是该项目负责人，该项目全部审批待办转由新负责人处理
            </div>
          </el-form-item>
          <el-form-item
            v-else
            label="目标工程师"
            required
          >
            <el-select
              v-model="transferForm.toEngineerId"
              placeholder="请选择工程师"
              filterable
              style="width: 100%"
              :loading="engineerLoading"
            >
              <el-option
                v-for="e in activeEngineers"
                :key="e.id"
                :label="`${e.name}（${e.username}）${e.phone ? ' · ' + e.phone : ''}`"
                :value="e.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="transferForm.remark"
              type="textarea"
              :rows="3"
              placeholder="请输入移交/转移/授权备注（选填，将记录到审计日志）"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <BaseButton @click="transferDialogVisible = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="transferLoading"
          @click="confirmTransfer"
        >
          确认{{ transferActionText }}
        </BaseButton>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="showImportDialog"
      title="批量导入项目"
      width="500px"
      @closed="resetImportDialog"
    >
      <!-- 关键填写要点前置（详细规则见模板内「填写说明」sheet） -->
      <el-alert
        type="info"
        :closable="false"
        class="import-tips"
      >
        <p>1. 项目类型可选：新建 / 改造 / 维护；优先级可选：高 / 中 / 低</p>
        <p>2. 日期格式必须为 yyyy-MM-dd（如 2026-03-01），且结束日期晚于开始日期</p>
        <p>3. 项目经理姓名请填写系统中已有用户，否则无法关联工作台统计与审核通知</p>
        <p>4. 模板第 2-3 行为示例数据，请修改或删除后再上传</p>
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
 * 项目管理 - 列表页
 * 业务用途：集中管理所有 IDC 数据中心项目，支持查询、筛选、分页、归档等操作。
 * 主要功能区：顶部操作栏（新建项目）、搜索筛选表单、状态分栏（全部/进行中/已完成/已归档）、
 *             表格视图与卡片视图切换、分页器。
 *
 * P1-2.2：表格视图迁移至 CrudTable 通用布局组件（封装 el-card + toolbar + el-table + 分页）。
 *         卡片视图为自定义卡片布局，保留原 card-box 结构；两者通过 v-if/v-else 切换。
 *         状态分栏 tabs + 视图切换按钮放在 CrudTable 的 #toolbar 槽中（表格视图）或 card-box 内（卡片视图），
 *         分页参数 currentPage/pageSize 由 watch 监听，统一触发 fetchList，两个视图共享。
 *
 * 数据来源：projectStore（服务端分页项目数据）、systemConfigStore（默认视图/每页条数），
 *          筛选项来自 @/constants/options 常量。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useSystemConfigStore } from '@/stores/systemConfig'
import { projectStatusOptions, projectPriorityOptions, projectTypeOptions } from '@/constants/options'
import { formatAmount, getProgressColor } from '@/utils/format'
import { feedback } from '@/utils/feedback'
import { isCancelError } from '@/utils/cancelToken'
import { hasPermission } from '@/utils/permission'
import { projectAuthApi } from '@/api/projectAuthorization'
import { projectApi } from '@/api/project'
// P1-2.2：通用列表布局组件（仅用于表格视图）
import CrudTable from '@/components/CrudTable.vue'
import { useImport } from '@/composables/useImport'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'

const router = useRouter()
const projectStore = useProjectStore()
const systemConfigStore = useSystemConfigStore()

// ===== 角色权限判定（按钮可见性） =====
// admin / project_manager 可执行写操作；工程师仅查看
const canCreate = computed(() => hasPermission('project:add'))
const canEdit = computed(() => hasPermission('project:edit'))
const canDelete = computed(() => hasPermission('project:remove'))
const canArchive = computed(() => hasPermission('project:archive'))
const canTransfer = computed(() => hasPermission('project:transfer'))

// 下拉筛选选项：状态、优先级、类型
const statusOptions = projectStatusOptions
const priorityOptions = projectPriorityOptions
const typeOptions = projectTypeOptions

const searchForm = ref({ keyword: '', status: '', type: '', priority: '' }) // 搜索筛选表单模型
const activeTab = ref('all')                                                // 当前状态分栏：all/active/completed/archived
// 默认视图和每页条数从系统配置读取
const viewMode = ref(systemConfigStore.config.defaultView || 'table')       // 视图模式：table 表格 / card 卡片
const currentPage = ref(1)                                                  // 当前页码
const pageSize = ref(systemConfigStore.config.pageSize || 10)               // 每页条数
const loading = ref(false)                                                   // 列表加载状态

// 服务端返回总数
const total = computed(() => projectStore.total)

// 列表数据直接来自 store（服务端分页）
// 表格视图与卡片视图均使用 pagedProjects，避免卡片视图前端二次过滤导致每页数量不一致
const pagedProjects = computed(() => projectStore.projects)

// 卡片视图直接复用服务端分页结果，filteredProjects 作为兼容别名保留
const filteredProjects = pagedProjects

/** 构造服务端查询参数：合并分页、搜索表单与当前分栏对应的状态/归档条件 */
function buildQueryParams() {
  const params = {
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (searchForm.value.keyword) params.keyword = searchForm.value.keyword
  if (searchForm.value.status) params.status = searchForm.value.status
  if (searchForm.value.type) params.type = searchForm.value.type
  if (searchForm.value.priority) params.priority = searchForm.value.priority
  if (activeTab.value === 'active') params.status = '进行中'
  else if (activeTab.value === 'completed') params.status = '已完成'
  else if (activeTab.value === 'archived') params.archived = true
  return params
}

/** 拉取列表数据（服务端分页），并对取消请求做静默处理 */
async function fetchList() {
  loading.value = true
  try {
    await projectStore.fetchList(buildQueryParams())
    // P2 修复：删除/归档当前页最后一条后，服务端返回空列表但页码仍指向空页，
    // 回退到最后一个有效页并重新拉取（仅服务端分页需要，客户端分页由 computed 重算）
    if (projectStore.projects.length === 0 && currentPage.value > 1) {
      const totalPages = Math.ceil(projectStore.total / pageSize.value)
      currentPage.value = Math.max(1, totalPages)
      await projectStore.fetchList(buildQueryParams())
    }
  } catch (error) {
    // 切换页面/快速筛选时主动取消上一个请求会触发 CanceledError，属正常行为，静默处理
    if (isCancelError(error)) return
    console.error('拉取项目列表失败:', error)
    feedback.error('拉取项目列表失败')
  } finally {
    loading.value = false
  }
}

/** 点击查询：重置到第 1 页并按当前筛选条件拉取 */
function handleSearch() {
  currentPage.value = 1
  fetchList()
}

/** 重置筛选表单并回到第 1 页重新拉取 */
function resetSearch() {
  searchForm.value = { keyword: '', status: '', type: '', priority: '' }
  currentPage.value = 1
  fetchList()
}

/** 切换状态分栏：清空筛选、回到第 1 页并重新拉取 */
function handleTabChange() {
  searchForm.value = { keyword: '', status: '', type: '', priority: '' }
  currentPage.value = 1
  fetchList()
}

// 监听分页（页码/每页条数）变化，自动重新拉取列表（表格与卡片视图共用）
watch([currentPage, pageSize], () => {
  fetchList()
})

/**
 * 计算卡片头部的样式类（不同状态/优先级对应不同渐变色）
 * @param {Object} project 项目对象
 * @returns {string} CSS 类名
 */
function getCardHeaderClass(project) {
  if (project.status === '已完成') return 'header-success'
  if (project.status === '已归档') return 'header-info'
  if (project.priority === '高') return 'header-danger'
  return 'header-primary'
}

/** 跳转项目详情页 @param {number|string} id 项目 ID */
function goDetail(id) {
  router.push(`/projects/${id}`)
}

/** 跳转项目编辑页 @param {number|string} id 项目 ID */
function goEdit(id) {
  router.push(`/projects/${id}/edit`)
}

/** 跳转项目进度页 @param {number|string} id 项目 ID */
function goProgress(id) {
  router.push(`/progress/${id}`)
}

/**
 * 归档项目：二次确认后调用 store 归档接口并刷新列表
 * @param {Object} row 当前行项目对象
 */
function handleArchive(row) {
  feedback.confirm(`确认归档项目「${row.name}」吗？归档后项目将变为只读状态。`, {
    title: '归档确认',
    type: 'warning'
  }).then(async () => {
    await projectStore.archiveProject(row.id)
    fetchList()
  }).catch(() => {})
}

/**
 * 取消归档：二次确认后调用 store 取消归档接口并刷新列表
 * @param {Object} row 当前行项目对象
 */
async function handleUnarchive(row) {
  // 修复 M15: 原代码无 try/catch 错误处理、无二次确认，与 handleArchive 实现不一致。补充二次确认与错误处理。
  try {
    await feedback.confirm(`确认取消归档项目「${row.name}」吗？`, { title: '取消归档确认', type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    await projectStore.unarchiveProject(row.id)
    feedback.success('取消归档成功')
    fetchList()
  } catch (e) {
    console.error('取消归档失败', e)
  }
}

/**
 * 删除项目：二次确认后调用 store 删除接口并刷新列表
 * @param {Object} row 当前行项目对象
 */
function handleDelete(row) {
  feedback.confirmDelete(`确认删除项目「${row.name}」？`).then(async () => {
    try {
      await projectStore.deleteProject(row.id)
      feedback.success('删除成功')
      fetchList()
    } catch (e) {
      feedback.error(e.message || '删除失败')
    }
  }).catch(() => {})
}

/* ===== 项目协作（移交/转移/授权）功能（仅 PM/admin）===== */

// 转移对话框状态
const transferDialogVisible = ref(false)
const transferTarget = ref(null)         // 当前操作的项目对象
const transferMode = ref('transfer')     // manager（移交负责人）/ transfer（替换工程师）/ assign（新增授权）
const transferForm = ref({ toManagerId: null, toEngineerId: null, remark: '' })
const transferLoading = ref(false)
const activeEngineers = ref([])
const engineerLoading = ref(false)
const activeManagers = ref([])
const managerLoading = ref(false)

// 当前模式对应的操作动词（按钮/确认框文案）
const transferActionText = computed(() =>
  transferMode.value === 'manager' ? '移交' : (transferMode.value === 'transfer' ? '转移' : '授权')
)

// 新负责人下拉选项：过滤当前负责人本人（重名场景由后端 toManagerId==managerId 硬校验兜底）
const managerOptions = computed(() =>
  activeManagers.value.filter((m) => m.name !== transferTarget.value?.manager)
)

/**
 * 打开协作对话框：加载活跃工程师列表
 * @param {Object} row 项目对象
 */
async function openTransferDialog(row) {
  transferTarget.value = row
  transferMode.value = 'transfer'
  transferForm.value = { toManagerId: null, toEngineerId: null, remark: '' }
  transferDialogVisible.value = true
  // 懒加载活跃工程师列表
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

// 切换到移交模式时懒加载活跃项目经理列表
watch(transferMode, async (mode) => {
  if (mode === 'manager' && activeManagers.value.length === 0) {
    managerLoading.value = true
    try {
      const res = await projectAuthApi.listActiveManagers()
      activeManagers.value = res.data || []
    } catch (e) {
      feedback.error('加载负责人列表失败')
    } finally {
      managerLoading.value = false
    }
  }
})

/**
 * 确认移交/转移/授权：按模式调用对应接口，成功后关闭对话框；
 * 移交模式额外刷新列表（当前用户可能已非该项目的负责人）
 */
async function confirmTransfer() {
  const isManager = transferMode.value === 'manager'
  // 分模式必填校验
  if (isManager && !transferForm.value.toManagerId) {
    feedback.warning('请选择新负责人')
    return
  }
  if (!isManager && !transferForm.value.toEngineerId) {
    feedback.warning('请选择目标工程师')
    return
  }
  if (!transferTarget.value) return
  const projectId = transferTarget.value.id
  const actionText = transferActionText.value

  // 构造目标人展示名（新负责人或目标工程师）
  let targetLabel = ''
  if (isManager) {
    const manager = activeManagers.value.find((m) => m.id === transferForm.value.toManagerId)
    targetLabel = manager ? `${manager.name}（${manager.username}）` : ''
  } else {
    const engineer = activeEngineers.value.find((e) => e.id === transferForm.value.toEngineerId)
    targetLabel = engineer ? `${engineer.name}（${engineer.username}）` : ''
  }

  // 二次确认（移交为不可自助撤回的高影响操作，文案单独强调审批责任转移）
  const confirmMsg = isManager
    ? `确认将项目「${transferTarget.value.name}」移交给 ${targetLabel} 负责？移交后该项目全部审批待办转由新负责人处理，您将失去该项目负责人权限`
    : `确认将项目「${transferTarget.value.name}」${actionText}给工程师 ${targetLabel}？`
  try {
    await feedback.confirm(confirmMsg, {
      title: `${actionText}确认`,
      type: transferMode.value === 'assign' ? 'info' : 'warning'
    })
  } catch (e) {
    return // 用户取消
  }

  transferLoading.value = true
  try {
    if (isManager) {
      // 仅传 toManagerId：移交所有权与审批责任，工程师授权保持不变
      await projectAuthApi.transfer(projectId, {
        toManagerId: transferForm.value.toManagerId,
        remark: transferForm.value.remark
      })
    } else if (transferMode.value === 'transfer') {
      await projectAuthApi.transfer(projectId, {
        toEngineerId: transferForm.value.toEngineerId,
        remark: transferForm.value.remark
      })
    } else {
      await projectAuthApi.assign(projectId, {
        toEngineerId: transferForm.value.toEngineerId,
        remark: transferForm.value.remark
      })
    }
    feedback.success(`${actionText}成功`)
    transferDialogVisible.value = false
    // 移交后刷新列表：负责人列变更，且当前用户可能已无该项目操作权限
    if (isManager) fetchList()
  } catch (e) {
    // request 拦截器已统一提示错误
    console.error(`[项目${actionText}] 失败:`, e?.message)
  } finally {
    transferLoading.value = false
  }
}

/* ===== 批量导入 / 模板下载 ===== */

// P3 重构：公共导入流程统一由 useImport 提供，此处仅注入项目模块差异项
const {
  showImportDialog, importLoading, uploadRef,
  handleFileChange, handleExceed, resetImportDialog,
  handleDownloadTemplate, handleImport
} = useImport({
  downloadTemplate: () => projectApi.downloadImportTemplate(),
  importFn: (file) => projectApi.importProjects(file),
  templateName: 'project_import_template.xlsx',
  onSuccess: () => fetchList()
})

// 首次挂载时加载列表
onMounted(() => {
  fetchList()
})

// 缓存页面被重新激活时（如从编辑页返回）刷新列表，避免展示旧数据
// （首次激活由 useKeepAliveRefresh 自动跳过，避免与 onMounted 请求双发）
useKeepAliveRefresh(() => fetchList())
</script>

<style scoped>
.dialog-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 12px 0;
}

.search-form {
  margin-bottom: 0;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.list-header :deep(.el-tabs) {
  flex: 1;
}

.list-header :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.view-switch {
  display: flex;
  gap: 4px;
}

.project-cards {
  margin-top: 8px;
}

.project-card {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 16px;
  cursor: pointer;
  /* 光晕 Token（Glow+Grain 铺开）：曲线/时长走全局动效 Token */
  transition: transform var(--dur-base) var(--ease-out-quart),
    box-shadow var(--dur-base) var(--ease-out-quart),
    border-color var(--dur-base);
  height: calc(100% - 16px);
  display: flex;
  flex-direction: column;
}

/* hover 泛出品牌蓝光晕（替代中性灰阴影），与工作台 StatCard 同一视觉语言 */
.project-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--glow-primary);
  border-color: #c9e3fb;
}

.project-card-header {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #fff;
}

.header-primary {
  background: linear-gradient(135deg, #1890ff, #40a9ff);
}

.header-success {
  background: linear-gradient(135deg, #52c41a, #73d13d);
}

.header-danger {
  background: linear-gradient(135deg, #f5222d, #ff7875);
}

.header-info {
  background: linear-gradient(135deg, #909399, #b1b3b8);
}

.project-card-type {
  display: flex;
  gap: 6px;
}

.project-card-body {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-code {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}

.project-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

.project-customer {
  font-size: 13px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-info-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
}

.project-info-row span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.project-progress {
  margin-top: 4px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.progress-num {
  font-weight: 600;
  color: #1f2937;
}

.project-date {
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}

.project-card-footer {
  padding: 10px 16px;
  border-top: 1px solid #f3f4f6;
  display: flex;
  justify-content: space-around;
  background: #fafbfc;
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
