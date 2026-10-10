<!--
  ConfirmDialog 确认弹窗组件

  业务用途：
    替代散落各页面的 uni.showModal 调用，提供与小程序原生 modal 一致但样式可定制的确认弹窗。
    支持危险操作样式（红色确认按钮，用于删除/退出等场景）。

  使用示例：
    <confirm-dialog
      v-model:visible="confirmVisible"
      title="确认删除"
      content="删除后不可恢复，确定删除客户「张三」吗？"
      confirm-text="删除"
      danger
      @confirm="onConfirm"
    />

  Props：
    - visible / v-model:visible: 显隐
    - title: 标题
    - content: 内容文案
    - confirmText: 确认按钮文案（默认"确定"）
    - cancelText: 取消按钮文案（默认"取消"）
    - danger: 是否为危险操作（红色确认按钮）
    - maskClosable: 点击遮罩是否关闭（默认 true）

  Events：
    - update:visible
    - confirm: 用户点击确认
    - cancel: 用户点击取消
-->
<template>
	<view v-if="visible" class="cd-mask" role="dialog" :aria-label="title" @click="onMaskClick">
		<view class="cd-popup" @click.stop>
			<view v-if="title" class="cd-title">{{ title }}</view>
			<view class="cd-content">{{ content }}</view>
			<view class="cd-actions">
				<button class="cd-btn cd-btn-cancel" @click="onCancel">{{ cancelText }}</button>
				<button
					class="cd-btn cd-btn-confirm"
					:class="{ 'is-danger': danger }"
					:loading="loading"
					:disabled="loading"
					@click="onConfirm"
				>{{ confirmText }}</button>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'ConfirmDialog',
		props: {
			visible: {
				type: Boolean,
				default: false
			},
			title: {
				type: String,
				default: '提示'
			},
			content: {
				type: String,
				default: ''
			},
			confirmText: {
				type: String,
				default: '确定'
			},
			cancelText: {
				type: String,
				default: '取消'
			},
			/** 是否为危险操作（红色确认按钮） */
			danger: {
				type: Boolean,
				default: false
			},
			/** 提交中 loading 态 */
			loading: {
				type: Boolean,
				default: false
			},
			maskClosable: {
				type: Boolean,
				default: true
			}
		},
		methods: {
			onMaskClick() {
				if (this.maskClosable) this.onCancel()
			},
			onCancel() {
				if (this.loading) return
				this.$emit('update:visible', false)
				this.$emit('cancel')
			},
			onConfirm() {
				if (this.loading) return
				this.$emit('confirm')
			}
		}
	}
</script>

<style lang="scss" scoped>
	.cd-mask {
		position: fixed;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.45);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 1000;
	}
	.cd-popup {
		width: 76%;
		max-width: 560rpx;
		background: #FFFFFF;
		border-radius: 16rpx;
		overflow: hidden;
	}
	.cd-title {
		text-align: center;
		font-size: 32rpx;
		font-weight: 600;
		color: #303133;
		padding: 32rpx 32rpx 12rpx;
	}
	.cd-content {
		text-align: center;
		font-size: 28rpx;
		color: #5C6370;
		padding: 12rpx 32rpx 32rpx;
		line-height: 1.5;
	}
	.cd-actions {
		display: flex;
		border-top: 1rpx solid #EBEEF5;
	}
	.cd-btn {
		flex: 1;
		height: 96rpx;
		line-height: 96rpx;
		font-size: 30rpx;
		border: none;
		border-radius: 0;
		background: #FFFFFF;
		padding: 0;
		margin: 0;
	}
	.cd-btn::after {
		border: none;
	}
	.cd-btn-cancel {
		color: #5C6370;
		border-right: 1rpx solid #EBEEF5;
	}
	.cd-btn-confirm {
		color: #1890FF;
	}
	.cd-btn-confirm.is-danger {
		color: #FF4D4F;
	}
	.cd-btn[disabled] {
		opacity: 0.5;
	}
</style>
