<template>
  <!--
    粒子星网背景（登录页装饰层，2026-08 粒子场景设计）
    纯装饰：aria-hidden + pointer-events:none，鼠标事件挂在 window 上，
    不拦截前景表单交互；z-index 低于内容卡片，保证核心信息清晰。

    视觉规格（对应设计简报）：
    - 粒子直径 2-6px、透明度阶梯 0.15-0.6、低饱和蓝白色系（与 aurora Token 同源）
    - 细线连结：距离阈值内透明度随距离衰减（0.04-0.28）
    - 漂移：基础线性速度极低 + 8-15s 随机相位正弦摆动（宏观缓慢、微观有呼吸感）
    - Hover 聚合：光标 160px 半径内粒子受微弱吸引 + 连线增强，离开即恢复
    - 发光质感：光点以 lighter（screen）混合叠亮

    性能与降噪：
    - 粒子数按面积自适应（约每 12000px² 一个，上限 130）
    - 页面隐藏（document.hidden）暂停 rAF；resize 防抖 200ms
    - DPR 上限 2（高分屏不过度渲染）；连线用平方距离比较避免开方
    - prefers-reduced-motion：静态渲染一帧（保留星点、去除全部动效）
  -->
  <canvas
    ref="canvasRef"
    class="particle-canvas"
    aria-hidden="true"
  />
</template>

<script setup>
/**
 * 粒子星网背景组件
 * 业务用途：登录页（及其他深色叙事页）的氛围装饰层——漂浮粒子 + 连线网络，
 *           科技/星空语境；不承载任何业务信息。
 * 实现说明：单 canvas + requestAnimationFrame；状态与清理函数在 setup 顶层持有，
 *           卸载时完整移除（rAF / 事件监听）；父级容器需 position:relative，
 *           本组件铺满其背景层（CSS 已设 inset:0）。
 */
import { onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  /** 密度缩放（1 = 默认每 12000px² 一粒；仪表板等小区域可传 0.6） */
  density: { type: Number, default: 1 },
  /** 是否启用光标聚合交互 */
  interactive: { type: Boolean, default: true }
})

const canvasRef = ref(null)

/** 低饱和星点色板（与 global.css aurora Token 同源：蓝灰/青/浅紫） */
const PARTICLE_COLORS = ['#8ec8ff', '#7fd4ff', '#aeb6c2', '#b7a8f0']
/** 连线基色（深蓝白，透明度随距离动态叠加） */
const LINK_COLOR = [126, 160, 214]

// ---- 引擎状态（setup 顶层持有，便于卸载清理） ----
let rafId = 0
let resizeTimer = 0
let width = 0
let height = 0
let particles = []
let ctx = null
const pointer = { x: -9999, y: -9999 }

/** 生成单粒：位置随机、极慢基础漂移 + 8-15s 摆动相位 */
function makeParticle() {
  const swayPeriod = 8 + Math.random() * 7 // 8-15s 周期
  return {
    x: Math.random() * width,
    y: Math.random() * height,
    // 基础漂移：±0.04 px/frame（60fps 下约 ±2.4px/s，宏观缓慢）
    vx: (Math.random() - 0.5) * 0.08,
    vy: (Math.random() - 0.5) * 0.08,
    // 摆动：微幅正弦（幅度 px/frame），随机相位与周期
    swayAmp: 0.06 + Math.random() * 0.14,
    swayPhase: Math.random() * Math.PI * 2,
    swayOmega: (Math.PI * 2) / (swayPeriod * 60), // 转 rad/frame
    r: 1 + Math.random() * 2, // 半径 1-3px → 直径 2-6px
    alpha: 0.15 + Math.random() * 0.45, // 透明度阶梯 0.15-0.6
    color: PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)]
  }
}

/** 画布尺寸 + 粒子重建（DPR 适配，逻辑坐标统一为 CSS 像素） */
function setup(canvas) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const rect = canvas.parentElement.getBoundingClientRect()
  width = rect.width
  height = rect.height
  canvas.width = Math.round(width * dpr)
  canvas.height = Math.round(height * dpr)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  // 面积自适应密度，上限 130（两两连线 O(n²) 规模可控）
  const count = Math.min(130, Math.max(24, Math.floor(width * height / 12000 * props.density)))
  particles = Array.from({ length: count }, makeParticle)
}

