<!--
  通知偏好设置页
  业务用途：当前登录用户自助配置通知偏好（通知渠道 + 通知内容），
           控制各类业务通知的接收开关。
  数据来源：userApi.getNotificationSettings（初始化）、
           userApi.updateNotificationSettings（保存）。
  导航特性：非 tabBar 页；保存成功后 navigateBack。
  分组展示：渠道 / 内容 两组卡片。
-->
<template>
	<view class="page">
		<!-- 顶部标题说明 -->
		<view class="page-header">
			<view class="page-title">通知偏好</view>
			<view class="page-desc">配置你希望接收的通知类型，可按渠道和内容分别设置</view>
		</view>

		<!-- 通知渠道卡片 -->
		<view class="card">
			<view class="card-title">通知渠道</view>
			<view class="card-desc">选择接收通知的方式</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">站内通知</view>
					<view class="switch-sub">在应用内消息中心显示</view>
				</view>
				<switch
					:checked="form.pushNotify"
					color="#1890FF"
					aria-label="站内通知开关"
					@change="onSwitchChange('pushNotify', $event)"
				/>
			</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">邮件通知</view>
					<view class="switch-sub">发送邮件到绑定邮箱</view>
				</view>
				<switch
					:checked="form.emailNotify"
					color="#1890FF"
					aria-label="邮件通知开关"
					@change="onSwitchChange('emailNotify', $event)"
				/>
			</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">短信通知</view>
					<view class="switch-sub">发送短信到绑定手机号</view>
				</view>
				<switch
					:checked="form.smsNotify"
					color="#1890FF"
					aria-label="短信通知开关"
					@change="onSwitchChange('smsNotify', $event)"
				/>
			</view>
		</view>

		<!-- 通知内容卡片 -->
		<view class="card">
			<view class="card-title">通知内容</view>
			<view class="card-desc">选择需要通知的业务事件</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">进度审核通知</view>
					<view class="switch-sub">进度提交审核结果反馈</view>
				</view>
				<switch
					:checked="form.progressReviewNotify"
					color="#1890FF"
					aria-label="进度审核通知开关"
					@change="onSwitchChange('progressReviewNotify', $event)"
				/>
			</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">任务到期通知</view>
					<view class="switch-sub">任务临近截止时间提醒</view>
				</view>
				<switch
					:checked="form.taskDueNotify"
					color="#1890FF"
					aria-label="任务到期通知开关"
					@change="onSwitchChange('taskDueNotify', $event)"
				/>
			</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">文档更新通知</view>
					<view class="switch-sub">项目资料新增或变更提醒</view>
				</view>
				<switch
					:checked="form.documentUpdateNotify"
					color="#1890FF"
					aria-label="文档更新通知开关"
					@change="onSwitchChange('documentUpdateNotify', $event)"
				/>
			</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">项目归档通知</view>
					<view class="switch-sub">项目状态变更为归档提醒</view>
				</view>
				<switch
					:checked="form.projectArchiveNotify"
					color="#1890FF"
					aria-label="项目归档通知开关"
					@change="onSwitchChange('projectArchiveNotify', $event)"
				/>
			</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">日报通知</view>
					<view class="switch-sub">每日工作汇总推送</view>
				</view>
				<switch
					:checked="form.dailyReportNotify"
					color="#1890FF"
					aria-label="日报通知开关"
					@change="onSwitchChange('dailyReportNotify', $event)"
				/>
			</view>

			<view class="switch-item">
				<view class="switch-info">
					<view class="switch-label">任务逾期通知</view>
					<view class="switch-sub">任务超过截止时间提醒</view>
				</view>
				<switch
					:checked="form.taskOverdueNotify"
					color="#1890FF"
					aria-label="任务逾期通知开关"
					@change="onSwitchChange('taskOverdueNotify', $event)"
				/>
			</view>
		</view>

		<!-- 保存按钮 -->
		<view class="action-area">
			<button class="btn-primary" :loading="submitting" :disabled="submitting" @click="handleSave">保存设置</button>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { userApi } from '@/api/user'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				submitting: false,        // 保存中标记（防重复提交）
				loaded: false,            // 设置是否已加载完成（避免初始化时 switch 触发 change 提交）
				form: {
					// 通知渠道
					pushNotify: false,           // 站内通知
					emailNotify: false,          // 邮件通知
					smsNotify: false,            // 短信通知
					// 通知内容
					progressReviewNotify: false, // 进度审核通知
					taskDueNotify: false,        // 任务到期通知
					documentUpdateNotify: false, // 文档更新通知
					projectArchiveNotify: false, // 项目归档通知
					dailyReportNotify: false,    // 日报通知
					taskOverdueNotify: false     // 任务逾期通知
				}
			}
		},
		onLoad() {
			this.loadSettings()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			/** 加载通知偏好设置，回填表单 */
			async loadSettings() {
				uni.showLoading({ title: '加载中', mask: true })
				try {
					const data = await userApi.getNotificationSettings()
					this.fillForm(data || {})
				} catch (e) {
					console.warn('load notification settings error', e)
				} finally {
					uni.hideLoading()
					this.loaded = true
				}
			},
			/**
			 * 用接口返回的数据回填表单
			 * 后端可能返回 boolean 或 0/1，统一转为 boolean
			 */
			fillForm(data) {
				const keys = Object.keys(this.form)
				keys.forEach((k) => {
					if (data[k] !== undefined && data[k] !== null) {
						this.form[k] = !!data[k]
					}
				})
			},
			/**
			 * switch 变化处理：仅更新本地状态，最终一次性提交保存
			 * @param {string} key form 字段名
			 * @param {Object} e switch change 事件
			 */
			onSwitchChange(key, e) {
				this.form[key] = !!(e && e.detail && e.detail.value)
			},
			/** 保存通知偏好（防重复提交） */
			async handleSave() {
				if (this.submitting) return
				this.submitting = true
				uni.showLoading({ title: '保存中', mask: true })
				try {
					await userApi.updateNotificationSettings({ ...this.form })
					uni.hideLoading()
					uni.showToast({ title: '保存成功', icon: 'success' })
					this.navBackTimer = setTimeout(() => uni.navigateBack(), 600)
				} catch (e) {
					uni.hideLoading()
					console.warn('save notification settings error', e)
				} finally {
					this.submitting = false
				}
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		background: #F5F7FA;
		padding: 24rpx 24rpx 40rpx;
	}

	/* 顶部标题说明 */
	.page-header {
		padding: 8rpx 4rpx 20rpx;
	}
	.page-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 8rpx;
	}
	.page-desc {
		font-size: 24rpx;
		color: #5C6370;
		line-height: 1.5;
	}

	/* 卡片 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 24rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.card-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 6rpx;
	}
	.card-desc {
		font-size: 22rpx;
		color: #767E8A;
		margin-bottom: 16rpx;
	}

	/* switch 行 */
	.switch-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 24rpx 0;
		border-top: 1rpx solid #F5F7FA;
		&:first-of-type { border-top: none; }
	}
	.switch-info {
		flex: 1;
		margin-right: 24rpx;
	}
	.switch-label {
		font-size: 28rpx;
		color: #303133;
		margin-bottom: 4rpx;
	}
	.switch-sub {
		font-size: 22rpx;
		color: #767E8A;
		line-height: 1.4;
	}

	/* 保存按钮 */
	.action-area {
		padding: 16rpx 0 0;
	}
	.btn-primary {
		width: 100%;
		height: 88rpx;
		line-height: 88rpx;
		font-size: 30rpx;
		border-radius: 12rpx;
		border: none;
		margin: 0;
		background: #1890FF;
		color: #FFFFFF;
		&::after { border: none; }
		&[disabled] { background: #B3D9FF; color: #FFFFFF; }
	}

	.bottom-safe {
		height: calc(40rpx + env(safe-area-inset-bottom));
	}
</style>
