/**
 * 材料库存管理模块 API
 * @module api/material
 */
import request from '@/utils/request'
import { uploadFile } from '@/utils/upload'

export const materialApi = {
  getList(params) {
    return request({ url: '/materials', method: 'get', params })
  },
  getDetail(id) {
    return request({ url: `/materials/${id}`, method: 'get' })
  },
  create(data) {
    return request({ url: '/materials', method: 'post', data })
  },
  update(id, data) {
    return request({ url: `/materials/${id}`, method: 'put', data })
  },
  remove(id) {
    return request({ url: `/materials/${id}`, method: 'delete' })
  },
  /** 入库 */
  stockIn(id, data) {
    return request({ url: `/materials/${id}/stock-in`, method: 'post', data })
  },
  /** 出库 */
  stockOut(id, data) {
    return request({ url: `/materials/${id}/stock-out`, method: 'post', data })
  },
  /** 库存调整 */
  adjust(id, data) {
    return request({ url: `/materials/${id}/adjust`, method: 'post', data })
  },
  /** 变更流水 */
  getTransactions(id, pageNum = 1, pageSize = 20) {
    return request({ url: `/materials/${id}/transactions`, method: 'get', params: { pageNum, pageSize } })
  },
  /** 按去向项目查材料出库流水（项目详情页"项目材料" tab） */
  getProjectTransactions(projectId, pageNum = 1, pageSize = 20) {
    return request({ url: '/materials/project-transactions', method: 'get', params: { projectId, pageNum, pageSize } })
  },
  /** 下载导入模板（返回 xlsx 文件流） */
  downloadImportTemplate() {
    return request({ url: '/materials/import-template', method: 'get', responseType: 'blob' })
  },
  /** 批量导入材料（出库物料导入模板） */
  importMaterials(file) {
    return uploadFile(file, '/materials/import')
  },
  /** 待处理预警 */
  getAlerts() {
    return request({ url: '/materials/alerts', method: 'get' })
  },
  /** 处理预警 */
  resolveAlert(alertId) {
    return request({ url: `/materials/alerts/${alertId}/resolve`, method: 'put' })
  }
}
