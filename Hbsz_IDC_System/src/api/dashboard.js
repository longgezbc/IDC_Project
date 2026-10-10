import request from '@/utils/request'

/**
 * 工作台模块 API
 *
 * 提供工作台汇总统计、我的任务、我的填报等接口。
 * @module api/dashboard
 */
export const dashboardApi = {
  /**
   * 获取工作台汇总统计
   * @returns {Promise<any>} 汇总数据（任务/填报/审核/通知统计）
   */
  getSummary() {
    return request({
      url: '/dashboard/summary',
      method: 'get',
      skipCancel: true
    })
  },

  /**
   * 我的任务列表（分页）
   * @param {Object} params 查询参数，如 { pageNum, pageSize, status }
   * @returns {Promise<any>} 任务分页列表
   */
  getMyTasks(params) {
    return request({
      url: '/dashboard/my-tasks',
      method: 'get',
      params
    })
  },

  /**
   * 我的填报列表（分页）
   * @param {Object} params 查询参数，如 { pageNum, pageSize, reviewStatus }
   * @returns {Promise<any>} 进度日志分页列表
   */
  getMySubmissions(params) {
    return request({
      url: '/dashboard/my-submissions',
      method: 'get',
      params
    })
  },

  /**
   * PM 风险预警看板
   * 聚合 PM 负责项目的风险信息：逾期任务、即将到期任务、待审核填报数、各项目风险等级
   * 仅 PM / admin 角色可访问
   * @returns {Promise<any>} 风险预警看板 VO
   */
  getRiskBoard() {
    return request({
      url: '/dashboard/risk-board',
      method: 'get',
      skipCancel: true
    })
  }
}
