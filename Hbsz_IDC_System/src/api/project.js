import request from '@/utils/request'
import { uploadFile } from '@/utils/upload'

/**
 * 项目管理模块 API
 *
 * 提供项目的增删改查、归档/取消归档、统计概览以及关联文档查询等接口。
 * 所有方法均返回 request 产生的 Promise，响应体已由拦截器统一解包。
 * @module api/project
 */
export const projectApi = {
  /**
   * 项目列表查询（分页/筛选）
   * @param {Object} params 查询参数，如分页、关键字、状态、类型等
   * @returns {Promise<any>} 分页后的项目列表数据
   */
  getList(params) {
    return request({
      url: '/projects',
      method: 'get',
      params
    })
  },

  /**
   * 项目详情
   * @param {number|string} id 项目 ID
   * @returns {Promise<any>} 指定项目的详细信息
   */
  getDetail(id) {
    return request({
      url: `/projects/${id}`,
      method: 'get'
    })
  },

  /**
   * 新建项目
   * @param {Object} data 项目字段数据
   * @returns {Promise<any>} 新建后的项目信息
   */
  create(data) {
    return request({
      url: '/projects',
      method: 'post',
      data
    })
  },

  /**
   * 更新项目
   * @param {number|string} id 项目 ID
   * @param {Object} data 待更新的项目字段
   * @returns {Promise<any>} 更新后的项目信息
   */
  update(id, data) {
    return request({
      url: `/projects/${id}`,
      method: 'put',
      data
    })
  },

  /**
   * 删除项目
   * @param {number|string} id 项目 ID
   * @returns {Promise<any>} 删除操作结果
   */
  remove(id) {
    return request({
      url: `/projects/${id}`,
      method: 'delete'
    })
  },

  /**
   * 归档项目
   * @param {number|string} id 项目 ID
   * @returns {Promise<any>} 归档操作结果
   */
  archive(id) {
    return request({
      url: `/projects/${id}/archive`,
      method: 'put'
    })
  },

  /**
   * 取消归档
   * @param {number|string} id 项目 ID
   * @returns {Promise<any>} 取消归档操作结果
   */
  unarchive(id) {
    return request({
      url: `/projects/${id}/unarchive`,
      method: 'put'
    })
  },

  /**
   * 下载导入模板（返回 xlsx 文件流）
   * @returns {Promise<any>} 包含 blob 数据的响应对象
   */
  downloadImportTemplate() {
    return request({
      url: '/projects/import-template',
      method: 'get',
      responseType: 'blob'
    })
  },

  /**
   * 批量导入项目
   * @param {File} file Excel 文件
   * @returns {Promise<any>} 导入结果（成功/失败条数及错误详情）
   */
  importProjects(file) {
    return uploadFile(file, '/projects/import')
  }
}
