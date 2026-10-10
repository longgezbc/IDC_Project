import request from '@/utils/request'
import { uploadFile } from '@/utils/upload'

/**
 * 客户管理模块 API
 *
 * 提供客户的增删改查、状态筛选及下拉列表等接口。
 * 所有方法均返回 request 产生的 Promise，响应体已由拦截器统一解包。
 * @module api/customer
 */
export const customerApi = {
  /**
   * 客户列表查询（分页/筛选）
   * @param {Object} params 查询参数，如 { pageNum, pageSize, keyword, status }
   * @returns {Promise<any>} 分页后的客户列表数据（每条含 projectCount）
   */
  getList(params) {
    return request({
      url: '/customers',
      method: 'get',
      params
    })
  },

  /**
   * 查询所有启用客户（下拉用，不分页）
   * @returns {Promise<any>} 启用客户列表
   */
  listAll() {
    return request({
      url: '/customers/all',
      method: 'get'
    })
  },

  /**
   * 客户详情
   * @param {number|string} id 客户 ID
   * @returns {Promise<any>} 客户详情（含 projectCount）
   */
  getDetail(id) {
    return request({
      url: `/customers/${id}`,
      method: 'get'
    })
  },

  /**
   * 新建客户
   * @param {Object} data 客户字段数据
   * @returns {Promise<any>} 新建后的客户信息
   */
  create(data) {
    return request({
      url: '/customers',
      method: 'post',
      data
    })
  },

  /**
   * 更新客户
   * @param {number|string} id 客户 ID
   * @param {Object} data 待更新的客户字段
   * @returns {Promise<any>} 更新后的客户信息
   */
  update(id, data) {
    return request({
      url: `/customers/${id}`,
      method: 'put',
      data
    })
  },

  /**
   * 删除客户（有关联项目时禁止删除）
   * @param {number|string} id 客户 ID
   * @returns {Promise<any>} 删除操作结果
   */
  remove(id) {
    return request({
      url: `/customers/${id}`,
      method: 'delete'
    })
  },

  /**
   * 下载导入模板（返回 xlsx 文件流）
   * @returns {Promise<any>} 包含 blob 数据的响应对象
   */
  downloadImportTemplate() {
    return request({
      url: '/customers/import-template',
      method: 'get',
      responseType: 'blob'
    })
  },

  /**
   * 批量导入客户
   * @param {File} file Excel 文件
   * @returns {Promise<any>} 导入结果（成功/失败条数及错误详情）
   */
  importCustomers(file) {
    return uploadFile(file, '/customers/import')
  }
}
