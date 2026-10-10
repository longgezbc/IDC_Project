/**
 * 项目授权申请管理 API 模块
 *
 * 对应后端 ProjectAuthController（@RequestMapping("/project-auth")）。
 * 阶段3：项目授权规范化（申请-审批-撤销-延期）。
 * 注意：与旧版 src/api/projectAuthorization.js（PM 直接授权工程师）是两条不同的业务线，
 *       本模块处理的是「申请-审批」流程。
 * 修复 L10：原导出名 projectAuthApi 与 projectAuthorization.js 同名，存在两个文件
 *       导出同名常量，import 时易错引到另一文件导致调用错误接口。改名为
 *       projectAuthRequestApi 以区分「申请-审批」与「直接授权」两条业务线。
 * @module api/projectAuth
 */
import request from '@/utils/request'

export const projectAuthRequestApi = {
  /* ====== 授权申请 ====== */

  /** 创建项目授权申请 */
  createAuthRequest(data) {
    return request({ url: '/project-auth/requests', method: 'post', data })
  },
  /** 分页查询项目授权申请列表 */
  listAuthRequests(params) {
    return request({ url: '/project-auth/requests', method: 'get', params })
  },
  /** 审批授权申请（approve=true 通过 / approve=false 驳回） */
  approveAuthRequest(id, data) {
    return request({ url: `/project-auth/requests/${id}/approve`, method: 'post', data })
  },
  /** 驳回授权申请 */
  rejectAuthRequest(id, data) {
    return request({ url: `/project-auth/requests/${id}/reject`, method: 'post', data })
  },

  /* ====== 授权关系管理 ====== */

  /** 分页查询项目授权关系列表 */
  listAssignments(params) {
    return request({ url: '/project-auth/assignments', method: 'get', params })
  },
  /** 撤销授权（reason 为撤销原因） */
  revokeAssignment(id, reason) {
    return request({ url: `/project-auth/assignments/${id}`, method: 'delete', params: { reason } })
  },
  /** 延期授权（newExpireAt 格式 yyyy-MM-ddTHH:mm:ss） */
  extendAssignment(id, newExpireAt) {
    return request({ url: `/project-auth/assignments/${id}/extend`, method: 'put', params: { newExpireAt } })
  }
}
