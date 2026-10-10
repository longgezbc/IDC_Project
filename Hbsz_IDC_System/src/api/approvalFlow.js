/**
 * 审批流程图模块 API
 *
 * 将后端业务审批数据（授权申请、进度审核、项目转移）转换为流程图组件
 * 所需的通用 {nodes, links} 数据结构。
 *
 * 后端接口：ApprovalFlowController（/approval-flow/*）
 * @module api/approvalFlow
 */
import request from '@/utils/request'

export const approvalFlowApi = {
  // ==================== 流程图详情 ====================

  /**
   * 获取项目授权申请的审批流程图数据
   * @param {number|string} id 授权申请ID（biz_project_auth_request.id）
   * @returns {Promise<Object>} 流程图 VO { flowType, bizId, projectId, projectName, title, nodes, links }
   */
  getAuthRequestFlow(id) {
    return request({ url: `/approval-flow/auth-request/${id}`, method: 'get' })
  },

  /**
   * 获取任务进度审核的审批流程图数据
   * @param {number|string} id 进度填报记录ID（biz_task_progress_log.id）
   * @returns {Promise<Object>} 流程图 VO
   */
  getProgressReviewFlow(id) {
    return request({ url: `/approval-flow/progress-review/${id}`, method: 'get' })
  },

  /**
   * 获取项目转移的审批流程图数据
   * @param {number|string} id 转移日志ID（biz_project_transfer_log.id）
   * @returns {Promise<Object>} 流程图 VO
   */
  getProjectTransferFlow(id) {
    return request({ url: `/approval-flow/project-transfer/${id}`, method: 'get' })
  },

  // ==================== 列表查询 ====================

  /**
   * 获取授权申请列表（供流程图列表页选择业务记录）
   * @returns {Promise<Array>} 授权申请简要列表
   */
  listAuthRequests() {
    return request({ url: '/approval-flow/auth-requests', method: 'get' })
  },

  /**
   * 获取进度填报列表（供流程图列表页选择业务记录）
   * @returns {Promise<Array>} 进度填报简要列表
   */
  listProgressReviews() {
    return request({ url: '/approval-flow/progress-reviews', method: 'get' })
  },

  /**
   * 获取项目转移日志列表（供流程图列表页选择业务记录）
   * @returns {Promise<Array>} 转移日志简要列表
   */
  listProjectTransfers() {
    return request({ url: '/approval-flow/project-transfers', method: 'get' })
  }
}
