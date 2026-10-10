<!--
  登录页
  业务用途：项目负责人账号登录入口，含品牌展示、用户名/密码输入、记住我。
  数据来源：userStore.login 调用 authApi.login 完成鉴权。
  导航特性：非 tabBar 页；登录成功后 reLaunch 到首页。
-->
<template>
	<view class="login-page">
		<!-- 顶部品牌区 -->
		<view class="brand" :style="{ paddingTop: 'calc(' + statusBarHeight + 'px + 40rpx)' }">
			<view class="brand-bg"></view>
			<view class="brand-content">
				<view class="logo" role="img" aria-label="IDC项目管家 Logo">
				<image class="logo-img" src="/static/logo.jpg" mode="aspectFill" />
			</view>
				<view class="brand-title">IDC项目管家</view>
				<view class="brand-subtitle">数据中心综合布线项目管理系统</view>
			</view>
		</view>

		<!-- 表单区 -->
		<view class="form-wrap">
			<view class="form-card">
				<view class="form-title">账号登录</view>
				<view class="form-subtitle">项目负责人移动工作台</view>

				<!-- 用户名 -->
				<view class="field" :class="{ 'field-focus': focusField === 'username' }">
					<view class="field-icon" aria-hidden="true"><image class="svg-icon" src="/static/icon/我的.svg" mode="aspectFit" /></view>
					<input
					class="field-input"
					v-model="form.username"
					aria-label="用户名"
					placeholder="请输入用户名"
						placeholder-class="ph"
						maxlength="50"
						@focus="focusField = 'username'"
						@blur="focusField = ''"
					/>
				</view>

				<!-- 密码 -->
			<view class="field" :class="{ 'field-focus': focusField === 'password' }">
				<view class="field-icon" aria-hidden="true"><image class="svg-icon" src="/static/icon/密码.svg" mode="aspectFit" /></view>
					<input
					class="field-input"
					v-model="form.password"
					aria-label="密码"
					:password="!showPassword"
						placeholder="请输入密码"
						placeholder-class="ph"
						maxlength="50"
						@focus="focusField = 'password'"
						@blur="focusField = ''"
						@confirm="handleLogin"
					/>
					<text class="field-action" role="button" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</text>
				</view>

				<!-- 验证码 -->
			<view class="captcha-row">
				<view class="field field-captcha" :class="{ 'field-focus': focusField === 'captcha' }">
					<view class="field-icon" aria-hidden="true"><image class="svg-icon" src="/static/icon/密码.svg" mode="aspectFit" /></view>
					<input
						class="field-input"
						v-model="form.captcha"
						aria-label="验证码答案"
						placeholder="请输入图片中字符"
						placeholder-class="ph"
						maxlength="4"
						@focus="focusField = 'captcha'"
						@blur="focusField = ''"
					/>
				</view>
				<view class="captcha-question" @click="refreshCaptcha" hover-class="captcha-question-hover">
					<text v-if="captchaLoading" class="captcha-loading">加载中...</text>
					<image v-else-if="captchaImage" class="captcha-img" :src="captchaImage" mode="aspectFill" />
					<text v-else class="captcha-text">点击获取</text>
				</view>
			</view>

				<!-- 记住我 -->
				<view class="form-options">
					<view class="remember" role="checkbox" :aria-checked="form.remember" aria-label="记住我" @click="form.remember = !form.remember">
						<view class="checkbox" :class="{ checked: form.remember }">
							<text v-if="form.remember" class="check-icon">✓</text>
						</view>
						<text class="remember-text">记住我</text>
					</view>
				</view>

				<!-- 登录按钮 -->
			<button class="login-btn" :loading="loading" :disabled="loading" @click="handleLogin">
				{{ loading ? '登录中...' : '登 录' }}
			</button>
			</view>
		</view>

		<!-- 底部 -->
		<view class="footer">
			<text class="footer-text">v1.0.0 · 数据中心综合布线项目管理系统</text>
		</view>
	</view>
</template>

