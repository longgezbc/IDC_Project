/**
 * 岗位管理 API 模块
 *
 * 对应后端 PositionController（@RequestMapping("/system/positions")）。
 * 前端 url 不带 /api 前缀，由 axios baseURL（/api）自动拼接。
 * 提供岗位的增删改查及启用岗位列表查询，服务于岗位管理页面与用户编辑下拉选择。
 * @module api/position
 */
import request from '@/utils/request'

export const positionApi = {
  /** 分页查询岗位列表 */
  query(params) {
    return request({ url: '/system/positions', method: 'get', params })
  },
  /** 查询所有启用岗位（下拉选择用） */
  listAll() {
    return request({ url: '/system/positions/all', method: 'get' })
  },
  /** 查询岗位详情 */
  getDetail(id) {
    return request({ url: `/system/positions/${id}`, method: 'get' })
  },
  /** 新增岗位 */
  create(data) {
    return request({ url: '/system/positions', method: 'post', data })
  },
  /** 修改岗位 */
  update(id, data) {
    return request({ url: `/system/positions/${id}`, method: 'put', data })
  },
  /** 删除岗位 */
  remove(id) {
    return request({ url: `/system/positions/${id}`, method: 'delete' })
  }
}