/** 单帧绘制：距离连线 + 发光光点（lighter 叠亮） */
function draw() {
  ctx.clearRect(0, 0, width, height)
  const linkR = 130 // 连线阈值
  const linkR2 = linkR * linkR
  const hoverR2 = 160 * 160 // 光标聚合半径平方

  // ---- 连线层（普通混合，低透明细线）----
  ctx.lineWidth = 0.6
  for (let i = 0; i < particles.length; i++) {
    const a = particles[i]
    for (let j = i + 1; j < particles.length; j++) {
      const b = particles[j]
      const dx = a.x - b.x
      const dy = a.y - b.y
      const d2 = dx * dx + dy * dy
      if (d2 > linkR2) continue
      const t = 1 - Math.sqrt(d2) / linkR // 距离越近越实
      // 光标附近连线增强（局部聚合的视觉反馈）
      let boost = 0
      const px = (a.x + b.x) / 2 - pointer.x
      const py = (a.y + b.y) / 2 - pointer.y
      if (px * px + py * py < hoverR2) boost = 0.12
      const alpha = Math.min(0.6, 0.04 + t * 0.24 + boost)
      ctx.strokeStyle = `rgba(${LINK_COLOR[0]},${LINK_COLOR[1]},${LINK_COLOR[2]},${alpha})`
      ctx.beginPath()
      ctx.moveTo(a.x, a.y)
      ctx.lineTo(b.x, b.y)
      ctx.stroke()
    }
  }

  // ---- 光点层（screen/lighter 混合，发光质感）----
  ctx.globalCompositeOperation = 'lighter'
  for (const p of particles) {
    ctx.globalAlpha = p.alpha
    ctx.fillStyle = p.color
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.globalCompositeOperation = 'source-over'
  ctx.globalAlpha = 1
}

/** 状态推进：漂移 + 摆动 + 光标吸引 + 环绕 */
function step() {
  for (const p of particles) {
    const dx = pointer.x - p.x
    const dy = pointer.y - p.y
    const d2 = dx * dx + dy * dy
    // 光标聚合：半径内微弱吸引（0.03 步进，离开自然恢复）
    if (props.interactive && d2 < 160 * 160 && d2 > 4) {
      const d = Math.sqrt(d2)
      p.x += (dx / d) * 0.03
      p.y += (dy / d) * 0.03
    }
    p.swayPhase += p.swayOmega
    p.x += p.vx + Math.cos(p.swayPhase) * p.swayAmp
    p.y += p.vy + Math.sin(p.swayPhase) * p.swayAmp
    // 环绕（wrap）：漂出边界从对侧回来，避免反弹的生硬感
    if (p.x < -6) p.x = width + 6
    else if (p.x > width + 6) p.x = -6
    if (p.y < -6) p.y = height + 6
    else if (p.y > height + 6) p.y = -6
  }
}

function frame() {
  step()
  draw()
  rafId = requestAnimationFrame(frame)
}

/** 光标坐标转 canvas 逻辑坐标（挂在 window，不拦截前景交互） */
function onPointerMove(e) {
  const rect = canvasRef.value.getBoundingClientRect()
  pointer.x = e.clientX - rect.left
  pointer.y = e.clientY - rect.top
}

function onPointerLeave(e) {
  // mouseout 会随元素间移动冒泡触发；relatedTarget 为 null 才表示真正离开窗口
  if (e.relatedTarget === null) {
    pointer.x = -9999
    pointer.y = -9999
  }
}

function onResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    setup(canvasRef.value)
    draw() // 立即重绘一帧（reduced 模式下由此保持静态画面正确）
  }, 200)
}

/** 页面隐藏暂停 rAF，恢复时续跑（性能降噪） */
function onVisibility() {
  if (document.hidden) {
    cancelAnimationFrame(rafId)
  } else {
    rafId = requestAnimationFrame(frame)
  }
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  ctx = canvas.getContext('2d')
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  setup(canvas)
  if (reduced) {
    // 停动模式：静态星图（保留氛围，去除全部动效与交互）
    draw()
  } else {
    rafId = requestAnimationFrame(frame)
    if (props.interactive) {
      window.addEventListener('mousemove', onPointerMove, { passive: true })
      window.addEventListener('mouseout', onPointerLeave, { passive: true })
    }
    document.addEventListener('visibilitychange', onVisibility)
  }
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  clearTimeout(resizeTimer)
  window.removeEventListener('mousemove', onPointerMove)
  window.removeEventListener('mouseout', onPointerLeave)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.particle-canvas {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}
</style>
