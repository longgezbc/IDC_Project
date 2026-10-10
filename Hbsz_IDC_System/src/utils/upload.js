/**
 * 文件上传公共 helper
 *
 * 统一封装 FormData 构造 + request 调用，消除 customer/project/system/task
 * 四个 API 模块中重复的上传代码。
 *
 * 注意：不要手动设置 Content-Type，axios 检测到 FormData 会自动设置
 *       multipart/form-data 并附加 boundary 参数。手动设置会丢失 boundary
 *       导致后端无法解析表单字段（M20 修复经验）。
 */
import request from '@/utils/request'

/**
 * 上传单个文件到指定接口
 * @param {File} file 文件对象
 * @param {string} url 上传接口 URL
 * @param {Object} [extraConfig] 额外的 request 配置（如 skipCancel 等）
 * @returns {Promise<any>} request 返回的 Promise
 */
export function uploadFile(file, url, extraConfig = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url,
    method: 'post',
    data: formData,
    ...extraConfig
  })
}
