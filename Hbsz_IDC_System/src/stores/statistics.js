/**
 * 统计分析状态仓库（Pinia setup store）
 *
 * 缓存综合概览、状态/类型/优先级分布、趋势与金额统计等结果，供统计页复用。
 * 提供各维度独立拉取动作，以及一次性并行加载全部统计的聚合动作。
 * @module stores/statistics
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { statisticsApi } from '@/api/statistics'
import { feedback } from '@/utils/feedback'
import { isCancelError } from '@/utils/cancelToken'

export const useStatisticsStore = defineStore('statistics', () => {
  // ===== state =====
  /** @type {import('vue').Ref<Object|null>} 综合统计概览 */
  const overview = ref(null)
  /** @type {import('vue').Ref<Array>} 项目状态分布 */
  const statusDistribution = ref([])
  /** @type {import('vue').Ref<Array>} 项目类型分布 */
  const typeDistribution = ref([])
  /** @type {import('vue').Ref<Array>} 项目优先级分布 */
  const priorityDistribution = ref([])
  /** @type {import('vue').Ref<Array>} 项目趋势数据 */
  const projectTrend = ref([])
  /** @type {import('vue').Ref<Object|null>} 金额统计 */
  const amountStatistics = ref(null)
  /** @type {import('vue').Ref<boolean>} 批量加载中标记 */
  const loading = ref(false)

  /**
   * 综合统计概览
   *
   * 副作用：更新 overview。
   * @returns {Promise<Object>} 概览数据
   */
  async function fetchOverview() {
    const res = await statisticsApi.getOverview()
    overview.value = res.data
    return res.data
  }

  /**
   * 项目状态分布
   *
   * 副作用：更新 statusDistribution。
   * @param {Object} [params={}] 筛选参数
   * @returns {Promise<Array>} 状态分布数据
   */
  async function fetchStatusDistribution(params = {}) {
    const res = await statisticsApi.getStatusDistribution(params)
    statusDistribution.value = res.data || []
    return res.data
  }

  /**
   * 项目类型分布
   *
   * 副作用：更新 typeDistribution。
   * @param {Object} [params={}] 筛选参数
   * @returns {Promise<Array>} 类型分布数据
   */
  async function fetchTypeDistribution(params = {}) {
    const res = await statisticsApi.getTypeDistribution(params)
    typeDistribution.value = res.data || []
    return res.data
  }

  /**
   * 项目优先级分布
   *
   * 副作用：更新 priorityDistribution。
   * @param {Object} [params={}] 筛选参数
   * @returns {Promise<Array>} 优先级分布数据
   */
  async function fetchPriorityDistribution(params = {}) {
    const res = await statisticsApi.getPriorityDistribution(params)
    priorityDistribution.value = res.data || []
    return res.data
  }

  /**
   * 项目趋势统计
   *
   * 副作用：更新 projectTrend。
   * @param {Object} [params={}] 筛选参数
   * @returns {Promise<Array>} 趋势数据
   */
  async function fetchProjectTrend(params = {}) {
    const res = await statisticsApi.getProjectTrend(params)
    projectTrend.value = res.data || []
    return res.data
  }

  /**
   * 金额统计
   *
   * 副作用：更新 amountStatistics。
   * @param {Object} [params={}] 筛选参数
   * @returns {Promise<Object>} 金额统计数据
   */
  async function fetchAmountStatistics(params = {}) {
    const res = await statisticsApi.getAmountStatistics(params)
    amountStatistics.value = res.data
    return res.data
  }

  /**
   * 导出报表
   * @param {Object} [params={}] 导出筛选参数
   * @returns {Promise<Object>} 完整响应对象（含 blob 数据）
   */
  async function exportReport(params = {}) {
    const res = await statisticsApi.exportReport(params)
    return res
  }

  /**
   * 批量加载所有统计数据
   *
   * P2 修复：
   *   1. Promise.all → allSettled：六个维度中任一接口失败会导致整体 reject，
   *      loading 虽能复位但所有维度 state 均不更新，页面整片空白；
   *      改用 allSettled 后失败维度保留旧值/为空，其余维度正常渲染，
   *      并对失败情况统一提示（与项目"并发请求用 allSettled"的约定一致）。
   *   2. 增加 loading 去重：快速重复调用（如切换时间范围连点）不再并发
   *      发起 12 个请求导致结果交错覆盖。
   * @param {string} [dateRange='all'] 时间范围筛选值
   * @returns {Promise<void>}
   */
  async function fetchAllStatistics(dateRange = 'all') {
    if (loading.value) return
    loading.value = true
    try {
      const params = { dateRange }
      const results = await Promise.allSettled([
        fetchOverview(),
        fetchStatusDistribution(params),
        fetchTypeDistribution(params),
        fetchPriorityDistribution(params),
        fetchProjectTrend(params),
        fetchAmountStatistics(params)
      ])
      // 排除被取消的请求（路由切换/登出时 cancelAllPending 触发），避免误报
      const failed = results.filter(r => r.status === 'rejected' && !isCancelError(r.reason))
      if (failed.length > 0) {
        feedback.warning('部分统计数据加载失败，请刷新重试')
      }
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置 store 状态到初始值
   * 修复 M21：登出时调用，避免下一个用户看到上一个用户缓存的统计数据
   * @returns {void}
   */
  function reset() {
    overview.value = null
    statusDistribution.value = []
    typeDistribution.value = []
    priorityDistribution.value = []
    projectTrend.value = []
    amountStatistics.value = null
    loading.value = false
  }

  return {
    overview,
    statusDistribution,
    typeDistribution,
    priorityDistribution,
    projectTrend,
    amountStatistics,
    loading,
    fetchOverview,
    fetchStatusDistribution,
    fetchTypeDistribution,
    fetchPriorityDistribution,
    fetchProjectTrend,
    fetchAmountStatistics,
    exportReport,
    fetchAllStatistics,
    reset
  }
})
