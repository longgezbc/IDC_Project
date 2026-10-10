<!--
  个人资料编辑页
  业务用途：当前登录用户自助编辑个人资料（头像、姓名、昵称、性别、联系方式、简介），
           并提供修改密码入口（弹窗内嵌，含密码强度指示器）。
  数据来源：userApi.getProfile（初始化）、userApi.updateProfile（保存）、
           userApi.changePassword（改密）、uni.uploadFile（头像上传到 /files/upload）。
  导航特性：非 tabBar 页；保存成功或改密成功后 navigateBack / reLaunch 到登录页。
-->
<template>
	<view class="page">
		<!-- 头部：头像 + 用户名展示，点击头像可上传新头像 -->
		<view class="hero">
			<view class="hero-bg"></view>
			<view class="hero-content" :style="{ paddingTop: 'calc(' + statusBarHeight + 'px + 32rpx)' }">
				<view class="avatar-wrap" role="button" aria-label="点击上传头像" hover-class="uni-hover" @click="chooseAvatar">
					<image v-if="avatarUrl" class="avatar-img" :src="avatarUrl" mode="aspectFill" />
					<view v-else class="avatar-placeholder">{{ userInitial }}</view>
					<view class="avatar-mask">
						<text class="avatar-mask-text">更换</text>
					</view>
				</view>
				<view class="hero-name">{{ form.username || '未登录' }}</view>
				<view class="hero-sub">点击头像可上传新头像</view>
			</view>
		</view>

		<!-- 资料表单卡片 -->
		<view class="form-card">
			<view class="card-title">基本信息</view>

			<!-- 用户名（只读） -->
			<view class="form-item">
				<text class="form-label">用户名</text>
				<view class="form-input readonly">{{ form.username || '-' }}</view>
			</view>

			<!-- 姓名（必填） -->
			<view class="form-item">
				<text class="form-label">姓名<text class="req">*</text></text>
				<input
					class="form-input"
					v-model="form.realName"
					maxlength="50"
					placeholder="请输入姓名"
					placeholder-class="ph"
					aria-label="姓名"
				/>
			</view>

			<!-- 昵称 -->
			<view class="form-item">
				<text class="form-label">昵称</text>
				<input
					class="form-input"
					v-model="form.nickname"
					maxlength="50"
					placeholder="请输入昵称（选填）"
					placeholder-class="ph"
					aria-label="昵称"
				/>
			</view>

			<!-- 性别（picker 单选） -->
			<view class="form-item">
				<text class="form-label">性别</text>
				<picker mode="selector" :range="genderOptions" :value="genderIndex" @change="onGenderChange">
					<view class="form-input picker">
						<text :class="['picker-text', genderIndex < 0 && 'ph']">
							{{ genderIndex >= 0 ? genderOptions[genderIndex] : '请选择性别' }}
						</text>
						<text class="picker-arrow" aria-hidden="true">›</text>
					</view>
				</picker>
			</view>

			<!-- 手机号 -->
			<view class="form-item">
				<text class="form-label">手机号</text>
				<input
					class="form-input"
					v-model="form.phone"
					type="number"
					maxlength="11"
					placeholder="请输入手机号"
					placeholder-class="ph"
					aria-label="手机号"
				/>
			</view>

			<!-- 邮箱 -->
			<view class="form-item">
				<text class="form-label">邮箱</text>
				<input
					class="form-input"
					v-model="form.email"
					placeholder="请输入邮箱"
					placeholder-class="ph"
					aria-label="邮箱"
				/>
			</view>

			<!-- 部门（只读） -->
			<view class="form-item">
				<text class="form-label">部门</text>
				<view class="form-input readonly">{{ form.department || '-' }}</view>
			</view>

			<!-- 岗位（只读） -->
			<view class="form-item">
				<text class="form-label">岗位</text>
				<view class="form-input readonly">{{ form.position || '-' }}</view>
			</view>

			<!-- 简介（textarea 200 字限制） -->
			<view class="form-item">
				<view class="form-label-row">
					<text class="form-label">简介</text>
					<text class="form-counter">{{ form.introduction.length }}/200</text>
				</view>
				<textarea
					class="form-textarea"
					v-model="form.introduction"
					maxlength="200"
					placeholder="请输入个人简介（选填）"
					placeholder-class="ph"
					:auto-height="false"
					aria-label="简介"
				/>
			</view>
		</view>

		<!-- 操作按钮 -->
		<view class="action-area">
			<button class="btn-primary" :loading="submitting" :disabled="submitting" @click="handleSubmit">保存资料</button>
			<button class="btn-ghost" @click="pwdVisible = true">修改密码</button>
		</view>

		<!-- 修改密码弹窗：内嵌表单 + 密码强度指示器 -->
		<view v-if="pwdVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="closePwdPopup">
			<view class="popup" @click.stop>
				<view class="popup-title">修改密码</view>

				<view class="form-item">
					<text class="form-label">旧密码<text class="req">*</text></text>
					<input
						class="form-input"
						v-model="pwdForm.oldPassword"
						password
						placeholder="请输入旧密码"
						placeholder-class="ph"
						aria-label="旧密码"
					/>
				</view>

				<view class="form-item">
					<text class="form-label">新密码<text class="req">*</text></text>
					<input
						class="form-input"
						v-model="pwdForm.newPassword"
						password
						placeholder="请输入新密码"
						placeholder-class="ph"
						aria-label="新密码"
						@input="onNewPwdInput"
					/>
					<!-- 密码强度指示器 -->
					<view class="pwd-strength">
						<view class="pwd-strength-bars">
							<view class="pwd-bar" :class="['weak', pwdStrength.level >= 1 ? 'active' : '']"></view>
							<view class="pwd-bar" :class="['medium', pwdStrength.level >= 2 ? 'active' : '']"></view>
							<view class="pwd-bar" :class="['strong', pwdStrength.level >= 3 ? 'active' : '']"></view>
						</view>
						<text class="pwd-strength-text" :class="pwdStrength.levelClass">{{ pwdStrength.label }}</text>
					</view>
				</view>

				<view class="form-item">
					<text class="form-label">确认新密码<text class="req">*</text></text>
					<input
						class="form-input"
						v-model="pwdForm.confirmPassword"
						password
						placeholder="请再次输入新密码"
						placeholder-class="ph"
						aria-label="确认新密码"
					/>
				</view>

				<view class="popup-actions">
					<button class="btn-cancel" @click="closePwdPopup">取消</button>
					<button class="btn-confirm" :loading="pwdSubmitting" :disabled="pwdSubmitting" @click="handleChangePassword">确认修改</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { userApi } from '@/api/user'
	import { auth } from '@/utils/auth'
	import { API_BASE_URL } from '@/utils/config'
	import { userStore } from '@/store/user'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				statusBarHeight: 20,            // 状态栏高度（px），用于头部避让
				submitting: false,              // 资料保存中标记（防重复提交）
				pwdSubmitting: false,           // 改密保存中标记（防重复提交）
				pwdVisible: false,              // 修改密码弹窗显隐
				genderOptions: ['男', '女', '保密'],
				genderIndex: -1,                // 性别 picker 当前选中下标，-1 表示未选择
				avatarUrl: '',                  // 头像完整 URL（用于 image 展示）
				form: {
					username: '',               // 用户名（只读，登录账号）
					realName: '',               // 姓名（必填）
					nickname: '',               // 昵称
					gender: '',                 // 性别：男/女/保密
					phone: '',                  // 手机号
					email: '',                  // 邮箱
					department: '',             // 部门（只读）
					position: '',               // 岗位（只读）
					introduction: '',           // 简介（≤200字）
					avatar: ''                  // 头像相对路径（提交给后端）
				},
				pwdForm: {
					oldPassword: '',
					newPassword: '',
					confirmPassword: ''
				}
			}
		},
		computed: {
			/** 用户名首字母，用于头像占位展示 */
			userInitial() {
				const n = this.form.realName || this.form.username || 'U'
				return n.charAt(0).toUpperCase()
			},
			/**
			 * 密码强度计算
			 * 规则：长度<8 弱；长度 8-15 且同时包含字母+数字 中；长度≥16 或包含特殊字符 强
			 * 返回 { level, label, levelClass }
			 *   level: 0 未填写 / 1 弱 / 2 中 / 3 强
			 */
			pwdStrength() {
				const pwd = this.pwdForm.newPassword || ''
				if (!pwd) return { level: 0, label: '未填写', levelClass: '' }
				const hasLetter = /[a-zA-Z]/.test(pwd)
				const hasDigit = /[0-9]/.test(pwd)
				const hasSpecial = /[^a-zA-Z0-9]/.test(pwd)
				let level = 1
				let label = '弱'
				let levelClass = 'weak'
				if (pwd.length >= 16 || hasSpecial) {
					level = 3
					label = '强'
					levelClass = 'strong'
				} else if (pwd.length >= 8 && hasLetter && hasDigit) {
					level = 2
					label = '中'
					levelClass = 'medium'
				}
				return { level, label, levelClass }
			}
		},
		onLoad() {
			// 读取系统状态栏高度用于头部避让
			try {
				const info = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
				this.statusBarHeight = info.statusBarHeight || 20
			} catch (e) {
				this.statusBarHeight = 20
			}
			this.loadProfile()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			/** 加载当前用户资料，回填表单 */
			async loadProfile() {
				uni.showLoading({ title: '加载中', mask: true })
				try {
					const data = await userApi.getProfile()
					this.fillForm(data || {})
				} catch (e) {
					console.warn('load profile error', e)
				} finally {
					uni.hideLoading()
				}
			},
			/**
			 * 用接口返回的数据回填表单
			 * 兼容多种字段命名：realName/name、phone/mobile、department/deptName、position/jobTitle 等
			 */
			fillForm(data) {
				this.form.username = data.username || ''
				this.form.realName = data.realName || data.name || ''
				this.form.nickname = data.nickname || ''
				this.form.gender = data.gender || ''
				this.form.phone = data.phone || data.mobile || ''
				this.form.email = data.email || ''
				this.form.department = data.department || data.deptName || ''
				this.form.position = data.position || data.jobTitle || ''
				this.form.introduction = data.introduction || data.bio || ''
				this.form.avatar = data.avatar || ''
				// 头像完整 URL 拼装：后端返回相对路径，前端拼接预览地址
				this.avatarUrl = this.resolveAvatarUrl(data.avatar)
				// 性别回填到 picker 下标
				if (this.form.gender) {
					const idx = this.genderOptions.indexOf(this.form.gender)
					this.genderIndex = idx >= 0 ? idx : -1
				} else {
					this.genderIndex = -1
				}
				// 同步到 userStore，确保其他页面（如 profile）使用的是最新信息
				if (userStore.state.userInfo) {
					userStore.state.userInfo = Object.assign({}, userStore.state.userInfo, data)
				}
			},
			/**
			 * 拼装头像完整 URL
			 * @param {string} avatar 后端返回的相对路径或完整 URL
			 * @returns {string} 可直接用于 <image :src> 的完整地址
			 */
			resolveAvatarUrl(avatar) {
				if (!avatar) return ''
				if (/^https?:\/\//i.test(avatar)) return avatar
				return API_BASE_URL + '/files/preview/' + avatar.replace(/^\/+/, '')
			},
			/** 性别 picker 选择变化 */
			onGenderChange(e) {
				const idx = Number(e.detail.value)
				this.genderIndex = idx
				this.form.gender = this.genderOptions[idx]
			},
			/** 新密码输入变化：触发响应式更新（pwdStrength 计算属性自动重新计算） */
			onNewPwdInput() {
				// 计算属性会自动响应，这里无需额外操作；保留 hook 以便后续扩展
			},
			/**
			 * 选择头像图片并上传到 /files/upload
			 * 上传成功后：更新表单 avatar 字段（相对路径）与 avatarUrl（完整 URL）
			 */
			chooseAvatar() {
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					sourceType: ['album', 'camera'],
					success: (res) => {
						const tempPath = res.tempFilePaths && res.tempFilePaths[0]
						if (!tempPath) return
						this.uploadAvatar(tempPath)
					},
					fail: () => {
						// 用户取消选择，静默处理
					}
				})
			},
			/**
			 * 上传头像文件到 /files/upload
			 * @param {string} filePath 临时文件路径
			 */
			uploadAvatar(filePath) {
				uni.showLoading({ title: '上传中', mask: true })
				const token = auth.getToken()
				uni.uploadFile({
					url: API_BASE_URL + '/files/upload',
					filePath,
					name: 'file',
					header: token ? { Authorization: `Bearer ${token}` } : {},
					success: (res) => {
						// res.data 是字符串，需 JSON 解析；后端统一响应 { code, message, data }
						let body = {}
						try {
							body = typeof res.data === 'string' ? JSON.parse(res.data) : (res.data || {})
						} catch (e) {
							body = {}
						}
						if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 200) {
							const relativePath = typeof body.data === 'string' ? body.data : (body.data && (body.data.path || body.data.url || body.data))
							if (relativePath) {
								this.form.avatar = relativePath
								this.avatarUrl = this.resolveAvatarUrl(relativePath)
								uni.showToast({ title: '上传成功', icon: 'success' })
							} else {
								uni.showToast({ title: '上传响应异常', icon: 'none' })
							}
						} else {
							uni.showToast({ title: body.message || '上传失败', icon: 'none' })
						}
					},
					fail: () => {
						uni.showToast({ title: '上传失败，请重试', icon: 'none' })
					},
					complete: () => {
						uni.hideLoading()
					}
				})
			},
			/** 校验表单，返回错误消息（首个错误），通过则返回空字符串 */
			validateForm() {
				if (!this.form.realName || !this.form.realName.trim()) {
					return '请输入姓名'
				}
				if (this.form.phone && !/^\d{11}$/.test(this.form.phone)) {
					return '手机号格式不正确（需 11 位数字）'
				}
				if (this.form.email && !(/@/.test(this.form.email) && /\./.test(this.form.email))) {
					return '邮箱格式不正确'
				}
				return ''
			},
			/** 提交保存资料（防重复提交） */
			async handleSubmit() {
				if (this.submitting) return
				const err = this.validateForm()
				if (err) {
					uni.showToast({ title: err, icon: 'none' })
					return
				}
				this.submitting = true
				uni.showLoading({ title: '保存中', mask: true })
				try {
					const payload = {
						realName: this.form.realName.trim(),
						nickname: this.form.nickname,
						gender: this.form.gender,
						phone: this.form.phone,
						email: this.form.email,
						introduction: this.form.introduction,
						avatar: this.form.avatar
					}
					const updated = await userApi.updateProfile(payload)
					// 同步更新 userStore.state.userInfo，保持本地与其他页面一致
					const merged = Object.assign({}, userStore.state.userInfo || {}, payload, updated || {})
					userStore.state.userInfo = merged
					auth.saveUserInfo(merged)
					uni.hideLoading()
					uni.showToast({ title: '保存成功', icon: 'success' })
					this.navBackTimer = setTimeout(() => uni.navigateBack(), 600)
				} catch (e) {
					uni.hideLoading()
					console.warn('update profile error', e)
				} finally {
					this.submitting = false
				}
			},
			/** 关闭修改密码弹窗并清空表单 */
			closePwdPopup() {
				this.pwdVisible = false
				this.pwdForm = { oldPassword: '', newPassword: '', confirmPassword: '' }
			},
			/** 修改密码：校验 + 调用接口 + 引导重新登录 */
			async handleChangePassword() {
				if (this.pwdSubmitting) return
				const { oldPassword, newPassword, confirmPassword } = this.pwdForm
				if (!oldPassword) {
					return uni.showToast({ title: '请输入旧密码', icon: 'none' })
				}
				if (!newPassword) {
					return uni.showToast({ title: '请输入新密码', icon: 'none' })
				}
				if (newPassword.length < 8) {
					return uni.showToast({ title: '新密码至少 8 位', icon: 'none' })
				}
				if (newPassword !== confirmPassword) {
					return uni.showToast({ title: '两次密码不一致', icon: 'none' })
				}
				this.pwdSubmitting = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await userApi.changePassword({ oldPassword, newPassword })
					uni.hideLoading()
					uni.showToast({ title: '修改成功', icon: 'success' })
					// 关闭弹窗
					this.pwdVisible = false
					this.pwdForm = { oldPassword: '', newPassword: '', confirmPassword: '' }
					// 后端可能未失效 token，前端引导用户重新登录
					this.navBackTimer = setTimeout(() => {
						uni.showModal({
							title: '提示',
							content: '密码已修改，建议重新登录以保障账号安全',
							showCancel: false,
							confirmText: '重新登录',
							success: () => {
								// 清除本地登录态并 reLaunch 到登录页
								auth.clearAuth()
								userStore.state.token = ''
								userStore.state.refreshToken = ''
								userStore.state.userInfo = null
								uni.reLaunch({ url: '/pages/login/login' })
							}
						})
					}, 800)
				} catch (e) {
					uni.hideLoading()
					console.warn('change password error', e)
				} finally {
					this.pwdSubmitting = false
				}
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		background: #F5F7FA;
		padding-bottom: calc(60rpx + env(safe-area-inset-bottom));
	}

	/* 头部 hero 区域（参考 profile.vue） */
	.hero {
		position: relative;
		margin-bottom: 32rpx;
	}
	.hero-bg {
		position: absolute;
		top: 0; left: 0; right: 0;
		height: 320rpx;
		background: linear-gradient(135deg, #1890FF 0%, #096DD9 100%);
		border-bottom-left-radius: 40rpx;
		border-bottom-right-radius: 40rpx;
	}
	.hero-content {
		position: relative;
		z-index: 1;
		padding: 0 32rpx 40rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.avatar-wrap {
		position: relative;
		width: 160rpx;
		height: 160rpx;
		border-radius: 50%;
		overflow: hidden;
		background: rgba(255, 255, 255, 0.25);
		border: 4rpx solid rgba(255, 255, 255, 0.6);
		box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.12);
	}
	.avatar-img {
		width: 100%;
		height: 100%;
		display: block;
	}
	.avatar-placeholder {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #FFFFFF;
		font-size: 64rpx;
		font-weight: 700;
	}
	.avatar-mask {
		position: absolute;
		left: 0; right: 0; bottom: 0;
		height: 48rpx;
		background: rgba(0, 0, 0, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.avatar-mask-text {
		color: #FFFFFF;
		font-size: 22rpx;
	}
	.hero-name {
		margin-top: 16rpx;
		font-size: 32rpx;
		font-weight: 700;
		color: #FFFFFF;
	}
	.hero-sub {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.8);
	}

	/* 表单卡片 */
	.form-card {
		margin: 0 24rpx 24rpx;
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.card-title {
		font-size: 28rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 16rpx;
		padding-left: 8rpx;
	}
	.form-item {
		margin-bottom: 24rpx;
		&:last-child { margin-bottom: 0; }
	}
	.form-label-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 12rpx;
	}
	.form-label {
		display: block;
		font-size: 26rpx;
		color: #606266;
		margin-bottom: 12rpx;
	}
	.form-label-row .form-label { margin-bottom: 0; }
	.req {
		color: #D4380D;
		margin-left: 4rpx;
	}
	.form-counter {
		font-size: 22rpx;
		color: #767E8A;
	}
	.form-input {
		width: 100%;
		height: 72rpx;
		line-height: 72rpx;
		padding: 0 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.form-input.readonly {
		color: #767E8A;
		background: #FAFAFA;
	}
	.form-input.picker {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.picker-text {
		font-size: 26rpx;
		color: #303133;
	}
	.picker-text.ph { color: #767E8A; }
	.picker-arrow {
		font-size: 36rpx;
		color: #767E8A;
	}
	.form-textarea {
		width: 100%;
		height: 160rpx;
		padding: 16rpx 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.ph { color: #767E8A; }

	/* 操作按钮区域 */
	.action-area {
		padding: 0 24rpx;
		display: flex;
		flex-direction: column;
		gap: 16rpx;
	}
	.btn-primary, .btn-ghost {
		width: 100%;
		height: 88rpx;
		line-height: 88rpx;
		font-size: 30rpx;
		border-radius: 12rpx;
		border: none;
		margin: 0;
		&::after { border: none; }
	}
	.btn-primary {
		background: #1890FF;
		color: #FFFFFF;
		&[disabled] { background: #B3D9FF; color: #FFFFFF; }
	}
	.btn-ghost {
		background: #FFFFFF;
		color: #096DD9;
		border: 2rpx solid #1890FF;
	}

	/* 弹窗（参考 customer/list.vue） */
	.popup-mask {
		position: fixed;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 999;
	}
	.popup {
		width: 640rpx;
		max-height: 80vh;
		background: #FFFFFF;
		border-radius: 24rpx;
		padding: 32rpx;
		display: flex;
		flex-direction: column;
	}
	.popup-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #303133;
		text-align: center;
		margin-bottom: 24rpx;
	}
	.popup-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 16rpx;
	}
	.btn-cancel, .btn-confirm {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		border: none;
		margin: 0;
		&::after { border: none; }
	}
	.btn-cancel {
		background: #F5F7FA;
		color: #606266;
	}
	.btn-confirm {
		background: #1890FF;
		color: #FFFFFF;
		&[disabled] { background: #B3D9FF; color: #FFFFFF; }
	}

	/* 密码强度指示器 */
	.pwd-strength {
		display: flex;
		align-items: center;
		margin-top: 12rpx;
		gap: 12rpx;
	}
	.pwd-strength-bars {
		display: flex;
		gap: 6rpx;
		flex: 1;
	}
	.pwd-bar {
		flex: 1;
		height: 8rpx;
		border-radius: 4rpx;
		background: #E4E7ED;
		transition: background 0.2s;
	}
	.pwd-bar.weak.active { background: #FF4D4F; }
	.pwd-bar.medium.active { background: #FAAD14; }
	.pwd-bar.strong.active { background: #52C41A; }
	.pwd-strength-text {
		font-size: 22rpx;
		color: #767E8A;
		min-width: 60rpx;
		text-align: right;
	}
	.pwd-strength-text.weak { color: #FF4D4F; }
	.pwd-strength-text.medium { color: #FAAD14; }
	.pwd-strength-text.strong { color: #52C41A; }
</style>
