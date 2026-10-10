<!--
  LoadMore 加载更多组件

  业务用途：
    统一展示列表底部的加载状态，替代各页面散落的 .load-more / .loading-tip / — 没有更多了 — 样式。
    支持三种状态：加载中、加载完成（无更多）、隐藏。适配 15+ 个分页列表页面复用。

  使用示例：
    <load-more :status="loadStatus" />
    // status: 'loading' | 'nomore' | 'hidden'

  Props：
    - status: 加载状态
      - 'loading'：显示加载中
      - 'nomore'：显示"没有更多了"
      - 'hidden'：隐藏组件
    - loadingText: 加载中文案
    - nomoreText: 没有更多文案
-->
<template>
	<view v-if="status !== 'hidden'" class="load-more" role="status" :aria-label="displayText">
		<view v-if="status === 'loading'" class="lm-loading">
			<view class="lm-spinner" aria-hidden="true"></view>
			<text class="lm-text">{{ loadingText }}</text>
		</view>
		<view v-else-if="status === 'nomore'" class="lm-nomore">
			<view class="lm-line"></view>
			<text class="lm-text">{{ nomoreText }}</text>
			<view class="lm-line"></view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'LoadMore',
		props: {
			/** 加载状态：'loading' 加载中 / 'nomore' 没有更多 / 'hidden' 隐藏 */
			status: {
				type: String,
				default: 'hidden',
				validator: (v) => ['loading', 'nomore', 'hidden'].includes(v)
			},
			/** 加载中文案 */
			loadingText: {
				type: String,
				default: '加载中...'
			},
			/** 没有更多文案 */
			nomoreText: {
				type: String,
				default: '没有更多了'
			}
		},
		computed: {
			displayText() {
				if (this.status === 'loading') return this.loadingText
				if (this.status === 'nomore') return this.nomoreText
				return ''
			}
		}
	}
</script>

<style lang="scss" scoped>
	.load-more {
		display: flex;
		justify-content: center;
		align-items: center;
		padding: 24rpx 0;
		min-height: 60rpx;
	}

	.lm-loading {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}
	.lm-spinner {
		width: 28rpx;
		height: 28rpx;
		border: 3rpx solid #EBEEF5;
		border-top-color: #1890FF;
		border-radius: 50%;
		animation: lm-spin 0.8s linear infinite;
	}
	@keyframes lm-spin {
		to { transform: rotate(360deg); }
	}

	.lm-nomore {
		display: flex;
		align-items: center;
		gap: 16rpx;
		width: 100%;
		padding: 0 80rpx;
		box-sizing: border-box;
	}
	.lm-line {
		flex: 1;
		height: 1rpx;
		background: linear-gradient(90deg, transparent, #DCDFE6, transparent);
	}

	.lm-text {
		font-size: 24rpx;
		color: #909399;
	}
</style>
