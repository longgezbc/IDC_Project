<!--
  进度（任务）列表页
  业务用途：展示某项目下的任务列表与项目整体进度，支持按「全部/里程碑/关键节点」筛选、
           按任务名称关键字搜索，并采用前端分页（首屏20条，触底加载更多）。
  数据来源：taskApi.getProjectProgress（整体进度）、taskApi.getList（任务列表，后端一次性返回）。
  导航特性：非 tabBar 页，通过 url 参数 projectId 接收项目ID；
           支持下拉刷新、上拉加载更多；点击任务卡片进入进度填报页。
-->
<template>
	<view class="page">
		<!-- 关键字搜索栏 -->
		<view class="search-bar">
			<view class="search-input-wrap">
			<image class="search-icon" src="/static/icon/搜索.svg" mode="aspectFit" aria-hidden="true" />
			<input
				class="search-input"
				type="text"
				placeholder="搜索任务名称"
				placeholder-class="search-placeholder"
				v-model="keyword"
				confirm-type="search"
				aria-label="搜索任务名称"
				@confirm="onSearchConfirm"
			/>
			<text v-if="keyword" class="search-clear" role="button" aria-label="清空搜索" @click="clearKeyword">×</text>
		</view>
			<!-- 新建任务按钮：PM/admin 可见 -->
			<view v-if="canManage" class="add-btn" role="button" hover-class="uni-hover" :hover-stay-time="80" @click="goCreateTask">+ 新建任务</view>
		</view>

		<view class="filter-bar">
			<view class="filter-item" :class="{ active: filterType === 'all' }" role="button" @click="setFilter('all')">全部</view>
			<view class="filter-item" :class="{ active: filterType === 'milestone' }" role="button" @click="setFilter('milestone')">里程碑</view>
			<view class="filter-item" :class="{ active: filterType === 'key' }" role="button" @click="setFilter('key')">关键节点</view>
			<view v-if="canManage" class="filter-item filter-gantt" role="button" @click="goGantt">📊 甘特图</view>
		</view>

		<view class="progress-summary card">
			<view class="summary-title">项目整体进度</view>
			<view class="summary-progress">
				<view class="progress-bar">
					<view class="progress-inner" :style="{ width: (overall.progress || 0) + '%' }"></view>
				</view>
				<text class="progress-text">{{ overall.progress || 0 }}%</text>
			</view>
			<view class="summary-meta" v-if="overall.totalTasks !== undefined">
				<text>共 {{ overall.totalTasks }} 项任务</text>
				<text class="success">已完成 {{ overall.completedTasks || 0 }}</text>
				<text class="primary">进行中 {{ overall.inProgressTasks || 0 }}</text>
			</view>
		</view>

		<view class="list">
			<view v-if="loading && list.length === 0" class="card">
				<view class="skeleton-line" v-for="i in 3" :key="i"></view>
			</view>
			<template v-else>
				<view
			v-for="t in displayedList"
			:key="t.id"
			class="task-card"
			hover-class="uni-hover"
			:hover-stay-time="80"
			role="link"
			:aria-label="t.name"
			@click="goReport(t)"
		>
					<view class="task-head">
						<view class="task-name">
							<text>{{ t.name }}</text>
							<text v-if="t.isMilestone" class="tag tag-milestone">里程碑</text>
							<text v-else-if="t.isKey" class="tag tag-key">关键</text>
						</view>
						<view class="task-status" :style="statusStyle(t.status)">{{ taskStatusText(t.status) }}</view>
					</view>
					<view class="task-meta">
						<text class="meta-text"><image class="meta-svg" src="/static/icon/日期.svg" mode="aspectFit" aria-hidden="true" /> {{ formatDate(t.startDate) }} ~ {{ formatDate(t.endDate) }}</text>
					<text v-if="t.assignee" class="meta-text"><image class="meta-svg" src="/static/icon/我的.svg" mode="aspectFit" aria-hidden="true" /> {{ t.assignee }}</text>
					</view>
					<view class="task-progress-row">
						<view class="progress-bar">
							<view class="progress-inner" :style="{ width: (t.progress || 0) + '%' }"></view>
						</view>
						<text class="progress-text">{{ t.progress || 0 }}%</text>
						<view class="report-btn">填报</view>
					</view>
					<view v-if="t.remark" class="task-remark">{{ t.remark }}</view>
				</view>

				<view v-if="filteredList.length === 0" class="empty">
					<view class="empty-icon" aria-hidden="true"><image class="svg-icon-lg" src="/static/icon/资料.svg" mode="aspectFit" /></view>
					<text>暂无任务</text>
				</view>

				<!-- 上拉加载更多提示 -->
				<view v-else-if="displayedList.length < filteredList.length" class="load-more">
					<text class="load-more-text">上拉加载更多</text>
				</view>
				<view v-else-if="filteredList.length > pageSize" class="load-more">
					<text class="load-more-text">已全部加载</text>
				</view>
			</template>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { taskApi } from '@/api/task'
	import { formatDate, getTaskStatus } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'
	import { isProjectManager } from '@/utils/permission'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				projectId: '',     // 项目ID（来自路由参数）
				filterType: 'all', // 筛选类型：all/milestone/key
				keyword: '',       // 关键字搜索（任务名称，模糊匹配）
				list: [],          // 任务列表（全量，后端一次性返回）
				overall: {},       // 项目整体进度汇总
				loading: false,    // 加载中标记
				pageSize: 20,      // 首屏每页条数
				pageIncrement: 10, // 触底加载每页增量
				currentPage: 1     // 当前已加载页数（displayedList = filteredList.slice(0, currentPage * pageSize)）
			}
		},
		computed: {
			/**
			 * 按筛选类型 + 关键字过滤后的任务列表
			 * 注意：filterType 与 keyword 任一变化都应重置分页，由 setFilter / onSearchConfirm / clearKeyword 处理
			 */
			filteredList() {
				let arr = this.list
				// 1. 按筛选类型过滤
				if (this.filterType === 'milestone') {
					arr = arr.filter(t => t.isMilestone)
				} else if (this.filterType === 'key') {
					arr = arr.filter(t => t.isKey)
				}
				// 2. 按关键字过滤（任务名称 includes，忽略大小写）
				const kw = (this.keyword || '').trim().toLowerCase()
				if (kw) {
					arr = arr.filter(t => String(t.name || '').toLowerCase().includes(kw))
				}
				return arr
			},
			/**
		 * 当前页实际展示的任务列表（前端分页）
		 * 取 filteredList 前 currentPage * pageSize 条
		 */
		displayedList() {
			const end = this.currentPage * this.pageSize
			return this.filteredList.slice(0, end)
		},
		/** 是否可管理（PM/admin）—— 控制新建任务/甘特图按钮可见性 */
		canManage() {
			return isProjectManager()
		}
	},
		// 页面加载：接收路由参数 projectId
		onLoad(opts) {
			this.projectId = opts.projectId
		},
		// 页面显示：加载整体进度与任务列表（从填报页返回时可刷新）
		onShow() {
			if (this.projectId) this.loadAll()
		},
		// 下拉刷新：重新加载并重置分页
		onPullDownRefresh() {
			this.loadAll(true)
		},
		// 上拉触底：加载下一页（仅前端切片，无需请求后端）
		onReachBottom() {
			if (this.displayedList.length < this.filteredList.length) {
				this.currentPage += 1
			}
		},
		methods: {
			formatDate,
			// taskStatusText 由 statusMixin 提供
			/** 任务状态标签内联样式（文字色 + 半透明同色背景） */
			statusStyle(s) {
				const o = getTaskStatus(s)
				return { color: o.color, background: o.color + '20' }
			},
			/**
			 * 切换筛选类型并重置分页
			 * @param {string} type all/milestone/key
			 */
			setFilter(type) {
				if (this.filterType === type) return
				this.filterType = type
				this.currentPage = 1
			},
			/** 搜索框确认：重置分页（输入实时过滤已生效，确认仅作分页重置语义） */
			onSearchConfirm() {
				this.currentPage = 1
			},
			/** 清空关键字：重置分页 */
			clearKeyword() {
				this.keyword = ''
				this.currentPage = 1
			},
			/**
			 * 并行加载整体进度与任务列表
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async loadAll(stopPull) {
				this.loading = true
				this.currentPage = 1 // 重置分页
				await Promise.allSettled([this.loadOverall(), this.loadList()])
				this.loading = false
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载项目整体进度并统计各状态任务数 */
			async loadOverall() {
				try {
					const data = await taskApi.getProjectProgress(this.projectId) || {}
					// 后端 ProjectProgressVO 字段映射：
					// overallProgress -> 进度百分比；tasks 列表用于统计任务数
					const tasks = Array.isArray(data.tasks) ? data.tasks : []
					this.overall = {
						progress: data.overallProgress ?? data.progress ?? 0,
						totalTasks: tasks.length,
						completedTasks: tasks.filter(t => t.status === '已完成').length,
						inProgressTasks: tasks.filter(t => t.status === '进行中').length
					}
				} catch (e) {
					console.warn('overall error', e)
				}
			},
			/** 加载任务列表（兼容多种返回结构） */
			async loadList() {
				try {
					const res = await taskApi.getList(this.projectId)
					const list = (res && (res.records || res.list || res)) || []
					this.list = Array.isArray(list) ? list : []
				} catch (e) {
					console.warn('list error', e)
					this.list = []
				}
			},
			/** 跳转进度填报页 */
		goReport(task) {
			uni.navigateTo({
				url: `/pages/progress/report?taskId=${task.id}&projectId=${this.projectId}`
			})
		},
		/** 跳转新建任务页 */
		goCreateTask() {
			uni.navigateTo({
				url: `/pages/progress/task-edit?projectId=${this.projectId}`
			})
		},
		/** 跳转甘特图页 */
		goGantt() {
			uni.navigateTo({
				url: `/pages/progress/gantt?projectId=${this.projectId}`
			})
		}
	}
	}
