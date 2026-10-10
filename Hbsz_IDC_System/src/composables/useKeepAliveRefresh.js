/**
 * keep-alive 页面激活刷新组合式函数
 *
 * 背景：被 <keep-alive> 缓存的组件首次挂载时，onMounted 与 onActivated 都会
 * 触发（onMounted 先），若两处都调用 fetchList 会导致请求双发。P2 修复在
 * 7 个列表页各自添加了 isMounted 标志守卫，样板代码逐字重复，此处统一抽取。
 *
 * 用法：
 *   onMounted(() => { fetchList() })          // 首次加载（保留在调用方）
 *   useKeepAliveRefresh(() => { fetchList() }) // 后续激活刷新
 *
 * 与原 isMounted 守卫的语义差异：本实现以"onActivated 是否触发过"为准，
 * 不依赖 onMounted 是否执行完毕，规避了异步 onMounted 未完成时提前返回
 * 页面导致守卫失效的边界情况。
 * @module composables/useKeepAliveRefresh
 */
import { onActivated } from 'vue'

/**
 * 注册 keep-alive 重新激活时的刷新回调（首次激活自动跳过）
 * @param {Function} refreshFn 重新激活时执行的刷新函数
 * @returns {void}
 */
export function useKeepAliveRefresh(refreshFn) {
  let activatedOnce = false
  onActivated(() => {
    if (!activatedOnce) {
      activatedOnce = true
      return
    }
    if (typeof refreshFn === 'function') refreshFn()
  })
}
