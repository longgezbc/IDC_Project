/**
 * 防抖与防重复提交工具
 *
 * 提供三种能力：
 *   - debounce：延迟合并高频调用，仅在停止触发后执行最后一次；
 *   - useSubmitGuard：组合式包装器，异步操作进行中阻止再次调用并暴露 loading 状态；
 *   - vSubmit：按钮指令，点击后自动置忙并禁用，异步完成前拦截重复点击。
 * 用于表单提交、按钮点击等场景，避免网络抖动或误操作导致的重复请求。
 * @module utils/submitGuard
 */
import { ref } from 'vue'

/**
 * 防抖函数
 *
 * 每次调用都会重置计时器，仅当停止触发超过 delay 后才执行一次，
 * 常用于搜索输入、窗口 resize 等高频场景以减少无效执行。
 *
 * 返回的包装函数附带 .cancel() 方法，用于在组件卸载时取消未触发的延迟调用，
 * 避免组件销毁后定时器仍执行回调导致"更新已卸载组件状态"的告警。
 * @param {Function} fn 原函数
 * @param {number} [delay=300] 延迟毫秒
 * @returns {Function & { cancel: () => void }} 包装后的防抖函数（附带 cancel 方法）
 */
export function debounce(fn, delay = 300) {
  let timer = null
  function debounced(...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
      timer = null
    }, delay)
  }
  // 提供 cancel 方法：组件 onUnmounted 时可调用以清理未触发的定时器
  debounced.cancel = function () {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }
  return debounced
}

/**
 * 防重复提交包装器
 * 在异步操作完成前阻止重复调用
 * @param {Function} fn 异步函数
 * @returns {[Function, Ref<boolean>]} [包装后的函数, loading状态]
 */
export function useSubmitGuard(fn) {
  const submitting = ref(false)
  const wrapped = async function (...args) {
    if (submitting.value) return
    submitting.value = true
    try {
      return await fn.apply(this, args)
    } finally {
      submitting.value = false
    }
  }
  return [wrapped, submitting]
}

/**
 * Vue 指令：v-submit
 * 自动禁用按钮并在异步操作完成前阻止重复点击
 * 用法：<el-button v-submit="handleSubmit">保存</el-button>
 *
 * 实现：在捕获点击时置忙、添加 is-loading 与 disabled；异步 handler 结束后（无论成败）复位，
 * 期间的重复点击通过 preventDefault/stopPropagation 拦截。
 */
export const vSubmit = {
  /**
   * 指令挂载钩子：为元素绑定带防重的点击处理器
   * @param {HTMLElement} el 指令绑定的按钮元素
   * @param {{ value: Function }} binding 指令绑定对象，value 为异步处理函数
   * @returns {void}
   */
  mounted(el, binding) {
    const handler = binding.value
    if (typeof handler !== 'function') return

    let submitting = false
    // 修复：将事件处理器命名并挂载到 el._vSubmitHandler，便于 unmounted 钩子中 removeEventListener 精确移除，避免匿名函数无法移除导致的内存泄漏
    el._vSubmitHandler = async (e) => {
      if (submitting) {
        e.preventDefault()
        e.stopPropagation()
        return
      }
      submitting = true
      el.classList.add('is-loading')
      el.setAttribute('disabled', 'true')
      try {
        await handler(e)
      } finally {
        submitting = false
        el.classList.remove('is-loading')
        el.removeAttribute('disabled')
      }
    }
    el.addEventListener('click', el._vSubmitHandler)
  },
  /**
   * 指令卸载钩子：移除 click 事件监听，释放引用
   * @param {HTMLElement} el 指令绑定的按钮元素
   * @returns {void}
   */
  // 修复：补充 unmounted 钩子，移除 mounted 中绑定的 click 监听器，防止组件销毁后仍持有引用导致内存泄漏
  unmounted(el) {
    if (el._vSubmitHandler) {
      el.removeEventListener('click', el._vSubmitHandler)
      delete el._vSubmitHandler
    }
  }
}
