/**
 * 进度/任务状态仓库（Pinia setup store）
 *
 * 维护任务列表、当前任务、进度日志，并缓存后端返回的关键节点提醒与项目整体进度。
 * 关键节点与整体进度优先使用后端数据，缺失时提供基于本地任务列表的前端兜底计算，
 * 兜底逻辑使用 dayjs 判断任务处于未来一周内或已逾期未完成。
 * @module stores/progress
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { taskApi } from '@/api/task'
import { feedback } from '@/utils/feedback'
import dayjs from 'dayjs'
import { isCancelError } from '@/utils/cancelToken'

export const useProgressStore = defineStore('progress', () => {
  // ===== state =====
  /** @type {import('vue').Ref<Array>} 当前项目的任务列表 */
  const tasks = ref([])
  /** @type {import('vue').Ref<number>} 任务总数 */
  const total = ref(0)
  /** @type {import('vue').Ref<boolean>} 列表加载中标记 */
  const loading = ref(false)
  /** @type {import('vue').Ref<Object|null>} 当前查看的任务详情 */
  const currentTask = ref(null)
  /** @type {import('vue').Ref<Array>} 当前任务的进度填报日志 */
  const progressLogs = ref([])
  // 缓存全部关键节点提醒（来自后端）
  /** @type {import('vue').Ref<Array>} 跨项目关键节点提醒缓存 */
  const allUpcomingKeyTasks = ref([])
  // 缓存项目维度的关键节点提醒
  /** @type {import('vue').Ref<Object<string, Array>>} 项目 ID 到其关键节点提醒的映射 */
  const projectUpcomingKeyTasks = ref({})
  // 缓存项目整体进度（来自后端）
  /** @type {import('vue').Ref<Object<string, Object>>} 项目 ID 到其整体进度数据的映射 */
  const projectProgressMap = ref({})

  // ===== getters =====
  /**
   * 按项目 ID 过滤已加载任务
   * @param {number|string} projectId 项目 ID
   * @returns {Array} 该项目的任务列表
   */
  function getProjectTasks(projectId) {
    return tasks.value.filter(t => String(t.projectId) === String(projectId))
  }

  /**
   * 获取指定项目的全部任务（getProjectTasks 的语义别名）
   * @param {number|string} projectId 项目 ID
   * @returns {Array} 该项目的任务列表
   */
  function getAllTasks(projectId) {
    return getProjectTasks(projectId)
  }

  /**
   * 按 ID 查找任务
   * @param {number|string} id 任务 ID
   * @returns {Object|undefined} 匹配的任务，未找到返回 undefined
   */
  function getTaskById(id) {
    return tasks.value.find(t => String(t.id) === String(id))
  }

  /** fetchList 竞态保护序列号（P2 修复）：快速切换项目/筛选时丢弃乱序返回的过期响应 */
  let fetchListSeq = 0

  /**
   * 拉取任务列表（按项目查询）
   *
   * 竞态保护（P2 修复）：先发的慢请求后返回会覆盖新数据，序列号机制确保仅最新请求的结果生效。
   * 副作用：更新 tasks、total 与 loading。未提供 projectId 时清空列表并返回空结果。
   * @param {Object} [params={}] 查询参数，须含 projectId
   * @returns {Promise<Array|Object>} 任务列表（无 projectId 时返回空结果对象）
   */
  async function fetchList(params = {}) {
    const currentSeq = ++fetchListSeq
    loading.value = true
    try {
      const projectId = params.projectId
      if (!projectId) {
        if (currentSeq !== fetchListSeq) return { records: [], total: 0 }
        tasks.value = []
        total.value = 0
        return { records: [], total: 0 }
      }
      const res = await taskApi.getList(projectId, params)
      if (currentSeq !== fetchListSeq) return []
      // 修复 M22：原代码 const list = res.data || [] 假设 res.data 为数组，
      // 但后端可能返回 { records, total } 分页对象，此时 list 会变成对象导致后续渲染异常。
      // 改为兼容数组与分页对象两种格式，与其他 store 写法保持一致。
      const data = res.data
      const list = Array.isArray(data) ? data : (data?.records || [])
      tasks.value = list
      total.value = Array.isArray(data) ? list.length : (data?.total || 0)
      return list
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
   * 拉取任务详情
   *
   * 副作用：更新 currentTask。
   * @param {number|string} id 任务 ID
   * @returns {Promise<Object>} 任务详情数据
   */
  async function fetchDetail(id) {
    const res = await taskApi.getDetail(id)
    currentTask.value = res.data
    return res.data
  }

  /**
   * 新建任务
   *
   * 副作用：成功后弹出成功提示。
   * @param {{ projectId: number|string, [key: string]: any }} data 任务数据，须含 projectId
   * @returns {Promise<Object>} 新建后的任务数据
   * @throws {Error} 缺少 projectId 时抛出"缺少项目ID"
   */
  async function addTask(data) {
    const projectId = data.projectId
    if (!projectId) throw new Error('缺少项目ID')
    const res = await taskApi.create(projectId, data)
    feedback.success('任务创建成功')
    return res.data
  }

  /**
   * 更新任务
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 任务 ID
   * @param {Object} data 待更新字段
   * @returns {Promise<Object>} 更新后的任务数据
   */
  async function updateTask(id, data) {
    const res = await taskApi.update(id, data)
    feedback.success('任务更新成功')
    return res.data
  }

  /**
   * 删除任务
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 任务 ID
   * @returns {Promise<void>}
   */
  async function deleteTask(id) {
    await taskApi.remove(id)
    feedback.success('任务已删除')
  }

  /**
   * 提交进度填报（工程师发起，进入项目经理审核流程）
   *
   * 副作用：成功后弹出提示。
   * @param {number|string} id 任务 ID
   * @param {{ progress: number, remark?: string, images?: string[] }} data
   *        进度填报数据：进度百分比、备注、施工图片URL列表
   * @returns {Promise<Object>} 进度日志 VO（含日志ID与审核状态）
   */
  async function reportProgress(id, data) {
    const res = await taskApi.reportProgress(id, data)
    feedback.success('进度填报已提交，等待项目经理审核')
    return res.data
  }

  /**
   * 审核进度填报（项目经理审核工程师提交的进度日志）
   *
   * 副作用：成功后弹出提示。
   * @param {number|string} logId 进度日志 ID
   * @param {{ approve: boolean, remark?: string }} data 审核参数
   * @returns {Promise<Object>} 审核后的进度日志 VO
   */
  async function reviewProgress(logId, data) {
    const res = await taskApi.reviewProgress(logId, data)
    feedback.success(data.approve ? '已通过审核' : '已驳回审核')
    return res.data
  }

  /**
   * 拉取待审核进度日志列表
   *
   * 副作用：更新 pendingLogs。
   * @param {number|string} [projectId] 项目 ID，可选
   * @returns {Promise<Array>} 待审核进度日志列表
   */
  const pendingLogs = ref([])
  async function fetchPendingLogs(projectId) {
    const params = projectId ? { projectId } : {}
    const res = await taskApi.listPendingProgress(params)
    pendingLogs.value = res.data || []
    return pendingLogs.value
  }

  /**
   * 获取任务进度日志（含审核状态）
   *
   * 副作用：更新 progressLogs。
   * @param {number|string} id 任务 ID
   * @returns {Promise<Array>} 进度日志列表
   */
  async function fetchProgressLogs(id) {
    const res = await taskApi.getProgressLogs(id)
    progressLogs.value = res.data || []
    return res.data
  }

  /**
   * 上传文件（用于进度填报施工图片等场景）
   * @param {File} file 文件对象
   * @returns {Promise<{ url: string, previewUrl: string }>} 含 url 与 previewUrl 的对象
   */
  async function uploadFile(file) {
    const res = await taskApi.uploadFile(file)
    return res.data
  }

  /**
   * 拉取全部项目的关键节点提醒（来自后端 /tasks/upcoming）
   * 替代原先的客户端过滤逻辑，减少数据传输和前端计算
   *
   * 副作用：更新 allUpcomingKeyTasks；异常时置空并返回空数组。
   * @returns {Promise<Array>} 关键节点提醒列表
   */
  async function fetchAllUpcomingKeyTasks() {
    try {
      const res = await taskApi.getUpcomingAll()
      allUpcomingKeyTasks.value = res.data || []
      return allUpcomingKeyTasks.value
    } catch (error) {
      console.error('拉取全部关键节点提醒失败:', error)
      allUpcomingKeyTasks.value = []
      return []
    }
  }

  /**
   * 拉取指定项目的关键节点提醒（来自后端 /projects/{projectId}/tasks/upcoming）
   *
   * 副作用：更新 projectUpcomingKeyTasks 中对应项目的缓存；异常时返回空数组。
   * @param {number|string} projectId 项目 ID
   * @returns {Promise<Array>} 该项目的关键节点提醒列表
   */
  async function fetchUpcomingKeyTasksByProject(projectId) {
    if (!projectId) return []
    try {
      const res = await taskApi.getUpcomingByProject(projectId)
      const list = res.data || []
      projectUpcomingKeyTasks.value[String(projectId)] = list
      return list
    } catch (error) {
      console.error('拉取项目关键节点提醒失败:', error)
      return []
    }
  }

  /**
   * 拉取项目整体进度（来自后端 /projects/{projectId}/progress）
   * 替代原先的客户端 calcProjectProgress 计算
   *
   * 副作用：更新 projectProgressMap 中对应项目的缓存；异常时返回 null。
   * @param {number|string} projectId 项目 ID
   * @returns {Promise<Object|null>} 项目整体进度数据
   */
  async function fetchProjectProgress(projectId) {
    if (!projectId) return null
    try {
      const res = await taskApi.getProjectProgress(projectId)
      const data = res.data
      projectProgressMap.value[String(projectId)] = data
      return data
    } catch (error) {
      console.error('拉取项目整体进度失败:', error)
      return null
    }
  }

  /**
   * 计算项目整体进度（前端兜底计算，当后端数据未加载时使用）
   *
   * 优先返回后端缓存的 overallProgress；否则对本地任务进度取平均并四舍五入。
   * @param {number|string} projectId 项目 ID
   * @returns {number} 整体进度百分比（0-100）
   */
  function calcProjectProgress(projectId) {
    // 优先使用后端返回的项目整体进度
    const cached = projectProgressMap.value[String(projectId)]
    if (cached && typeof cached.overallProgress === 'number') {
      return cached.overallProgress
    }
    // 兜底：前端基于任务列表计算
    const list = getProjectTasks(projectId)
    if (list.length === 0) return 0
    const totalProgress = list.reduce((sum, t) => sum + (Number(t.progress) || 0), 0)
    return Math.round(totalProgress / list.length)
  }

  /**
   * 获取即将到来的关键节点（项目维度，优先使用后端数据）
   *
   * 优先返回后端缓存；缺失时基于本地任务过滤出未来 7 天内开始/结束或已逾期未完成的关键任务。
   * @param {number|string} projectId 项目 ID
   * @returns {Array} 关键节点任务列表
   */
  function getUpcomingKeyTasks(projectId) {
    const cached = projectUpcomingKeyTasks.value[String(projectId)]
    if (cached) return cached
    // 兜底：前端基于已加载任务列表过滤
    const list = getProjectTasks(projectId)
    const today = dayjs()
    const nextWeek = today.add(7, 'day')
    return list.filter(t => {
      if (!t.isKey) return false
      const start = dayjs(t.startDate)
      const end = dayjs(t.endDate)
      return (start.isAfter(today) && start.isBefore(nextWeek)) ||
             (end.isAfter(today) && end.isBefore(nextWeek)) ||
             (t.status !== '已完成' && end.isBefore(today))
    })
  }

  /**
   * 获取所有项目的关键节点提醒（优先使用后端数据）
   *
   * 后端缓存非空时直接返回；否则基于全部本地任务应用与项目维度一致的时间窗口过滤。
   * @returns {Array} 跨项目关键节点任务列表
   */
  function getAllUpcomingKeyTasks() {
    if (allUpcomingKeyTasks.value.length > 0) {
      return allUpcomingKeyTasks.value
    }
    // 兜底：前端基于已加载任务列表过滤
    return tasks.value.filter(t => {
      if (!t.isKey) return false
      const today = dayjs()
      const nextWeek = today.add(7, 'day')
      const start = dayjs(t.startDate)
      const end = dayjs(t.endDate)
      return (start.isAfter(today) && start.isBefore(nextWeek)) ||
             (end.isAfter(today) && end.isBefore(nextWeek)) ||
             (t.status !== '已完成' && end.isBefore(today))
    })
  }

  /**
   * 设置任务列表（action）
   *
   * 修复 M17: 原先组件外部直接赋值 progressStore.tasks 破坏了 store 封装。
   * 提供统一的 setTasks action，便于后续扩展（如校验、派发事件等）。
   * @param {Array} list 任务列表
   */
  function setTasks(list) {
    tasks.value = Array.isArray(list) ? list : []
    total.value = tasks.value.length
  }

  /**
   * 重置 store 状态到初始值
   * 修复 M21：登出时调用，避免下一个用户看到上一个用户缓存的任务列表/进度日志/关键节点提醒
   * @returns {void}
   */
  function reset() {
    tasks.value = []
    total.value = 0
    loading.value = false
    currentTask.value = null
    progressLogs.value = []
    pendingLogs.value = []
    allUpcomingKeyTasks.value = []
    projectUpcomingKeyTasks.value = {}
    projectProgressMap.value = {}
  }

  return {
    tasks,
    total,
    loading,
    currentTask,
    progressLogs,
    pendingLogs,
    allUpcomingKeyTasks,
    projectUpcomingKeyTasks,
    projectProgressMap,
    getProjectTasks,
    getAllTasks,
    getTaskById,
    fetchList,
    fetchDetail,
    addTask,
    updateTask,
    deleteTask,
    reportProgress,
    reviewProgress,
    fetchPendingLogs,
    fetchProgressLogs,
    uploadFile,
    fetchAllUpcomingKeyTasks,
    fetchUpcomingKeyTasksByProject,
    fetchProjectProgress,
    calcProjectProgress,
    getUpcomingKeyTasks,
    getAllUpcomingKeyTasks,
    setTasks,
    reset
  }
})
