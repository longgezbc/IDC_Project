/**
 * 项目模板模块 API
 * @module api/projectTemplate
 */
import request from '@/utils/request'

export const projectTemplateApi = {
  getList(params) {
    return request({ url: '/project-templates', method: 'get', params })
  },
  getEnabled() {
    return request({ url: '/project-templates/enabled', method: 'get' })
  },
  getDetail(id) {
    return request({ url: `/project-templates/${id}`, method: 'get' })
  },
  create(data) {
    return request({ url: '/project-templates', method: 'post', data })
  },
  update(id, data) {
    return request({ url: `/project-templates/${id}`, method: 'put', data })
  },
  remove(id) {
    return request({ url: `/project-templates/${id}`, method: 'delete' })
  }
}
