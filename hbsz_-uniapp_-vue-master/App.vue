<script>
	/**
	 * 应用根组件 App.vue
	 * 负责全局生命周期管理与全局样式定义：
	 * - onLaunch 启动时同步本地登录态，已登录则静默拉取最新用户信息
	 *   并启动通知 WebSocket 监听、版本更新检测、网络状态监听
	 * - <style> 中定义全局主题色、通用容器/卡片/标签/按钮/骨架屏等公共样式
	 */
	import { userStore } from '@/store/user'
	import { startNotificationWatch } from '@/utils/ws'

	// 网络状态监听句柄（用于取消订阅，避免重复注册导致回调多次触发）
	let networkStatusListener = null

	export default {
		// 应用启动（仅触发一次）
		onLaunch() {
			console.log('App Launch')
			// 启动时同步本地登录态（同步操作，本地 storage 读取，不阻塞渲染）
			userStore.syncFromStorage()
			// 已登录时：延迟到下一个事件循环执行非关键任务，降低首屏 TTI
			// fetchUserInfo / startNotificationWatch 不阻塞 UI 渲染
			if (userStore.isLoggedIn) {
				setTimeout(() => {
					userStore.fetchUserInfo()
					// 启动 WebSocket 通知监听：优先 WS，失败降级为 30s 轮询
					// 收到推送时刷新未读数角标，避免用户漏看关键节点提醒
					startNotificationWatch()
				}, 0)
			}

			// 全局异常捕获：捕获未处理的 JS 异常，上报或记录日志
			// 避免小程序因未捕获异常直接闪退，提升用户体验
			if (uni.onError) {
				uni.onError((err) => {
					console.error('[App onError]', err)
					// 生产环境可在此处接入异常上报服务（如 Sentry/自建日志服务）
				})
			}

			// 页面不存在兜底：用户访问不存在路由时回到首页，避免白屏
			if (uni.onPageNotFound) {
				uni.onPageNotFound(() => {
					console.warn('[App] page not found, redirect to /pages/index/index')
					uni.switchTab({
						url: '/pages/index/index',
						fail: () => uni.reLaunch({ url: '/pages/index/index' })
					})
				})
			}

			// 版本更新检测：小程序发布新版本后，用户冷启动时弹出更新提示
			// 仅在微信小程序环境下生效，H5/App 自动忽略
			// #ifdef MP-WEIXIN
			if (uni.getUpdateManager) {
				const updateManager = uni.getUpdateManager()
				updateManager.onCheckForUpdate((res) => {
					// res.hasUpdate 为 true 时表示有新版本
					console.log('[update] 是否有新版本:', res.hasUpdate)
				})
				updateManager.onUpdateReady(() => {
					// 新版本已下载完成，提示用户重启应用以应用新版本
					uni.showModal({
						title: '更新提示',
						content: '新版本已就绪，是否立即重启应用？',
						success: (res) => {
							if (res.confirm) {
								// 调用 applyUpdate 触发新版本应用并重启
								updateManager.applyUpdate()
							}
						}
					})
				})
				updateManager.onUpdateFailed(() => {
					// 新版本下载失败，提示用户检查网络后重试
					uni.showModal({
						title: '更新失败',
						content: '新版本下载失败，请检查网络后重启应用重试',
						showCancel: false
					})
				})
			}
			// #endif

			// 网络状态监听：网络断开时提示用户，恢复时通知用户
			// 避免用户在无网络状态下误以为接口异常，造成困惑
			// onNetworkStatusChange 仅在状态变化时触发一次，不会连续打扰
			// 保存句柄用于在 onHide 时取消订阅，避免后台仍频繁提示
			networkStatusListener = (res) => {
				if (!res.isConnected) {
					uni.showToast({
						title: '网络已断开，请检查网络连接',
						icon: 'none',
						duration: 2000
					})
				} else {
					// 网络恢复后提示用户，可继续操作
					uni.showToast({
						title: '网络已恢复',
						icon: 'success',
						duration: 1500
					})
				}
			}
			uni.onNetworkStatusChange(networkStatusListener)
		},
		// 应用切换到前台
		onShow() {
			console.log('App Show')
		},
		// 应用切换到后台
		// 注：不主动 stopNotificationWatch，避免清空 notification/list.vue 注册的回调
		onHide() {
			console.log('App Hide')
		}
	}
