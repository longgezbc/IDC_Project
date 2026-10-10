<!--
  EmptyState 空状态组件

  业务用途：
    统一展示列表/数据为空时的占位提示，替代各页面散落的 .empty / .empty-sm 样式。
    支持自定义图标、文案、操作按钮，适配 19+ 个页面复用。

  使用示例：
    <empty-state text="暂无项目" />
    <empty-state icon="📋" text="暂无任务" size="sm" />
    <empty-state text="暂无数据" action-text="去新增" @action="onCreate" />

  Props：
    - icon: 图标，可为 emoji 字符或图片路径（如 /static/icon/资料.svg）
    - text: 主文案
    - subText: 副文案（可选）
    - size: 尺寸，'sm' 紧凑模式（用于卡片内空态）、'lg' 默认（整页空态）
    - actionText: 操作按钮文案（可选，传入则显示按钮）
-->
<template>
	<view class="empty-state" :class="['size-' + size]">
		<view class="es-icon" aria-hidden="true">
			<image v-if="isImage" class="es-img" :src="icon" mode="aspectFit" />
			<text v-else class="es-emoji">{{ icon }}</text>
		</view>
		<text class="es-text">{{ text }}</text>
		<text v-if="subText" class="es-sub">{{ subText }}</text>
		<view v-if="actionText" class="es-action" role="button" hover-class="uni-hover" :hover-stay-time="80" @click="$emit('action')">
			{{ actionText }}
		</view>
	</view>
</template>

<script>
	export default {
		name: 'EmptyState',
		props: {
			/** 图标：emoji 字符或图片路径（以 / 或 http 开头视为图片） */
			icon: {
				type: String,
				default: '📭'
			},
			/** 主文案 */
			text: {
				type: String,
				default: '暂无数据'
			},
			/** 副文案 */
			subText: {
				type: String,
				default: ''
			},
			/** 尺寸：'lg' 整页空态（默认）、'sm' 卡片内紧凑空态 */
			size: {
				type: String,
				default: 'lg',
				validator: (v) => ['lg', 'sm'].includes(v)
			},
			/** 操作按钮文案（可选） */
			actionText: {
				type: String,
				default: ''
			}
		},
		computed: {
			/** 是否为图片路径 */
			isImage() {
				return /^(\/|https?:\/\/|data:)/.test(this.icon)
			}
		}
	}
</script>

<style lang="scss" scoped>
	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		color: #909399;
	}

	/* lg 模式：整页空态 */
	.size-lg {
		padding: 120rpx 40rpx;
	}
	.size-lg .es-icon {
		width: 120rpx;
		height: 120rpx;
		margin-bottom: 24rpx;
	}
	.size-lg .es-img {
		width: 120rpx;
		height: 120rpx;
	}
	.size-lg .es-emoji {
		font-size: 80rpx;
		line-height: 1;
	}
	.size-lg .es-text {
		font-size: 28rpx;
		color: #5C6370;
	}
	.size-lg .es-sub {
		font-size: 24rpx;
		color: #909399;
		margin-top: 8rpx;
		text-align: center;
	}

	/* sm 模式：卡片内紧凑空态 */
	.size-sm {
		padding: 60rpx 20rpx;
	}
	.size-sm .es-icon {
		width: 72rpx;
		height: 72rpx;
		margin-bottom: 12rpx;
	}
	.size-sm .es-img {
		width: 72rpx;
		height: 72rpx;
	}
	.size-sm .es-emoji {
		font-size: 48rpx;
		line-height: 1;
	}
	.size-sm .es-text {
		font-size: 24rpx;
		color: #909399;
	}

	.es-action {
		margin-top: 24rpx;
		padding: 14rpx 32rpx;
		font-size: 26rpx;
		font-weight: 500;
		color: #FFFFFF;
		background: linear-gradient(135deg, #1890FF, #096DD9);
		border-radius: 32rpx;
		box-shadow: 0 4rpx 12rpx rgba(24, 144, 255, 0.3);
	}
</style>