<script>
	import { userStore } from '@/store/user'
	import { authApi } from '@/api/auth'

	export default {
		data() {
			return {
				statusBarHeight: 20,   // 状态栏高度（rpx），用于品牌区顶部避让
				focusField: '',        // 当前聚焦的输入框（用于高亮样式）
				showPassword: false,   // 是否明文显示密码
				loading: false,        // 登录请求中标记
				captchaLoading: false, // 验证码加载中标记
				captchaImage: '',      // 验证码图片（base64 data URL）
				form: {
					username: '',
					password: '',
					captchaId: '',     // 验证码标识
					captcha: '',       // 验证码答案
					remember: true     // 记住我（默认开启）
				}
			}
		},
		// 页面加载：读取系统状态栏高度，并获取验证码
		onLoad() {
			try {
				const info = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
				this.statusBarHeight = info.statusBarHeight || 20
			} catch (e) {
				this.statusBarHeight = 20
			}
			this.fetchCaptcha()
		},
		methods: {
			/**
			 * 获取验证码：调用后端接口获取 captchaId 和 base64 图片
			 */
			async fetchCaptcha() {
				this.captchaLoading = true
				try {
					const result = await authApi.getCaptcha()
					if (result && result.captchaId) {
						this.form.captchaId = result.captchaId
						this.captchaImage = result.image || ''
					}
				} catch (e) {
					console.error('获取验证码失败', e)
					uni.showToast({ title: '获取验证码失败，请重试', icon: 'none' })
				} finally {
					this.captchaLoading = false
				}
			},

			/**
			 * 刷新验证码：点击图片区域重新获取
			 */
			refreshCaptcha() {
				if (this.captchaLoading) return
				this.form.captcha = ''   // 清空已输入答案
				this.fetchCaptcha()
			},

			/**
			 * 处理登录：前端校验后调用 store 登录，成功跳首页
			 */
			async handleLogin() {
				const { username, password, captcha, captchaId } = this.form
				if (!username || !username.trim()) {
					return uni.showToast({ title: '请输入用户名', icon: 'none' })
				}
				if (!password || password.length < 6) {
					return uni.showToast({ title: '密码至少 6 位', icon: 'none' })
				}
				if (!captchaId) {
					return uni.showToast({ title: '验证码未加载，请点击刷新', icon: 'none' })
				}
				if (!captcha || !captcha.trim()) {
				return uni.showToast({ title: '请输入图片中的验证码', icon: 'none' })
			}
				this.loading = true
				try {
					await userStore.login({
						username: username.trim(),
						password,
						captchaId,
						captchaAnswer: captcha.trim(),
						remember: this.form.remember
					})
					uni.showToast({ title: '登录成功', icon: 'success' })
					// 登录成功后重启到首页，清空页面栈
					setTimeout(() => {
						uni.reLaunch({ url: '/pages/index/index' })
					}, 500)
				} catch (e) {
					// 登录失败后刷新验证码，避免重复提交同一验证码
					this.fetchCaptcha()
					console.error('login error', e)
				} finally {
					this.loading = false
				}
			}
		}
	}
</script>

