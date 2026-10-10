/**
 * 文档状态仓库（Pinia setup store）
 *
 * 维护文档列表、分页总数、当前文档详情及各分类计数（独立于列表，避免切换分类时归零）。
 * 派生按分类分组视图，并封装列表/详情拉取、上传、更新、版本、删除、下载与预览等动作。
 * 分类统计拉取对请求取消（CanceledError）做静默处理。
 * @module stores/document
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { documentApi } from '@/api/document'
import { feedback } from '@/utils/feedback'
import { isCancelError } from '@/utils/cancelToken'

export const useDocumentStore = defineStore('document', () => {
  // ===== state =====
  /** @type {import('vue').Ref<Array>} 当前列表页的文档集合 */
  const documents = ref([])
  /** @type {import('vue').Ref<number>} 列表总记录数（用于分页） */
  const total = ref(0)
  /** @type {import('vue').Ref<boolean>} 列表加载中标记 */
  const loading = ref(false)
  /** @type {import('vue').Ref<Object|null>} 当前查看的文档详情 */
  const currentDocument = ref(null)
  // 各分类文档数量（独立于当前列表数据，避免切换分类时计数变 0）
  /** @type {import('vue').Ref<Object<string, number>>} 分类名到文档数量的映射 */
  const categoryCounts = ref({})
  /** fetchList 竞态保护序列号 */
  let fetchListSeq = 0

  // ===== getters =====
  /** 将当前文档列表按 category 字段分组 */
  const documentsByCategory = computed(() => {
    const groups = {}
    documents.value.forEach(doc => {
      if (!groups[doc.category]) groups[doc.category] = []
      groups[doc.category].push(doc)
    })
    return groups
  })

  /**
   * 从已加载列表中按项目 ID 过滤文档（本地过滤，不发请求）
   * @param {number|string} projectId 项目 ID
   * @returns {Array} 该项目关联的文档列表
   */
  function getDocumentsByProject(projectId) {
    return documents.value.filter(d => String(d.projectId) === String(projectId))
  }

  /**
   * 从已加载列表中按 ID 查找文档（本地查找，不发请求）
   * @param {number|string} id 文档 ID
   * @returns {Object|undefined} 匹配的文档，未找到返回 undefined
   */
  function getDocumentById(id) {
    return documents.value.find(d => String(d.id) === String(id))
  }

  /**
   * 拉取文档列表
   *
   * 副作用：更新 documents、total 与 loading；兼容后端 records/list 两种字段。
   * @param {Object} [params={}] 分页与筛选参数（分类、关键字等）
   * @returns {Promise<Object>} 列表响应数据
   */
  async function fetchList(params = {}) {
    const currentSeq = ++fetchListSeq
    loading.value = true
    try {
      const res = await documentApi.getList(params)
      if (currentSeq !== fetchListSeq) return res.data
      documents.value = res.data.records || res.data.list || []
      total.value = res.data.total || 0
      return res.data
    } catch (error) {
      if (currentSeq !== fetchListSeq) return null
      if (isCancelError(error)) return null
      throw error
    } finally {
      if (currentSeq === fetchListSeq) {
        loading.value = false
      }
    }
  }

  /**
   * 拉取各分类文档数量（独立于列表数据）
   *
   * 副作用：更新 categoryCounts。切换页面时上一请求被取消触发的 CanceledError 属正常行为，静默处理。
   * @returns {Promise<Object<string, number>>} 分类计数映射（异常时返回空对象）
   */
  async function fetchCategoryStats() {
    try {
      const res = await documentApi.getCategoryStats()
      const map = {}
      ;(res.data || []).forEach(item => {
        map[item.category] = Number(item.count) || 0
      })
      categoryCounts.value = map
      return map
    } catch (error) {
      // 切换页面时主动取消上一个请求会触发 CanceledError，属正常行为，静默处理
      if (isCancelError(error)) return {}
      console.error('拉取文档分类统计失败:', error)
      return {}
    }
  }

  /**
   * 获取指定分类的文档数量
   * @param {string} category 文档分类名
   * @returns {number} 该分类的文档数量（无则为 0）
   */
  function getCategoryCount(category) {
    return categoryCounts.value[category] || 0
  }

  /**
   * 拉取文档详情
   *
   * 副作用：更新 currentDocument。
   * @param {number|string} id 文档 ID
   * @returns {Promise<Object>} 文档详情数据
   */
  async function fetchDetail(id) {
    const res = await documentApi.getDetail(id)
    currentDocument.value = res.data
    return res.data
  }

  /**
   * 上传文档（multipart/form-data）
   *
   * 副作用：成功后弹出成功提示。
   * @param {FormData} formData 文件及元信息表单数据
   * @returns {Promise<Object>} 上传后的文档数据
   */
  async function addDocument(formData) {
    const res = await documentApi.upload(formData)
    feedback.success('文档上传成功')
    return res.data
  }

  /**
   * 更新文档
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 文档 ID
   * @param {Object} data 待更新字段
   * @returns {Promise<Object>} 更新后的文档数据
   */
  async function updateDocument(id, data) {
    const res = await documentApi.update(id, data)
    feedback.success('文档更新成功')
    return res.data
  }

  /**
   * 新增文档版本
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 文档 ID
   * @param {FormData} formData 新版本文件及元信息表单数据
   * @returns {Promise<Object>} 新增版本后的文档数据
   */
  async function addVersion(id, formData) {
    const res = await documentApi.addVersion(id, formData)
    feedback.success('版本新增成功')
    return res.data
  }

  /**
   * 删除文档
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 文档 ID
   * @returns {Promise<void>}
   */
  async function deleteDocument(id) {
    await documentApi.remove(id)
    feedback.success('文档已删除')
  }

  /**
   * 下载文档
   * @param {number|string} id 文档 ID
   * @returns {Promise<Object>} 完整响应对象（含 blob 数据与响应头）
   */
  async function downloadDocument(id) {
    const res = await documentApi.download(id)
    return res
  }

  /**
   * 预览文档
   * @param {number|string} id 文档 ID
   * @returns {Promise<any>} 预览所需的地址或内容数据
   */
  async function previewDocument(id) {
    const res = await documentApi.preview(id)
    return res.data
  }

  /**
   * 重置 store 状态到初始值
   * 修复 M21：登出时调用，避免下一个用户看到上一个用户缓存的文档列表/详情/分类计数
   * @returns {void}
   */
  function reset() {
    documents.value = []
    total.value = 0
    loading.value = false
    currentDocument.value = null
    categoryCounts.value = {}
  }

  return {
    documents,
    total,
    loading,
    currentDocument,
    categoryCounts,
    documentsByCategory,
    getDocumentsByProject,
    getDocumentById,
    getCategoryCount,
    fetchList,
    fetchCategoryStats,
    fetchDetail,
    addDocument,
    updateDocument,
    addVersion,
    deleteDocument,
    downloadDocument,
    previewDocument,
    reset
  }
})
