/**
 * 项目组管理 API 模块
 *
 * 对应后端 ProjectTeamController（@RequestMapping("/project-teams")）。
 * 阶段4：项目组跨部门协作（添加/移除成员、转让组长、查询我参与的项目组）。
 * @module api/projectTeam
 */
import request from '@/utils/request'

export const projectTeamApi = {
  /** 添加项目组成员 */
  addMember(data) {
    return request({ url: '/project-teams/members', method: 'post', data })
  },
  /** 分页查询项目组成员列表 */
  listMembers(params) {
    return request({ url: '/project-teams/members', method: 'get', params })
  },
  /** 移除项目组成员（reason 为移除原因） */
  removeMember(id, reason) {
    return request({ url: `/project-teams/members/${id}`, method: 'delete', params: { reason } })
  },
  /** 查询当前用户参与的项目组 */
  myTeams() {
    return request({ url: '/project-teams/my', method: 'get' })
  },
  /** 转让组长（同步更新项目负责人） */
  transferLeader(projectId, newLeaderId) {
    return request({ url: `/project-teams/${projectId}/transfer-leader`, method: 'post', params: { newLeaderId } })
  }
}
