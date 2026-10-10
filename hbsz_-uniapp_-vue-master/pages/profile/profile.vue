<!--
  我的（个人中心）页（tabBar 页面）
  业务用途：展示当前用户信息与角色、数据统计（负责项目/进行中/待办节点）、功能菜单入口，
           并提供刷新用户信息、清除缓存、关于应用与退出登录等操作。
  数据来源：userStore（用户信息/角色）、projectApi.getStatistics（统计）、taskApi.getUpcomingAll（待办节点）。
  导航特性：tabBar 页；未登录时不加载统计；退出登录后 reLaunch 到登录页。
-->
<template>
	<view class="page">
		<!-- 用户信息头部 -->
		<view class="hero">
			<view class="hero-bg"></view>
			<view class="hero-content" :style="{ paddingTop: 'calc(' + statusBarHeight + 'px + 32rpx)' }">
				<view class="user-card">
					<view class="avatar" role="img" aria-label="用户头像">{{ userInitial }}</view>
					<view class="user-info">
						<view class="user-name">{{ userName || '未登录' }}</view>
						<view class="user-role">
							<text v-for="r in roles" :key="r" class="role-tag">{{ r }}</text>
							<text v-if="roles.length === 0" class="role-text">项目负责人</text>
						</view>
					</view>
				</view>
			</view>
		</view>

		<!-- 数据统计 -->
		<view class="stat-card">
			<view class="stat-item">
				<view class="stat-num">{{ stat.total || 0 }}</view>
				<view class="stat-label">负责项目</view>
			</view>
			<view class="stat-item">
				<view class="stat-num primary">{{ stat.inProgress || 0 }}</view>
				<view class="stat-label">进行中</view>
			</view>
			<view class="stat-item">
				<view class="stat-num warning">{{ stat.upcoming || 0 }}</view>
				<view class="stat-label">待办节点</view>
			</view>
		</view>

		<!-- 我的工作菜单 -->
		<view class="menu-group">
			<view class="group-title">我的工作</view>
			<view class="menu-card">
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/my/tasks')">
					<view class="menu-icon icon-cyan" aria-hidden="true"><image class="svg-icon" src="/static/icon/任务.svg" mode="aspectFit" /></view>
					<text class="menu-text">我的任务</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/my/submissions')">
					<view class="menu-icon icon-orange" aria-hidden="true"><image class="svg-icon" src="/static/icon/填报.svg" mode="aspectFit" /></view>
					<text class="menu-text">我的填报</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/notification/list')">
					<view class="menu-icon icon-red" aria-hidden="true"><image class="svg-icon" src="/static/icon/通知.svg" mode="aspectFit" /></view>
					<text class="menu-text">站内通知</text>
					<view v-if="unreadCount > 0" class="menu-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</view>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<!-- 工程师：授权申请入口 -->
				<view v-if="!canManage" class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/project-auth/management')">
					<view class="menu-icon icon-indigo" aria-hidden="true"><image class="svg-icon" src="/static/icon/转移.svg" mode="aspectFit" /></view>
					<text class="menu-text">我的授权申请</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
			</view>
		</view>

		<!-- 项目工作菜单 -->
		<view class="menu-group">
			<view class="group-title">项目工作</view>
			<view class="menu-card">
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/project/list')">
					<view class="menu-icon icon-blue" aria-hidden="true"><image class="svg-icon" src="/static/icon/项目.svg" mode="aspectFit" /></view>
					<text class="menu-text">我的项目</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/document/list')">
					<view class="menu-icon icon-purple" aria-hidden="true"><image class="svg-icon" src="/static/icon/资料.svg" mode="aspectFit" /></view>
					<text class="menu-text">项目资料</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/customer/list')">
					<view class="menu-icon icon-green2" aria-hidden="true"><image class="svg-icon" src="/static/icon/客户.svg" mode="aspectFit" /></view>
					<text class="menu-text">客户管理</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
			</view>
		</view>

		<!-- PM 管理菜单（仅 PM/admin 可见） -->
		<view v-if="canManage" class="menu-group">
			<view class="group-title">项目管理</view>
			<view class="menu-card">
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/progress/review')">
					<view class="menu-icon icon-red" aria-hidden="true"><image class="svg-icon" src="/static/icon/审核.svg" mode="aspectFit" /></view>
					<text class="menu-text">进度审核</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/document/review')">
					<view class="menu-icon icon-red" aria-hidden="true"><image class="svg-icon" src="/static/icon/审核.svg" mode="aspectFit" /></view>
					<text class="menu-text">文档审核</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/pm/risk-board')">
					<view class="menu-icon icon-yellow" aria-hidden="true"><image class="svg-icon" src="/static/icon/风险.svg" mode="aspectFit" /></view>
					<text class="menu-text">风险预警看板</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/pm/project-auth')">
					<view class="menu-icon icon-indigo" aria-hidden="true"><image class="svg-icon" src="/static/icon/转移.svg" mode="aspectFit" /></view>
					<text class="menu-text">项目授权转移</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/pm/team')">
					<view class="menu-icon icon-indigo" aria-hidden="true"><image class="svg-icon" src="/static/icon/转移.svg" mode="aspectFit" /></view>
					<text class="menu-text">项目组管理</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/archive/list')">
					<view class="menu-icon icon-gray" aria-hidden="true"><image class="svg-icon" src="/static/icon/资料.svg" mode="aspectFit" /></view>
					<text class="menu-text">归档管理</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
			</view>
		</view>

		<!-- 统计与报表菜单（PM/admin 可见） -->
		<view v-if="canManage" class="menu-group">
			<view class="group-title">统计报表</view>
			<view class="menu-card">
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/statistics/index')">
					<view class="menu-icon icon-blue" aria-hidden="true"><image class="svg-icon" src="/static/icon/项目.svg" mode="aspectFit" /></view>
					<text class="menu-text">统计分析</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/daily-report/preview')">
					<view class="menu-icon icon-orange" aria-hidden="true"><image class="svg-icon" src="/static/icon/填报.svg" mode="aspectFit" /></view>
					<text class="menu-text">日报预览</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
			</view>
		</view>

		<!-- 系统管理菜单（仅 admin 可见） -->
		<view v-if="canAdmin" class="menu-group">
			<view class="group-title">系统管理</view>
			<view class="menu-card">
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/system/users')">
					<view class="menu-icon icon-cyan" aria-hidden="true"><image class="svg-icon" src="/static/icon/我的.svg" mode="aspectFit" /></view>
					<text class="menu-text">用户管理</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
			</view>
		</view>

		<view class="menu-group">
			<view class="group-title">账号设置</view>
			<view class="menu-card">
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/profile/edit')">
					<view class="menu-icon icon-blue" aria-hidden="true"><image class="svg-icon" src="/static/icon/我的.svg" mode="aspectFit" /></view>
					<text class="menu-text">编辑资料</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="link" @click="goPage('/pages/profile/notification-settings')">
					<view class="menu-icon icon-orange" aria-hidden="true"><image class="svg-icon" src="/static/icon/通知.svg" mode="aspectFit" /></view>
					<text class="menu-text">通知偏好</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="button" @click="handleRefresh">
					<view class="menu-icon icon-green" aria-hidden="true"><image class="svg-icon" src="/static/icon/刷新.svg" mode="aspectFit" /></view>
					<text class="menu-text">刷新用户信息</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="button" @click="handleClearCache">
					<view class="menu-icon icon-orange" aria-hidden="true"><image class="svg-icon" src="/static/icon/清理缓存.svg" mode="aspectFit" /></view>
					<text class="menu-text">清除缓存</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
			</view>
		</view>

		<view class="menu-group">
			<view class="group-title">关于</view>
			<view class="menu-card">
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80">
					<view class="menu-icon icon-gray" aria-hidden="true"><image class="svg-icon" src="/static/icon/版本.svg" mode="aspectFit" /></view>
					<text class="menu-text">版本信息</text>
					<text class="menu-arrow version">v1.0.0</text>
				</view>
				<view class="menu-item" hover-class="uni-hover" :hover-stay-time="80" role="button" @click="showAbout">
					<view class="menu-icon icon-gray" aria-hidden="true"><image class="svg-icon" src="/static/icon/关于应用.svg" mode="aspectFit" /></view>
					<text class="menu-text">关于应用</text>
					<text class="menu-arrow" aria-hidden="true">›</text>
				</view>
			</view>
		</view>

		<!-- 退出登录 -->
		<view class="logout-area">
			<button class="logout-btn" @click="handleLogout">退出登录</button>
		</view>

		<view class="footer">
			<text>数据中心综合布线项目管理系统 · 移动端</text>
		</view>

		<!-- 关于应用弹窗：展示 Logo 与版本信息 -->
		<view v-if="aboutVisible" class="about-mask" @click="aboutVisible = false">
			<view class="about-popup" @click.stop>
				<image class="about-logo" role="img" aria-label="应用Logo" src="/static/logo.jpg" mode="aspectFill" />
				<view class="about-title">{{ appName }}</view>
				<view class="about-version">v1.0.0</view>
				<view class="about-desc">
					<view>数据中心综合布线项目管理系统·移动端</view>
					<view>为项目负责人提供高效的项目进度更新与资料查看能力</view>
				</view>
				<view class="about-btn" role="button" @click="aboutVisible = false">确定</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { userStore } from '@/store/user'
	import { projectApi } from '@/api/project'
	import { taskApi } from '@/api/task'
	import { notificationApi } from '@/api/notification'
	import { isProjectManager, isAdmin } from '@/utils/permission'
	import { APP_NAME } from '@/utils/config'
	import { auth } from '@/utils/auth'

	export default {
		data() {
			return {
				statusBarHeight: 20,            // 状态栏高度（rpx），用于头部避让
				stat: { total: 0, inProgress: 0, upcoming: 0 }, // 数据统计：负责项目/进行中/待办节点数
				unreadCount: 0,                 // 未读通知数（菜单角标）
				// 关于弹窗显隐：使用自定义弹窗以展示 Logo
				aboutVisible: false
			}
		},
		computed: {
			/** 当前用户名称 */
			userName() { return userStore.userName },
			/** 头像占位字符（取用户名首字母大写） */
			userInitial() {
				const n = this.userName || 'U'
				return n.charAt(0).toUpperCase()
			},
			/** 用户角色列表 */
			roles() { return userStore.roles },
			/** 是否可管理（PM/admin）—— 控制项目管理菜单组可见性 */
		canManage() { return isProjectManager() },
		/** 是否为管理员 —— 控制系统管理菜单组可见性 */
		canAdmin() { return isAdmin() },
		// 应用名称：从 config 常量获取，供关于弹窗使用
		appName() { return APP_NAME }
		},
		// 页面加载：读取系统状态栏高度用于头部布局（getWindowInfo 带降级，避免废弃 API 警告）
		onLoad() {
			try {
				const info = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
				this.statusBarHeight = info.statusBarHeight || 20
			} catch (e) {
				this.statusBarHeight = 20
			}
		},
		// 页面显示：已登录时加载个人数据统计与未读通知数
		onShow() {
			if (userStore.isLoggedIn) {
				this.loadStat()
				this.loadUnread()
			}
		},
		methods: {
			/** 加载个人数据统计（负责项目数、进行中、待办节点数；各接口独立容错） */
			async loadStat() {
				try {
					const [stats, upcoming] = await Promise.allSettled([
						projectApi.getStatistics(),
						taskApi.getUpcomingAll()
					])
					if (stats.status === 'fulfilled' && stats.value) {
						// 后端 ProjectStatisticsVO 字段映射
						this.stat.total = stats.value.totalCount ?? stats.value.total ?? 0
						this.stat.inProgress = stats.value.activeCount ?? stats.value.inProgress ?? 0
					}
					if (upcoming.status === 'fulfilled' && Array.isArray(upcoming.value)) {
						this.stat.upcoming = upcoming.value.length
					}
				} catch (e) {
					console.warn('stat error', e)
				}
			},
			/** 加载未读通知数（用于站内通知菜单角标，静默失败） */
			async loadUnread() {
				try {
					const count = await notificationApi.getUnreadCount()
					this.unreadCount = typeof count === 'number' ? count : (count?.count ?? 0)
				} catch (e) {
					// 静默失败
				}
			},
			/**
			 * 跳转页面：优先按 tabBar 切换，失败回退为普通页面导航
			 * @param {string} url 目标页面路径
			 */
			goPage(url) {
				uni.switchTab({ url, fail: () => uni.navigateTo({ url }) })
			},
			/** 刷新用户信息：重新拉取最新信息并根据结果轻提示 */
			async handleRefresh() {
				const info = await userStore.fetchUserInfo()
				uni.showToast({ title: info ? '已刷新' : '刷新失败', icon: info ? 'success' : 'none' })
			},
			/**
		 * 清除本地缓存（弹窗确认；清除后重新同步登录态，不影响登录状态）
		 *
		 * 安全要点（P0 修复）：
		 *   原 `uni.clearStorageSync()` 会清掉全部 storage，包括 hbsz_token / hbsz_refresh_token /
		 *   hbsz_user_info，导致清缓存后用户被强制登出，与文案"不影响登录状态"严重不符。
		 *   修复方案：先通过 auth 工具读取并备份认证信息，清空 storage 后再还原，
		 *   这样无论 key 命名如何变化都能稳定保留登录态。
		 */
		handleClearCache() {
			uni.showModal({
				title: '提示',
				content: '将清除本地缓存（不影响登录状态）',
				success: (res) => {
					if (!res.confirm) return
					// 1. 备份认证信息：token / refreshToken / userInfo（避免被 clearStorage 误清）
					const token = auth.getToken()
					const refreshToken = auth.getRefreshToken()
					const userInfo = auth.getUserInfo()
					// 2. 清空所有 storage（业务缓存 + 登录信息一并清除）
					uni.clearStorageSync()
					// 3. 还原认证信息：使用 auth.saveAuth 写回，保持 key 命名一致性
					auth.saveAuth({ token, refreshToken, userInfo })
					// 4. 通知 store 从 storage 重新同步状态
					userStore.syncFromStorage()
					uni.showToast({ title: '已清除', icon: 'success' })
				}
			})
		},
			/** 打开「关于应用」弹窗 */
			showAbout() {
				// 使用自定义弹窗展示 Logo + 应用信息，比原生 showModal 更直观
				this.aboutVisible = true
			},
			/** 退出登录：弹窗确认后调用 store 登出并 reLaunch 到登录页 */
			handleLogout() {
				uni.showModal({
					title: '提示',
					content: '确定要退出登录吗？',
					success: async (res) => {
						if (res.confirm) {
							uni.showLoading({ title: '退出中...', mask: true })
							await userStore.logout()
							uni.hideLoading()
							uni.reLaunch({ url: '/pages/login/login' })
						}
					}
				})
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		padding-bottom: 60rpx;
	}

	.hero {
		position: relative;
		margin-bottom: 80rpx;
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
		padding: 0 32rpx;
	}
	.user-card {
		display: flex;
		align-items: center;
	}
	.avatar {
		width: 120rpx;
		height: 120rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.25);
		border: 4rpx solid rgba(255, 255, 255, 0.5);
		color: #FFFFFF;
		font-size: 48rpx;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 24rpx;
	}
	.user-info { flex: 1; }
	.user-name {
		font-size: 38rpx;
		font-weight: 700;
		color: #FFFFFF;
		margin-bottom: 10rpx;
	}
	.user-role {
		display: flex;
		flex-wrap: wrap;
		gap: 8rpx;
	}
	.role-tag {
		font-size: 22rpx;
		color: #FFFFFF;
		background: rgba(255, 255, 255, 0.25);
		padding: 4rpx 14rpx;
		border-radius: 20rpx;
	}
	.role-text {
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.85);
	}

	.stat-card {
		margin: -60rpx 24rpx 0;
		background: #FFFFFF;
		border-radius: 20rpx;
		padding: 32rpx 0;
		display: flex;
		position: relative;
		z-index: 2;
		box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.08);
	}
	.stat-item {
		flex: 1;
		text-align: center;
		border-right: 1rpx solid #F0F2F5;
	}
	.stat-item:last-child { border-right: none; }
	.stat-num {
		font-size: 44rpx;
		font-weight: 700;
		color: #303133;
		line-height: 1.2;
	}
	.stat-num.primary { color: #096DD9; }
	.stat-num.warning { color: #D48806; }
	.stat-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}

	.menu-group {
		margin: 24rpx 24rpx 0;
	}
	.group-title {
		font-size: 24rpx;
		color: #5C6370;
		margin-bottom: 12rpx;
		padding-left: 8rpx;
	}
	.menu-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		overflow: hidden;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.menu-item {
		display: flex;
		align-items: center;
		padding: 28rpx 24rpx;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.menu-item:last-child { border-bottom: none; }
	.menu-icon {
		width: 64rpx;
		height: 64rpx;
		border-radius: 14rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 32rpx;
		margin-right: 20rpx;
		flex-shrink: 0;
	}
	.icon-blue { background: #E6F7FF; }
	.icon-purple { background: #F9F0FF; }
	.icon-green { background: #F6FFED; }
	.icon-orange { background: #FFF7E6; }
	.icon-gray { background: #F5F7FA; }
	.icon-cyan { background: #E6FFFB; }
	.icon-red { background: #FFF1F0; }
	.icon-yellow { background: #FFFBE6; }
	.icon-green2 { background: #F6FFED; }
	.icon-indigo { background: #F0F5FF; }
	.menu-badge {
		min-width: 32rpx;
		height: 32rpx;
		padding: 0 8rpx;
		border-radius: 16rpx;
		background: #FF4D4F;
		color: #FFFFFF;
		font-size: 20rpx;
		line-height: 32rpx;
		text-align: center;
		margin-right: 12rpx;
	}
	.svg-icon {
		width: 36rpx;
		height: 36rpx;
	}
	.menu-text {
		flex: 1;
		font-size: 28rpx;
		color: #303133;
	}
	.menu-arrow {
		font-size: 36rpx;
		color: #767E8A;
	}
	.menu-arrow.version {
		font-size: 24rpx;
		color: #5C6370;
	}

	.logout-area {
		padding: 48rpx 24rpx 16rpx;
	}
	.logout-btn {
		width: 100%;
		height: 88rpx;
		line-height: 88rpx;
		background: #FFFFFF;
		color: #D4380D;
		font-size: 30rpx;
		font-weight: 500;
		border-radius: 12rpx;
		border: none;
		&::after { border: none; }
	}

	.footer {
		text-align: center;
		font-size: 22rpx;
		color: #767E8A;
		padding: 16rpx 0;
	}

	/* 关于弹窗：遮罩 + 居中弹窗，适配各尺寸屏幕 */
	.about-mask {
		position: fixed;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 999;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.about-popup {
		width: 580rpx;
		background: #FFFFFF;
		border-radius: 24rpx;
		padding: 48rpx 40rpx 32rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.about-logo {
		width: 160rpx;
		height: 160rpx;
		border-radius: 24rpx;
		margin-bottom: 24rpx;
		box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.1);
	}
	.about-title {
		font-size: 34rpx;
		font-weight: 700;
		color: #1f2937;
		margin-bottom: 8rpx;
	}
	.about-version {
		font-size: 24rpx;
		color: #5C6370;
		margin-bottom: 24rpx;
	}
	.about-desc {
		font-size: 26rpx;
		color: #606266;
		line-height: 1.6;
		text-align: center;
		margin-bottom: 32rpx;
	}
	.about-desc view {
		margin-bottom: 8rpx;
	}
	.about-btn {
		width: 100%;
		height: 80rpx;
		line-height: 80rpx;
		text-align: center;
		background: #4080ff;
		color: #FFFFFF;
		border-radius: 12rpx;
		font-size: 30rpx;
	}
</style>