<style lang="scss" scoped>
	.login-page {
		min-height: 100vh;
		background: #F5F7FA;
		display: flex;
		flex-direction: column;
	}

	/* 品牌区 */
	.brand {
		position: relative;
		height: 460rpx;
		overflow: hidden;
	}
	.brand-bg {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		background: linear-gradient(135deg, #1890FF 0%, #096DD9 100%);
		border-bottom-left-radius: 60rpx;
		border-bottom-right-radius: 60rpx;
	}
	.brand-content {
		position: relative;
		z-index: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 80rpx;
	}
	.logo {
		width: 140rpx;
		height: 140rpx;
		border-radius: 32rpx;
		/* 移除半透明背景：使用纯图片 Logo */
		background: rgba(255, 255, 255, 0.18);
		border: 2rpx solid rgba(255, 255, 255, 0.35);
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 24rpx;
		box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.12);
		overflow: hidden;
	}
	/* Logo 图片：填满 logo 容器 */
	.logo-img {
		width: 100%;
		height: 100%;
		border-radius: 30rpx;
	}
	.logo-text {
		font-size: 56rpx;
		font-weight: 700;
		color: #FFFFFF;
		letter-spacing: 2rpx;
	}
	.brand-title {
		font-size: 44rpx;
		font-weight: 700;
		color: #FFFFFF;
		margin-bottom: 12rpx;
	}
	.brand-subtitle {
		font-size: 26rpx;
		color: rgba(255, 255, 255, 0.85);
	}

	/* 表单区 */
	.form-wrap {
		flex: 1;
		margin-top: -80rpx;
		padding: 0 48rpx;
		position: relative;
		z-index: 2;
	}
	.form-card {
		background: #FFFFFF;
		border-radius: 24rpx;
		padding: 48rpx 40rpx 40rpx;
		box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.08);
	}
	.form-title {
		font-size: 40rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 8rpx;
	}
	.form-subtitle {
		font-size: 24rpx;
		color: #5C6370;
		margin-bottom: 48rpx;
	}

	.field {
		display: flex;
		align-items: center;
		height: 96rpx;
		background: #F5F7FA;
		border-radius: 12rpx;
		padding: 0 24rpx;
		margin-bottom: 28rpx;
		border: 2rpx solid transparent;
		transition: all 0.2s;
	}
	.field-focus {
		background: #FFFFFF;
		border-color: #1890FF;
		box-shadow: 0 0 0 4rpx rgba(24, 144, 255, 0.12);
	}
	.field-icon {
		font-size: 32rpx;
		margin-right: 16rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40rpx;
		height: 40rpx;
	}
	.svg-icon {
		width: 36rpx;
		height: 36rpx;
	}
	.field-input {
		flex: 1;
		height: 96rpx;
		font-size: 30rpx;
		color: #303133;
	}
	.ph {
		color: #767E8A;
		font-size: 30rpx;
	}
	.field-action {
		font-size: 24rpx;
		color: #096DD9;
		padding: 0 8rpx;
	}

	/* 验证码行 */
	.captcha-row {
		display: flex;
		align-items: center;
		margin-bottom: 28rpx;
		gap: 16rpx;
	}
	.field-captcha {
		flex: 1;
		margin-bottom: 0;
	}
	.captcha-question {
		width: 220rpx;
		height: 96rpx;
		background: #F5F7FA;
		border-radius: 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border: 2rpx solid #DDE2EA;
		overflow: hidden;
	}
	.captcha-question-hover {
		opacity: 0.8;
	}
	.captcha-img {
		width: 100%;
		height: 100%;
	}
	.captcha-text {
		font-size: 30rpx;
		font-weight: 700;
		color: #1890FF;
		letter-spacing: 2rpx;
	}
	.captcha-loading {
		font-size: 24rpx;
		color: #5C6370;
	}

	.form-options {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 36rpx;
	}
	.remember {
		display: flex;
		align-items: center;
	}
	.checkbox {
		width: 32rpx;
		height: 32rpx;
		border: 2rpx solid #C0C4CC;
		border-radius: 6rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 12rpx;
	}
	.checkbox.checked {
		background: #1890FF;
		border-color: #1890FF;
	}
	.check-icon {
		color: #FFFFFF;
		font-size: 22rpx;
		font-weight: 700;
	}
	.remember-text {
		font-size: 26rpx;
		color: #606266;
	}

	.login-btn {
		width: 100%;
		height: 96rpx;
		line-height: 96rpx;
		background: linear-gradient(135deg, #1890FF 0%, #096DD9 100%);
		color: #FFFFFF;
		font-size: 32rpx;
		font-weight: 600;
		border-radius: 12rpx;
		border: none;
		&::after {
			border: none;
		}
		&[disabled] {
			background: #B3D9FF;
			color: #5C6370;
		}
	}

	.footer {
		padding: 48rpx 0 40rpx;
		text-align: center;
	}
	.footer-text {
		font-size: 22rpx;
		color: #767E8A;
	}
</style>
