<!--
  项目甘特图页（简化版纯CSS）
  业务用途：以横向时间轴形式展示项目任务进度，支持「日/周/月」三种视图切换。
           里程碑以菱形标记，关键节点高亮，今日以纵向虚线标识。
           点击任务条可跳转进度填报页。
  数据来源：taskApi.getList（任务列表，按 startDate/endDate 计算时间区间）。
  导航特性：非 tabBar 页，通过 url 参数 projectId 接收项目ID；
           横向可滚动以查看完整时间区间；支持下拉刷新。
  设计说明：小程序不引入 ECharts/uCharts 等图表库，
           采用纯 CSS Grid + 绝对定位实现简化版甘特图，保持轻量。
-->
<template>
	<view class="page">
		<!-- 项目概览 -->
		<view class="overview-card card">
			<view class="overview-title">{{ projectName || '项目甘特图' }}</view>
			<view class="overview-progress">
				<view class="progress-bar">
					<view class="progress-inner" :style="{ width: (overallProgress || 0) + '%' }"></view>
				</view>
				<text class="progress-num">{{ overallProgress || 0 }}%</text>
			</view>
			<view class="overview-meta">
				<text>共 {{ list.length }} 项任务</text>
				<text class="primary">进行中 {{ inProgressCount }}</text>
				<text class="success">已完成 {{ completedCount }}</text>
				<text class="danger">逾期 {{ overdueCount }}</text>
			</view>
		</view>

		<!-- 视图切换 -->
		<view class="view-switcher">
			<view
				v-for="opt in viewOptions"
				:key="opt.value"
				class="view-item"
				:class="{ active: currentView === opt.value }"
				role="button"
				@click="switchView(opt.value)"
			>{{ opt.label }}</view>
		</view>

		<!-- 甘特图主体 -->
		<view v-if="list.length > 0 && timeline.days.length > 0" class="gantt-card card">
			<!-- 横向滚动容器 -->
			<scroll-view scroll-x class="gantt-scroll" :scroll-left="scrollLeft" :show-scrollbar="false">
				<view class="gantt-inner" :style="{ width: ganttWidth + 'px' }">
					<!-- 时间轴表头 -->
					<view class="gantt-header">
						<view class="gantt-header-cell gantt-task-col-header">任务名称</view>
						<view class="gantt-header-cells">
							<view
								v-for="(d, idx) in timeline.days"
								:key="idx"
								class="gantt-header-cell"
								:class="{
									weekend: isWeekend(d.date),
									today: isToday(d.date),
									'first-of-month': d.dayOfMonth === 1
								}"
							>
								<view v-if="currentView === 'month'" class="hdr-month">{{ d.monthLabel }}</view>
								<view v-else class="hdr-date">
									<text class="hdr-day">{{ d.dayOfMonth }}</text>
									<text v-if="d.dayOfMonth === 1 || currentView === 'week'" class="hdr-month-text">{{ d.monthLabel }}</text>
								</view>
							</view>
						</view>
					</view>

					<!-- 任务行 -->
					<view
						v-for="(task, rowIdx) in visibleTasks"
						:key="task.id"
						class="gantt-row"
						:class="{ 'row-even': rowIdx % 2 === 0, 'row-odd': rowIdx % 2 === 1 }"
					>
						<!-- 任务名称列（A11y：可点击区域补 role 与动态 aria-label） -->
					<view
						class="gantt-task-col"
						role="button"
						:aria-label="task.name + (task.isMilestone ? '，里程碑' : (task.isKey ? '，关键节点' : '')) + '，进度' + (task.progress || 0) + '%'"
						@click="goTaskReport(task)"
					>
						<text class="task-name" :class="{ 'is-milestone': task.isMilestone }">{{ task.name }}</text>
						<!-- 装饰性符号 ◆● 仅视觉，对屏幕阅读器隐藏，状态由 aria-label 提供 -->
						<text v-if="task.isMilestone" class="milestone-mark" aria-hidden="true">◆</text>
						<text v-else-if="task.isKey" class="key-mark" aria-hidden="true">●</text>
						<text class="task-progress-text">{{ task.progress || 0 }}%</text>
					</view>

					<!-- 时间格子列 -->
					<view class="gantt-time-cells" :style="{ position: 'relative' }">
						<!-- 网格背景 -->
						<view
							v-for="(d, idx) in timeline.days"
							:key="idx"
							class="gantt-cell"
							:class="{
								weekend: isWeekend(d.date),
								today: isToday(d.date),
								'first-of-month': d.dayOfMonth === 1
							}"
						></view>

						<!-- 任务条（A11y：补 role 与 aria-label） -->
						<view
							v-if="!task.isMilestone"
							class="gantt-bar"
							:class="barClass(task)"
							:style="barStyle(task)"
							role="button"
							:aria-label="task.name + '，进度' + (task.progress || 0) + '%'"
							@click="goTaskReport(task)"
						>
							<view class="bar-fill" :style="{ width: (task.progress || 0) + '%' }"></view>
							<text class="bar-label">{{ task.progress || 0 }}%</text>
						</view>

						<!-- 里程碑标记（A11y：补 role 与 aria-label，◆ 符号 aria-hidden） -->
						<view
							v-else
							class="gantt-milestone"
							:style="milestoneStyle(task)"
							role="button"
							:aria-label="task.name + '，里程碑'"
							@click="goTaskReport(task)"
						><text aria-hidden="true">◆</text></view>

							<!-- 今日竖线 -->
							<view
								v-if="todayOffset !== null"
								class="today-line"
								:style="{ left: todayOffset + 'px' }"
							></view>
						</view>
					</view>
				</view>
			</scroll-view>
		</view>

		<!-- 图例 -->
		<view class="legend card">
			<view class="legend-item">
				<view class="legend-bar color-todo"></view>
				<text>未开始</text>
			</view>
			<view class="legend-item">
				<view class="legend-bar color-doing"></view>
				<text>进行中</text>
			</view>
			<view class="legend-item">
				<view class="legend-bar color-done"></view>
				<text>已完成</text>
			</view>
			<view class="legend-item">
				<view class="legend-bar color-overdue"></view>
				<text>已逾期</text>
			</view>
			<view class="legend-item">
				<view class="legend-mark">◆</view>
				<text>里程碑</text>
			</view>
		</view>

		<!-- 空态 -->
		<view v-if="loading === false && list.length === 0" class="empty">
			<view class="empty-icon" aria-hidden="true">
				<image class="svg-icon-lg" src="/static/icon/资料.svg" mode="aspectFit" />
			</view>
			<text>暂无任务，无法生成甘特图</text>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { taskApi } from '@/api/task'
	import { formatDate } from '@/utils/format'

	// 视图配置
	const VIEW_OPTIONS = [
		{ label: '日视图', value: 'day' },
		{ label: '周视图', value: 'week' },
		{ label: '月视图', value: 'month' }
	]

	// 列宽常量（单位 rpx → px 转换在运行时进行）
	const COL_WIDTH_DAY = 60      // 日视图：每天 60rpx
	const COL_WIDTH_WEEK = 120    // 周视图：每天 120rpx
	const COL_WIDTH_MONTH = 200   // 月视图：每天 200rpx
	const TASK_COL_WIDTH = 220    // 任务名列宽（rpx）

	export default {
		data() {
			return {
				projectId: null,         // 项目ID
				projectName: '',         // 项目名称
				list: [],                // 任务列表
				overallProgress: 0,      // 项目整体进度
				loading: false,
				currentView: 'day',      // 当前视图 day/week/month
				viewOptions: VIEW_OPTIONS,
				timeline: { days: [], start: null, end: null }, // 时间轴数据
				scrollLeft: 0,           // 横向滚动位置（自动定位到今日）
				screenWidth: 375,        // 屏幕宽度（px）
				rpxRatio: 1              // rpx → px 转换比率
			}
		},
		computed: {
			/** 进行中任务数 */
			inProgressCount() {
				return this.list.filter(t => t.status === '进行中').length
			},
			/** 已完成任务数 */
			completedCount() {
				return this.list.filter(t => t.status === '已完成').length
			},
			/** 逾期任务数 */
			overdueCount() {
				return this.list.filter(t => t.status === '已延期').length
			},
			/** 显示在甘特图中的任务（限制最多 30 条，避免渲染卡顿） */
			visibleTasks() {
				return this.list.slice(0, 30)
			},
			/** 时间格子列宽（px） */
			colWidthPx() {
				const rpx = this.currentView === 'day'
					? COL_WIDTH_DAY
					: (this.currentView === 'week' ? COL_WIDTH_WEEK : COL_WIDTH_MONTH)
				return rpx * this.rpxRatio
			},
			/** 任务名列宽（px） */
			taskColWidthPx() {
				return TASK_COL_WIDTH * this.rpxRatio
			},
			/** 甘特图总宽度（px） */
			ganttWidth() {
				return this.taskColWidthPx + this.timeline.days.length * this.colWidthPx + 20
			},
			/** 今日竖线偏移量（相对时间格子容器起点） */
			todayOffset() {
				if (!this.timeline.days.length) return null
				const today = new Date()
				today.setHours(0, 0, 0, 0)
				const start = new Date(this.timeline.start)
				start.setHours(0, 0, 0, 0)
				const diffDays = Math.floor((today - start) / 86400000)
				if (diffDays < 0 || diffDays >= this.timeline.days.length) return null
				return diffDays * this.colWidthPx + this.colWidthPx / 2
			}
		},
		onLoad(options) {
			this.projectId = options && options.projectId ? Number(options.projectId) : null
			if (options && options.projectName) {
				this.projectName = decodeURIComponent(options.projectName)
			}
			// 获取屏幕宽度用于 rpx 转换
			try {
				const sysInfo = uni.getSystemInfoSync()
				this.screenWidth = sysInfo.windowWidth || 375
				// rpx → px 转换比率：750rpx = 屏幕宽度（px）
				this.rpxRatio = this.screenWidth / 750
			} catch (e) {
				this.rpxRatio = 0.5
			}
		},
		onShow() {
			if (this.projectId) this.loadData()
		},
		onPullDownRefresh() {
			if (this.projectId) {
				this.loadData(true)
			} else {
				uni.stopPullDownRefresh()
			}
		},
		methods: {
			/** 加载任务列表与项目进度 */
			async loadData(stopPull) {
				if (!this.projectId) {
					uni.stopPullDownRefresh && uni.stopPullDownRefresh()
					return
				}
				this.loading = true
				try {
					// 并发拉取任务列表 + 项目整体进度
					const [taskList, progressInfo] = await Promise.allSettled([
						taskApi.getList(this.projectId, {}),
						taskApi.getProjectProgress(this.projectId)
					])
					if (taskList.status === 'fulfilled') {
						const raw = taskList.value
						this.list = Array.isArray(raw) ? raw : (raw && (raw.records || raw.list)) || []
					} else {
						this.list = []
					}
					if (progressInfo.status === 'fulfilled') {
						this.overallProgress = progressInfo.value && progressInfo.value.progress || 0
						if (!this.projectName && progressInfo.value && progressInfo.value.projectName) {
							this.projectName = progressInfo.value.projectName
						}
					}
					// 计算时间轴
					this.buildTimeline()
					// 自动定位到今日
					this.$nextTick(() => this.scrollToToday())
				} catch (e) {
					console.warn('load gantt error', e)
					uni.showToast({ title: '加载失败', icon: 'none' })
				} finally {
					this.loading = false
					if (stopPull) uni.stopPullDownRefresh()
				}
			},
			/** 切换视图 */
			switchView(view) {
				if (this.currentView === view) return
				this.currentView = view
				this.$nextTick(() => this.scrollToToday())
			},
			/**
			 * 构建时间轴：以最早任务开始日期为起点，最晚结束日期为终点，
			 * 至少包含今日前后各 7 天，避免任务超出可视范围
			 */
			buildTimeline() {
				if (!this.list.length) {
					this.timeline = { days: [], start: null, end: null }
					return
				}
				const today = new Date()
				today.setHours(0, 0, 0, 0)
				let min = today
				let max = today
				this.list.forEach(t => {
					if (t.startDate) {
						const s = new Date(t.startDate)
						if (!isNaN(s.getTime())) {
							s.setHours(0, 0, 0, 0)
							if (!min || s < min) min = s
						}
					}
					if (t.endDate) {
						const e = new Date(t.endDate)
						if (!isNaN(e.getTime())) {
							e.setHours(0, 0, 0, 0)
							if (!max || e > max) max = e
						}
					}
				})
				// 前后各延展 3 天，便于查看
				min = new Date(min.getTime() - 3 * 86400000)
				max = new Date(max.getTime() + 3 * 86400000)
				const days = []
				let cursor = new Date(min)
				while (cursor <= max) {
					days.push({
						date: new Date(cursor),
						dayOfMonth: cursor.getDate(),
						monthLabel: `${cursor.getMonth() + 1}月`
					})
					cursor = new Date(cursor.getTime() + 86400000)
				}
				this.timeline = { days, start: min, end: max }
			},
			/** 自动滚动到今日 */
			scrollToToday() {
				if (this.todayOffset === null) return
				// 留出 1 个屏幕宽度的前置内容
				const targetLeft = Math.max(0, this.todayOffset - this.screenWidth / 2)
				this.scrollLeft = targetLeft
			},
			/** 是否为今天 */
			isToday(date) {
				const today = new Date()
				return date.getFullYear() === today.getFullYear()
					&& date.getMonth() === today.getMonth()
					&& date.getDate() === today.getDate()
			},
			/** 是否为周末 */
			isWeekend(date) {
				const day = date.getDay()
				return day === 0 || day === 6
			},
			/** 任务条样式（位置 + 宽度） */
			barStyle(task) {
				if (!task.startDate || !task.endDate) return { display: 'none' }
				const start = new Date(task.startDate)
				const end = new Date(task.endDate)
				if (isNaN(start.getTime()) || isNaN(end.getTime())) return { display: 'none' }
				start.setHours(0, 0, 0, 0)
				end.setHours(0, 0, 0, 0)
				const tlStart = new Date(this.timeline.start)
				tlStart.setHours(0, 0, 0, 0)
				const offsetDays = Math.max(0, Math.floor((start - tlStart) / 86400000))
				const duration = Math.max(1, Math.floor((end - start) / 86400000) + 1)
				const left = offsetDays * this.colWidthPx + 2
				const width = duration * this.colWidthPx - 4
				return { left: left + 'px', width: width + 'px' }
			},
			/** 里程碑标记位置（基于 startDate） */
			milestoneStyle(task) {
				if (!task.startDate && !task.endDate) return { display: 'none' }
				const ref = new Date(task.startDate || task.endDate)
				if (isNaN(ref.getTime())) return { display: 'none' }
				ref.setHours(0, 0, 0, 0)
				const tlStart = new Date(this.timeline.start)
				tlStart.setHours(0, 0, 0, 0)
				const offsetDays = Math.max(0, Math.floor((ref - tlStart) / 86400000))
				const left = offsetDays * this.colWidthPx + this.colWidthPx / 2 - 14
				return { left: left + 'px' }
			},
			/** 任务条样式类 */
			barClass(task) {
				if (task.status === '已完成') return 'bar-done'
				if (task.status === '已延期') return 'bar-overdue'
				if (task.status === '已暂停') return 'bar-paused'
				if (task.status === '进行中') return 'bar-doing'
				return 'bar-todo'
			},
			/** 跳转进度填报页 */
			goTaskReport(task) {
				uni.navigateTo({
					url: `/pages/progress/report?taskId=${task.id}&projectId=${this.projectId}`
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

	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	/* 概览卡片 */
	.overview-card {
		padding: 32rpx 28rpx;
	}
	.overview-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 16rpx;
	}
	.overview-progress {
		display: flex;
		align-items: center;
		gap: 16rpx;
		margin-bottom: 12rpx;
	}
	.overview-progress .progress-bar {
		flex: 1;
		height: 16rpx;
		background: #EBEEF5;
		border-radius: 8rpx;
		overflow: hidden;
	}
	.overview-progress .progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 8rpx;
	}
	.overview-progress .progress-num {
		font-size: 32rpx;
		font-weight: 700;
		color: #096DD9;
		min-width: 80rpx;
		text-align: right;
	}
	.overview-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx 24rpx;
		font-size: 24rpx;
		color: #5C6370;
	}
	.overview-meta .primary { color: #1890FF; }
	.overview-meta .success { color: #52C41A; }
	.overview-meta .danger { color: #FF4D4F; }

	/* 视图切换 */
	.view-switcher {
		display: flex;
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 8rpx;
		margin-bottom: 20rpx;
	}
	.view-item {
		flex: 1;
		text-align: center;
		padding: 16rpx 0;
		font-size: 26rpx;
		color: #5C6370;
		border-radius: 12rpx;
		transition: all 0.2s;
		&.active {
			background: #1890FF;
			color: #FFFFFF;
			font-weight: 600;
		}
	}

	/* 甘特图主体 */
	.gantt-card {
		padding: 0;
		overflow: hidden;
	}
	.gantt-scroll {
		width: 100%;
		white-space: nowrap;
	}
	.gantt-inner {
		position: relative;
		min-width: 100%;
	}

	/* 表头 */
	.gantt-header {
		display: flex;
		height: 80rpx;
		background: #FAFAFA;
		border-bottom: 1rpx solid #EBEEF5;
		position: sticky;
		top: 0;
		z-index: 2;
	}
	.gantt-task-col-header {
		width: 220rpx;
		min-width: 220rpx;
		display: flex;
		align-items: center;
		padding: 0 20rpx;
		font-size: 24rpx;
		color: #5C6370;
		font-weight: 600;
		border-right: 1rpx solid #EBEEF5;
		flex-shrink: 0;
	}
	.gantt-header-cells {
		display: flex;
		flex-shrink: 0;
	}
	.gantt-header-cell {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		font-size: 20rpx;
		color: #5C6370;
		border-right: 1rpx solid #F5F7FA;
		flex-shrink: 0;
		box-sizing: border-box;
		&.weekend {
			background: #FAFAFA;
		}
		&.today {
			background: #E6F7FF;
			color: #096DD9;
			font-weight: 700;
		}
		&.first-of-month {
			border-left: 1rpx solid #D9D9D9;
		}
	}
	.hdr-day {
		font-size: 22rpx;
		font-weight: 500;
	}
	.hdr-month {
		font-size: 20rpx;
		font-weight: 600;
	}
	.hdr-month-text {
		font-size: 18rpx;
		color: #909399;
		margin-top: 2rpx;
	}

	/* 任务行 */
	.gantt-row {
		display: flex;
		height: 72rpx;
		border-bottom: 1rpx solid #F5F7FA;
		&.row-even {
			background: #FFFFFF;
		}
		&.row-odd {
			background: #FBFCFD;
		}
	}

	/* 任务名称列 */
	.gantt-task-col {
		width: 220rpx;
		min-width: 220rpx;
		padding: 0 16rpx;
		display: flex;
		align-items: center;
		gap: 6rpx;
		border-right: 1rpx solid #EBEEF5;
		flex-shrink: 0;
		overflow: hidden;
	}
	.task-name {
		flex: 1;
		font-size: 22rpx;
		color: #303133;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		&.is-milestone {
			font-weight: 600;
			color: #722ED1;
		}
	}
	.milestone-mark {
		color: #722ED1;
		font-size: 18rpx;
	}
	.key-mark {
		color: #FF4D4F;
		font-size: 18rpx;
	}
	.task-progress-text {
		font-size: 18rpx;
		color: #909399;
		flex-shrink: 0;
	}

	/* 时间格子列 */
	.gantt-time-cells {
		position: relative;
		display: flex;
		flex-shrink: 0;
	}
	.gantt-cell {
		border-right: 1rpx solid #F5F7FA;
		flex-shrink: 0;
		box-sizing: border-box;
		&.weekend {
			background: #FAFAFA;
		}
		&.today {
			background: #E6F7FF;
		}
		&.first-of-month {
			border-left: 1rpx solid #D9D9D9;
		}
	}

	/* 任务条 */
	.gantt-bar {
		position: absolute;
		top: 12rpx;
		height: 48rpx;
		border-radius: 8rpx;
		display: flex;
		align-items: center;
		justify-content: flex-start;
		padding: 0 12rpx;
		font-size: 18rpx;
		color: #FFFFFF;
		overflow: hidden;
		z-index: 1;
		box-sizing: border-box;
	}
	.gantt-bar .bar-fill {
		position: absolute;
		left: 0;
		top: 0;
		height: 100%;
		background: rgba(255, 255, 255, 0.3);
		z-index: 0;
	}
	.gantt-bar .bar-label {
		position: relative;
		z-index: 1;
	}
	.bar-todo {
		background: #909399;
	}
	.bar-doing {
		background: #1890FF;
	}
	.bar-done {
		background: #52C41A;
	}
	.bar-overdue {
		background: #FF4D4F;
	}
	.bar-paused {
		background: #FAAD14;
	}

	/* 里程碑标记 */
	.gantt-milestone {
		position: absolute;
		top: 18rpx;
		width: 28rpx;
		height: 28rpx;
		font-size: 28rpx;
		line-height: 28rpx;
		color: #722ED1;
		text-align: center;
		z-index: 2;
	}

	/* 今日竖线 */
	.today-line {
		position: absolute;
		top: 0;
		bottom: 0;
		width: 2rpx;
		background: #FF4D4F;
		z-index: 3;
		opacity: 0.6;
		&::before {
			content: '今';
			position: absolute;
			top: -2rpx;
			left: -10rpx;
			font-size: 18rpx;
			color: #FF4D4F;
			font-weight: 700;
		}
	}

	/* 图例 */
	.legend {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx 32rpx;
		padding: 24rpx 28rpx;
	}
	.legend-item {
		display: flex;
		align-items: center;
		gap: 10rpx;
		font-size: 22rpx;
		color: #5C6370;
	}
	.legend-bar {
		width: 32rpx;
		height: 16rpx;
		border-radius: 4rpx;
	}
	.color-todo { background: #909399; }
	.color-doing { background: #1890FF; }
	.color-done { background: #52C41A; }
	.color-overdue { background: #FF4D4F; }
	.legend-mark {
		color: #722ED1;
		font-size: 24rpx;
	}

	/* 空态 */
	.empty {
		text-align: center;
		padding: 120rpx 0;
		color: #909399;
	}
	.empty-icon {
		width: 120rpx;
		height: 120rpx;
		margin: 0 auto 24rpx;
	}
	.svg-icon-lg {
		width: 120rpx;
		height: 120rpx;
	}

	.bottom-safe {
		height: env(safe-area-inset-bottom);
		padding-bottom: 20rpx;
	}
</style>
