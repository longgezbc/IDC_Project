/**
 * 通用格式化工具
 *
 * 同步源：与 Web 端共享的纯函数（formatDate/formatDateTime/formatFileSize/
 *        parseFileSize/getExt/getFileTypeColor/truncate）逻辑保持与
 *        Hbsz/shared/format.js 一致，修改时请同步更新 shared 源文件。
 *        本文件额外维护小程序 UI 相关的颜色/状态映射。
 */

/**
 * 格式化日期 yyyy-MM-dd
 * @param {string|number|Date} date 日期值
 * @returns {string} 格式化后的日期字符串；非法日期兜底截取原字符串前 10 位
 */
export function formatDate(date) {
	if (!date) return ''
	const d = new Date(date)
	// 非法日期兜底：直接截取原字符串前 10 位
	if (isNaN(d.getTime())) return String(date).substring(0, 10)
	const y = d.getFullYear()
	const m = String(d.getMonth() + 1).padStart(2, '0')
	const day = String(d.getDate()).padStart(2, '0')
	return `${y}-${m}-${day}`
}

/**
 * 格式化日期时间 yyyy-MM-dd HH:mm
 * @param {string|number|Date} date 日期值
 * @returns {string} 格式化后的日期时间字符串；非法日期原样返回
 */
export function formatDateTime(date) {
	if (!date) return ''
	const d = new Date(date)
	if (isNaN(d.getTime())) return String(date)
	const y = d.getFullYear()
	const m = String(d.getMonth() + 1).padStart(2, '0')
	const day = String(d.getDate()).padStart(2, '0')
	const h = String(d.getHours()).padStart(2, '0')
	const min = String(d.getMinutes()).padStart(2, '0')
	return `${y}-${m}-${day} ${h}:${min}`
}

/**
 * 文件大小格式化（与 shared/format.js 保持一致，单位前带空格）
 * @param {number|string} bytes 字节数
 * @returns {string} 带单位的可读大小（B/KB/MB/GB）
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
 * 截断文本
 * @param {string} str 原始文本
 * @param {number} [len=30] 最大保留长度，超出部分以省略号结尾
 * @returns {string} 截断后的文本
 */
export function truncate(str, len = 30) {
	if (!str) return ''
	return str.length > len ? str.substring(0, len) + '...' : str
}

/** 项目状态映射（与后端中文状态值保持一致） */
export const PROJECT_STATUS_MAP = {
	'进行中': { text: '进行中', color: '#1890FF', bg: '#E6F7FF' },
	'已完成': { text: '已完成', color: '#52C41A', bg: '#F6FFED' },
	'已归档': { text: '已归档', color: '#909399', bg: '#F4F4F5' },
	'暂停': { text: '暂停', color: '#FAAD14', bg: '#FFFBE6' },
	'延期': { text: '延期', color: '#FF4D4F', bg: '#FFF1F0' }
}

/**
 * 获取项目状态展示配置（文本/文字色/背景色），未知状态返回灰色兜底
 * @param {string} status 后端项目状态中文值
 * @returns {{text:string,color:string,bg:string}}
 */
export function getProjectStatus(status) {
	return PROJECT_STATUS_MAP[status] || { text: status || '未知', color: '#909399', bg: '#F4F4F5' }
}

/** 优先级映射（与后端中文取值一致） */
export const PRIORITY_MAP = {
	'高': { text: '高', color: '#FF4D4F' },
	'中': { text: '中', color: '#FAAD14' },
	'低': { text: '低', color: '#52C41A' }
}

/**
 * 获取优先级展示配置（文本/颜色）
 * @param {string} priority 优先级中文值（高/中/低）
 * @returns {{text:string,color:string}}
 */
export function getPriority(priority) {
	return PRIORITY_MAP[priority] || { text: priority || '-', color: '#909399' }
}

/** 任务状态映射（与后端中文取值一致） */
export const TASK_STATUS_MAP = {
	'未开始': { text: '未开始', color: '#909399' },
	'进行中': { text: '进行中', color: '#1890FF' },
	'已完成': { text: '已完成', color: '#52C41A' },
	'已延期': { text: '已延期', color: '#FF4D4F' },
	'已暂停': { text: '已暂停', color: '#FAAD14' }
}

/**
 * 获取任务状态展示配置（文本/颜色），未知状态返回灰色兜底
 * @param {string} status 后端任务状态中文值
 * @returns {{text:string,color:string}}
 */
export function getTaskStatus(status) {
	return TASK_STATUS_MAP[status] || { text: status || '未知', color: '#909399' }
}

/**
 * 文件类型 -> 图标颜色
 * @param {string} fileType 文件类型或扩展名（大小写不敏感）
 * @returns {string} 对应的图标背景色；未知类型返回主题蓝
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
 * 根据文件名获取扩展名
 * @param {string} name 文件名
 * @returns {string} 大写扩展名；无扩展名时返回空字符串
 */
export function getExt(name) {
	if (!name || !name.includes('.')) return ''
	return name.substring(name.lastIndexOf('.') + 1).toUpperCase()
}
