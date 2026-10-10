<!--
  StatusTag 状态标签组件

  业务用途：
    统一展示项目状态/任务状态/优先级标签，避免每个页面重复实现 statusStyle + status-tag 样式。
    配合增强后的 statusMixin 使用，开箱即用。

  使用示例：
    <status-tag :status="project.status" type="project" />
    <status-tag :status="task.status" type="task" />
    <status-tag :text="project.priority" type="priority" />
    <status-tag text="自定义" color="#722ED1" bg="#F9F0FF" />

  Props：
    - status: 状态值（项目/任务状态中文值，如 '进行中'）
    - text:   标签文本（优先使用，未传时按 status 解析）
    - type:   类型：'project'（默认）/ 'task' / 'priority' / 'custom'
    - color:  自定义文字色（type='custom' 时使用）
    - bg:     自定义背景色（type='custom' 时使用）
    - size:   尺寸：'sm'（默认，行内小标签）/ 'lg'（大标签）
-->
<template>
	<view v-if="displayText" class="status-tag" :class="['size-' + size]" :style="tagStyle">
		<text>{{ displayText }}</text>
	</view>
</template>

<script>
	import { getProjectStatus, getTaskStatus, getPriority } from '@/utils/format'

	export default {
		name: 'StatusTag',
		props: {
			/** 状态值（项目/任务状态中文值） */
			status: {
				type: String,
				default: ''
			},
			/** 标签文本（优先于 status 自动解析） */
			text: {
				type: String,
				default: ''
			},
			/**
			 * 类型：决定如何解析 status
			 * - 'project'：项目状态（进行中/已完成/已归档/暂停/延期）
			 * - 'task'：任务状态（未开始/进行中/已完成/已延期/已暂停）
			 * - 'priority'：优先级（高/中/低）
			 * - 'custom'：自定义，配合 color/bg props
			 */
			type: {
				type: String,
				default: 'project',
				validator: (v) => ['project', 'task', 'priority', 'custom'].includes(v)
			},
			/** 自定义文字色（type='custom' 时生效） */
			color: {
				type: String,
				default: ''
			},
			/** 自定义背景色（type='custom' 时生效） */
			bg: {
				type: String,
				default: ''
			},
			/** 尺寸：'sm' 小标签 / 'lg' 大标签 */
			size: {
				type: String,
				default: 'sm',
				validator: (v) => ['sm', 'lg'].includes(v)
			}
		},
		computed: {
			/** 解析后的展示文本 */
			displayText() {
				if (this.text) return this.text
				if (!this.status) return ''
				if (this.type === 'project') return getProjectStatus(this.status).text
				if (this.type === 'task') return getTaskStatus(this.status).text
				if (this.type === 'priority') return getPriority(this.status).text
				return this.status
			},
			/** 解析后的样式 */
			tagStyle() {
				// 自定义类型直接使用传入的颜色
				if (this.type === 'custom') {
					return `color:${this.color || '#5C6370'};background:${this.bg || '#F4F4F5'};`
				}
				// 其他类型从映射表查询
				let cfg = { color: '#5C6370', bg: '#F4F4F5' }
				if (this.type === 'project' && this.status) {
					cfg = getProjectStatus(this.status)
				} else if (this.type === 'task' && this.status) {
					const t = getTaskStatus(this.status)
					const bgMap = {
						'#1890FF': '#E6F7FF',
						'#52C41A': '#F6FFED',
						'#909399': '#F4F4F5',
						'#FF4D4F': '#FFF1F0',
						'#FAAD14': '#FFFBE6'
					}
					cfg = { color: t.color, bg: bgMap[t.color] || '#F4F4F5' }
				} else if (this.type === 'priority' && this.status) {
					const p = getPriority(this.status)
					const bgMap = {
						'#FF4D4F': '#FFF1F0',
						'#FAAD14': '#FFFBE6',
						'#52C41A': '#F6FFED'
					}
					cfg = { color: p.color, bg: bgMap[p.color] || '#F4F4F5' }
				}
				return `color:${cfg.color};background:${cfg.bg};`
			}
		}
	}
</script>

<style lang="scss" scoped>
	.status-tag {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: 8rpx;
		white-space: nowrap;
	}

	/* sm：行内小标签 */
	.size-sm {
		padding: 4rpx 12rpx;
		font-size: 20rpx;
		line-height: 1.4;
	}

	/* lg：大标签（卡片头部等） */
	.size-lg {
		padding: 8rpx 20rpx;
		font-size: 24rpx;
		line-height: 1.4;
		border-radius: 12rpx;
	}
</style>
