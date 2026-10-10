import request from '@/utils/request'

/**
 * 日报导出模块 API
 *
 * 提供日报数据预览、报表导出（固定 Excel）、导出历史查询、文件下载与历史删除等接口。
 * 下载接口以 blob 形式接收二进制文件流；其余接口走标准 JSON 业务响应。
 * @module api/dailyReport
 */
export const dailyReportApi = {
  /**
   * 预览日报数据
   * 根据起止日期拉取甘特图进度日报数据，包括概览指标、项目进度总览、任务进度明细。
   * 支持按项目筛选，未传 projectId 时查询全部未归档项目。
   * @param {Object} params 查询参数
   * @param {string} params.startDate 起始日期，格式 YYYY-MM-DD
   * @param {string} params.endDate 截止日期，格式 YYYY-MM-DD
   * @param {number} [params.projectId] 项目ID（可选，未传则查询全部项目）
   * @returns {Promise<any>} 日报预览数据（reportTitle/summary/projectSummary/taskProgress/projectId/projectName）
   */
  preview(params) {
    return request({
      url: '/daily-report/preview',
      method: 'get',
      params
    })
  },

  /**
   * 导出日报报表
   * 触发后端生成 Excel 报表文件（每个项目独占一个 Sheet），并写入导出历史记录。
   * 支持按项目筛选，未传 projectId 时导出全部未归档项目。
   * @param {Object} params 导出参数
   * @param {string} params.startDate 起始日期，格式 YYYY-MM-DD
   * @param {string} params.endDate 截止日期，格式 YYYY-MM-DD
   * @param {number} [params.projectId] 项目ID（可选，未传则导出全部项目）
   * @returns {Promise<any>} 导出记录信息（id/fileName/fileSize/format/status/createdAt）
   */
  export(params) {
    return request({
      url: '/daily-report/export',
      method: 'post',
      params
    })
  },

  /**
   * 查询导出历史
   * 分页返回日报导出历史记录列表。
   * 注意：该接口后端 Controller 绑定的参数名为 page/size（与 project/document 等模块的 pageNum/pageSize 不同），
   * 此处做一次参数名映射，调用方仍统一使用 pageNum/pageSize。
   * @param {Object} params 分页参数
   * @param {number} params.pageNum 页码（从 1 开始）
   * @param {number} params.pageSize 每页条数
   * @returns {Promise<any>} 分页结果（records/total/page/size）
   */
  getHistory(params) {
    const { pageNum, pageSize, ...rest } = params || {}
    return request({
      url: '/daily-report/history',
      method: 'get',
      params: {
        ...rest,
        page: pageNum,
        size: pageSize
      }
    })
  },

  /**
   * 下载导出的报表文件
   * 以二进制流形式下载指定 ID 的报表文件，前端用 URL.createObjectURL 触发浏览器下载。
   * @param {number|string} id 导出记录 ID
   * @returns {Promise<any>} axios 响应对象，data 为 Blob 文件流
   */
  download(id) {
    return request({
      url: `/daily-report/download/${id}`,
      method: 'get',
      responseType: 'blob'
    })
  },

  /**
   * 删除导出历史记录
   * @param {number|string} id 导出记录 ID
   * @returns {Promise<any>} 删除结果
   */
  remove(id) {
    return request({
      url: `/daily-report/history/${id}`,
      method: 'delete'
    })
  }
}
