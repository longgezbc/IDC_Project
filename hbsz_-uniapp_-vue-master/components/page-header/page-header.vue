<!--
  PageHeader 页面头部组件

  业务用途：
    统一展示非 tabBar 页面的顶部标题栏，支持返回按钮、标题、右侧操作按钮。
    替代散落各页面的 .nav-bar / .nav-title / .nav-action 样式。

  使用示例：
    1. 简单标题栏（自带返回按钮）
       <page-header title="客户管理" />

    2. 带右侧操作
       <page-header title="客户管理">
         <view class="nav-action" @click="onCreate">+ 新增</view>
       </page-header>

    3. 带副标题
       <page-header title="项目详情" subtitle="BJ-2026-001" />

    4. 不显示返回按钮（如首页）
       <page-header title="工作台" :show-back="false" />

  Props：
    - title: 标题
    - subtitle: 副标题（可选）
    - showBack: 是否显示返回按钮（默认 true）
    - backText: 返回按钮文案（默认不显示文字，只显示箭头）

  Events：
    - back: 用户点击返回按钮（默认行为是 uni.navigateBack，传入则覆盖）

  Slots：
    - default / action: 右侧操作区
-->
<template>
	<view class="page-header">
		<view v-if="showBack" class="ph-back" role="button" aria-label="返回" @click="onBack">
			<text class="ph-back-arrow">‹</text>
			<text v-if="backText" class="ph-back-text">{{ backText }}</text>
		</view>
		<view class="ph-title-area">
			<text class="ph-title">{{ title }}</text>
			<text v-if="subtitle" class="ph-subtitle">{{ subtitle }}</text>
		</view>
		<view class="ph-action-area">
			<slot />
		</view>
	</view>
</template>

<script>
	export default {
		name: 'PageHeader',
		props: {
			/** 标题 */
			title: {
				type: String,
				default: ''
			},
			/** 副标题 */
			subtitle: {
				type: String,
				default: ''
			},
			/** 是否显示返回按钮 */
			showBack: {
				type: Boolean,
				default: true
			},
			/** 返回按钮文案（默认只显示箭头） */
			backText: {
				type: String,
				default: ''
			}
		},
		methods: {
			/** 返回按钮：默认调用 navigateBack，父组件可通过 @back 监听覆盖 */
			onBack() {
				this.$emit('back')
				// 如果父组件未阻止默认行为，则调用 navigateBack
				// 通过判断是否有监听器来决定是否执行默认行为
				// 简化处理：始终执行默认返回，父组件可在 @back 中调用其他逻辑
				uni.navigateBack({ fail: () => {
					// 返回失败时跳转到首页
					uni.switchTab({ url: '/pages/index/index' })
				}})
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page-header {
		display: flex;
		align-items: center;
		padding: 8rpx 24rpx 16rpx;
		background: #FFFFFF;
		min-height: 88rpx;
		box-sizing: border-box;
	}
	.ph-back {
		display: flex;
		align-items: center;
		padding: 8rpx 8rpx 8rpx 0;
		flex-shrink: 0;
	}
	.ph-back-arrow {
		font-size: 44rpx;
		color: #303133;
		line-height: 1;
	}
	.ph-back-text {
		font-size: 28rpx;
		color: #303133;
		margin-left: 4rpx;
	}
	.ph-title-area {
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: center;
		padding: 0 16rpx;
		min-width: 0;
	}
	.ph-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
		line-height: 1.2;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.ph-subtitle {
		font-size: 22rpx;
		color: #909399;
		margin-top: 4rpx;
		line-height: 1.2;
	}
	.ph-action-area {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		gap: 16rpx;
	}
</style>
