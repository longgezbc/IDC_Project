<!--
  SearchBar 搜索栏组件

  业务用途：
    统一展示列表页顶部的搜索输入框，替代各页面散落的 .search-bar / .search-input-wrap 样式。
    支持防抖搜索、清空按钮、右侧操作插槽（如"新建""上传"按钮）。
    适配 10+ 个列表页面复用。

  使用示例：
    <search-bar v-model="keyword" placeholder="搜索项目名称" @search="onSearch" />
    <search-bar v-model="keyword" placeholder="搜索任务" @search="onSearch">
      <view class="add-btn" @click="onCreate">+ 新建</view>
    </search-bar>

  Props：
    - modelValue / v-model: 双向绑定的关键字
    - placeholder: 占位文案
    - debounce: 防抖延迟（毫秒），0 表示不防抖
    - ariaLabel: 无障碍标签

  Events：
    - update:modelValue / v-model: 关键字变化
    - search: 用户确认搜索（回车）
    - clear: 用户清空关键字

  Slots：
    - default: 右侧操作区（如新建按钮）
-->
<template>
	<view class="search-bar">
		<view class="search-input-wrap">
			<image class="search-icon" src="/static/icon/搜索.svg" mode="aspectFit" aria-hidden="true" />
			<input
				class="search-input"
				type="text"
				:value="modelValue"
				:placeholder="placeholder"
				:placeholder-class="'ph'"
				confirm-type="search"
				:aria-label="ariaLabel || placeholder"
				@input="onInput"
				@confirm="onConfirm"
			/>
			<text
				v-if="modelValue"
				class="search-clear"
				role="button"
				aria-label="清空搜索"
				@click="onClear"
			>×</text>
		</view>
		<!-- 右侧操作区插槽：用于放置"新建""上传"等按钮 -->
		<slot />
	</view>
</template>

<script>
	export default {
		name: 'SearchBar',
		props: {
			/** 关键字（支持 v-model） */
			modelValue: {
				type: String,
				default: ''
			},
			/** 占位文案 */
			placeholder: {
				type: String,
				default: '搜索...'
			},
			/** 防抖延迟（毫秒），0 表示不防抖 */
			debounce: {
				type: Number,
				default: 0
			},
			/** 无障碍标签 */
			ariaLabel: {
				type: String,
				default: ''
			}
		},
		data() {
			return {
				timer: null
			}
		},
		// 页面卸载时清理防抖定时器（避免在已销毁页面上执行回调）
		beforeUnmount() {
			if (this.timer) {
				clearTimeout(this.timer)
				this.timer = null
			}
		},
		methods: {
			/** 输入事件：更新 v-model，可选防抖 */
			onInput(e) {
				const val = e.detail.value
				this.$emit('update:modelValue', val)
				// 防抖模式：延迟触发 search 事件
				if (this.debounce > 0) {
					if (this.timer) clearTimeout(this.timer)
					this.timer = setTimeout(() => {
						this.$emit('search', val)
					}, this.debounce)
				}
			},
			/** 确认搜索（回车） */
			onConfirm(e) {
				this.$emit('search', e.detail.value)
			},
			/** 清空关键字 */
			onClear() {
				this.$emit('update:modelValue', '')
				this.$emit('clear')
				// 清空后立即触发一次空关键字搜索，便于父组件刷新列表
				this.$emit('search', '')
			}
		}
	}
</script>

<style lang="scss" scoped>
	/* UI 布局优化：
	   - padding 改为上下间距 8rpx，左右由父级控制（避免与 .page padding 叠加产生双边距）
	   - 移除 border-bottom，避免与列表卡片视觉割裂；如需分隔可由父级添加 */
	.search-bar {
		display: flex;
		align-items: center;
		gap: 16rpx;
		padding: 8rpx 0 16rpx;
	}
	.search-input-wrap {
		flex: 1;
		display: flex;
		align-items: center;
		background: #FFFFFF;
		border-radius: 32rpx;
		padding: 0 24rpx;
		height: 72rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.search-icon {
		width: 28rpx;
		height: 28rpx;
		margin-right: 12rpx;
		flex-shrink: 0;
		opacity: 0.5;
	}
	.search-input {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
		height: 72rpx;
		line-height: 72rpx;
	}
	.ph {
		color: #767E8A;
		font-size: 26rpx;
	}
	.search-clear {
		font-size: 32rpx;
		color: #767E8A;
		padding: 0 8rpx;
		line-height: 1;
		flex-shrink: 0;
	}
</style>