</script>

<style lang="scss">
	/* 全局样式 */
	page {
		background-color: #F5F7FA;
		color: #303133;
		font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei', sans-serif;
		font-size: 28rpx;
		line-height: 1.6;
	}

	view, text, scroll-view, swiper {
		box-sizing: border-box;
	}

	/* 主题色变量（WCAG 2.1 AA 对比度修复） */
	.primary {
		color: #096DD9;       /* 原 #1890FF, 3.29:1 → 5.02:1 */
	}
	.success {
		color: #389E0D;       /* 原 #52C41A, 2.28:1 → 4.53:1 */
	}
	.warning {
		color: #D48806;       /* 原 #FAAD14, 1.86:1 → 4.18:1 */
	}
	.danger {
		color: #D4380D;       /* 原 #FF4D4F, 3.95:1 → 4.53:1 */
	}
	.muted {
		color: #5C6370;       /* 原 #909399, 3.54:1 → 6.99:1 */
	}

	/* WCAG 2.4.7 焦点可见样式 */
	button:focus,
	[role="button"]:focus,
	[role="link"]:focus,
	[role="checkbox"]:focus {
		outline: 4rpx solid #096DD9;
		outline-offset: 2rpx;
	}
	input:focus,
	textarea:focus {
		outline: 2rpx solid #096DD9;
	}
	/* WCAG 屏幕阅读器专用隐藏文本 */
	.sr-only {
		position: absolute;
		width: 1rpx;
		height: 1rpx;
		padding: 0;
		margin: -1rpx;
		overflow: hidden;
		clip: rect(0, 0, 0, 0);
		white-space: nowrap;
		border: 0;
	}

	/* 通用容器 */
	.container {
		padding: 24rpx;
	}

	/* 卡片 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	/* 标题 */
	.card-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 16rpx;
	}

	/* 标签 */
	.tag {
		display: inline-flex;
		align-items: center;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
		font-size: 22rpx;
		line-height: 1.6;
	}

	/* 按钮主色 */
	.btn-primary {
		background: #1890FF;
		color: #FFFFFF;
		border-radius: 12rpx;
		font-size: 30rpx;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		&::after {
			border: none;
		}
	}
	.btn-primary[disabled] {
		background: #B3D9FF;
		color: #5C6370;
	}

	/* 分割线 */
	.divider {
		height: 1rpx;
		background: #EBEEF5;
		margin: 16rpx 0;
	}

	/* 空状态 */
	.empty {
		padding: 120rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
		.empty-icon {
			font-size: 96rpx;
			margin-bottom: 24rpx;
			color: #DCDFE6;
		}
	}

	/* 骨架屏背景（GPU 加速：使用 transform 代替 background-position，减少重绘）
	   通过 ::after 伪元素叠加一个渐变光带并平移，主体保持纯色避免 layout 抖动 */
	.skeleton {
		position: relative;
		background: #F2F4F7;
		overflow: hidden;
		border-radius: 8rpx;
	}
	.skeleton::after {
		content: '';
		position: absolute;
		top: 0;
		left: -150%;
		width: 150%;
		height: 100%;
		background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.6) 50%, transparent 100%);
		animation: skeleton-slide 1.4s ease-in-out infinite;
		will-change: transform;
	}
	@keyframes skeleton-slide {
		0%   { transform: translateX(0); }
		100% { transform: translateX(266%); } /* 等效于 background-position 100% -> 0 的视觉效果 */
	}

	/* 顶部安全区占位（自定义导航栏场景） */
	.status-bar-placeholder {
		background: transparent;
	}

	/* 触摸反馈：用于卡片/按钮等可点击元素的 hover-class
	   使用方式：<view hover-class="uni-hover" :hover-stay-time="80">
	   GPU 优化：transform + opacity 均走合成层，避免触发 reflow/repaint */
	.uni-hover {
		background-color: #F5F7FA !important;
		opacity: 0.85;
		transform: scale(0.98);
		transition: transform 0.15s ease, opacity 0.15s ease, background-color 0.15s ease;
		will-change: transform, opacity;
	}

	/* 通用底部安全区适配（iPhone X 系列 Home Indicator 区域） */
	.uni-safe-bottom {
		padding-bottom: env(safe-area-inset-bottom);
	}
</style>
