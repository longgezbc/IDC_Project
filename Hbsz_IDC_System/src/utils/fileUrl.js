/**
 * 文件 URL 解析工具
 *
 * 后端返回的图片/附件为相对路径（如 uploads/2026/08/xx.jpg 或带前导斜杠的
 * /uploads/...），需拼接 /api/files/preview 前缀才能被 <img>/预览访问。
 * P3 修复：此前 MySubmissions / PendingReview / ProgressReviewDialog 三处
 * 各自实现且行为不一致（是否处理连续前导斜杠），统一收敛到此处。
 * @module utils/fileUrl
 */

/**
 * 将后端相对文件路径解析为可访问 URL
 * @param {string} relativePath 相对路径（空值返回空串）
 * @param {string} [base='/api/files/preview'] 预览接口前缀
 * @returns {string} 可直接用于 src/href 的 URL；已是 http(s) 绝对地址时原样返回
 */
export function resolveFileUrl(relativePath, base = '/api/files/preview') {
  if (!relativePath) return ''
  if (/^https?:\/\//.test(relativePath)) return relativePath
  // 去掉所有前导斜杠后统一拼接，兼容 "a/b"、"a/b"、"/a/b" 等形态
  return `${base}/${String(relativePath).replace(/^\/+/, '')}`
}
