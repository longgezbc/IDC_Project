<!--
  站内通知列表页
  业务用途：展示当前用户的站内通知，支持按"全部/未读"筛选、分页加载、下拉刷新、
           单条已读、全部已读、删除、点击跳转关联业务（任务/项目）。
           通过 WebSocket 适配层（utils/ws.js）接收实时推送，新通知自动置顶并提示。
  数据来源：notificationApi（REST 查询与操作）；utils/ws（实时推送）；utils/format（时间格式化）。
  导航特性：非 tabBar 页，点击通知可跳转至 /pages/progress/report 或 /pages/project/detail。
-->
<template>
	<view class="page">
		<!-- 顶部标题栏 -->
		<view class="header">
			<text class="header-title">站内通知</text>
			<text
				class="header-action"
				:class="{ disabled: unreadCount === 0 }"
				role="button"
				@click="markAllRead"
			>全部已读</text>
		</view>

		<!-- 筛选标签 -->
		<view class="tabs">
			<view
				class="tab-item"
				:class="{ active: activeTab === 'all' }"
				role="button"
				@click="switchTab('all')"
			>全部</view>
			<view
				class="tab-item"
				:class="{ active: activeTab === 'unread' }"
				role="button"
				@click="switchTab('unread')"
			>
				<text>未读</text>
				<text v-if="unreadCount > 0" class="tab-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
			</view>
		</view>

		<!-- 通知列表 -->
		<view v-if="list.length > 0" class="list">
			<view
				v-for="item in list"
				:key="item.id"
				class="card"
				hover-class="card-hover"
				:hover-stay-time="80"
				role="link"
				:aria-label="item.title + '，来自' + (item.senderName || '系统')"
				@click="handleCardClick(item)"
				@longpress="confirmDelete(item)"
			>
				<!-- 未读小红点 -->
				<view v-if="!item.isRead" class="unread-dot"></view>
				<!-- 类型图标 -->
				<view class="type-icon" :style="{ background: getTypeConfig(item).color }">
					<text class="icon-text">{{ getTypeConfig(item).icon }}</text>
				</view>
				<!-- 内容区 -->
				<view class="card-body">
					<view class="card-title-text">{{ item.title }}</view>
					<view class="card-content">{{ item.content }}</view>
					<view class="card-meta">{{ item.senderName || '系统' }} · {{ formatRelativeTime(item.createTime) }}</view>
				</view>
			</view>

			<!-- 分页加载状态 -->
			<view v-if="loadingMore" class="load-status">加载中...</view>
			<view v-else-if="list.length >= total && total > 0" class="load-status">没有更多了</view>
		</view>

		<!-- 首次加载中 -->
		<view v-else-if="loading" class="load-status">加载中...</view>

		<!-- 空状态 -->
		<view v-else class="empty">
			<text class="empty-icon">🔔</text>
			<text class="empty-text">暂无通知</text>
		</view>
	</view>
</template>

