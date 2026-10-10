/**
 * 公共格式化工具函数
 * 统一管理项目中重复使用的格式化方法，保证一致性
 *
 * 跨端共享：日期、文件大小、扩展名等纯函数已抽到 @shared/format，
 *           Web 端这里仅保留 Element Plus Tag 类型、颜色等 UI 相关映射。
 * @module utils/format
 */

// 跨端共享的纯函数，从 shared 模块 re-export，业务方仍可从 '@/utils/format' 统一引入
export {
  formatDate,
  formatDateTime,
  formatFileSize,
  parseFileSize as parseSize,
  formatFileSize as formatSize,
  getExt,
  getFileTypeColor,
  truncate
} from '@shared/format'

/**
 * 格式化金额展示
 * @param {number} num 金额数值
 * @param {boolean} [compact=false] 是否紧凑显示（超过 1 万时显示为"X.X 万"）
 * @returns {string} 格式化后的金额字符串
 */
export function formatAmount(num, compact = false) {
  if (num === null || num === undefined) return '0'
  if (compact && num >= 10000) return (num / 10000).toFixed(1) + '万'
  return Number(num).toLocaleString('zh-CN')
}

/**
 * 数字显示：去掉无意义小数尾零（null/undefined 显示 0）
 * @param {number|string} val 数值
 * @returns {string} 格式化后的数字字符串
 */
export function formatQty(val) {
  if (val === null || val === undefined) return '0'
  const n = Number(val)
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '')
}

/**
 * 数量显示：null/undefined 显示 "-"（如未设置总量的剩余工作量），其余走 formatQty
 * @param {number|string} val 数值
 * @returns {string} 格式化后的数字字符串或 "-"
 */
export function formatQtyOrDash(val) {
  return (val === null || val === undefined) ? '-' : formatQty(val)
}

/**
 * 获取项目状态的 Element Plus Tag 类型
 * @param {string} status 项目状态
 * @returns {string} Tag 类型（primary/success/info/warning/danger）
 */
export function getStatusType(status) {
  const map = {
    '进行中': 'primary',
    '已完成': 'success',
    '已归档': 'info',
    '暂停': 'warning',
    '延期': 'danger'
  }
  return map[status] || 'info'
}

/**
 * 获取任务状态的 Element Plus Tag 类型
 * @param {string} status 任务状态
 * @returns {string} Tag 类型
 */
export function getTaskStatusType(status) {
  const map = {
    '已完成': 'success',
    '进行中': 'primary',
    '未开始': 'info',
    '已延期': 'danger',
    '已暂停': 'warning'
  }
  return map[status] || 'info'
}

/**
 * 根据进度百分比返回对应颜色
 * @param {number} progress 进度值（0-100）
 * @returns {string} 十六进制颜色值
 */
export function getProgressColor(progress) {
  if (progress === 100) return '#52c41a'
  if (progress >= 50) return '#1890ff'
  if (progress >= 25) return '#faad14'
  return '#f5222d'
}

/**
 * 获取文档分类的 Element Plus Tag 类型
 * @param {string} category 文档分类
 * @returns {string} Tag 类型
 */
export function getCategoryTagType(category) {
  const map = {
    '技术文档': 'primary',
    '合同文件': 'warning',
    '验收报告': 'success',
    '设计图纸': 'info',
    '会议纪要': 'info',
    '其他': 'info'
  }
  // 未匹配的分类返回 'info'：el-tag 的 type prop 不接受空字符串，
  // 必须是 primary/success/info/warning/danger 之一
  return map[category] || 'info'
}

/**
 * 获取文档分类对应的展示颜色
 * @param {string} category 文档分类
 * @returns {string} 十六进制颜色值
 */
export function getCategoryColor(category) {
  const map = {
    '技术文档': '#1890ff',
    '合同文件': '#faad14',
    '验收报告': '#52c41a',
    '设计图纸': '#722ed1',
    '会议纪要': '#13c2c2',
    '其他': '#909399'
  }
  return map[category] || '#909399'
}
