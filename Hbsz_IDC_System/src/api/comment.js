/**
 * 评论模块 API（含 @提及）
 * @module api/comment
 */
import request from '@/utils/request'

export const commentApi = {
  /** 按业务对象查询评论 */
  getList(bizType, bizId, pageNum = 1, pageSize = 20) {
    return request({ url: '/comments', method: 'get', params: { bizType, bizId, pageNum, pageSize } })
  },
  /** 创建评论（后端自动解析 @提及并发通知） */
  create(data) {
    return request({ url: '/comments', method: 'post', data })
  },
  /** 删除评论 */
  remove(id) {
    return request({ url: `/comments/${id}`, method: 'delete' })
  }
}
