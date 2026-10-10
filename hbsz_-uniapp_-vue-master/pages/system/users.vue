<!--
  用户管理页（管理员视角）
  业务用途：管理员移动端紧急处理用户账号问题，包括：
           - 查询用户列表（关键字/状态筛选）
           - 查看用户详情
           - 启用/禁用账号（紧急封禁）
           - 重置密码（紧急场景）
           小程序不支持 CRUD、批量导入、角色分配等复杂操作，需在 Web 端处理。
  数据来源：systemUserApi.getList（列表）、systemUserApi.getDetail（详情）、
           systemUserApi.updateStatus（状态切换）、systemUserApi.resetPassword（重置密码）。
  导航特性：非 tabBar 页；支持下拉刷新与上拉加载更多；仅 admin 可访问。
-->
<template>
	<view class="page">
		<!-- 顶部标题栏 -->
		<view class="nav-bar">
			<view class="nav-title">用户管理</view>
			<view class="nav-action" v-if="total > 0">{{ total > 99 ? '99+' : total }} 人</view>
		</view>

		<!-- 搜索栏 -->
		<view class="search-bar">
			<view class="search-input-wrap">
				<text class="search-icon" aria-hidden="true">🔍</text>
				<input
					class="search-input"
					v-model="keyword"
					aria-label="搜索用户"
					placeholder="搜索用户名/姓名/手机号"
					placeholder-class="ph"
					confirm-type="search"
					@confirm="onSearch"
					@input="onSearchInput"
				/>
				<text v-if="keyword" class="search-clear" role="button" aria-label="清空搜索" @click="clearKeyword">✕</text>
			</view>
		</view>

		<!-- 状态筛选 -->
		<view class="status-filter">
			<view
				v-for="opt in statusOptions"
				:key="opt.value"
				class="status-item"
				:class="{ active: currentStatus === opt.value }"
				role="button"
				@click="changeStatus(opt.value)"
			>{{ opt.label }}</view>
		</view>

		<!-- 用户列表 -->
		<view class="list">
			<view v-if="loading && list.length === 0" class="loading-tip">加载中...</view>
			<template v-else>
				<view
					v-for="u in list"
					:key="u.id"
					class="user-card"
					hover-class="uni-hover"
					:hover-stay-time="80"
					role="link"
					@click="goDetail(u)"
				>
					<view class="user-head">
						<view class="user-avatar">{{ initialOf(u.realName || u.username) }}</view>
						<view class="user-info">
							<view class="user-name">
								<text>{{ u.realName || u.username || '-' }}</text>
								<text v-if="u.status !== undefined" class="status-tag" :class="statusTagClass(u.status)">
									{{ statusTagText(u.status) }}
								</text>
							</view>
							<view class="user-meta">
								<text class="meta-text">@{{ u.username || '-' }}</text>
								<text v-if="u.phone" class="meta-text"><text aria-hidden="true">📞 </text>{{ u.phone }}</text>
							</view>
							<view v-if="u.roleName || u.roles" class="user-role">
								<text class="role-tag">{{ u.roleName || (Array.isArray(u.roles) ? u.roles.join('、') : u.roles) || '未分配' }}</text>
							</view>
						</view>
						<!-- 操作按钮（阻止冒泡，避免触发卡片点击） -->
						<view class="user-actions">
							<view
								v-if="u.status !== undefined"
								class="action-btn-mini"
								role="button"
								@click.stop="toggleStatus(u)"
							>{{ u.status === 1 ? '禁用' : '启用' }}</view>
							<view
								class="action-btn-mini reset"
								role="button"
								@click.stop="openResetDialog(u)"
							>重置密码</view>
						</view>
					</view>
				</view>

				<!-- 空状态 -->
				<view v-if="list.length === 0" class="empty">
					<view class="empty-icon" aria-hidden="true">👥</view>
					<text class="empty-text">暂无用户</text>
				</view>
				<view v-if="loading && list.length > 0" class="load-more">加载中...</view>
				<view v-else-if="noMore && list.length > 0" class="load-more">— 没有更多了 —</view>
			</template>
		</view>

		<view class="bottom-safe"></view>

		<!-- 重置密码弹窗 -->
		<view v-if="resetVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="resetVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">重置密码</view>
				<view class="popup-user-info">
					<text>用户：</text>
					<text class="popup-user-name">{{ currentUser.realName || currentUser.username || '-' }}</text>
				</view>
				<view class="form-item">
					<text class="form-label">新密码<text class="req">*</text></text>
					<input
						class="form-input"
						v-model="resetForm.newPassword"
						type="text"
						password
						aria-label="新密码"
						placeholder="请输入新密码（8-20位）"
						placeholder-class="ph"
						maxlength="20"
					/>
				</view>
				<view class="form-item">
					<text class="form-label">确认密码<text class="req">*</text></text>
					<input
						class="form-input"
						v-model="resetForm.confirmPassword"
						type="text"
						password
						aria-label="确认密码"
						placeholder="请再次输入新密码"
						placeholder-class="ph"
						maxlength="20"
					/>
				</view>
				<view class="popup-actions">
					<button class="btn-cancel" @click="resetVisible=false">取消</button>
					<button class="btn-confirm" :loading="submitting" :disabled="submitting" @click="confirmReset">确认重置</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { systemUserApi } from '@/api/systemUser'
	import { isAdmin } from '@/utils/permission'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				hasPermission: true,    // 权限校验
				keyword: '',            // 搜索关键字
				currentStatus: '',      // 当前状态筛选（''=全部）
				statusOptions: [
					{ label: '全部', value: '' },
					{ label: '启用', value: 1 },
					{ label: '禁用', value: 0 }
				],
				list: [],               // 用户列表
				page: 1,                // 当前页码
				size: 20,               // 每页条数
				total: 0,               // 总条数
				loading: false,         // 加载中标记
				noMore: false,          // 是否已无更多
				searchTimer: null,      // 搜索防抖定时器
				// 重置密码弹窗
				resetVisible: false,
				currentUser: {},
				resetForm: {
					newPassword: '',
					confirmPassword: ''
				},
				submitting: false
			}
		},
		onLoad() {
			this.hasPermission = isAdmin()
			if (!this.hasPermission) {
				uni.showToast({ title: '无权限访问', icon: 'none' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
				return
			}
			this.refresh()
		},
		onShow() {
			if (this.hasPermission && this.list.length > 0) this.loadList()
		},
		onPullDownRefresh() {
			if (!this.hasPermission) {
				uni.stopPullDownRefresh()
				return
			}
			this.refresh(true)
		},
		onReachBottom() {
			this.loadMore()
		},
		onUnload() {
			if (this.searchTimer) {
				clearTimeout(this.searchTimer)
				this.searchTimer = null
			}
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			/** 取用户名/姓名首字符作为头像展示 */
			initialOf(name) {
				if (!name) return '?'
				return String(name).charAt(0).toUpperCase()
			},
			/** 状态标签样式 */
			statusTagClass(status) {
				return status === 1 ? 'status-enabled' : 'status-disabled'
			},
			/** 状态标签文本 */
			statusTagText(status) {
				return status === 1 ? '启用' : '禁用'
			},
			/** 点击搜索 */
			onSearch() { this.refresh() },
			/** 输入防抖搜索 */
			onSearchInput() {
				if (this.searchTimer) clearTimeout(this.searchTimer)
				this.searchTimer = setTimeout(() => {
					this.refresh()
				}, 400)
			},
			/** 清空关键字并刷新 */
			clearKeyword() {
				this.keyword = ''
				this.refresh()
			},
			/** 切换状态筛选 */
			changeStatus(v) {
				if (this.currentStatus === v) return
				this.currentStatus = v
				this.refresh()
			},
			/** 刷新列表（重置到第一页） */
			async refresh(stopPull) {
				this.page = 1
				this.noMore = false
				await this.loadList()
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载下一页 */
			async loadMore() {
				if (this.loading || this.noMore) return
				await this.loadList(true, this.page + 1)
			},
			/** 加载列表数据 */
			async loadList(append = false, loadPage) {
				if (this.loading) return
				this.loading = true
				const page = loadPage || this.page
				try {
					const params = { pageNum: page, pageSize: this.size }
					if (this.keyword) params.keyword = this.keyword
					if (this.currentStatus !== '') params.status = this.currentStatus
					const res = await systemUserApi.getList(params)
					const list = (res && (res.records || res.list || res)) || []
					const total = (res && (res.total || res.totalCount)) || 0
					if (append) {
						this.list = this.list.concat(list)
					} else {
						this.list = list
					}
					this.total = total
					this.noMore = this.list.length >= total
					this.page = page
				} catch (e) {
					console.warn('user list error', e)
					if (!append) this.list = []
				} finally {
					this.loading = false
				}
			},
			/** 跳转用户详情（暂未单独页，弹出提示） */
			goDetail(u) {
				uni.showActionSheet({
					itemList: ['查看详情', '禁用/启用账号', '重置密码'],
					success: (res) => {
						if (res.tapIndex === 0) this.showUserDetail(u)
						else if (res.tapIndex === 1) this.toggleStatus(u)
						else if (res.tapIndex === 2) this.openResetDialog(u)
					}
				})
			},
			/** 显示用户详情 */
			showUserDetail(u) {
				const lines = [
					`用户名：${u.username || '-'}`,
					`姓名：${u.realName || '-'}`,
					`手机：${u.phone || '-'}`,
					`邮箱：${u.email || '-'}`,
					`角色：${u.roleName || (Array.isArray(u.roles) ? u.roles.join('、') : (u.roles || '-'))}`,
					`状态：${this.statusTagText(u.status)}`,
					`创建时间：${u.createTime || '-'}`
				]
				uni.showModal({
					title: '用户详情',
					content: lines.join('\n'),
					showCancel: false,
					confirmText: '关闭'
				})
			},
			/** 切换用户状态（启用/禁用） */
			toggleStatus(u) {
				const targetStatus = u.status === 1 ? 0 : 1
				const actionText = targetStatus === 1 ? '启用' : '禁用'
				uni.showModal({
					title: '确认操作',
					content: `确定要${actionText}用户「${u.realName || u.username}」吗？`,
					confirmColor: targetStatus === 1 ? '#52C41A' : '#FF4D4F',
					confirmText: actionText,
					success: async (res) => {
						if (!res.confirm) return
						uni.showLoading({ title: '处理中', mask: true })
						try {
							await systemUserApi.updateStatus(u.id, targetStatus)
							// 本地同步状态
							u.status = targetStatus
							uni.showToast({ title: `${actionText}成功`, icon: 'success' })
						} catch (e) {
							console.warn('toggle status error', e)
						} finally {
							uni.hideLoading()
						}
					}
				})
			},
			/** 打开重置密码弹窗 */
			openResetDialog(u) {
				this.currentUser = u
				this.resetForm = { newPassword: '', confirmPassword: '' }
				this.resetVisible = true
			},
			/** 确认重置密码 */
			async confirmReset() {
				if (this.submitting) return
				const { newPassword, confirmPassword } = this.resetForm
				if (!newPassword) {
					return uni.showToast({ title: '请输入新密码', icon: 'none' })
				}
				if (newPassword.length < 8) {
					return uni.showToast({ title: '密码至少 8 位', icon: 'none' })
				}
				if (newPassword !== confirmPassword) {
					return uni.showToast({ title: '两次密码不一致', icon: 'none' })
				}
				this.submitting = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await systemUserApi.resetPassword(this.currentUser.id, { newPassword, confirmPassword })
					this.resetVisible = false
					uni.showToast({ title: '重置成功', icon: 'success' })
				} catch (e) {
					console.warn('reset password error', e)
				} finally {
					this.submitting = false
					uni.hideLoading()
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

	/* 顶部标题栏 */
	.nav-bar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 8rpx 4rpx 16rpx;
	}
	.nav-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
	}
	.nav-action {
		font-size: 24rpx;
		color: #5C6370;
		padding: 4rpx 16rpx;
		background: #FFFFFF;
		border-radius: 24rpx;
	}

	/* 搜索栏 */
	.search-bar {
		padding: 8rpx 0 20rpx;
	}
	.search-input-wrap {
		display: flex;
		align-items: center;
		background: #FFFFFF;
		border-radius: 32rpx;
		padding: 0 24rpx;
		height: 72rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.search-icon {
		font-size: 28rpx;
		margin-right: 12rpx;
	}
	.search-input {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
	}
	.ph { color: #767E8A; }
	.search-clear {
		color: #767E8A;
		font-size: 24rpx;
		padding: 0 8rpx;
	}

	/* 状态筛选 */
	.status-filter {
		display: flex;
		gap: 12rpx;
		padding-bottom: 20rpx;
	}
	.status-item {
		padding: 8rpx 24rpx;
		background: #FFFFFF;
		border-radius: 24rpx;
		font-size: 24rpx;
		color: #5C6370;
	}
	.status-item.active {
		background: #1890FF;
		color: #FFFFFF;
	}

	/* 列表 */
	.list {
		padding-top: 4rpx;
	}
	.loading-tip {
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
		padding: 80rpx 0;
	}
	.user-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.user-head {
		display: flex;
		align-items: flex-start;
		gap: 20rpx;
	}
	.user-avatar {
		width: 80rpx;
		height: 80rpx;
		border-radius: 50%;
		background: linear-gradient(135deg, #1890FF, #096DD9);
		color: #FFFFFF;
		font-size: 36rpx;
		font-weight: 600;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}
	.user-info {
		flex: 1;
		min-width: 0;
	}
	.user-name {
		display: flex;
		align-items: center;
		gap: 12rpx;
		font-size: 30rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 6rpx;
	}
	.status-tag {
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		font-size: 20rpx;
		font-weight: 400;
	}
	.status-enabled {
		background: #F6FFED;
		color: #52C41A;
	}
	.status-disabled {
		background: #FFF1F0;
		color: #FF4D4F;
	}
	.user-meta {
		display: flex;
		gap: 16rpx;
		margin-bottom: 8rpx;
		flex-wrap: wrap;
	}
	.meta-text {
		font-size: 24rpx;
		color: #5C6370;
	}
	.user-role {
		margin-top: 4rpx;
	}
	.role-tag {
		display: inline-block;
		padding: 4rpx 12rpx;
		background: #E6F7FF;
		color: #096DD9;
		border-radius: 8rpx;
		font-size: 22rpx;
	}
	.user-actions {
		display: flex;
		flex-direction: column;
		gap: 8rpx;
		flex-shrink: 0;
	}
	.action-btn-mini {
		padding: 8rpx 16rpx;
		background: #FFFBE6;
		color: #FAAD14;
		border: 1rpx solid #FFE58F;
		border-radius: 8rpx;
		font-size: 22rpx;
		text-align: center;
		white-space: nowrap;
	}
	.action-btn-mini.reset {
		background: #FFF1F0;
		color: #FF4D4F;
		border-color: #FFA39E;
	}

	/* 空态 */
	.empty {
		text-align: center;
		padding: 120rpx 0;
		color: #909399;
	}
	.empty-icon {
		font-size: 96rpx;
		margin-bottom: 16rpx;
	}
	.empty-text {
		font-size: 26rpx;
		display: block;
	}

	.load-more {
		text-align: center;
		color: #909399;
		font-size: 24rpx;
		padding: 24rpx 0;
	}

	/* 重置密码弹窗 */
	.popup-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 999;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 60rpx;
	}
	.popup {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 32rpx;
		width: 100%;
		max-width: 620rpx;
	}
	.popup-title {
		font-size: 32rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 16rpx;
	}
	.popup-user-info {
		font-size: 26rpx;
		color: #5C6370;
		margin-bottom: 24rpx;
	}
	.popup-user-name {
		color: #303133;
		font-weight: 600;
	}
	.form-item {
		margin-bottom: 16rpx;
	}
	.form-label {
		display: block;
		font-size: 24rpx;
		color: #5C6370;
		margin-bottom: 8rpx;
	}
	.req { color: #FF4D4F; }
	.form-input {
		width: 100%;
		height: 72rpx;
		padding: 0 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.popup-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 24rpx;
	}
	.popup-actions button {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		padding: 0;
		margin: 0;
		border: none;
	}
	.btn-cancel {
		background: #F5F7FA;
		color: #5C6370;
	}
	.btn-confirm {
		background: #1890FF;
		color: #FFFFFF;
	}

	.bottom-safe {
		height: env(safe-area-inset-bottom);
		padding-bottom: 20rpx;
	}
</style>
