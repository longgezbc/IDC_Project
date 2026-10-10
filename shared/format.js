/**
 * 跨端共享的纯函数格式化工具
 *
 * 背景：Web（Hbsz_IDC_System）与小程序（Hbsz_Uniapp_vue）各自维护一份 format.js，
 *      日期/文件大小/扩展名等纯逻辑函数完全重复，存在双端不一致风险。
 * 约定：本文件只放不依赖任何 UI 框架、不依赖运行时 API 的纯函数。
 *      涉及 Element Plus Tag 类型、小程序颜色十六进制等 UI 相关映射，
 *      由两端各自在 format.js 中维护。
 *
 * 引用方式：
 *   - Web 端：vite alias `@shared` -> 本目录，import { formatDate } from '@shared/format'
 *   小程序端：HBuilderX 不支持跨目录引用，utils/format.js 保留独立副本，
 *      修改本文件后需手动同步到 Hbsz_Uniapp_vue/utils/format.js 对应函数。
 */

/**
 * 格式化日期 yyyy-MM-dd
 * @param {string|number|Date} date 日期值
 * @returns {string}
 */
export function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  if (isNaN(d.getTime())) return String(date).substring(0, 10)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * 格式化日期时间
 * 默认 yyyy-MM-dd HH:mm，可自定义格式（支持 YYYY/MM/DD/HH/mm/ss 占位符）
 * @param {string|number|Date} value 日期值
 * @param {string} [format='YYYY-MM-DD HH:mm'] 格式
 * @returns {string}
 */
export function formatDateTime(value, format = 'YYYY-MM-DD HH:mm') {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const map = {
    YYYY: d.getFullYear(),
    MM: pad(d.getMonth() + 1),
    DD: pad(d.getDate()),
    HH: pad(d.getHours()),
    mm: pad(d.getMinutes()),
    ss: pad(d.getSeconds())
  }
  return format.replace(/YYYY|MM|DD|HH|mm|ss/g, (m) => map[m])
}

/**
 * 格式化文件大小
 * @param {number} bytes 字节数
 * @returns {string} 带单位的文件大小字符串
 */
export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const n = Number(bytes)
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(1) + ' GB'
}

/**
 * 解析文件大小字符串为字节数
 * 用于处理后端返回的格式化字符串（如 "1.5MB"、"500KB"）并累加
 * @param {string|number} size 文件大小字符串或字节数
 * @returns {number} 字节数
 */
export function parseFileSize(size) {
  if (!size) return 0
  if (typeof size === 'number') return size
  const str = String(size).trim().toUpperCase()
  const match = str.match(/^([\d.]+)\s*(B|KB|MB|GB|TB)?$/)
  if (!match) return 0
  const num = parseFloat(match[1])
  const unit = match[2] || 'B'
  const units = { B: 1, KB: 1024, MB: 1024 * 1024, GB: 1024 * 1024 * 1024, TB: 1024 * 1024 * 1024 * 1024 }
  return num * (units[unit] || 1)
}

/**
 * 根据文件名获取扩展名（大写）
 * @param {string} name 文件名
 * @returns {string} 扩展名（不含点），无扩展名返回空字符串
 */
export function getExt(name) {
  if (!name || !name.includes('.')) return ''
  return name.substring(name.lastIndexOf('.') + 1).toUpperCase()
}

/**
 * 文件类型 -> 图标颜色（跨端通用，UI 框架无关）
 * @param {string} fileType 文件扩展名
 * @returns {string} 十六进制颜色值
 */
export function getFileTypeColor(fileType) {
  const t = (fileType || '').toUpperCase()
  if (['PDF'].includes(t)) return '#FF4D4F'
  if (['DOC', 'DOCX'].includes(t)) return '#1890FF'
  if (['XLS', 'XLSX'].includes(t)) return '#52C41A'
  if (['PPT', 'PPTX'].includes(t)) return '#FA8C16'
  if (['JPG', 'JPEG', 'PNG', 'GIF', 'BMP'].includes(t)) return '#722ED1'
  if (['ZIP', 'RAR', '7Z'].includes(t)) return '#8C8C8C'
  if (['TXT', 'MD'].includes(t)) return '#595959'
  return '#1890FF'
}

/**
 * 截断文本
 * @param {string} str 原始字符串
 * @param {number} [len=30] 最大长度
 * @returns {string}
 */
export function truncate(str, len = 30) {
  if (!str) return ''
  return str.length > len ? str.substring(0, len) + '...' : str
}
