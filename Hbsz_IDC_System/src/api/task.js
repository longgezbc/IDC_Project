import request from '@/utils/request'
import { uploadFile as uploadFileHelper } from '@/utils/upload'

/**
 * 进度管理模块 API
 * 接口定义遵循 API_DOCS.md 第5章
 *
 * 提供项目任务的增删改查、进度填报、进度日志、项目整体进度
 * 以及关键节点（临期任务）提醒等接口。
 * @module api/task
 */
export const taskApi = {
  /**
   * 项目任务列表
   * @param {number|string} projectId 项目 ID
   * @param {Object} params 查询参数，如分页、状态等
   * @returns {Promise<any>} 该项目下的任务列表
   */
  getList(projectId, params) {
    return request({
      url: `/projects/${projectId}/tasks`,
      method: 'get',
      params
    })
  },

  /**
   * 任务详情
   * @param {number|string} id 任务 ID
   * @returns {Promise<any>} 指定任务的详细信息
   */
  getDetail(id) {
    return request({
      url: `/tasks/${id}`,
      method: 'get'
    })
  },

  /**
   * 新建任务（需指定项目）
   * @param {number|string} projectId 所属项目 ID
   * @param {Object} data 任务字段数据
   * @returns {Promise<any>} 新建后的任务信息
   */
  create(projectId, data) {
    return request({
      url: `/projects/${projectId}/tasks`,
      method: 'post',
      data
    })
  },

  /**
   * 更新任务
   * @param {number|string} id 任务 ID
   * @param {Object} data 待更新的任务字段
   * @returns {Promise<any>} 更新后的任务信息
   */
  update(id, data) {
    return request({
      url: `/tasks/${id}`,
      method: 'put',
      data
    })
  },

  /**
   * 删除任务
   * @param {number|string} id 任务 ID
   * @returns {Promise<any>} 删除操作结果
   */
  remove(id) {
    return request({
      url: `/tasks/${id}`,
      method: 'delete'
    })
  },

  /**
   * 提交进度填报（工程师发起，进入项目经理审核流程）
   * @param {number|string} id 任务 ID
   * @param {{ progress: number, remark?: string, images?: string[] }} data
   *        进度填报数据：进度百分比、备注、施工图片URL列表
   * @returns {Promise<any>} 进度日志 VO（含日志ID与审核状态）
   */
  reportProgress(id, data) {
    return request({
      url: `/tasks/${id}/progress`,
      method: 'put',
      data
    })
  },

  /**
   * 审核进度填报（项目经理审核工程师提交的进度日志）
   * @param {number|string} logId 进度日志 ID
   * @param {{ approve: boolean, remark?: string }} data 审核参数（是否通过、审核意见）
   * @returns {Promise<any>} 审核后的进度日志 VO
   */
  reviewProgress(logId, data) {
    return request({
      url: `/tasks/progress/${logId}/review`,
      method: 'put',
      data
    })
  },

  /**
   * 待审核进度日志列表
   * @param {Object} [params] 查询参数，可选 { projectId }
   * @returns {Promise<any>} 待审核进度日志列表
   */
  listPendingProgress(params = {}) {
    return request({
      url: '/tasks/progress/pending',
      method: 'get',
      params
    })
  },

  /**
   * 任务进度日志（含审核状态）
   * @param {number|string} id 任务 ID
   * @returns {Promise<any>} 该任务的历史进度填报日志列表
   */
  getProgressLogs(id) {
    return request({
      url: `/tasks/${id}/logs`,
      method: 'get'
    })
  },

  /**
   * 上传文件（用于进度填报施工图片等场景）
   * @param {File} file 文件对象
   * @returns {Promise<any>} 含 url 与 previewUrl 的对象
   */
  uploadFile(file) {
    return uploadFileHelper(file, '/files/upload')
  },

  /**
   * 项目整体进度
   * @param {number|string} projectId 项目 ID
   * @returns {Promise<any>} 项目聚合后的整体进度数据
   */
  getProjectProgress(projectId) {
    return request({
      url: `/projects/${projectId}/progress`,
      method: 'get'
    })
  },

  /**
   * 项目关键节点提醒
   * @param {number|string} projectId 项目 ID
   * @returns {Promise<any>} 该项目临近截止的任务列表
   */
  getUpcomingByProject(projectId) {
    return request({
      url: `/projects/${projectId}/tasks/upcoming`,
      method: 'get'
    })
  },

  /**
   * 全部关键节点提醒
   * @returns {Promise<any>} 跨项目的临近截止任务列表
   */
  getUpcomingAll() {
    return request({
      url: '/tasks/upcoming',
      method: 'get'
    })
  }
}
