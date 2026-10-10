/**
 * 文件下载工具
 *
 * 统一封装"Blob → ObjectURL → 触发浏览器下载 → 延迟释放"流程。
 * P3 修复：此前该流程在 UserManagement/CustomerManagement/ProjectList/
 * DailyReport/Statistics/DocumentManagement/DocumentVersionDialog 7 处逐字重复，
 * 且释放时机不一致（3 处立即 revoke，4 处延迟 100ms）。立即 revoke 在部分
 * 浏览器慢速启动下载时会截断文件，现统一为 100ms 延迟释放兜底。
 * @module utils/download
 */

/** Blob URL 释放延迟（毫秒）：留出浏览器读取 Blob 的时间，防止下载被截断 */
const REVOKE_DELAY = 100

/**
 * 触发浏览器下载一个 Blob
 * @param {Blob} blob 文件内容
 * @param {string} filename 下载保存的文件名
 * @returns {void}
 */
export function downloadBlob(blob, filename) {
  if (!blob) return
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'download'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  setTimeout(() => window.URL.revokeObjectURL(url), REVOKE_DELAY)
}

/**
 * 下载 axios blob 响应（responseType: 'blob' 的接口返回值）
 *
 * 后端接口必须以 responseType: 'blob' 请求，response.data 即 Blob。
 * 若响应未携带类型信息（type 为空），可用 mime 参数补齐（如 Excel 导出）。
 * @param {Object} response axios 响应对象
 * @param {string} filename 下载保存的文件名
 * @param {string} [mime] 可选 MIME 类型，仅在 Blob 自身无 type 时应用
 * @returns {void}
 */
export function downloadBlobFromResponse(response, filename, mime) {
  const raw = response?.data
  if (!raw) return
  const blob = !raw.type && mime ? new Blob([raw], { type: mime }) : raw
  downloadBlob(blob, filename)
}
