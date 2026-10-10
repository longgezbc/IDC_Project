<!--
  风险预警看板页（PM 视角）
  业务用途：PM 聚合查看所负责项目的风险信息——逾期任务、即将到期任务、待审核填报数、
           各项目风险等级，便于快速定位高风险项目与任务并跟进处理。
  数据来源：dashboardApi.getRiskBoard（GET /dashboard/risk-board）。
  导航特性：非 tabBar 页；仅 PM / admin 可访问，onLoad 校验权限（无权限 toast 后返回）；
           支持下拉刷新（onPullDownRefresh）；点击项目卡片进入项目详情，点击任务项进入进度填报页。
-->
<template>
	<view class="page">
		<!-- 风险概览：逾期 / 即将到期 / 待审核填报 -->
		<view class="stat-row">
			<view class="stat-card">
				<view class="stat-num danger">{{ overdueTasks.length }}</view>
				<view class="stat-label">逾期任务</view>
			</view>
			<view class="stat-card">
				<view class="stat-num warning">{{ expiringTasks.length }}</view>
				<view class="stat-label">即将到期</view>
			</view>
			<view class="stat-card">
				<view class="stat-num primary">{{ pendingReviewCount }}</view>
				<view class="stat-label">待审核填报</view>
			</view>
		</view>

		<!-- 空状态：完全没有风险时展示 -->
		<view v-if="isEmpty" class="empty">
			<view class="empty-icon">✅</view>
			<text class="empty-text">暂无风险，项目运行良好</text>
		</view>

		<template v-else>
			<!-- 风险等级图例 -->
			<view class="legend">
				<view class="legend-item"><view class="legend-dot high"></view>高风险</view>
				<view class="legend-item"><view class="legend-dot medium"></view>中风险</view>
				<view class="legend-item"><view class="legend-dot low"></view>低风险</view>
			</view>

			<!-- 项目风险列表 -->
			<view v-if="projectRisks.length" class="section">
				<view class="section-header">
					<text class="section-title">项目风险</text>
				</view>
				<view
				v-for="p in projectRisks"
				:key="p.projectId"
				class="risk-card"
				:class="'risk-' + (p.riskLevel || 'low')"
				hover-class="uni-hover"
				:hover-stay-time="80"
				role="link"
				:aria-label="'查看项目' + (p.projectName || '') + '详情，逾期' + (p.overdueCount || 0) + '个任务，即将到期' + (p.expiringCount || 0) + '个任务'"
				@click="goProjectDetail(p.projectId)"
			>
					<view class="risk-head">
						<view class="risk-name">{{ p.projectName }}</view>
						<view class="tag" :style="statusStyle(p.status)">{{ p.status || '未知' }}</view>
					</view>
					<view class="risk-progress">
						<view class="progress-bar">
							<view class="progress-inner" :style="{ width: (p.progress || 0) + '%' }"></view>
						</view>
						<text class="progress-text">{{ p.progress || 0 }}%</text>
					</view>
					<view class="risk-stats">
						<text class="risk-stat danger">逾期 {{ p.overdueCount || 0 }}</text>
						<text class="risk-stat warning">即将到期 {{ p.expiringCount || 0 }}</text>
					</view>
				</view>
			</view>

			<!-- 逾期任务（可折叠，默认展开） -->
			<view v-if="overdueTasks.length" class="section">
				<view class="section-header" role="button" hover-class="uni-hover" :hover-stay-time="80" @click="toggleOverdue">
					<text class="section-title">逾期任务</text>
					<view class="section-count">
						<text class="count-num">{{ overdueTasks.length }}</text>
						<text class="chevron" :class="{ expanded: overdueExpanded }">›</text>
					</view>
				</view>
				<view v-if="overdueExpanded" class="section-body">
					<view
						v-for="(t, idx) in overdueTasks"
					:key="t.id"
					class="task-item"
					:class="{ 'no-border': idx === overdueTasks.length - 1 }"
					hover-class="uni-hover"
					:hover-stay-time="80"
					role="link"
					@click="goTaskReport(t)"
					>
						<view class="task-name">{{ t.name }}</view>
						<view class="task-meta">
							<text class="meta-project">{{ t.projectName }}</text>
							<text class="meta-date danger">{{ formatDate(t.endDate) }}</text>
						</view>
						<view class="task-foot">
							<text class="meta-assignee">{{ t.assignee || '未指派' }}</text>
							<text class="task-progress">{{ t.progress || 0 }}%</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 即将到期任务（可折叠，默认展开） -->
			<view v-if="expiringTasks.length" class="section">
				<view class="section-header" role="button" hover-class="uni-hover" :hover-stay-time="80" @click="toggleExpiring">
					<text class="section-title">即将到期</text>
					<view class="section-count">
						<text class="count-num">{{ expiringTasks.length }}</text>
						<text class="chevron" :class="{ expanded: expiringExpanded }">›</text>
					</view>
				</view>
				<view v-if="expiringExpanded" class="section-body">
					<view
						v-for="(t, idx) in expiringTasks"
					:key="t.id"
					class="task-item"
					:class="{ 'no-border': idx === expiringTasks.length - 1 }"
					hover-class="uni-hover"
					:hover-stay-time="80"
					role="link"
					@click="goTaskReport(t)"
					>
						<view class="task-name">{{ t.name }}</view>
						<view class="task-meta">
							<text class="meta-project">{{ t.projectName }}</text>
							<text class="meta-date warning">剩 {{ t.daysLeft }} 天</text>
						</view>
						<view class="task-foot">
							<text class="meta-assignee">{{ t.assignee || '未指派' }}</text>
							<text class="task-progress">{{ t.progress || 0 }}%</text>
						</view>
					</view>
				</view>
			</view>
		</template>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { dashboardApi } from '@/api/dashboard'
	import { formatDate, getProjectStatus } from '@/utils/format'
	import { canViewRiskBoard } from '@/utils/permission'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				overdueTasks: [],        // 逾期任务列表
				expiringTasks: [],       // 即将到期任务列表（≤3 天）
				pendingReviewCount: 0,   // 待审核进度填报数
				projectRisks: [],        // 项目风险概要列表
				loading: false,          // 加载中标记（防重复请求）
				loaded: false,           // 首次加载完成标记（控制空状态展示时机）
				overdueExpanded: true,   // 逾期任务区折叠状态（默认展开）
				expiringExpanded: true   // 即将到期区折叠状态（默认展开）
			}
		},
		computed: {
			/** 是否完全没有风险：逾期、即将到期、待审核、项目风险均为空（且首次加载已完成） */
			isEmpty() {
				if (!this.loaded) return false
				return this.overdueTasks.length === 0
					&& this.expiringTasks.length === 0
					&& this.pendingReviewCount === 0
					&& this.projectRisks.length === 0
			}
		},
		// 页面加载：校验权限后加载数据；无权限提示并返回
		onLoad() {
			if (!canViewRiskBoard()) {
				uni.showToast({ title: '无权限访问', icon: 'none' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 1000)
				return
			}
			this.loadData()
		},
		// 下拉刷新：先校验权限，无权限时直接停止刷新动画
		onPullDownRefresh() {
			if (!canViewRiskBoard()) {
				uni.stopPullDownRefresh()
				return
			}
			this.loadData(true)
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			formatDate,
			/** 项目状态标签内联样式（文字色 + 背景色） */
			statusStyle(s) {
				const o = getProjectStatus(s)
				return { color: o.color, background: o.bg }
			},
			/**
			 * 加载风险看板数据
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async loadData(stopPull) {
				if (this.loading) return
				this.loading = true
				uni.showLoading({ title: '加载中', mask: true })
				try {
					const data = await dashboardApi.getRiskBoard()
					this.overdueTasks = (data && data.overdueTasks) || []
					this.expiringTasks = (data && data.expiringTasks) || []
					this.pendingReviewCount = (data && data.pendingReviewCount) || 0
					this.projectRisks = (data && data.projectRisks) || []
					this.loaded = true
				} catch (e) {
					console.warn('risk board error', e)
					uni.showToast({ title: '加载失败', icon: 'none' })
				} finally {
					this.loading = false
					uni.hideLoading()
					if (stopPull) uni.stopPullDownRefresh()
				}
			},
			/** 切换逾期任务区折叠状态 */
			toggleOverdue() {
				this.overdueExpanded = !this.overdueExpanded
			},
			/** 切换即将到期区折叠状态 */
			toggleExpiring() {
				this.expiringExpanded = !this.expiringExpanded
			},
			/** 跳转项目详情 */
			goProjectDetail(projectId) {
				if (!projectId) return
				uni.navigateTo({ url: `/pages/project/detail?id=${projectId}` })
			},
			/** 跳转进度填报页 */
			goTaskReport(task) {
				if (!task || !task.id) return
				uni.navigateTo({ url: `/pages/progress/report?taskId=${task.id}&projectId=${task.projectId || ''}` })
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

	/* 风险概览统计卡：3 列 */
	.stat-row {
		display: flex;
		gap: 16rpx;
		margin-bottom: 24rpx;
	}
	.stat-card {
		flex: 1;
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
		text-align: center;
	}
	.stat-num {
		font-size: 44rpx;
		font-weight: 700;
		line-height: 1.2;
	}
	.stat-num.danger { color: #D4380D; }
	.stat-num.warning { color: #D48806; }
	.stat-num.primary { color: #096DD9; }
	.stat-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}

	/* 风险等级图例 */
	.legend {
		display: flex;
		gap: 24rpx;
		padding: 0 4rpx 20rpx;
		font-size: 22rpx;
		color: #5C6370;
	}
	.legend-item {
		display: flex;
		align-items: center;
	}
	.legend-dot {
		width: 14rpx;
		height: 14rpx;
		border-radius: 50%;
		margin-right: 8rpx;
	}
	.legend-dot.high { background: #FF4D4F; }
	.legend-dot.medium { background: #FAAD14; }
	.legend-dot.low { background: #52C41A; }

	/* 区块通用 */
	.section {
		margin-bottom: 24rpx;
	}
	.section-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 0 4rpx 16rpx;
	}
	.section-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
	}
	.section-count {
		display: flex;
		align-items: center;
	}
	.count-num {
		font-size: 24rpx;
		color: #5C6370;
		margin-right: 8rpx;
	}
	/* 折叠箭头：展开时旋转 90deg */
	.chevron {
		font-size: 32rpx;
		color: #767E8A;
		display: inline-block;
		transform: rotate(0deg);
		transition: transform 0.2s ease;
		line-height: 1;
	}
	.chevron.expanded {
		transform: rotate(90deg);
	}
	.section-body {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 0 24rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	/* 项目风险卡片：左侧色带表示风险等级 */
	.risk-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.risk-card.risk-high { border-left: 6rpx solid #FF4D4F; }
	.risk-card.risk-medium { border-left: 6rpx solid #FAAD14; }
	.risk-card.risk-low { border-left: 6rpx solid #52C41A; }
	.risk-head {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 16rpx;
	}
	.risk-name {
		font-size: 30rpx;
		font-weight: 600;
		color: #303133;
		flex: 1;
		margin-right: 12rpx;
	}
	.tag {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.risk-progress {
		display: flex;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.progress-bar {
		flex: 1;
		height: 10rpx;
		background: #EBEEF5;
		border-radius: 5rpx;
		overflow: hidden;
		margin-right: 16rpx;
	}
	.progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 5rpx;
	}
	.progress-text {
		font-size: 24rpx;
		color: #096DD9;
		font-weight: 600;
		min-width: 60rpx;
		text-align: right;
	}
	.risk-stats {
		display: flex;
		gap: 24rpx;
		font-size: 24rpx;
	}
	.risk-stat.danger { color: #D4380D; }
	.risk-stat.warning { color: #D48806; }

	/* 任务项 */
	.task-item {
		padding: 24rpx 0;
		border-bottom: 1rpx solid #F0F2F5;
	}
	.task-item.no-border { border-bottom: none; }
	.task-name {
		font-size: 28rpx;
		color: #303133;
		font-weight: 500;
		margin-bottom: 10rpx;
	}
	.task-meta {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 8rpx;
		font-size: 22rpx;
	}
	.meta-project {
		color: #5C6370;
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		margin-right: 16rpx;
	}
	.meta-date {
		flex-shrink: 0;
	}
	.meta-date.danger { color: #D4380D; }
	.meta-date.warning { color: #D48806; }
	.task-foot {
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 22rpx;
		color: #5C6370;
	}
	.task-progress {
		color: #096DD9;
		font-weight: 600;
	}

	/* 空状态 */
	.empty {
		padding: 160rpx 40rpx;
		text-align: center;
	}
	.empty-icon {
		font-size: 96rpx;
		margin-bottom: 24rpx;
		line-height: 1;
	}
	.empty-text {
		font-size: 28rpx;
		color: #5C6370;
	}

	.bottom-safe {
		height: calc(40rpx + env(safe-area-inset-bottom));
	}
</style>
