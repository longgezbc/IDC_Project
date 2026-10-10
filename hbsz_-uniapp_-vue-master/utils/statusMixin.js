/**
 * 状态展示 Mixin（增强版 v2）
 *
 * 背景：v1 只提供 statusText/taskStatusText，statusStyle 因各页面定制不纳入 mixin。
 *      但实际有 8+ 个页面都在重复实现 statusStyle（返回 CSS 字符串），
 *      且逻辑高度雷同（颜色映射），属于"伪定制"。
 *      v2 将 statusStyle/priorityStyle/statusClass 统一纳入 mixin，并提供：
 *      - 内联样式（style 字符串，兼容 :style="statusStyle(s)" 用法）
 *      - CSS 类名（class 字符串，配套 StatusTag 组件）
 *
 * 用法：
 *   import { statusMixin } from '@/utils/statusMixin'
 *   mixins: [statusMixin]
 *   // 模板即可直接用：statusText(s) / statusStyle(s) / priorityText(p) / priorityStyle(p)
 *
 * 配套组件：
 *   <status-tag :status="s" type="project" />
 *   <status-tag :status="s" type="task" />
 *   <status-tag :text="p" type="priority" />
 *
 * 注意：若个别页面有特殊样式需求，可在页面 methods 中覆盖同名方法。
 */
import { getProjectStatus, getTaskStatus, getPriority } from './format'

export const statusMixin = {
  methods: {
    /* ============ 项目状态 ============ */
    /** 项目状态文本 */
    statusText(s) {
      return getProjectStatus(s).text
    },
    /**
     * 项目状态内联样式（兼容 :style="statusStyle(s)" 旧用法）
     * @returns {string} 形如 'color:#1890FF;background:#E6F7FF;'
     */
    statusStyle(s) {
      const cfg = getProjectStatus(s)
      return `color:${cfg.color};background:${cfg.bg || '#F4F4F5'};`
    },

    /* ============ 任务状态 ============ */
    /** 任务状态文本 */
    taskStatusText(s) {
      return getTaskStatus(s).text
    },
    /** 任务状态内联样式（与 statusStyle 兼容，统一字段） */
    taskStatusStyle(s) {
      const cfg = getTaskStatus(s)
      // 任务状态无 bg 字段，统一使用浅色背景
      const bgMap = {
        '#1890FF': '#E6F7FF',
        '#52C41A': '#F6FFED',
        '#909399': '#F4F4F5',
        '#FF4D4F': '#FFF1F0',
        '#FAAD14': '#FFFBE6'
      }
      return `color:${cfg.color};background:${bgMap[cfg.color] || '#F4F4F5'};`
    },

    /* ============ 优先级 ============ */
    /** 优先级文本 */
    priorityText(p) {
      return getPriority(p).text
    },
    /** 优先级内联样式 */
    priorityStyle(p) {
      const cfg = getPriority(p)
      const bgMap = {
        '#FF4D4F': '#FFF1F0',
        '#FAAD14': '#FFFBE6',
        '#52C41A': '#F6FFED'
      }
      return `color:${cfg.color};background:${bgMap[cfg.color] || '#F4F4F5'};`
    },

    /* ============ 审核状态（进度/工作量/授权三类审批流，英文枚举） ============ */
    /** 审核状态文本：pending/approved/rejected */
    reviewStatusText(s) {
      const map = { pending: '待审核', approved: '已通过', rejected: '已驳回' }
      return map[s] || s || '-'
    },
    /** 审核状态内联样式（与 Web 端 statusMap 的 warning/success/danger 对应） */
    reviewStatusStyle(s) {
      const map = {
        pending: 'color:#FAAD14;background:#FFFBE6;',
        approved: 'color:#52C41A;background:#F6FFED;',
        rejected: 'color:#FF4D4F;background:#FFF1F0;'
      }
      return map[s] || 'color:#909399;background:#F4F4F5;'
    },

    /* ============ 流转操作类型（转移/授权审计日志） ============ */
    /**
     * 流转操作类型文本。
     * 授权双入口收敛后直接授权产生 auth_request + approve 两条审计，
     * assign 保留映射用于展示历史存量日志（与 Web 端 statusMap 一致）。
     */
    actionText(t) {
      const map = {
        transfer: '转移',
        assign: '直接授权',
        revoke: '撤销',
        auth_request: '授权申请',
        approve: '审批通过',
        reject: '审批驳回',
        extend: '延期'
      }
      return map[t] || t || '-'
    },
    /** 流转操作类型内联样式 */
    actionStyle(t) {
      const map = {
        transfer: 'color:#FAAD14;background:#FFFBE6;',
        assign: 'color:#52C41A;background:#F6FFED;',
        revoke: 'color:#FF4D4F;background:#FFF1F0;',
        auth_request: 'color:#1890FF;background:#E6F7FF;',
        approve: 'color:#52C41A;background:#F6FFED;',
        reject: 'color:#FF4D4F;background:#FFF1F0;',
        extend: 'color:#FAAD14;background:#FFFBE6;'
      }
      return map[t] || 'color:#909399;background:#F4F4F5;'
    }
  }
}

export default statusMixin
