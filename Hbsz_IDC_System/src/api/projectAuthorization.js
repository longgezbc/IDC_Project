/**
 * 项目授权与转移 API
 *
 * 提供 PM 转移项目授权、授权/撤销工程师、查询项目授权工程师列表、
 * 活跃工程师下拉、转移审计日志查询等接口。
 */
import request from '@/utils/request'

export const projectAuthApi = {
  /**
   * 项目转移 / 负责人移交（toManagerId 与 toEngineerId 至少传一项）
   * @param {number} projectId 项目ID
   * @param {Object} data { toManagerId?, toEngineerId?, remark }
   *   - toManagerId：移交项目所有权与全部审批责任（manager_id 变更，审批待办实时流向新负责人）
   *   - toEngineerId：撤销当前所有工程师授权，仅授权给目标工程师
   */
  transfer(projectId, data) {
    return request({
      url: `/projects/${projectId}/authorization/transfer`,
      method: 'post',
      data,
      skipCancel: true
    })
  },

  /**
   * 项目授权：为项目新增一名工程师授权
   * @param {number} projectId 项目ID
   * @param {Object} data { toEngineerId, remark }
   */
  assign(projectId, data) {
    return request({
      url: `/projects/${projectId}/authorization`,
      method: 'post',
      data,
      skipCancel: true
    })
  },

  /**
   * 撤销授权：移除项目对指定工程师的授权
   * @param {number} projectId 项目ID
   * @param {number} engineerId 工程师ID
   * @param {string} [remark] 撤销备注
   */
  revoke(projectId, engineerId, remark) {
    return request({
      url: `/projects/${projectId}/authorization/${engineerId}`,
      method: 'delete',
      params: { remark },
      skipCancel: true
    })
  },

  /**
   * 查询项目下所有有效授权工程师
   * @param {number} projectId 项目ID
   */
  listProjectEngineers(projectId) {
    return request({
      url: `/projects/${projectId}/authorization`,
      method: 'get'
    })
  },

  /**
   * 查询所有启用状态的工程师（转移/授权下拉用）
   */
  listActiveEngineers() {
    return request({
      url: '/projects/authorization/engineers',
      method: 'get',
      skipCancel: true
    })
  },

  /**
   * 查询所有启用状态的项目经理（负责人移交下拉用）
   */
  listActiveManagers() {
    return request({
      url: '/projects/authorization/managers',
      method: 'get',
      skipCancel: true
    })
  },

  /**
   * 分页查询转移审计日志
   * @param {Object} params { pageNum, pageSize, projectId, operatedBy, actionType, dateBegin, dateEnd }
   */
  transferLogs(params) {
    return request({
      url: '/projects/authorization/logs',
      method: 'get',
      params
    })
  }
}
