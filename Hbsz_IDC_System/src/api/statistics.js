import request from '@/utils/request'

/**
 * 统计分析模块 API
 *
 * 提供综合概览、项目状态/类型/优先级分布、趋势、金额统计以及报表导出等接口。
 * 导出接口以 blob 形式接收二进制文件流。
 * @module api/statistics
 */
export const statisticsApi = {
  /**
   * 综合统计概览
   * @returns {Promise<any>} 首页/统计页所需的综合概览数据
   */
  getOverview() {
    return request({
      url: '/statistics/overview',
      method: 'get'
    })
  },

  /**
   * 项目状态分布
   * @param {Object} params 筛选参数，如时间范围、项目类型等
   * @returns {Promise<any>} 按状态聚合的项目分布数据
   */
  getStatusDistribution(params) {
    return request({
      url: '/statistics/project-status',
      method: 'get',
      params
    })
  },

  /**
   * 项目类型分布
   * @param {Object} params 筛选参数
   * @returns {Promise<any>} 按类型聚合的项目分布数据
   */
  getTypeDistribution(params) {
    return request({
      url: '/statistics/project-type',
      method: 'get',
      params
    })
  },

  /**
   * 项目优先级分布
   * @param {Object} params 筛选参数
   * @returns {Promise<any>} 按优先级聚合的项目分布数据
   */
  getPriorityDistribution(params) {
    return request({
      url: '/statistics/project-priority',
      method: 'get',
      params
    })
  },

  /**
   * 项目趋势统计
   * @param {Object} params 筛选参数，如时间粒度、区间等
   * @returns {Promise<any>} 项目随时间变化的趋势数据
   */
  getProjectTrend(params) {
    return request({
      url: '/statistics/project-trend',
      method: 'get',
      params
    })
  },

  /**
   * 金额统计
   * @param {Object} params 筛选参数
   * @returns {Promise<any>} 项目金额相关的聚合统计数据
   */
  getAmountStatistics(params) {
    return request({
      url: '/statistics/amount',
      method: 'get',
      params
    })
  },

  /**
   * 导出报表
   * @param {Object} params 导出筛选参数
   * @returns {Promise<Blob>} 报表文件的二进制流（responseType: blob）
   */
  exportReport(params) {
    return request({
      url: '/statistics/export',
      method: 'get',
      params,
      responseType: 'blob'
    })
  }
}
