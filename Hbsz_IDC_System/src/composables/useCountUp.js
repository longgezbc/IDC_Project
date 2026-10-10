import { onMounted, ref, watch } from 'vue'

/**
 * 数字滚动微交互（Glow+Grain 动效基建的运营工具克制版）
 *
 * 用途：StatCard / 横幅指标等纯数字指标的入场滚动。
 * 仅对 Number 类型生效；字符串值（如「¥1,234.5万」）原样直出，不做解析——
 * 运营工具数字滚动是氛围点缀而非数据可视化，格式化字符串强行滚动
 * 会引入解析歧义（货币符号/单位/千分位），得不偿失。
 *
 * 无障碍：respect prefers-reduced-motion —— 用户偏好减弱动效时
 * 跳过滚动直接显示终值（与 global.css 的动画降级一致）。
 *
 * @param {import('vue').Ref<number|string>} source 响应式数值源
 * @param {Object} options
 * @param {number} [options.duration=650] 滚动时长（ms）
 * @returns {{ display: import('vue').Ref<string> }} 滚动中的显示值
 */
export function useCountUp(source, { duration = 650 } = {}) {
  const display = ref(formatValue(source.value))

  function formatValue(v) {
    // 字符串/非有限数字直出，不参与滚动
    if (typeof v !== 'number' || !Number.isFinite(v)) return String(v ?? 0)
    return Number.isInteger(v) ? String(v) : v.toFixed(1)
  }

  function animateTo(target) {
    if (typeof target !== 'number' || !Number.isFinite(target)) {
      display.value = String(target ?? 0)
      return
    }
    // 减弱动效偏好：直接落定（动画时长趋零）
    const reduced =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (reduced || duration <= 0) {
      display.value = formatValue(target)
      return
    }
    const from = Number.parseFloat(display.value) || 0
    if (from === target) {
      display.value = formatValue(target)
      return
    }
    const start = performance.now()
    const tick = (now) => {
      const t = Math.min(1, (now - start) / duration)
      // easeOutQuart：先快后慢，符合数据落定直觉
      const eased = 1 - Math.pow(1 - t, 4)
      const current = from + (target - from) * eased
      display.value = formatValue(Math.round(current * 10) / 10)
      if (t < 1) requestAnimationFrame(tick)
      else display.value = formatValue(target)
    }
    requestAnimationFrame(tick)
  }

  onMounted(() => animateTo(source.value))

  watch(source, (val) => animateTo(val))

  return { display }
}
