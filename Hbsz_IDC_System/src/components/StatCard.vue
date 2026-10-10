<template>
  <!--
    工作台统计卡片组件（2026-08 大气·光晕升级）
    attrs 透传约定：@click / role / aria-label 等落到根元素；
    是否挂载 @click 决定手型光标与右侧箭头（不可点击的卡片不提供误导性交互暗示）。
    视觉：数字滚动入场（useCountUp，仅纯数字）+ 等宽数字字体 + hover 泛出
    与 tone 一致的彩色光晕（glow shadow），替代中性灰阴影。
  -->
  <div
    class="stat-card"
    :class="[`stat-card--${tone}`, { 'is-clickable': clickable }]"
  >
    <div
      class="stat-icon"
      :class="`stat-icon--${tone}`"
    >
      <el-icon :size="22">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="stat-content">
      <div class="stat-value num">
        {{ displayValue }}
      </div>
      <div class="stat-label">
        {{ label }}
      </div>
      <div
        v-if="sub"
        class="stat-sub"
      >
        {{ sub }}
      </div>
    </div>
    <el-icon
      v-if="clickable"
      class="stat-arrow"
    >
      <ArrowRight />
    </el-icon>
  </div>
</template>

<script setup>
/**
 * 工作台统计卡片（大气·光晕版）
 * 业务用途：Dashboard 数据概览区统一卡片（数字 + 标签 + 可选副文案）。
 * 视觉：软色调图标芯片 + 等宽数字（var(--font-numeric)）+ 数字滚动入场；
 *      hover 泛出 tone 色光晕（--glow-* Token），可点击卡片浮现右箭头。
 * 数字滚动：仅 Number 类型 value 滚动（useCountUp 内部判定），
 *          格式化字符串（如「¥1,234.5万」）直接展示，避免解析歧义。
 * 图标：通过 icon 传全局注册的 Element Plus 图标名（如 'Folder'）。
 */
import { computed, toRef, useAttrs } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { useCountUp } from '@/composables/useCountUp'

const props = defineProps({
  /** Element Plus 图标名（需已在 utils/icons.js 全局注册） */
  icon: { type: String, required: true },
  /** 色调：primary / success / warning / danger / purple */
  tone: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'success', 'warning', 'danger', 'purple'].includes(v)
  },
  /** 主数值（数字或已格式化字符串） */
  value: { type: [String, Number], default: 0 },
  /** 指标名称 */
  label: { type: String, default: '' },
  /** 副文案（如「进度填报」「已通过 3 · 已驳回 1」） */
  sub: { type: String, default: '' }
})

const attrs = useAttrs()
/** 父组件是否传入点击跳转（未传则不显示手型与箭头） */
const clickable = computed(() => typeof attrs.onClick === 'function')

/** 数字滚动：纯数字滚动入场，字符串原样直出 */
const { display: displayValue } = useCountUp(toRef(props, 'value'))
</script>

<style scoped>
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  height: 96px;
  padding: 20px;
  box-sizing: border-box;
  margin-bottom: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition: transform var(--dur-base) var(--ease-out-quart),
    box-shadow var(--dur-base), border-color var(--dur-base);
  /* 入场：带模糊淡入的上浮（Glow+Grain 动效 Token），延迟由父组件按列位设置 */
  animation: fadeUpBlur var(--dur-slow) var(--ease-out-quart) backwards;
}

/* 显式重置 global.css 旧版全局 .stat-card 规则（顶条/悬停上浮），避免叠加冲突 */
.stat-card::before {
  display: none;
}

.stat-card:hover {
  transform: none;
}

.is-clickable {
  cursor: pointer;
}

/* hover 光晕：泛出与 tone 一致的彩色辉光（替代中性灰阴影）+ 轻微抬升 */
.is-clickable:hover {
  transform: translateY(-2px);
}

.stat-card--primary.is-clickable:hover { box-shadow: var(--glow-primary); border-color: #c9e3fb; }
.stat-card--success.is-clickable:hover { box-shadow: var(--glow-success); border-color: #cdebb8; }
.stat-card--warning.is-clickable:hover { box-shadow: var(--glow-warning); border-color: #ffe3a6; }
.stat-card--danger.is-clickable:hover  { box-shadow: var(--glow-danger); border-color: #ffc9c5; }
.stat-card--purple.is-clickable:hover { box-shadow: var(--glow-purple); border-color: #dcc8f7; }

.is-clickable:active {
  transform: translateY(0);
}

/* 图标芯片：软底色 + 主题色图标（色板对齐 global.css 朴素按钮的 Ant 色阶） */
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  margin-right: 0;
  flex-shrink: 0;
}

.stat-icon--primary { background: #e6f7ff; color: #1890ff; }
.stat-icon--success { background: #f6ffed; color: #52c41a; }
.stat-icon--warning { background: #fffbe6; color: #faad14; }
.stat-icon--danger  { background: #fff1f0; color: #f5222d; }
.stat-icon--purple  { background: #f9f0ff; color: #722ed1; }

.stat-content {
  flex: 1;
  min-width: 0;
}

/* 等宽数字：mono 字体栈（--font-numeric）+ tabular-nums，
   数值变化时宽度稳定，多卡片对齐；
   （选择器带 .stat-content 前缀以覆盖 global.css 旧版 .stat-content .stat-value 的 26px 字号） */
.stat-content .stat-value {
  font-family: var(--font-numeric);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.stat-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 跳转暗示箭头：默认隐藏，hover 浮现 */
.stat-arrow {
  flex-shrink: 0;
  color: #b8c0cc;
  opacity: 0;
  transform: translateX(-6px);
  transition: opacity var(--dur-base), transform var(--dur-base), color var(--dur-base);
}

.is-clickable:hover .stat-arrow {
  opacity: 1;
  transform: none;
  color: var(--primary-color);
}
</style>
