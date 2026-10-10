/**
 * 状态映射工具
 *
 * 集中管理"业务状态/类型枚举 → 中文文本 / el-tag type"的映射。
 * P3 修复：此前这些映射在 DailyReport / Dashboard / RiskBoard / MySubmissions /
 * NotificationDrawer / TransferLogs 等 6+ 个视图中重复定义（且实现风格分裂、
 * 命名不一），现统一收敛到此处，供所有视图复用。
 *
 * 约定：
 *   - 文本映射：未知值原样返回（保留后端新枚举的可读性），个别模块有历史
 *     默认值（如风险等级默认"低风险"、通知类型默认"其他"）按原行为保留；
 *   - tag 映射：未知值返回 'info'（灰色中性展示）。
 * @module utils/statusMap
 */

// ===== 项目 / 任务状态（兼容英文枚举与中文值两种后端形态） =====

/** 状态 → el-tag type */
const STATUS_TAG_TYPE_MAP = {
  completed: 'success', '已完成': 'success',
  in_progress: 'primary', '进行中': 'primary',
  delayed: 'danger', '已逾期': 'danger', '已延期': 'danger',
  pending: 'info', '待开始': 'info', '未开始': 'info',
  paused: 'warning', '已暂停': 'warning'
}

/** 状态英文枚举 → 中文文本 */
const STATUS_TEXT_MAP = {
  completed: '已完成',
  in_progress: '进行中',
  pending: '未开始',
  delayed: '已延期',
  paused: '已暂停'
}

/**
 * 项目/任务状态 → el-tag type
 * @param {string} status 状态枚举（completed/in_progress/pending/delayed/paused 或对应中文）
 * @returns {string} el-tag type，未知返回 'info'
 */
export function getStatusTagType(status) {
  return STATUS_TAG_TYPE_MAP[status] || 'info'
}

/**
 * 项目/任务状态 → 中文文本
 * @param {string} status 状态英文枚举
 * @returns {string} 中文名；空值返回 '-'，未知值原样返回
 */
export function getStatusText(status) {
  if (!status) return '-'
  return STATUS_TEXT_MAP[status] || status
}

// ===== 审核状态（进度填报 / 文档等审批流） =====

/** 审核状态 → 中文文本 */
const REVIEW_STATUS_TEXT_MAP = { pending: '待审核', approved: '已通过', rejected: '已驳回' }

/** 审核状态 → el-tag type */
const REVIEW_STATUS_TAG_MAP = { pending: 'warning', approved: 'success', rejected: 'danger' }

/**
 * 审核状态 → 中文文本
 * @param {string} status pending/approved/rejected
 * @returns {string} 中文名，未知值原样返回
 */
export function getReviewStatusText(status) {
  return REVIEW_STATUS_TEXT_MAP[status] || status
}

/**
 * 审核状态 → el-tag type
 * @param {string} status pending/approved/rejected
 * @returns {string} el-tag type，未知返回 'info'
 */
export function getReviewStatusTagType(status) {
  return REVIEW_STATUS_TAG_MAP[status] || 'info'
}

// ===== 风险等级 =====

/**
 * 风险等级 → el-tag type
 * @param {string} level high/medium/low
 * @returns {string} el-tag type，未知按低风险处理返回 'success'
 */
export function getRiskTagType(level) {
  const map = { high: 'danger', medium: 'warning', low: 'success' }
  return map[level] || 'success'
}

/**
 * 风险等级 → 中文文本
 * @param {string} level high/medium/low
 * @returns {string} 中文名，未知按低风险处理
 */
export function getRiskLevelText(level) {
  const map = { high: '高风险', medium: '中等', low: '低风险' }
  return map[level] || '低风险'
}

// ===== 站内通知类型 =====

/**
 * 通知类型 → 中文文本
 * @param {string} notifyType progress/task_due/doc_update/archive/system
 * @returns {string} 中文名，未知返回 '其他'
 */
export function getNotifyTypeText(notifyType) {
  const map = { progress: '进度', task_due: '到期', doc_update: '文档', archive: '归档', system: '系统' }
  return map[notifyType] || '其他'
}

/**
 * 通知类型 → el-tag type
 * @param {string} notifyType progress/task_due/doc_update/archive/system
 * @returns {string} el-tag type，未知返回 'info'
 */
export function getNotifyTypeTagType(notifyType) {
  const map = { progress: 'primary', task_due: 'warning', doc_update: 'info', archive: 'success', system: 'info' }
  return map[notifyType] || 'info'
}

// ===== 项目流转操作类型（转移/授权日志） =====

/**
 * 流转操作类型 → 中文文本
 * 授权双入口收敛后，直接授权产生 auth_request + approve 两条审计（替代旧的 assign）；
 * assign 保留映射用于展示历史存量日志。
 * transfer_leader：负责人移交（项目所有权与审批责任转移，与团队管理 transferLeader 单点对应）。
 * @param {string} type transfer/transfer_leader/assign/revoke/auth_request/approve/reject/extend
 * @returns {string} 中文名，未知值原样返回
 */
export function getActionText(type) {
  const map = {
    transfer: '转移',
    transfer_leader: '负责人移交',
    assign: '直接授权',
    revoke: '撤销',
    auth_request: '授权申请',
    approve: '审批通过',
    reject: '审批驳回',
    extend: '延期'
  }
  return map[type] || type
}

/**
 * 流转操作类型 → el-tag type
 * @param {string} type transfer/transfer_leader/assign/revoke/auth_request/approve/reject/extend
 * @returns {string} el-tag type，未知返回 'info'
 */
export function getActionTagType(type) {
  const map = {
    transfer: 'warning',
    transfer_leader: 'primary',
    assign: 'success',
    revoke: 'danger',
    auth_request: 'primary',
    approve: 'success',
    reject: 'danger',
    extend: 'warning'
  }
  return map[type] || 'info'
}
