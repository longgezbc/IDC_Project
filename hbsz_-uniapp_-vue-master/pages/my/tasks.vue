<!--
  我的任务页
  业务用途：展示当前登录用户被分派的所有任务，支持按任务状态筛选、分页加载、
           下拉刷新与上拉加载更多；卡片展示任务名称、所属项目、负责人、起止日期、
           状态、进度与截止日提醒，点击卡片跳转至进度填报页。
  数据来源：dashboardApi.getMyTasks（分页查询当前用户任务）。
  导航特性：非 tabBar 页；支持下拉刷新（onPullDownRefresh）与触底加载更多（onReachBottom）；
           点击任务卡片携带 taskId、projectId 跳转至 /pages/progress/report。
-->
<template>
	<view class="page">
		<!-- 页头：标题 + 任务总数 -->
		<view class="header">
			<view class="header-left">
				<text class="header-title">我的任务</text>
				<view class="count-badge" v-if="total > 0">{{ total }}</view>
			</view>
			<text class="header-sub">共 {{ total }} 项任务</text>
		</view>

		<!-- 状态筛选（横向滚动 pill 风格） -->
		<scroll-view class="filter-bar" scroll-x>
			<view
			v-for="item in statusFilters"
			:key="item.value"
			class="filter-item"
			role="button"
			:class="{ active: currentStatus === item.value }"
			@click="changeStatus(item.value)"
		>
				{{ item.label }}
			</view>
		</scroll-view>

		<!-- 汇总统计卡片：总数 / 进行中 / 待开始 / 已完成 / 逾期 -->
		<view class="summary-card">
			<view class="summary-item">
				<view class="summary-num total">{{ summary.total }}</view>
				<view class="summary-label">总数</view>
			</view>
			<view class="summary-divider"></view>
			<view class="summary-item">
				<view class="summary-num in-progress">{{ summary.inProgress }}</view>
				<view class="summary-label">进行中</view>
			</view>
			<view class="summary-divider"></view>
			<view class="summary-item">
				<view class="summary-num pending">{{ summary.pending }}</view>
				<view class="summary-label">待开始</view>
			</view>
			<view class="summary-divider"></view>
			<view class="summary-item">
				<view class="summary-num completed">{{ summary.completed }}</view>
				<view class="summary-label">已完成</view>
			</view>
			<view class="summary-divider"></view>
			<view class="summary-item">
				<view class="summary-num overdue">{{ summary.overdue }}</view>
				<view class="summary-label">逾期</view>
			</view>
		</view>

		<!-- 任务列表 -->
		<view class="list">
			<!-- 首屏加载骨架（3 行） -->
			<view v-if="loading && list.length === 0" class="skeleton-wrap">
				<view class="skeleton-card" v-for="i in 3" :key="i">
					<view class="sk-line sk-title"></view>
					<view class="sk-line sk-meta"></view>
					<view class="sk-line sk-progress"></view>
				</view>
			</view>

			<template v-else>
				<view
				v-for="t in list"
				:key="t.id"
				class="task-card"
				role="link"
				hover-class="uni-hover"
				:hover-stay-time="80"
				@click="goReport(t)"
			>
					<!-- 标题行：任务名 + 里程碑/关键标签 + 状态标签 -->
					<view class="task-head">
						<view class="task-name">
							<text class="name-text">{{ t.name }}</text>
							<text v-if="t.isMilestone" class="tag tag-milestone">里程碑</text>
							<text v-else-if="t.isKey" class="tag tag-key">关键</text>
						</view>
						<view class="task-status" :style="statusStyle(t.status)">{{ taskStatusText(t.status) }}</view>
					</view>

					<!-- 项目 + 负责人 -->
					<view class="task-meta">
						<text v-if="t.projectName" class="meta-text">
							<image class="meta-svg" src="/static/icon/项目.svg" mode="aspectFit" aria-hidden="true" /> {{ t.projectName }}
						</text>
						<text v-if="t.assignee" class="meta-text">
							<image class="meta-svg" src="/static/icon/我的.svg" mode="aspectFit" aria-hidden="true" /> {{ t.assignee }}
						</text>
					</view>

					<!-- 日期范围 -->
					<view class="task-meta">
						<text class="meta-text">
							<image class="meta-svg" src="/static/icon/日期.svg" mode="aspectFit" aria-hidden="true" />
							{{ formatDate(t.startDate) }} ~ {{ formatDate(t.endDate) }}
						</text>
					</view>

					<!-- 进度行 -->
					<view class="task-progress-row">
						<view class="progress-bar">
							<view class="progress-inner" :style="progressStyle(t)"></view>
						</view>
						<text class="progress-text">{{ t.progress || 0 }}%</text>
					</view>

					<!-- 截止提醒 -->
					<view v-if="t.endDate && deadlineInfo(t).text" class="task-deadline" :class="deadlineInfo(t).cls">
						<image class="meta-svg" src="/static/icon/闹钟.svg" mode="aspectFit" aria-hidden="true" />
						<text>{{ deadlineInfo(t).text }}</text>
					</view>
				</view>

				<!-- 空状态 -->
				<view v-if="list.length === 0" class="empty">
					<view class="empty-icon" aria-hidden="true">
						<image class="svg-icon-lg" src="/static/icon/任务.svg" mode="aspectFit" />
					</view>
					<text>暂无任务</text>
				</view>

				<!-- 加载更多 / 没有更多 -->
				<view v-if="loading && list.length > 0" class="load-more">加载中...</view>
				<view v-else-if="noMore && list.length > 0" class="load-more">— 没有更多了 —</view>
			</template>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { dashboardApi } from '@/api/dashboard'
	import { formatDate, getTaskStatus } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				currentStatus: '',    // 当前选中的状态筛选值（''=全部）
				statusFilters: [      // 状态筛选选项
					{ label: '全部', value: '' },
					{ label: '进行中', value: '进行中' },
					{ label: '未开始', value: '未开始' },
					{ label: '已完成', value: '已完成' }
				],
				list: [],             // 任务列表数据
				pageNum: 1,           // 当前页码
				pageSize: 10,         // 每页条数
				total: 0,             // 总条数
				loading: false,       // 加载中标记
				noMore: false,        // 是否已无更多数据
				// 汇总统计：从已加载列表客户端遍历计算（总数/进行中/待开始/已完成/逾期）
				summary: {
					total: 0,
					inProgress: 0,
					pending: 0,
					completed: 0,
					overdue: 0
				}
			}
		},
		// 页面加载：首屏拉取任务列表
		onLoad() {
			this.refresh()
		},
		// 页面显示：从填报页返回时刷新列表（保证进度/状态变更即时同步）
		// 注意：调用 loadList(false) 保留当前 pageNum，避免回到第 1 页造成体验差
		onShow() {
			if (this.list.length > 0) this.loadList(false)
		},
		// 下拉刷新：重置到第一页并重新加载
		onPullDownRefresh() {
			this.refresh(true)
		},
		// 触底加载更多：追加下一页
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			formatDate,
			// taskStatusText 由 statusMixin 提供
			/** 任务状态标签内联样式（文字色 + 半透明同色背景） */
			statusStyle(s) {
				const o = getTaskStatus(s)
				return { color: o.color, background: o.color + '20' }
			},
			/** 进度条内联宽度样式 */
			progressStyle(t) {
				return { width: (t.progress || 0) + '%' }
			},
			/**
			 * 截止日提醒：逾期红色、≤3 天黄色、其余不展示
			 * @param {Object} task 任务对象
			 * @returns {{text:string,cls:string}} 提醒文案与样式类
			 */
			deadlineInfo(task) {
				if (!task.endDate) return { text: '', cls: '' }
				// 已完成的任务不再展示截止提醒
				if (task.status === '已完成') return { text: '', cls: '' }
				const days = Math.ceil((new Date(task.endDate) - new Date()) / (1000 * 60 * 60 * 24))
				if (days < 0) return { text: `已逾期 ${Math.abs(days)} 天`, cls: 'danger' }
				if (days === 0) return { text: '今日截止', cls: 'warning' }
				if (days <= 3) return { text: `还剩 ${days} 天截止`, cls: 'warning' }
				return { text: '', cls: '' }
			},
			/**
			 * 计算汇总统计（基于已加载列表客户端遍历）
			 * 分类规则：
			 *   - 已完成：status === '已完成' 或 progress >= 100
			 *   - 待开始：status === '待开始'/'未开始' 或 progress === 0
			 *   - 进行中：status === '进行中' 或 0 < progress < 100
			 *   - 逾期：endDate < today 且未完成（与进行中/待开始重叠计数）
			 */
			computeSummary() {
				const today = new Date()
				let inProgress = 0, pending = 0, completed = 0, overdue = 0
				;(this.list || []).forEach(t => {
					const progress = Number(t.progress) || 0
					const done = t.status === '已完成' || progress >= 100
					const isPending = !done && (t.status === '待开始' || t.status === '未开始' || progress === 0)
					if (done) completed++
					else if (isPending) pending++
					else inProgress++
					if (t.endDate && !done && new Date(t.endDate) < today) overdue++
				})
				this.summary = {
					total: (this.list || []).length,
					inProgress,
					pending,
					completed,
					overdue
				}
			},
			/** 切换状态筛选并刷新首屏 */
			changeStatus(v) {
				if (this.currentStatus === v) return
				this.currentStatus = v
				this.refresh()
			},
			/**
			 * 刷新列表（重置到第一页）
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async refresh(stopPull) {
				this.pageNum = 1
				this.noMore = false
				await this.loadList()
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载下一页（追加模式） */
			async loadMore() {
				if (this.loading || this.noMore) return
				this.pageNum++
				await this.loadList(true)
			},
			/**
			 * 加载列表数据
			 * @param {boolean} append 是否追加到现有列表（false 为替换）
			 */
			async loadList(append = false) {
				if (this.loading) return
				this.loading = true
				try {
					const params = {
						pageNum: this.pageNum,
						pageSize: this.pageSize
					}
					if (this.currentStatus) params.status = this.currentStatus
					const res = await dashboardApi.getMyTasks(params) || {}
					const list = Array.isArray(res.records) ? res.records : []
					const total = res.total || 0
					if (append) {
						this.list = this.list.concat(list)
					} else {
						this.list = list
					}
					this.total = total
					this.noMore = this.list.length >= total
				} catch (e) {
					console.warn('my tasks error', e)
					uni.showToast({ title: '加载失败，请重试', icon: 'none' })
				} finally {
					this.loading = false
					this.computeSummary()
				}
			},
			/** 跳转进度填报页 */
			goReport(task) {
				uni.navigateTo({
					url: `/pages/progress/report?taskId=${task.id}&projectId=${task.projectId || ''}`
				})
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

	/* 页头 */
	.header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 8rpx 4rpx 20rpx;
	}
	.header-left {
		display: flex;
		align-items: center;
	}
	.header-title {
		font-size: 40rpx;
		font-weight: 700;
		color: #303133;
	}
	.count-badge {
		margin-left: 16rpx;
		min-width: 40rpx;
		height: 40rpx;
		line-height: 40rpx;
		padding: 0 12rpx;
		border-radius: 20rpx;
		background: #1890FF;
		color: #FFFFFF;
		font-size: 24rpx;
		font-weight: 600;
		text-align: center;
	}
	.header-sub {
		font-size: 24rpx;
		color: #5C6370;
	}

	/* 状态筛选（横向滚动 pill） */
	.filter-bar {
		white-space: nowrap;
		padding: 8rpx 0 20rpx;
	}
	.filter-item {
		display: inline-block;
		padding: 12rpx 32rpx;
		margin-right: 16rpx;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 32rpx;
	}
	.filter-item.active {
		background: #1890FF;
		color: #FFFFFF;
	}

	/* 汇总统计卡片 */
	.summary-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx 0;
		margin-bottom: 20rpx;
		display: flex;
		align-items: center;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.summary-item {
		flex: 1;
		text-align: center;
	}
	.summary-num {
		font-size: 36rpx;
		font-weight: 700;
		line-height: 1.2;
	}
	.summary-num.total { color: #096DD9; }
	.summary-num.in-progress { color: #1890FF; }
	.summary-num.pending { color: #8C8C8C; }
	.summary-num.completed { color: #52C41A; }
	.summary-num.overdue { color: #FF4D4F; }
	.summary-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 6rpx;
	}
	.summary-divider {
		width: 1rpx;
		height: 48rpx;
		background: #F0F2F5;
	}

	/* 列表 */
	.list {
		padding-top: 4rpx;
	}

	/* 骨架屏 */
	.skeleton-wrap {
		display: flex;
		flex-direction: column;
		gap: 20rpx;
	}
	.skeleton-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.sk-line {
		height: 28rpx;
		border-radius: 8rpx;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
	}
	.sk-title { width: 60%; margin-bottom: 20rpx; }
	.sk-meta { width: 80%; margin-bottom: 16rpx; height: 24rpx; }
	.sk-progress { width: 100%; height: 16rpx; }
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}

	/* 任务卡片 */
	.task-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.task-head {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 14rpx;
	}
	.task-name {
		flex: 1;
		margin-right: 16rpx;
		display: flex;
		align-items: center;
		flex-wrap: wrap;
	}
	.name-text {
		font-size: 30rpx;
		font-weight: 600;
		color: #303133;
	}
	.tag {
		margin-left: 12rpx;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.tag-milestone { background: #FFF7E6; color: #D48806; }
	.tag-key { background: #FFF1F0; color: #D4380D; }
	.task-status {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}

	/* 元信息行 */
	.task-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
		margin-bottom: 12rpx;
	}
	.meta-text {
		font-size: 24rpx;
		color: #5C6370;
		display: flex;
		align-items: center;
	}
	.meta-svg {
		width: 24rpx;
		height: 24rpx;
		margin-right: 6rpx;
	}

	/* 进度条 */
	.task-progress-row {
		display: flex;
		align-items: center;
		margin-top: 8rpx;
	}
	.progress-bar {
		flex: 1;
		height: 12rpx;
		background: #EBEEF5;
		border-radius: 6rpx;
		overflow: hidden;
		margin-right: 16rpx;
	}
	.progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 6rpx;
		transition: width 0.3s;
	}
	.progress-text {
		font-size: 26rpx;
		color: #096DD9;
		font-weight: 600;
		min-width: 70rpx;
		text-align: right;
	}

	/* 截止提醒 */
	.task-deadline {
		display: flex;
		align-items: center;
		margin-top: 16rpx;
		padding: 10rpx 16rpx;
		border-radius: 8rpx;
		font-size: 24rpx;
	}
	.task-deadline.danger {
		background: #FFF1F0;
		color: #D4380D;
	}
	.task-deadline.warning {
		background: #FFFBE6;
		color: #D48806;
	}
	.task-deadline .meta-svg {
		width: 24rpx;
		height: 24rpx;
		margin-right: 8rpx;
	}

	/* 空状态 */
	.empty {
		padding: 160rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	.empty-icon {
		margin-bottom: 24rpx;
		display: flex;
		justify-content: center;
		align-items: center;
	}
	.svg-icon-lg {
		width: 96rpx;
		height: 96rpx;
	}

	/* 加载更多 */
	.load-more {
		text-align: center;
		color: #767E8A;
		font-size: 24rpx;
		padding: 24rpx 0;
	}

	.bottom-safe { height: calc(40rpx + env(safe-area-inset-bottom)); }
</style>
