<!--
  SkeletonCard 骨架屏组件

  业务用途：
    列表/卡片首次加载时的占位骨架，替代各页面散落的 .skeleton-card / .sk-line / @keyframes 样式。
    内置三行内容占位（标题/副标题/正文），支持自定义行数与宽度。

  使用示例：
    <skeleton-card v-if="loading" />
    <skeleton-card v-if="loading" :rows="4" />
    <skeleton-card v-if="loading" :rows="3" :widths="['240rpx', '160rpx', '100%']" />

  Props：
    - rows: 占位行数（默认 3）
    - widths: 各行宽度数组（rpx 或 100%），未指定时按默认规则生成
-->
<template>
	<view class="skeleton-card">
		<view
			v-for="(w, i) in widthList"
			:key="i"
			class="sk-line"
			:style="{ width: w }"
		></view>
	</view>
</template>

<script>
	export default {
		name: 'SkeletonCard',
		props: {
			/** 占位行数 */
			rows: {
				type: Number,
				default: 3
			},
			/**
			 * 各行宽度数组（rpx 或百分比）
			 * 未指定时按 [240rpx, 160rpx, 100%, 80%, 100%...] 规则循环生成
			 */
			widths: {
				type: Array,
				default: () => []
			}
		},
		computed: {
			widthList() {
				const defaults = ['240rpx', '160rpx', '100%', '80%', '100%']
				const list = []
				for (let i = 0; i < this.rows; i++) {
					if (this.widths[i]) {
						list.push(this.widths[i])
					} else {
						list.push(defaults[i % defaults.length])
					}
				}
				return list
			}
		}
	}
</script>

<style lang="scss" scoped>
	.skeleton-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 32rpx 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.sk-line {
		height: 28rpx;
		margin-bottom: 20rpx;
		border-radius: 8rpx;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
	}
	.sk-line:last-child {
		margin-bottom: 0;
	}
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}
</style>
