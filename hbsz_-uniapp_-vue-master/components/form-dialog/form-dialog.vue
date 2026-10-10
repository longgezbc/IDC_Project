<!--
  FormDialog 表单弹窗组件

  业务用途：
    统一展示新增/编辑表单的弹窗容器，提供遮罩、标题、可滚动内容区、底部操作按钮。
    替代各页面散落的 .popup-mask / .popup / .popup-title / .popup-actions / .popup-scroll 样式。
    组件本身不关心表单字段，由调用方通过默认插槽自定义表单内容。

  使用示例：
    <form-dialog
      v-model:visible="formVisible"
      :title="editMode ? '编辑客户' : '新增客户'"
      :submit-text="editMode ? '保存' : '创建'"
      :loading="saving"
      @submit="handleSave"
      @cancel="formVisible = false"
    >
      <view class="form-item">
        <text class="form-label">客户名称</text>
        <input class="form-input" v-model="form.name" />
      </view>
    </form-dialog>

  Props：
    - visible / v-model:visible: 弹窗显隐（支持 v-model:visible）
    - title: 弹窗标题
    - submitText: 提交按钮文案（默认"保存"）
    - cancelText: 取消按钮文案（默认"取消"）
    - loading: 提交中 loading 态（禁用按钮 + 显示 spinner）
    - maskClosable: 点击遮罩是否关闭（默认 false，避免误触丢失数据）

  Events：
    - update:visible: 显隐变化
    - submit: 点击提交按钮
    - cancel: 点击取消按钮

  Slots：
    - default: 表单主体内容（必填）
    - footer: 自定义底部按钮区（不传则使用默认双按钮）
-->
<template>
	<view v-if="visible" class="fd-mask" role="dialog" :aria-label="title" @click="onMaskClick">
		<view class="fd-popup" @click.stop>
			<!-- 标题栏 -->
			<view class="fd-header">
				<text class="fd-title">{{ title }}</text>
				<text
					v-if="closable"
					class="fd-close"
					role="button"
					aria-label="关闭"
					@click="onCancel"
				>×</text>
			</view>

			<!-- 内容区：可滚动 -->
			<scroll-view scroll-y class="fd-body">
				<slot />
			</scroll-view>

			<!-- 底部操作区 -->
			<view class="fd-footer">
				<slot name="footer">
					<button class="fd-btn fd-btn-cancel" :disabled="loading" @click="onCancel">{{ cancelText }}</button>
					<button class="fd-btn fd-btn-submit" :loading="loading" :disabled="loading" @click="onSubmit">{{ submitText }}</button>
				</slot>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'FormDialog',
		props: {
			/** 弹窗显隐（支持 v-model:visible） */
			visible: {
				type: Boolean,
				default: false
			},
			/** 弹窗标题 */
			title: {
				type: String,
				default: ''
			},
			/** 提交按钮文案 */
			submitText: {
				type: String,
				default: '保存'
			},
			/** 取消按钮文案 */
			cancelText: {
				type: String,
				default: '取消'
			},
			/** 提交中 loading 态 */
			loading: {
				type: Boolean,
				default: false
			},
			/** 点击遮罩是否关闭（默认 false，避免误触丢失数据） */
			maskClosable: {
				type: Boolean,
				default: false
			},
			/** 是否显示右上角关闭按钮 */
			closable: {
				type: Boolean,
				default: true
			}
		},
		methods: {
			/** 点击遮罩 */
			onMaskClick() {
				if (this.maskClosable) this.onCancel()
			},
			/** 取消 */
			onCancel() {
				if (this.loading) return
				this.$emit('update:visible', false)
				this.$emit('cancel')
			},
			/** 提交 */
			onSubmit() {
				if (this.loading) return
				this.$emit('submit')
			}
		}
	}
</script>

<style lang="scss" scoped>
	.fd-mask {
		position: fixed;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.45);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 999;
	}
	.fd-popup {
		width: 88%;
		max-width: 640rpx;
		max-height: 80vh;
		background: #FFFFFF;
		border-radius: 16rpx;
		display: flex;
		flex-direction: column;
		overflow: hidden;
	}

	/* 标题栏 */
	.fd-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 28rpx 32rpx 16rpx;
		border-bottom: 1rpx solid #EBEEF5;
		flex-shrink: 0;
	}
	.fd-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #303133;
	}
	.fd-close {
		font-size: 40rpx;
		color: #909399;
		line-height: 1;
		padding: 0 8rpx;
	}

	/* 内容区：可滚动 */
	.fd-body {
		flex: 1;
		padding: 24rpx 32rpx;
		overflow: auto;
	}

	/* 底部按钮区 */
	.fd-footer {
		display: flex;
		gap: 16rpx;
		padding: 16rpx 32rpx 24rpx;
		border-top: 1rpx solid #EBEEF5;
		flex-shrink: 0;
	}
	.fd-btn {
		flex: 1;
		height: 76rpx;
		line-height: 76rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		padding: 0;
		margin: 0;
		border: none;
	}
	.fd-btn-cancel {
		background: #F5F7FA;
		color: #5C6370;
	}
	.fd-btn-cancel[disabled] {
		opacity: 0.5;
	}
	.fd-btn-submit {
		background: #1890FF;
		color: #FFFFFF;
	}
	.fd-btn-submit[disabled] {
		opacity: 0.6;
	}
</style>