</script>

<style lang="scss" scoped>
	.page { min-height: 100vh; }

	/* 搜索栏：吸顶效果，与下方筛选栏视觉分离 */
	.search-bar {
		display: flex;
		align-items: center;
		gap: 16rpx;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
		border-bottom: 1rpx solid #EBEEF5;
	}
	.search-input-wrap {
		flex: 1;
		display: flex;
		align-items: center;
		background: #F5F7FA;
		border-radius: 32rpx;
		padding: 0 24rpx;
		height: 64rpx;
	}
	.add-btn {
		flex-shrink: 0;
		height: 64rpx;
		line-height: 64rpx;
		padding: 0 24rpx;
		font-size: 26rpx;
		font-weight: 500;
		color: #FFFFFF;
		background: linear-gradient(135deg, #1890FF, #096DD9);
		border-radius: 32rpx;
		box-shadow: 0 4rpx 12rpx rgba(24, 144, 255, 0.3);
	}
	.search-icon {
		width: 28rpx;
		height: 28rpx;
		margin-right: 12rpx;
		flex-shrink: 0;
		opacity: 0.5;
	}
	.search-input {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
		height: 64rpx;
		line-height: 64rpx;
	}
	.search-placeholder {
		color: #767E8A;
		font-size: 26rpx;
	}
	.search-clear {
		font-size: 36rpx;
		color: #767E8A;
		padding: 0 8rpx;
		line-height: 1;
	}

	.filter-bar {
		display: flex;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
		gap: 16rpx;
		border-bottom: 1rpx solid #EBEEF5;
	}
	.filter-item {
		flex: 1;
		text-align: center;
		padding: 14rpx 0;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 10rpx;
	}
	.filter-item.active {
		background: #1890FF;
		color: #FFFFFF;
	}
	/* 甘特图按钮：非 flex:1，固定宽度，强调操作属性 */
	.filter-gantt {
		flex: 0 0 auto;
		min-width: 160rpx;
		padding: 14rpx 20rpx;
		color: #096DD9;
		background: #E6F7FF;
		border: 1rpx solid #91D5FF;
	}

	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin: 20rpx 24rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.summary-title {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 16rpx;
	}
	.summary-progress {
		display: flex;
		align-items: center;
		margin-bottom: 12rpx;
	}
	.progress-bar {
		flex: 1;
		height: 16rpx;
		background: #EBEEF5;
		border-radius: 8rpx;
		overflow: hidden;
		margin-right: 16rpx;
	}
	.progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 8rpx;
		transition: width 0.3s;
	}
	.progress-text {
		font-size: 26rpx;
		color: #096DD9;
		font-weight: 600;
		min-width: 70rpx;
		text-align: right;
	}
	.summary-meta {
		display: flex;
		gap: 24rpx;
		font-size: 22rpx;
		color: #5C6370;
	}
	.summary-meta .success { color: #389E0D; }
	.summary-meta .primary { color: #096DD9; }

	.list { padding: 0 24rpx; }
	.skeleton-line {
		height: 180rpx;
		margin-bottom: 20rpx;
		border-radius: 16rpx;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
	}
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}

	.task-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.task-head {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 12rpx;
	}
	.task-name {
		font-size: 30rpx;
		font-weight: 600;
		color: #303133;
		flex: 1;
		margin-right: 16rpx;
		display: flex;
		align-items: center;
		flex-wrap: wrap;
	}
	.tag {
		margin-left: 12rpx;
		padding: 2rpx 12rpx;
		border-radius: 6rpx;
		font-size: 20rpx;
	}
	.tag-milestone { background: #FFF7E6; color: #D48806; }
	.tag-key { background: #FFF1F0; color: #D4380D; }
	.task-status {
		padding: 4rpx 14rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.task-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
		margin-bottom: 16rpx;
	}
	.meta-text {
		font-size: 22rpx;
		color: #5C6370;
		display: flex;
		align-items: center;
	}
	.meta-svg {
		width: 22rpx;
		height: 22rpx;
		margin-right: 6rpx;
	}
	.task-progress-row {
		display: flex;
		align-items: center;
	}
	.task-progress-row .progress-bar {
		flex: 1;
		height: 10rpx;
		margin-right: 12rpx;
	}
	.task-progress-row .progress-text {
		font-size: 24rpx;
		min-width: 60rpx;
		text-align: right;
	}
	.report-btn {
		margin-left: 20rpx;
		font-size: 22rpx;
		color: #FFFFFF;
		background: #1890FF;
		padding: 8rpx 24rpx;
		border-radius: 20rpx;
	}
	.task-remark {
		margin-top: 12rpx;
		padding: 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 24rpx;
		color: #606266;
		line-height: 1.6;
	}

	.empty {
		padding: 160rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	.empty-icon {
		font-size: 96rpx;
		margin-bottom: 24rpx;
		display: flex;
		justify-content: center;
		align-items: center;
	}
	.svg-icon-lg {
		width: 96rpx;
		height: 96rpx;
	}
	.bottom-safe { height: calc(40rpx + env(safe-area-inset-bottom)); }

	/* 上拉加载更多提示 */
	.load-more {
		padding: 32rpx 0;
		text-align: center;
	}
	.load-more-text {
		font-size: 24rpx;
		color: #5C6370;
	}
</style>
