/**
 * 统一操作反馈工具
 *
 * 封装 ElMessage / ElMessageBox，提供一致的成功/失败/确认交互体验：
 * - 成功操作：绿色消息 + 自动消失
 * - 错误提示：红色消息 + 需手动关闭（重要错误）
 * - 删除确认：二次确认对话框 + 显示影响范围
 * - 批量操作：进度反馈
 *
 * 用法：
 *   import { feedback } from '@/utils/feedback'
 *   feedback.success('保存成功')
 *   feedback.error('网络错误')
 *   feedback.confirm('确认删除？', '将删除3条记录').then(...)
 */
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'

const SUCCESS_DURATION = 2500
const ERROR_DURATION = 4000

/**
 * HTML 特殊字符转义，防止 XSS
 *
 * confirm 方法使用 dangerouslyUseHTMLString 渲染富文本布局（<br/>、<span>），
 * 但 message / detail 为开放入参，可能包含用户可控内容（用户名、项目名、备注等）。
 * 若不转义，攻击者可通过 <img src=x onerror=alert(1)> 等 payload 注入脚本。
 * 本函数对 5 个 HTML 特殊字符做实体转义，确保用户内容以纯文本形式渲染。
 * P3 修复：加 export 供 composables/useImport.js 等复用（此前 Excel 导入
 * 错误详情在 3 个视图中内联重复同样的转义链）。
 * @param {string} str 原始字符串
 * @returns {string} 转义后的字符串（& < > " ' 均转为 HTML 实体）
 */
export function escapeHtml(str) {
  if (str === null || str === undefined) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export const feedback = {
  /**
   * 成功提示（绿色，2.5秒自动消失）
   */
  success(message, duration = SUCCESS_DURATION) {
    ElMessage({
      message,
      type: 'success',
      duration,
      showClose: false,
      grouping: true
    })
  },

  /**
   * 错误提示（红色，4秒自动消失，带关闭按钮）
   */
  error(message, duration = ERROR_DURATION) {
    ElMessage({
      message,
      type: 'error',
      duration,
      showClose: true,
      grouping: true
    })
  },

  /**
   * 警告提示（橙色，3秒自动消失）
   */
  warning(message, duration = 3000) {
    ElMessage({
      message,
      type: 'warning',
      duration,
      showClose: false,
      grouping: true
    })
  },

  /**
   * 信息提示（蓝色，3秒自动消失）
   */
  info(message, duration = 3000) {
    ElMessage({
      message,
      type: 'info',
      duration,
      showClose: false,
      grouping: true
    })
  },

  /**
   * 操作确认对话框
   *
   * 安全说明：
   *   message / detail 为开放入参，可能包含用户可控内容（用户名、项目名、备注等）。
   *   - 有 detail 时：需要 <br/> + <span> 富文本布局，启用 dangerouslyUseHTMLString，
   *     但对 message / detail 先做 escapeHtml 转义，防止 XSS 注入。
   *   - 无 detail 时：content 为纯文本，关闭 dangerouslyUseHTMLString，完全杜绝 XSS。
   * @param {string} message 确认消息
   * @param {object} options { title, type, confirmText, cancelText, detail }
   * @returns {Promise}
   */
  confirm(message, options = {}) {
    const {
      title = '操作确认',
      type = 'warning',
      confirmText = '确认',
      cancelText = '取消',
      detail = ''
    } = options

    const hasDetail = !!detail
    // 有 detail 时需要富文本布局（<br/> + <span>），对用户内容转义后拼接；
    // 无 detail 时为纯文本，不启用 HTML 渲染，彻底杜绝 XSS。
    const content = hasDetail
      ? `${escapeHtml(message)}<br/><span style="color:#909399;font-size:13px;margin-top:8px;display:inline-block">${escapeHtml(detail)}</span>`
      : message

    return ElMessageBox.confirm(content, title, {
      confirmButtonText: confirmText,
      cancelButtonText: cancelText,
      type,
      dangerouslyUseHTMLString: hasDetail,
      customClass: 'confirm-dialog',
      confirmButtonClass: type === 'error' ? 'el-button--danger' : 'el-button--primary'
    })
  },

  /**
   * 删除确认（危险操作）
   */
  confirmDelete(message, count = 1) {
    return this.confirm(message, {
      title: '删除确认',
      type: 'error',
      confirmText: '确认删除',
      detail: count > 1 ? `此操作将删除 ${count} 条记录，不可恢复` : '此操作不可恢复'
    })
  },

  /**
   * 通知（右上角，适合长时间停留）
   */
  notify(title, message, type = 'info', duration = 4500) {
    ElNotification({
      title,
      message,
      type,
      duration
    })
  },

  /**
   * 批量操作进度反馈
   */
  batchProgress(current, total, action = '处理') {
    if (current === total) {
      this.success(`${action}完成：共 ${total} 项`)
    } else if (current === 1) {
      this.info(`开始${action}：共 ${total} 项`)
    }
  }
}

export default feedback
