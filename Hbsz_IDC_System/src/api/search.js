/**
 * 全局搜索模块 API
 *
 * 跨模块聚合搜索项目/文档/客户，供顶部全局搜索框调用。
 * @module api/search
 */
import request from '@/utils/request'

export const searchApi = {
  /**
   * 全局搜索
   * @param {string} q 关键字
   * @param {number} [limit=5] 每类最大返回条数
   * @returns {Promise<{projects:Array, documents:Array, customers:Array, total:number}>}
   */
  search(q, limit = 5) {
    return request({
      url: '/search',
      method: 'get',
      params: { q, limit },
      // 静默失败：搜索不弹错误提示，避免输入过程中频繁弹窗
      silent: true
    })
  }
}
