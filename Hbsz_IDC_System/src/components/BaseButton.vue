<template>
  <el-button
    :type="type"
    :size="size"
    :plain="plain"
    :round="round"
    :circle="circle"
    :link="link"
    :text="text"
    :disabled="disabled"
    :loading="loading"
    :icon="icon"
    :class="['base-btn', `base-btn--${variant}`]"
    v-bind="$attrs"
  >
    <el-icon
      v-if="iconName && !loading"
      class="base-btn__icon"
    >
      <component :is="iconName" />
    </el-icon>
    <span
      v-if="$slots.default"
      class="base-btn__text"
    ><slot /></span>
  </el-button>
</template>

<script setup>
/**
 * BaseButton 公共按钮组件
 * 统一封装 Element Plus 按钮，预设常用样式变体
 *
 * variant 变体说明：
 * - primary:      蓝色实心按钮（主要操作）
 * - plain:        朴素按钮（次要操作）
 * - link:         蓝色链接按钮（行内操作）
 * - text:         蓝色文字按钮（轻量操作）
 * - success:      绿色实心按钮（成功操作）
 * - warning:      橙色实心按钮（警告操作）
 * - danger:       红色实心按钮（危险操作）
 * - link-success: 绿色链接按钮
 * - link-warning: 橙色链接按钮
 * - link-danger:  红色链接按钮
 */
import { computed } from 'vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'plain', 'link', 'text', 'success', 'warning', 'danger', 'link-success', 'link-warning', 'link-danger'].includes(v)
  },
  size: {
    type: String,
    default: 'default',
    validator: (v) => ['large', 'default', 'small'].includes(v)
  },
  iconName: {
    type: [String, Object],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  round: {
    type: Boolean,
    default: false
  },
  circle: {
    type: Boolean,
    default: false
  }
})

/**
 * 将 variant 变体映射为 el-button 的原生 type
 * 链接类变体（link-*）沿用对应语义色，其余按语义色或默认 primary 处理。
 * @returns {string} el-button 的 type 值
 */
const type = computed(() => {
  if (props.variant === 'plain') return 'primary'
  if (props.variant === 'link') return 'primary'
  if (props.variant === 'text') return 'primary'
  if (props.variant === 'link-success') return 'success'
  if (props.variant === 'link-warning') return 'warning'
  if (props.variant === 'link-danger') return 'danger'
  if (props.variant === 'success') return 'success'
  if (props.variant === 'warning') return 'warning'
  if (props.variant === 'danger') return 'danger'
  return 'primary'
})

const plain = computed(() => props.variant === 'plain')
const link = computed(() => ['link', 'link-success', 'link-warning', 'link-danger'].includes(props.variant))
const text = computed(() => props.variant === 'text')
const icon = computed(() => null) // 使用 iconName 插槽方式
</script>

<style scoped>
/* P3 重构：颜色统一改用 CSS 变量。
 * primary 系列接入 Element Plus 主题变量（--el-color-primary 及其 light 变体），
 * 由 systemConfig.applyThemeColor 运行时更新，按钮可跟随"系统设置-主题色"实时换肤；
 * 回退值保持原 Ant Design 色板，变量未定义时视觉不变。
 * success/warning/danger 基色接入 global.css 项目变量（值与原字面量一致，零视觉变化）；
 * 其悬停/禁用浅色为 Ant Design 派生色阶，无对应变量，保留字面量。 */

/* 基础按钮样式 */
.base-btn {
  font-weight: 500;
  transition: all 0.2s;
}

.base-btn__icon {
  margin-right: 4px;
  vertical-align: middle;
}