<script>
	import { notificationApi } from '@/api/notification'
	import { formatDateTime } from '@/utils/format'
	import { startNotificationWatch, stopNotificationWatch, refreshUnread } from '@/utils/ws'

	export default {
		data() {
			return {
				list: [],           // 通知列表
				loading: false,     // 首次加载中
				loadingMore: false, // 加载更多中
				pageNum: 1,         // 当前页码
				pageSize: 20,       // 每页条数
				total: 0,           // 总条数
				activeTab: 'all',   // 筛选标签：'all' | 'unread'
				unreadCount: 0      // 未读数（用于标签角标）
			}
		},
		onLoad() {
			this.loadList()
			// 启动 WebSocket / 轮询监听
			startNotificationWatch({
				onMessage: (data) => this.handleWsMessage(data),
				onUnreadCount: (count) => this.updateUnread(count)
			})
			// 主动拉取一次未读数
			refreshUnread()
		},
		onUnload() {
			stopNotificationWatch()
		},
		onPullDownRefresh() {
			this.loadList().finally(() => {
				uni.stopPullDownRefresh()
			})
		},
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			/** 加载通知列表（重置到第一页） */
			async loadList() {
				this.loading = true
				this.pageNum = 1
				try {
					const params = {
						pageNum: this.pageNum,
						pageSize: this.pageSize
					}
					if (this.activeTab === 'unread') {
						params.isRead = false
					}
					const res = await notificationApi.getList(params)
					this.list = (res && res.records) || []
					this.total = (res && res.total) || 0
				} catch (e) {
					console.warn('[notification] loadList failed', e)
				} finally {
					this.loading = false
				}
			},
			/** 加载更多（分页） */
			async loadMore() {
				if (this.loading || this.loadingMore) return
				if (this.list.length >= this.total) return
				this.loadingMore = true
				try {
					this.pageNum++
					const params = {
						pageNum: this.pageNum,
						pageSize: this.pageSize
					}
					if (this.activeTab === 'unread') {
						params.isRead = false
					}
					const res = await notificationApi.getList(params)
					const records = (res && res.records) || []
					this.list = this.list.concat(records)
					this.total = (res && res.total) || 0
				} catch (e) {
					this.pageNum--
					console.warn('[notification] loadMore failed', e)
				} finally {
					this.loadingMore = false
				}
			},
			/** 切换筛选标签 */
			switchTab(tab) {
				if (this.activeTab === tab) return
				this.activeTab = tab
				this.loadList()
			},
			/** 点击通知：未读先标记已读，再按 relatedType 跳转 */
			handleCardClick(item) {
				if (!item.isRead) {
					this.markAsReadSingle(item)
				}
				if (!item.relatedId || !item.relatedType) return
				if (item.relatedType === 'task') {
					uni.navigateTo({
						url: `/pages/progress/report?taskId=${item.relatedId}`
					})
				} else if (item.relatedType === 'project') {
					uni.navigateTo({
						url: `/pages/project/detail?id=${item.relatedId}`
					})
				}
			},
			/** 标记单条已读（乐观更新 UI） */
			async markAsReadSingle(item) {
				if (item.isRead) return
				// 用 map 重建数组确保 Vue 2 响应式触发，立即更新列表项的未读样式
				this.list = this.list.map(n => n.id === item.id ? { ...n, isRead: true } : n)
				this.unreadCount = Math.max(0, this.unreadCount - 1)
				try {
					await notificationApi.markAsRead(item.id)
				} catch (e) {
					console.warn('[notification] markAsRead failed', e)
				}
			},
			/** 全部标记已读 */
			markAllRead() {
				if (this.unreadCount === 0) {
					return uni.showToast({ title: '暂无未读通知', icon: 'none' })
				}
				uni.showModal({
					title: '提示',
					content: '确定将全部通知标记为已读？',
					success: async (res) => {
						if (!res.confirm) return
						uni.showLoading({ title: '处理中', mask: true })
						try {
							await notificationApi.markAllAsRead()
							this.list.forEach((item) => { item.isRead = true })
							this.unreadCount = 0
							uni.showToast({ title: '已全部标记为已读', icon: 'success' })
						} catch (e) {
							console.warn('[notification] markAllAsRead failed', e)
							uni.showToast({ title: '操作失败', icon: 'none' })
						} finally {
							uni.hideLoading()
						}
					}
				})
			},
			/** 长按删除确认 */
			confirmDelete(item) {
				uni.showModal({
					title: '提示',
					content: '确定删除该通知？',
					success: async (res) => {
						if (!res.confirm) return
						try {
							await notificationApi.remove(item.id)
							const idx = this.list.findIndex((n) => n.id === item.id)
							if (idx > -1) {
								this.list.splice(idx, 1)
								this.total = Math.max(0, this.total - 1)
								if (!item.isRead) {
									this.unreadCount = Math.max(0, this.unreadCount - 1)
								}
							}
							uni.showToast({ title: '已删除', icon: 'success' })
						} catch (e) {
							console.warn('[notification] remove failed', e)
							uni.showToast({ title: '删除失败', icon: 'none' })
						}
					}
				})
			},
			/** WebSocket 收到新消息：置顶并提示 */
			handleWsMessage(data) {
				if (!data) return
				// 兼容直接通知对象或 { notification: {...} } 包装
				const notification = data.notification || data
				if (!notification || (!notification.id && !notification.title)) return
				// 避免重复
				if (this.list.some((n) => n.id === notification.id)) return
				this.list.unshift(notification)
				this.total++
				if (notification.isRead === false || notification.isRead === undefined) {
					this.unreadCount++
				}
				uni.showToast({ title: '收到新通知', icon: 'none' })
			},
			/** 更新未读数（由 WS 适配层回调） */
			updateUnread(count) {
				this.unreadCount = typeof count === 'number' ? count : 0
			},
			/** 获取通知类型图标与颜色配置 */
			getTypeConfig(item) {
				const type = item && item.notifyType
				const map = {
					progress_report: { icon: '📝', color: '#1890FF' },
					task_assign: { icon: '📋', color: '#722ED1' },
					project_archive: { icon: '📦', color: '#909399' },
					task_due: { icon: '⏰', color: '#FAAD14' },
					system: { icon: '🔔', color: '#1890FF' }
				}
				if (type === 'progress_review') {
					// 根据内容判断通过/驳回
					const content = (item && item.content) || ''
					const isRejected = /拒|驳回|不通过|未通过|reject/i.test(content)
					return isRejected
						? { icon: '✗', color: '#FF4D4F' }
						: { icon: '✓', color: '#52C41A' }
				}
				return map[type] || { icon: '🔔', color: '#1890FF' }
			},
			/** 相对时间格式化（刚刚/N分钟前/N小时前/昨天/完整时间） */
			formatRelativeTime(date) {
				if (!date) return ''
				const d = new Date(date)
				if (isNaN(d.getTime())) return ''
				const now = new Date()
				const diff = (now - d) / 1000
				if (diff < 60) return '刚刚'
				if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
				if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
				if (diff < 172800) return '昨天'
				return formatDateTime(date)
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		background: #F5F7FA;
		padding: 24rpx 24rpx 40rpx;
		box-sizing: border-box;
	}

	/* 顶部标题栏 */
	.header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 8rpx 4rpx 20rpx;
	}
	.header-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
	}
	.header-action {
		font-size: 24rpx;
		color: #096DD9;
		padding: 8rpx 16rpx;
	}
	.header-action.disabled {
		color: #767E8A;
	}

	/* 筛选标签 */
	.tabs {
		display: flex;
		gap: 16rpx;
		margin-bottom: 24rpx;
	}
	.tab-item {
		flex: 1;
		text-align: center;
		padding: 14rpx 0;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 40rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
	}
	.tab-item.active {
		background: #1890FF;
		color: #FFFFFF;
		font-weight: 600;
	}
	.tab-badge {
		display: inline-block;
		min-width: 32rpx;
		height: 32rpx;
		line-height: 32rpx;
		padding: 0 8rpx;
		border-radius: 16rpx;
		background: #FF4D4F;
		color: #FFFFFF;
		font-size: 20rpx;
		text-align: center;
	}
	.tab-item.active .tab-badge {
		background: #FFFFFF;
		color: #096DD9;
	}

	/* 通知列表 */
	.list {
		padding-bottom: 20rpx;
	}
	.card {
		position: relative;
		display: flex;
		align-items: flex-start;
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.card-hover {
		opacity: 0.85;
	}

	/* 未读小红点 */
	.unread-dot {
		position: absolute;
		top: 16rpx;
		right: 16rpx;
		width: 16rpx;
		height: 16rpx;
		border-radius: 50%;
		background: #FF4D4F;
	}

	/* 类型图标 */
	.type-icon {
		width: 80rpx;
		height: 80rpx;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		margin-right: 20rpx;
	}
	.icon-text {
		font-size: 36rpx;
		line-height: 1;
	}

	/* 内容区 */
	.card-body {
		flex: 1;
		min-width: 0;
		padding-right: 24rpx;
	}
	.card-title-text {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		margin-bottom: 8rpx;
	}
	.card-content {
		font-size: 24rpx;
		color: #606266;
		line-height: 1.5;
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		overflow: hidden;
		margin-bottom: 12rpx;
	}
	.card-meta {
		font-size: 22rpx;
		color: #5C6370;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	/* 加载状态 */
	.load-status {
		text-align: center;
		padding: 32rpx 0;
		font-size: 24rpx;
		color: #5C6370;
	}

	/* 空状态 */
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 160rpx 0;
	}
	.empty-icon {
		font-size: 96rpx;
		line-height: 1;
		margin-bottom: 24rpx;
	}
	.empty-text {
		font-size: 28rpx;
		color: #5C6370;
	}
</style>
