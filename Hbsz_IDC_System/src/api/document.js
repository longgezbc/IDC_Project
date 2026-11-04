import request from '@/utils/request'

/**
 * 文档管理模块 API
 *
 * 提供文档的列表查询、分类统计、详情、上传、更新、删除、版本管理、
 * 下载与预览等接口。上传与新增版本使用 multipart/form-data；
 * 下载接口以 blob 形式接收二进制流。
 * @module api/document
 */
export const documentApi = {
  /**
   * 文档列表查询（分页/筛选）
   * @param {Object} params 查询参数，如分页、分类、关键字等
   * @returns {Promise<any>} 分页后的文档列表数据
   */
  getList(params) {
    return request({
      url: '/documents',
      method: 'get',
      params
    })
  },

  /**
   * 文档分类统计（用于左侧分类菜单的计数显示）
   * @returns {Promise<any>} 各分类下的文档数量统计
   */
  getCategoryStats() {
    return request({
      url: '/documents/category-stats',
      method: 'get'
    })
  },

  /**
   * 文档详情
   * @param {number|string} id 文档 ID
   * @returns {Promise<any>} 指定文档的详细信息
   */
  getDetail(id) {
    return request({
      url: `/documents/${id}`,
      method: 'get'
    })
  },

  /**
   * 上传文档（multipart/form-data）
   * @param {FormData} formData 包含文件及元信息的表单数据
   * @returns {Promise<any>} 上传后的文档信息
   */
  upload(formData) {
    // 修复 M20：删除手动设置的 'Content-Type': 'multipart/form-data'，
    // 该 header 缺少 boundary 参数，后端无法解析表单字段。
    // axios 检测到 data 为 FormData 时会自动设置带 boundary 的正确 Content-Type。
    return request({
      url: '/documents',
      method: 'post',
      data: formData
    })
  },

  /**
   * 更新文档
   * @param {number|string} id 文档 ID
   * @param {Object} data 待更新的文档字段
   * @returns {Promise<any>} 更新后的文档信息
   */
  update(id, data) {
    return request({
      url: `/documents/${id}`,
      method: 'put',
      data
    })
  },

  /**
   * 删除文档
   * @param {number|string} id 文档 ID
   * @returns {Promise<any>} 删除操作结果
   */
  remove(id) {
    return request({
      url: `/documents/${id}`,
      method: 'delete'
    })
  },

  /**
   * 新增文档版本（multipart/form-data）
   * @param {number|string} id 文档 ID
   * @param {FormData} formData 新版本文件及元信息表单数据
   * @returns {Promise<any>} 新增版本后的文档信息
   */
  addVersion(id, formData) {
    // 修复 M20：同 upload，删除手动 Content-Type，由 axios 自动设置带 boundary 的正确值
    return request({
      url: `/documents/${id}/versions`,
      method: 'post',
      data: formData
    })
  },

  /**
   * 文档下载
   * @param {number|string} id 文档 ID
   * @returns {Promise<Blob>} 文档文件的二进制流（responseType: blob）
   */
  download(id) {
    return request({
      url: `/documents/${id}/download`,
      method: 'get',
      responseType: 'blob'
    })
  },

  /**
   * 文档预览
   * @param {number|string} id 文档 ID
   * @returns {Promise<any>} 预览所需的地址或内容数据
   */
  preview(id) {
    return request({
      url: `/documents/${id}/preview`,
      method: 'get'
    })
  },

  /**
   * OnlyOffice 在线编辑配置
   * @param {number|string} id 文档 ID
   * @param {string} [mode='edit'] edit 或 view
   * @returns {Promise<Object>} 编辑器初始化配置（含 JWT 签名）
   */
  editConfig(id, mode = 'edit') {
    return request({
      url: `/documents/${id}/edit-config`,
      method: 'get',
      params: { mode }
    })
  },

  /**
   * 审核文档（通过/驳回）
   * @param {number|string} id 文档 ID
   * @param {Object} data { approve: boolean, remark: string }
   * @returns {Promise<any>} 审核后的文档信息
   */
  review(id, data) {
    return request({
      url: `/documents/${id}/review`,
      method: 'post',
      data
    })
  }
}
