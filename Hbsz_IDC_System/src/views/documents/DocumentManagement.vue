<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          文档管理
        </div>
        <div class="page-subtitle">
          管理项目相关技术文档、合同文件、验收报告等
        </div>
      </div>
      <BaseButton
        variant="primary"
        icon-name="Upload"
        @click="showUpload = true"
      >
        上传文档
      </BaseButton>
    </div>

    <el-row :gutter="20">
      <!-- 左侧分类树 -->
      <el-col
        :xs="24"
        :sm="6"
        :md="5"
        :lg="4"
      >
        <div class="card-box category-box">
          <div class="category-title">
            <el-icon><FolderOpened /></el-icon>
            <span>文档分类</span>
          </div>
          <el-menu
            :default-active="activeCategory"
            @select="handleCategorySelect"
          >
            <el-menu-item index="all">
              <el-icon><Files /></el-icon>
              <span>全部文档</span>
              <el-badge
                :value="totalAllCount"
                :max="999"
                class="menu-badge"
              />
            </el-menu-item>
            <el-menu-item
              v-for="cat in categories"
              :key="cat"
              :index="cat"
            >
              <el-icon><Folder /></el-icon>
              <span>{{ cat }}</span>
              <el-badge
                :value="documentStore.getCategoryCount(cat)"
                :max="999"
                class="menu-badge"
              />
            </el-menu-item>
          </el-menu>
        </div>
      </el-col>

      <!-- 右侧文档列表 -->
      <el-col
        :xs="24"
        :sm="18"
        :md="19"
        :lg="20"
      >
        <!-- P1-2.2：列表视图使用 CrudTable 通用布局组件（封装 el-card + toolbar + el-table + 分页）。
             网格视图为自定义卡片布局，保留原 card-box 结构；两者通过 v-if/v-else 切换。
             搜索栏 + 视图切换按钮放在 CrudTable 的 #toolbar 槽中（列表视图）或 card-box 内（网格视图）。
             分页参数 currentPage/pageSize 由 watch 监听，统一触发 fetchList，列表与网格视图共享。 -->
        <CrudTable
          v-if="viewMode === 'list'"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :data="pagedDocuments"
          :loading="loading"
          :total="filteredDocuments.length"
        >
          <template #toolbar>
            <div class="toolbar-left">
              <span class="title-chip title-chip--primary"><el-icon><Document /></el-icon></span>
              <span class="toolbar-title">文档列表</span>
            </div>
            <div class="toolbar-right">
              <el-input
                v-model="searchForm.keyword"
                placeholder="搜索文档名称、编号..."
                clearable
                prefix-icon="Search"
                style="width: 280px"
                @keyup.enter="handleSearch"
              />
              <el-select
                v-model="searchForm.projectId"
                placeholder="所属项目"
                clearable
                style="width: 200px"
              >
                <el-option
                  v-for="p in projectStore.projects"
                  :key="p.id"
                  :label="p.name"
                  :value="p.id"
                />
              </el-select>
              <el-select
                v-model="searchForm.fileType"
                placeholder="文件类型"
                clearable
                style="width: 120px"
              >
                <el-option
                  v-for="t in fileTypeOptions"
                  :key="t"
                  :label="t"
                  :value="t"
                />
              </el-select>
              <div class="view-switch">
                <el-tooltip
                  content="列表视图"
                  placement="top"
                >
                  <BaseButton
                    :variant="viewMode === 'list' ? 'primary' : 'plain'"
                    size="small"
                    icon-name="Grid"
                    aria-label="切换为列表视图"
                    @click="viewMode = 'list'"
                  />
                </el-tooltip>
                <el-tooltip
                  content="网格视图"
                  placement="top"
                >
                  <BaseButton
                    :variant="viewMode === 'grid' ? 'primary' : 'plain'"
                    size="small"
                    icon-name="Menu"
                    aria-label="切换为网格视图"
                    @click="viewMode = 'grid'"
                  />
                </el-tooltip>
              </div>
            </div>
          </template>

          <el-table-column
            label="文档名称"
            min-width="280"
          >
            <template #default="{ row }">
              <div class="doc-name-cell">
                <div
                  class="doc-icon"
                  :class="getIconClass(row.fileType)"
                >
                  <el-icon><component :is="getIconComponent(row.fileType)" /></el-icon>
                </div>
                <div class="doc-info">
                  <el-link
                    type="primary"
                    @click="handlePreview(row)"
                  >
                    {{ row.name }}
                  </el-link>
                  <div class="doc-meta">
                    {{ row.code }} · v{{ row.version }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="category"
            label="分类"
            width="120"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="getCategoryTagType(row.category)"
              >
                {{ row.category }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="所属项目"
            min-width="180"
          >
            <template #default="{ row }">
              {{ getProjectName(row.projectId) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="fileType"
            label="类型"
            width="80"
          >
            <template #default="{ row }">
              <span class="file-type-badge">{{ row.fileType }}</span>
            </template>
          </el-table-column>
          <el-table-column
            prop="size"
            label="大小"
            width="100"
          >
            <template #default="{ row }">
              {{ row.size || '-' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="uploader"
            label="上传人"
            width="100"
          />
          <el-table-column
            prop="uploadDate"
            label="上传时间"
            width="120"
          />
          <el-table-column
            label="状态"
            width="100"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.status === '已审核' ? 'success' : row.status === '已驳回' ? 'danger' : 'warning'"
                effect="light"
              >
                {{ row.status || '待审核' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="370"
            fixed="right"
          >
            <template #default="{ row }">
              <BaseButton
                v-if="row.status === '待审核'"
                variant="link-success"
                size="small"
                icon-name="Check"
                @click="handleReview(row)"
              >
                审核
              </BaseButton>
              <BaseButton
                variant="link"
                size="small"
                icon-name="View"
                @click="handlePreview(row)"
              >
                预览
              </BaseButton>
              <BaseButton
                variant="link"
                size="small"
                icon-name="Edit"
                @click="handleEdit(row)"
              >
                编辑
              </BaseButton>
              <BaseButton
                variant="link"
                size="small"
                icon-name="Files"
                @click="handleVersion(row)"
              >
                版本
              </BaseButton>
              <BaseButton
                variant="link"
                size="small"
                icon-name="Download"
                @click="handleDownload(row)"
              >
                下载
              </BaseButton>
              <BaseButton
                variant="link-danger"
                size="small"
                icon-name="Delete"
                @click="handleDelete(row)"
              >
                删除
              </BaseButton>
            </template>
          </el-table-column>

          <template #empty>
            <EmptyState
              icon-name="Document"
              title="暂无文档"
              description="上传文档后可在此管理"
            />
          </template>
        </CrudTable>

        <!-- 网格视图：保留原 card-box 布局（CrudTable 仅适用于表格视图） -->
        <div
          v-else
          class="card-box"
        >
          <div class="search-bar">
            <el-input
              v-model="searchForm.keyword"
              placeholder="搜索文档名称、编号..."
              clearable
              prefix-icon="Search"
              style="width: 280px"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-model="searchForm.projectId"
              placeholder="所属项目"
              clearable
              style="width: 200px; margin-left: 12px"
            >
              <el-option
                v-for="p in projectStore.projects"
                :key="p.id"
                :label="p.name"
                :value="p.id"
              />
            </el-select>
            <el-select
              v-model="searchForm.fileType"
              placeholder="文件类型"
              clearable
              style="width: 120px; margin-left: 12px"
            >
              <el-option
                v-for="t in fileTypeOptions"
                :key="t"
                :label="t"
                :value="t"
              />
            </el-select>
            <div class="view-switch">
              <el-tooltip
                content="列表视图"
                placement="top"
              >
                <BaseButton
                  :variant="viewMode === 'list' ? 'primary' : 'plain'"
                  size="small"
                  icon-name="Grid"
                  aria-label="切换为列表视图"
                  @click="viewMode = 'list'"
                />
              </el-tooltip>
              <el-tooltip
                content="网格视图"
                placement="top"
              >
                <BaseButton
                  :variant="viewMode === 'grid' ? 'primary' : 'plain'"
                  size="small"
                  icon-name="Menu"
                  aria-label="切换为网格视图"
                  @click="viewMode = 'grid'"
                />
              </el-tooltip>
            </div>
          </div>

          <div class="doc-grid">
            <div
              v-for="doc in pagedDocuments"
              :key="doc.id"
              class="doc-card"
              @click="handlePreview(doc)"
            >
              <div
                class="doc-card-icon"
                :class="getIconClass(doc.fileType)"
              >
                <el-icon :size="40">
                  <component :is="getIconComponent(doc.fileType)" />
                </el-icon>
                <div class="doc-card-type">
                  {{ doc.fileType }}
                </div>
              </div>
              <div class="doc-card-info">
                <div
                  class="doc-card-name"
                  :title="doc.name"
                >
                  {{ doc.name }}
                </div>
                <div class="doc-card-meta">
                  <el-tag
                    size="small"
                    :type="getCategoryTagType(doc.category)"
                  >
                    {{ doc.category }}
                  </el-tag>
                  <span>{{ doc.size || '-' }}</span>
                </div>
                <div class="doc-card-bottom">
                  <span class="doc-card-project">{{ getProjectName(doc.projectId) }}</span>
                  <span class="doc-card-date">{{ doc.uploadDate }}</span>
                </div>
              </div>
              <div
                class="doc-card-actions"
                @click.stop
              >
                <button
                  class="action-btn action-preview"
                  title="预览"
                  @click="handlePreview(doc)"
                >
                  <el-icon><View /></el-icon>
                </button>
                <button
                  class="action-btn action-download"
                  title="下载"
                  @click="handleDownload(doc)"
                >
                  <el-icon><Download /></el-icon>
                </button>
                <button
                  class="action-btn action-delete"
                  title="删除"
                  @click="handleDelete(doc)"
                >
                  <el-icon><Delete /></el-icon>
                </button>
              </div>
            </div>
            <el-empty
              v-if="filteredDocuments.length === 0"
              description="暂无文档"
            />
          </div>

          <div
            v-if="filteredDocuments.length > 0"
            class="pagination-wrapper"
          >
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="filteredDocuments.length"
              layout="total, sizes, prev, pager, next, jumper"
              background
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- P1-2.1：5 个对话框已抽取为独立组件，统一通过 v-model:visible 控制显隐 -->
    <DocumentUploadDialog
      v-model:visible="showUpload"
      :categories="categories"
      :projects="projectStore.projects"
      @success="onDialogSuccess"
    />
    <DocumentReviewDialog
      v-model:visible="showReview"
      :doc="currentDoc"
      @success="onDialogSuccess"
    />
    <DocumentPreviewDialog
      v-model:visible="showPreview"
      :doc="currentDoc"
      @download="handleDownload(currentDoc)"
    />
    <DocumentEditDialog
      v-model:visible="showEdit"
      :doc="currentDoc"
      :categories="categories"
      :projects="projectStore.projects"
      @success="onDialogSuccess"
    />
    <DocumentVersionDialog
      v-model:visible="showVersion"
      :doc="currentDoc"
      @success="fetchList"
    />
  </div>
</template>

<script setup>
/**
 * 文档管理页
 * 业务用途：统一管理项目相关的技术文档、合同文件、验收报告等，支持上传、预览、编辑、版本管理、下载与删除。
 * 主要功能区：左侧文档分类树（含各分类计数）、右侧搜索筛选与列表/网格视图、分页。
 *
 * P1-2.1：5 个对话框（上传/审核/预览/编辑/版本）已抽取到 ./components/ 目录下，
 *        本页仅负责列表与网格视图、搜索筛选、分页、对话框显隐控制与刷新协调。
 *
 * P1-2.2：列表视图迁移至 CrudTable 通用布局组件（封装 el-card + toolbar + el-table + 分页）。
 *         网格视图为自定义卡片布局，保留原 card-box 结构；两者通过 v-if/v-else 切换。
 *         搜索栏 + 视图切换按钮同时存在于两个视图（在 #toolbar 槽与 card-box 内分别渲染），
 *         分页参数 currentPage/pageSize 由 watch 监听，统一触发 fetchList，两个视图共享。
 *
 * 数据来源：documentStore（文档列表/分类计数/详情接口）、projectStore（项目下拉），
 *          分类选项来自 @/constants/options，图标工具来自 ./utils/documentIcons。
 */
// 注：BaseButton / TableSkeleton / EmptyState 已在 main.js 全局注册，无需在此导入
import { ref, computed, onMounted, watch } from 'vue'
import { useDocumentStore } from '@/stores/document'
import { useProjectStore } from '@/stores/project'
import { documentCategoryOptions } from '@/constants/options'
import { feedback } from '@/utils/feedback'
import { getCategoryTagType } from '@/utils/format'
import { downloadBlob } from '@/utils/download'
import { isCancelError } from '@/utils/cancelToken'
import {
  Document, Files, Folder, FolderOpened,
  View, Download, Delete
} from '@element-plus/icons-vue'
// P1-2.1：抽取的对话框组件
import DocumentUploadDialog from './components/DocumentUploadDialog.vue'
import DocumentReviewDialog from './components/DocumentReviewDialog.vue'
import DocumentPreviewDialog from './components/DocumentPreviewDialog.vue'
import DocumentEditDialog from './components/DocumentEditDialog.vue'
import DocumentVersionDialog from './components/DocumentVersionDialog.vue'
// P1-2.1：抽取的图标工具函数（与 DocumentPreviewDialog 共享）
import { getIconClass, getIconComponent } from './utils/documentIcons'
// P1-2.2：通用列表布局组件（仅用于列表视图）
import CrudTable from '@/components/CrudTable.vue'
import { useKeepAliveRefresh } from '@/composables/useKeepAliveRefresh'

const documentStore = useDocumentStore()
const projectStore = useProjectStore()

const categories = documentCategoryOptions  // 文档分类选项
const fileTypeOptions = ['PDF', 'DOC', 'DOCX', 'XLS', 'XLSX', 'PPT', 'PPTX', 'JPG', 'PNG', 'DWG', 'ZIP']  // 文件类型筛选选项

const activeCategory = ref('all')   // 当前选中的左侧分类（all 表示全部）
const viewMode = ref('list')        // 视图模式：list 列表 / grid 网格
const loading = ref(false)          // 列表加载中状态（控制骨架屏显隐）
const currentPage = ref(1)          // 当前页码
const pageSize = ref(10)            // 每页条数
const showUpload = ref(false)       // 上传对话框显隐
const showPreview = ref(false)      // 预览对话框显隐
const showEdit = ref(false)         // 编辑对话框显隐
const showVersion = ref(false)      // 版本管理对话框显隐
const showReview = ref(false)       // 审核对话框显隐
const currentDoc = ref(null)        // 当前操作的文档（传给 Review/Preview/Edit/Version 对话框）
const searchForm = ref({ keyword: '', projectId: '', fileType: '' })  // 搜索筛选表单

// 前端综合过滤：按分类、关键词（名称/编号）、所属项目、文件类型筛选文档
const filteredDocuments = computed(() => {
  return documentStore.documents.filter(d => {
    let match = true
    if (activeCategory.value !== 'all') match = match && d.category === activeCategory.value
    if (searchForm.value.keyword) {
      match = match && (d.name.toLowerCase().includes(searchForm.value.keyword.toLowerCase()) ||
        (d.code || '').toLowerCase().includes(searchForm.value.keyword.toLowerCase()))
    }
    if (searchForm.value.projectId) match = match && String(d.projectId) === String(searchForm.value.projectId)
    if (searchForm.value.fileType) match = match && d.fileType === searchForm.value.fileType
    return match
  })
})

// 分页后的数据
const pagedDocuments = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredDocuments.value.slice(start, start + pageSize.value)
})

/** 全部文档总数（各分类计数之和，独立于当前列表数据） */
const totalAllCount = computed(() => {
  return Object.values(documentStore.categoryCounts).reduce((sum, n) => sum + (Number(n) || 0), 0)
})

/** 构造查询参数 */
function buildQueryParams() {
  const params = {
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (activeCategory.value !== 'all') params.category = activeCategory.value
  if (searchForm.value.keyword) params.keyword = searchForm.value.keyword
  if (searchForm.value.projectId) params.projectId = searchForm.value.projectId
  if (searchForm.value.fileType) params.fileType = searchForm.value.fileType
  return params
}

/** 拉取列表数据 */
async function fetchList() {
  loading.value = true
  try {
    await documentStore.fetchList(buildQueryParams())
  } catch (error) {
    // 切换页面/分类时主动取消上一个请求会触发 CanceledError，属正常行为，静默处理
    if (!isCancelError(error)) {
      console.error('拉取文档列表失败:', error)
      feedback.error('拉取文档列表失败')
    }
  } finally {
    loading.value = false
  }
}

/** 拉取分类计数（独立于列表数据，避免切换分类时计数变 0） */
async function refreshCategoryStats() {
  try {
    await documentStore.fetchCategoryStats()
  } catch (error) {
    if (isCancelError(error)) return
    console.error('拉取分类统计失败:', error)
  }
}

/** 切换左侧分类：更新选中项、回到第 1 页并重新拉取 @param {string} index 分类名或 'all' */
function handleCategorySelect(index) {
  activeCategory.value = index
  currentPage.value = 1
  fetchList()
}

/** 搜索：回到第 1 页并重新拉取列表 */
function handleSearch() {
  currentPage.value = 1
  fetchList()
}

/** 根据项目 ID 获取项目名称 @param {number|string} id 项目 ID @returns {string} 项目名，未匹配返回"未关联" */
function getProjectName(id) {
  const p = projectStore.projects.find(p => String(p.id) === String(id))
  return p ? p.name : '未关联'
}

/** 打开预览对话框：先拉取详情以获取完整字段 @param {Object} doc 文档对象 */
async function handlePreview(doc) {
  try {
    await documentStore.fetchDetail(doc.id)
    currentDoc.value = documentStore.currentDocument
    showPreview.value = true
  } catch (error) {
    console.error('加载文档详情失败:', error)
  }
}

/** 打开审核对话框：使用列表行数据（仅需 id + name） @param {Object} doc 文档对象 */
function handleReview(doc) {
  currentDoc.value = doc
  showReview.value = true
}

/** 打开编辑对话框：先拉取详情以获取标签、描述等完整字段 @param {Object} doc 文档对象 */
async function handleEdit(doc) {
  try {
    await documentStore.fetchDetail(doc.id)
    currentDoc.value = documentStore.currentDocument || doc
    showEdit.value = true
  } catch (error) {
    console.error('加载文档详情失败:', error)
    // 兜底：使用列表行数据
    currentDoc.value = doc
    showEdit.value = true
  }
}

/** 打开版本管理对话框：先拉取详情，对话框内部会自动拉取版本列表 @param {Object} doc 文档对象 */
async function handleVersion(doc) {
  try {
    await documentStore.fetchDetail(doc.id)
    currentDoc.value = documentStore.currentDocument || doc
    showVersion.value = true
  } catch (error) {
    console.error('加载文档详情失败:', error)
    // 兜底：使用列表行数据打开版本对话框
    currentDoc.value = doc
    showVersion.value = true
  }
}

/** 下载文档：触发浏览器文件下载 @param {Object} doc 文档对象 */
async function handleDownload(doc) {
  if (!doc) return
  try {
    const res = await documentStore.downloadDocument(doc.id)
    downloadBlob(res.data, doc.name)
  } catch (error) {
    console.error('下载失败:', error)
    feedback.error('下载失败，请稍后重试')
  }
}

/** 删除文档：二次确认后调用删除接口，并刷新列表与分类计数 @param {Object} doc 文档对象 */
function handleDelete(doc) {
  feedback.confirmDelete(`确认删除文档「${doc.name}」？`).then(async () => {
    await documentStore.deleteDocument(doc.id)
    fetchList()
    refreshCategoryStats()
    feedback.success('删除成功')
  }).catch(() => {})
}

/** 对话框操作成功后的统一回调：刷新列表与分类计数（版本对话框仅需刷新列表） */
function onDialogSuccess() {
  fetchList()
  refreshCategoryStats()
}

// 分页（页码/每页条数）变化时重新拉取列表（列表与网格视图共用）
watch([currentPage, pageSize], () => {
  fetchList()
})

// 首次挂载：拉取文档列表、分类计数，并加载项目下拉数据
onMounted(() => {
  fetchList()
  // 拉取分类计数（独立于列表数据）
  refreshCategoryStats()
  // 加载项目列表用于下拉选择
  projectStore.fetchList({ pageSize: 100 }).catch(() => {})
})

// 缓存页面被重新激活时刷新列表，避免展示旧数据
// 仅刷新列表与分类计数，不重复拉取项目下拉（稳定数据）
// （首次激活由 useKeepAliveRefresh 自动跳过，避免与 onMounted 请求双发）
useKeepAliveRefresh(() => {
  fetchList()
  refreshCategoryStats()
})
</script>

<style scoped>
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 12px 0;
}

.category-box {
  padding: 12px 0;
}

.category-title {
  padding: 0 16px 12px;
  font-weight: 600;
  font-size: 14px;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 6px;
  border-bottom: 1px solid #f3f4f6;
}

.category-title + .el-menu {
  border-right: none;
}

/* 菜单项选中状态 - 确保文字清晰可读 */
.category-title + .el-menu .el-menu-item.is-active {
  color: #ffffff !important;
  background-color: #1890ff !important;
}

.category-title + .el-menu .el-menu-item.is-active .el-icon {
  color: #ffffff !important;
}

.category-title + .el-menu .el-menu-item.is-active .el-badge__content {
  background-color: #ffffff !important;
  color: #1890ff !important;
}

/* 菜单项悬停效果 */
.category-title + .el-menu .el-menu-item:hover {
  background-color: #e6f7ff !important;
  color: #1890ff !important;
}

.category-title + .el-menu .el-menu-item:hover .el-icon {
  color: #1890ff !important;
}

.menu-badge {
  margin-left: auto;
  align-self: center;
}

/* 徽章与文字垂直居中 */
.menu-badge :deep(.el-badge__content) {
  vertical-align: middle;
  margin-top: 0 !important;
}

.category-title + .el-menu .el-menu-item {
  display: flex;
  align-items: center;
}

.search-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.view-switch {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 文件类型图标：软底色 + 主题色图标（与全站 title-chip/StatCard 色板一致的芯片风格） */
.doc-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.icon-pdf { background: #fff1f0; color: #f5222d; }
.icon-word { background: #e6f7ff; color: #1890ff; }
.icon-excel { background: #f6ffed; color: #52c41a; }
.icon-ppt { background: #fff7e6; color: #fa541c; }
.icon-image { background: #f9f0ff; color: #722ed1; }
.icon-cad { background: #e6fffb; color: #13c2c2; }
.icon-zip { background: #fffbe6; color: #faad14; }
.icon-default { background: #f4f4f5; color: #909399; }

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.file-type-badge {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 3px;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.doc-card {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  gap: 12px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.doc-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border-color: #1890ff;
}

.doc-card-icon {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  position: relative;
}

.doc-card-type {
  font-size: 10px;
  font-weight: 600;
  margin-top: 2px;
  background: rgba(0, 0, 0, 0.2);
  padding: 1px 6px;
  border-radius: 3px;
}

.doc-card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-card-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.doc-card-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
  margin-top: auto;
}

.doc-card-project {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.doc-card-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 4px;
  padding: 4px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  opacity: 0;
  transform: translateY(-4px);
  transition: all 0.25s ease;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.doc-card:hover .doc-card-actions {
  opacity: 1;
  transform: translateY(0);
}

/* 操作按钮基础样式 */
.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: #6b7280;
  transition: all 0.2s ease;
}

.action-btn .el-icon {
  font-size: 15px;
}

.action-btn:hover {
  transform: scale(1.15);
}

/* 预览按钮 - 蓝色 */
.action-preview:hover {
  background: #e6f7ff;
  color: #1890ff;
}

/* 下载按钮 - 绿色 */
.action-download:hover {
  background: #f6ffed;
  color: #52c41a;
}

/* 删除按钮 - 红色 */
.action-delete:hover {
  background: #fff1f0;
  color: #f5222d;
}
</style>
