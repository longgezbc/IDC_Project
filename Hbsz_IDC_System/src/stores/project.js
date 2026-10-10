/**
 * 项目状态仓库（Pinia setup store）
 *
 * 缓存项目列表与分页总数，维护当前查看的项目详情，并派生进行中/已完成/已归档
 * 等分组视图及合同总额、平均进度等统计。动作方法封装列表/详情拉取与增删改归档，
 * 写操作成功后统一弹出成功提示。
 * @module stores/project
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { projectApi } from '@/api/project'
import { feedback } from '@/utils/feedback'
import { isCancelError } from '@/utils/cancelToken'

export const useProjectStore = defineStore('project', () => {
  // ===== state =====
  /** @type {import('vue').Ref<Array>} 当前列表页的项目集合 */
  const projects = ref([])
  /** @type {import('vue').Ref<number>} 列表总记录数（用于分页） */
  const total = ref(0)
  /** @type {import('vue').Ref<boolean>} 列表加载中标记 */
  const loading = ref(false)
  /** @type {import('vue').Ref<Object|null>} 当前查看的项目详情 */
  const currentProject = ref(null)

  // ===== getters =====
  /** 进行中且未归档的项目 */
  const activeProjects = computed(() => projects.value.filter(p => !p.archived && p.status === '进行中'))
  /** 已完成的项目 */
  const completedProjects = computed(() => projects.value.filter(p => p.status === '已完成'))
  /** 已归档的项目 */
  const archivedProjects = computed(() => projects.value.filter(p => p.archived))

  /** 所有项目合同金额之和 */
  const totalContractAmount = computed(() =>
    projects.value.reduce((sum, p) => sum + (Number(p.contractAmount) || 0), 0)
  )

  /** 进行中项目的平均进度（四舍五入取整；无进行中项目时为 0） */
  const avgProgress = computed(() => {
    const active = projects.value.filter(p => p.status === '进行中')
    if (active.length === 0) return 0
    return Math.round(active.reduce((sum, p) => sum + (Number(p.progress) || 0), 0) / active.length)
  })

  /**
   * 从已加载列表中按 ID 查找项目（本地查找，不发请求）
   * @param {number|string} id 项目 ID
   * @returns {Object|undefined} 匹配的项目，未找到返回 undefined
   */
  function getProjectById(id) {
    return projects.value.find(p => String(p.id) === String(id))
  }

  /** fetchList 竞态保护序列号：快速翻页/搜索时丢弃乱序返回的过期响应 */
  let fetchListSeq = 0

  /**
   * 拉取项目列表
   *
   * 竞态保护（P2 修复）：先发的慢请求后返回会覆盖新数据，序列号机制确保仅最新请求的结果生效。
   * 副作用：更新 projects、total 与 loading；兼容后端 records/list 两种字段。
   * @param {Object} [params={}] 分页与筛选参数
   * @returns {Promise<Object>} 列表响应数据
   */
  async function fetchList(params = {}) {
    const currentSeq = ++fetchListSeq
    loading.value = true
    try {
      const res = await projectApi.getList(params)
      if (currentSeq !== fetchListSeq) return res.data
      projects.value = res.data.records || res.data.list || []
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
   * 拉取项目详情
   *
   * 副作用：更新 currentProject。
   * @param {number|string} id 项目 ID
   * @returns {Promise<Object>} 项目详情数据
   */
  async function fetchDetail(id) {
    const res = await projectApi.getDetail(id)
    currentProject.value = res.data
    return res.data
  }

  /**
   * 新建项目
   *
   * 副作用：成功后弹出成功提示。
   * @param {Object} data 项目字段数据
   * @returns {Promise<Object>} 新建后的项目数据
   */
  async function addProject(data) {
    const res = await projectApi.create(data)
    feedback.success('项目创建成功')
    return res.data
  }

  /**
   * 更新项目
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 项目 ID
   * @param {Object} data 待更新的项目字段
   * @returns {Promise<Object>} 更新后的项目数据
   */
  async function updateProject(id, data) {
    const res = await projectApi.update(id, data)
    feedback.success('项目更新成功')
    return res.data
  }

  /**
   * 归档项目
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 项目 ID
   * @returns {Promise<Object>} 归档操作结果数据
   */
  async function archiveProject(id) {
    const res = await projectApi.archive(id)
    feedback.success('项目已归档')
    return res.data
  }

  /**
   * 取消归档
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 项目 ID
   * @returns {Promise<Object>} 取消归档操作结果数据
   */
  async function unarchiveProject(id) {
    const res = await projectApi.unarchive(id)
    feedback.success('已取消归档')
    return res.data
  }

  /**
   * 删除项目
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 项目 ID
   * @returns {Promise<void>}
   */
  async function deleteProject(id) {
    await projectApi.remove(id)
    feedback.success('项目已删除')
  }

  /**
   * 重置 store 状态到初始值
   * 修复 M21：登出时调用，避免下一个用户看到上一个用户缓存的项目列表/详情
   * @returns {void}
   */
  function reset() {
    projects.value = []
    total.value = 0
    loading.value = false
    currentProject.value = null
  }

  return {
    projects,
    total,
    loading,
    currentProject,
    activeProjects,
    completedProjects,
    archivedProjects,
    totalContractAmount,
    avgProgress,
    getProjectById,
    fetchList,
    fetchDetail,
    addProject,
    updateProject,
    archiveProject,
    unarchiveProject,
    deleteProject,
    reset
  }
})