/* primary 实心按钮 */
.base-btn--primary {
  background: var(--el-color-primary, #1890ff) !important;
  border-color: var(--el-color-primary, #1890ff) !important;
  color: #ffffff !important;
}

.base-btn--primary:hover {
  background: var(--el-color-primary-light-3, #40a9ff) !important;
  border-color: var(--el-color-primary-light-3, #40a9ff) !important;
  color: #ffffff !important;
}

/* plain 朴素按钮 */
.base-btn--plain {
  background: var(--el-color-primary-light-9, #e6f7ff) !important;
  border-color: var(--el-color-primary-light-5, #91d5ff) !important;
  color: var(--el-color-primary, #1890ff) !important;
}

.base-btn--plain:hover {
  background: var(--el-color-primary, #1890ff) !important;
  border-color: var(--el-color-primary, #1890ff) !important;
  color: #ffffff !important;
}

/* link 链接按钮 */
.base-btn--link {
  background: transparent !important;
  border-color: transparent !important;
  color: var(--el-color-primary, #1890ff) !important;
  padding: 0 4px;
}

.base-btn--link:hover {
  color: var(--el-color-primary-light-3, #40a9ff) !important;
  background: rgba(24, 144, 255, 0.06) !important;
}

/* link-success 绿色链接按钮 */
.base-btn--link-success {
  background: transparent !important;
  border-color: transparent !important;
  color: var(--success-color, #52c41a) !important;
  padding: 0 4px;
}

.base-btn--link-success:hover {
  color: #73d13d !important;
  background: rgba(82, 196, 26, 0.06) !important;
}

/* link-warning 橙色链接按钮 */
.base-btn--link-warning {
  background: transparent !important;
  border-color: transparent !important;
  color: var(--warning-color, #faad14) !important;
  padding: 0 4px;
}

.base-btn--link-warning:hover {
  color: #ffc53d !important;
  background: rgba(250, 173, 20, 0.06) !important;
}

/* link-danger 红色链接按钮 */
.base-btn--link-danger {
  background: transparent !important;
  border-color: transparent !important;
  color: var(--danger-color, #f5222d) !important;
  padding: 0 4px;
}

.base-btn--link-danger:hover {
  color: #ff4d4f !important;
  background: rgba(245, 34, 45, 0.06) !important;
}

/* text 文字按钮 */
.base-btn--text {
  background: transparent !important;
  border-color: transparent !important;
  color: var(--el-color-primary, #1890ff) !important;
}

.base-btn--text:hover {
  color: var(--el-color-primary-light-3, #40a9ff) !important;
  background: rgba(24, 144, 255, 0.06) !important;
}

/* success 成功按钮 */
.base-btn--success {
  background: var(--success-color, #52c41a) !important;
  border-color: var(--success-color, #52c41a) !important;
  color: #ffffff !important;
}

.base-btn--success:hover {
  background: #73d13d !important;
  border-color: #73d13d !important;
  color: #ffffff !important;
}

/* warning 警告按钮 */
.base-btn--warning {
  background: var(--warning-color, #faad14) !important;
  border-color: var(--warning-color, #faad14) !important;
  color: #ffffff !important;
}

.base-btn--warning:hover {
  background: #ffc53d !important;
  border-color: #ffc53d !important;
  color: #ffffff !important;
}

/* danger 危险按钮 */
.base-btn--danger {
  background: var(--danger-color, #f5222d) !important;
  border-color: var(--danger-color, #f5222d) !important;
  color: #ffffff !important;
}

.base-btn--danger:hover {
  background: #ff4d4f !important;
  border-color: #ff4d4f !important;
  color: #ffffff !important;
}

/* 禁用状态 - 提升可读性 */
.base-btn.is-disabled,
.base-btn.is-disabled:hover {
  cursor: not-allowed;
}

.base-btn--primary.is-disabled,
.base-btn--primary.is-disabled:hover {
  background: var(--el-color-primary-light-5, #a0c4ff) !important;
  border-color: var(--el-color-primary-light-5, #a0c4ff) !important;
  color: #ffffff !important;
  opacity: 0.85;
}

.base-btn--success.is-disabled,
.base-btn--success.is-disabled:hover {
  background: #b7eb8f !important;
  border-color: #b7eb8f !important;
  color: #ffffff !important;
  opacity: 0.85;
}

.base-btn--warning.is-disabled,
.base-btn--warning.is-disabled:hover {
  background: #ffd591 !important;
  border-color: #ffd591 !important;
  color: #ffffff !important;
  opacity: 0.85;
}

.base-btn--danger.is-disabled,
.base-btn--danger.is-disabled:hover {
  background: #ffa39e !important;
  border-color: #ffa39e !important;
  color: #ffffff !important;
  opacity: 0.85;
}

.base-btn--plain.is-disabled,
.base-btn--plain.is-disabled:hover {
  background: #f5f7fa !important;
  border-color: #e4e7ed !important;
  color: #c0c4cc !important;
  opacity: 0.8;
}

.base-btn--link.is-disabled,
.base-btn--link.is-disabled:hover,
.base-btn--text.is-disabled,
.base-btn--text.is-disabled:hover {
  background: transparent !important;
  border-color: transparent !important;
  color: var(--el-color-primary-light-5, #a0c4ff) !important;
  opacity: 0.6;
}

.base-btn--link-success.is-disabled,
.base-btn--link-success.is-disabled:hover {
  background: transparent !important;
  border-color: transparent !important;
  color: #b7eb8f !important;
  opacity: 0.6;
}

.base-btn--link-warning.is-disabled,
.base-btn--link-warning.is-disabled:hover {
  background: transparent !important;
  border-color: transparent !important;
  color: #ffd591 !important;
  opacity: 0.6;
}

.base-btn--link-danger.is-disabled,
.base-btn--link-danger.is-disabled:hover {
  background: transparent !important;
  border-color: transparent !important;
  color: #ffa39e !important;
  opacity: 0.6;
}

/* 尺寸调整 */
.base-btn--small {
  font-size: 12px;
}

.base-btn--large {
  font-size: 16px;
}
</style>
