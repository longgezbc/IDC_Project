<!--
  日报预览页
  业务用途：预览指定日期的日报数据，包含概览指标、项目进度总览、任务进度明细、延期任务预警。
           小程序不支持 Excel 导出，本页仅用于查看与分享截图。
           如需导出，可跳转 Web 端。
  数据来源：dailyReportApi.preview（预览数据）；projectApi.getList（项目筛选下拉）。
  导航特性：非 tabBar 页；顶部为日期+项目筛选；不支持下拉刷新（数据由筛选触发）。
-->
<template>
	<view class="page">
		<!-- 顶部筛选条 -->
		<view class="filter-card card">
			<view class="filter-row">
				<view class="filter-label">开始日期</view>
				<picker mode="date" :value="filterStartDate" @change="onStartDateChange">
					<view class="picker-value">
						<text>{{ filterStartDate || '请选择开始日期' }}</text>
						<text class="picker-arrow">▾</text>
					</view>
				</picker>
			</view>
			<view class="filter-row">
				<view class="filter-label">结束日期</view>
				<picker mode="date" :value="filterEndDate" @change="onEndDateChange">
					<view class="picker-value">
						<text>{{ filterEndDate || '请选择结束日期' }}</text>
						<text class="picker-arrow">▾</text>
					</view>
				</picker>
			</view>
			<view class="filter-row">
				<view class="filter-label">项目</view>
				<picker
					mode="selector"
					:range="projectOptions"
					range-key="name"
					:value="projectSelectorIndex"
					@change="onProjectChange"
				>
					<view class="picker-value">
						<text>{{ currentProjectName || '全部项目' }}</text>
						<text class="picker-arrow">▾</text>
					</view>
				</picker>
			</view>
			<view class="quick-range">
				<view class="quick-btn" role="button" :class="{ active: activeRange === 'today' }" @click="setQuickRange('today')">今日</view>
				<view class="quick-btn" role="button" :class="{ active: activeRange === 'week' }" @click="setQuickRange('week')">本周</view>
				<view class="quick-btn" role="button" :class="{ active: activeRange === 'month' }" @click="setQuickRange('month')">本月</view>
			</view>
			<button class="refresh-btn" :loading="loading" :disabled="loading" @click="loadPreview">刷新数据</button>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && !report">
			<view class="card skeleton-card" v-for="i in 3" :key="'sk' + i">
				<view class="sk-line sk-w240"></view>
				<view class="sk-line sk-w160"></view>
				<view class="sk-line sk-w-full"></view>
			</view>
		</view>

		<!-- 日报数据 -->
		<template v-else-if="report">
			<!-- 概览指标 -->
			<view class="section-title">概览指标</view>
			<view class="overview-grid card">
				<view class="overview-item">
					<view class="ov-num">{{ overview.totalProjects || 0 }}</view>
					<view class="ov-label">项目总数</view>
				</view>
				<view class="overview-item">
					<view class="ov-num primary">{{ overview.inProgressProjects || 0 }}</view>
					<view class="ov-label">进行中</view>
				</view>
				<view class="overview-item">
					<view class="ov-num success">{{ overview.completedTasks || 0 }}</view>
					<view class="ov-label">今日完成任务</view>
				</view>
				<view class="overview-item">
					<view class="ov-num warning">{{ overview.pendingTasks || 0 }}</view>
					<view class="ov-label">待办任务</view>
				</view>
				<view class="overview-item">
					<view class="ov-num danger">{{ overview.overdueTasks || 0 }}</view>
					<view class="ov-label">逾期任务</view>
				</view>
				<view class="overview-item">
					<view class="ov-num">{{ overview.totalProgress || 0 }}%</view>
					<view class="ov-label">平均进度</view>
				</view>
			</view>

			<!-- 项目进度总览 -->
			<view class="section-head">
				<text class="section-title">项目进度总览</text>
				<text class="section-count">{{ projectProgress.length }} 个项目</text>
			</view>
			<view v-if="projectProgress.length" class="card">
				<view
					v-for="(p, idx) in projectProgress"
					:key="p.projectId || idx"
					class="progress-row"
					:class="{ 'no-border': idx === projectProgress.length - 1 }"
				>
					<view class="proj-info">
						<view class="proj-name">{{ p.projectName || p.name || '-' }}</view>
						<view class="proj-meta">
							<text>{{ p.manager || '未指定' }}</text>
							<text class="status-tag" :style="statusStyle(p.status)">{{ p.status || '进行中' }}</text>
						</view>
					</view>
					<view class="proj-progress">
						<view class="progress-bar">
							<view class="progress-inner" :style="{ width: (p.progress || 0) + '%' }"></view>
						</view>
						<text class="progress-num">{{ p.progress || 0 }}%</text>
					</view>
				</view>
			</view>
			<view v-else class="empty-sm">暂无项目数据</view>

			<!-- 任务进度明细 -->
			<view class="section-head">
				<text class="section-title">任务进度明细</text>
				<text class="section-count">{{ taskProgress.length }} 个任务</text>
			</view>
			<view v-if="taskProgress.length" class="card">
				<view
					v-for="(t, idx) in taskProgress"
					:key="t.id || idx"
					class="task-row"
					:class="{ 'no-border': idx === taskProgress.length - 1 }"
				>
					<view class="task-info">
						<view class="task-name">{{ t.taskName || t.name || '-' }}</view>
						<view class="task-meta">
							<text>{{ t.projectName || '项目' }}</text>
							<text>· {{ t.assignee || t.assigneeName || '未指派' }}</text>
						</view>
						<!-- 计划时间：开始 - 结束 -->
						<view class="task-time">
							<text class="time-label">计划：</text>
							<text class="time-val">{{ t.startDate || '-' }}</text>
							<text class="time-sep">至</text>
							<text class="time-val">{{ t.endDate || '-' }}</text>
						</view>
						<!-- 实际时间（如有） -->
						<view v-if="t.actualStartDate || t.actualEndDate" class="task-time actual">
							<text class="time-label">实际：</text>
							<text class="time-val">{{ t.actualStartDate || '-' }}</text>
							<text class="time-sep">至</text>
							<text class="time-val">{{ t.actualEndDate || '-' }}</text>
						</view>
					</view>
					<view class="task-progress">
						<text class="task-progress-num">{{ t.progress || 0 }}%</text>
						<text v-if="t.status" class="status-tag" :style="statusStyle(t.status)">{{ t.status }}</text>
					</view>
				</view>
			</view>
			<view v-else class="empty-sm">暂无任务数据</view>

			<!-- 延期任务预警 -->
			<view class="section-head">
				<text class="section-title">延期任务预警</text>
				<text class="section-count danger">{{ delayedTasks.length }} 个</text>
			</view>
			<view v-if="delayedTasks.length" class="card">
				<view
					v-for="(t, idx) in delayedTasks"
					:key="t.id || idx"
					class="task-row overdue-row"
					:class="{ 'no-border': idx === delayedTasks.length - 1 }"
				>
					<view class="task-info">
						<view class="task-name">{{ t.taskName || t.name || '-' }}</view>
						<view class="task-meta">
							<text class="danger-text">逾期 {{ t.overdueDays || '?' }} 天</text>
							<text>· {{ t.projectName || '' }}</text>
						</view>
						<view class="task-time overdue">
							<text class="time-label">截止：</text>
							<text class="time-val">{{ t.endDate || '-' }}</text>
							<text v-if="t.actualEndDate" class="time-sep">/ 实际：{{ t.actualEndDate }}</text>
						</view>
					</view>
					<view class="task-progress">
						<text class="task-progress-num">{{ t.progress || 0 }}%</text>
					</view>
				</view>
			</view>
			<view v-else class="empty-sm success-text">暂无逾期任务</view>

			<!-- 报表生成信息 -->
			<view class="footer-info">
				<text>报表区间：{{ filterStartDate }} 至 {{ filterEndDate }}</text>
				<text>生成时间：{{ report.generateTime || formatDateTime(report.createTime) || '-' }}</text>
			</view>

			<!-- 操作提示 -->
			<view class="action-tip">
				<text class="tip-icon">ℹ</text>
				<text>小程序暂不支持 Excel 导出，如需导出请使用 Web 端。</text>
			</view>
		</template>

		<!-- 空态 -->
		<view v-else class="empty">
			<view class="empty-icon" aria-hidden="true">
				<image class="svg-icon-lg" src="/static/icon/资料.svg" mode="aspectFit" />
			</view>
			<text>暂无日报数据</text>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { dailyReportApi } from '@/api/dailyReport'
	import { projectApi } from '@/api/project'
	import { formatDateTime, formatDate } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				filterStartDate: '',   // 开始日期 yyyy-MM-dd（后端必填）
				filterEndDate: '',     // 结束日期 yyyy-MM-dd（后端必填）
				activeRange: 'today',  // 当前快捷选中范围
				projectOptions: [{ id: '', name: '全部项目' }], // 项目筛选项
				projectSelectorIndex: 0, // 项目选择器索引
				currentProjectName: '全部项目',
				report: null,           // 日报数据
				loading: false
			}
		},
		computed: {
			/** 概览数据 */
			overview() {
				return (this.report && this.report.overview) || {}
			},
			/** 项目进度列表 */
			projectProgress() {
				const list = this.report && this.report.projectProgress
				return Array.isArray(list) ? list : []
			},
			/** 任务进度列表 */
			taskProgress() {
				const list = this.report && this.report.taskProgress
				return Array.isArray(list) ? list : []
			},
			/** 延期任务列表 */
			delayedTasks() {
				const list = this.report && this.report.delayedTasks
				return Array.isArray(list) ? list : []
			}
		},
		onLoad(options) {
			// 默认查询今日（startDate = endDate = 今天）
			this.setQuickRange('today')
			this.loadProjectOptions()
			this.loadPreview()
		},
		methods: {
			formatDateTime,
			/** 加载项目筛选项 */
			async loadProjectOptions() {
				try {
					const res = await projectApi.getList({ pageNum: 1, pageSize: 50 })
					const list = (res && (res.records || res.list || res)) || []
					this.projectOptions = [{ id: '', name: '全部项目' }]
						.concat(list.map(p => ({ id: p.id, name: p.name })))
				} catch (e) {
					console.warn('project options error', e)
				}
			},
			/** 开始日期切换 */
			onStartDateChange(e) {
				this.filterStartDate = e.detail.value
				this.activeRange = ''
				this.loadPreview()
			},
			/** 结束日期切换 */
			onEndDateChange(e) {
				this.filterEndDate = e.detail.value
				this.activeRange = ''
				this.loadPreview()
			},
			/**
			 * 快捷选择日期区间
			 * @param {'today'|'week'|'month'} type 范围类型
			 */
			setQuickRange(type) {
				const today = new Date()
				let start
				if (type === 'today') {
					start = today
				} else if (type === 'week') {
					// 本周一至今天
					const day = today.getDay() || 7
					start = new Date(today)
					start.setDate(today.getDate() - day + 1)
				} else if (type === 'month') {
					// 本月1号至今天
					start = new Date(today.getFullYear(), today.getMonth(), 1)
				} else {
					return
				}
				this.filterStartDate = formatDate(start)
				this.filterEndDate = formatDate(today)
				this.activeRange = type
				this.loadPreview()
			},
			/** 项目切换 */
			onProjectChange(e) {
				this.projectSelectorIndex = e.detail.value
				const opt = this.projectOptions[e.detail.value]
				this.currentProjectName = opt ? opt.name : '全部项目'
				this.loadPreview()
			},
			/** 加载日报预览数据 */
			async loadPreview() {
				if (this.loading) return
				if (!this.filterStartDate || !this.filterEndDate) {
					uni.showToast({ title: '请选择日期区间', icon: 'none' })
					return
				}
				if (this.filterStartDate > this.filterEndDate) {
					uni.showToast({ title: '开始日期不能晚于结束日期', icon: 'none' })
					return
				}
				this.loading = true
				try {
					// 后端契约：startDate + endDate 均必填
					const params = {
						startDate: this.filterStartDate,
						endDate: this.filterEndDate
					}
					const selected = this.projectOptions[this.projectSelectorIndex]
					if (selected && selected.id) params.projectId = selected.id
					const res = await dailyReportApi.preview(params)
					this.report = res || null
				} catch (e) {
					console.warn('preview error', e)
					this.report = null
				} finally {
					this.loading = false
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

	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	/* 顶部筛选条 */
	.filter-card {
		padding: 24rpx 28rpx;
	}
	.filter-row {
		display: flex;
		align-items: center;
		padding: 12rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.filter-row:last-of-type {
		border-bottom: none;
	}
	.filter-label {
		width: 100rpx;
		font-size: 26rpx;
		color: #5C6370;
		flex-shrink: 0;
	}
	.picker-value {
		flex: 1;
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 28rpx;
		color: #303133;
	}
	.picker-arrow {
		color: #909399;
		font-size: 24rpx;
	}
	.refresh-btn {
		margin-top: 16rpx;
		height: 76rpx;
		line-height: 76rpx;
		font-size: 28rpx;
		background: #1890FF;
		color: #FFFFFF;
		border-radius: 8rpx;
		border: none;
		padding: 0;
	}
	.refresh-btn[disabled] {
		opacity: 0.6;
	}

	/* 快捷日期范围按钮组 */
	.quick-range {
		display: flex;
		gap: 12rpx;
		margin-top: 16rpx;
	}
	.quick-btn {
		flex: 1;
		text-align: center;
		padding: 12rpx 0;
		font-size: 24rpx;
		color: #5C6370;
		background: #F5F7FA;
		border-radius: 8rpx;
	}
	.quick-btn.active {
		color: #FFFFFF;
		background: #1890FF;
	}

	/* 区块标题 */
	.section-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 16rpx 4rpx 8rpx;
	}
	.section-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
	}
	.section-count {
		font-size: 24rpx;
		color: #5C6370;
	}
	.section-count.danger { color: #FF4D4F; }

	/* 概览网格 */
	.overview-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 16rpx 0;
		padding: 32rpx 28rpx;
	}
	.overview-item {
		width: 33.33%;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.ov-num {
		font-size: 40rpx;
		font-weight: 700;
		color: #303133;
		line-height: 1.2;
	}
	.ov-num.primary { color: #1890FF; }
	.ov-num.success { color: #52C41A; }
	.ov-num.warning { color: #FAAD14; }
	.ov-num.danger { color: #FF4D4F; }
	.ov-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}

	/* 项目进度行 */
	.progress-row {
		display: flex;
		align-items: center;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.progress-row:last-child {
		border-bottom: none;
	}
	.proj-info {
		flex: 1;
		padding-right: 20rpx;
	}
	.proj-name {
		font-size: 28rpx;
		font-weight: 500;
		color: #303133;
		margin-bottom: 4rpx;
	}
	.proj-meta {
		display: flex;
		gap: 12rpx;
		font-size: 22rpx;
		color: #5C6370;
		align-items: center;
	}
	.status-tag {
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		font-size: 20rpx;
	}
	.proj-progress {
		width: 200rpx;
		flex-shrink: 0;
	}
	.progress-bar {
		height: 12rpx;
		background: #EBEEF5;
		border-radius: 6rpx;
		overflow: hidden;
		margin-bottom: 4rpx;
	}
	.progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 6rpx;
	}
	.progress-num {
		font-size: 22rpx;
		color: #096DD9;
		font-weight: 600;
	}

	/* 任务进度行 */
	.task-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.task-row:last-child {
		border-bottom: none;
	}
	.task-row.overdue-row .task-name {
		color: #FF4D4F;
	}
	.task-info {
		flex: 1;
		padding-right: 20rpx;
	}
	.task-name {
		font-size: 26rpx;
		font-weight: 500;
		color: #303133;
		margin-bottom: 4rpx;
	}
	.task-meta {
		display: flex;
		gap: 8rpx;
		font-size: 22rpx;
		color: #5C6370;
		align-items: center;
	}
	/* 任务时间行：计划/实际日期 */
	.task-time {
		display: flex;
		align-items: center;
		gap: 6rpx;
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #909399;
	}
	.task-time.actual {
		color: #096DD9;
	}
	.task-time.overdue {
		color: #FF4D4F;
	}
	.time-label {
		color: #909399;
	}
	.time-val {
		color: #5C6370;
	}
	.time-sep {
		color: #C0C4CC;
		padding: 0 4rpx;
	}
	.danger-text { color: #FF4D4F; font-weight: 600; }
	.task-progress {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		gap: 8rpx;
	}
	.task-progress-num {
		font-size: 24rpx;
		font-weight: 600;
		color: #303133;
	}

	/* 小型空态 */
	.empty-sm {
		text-align: center;
		color: #909399;
		font-size: 24rpx;
		padding: 40rpx 0;
	}
	.empty-sm.success-text {
		color: #52C41A;
	}

	/* 底部信息 */
	.footer-info {
		display: flex;
		justify-content: space-between;
		padding: 24rpx 0;
		font-size: 22rpx;
		color: #5C6370;
	}
	.action-tip {
		display: flex;
		gap: 12rpx;
		align-items: flex-start;
		background: #FFFBE6;
		border: 1rpx solid #FFE58F;
		border-radius: 8rpx;
		padding: 20rpx;
		font-size: 24rpx;
		color: #8C6E2A;
		line-height: 1.5;
		margin-bottom: 20rpx;
	}
	.tip-icon {
		flex-shrink: 0;
		color: #FAAD14;
		font-weight: 700;
	}

	/* 骨架屏 */
	.skeleton-card {
		padding: 32rpx 28rpx;
	}
	.sk-line {
		height: 28rpx;
		margin-bottom: 20rpx;
		border-radius: 8rpx;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
	}
	.sk-line:last-child { margin-bottom: 0; }
	.sk-w240 { width: 240rpx; }
	.sk-w160 { width: 160rpx; }
	.sk-w-full { width: 100%; }
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
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
