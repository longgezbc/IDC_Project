<!--
  首页（tabBar 页面）
  业务用途：项目负责人移动工作台首页，展示问候语、项目统计概览、工作台汇总数据、快捷入口、
           关键节点提醒与近期项目列表。
  数据来源：projectApi.getStatistics（项目统计）、taskApi.getUpcomingAll（关键节点）、
           projectApi.getList（近期项目）、dashboardApi.getSummary（工作台汇总）、
           notificationApi.getUnreadCount（未读通知数）；用户信息来自 userStore。
  导航特性：本页为 tabBar 首页；未登录时在 onShow 中 reLaunch 到登录页；
           支持下拉刷新（onPullDownRefresh）；快捷入口按角色差异化展示。
-->
<template>
	<view class="page">
		<!-- 顶部问候区 -->
		<view class="hero">
			<view class="hero-bg"></view>
			<view class="hero-content" :style="{ paddingTop: 'calc(' + statusBarHeight + 'px + 24rpx)' }">
				<view class="hero-top">
					<view class="greet">
						<view class="greet-hi">{{ greetText }}，{{ userName || '负责人' }}</view>
						<view class="greet-sub">{{ todayText }}</view>
					</view>
					<view class="hero-actions">
						<!-- 通知铃铛：右上角带未读角标 -->
						<view class="bell" role="button" aria-label="通知" @click="goNotification">
						<image class="bell-icon" src="/static/icon/通知.svg" mode="aspectFit" aria-hidden="true" />
						<view v-if="unreadCount > 0" class="bell-badge" aria-label="未读数量">
							<text v-if="unreadCount > 99">99+</text>
							<text v-else>{{ unreadCount }}</text>
						</view>
					</view>
						<view class="avatar" role="link" aria-label="个人中心" @click="goProfile">{{ userInitial }}</view>
					</view>
				</view>

				<!-- 项目统计卡片 -->
				<view class="stat-grid">
					<view class="stat-item">
						<view class="stat-num">{{ stats.total || 0 }}</view>
						<view class="stat-label">项目总数</view>
					</view>
					<view class="stat-divider"></view>
					<view class="stat-item">
						<view class="stat-num primary">{{ stats.inProgress || 0 }}</view>
						<view class="stat-label">进行中</view>
					</view>
					<view class="stat-divider"></view>
					<view class="stat-item">
						<view class="stat-num success">{{ stats.completed || 0 }}</view>
						<view class="stat-label">已完成</view>
					</view>
				</view>
			</view>
		</view>

		<!-- 工作台汇总：按角色差异化展示 4 张统计卡片 -->
		<view class="summary-card">
			<template v-for="(card, idx) in currentRoleCards" :key="card.key">
				<view
				class="summary-item"
				:role="card.action ? 'link' : 'text'"
				:hover-class="card.action ? 'uni-hover' : ''"
				:hover-stay-time="80"
				@click="onRoleCardClick(card)"
			>
					<view class="summary-num" :class="card.tone">{{ roleCardValues[card.valueKey] }}</view>
					<view class="summary-label">{{ card.label }}</view>
				</view>
				<view v-if="idx < currentRoleCards.length - 1" class="summary-divider"></view>
			</template>
		</view>

		<!-- 快捷入口 -->
		<view class="quick-card">
			<view class="quick-row">
				<view class="quick-item" role="link" aria-label="项目" hover-class="uni-hover" :hover-stay-time="80" @click="goTab('/pages/project/list')">
					<view class="quick-icon icon-blue" aria-hidden="true"><image class="svg-icon" src="/static/icon/项目.svg" mode="aspectFit" /></view>
					<text class="quick-text">项目</text>
				</view>
				<view class="quick-item" role="link" aria-label="资料" hover-class="uni-hover" :hover-stay-time="80" @click="goTab('/pages/document/list')">
					<view class="quick-icon icon-purple" aria-hidden="true"><image class="svg-icon" src="/static/icon/资料.svg" mode="aspectFit" /></view>
					<text class="quick-text">资料</text>
				</view>
				<view class="quick-item" role="link" aria-label="我的任务" hover-class="uni-hover" :hover-stay-time="80" @click="goMyTasks">
					<view class="quick-icon icon-cyan" aria-hidden="true"><image class="svg-icon" src="/static/icon/任务.svg" mode="aspectFit" /></view>
					<text class="quick-text">我的任务</text>
				</view>
				<view class="quick-item" role="link" aria-label="我的填报" hover-class="uni-hover" :hover-stay-time="80" @click="goMySubmissions">
				<view class="quick-icon icon-orange" aria-hidden="true"><image class="svg-icon" src="/static/icon/填报.svg" mode="aspectFit" /></view>
				<text class="quick-text">我的填报</text>
			</view>
		</view>
		<!-- 工作量快捷入口：填报全员可用；审核与审批中心仅 PM/admin -->
		<view class="quick-row quick-row-gap">
			<view class="quick-item" role="link" aria-label="工作量填报" hover-class="uni-hover" :hover-stay-time="80" @click="goWorkloadReport">
				<view class="quick-icon icon-green" aria-hidden="true"><image class="svg-icon" src="/static/icon/奖杯.svg" mode="aspectFit" /></view>
				<text class="quick-text">工作量填报</text>
			</view>
			<view v-if="canReview" class="quick-item" role="link" aria-label="工作量审核" hover-class="uni-hover" :hover-stay-time="80" @click="goWorkloadReview">
				<view class="quick-icon icon-cyan" aria-hidden="true"><image class="svg-icon" src="/static/icon/审核.svg" mode="aspectFit" /></view>
				<text class="quick-text">工作量审核</text>
			</view>
			<view v-if="canReview" class="quick-item" role="link" aria-label="审批中心" hover-class="uni-hover" :hover-stay-time="80" @click="goApprovalTodo">
				<view class="quick-icon icon-purple" aria-hidden="true"><image class="svg-icon" src="/static/icon/五角星.svg" mode="aspectFit" /></view>
				<text class="quick-text">审批中心</text>
			</view>
		</view>
		<view class="quick-row quick-row-gap">
				<view v-if="canReview" class="quick-item" role="link" aria-label="进度审核" hover-class="uni-hover" :hover-stay-time="80" @click="goPendingReview">
					<view class="quick-icon icon-red" aria-hidden="true"><image class="svg-icon" src="/static/icon/审核.svg" mode="aspectFit" /></view>
					<text class="quick-text">进度审核</text>
				</view>
				<view v-if="canReview" class="quick-item" role="link" aria-label="风险看板" hover-class="uni-hover" :hover-stay-time="80" @click="goRiskBoard">
					<view class="quick-icon icon-yellow" aria-hidden="true"><image class="svg-icon" src="/static/icon/风险.svg" mode="aspectFit" /></view>
					<text class="quick-text">风险看板</text>
				</view>
				<view v-if="canReview" class="quick-item" role="link" aria-label="客户管理" hover-class="uni-hover" :hover-stay-time="80" @click="goCustomer">
					<view class="quick-icon icon-green2" aria-hidden="true"><image class="svg-icon" src="/static/icon/客户.svg" mode="aspectFit" /></view>
					<text class="quick-text">客户管理</text>
				</view>
				<view v-if="canReview" class="quick-item" role="link" aria-label="项目授权" hover-class="uni-hover" :hover-stay-time="80" @click="goProjectAuth">
					<view class="quick-icon icon-indigo" aria-hidden="true"><image class="svg-icon" src="/static/icon/转移.svg" mode="aspectFit" /></view>
					<text class="quick-text">项目授权</text>
				</view>
				<!-- 工程师视角占位：通知入口（PM 视角上面已展示，这里给非 PM 用） -->
				<view v-if="!canReview" class="quick-item" role="link" aria-label="通知" hover-class="uni-hover" :hover-stay-time="80" @click="goNotification">
					<view class="quick-icon icon-red" aria-hidden="true"><image class="svg-icon" src="/static/icon/通知.svg" mode="aspectFit" /></view>
					<text class="quick-text">通知</text>
				</view>
				<view v-if="!canReview" class="quick-item" role="link" aria-label="客户" hover-class="uni-hover" :hover-stay-time="80" @click="goCustomer">
					<view class="quick-icon icon-green2" aria-hidden="true"><image class="svg-icon" src="/static/icon/客户.svg" mode="aspectFit" /></view>
					<text class="quick-text">客户</text>
				</view>
				<view v-if="!canReview" class="quick-item" role="button" aria-label="关键节点" hover-class="uni-hover" :hover-stay-time="80" @click="loadUpcoming">
					<view class="quick-icon icon-orange" aria-hidden="true"><image class="svg-icon" src="/static/icon/闹钟.svg" mode="aspectFit" /></view>
					<text class="quick-text">关键节点</text>
				</view>
				<view v-if="!canReview" class="quick-item" role="link" aria-label="我的" hover-class="uni-hover" :hover-stay-time="80" @click="goProfile">
					<view class="quick-icon icon-green" aria-hidden="true"><image class="svg-icon" src="/static/icon/我的.svg" mode="aspectFit" /></view>
					<text class="quick-text">我的</text>
				</view>
			</view>
		</view>

		<!-- 关键节点提醒 -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">关键节点提醒</text>
				<text v-if="upcoming.length" class="section-more" role="button" @click="loadUpcoming">刷新</text>
			</view>
			<view v-if="loadingUpcoming" class="card">
				<view class="skeleton-line" v-for="i in 3" :key="i"></view>
			</view>
			<view v-else-if="upcoming.length" class="card list-card">
				<view
				v-for="(item, idx) in upcoming"
				:key="item.id || idx"
				class="node-item"
				:class="{ 'no-border': idx === upcoming.length - 1 }"
				role="link"
				hover-class="uni-hover"
				:hover-stay-time="80"
				@click="goTaskReport(item.id, item.projectId)"
			>
					<view class="node-dot" :class="{ milestone: item.isMilestone }"></view>
					<view class="node-body">
						<view class="node-title">
							<text class="node-name">{{ item.name }}</text>
							<text v-if="item.isMilestone" class="tag tag-milestone">里程碑</text>
							<text v-else-if="item.isKey" class="tag tag-key">关键</text>
						</view>
						<view class="node-meta">
							<text class="meta-text">{{ item.projectName || '项目' }}</text>
							<text class="meta-date">{{ formatDate(item.endDate) }}</text>
						</view>
					</view>
					<view class="node-progress">{{ item.progress || 0 }}%</view>
				</view>
			</view>
			<view v-else class="empty">
				<view class="empty-icon" aria-hidden="true"><image class="svg-icon-lg" src="/static/icon/日期.svg" mode="aspectFit" /></view>
				<text>暂无关键节点提醒</text>
			</view>
		</view>

		<!-- 近期项目 -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">近期项目</text>
				<text class="section-more" role="link" @click="goTab('/pages/project/list')">查看全部</text>
			</view>
			<view v-if="recentProjects.length" class="card list-card">
				<view
				v-for="(p, idx) in recentProjects"
				:key="p.id"
				class="project-item"
				:class="{ 'no-border': idx === recentProjects.length - 1 }"
				role="link"
				hover-class="uni-hover"
				:hover-stay-time="80"
				@click="goProjectDetail(p.id)"
			>
					<view class="proj-main">
						<view class="proj-title">{{ p.name }}</view>
						<view class="proj-sub">{{ p.code }} · {{ p.manager || '未指定' }}</view>
					</view>
					<view class="proj-right">
						<view class="proj-progress">
							<view class="progress-bar">
								<view class="progress-inner" :style="{ width: (p.progress || 0) + '%' }"></view>
							</view>
							<text class="progress-text">{{ p.progress || 0 }}%</text>
						</view>
						<view class="tag" :style="statusStyle(p.status)">{{ statusText(p.status) }}</view>
					</view>
				</view>
			</view>
			<view v-else class="empty">
				<view class="empty-icon" aria-hidden="true"><image class="svg-icon-lg" src="/static/icon/项目.svg" mode="aspectFit" /></view>
				<text>暂无项目</text>
			</view>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { userStore } from '@/store/user'
	import { projectApi } from '@/api/project'
	import { taskApi } from '@/api/task'
	import { dashboardApi } from '@/api/dashboard'
	import { notificationApi } from '@/api/notification'
	import { documentApi } from '@/api/document'
	import { statisticsApi } from '@/api/statistics'
	import { canReviewProgress, canManageCustomer, canTransferProject } from '@/utils/permission'
	import { formatDate, getProjectStatus } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'

	// 角色卡片配置外移为模块级常量，避免每次组件实例化时重新创建对象
	// （Web Vitals GC 优化：减少 data 体积与 GC 压力）
	const ROLE_CARDS = {
		admin: [
			{ key: 'a1', label: '进行中项目', valueKey: 'inProgressProjects', tone: 'primary', action: 'goTab', target: '/pages/project/list' },
			{ key: 'a2', label: '待审核进度', valueKey: 'pendingProgress', tone: 'warning', action: 'goPendingReview' },
			{ key: 'a3', label: '文档总数', valueKey: 'docTotal', tone: '', action: 'goTab', target: '/pages/document/list' },
			{ key: 'a4', label: '合同总额', valueKey: 'contractAmount', tone: 'success', action: null }
		],
		pm: [
			{ key: 'p1', label: '待我审核', valueKey: 'pendingProgress', tone: 'warning', action: 'goPendingReview' },
			{ key: 'p2', label: '管理项目', valueKey: 'managedProjects', tone: 'primary', action: 'goTab', target: '/pages/project/list' },
			{ key: 'p3', label: '关键节点', valueKey: 'keyNodes', tone: '', action: 'loadUpcoming' },
			{ key: 'p4', label: '未读通知', valueKey: 'unread', tone: 'danger', action: 'goNotification' }
		],
		engineer: [
			{ key: 'e1', label: '我的任务', valueKey: 'engineerMyTasks', tone: 'primary', action: 'goMyTasks' },
			{ key: 'e2', label: '待办任务', valueKey: 'todoTasks', tone: '', action: 'goMyTasks' },
			{ key: 'e3', label: '逾期任务', valueKey: 'overdueTasks', tone: 'danger', action: 'goMyTasks' },
			{ key: 'e4', label: '待审核填报', valueKey: 'pendingSubmissions', tone: 'warning', action: 'goMySubmissions' }
		],
		customer: [
			{ key: 'c1', label: '我的任务', valueKey: 'customerMyTasks', tone: 'primary', action: 'goMyTasks' },
			{ key: 'c2', label: '未读通知', valueKey: 'unread', tone: 'danger', action: 'goNotification' },
			{ key: 'c3', label: '文档总数', valueKey: 'docTotal', tone: '', action: 'goTab', target: '/pages/document/list' },
			{ key: 'c4', label: '进行中项目', valueKey: 'inProgressProjects', tone: 'success', action: 'goTab', target: '/pages/project/list' }
		]
	}

	export default {
		mixins: [statusMixin],
		data() {
		return {
			statusBarHeight: 20,            // 状态栏高度（rpx），用于自定义头部避让
			stats: { total: 0, inProgress: 0, completed: 0 }, // 项目统计概览
			summary: {                     // 工作台汇总统计
				myTasks: 0,
				mySubmissions: 0,
				pendingReview: 0,
				unreadNotifications: 0
			},
			unreadCount: 0,                // 未读通知数（顶部铃铛角标，与 summary.unreadNotifications 同步）
			upcoming: [],                   // 关键节点提醒列表（最多 5 条）
			recentProjects: [],             // 近期项目列表（最多 5 条）
			loadingUpcoming: false,         // 关键节点加载中标记（防重复请求）
			lastLoadTime: 0,                // 上次数据加载时间戳（用于 onShow 节流）
			// 角色差异化卡片所需数据（按需异步加载，复用 stats/summary/unreadCount/upcoming 避免重复请求）
			roleData: {
				pendingProgressCount: 0,    // 待审核进度数（admin 待审核进度 / pm 待我审核）
				docTotal: 0,                // 文档总数（admin / customer）
				contractAmount: 0,          // 合同总额（admin）
				myTasksTotal: null,         // 我的任务总数（engineer，来自 getMyTasks）
				myTasksTodo: 0,             // 待办任务数（engineer，未完成）
				myTasksOverdue: 0           // 逾期任务数（engineer，未完成且逾期）
			}
			// 注：roleCards 配置外移为模块级常量 ROLE_CARDS，避免每次组件实例化重新创建
		}
	},
		computed: {
			/** 当前用户名称 */
			userName() {
				return userStore.userName
			},
			/** 头像占位字符（取用户名首字母大写） */
			userInitial() {
				const n = this.userName || 'U'
				return n.charAt(0).toUpperCase()
			},
			/** 根据当前时段返回问候语 */
			greetText() {
				const h = new Date().getHours()
				if (h < 6) return '凌晨好'
				if (h < 11) return '上午好'
				if (h < 13) return '中午好'
				if (h < 18) return '下午好'
				return '晚上好'
			},
			/** 今日日期文案（含星期） */
			todayText() {
				const d = new Date()
				const week = ['日', '一', '二', '三', '四', '五', '六']
				return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week[d.getDay()]}`
			},
			/** 是否可审核进度（PM / admin） */
			canReview() {
				return canReviewProgress()
			},
			/** 是否可管理客户（PM / admin） */
			canManageCust() {
				return canManageCustomer()
			},
			/** 是否可转移项目（PM / admin） */
			canTransfer() {
				return canTransferProject()
			},
			/**
			 * 当前用户角色（归一化为 admin/pm/engineer/customer）
			 * 优先取 userStore.roles[0]，兼容项目实际角色码 project_manager/user
			 * 与任务约定角色码 pm/customer；未知时按权限兜底
			 */
			currentRole() {
				const roles = userStore.roles || []
				const r = roles[0]
				if (r === 'admin') return 'admin'
				if (r === 'project_manager' || r === 'pm') return 'pm'
				if (r === 'engineer') return 'engineer'
				if (r === 'user' || r === 'customer') return 'customer'
				// 兜底：可审核视为 PM，否则视为工程师
				return this.canReview ? 'pm' : 'engineer'
			},
			/** 当前角色应展示的卡片配置数组（未知角色回退 engineer） */
		currentRoleCards() {
			return ROLE_CARDS[this.currentRole] || ROLE_CARDS.engineer
		},
			/** 卡片取值映射：聚合已加载数据，避免重复请求 */
			roleCardValues() {
				return {
					inProgressProjects: this.stats.inProgress || 0,
					pendingProgress: this.roleData.pendingProgressCount || 0,
					docTotal: this.roleData.docTotal || 0,
					contractAmount: this.formatAmount(this.roleData.contractAmount),
					managedProjects: this.stats.total || 0,
					keyNodes: (this.upcoming || []).length,
					unread: this.unreadCount || 0,
					engineerMyTasks: this.roleData.myTasksTotal != null ? this.roleData.myTasksTotal : (this.summary.myTasks || 0),
					todoTasks: this.roleData.myTasksTodo || 0,
					overdueTasks: this.roleData.myTasksOverdue || 0,
					pendingSubmissions: this.summary.mySubmissions || 0,
					customerMyTasks: this.summary.myTasks || 0
				}
			}
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
		// 页面显示：校验登录态，未登录跳登录页；30 秒内节流避免 tab 切换/详情返回时重复请求
		onShow() {
			// 未登录跳登录页
			if (!userStore.isLoggedIn) {
				uni.reLaunch({ url: '/pages/login/login' })
				return
			}
			// 节流：30 秒内不重复加载（下拉刷新会强制绕过节流）
			const now = Date.now()
			if (this.lastLoadTime && now - this.lastLoadTime < 30000) return
			this.loadAll()
		},
		// 下拉刷新：重新加载并在完成后停止刷新动画
		onPullDownRefresh() {
			this.loadAll(true)
		},
		methods: {
			formatDate,
			/**
			 * 并行加载统计、工作台汇总、未读通知、关键节点、近期项目
			 * @param {boolean} stopPull 是否为下拉刷新触发（完成后停止下拉动画）
			 */
			async loadAll(stopPull) {
			await Promise.allSettled([
				this.loadStats(),
				this.loadSummary(),
				this.loadUnread(),
				this.loadUpcoming(true),
				this.loadRecent(),
				this.loadRoleCards()
			])
				// 记录加载时间戳，用于 onShow 节流
				this.lastLoadTime = Date.now()
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载项目统计概览（兼容后端多种字段命名） */
			async loadStats() {
				try {
					const data = await projectApi.getStatistics()
					if (data) {
						// 后端 ProjectStatisticsVO 字段映射：
						// totalCount -> 项目总数；activeCount -> 进行中；completedCount -> 已完成
						this.stats = {
							total: data.totalCount ?? data.total ?? 0,
							inProgress: data.activeCount ?? data.inProgress ?? 0,
							completed: data.completedCount ?? data.completed ?? 0
						}
					}
				} catch (e) {
					console.warn('stats error', e)
				}
			},
			/** 加载工作台汇总统计（我的任务/填报/待审核/未读通知） */
			async loadSummary() {
				try {
					const data = await dashboardApi.getSummary()
					if (data) {
						// 兼容后端 DashboardSummaryVO 字段：myTaskCount / mySubmissionCount / pendingReviewCount / unreadNotificationCount
						this.summary = {
							myTasks: data.myTaskCount ?? data.myTasks ?? 0,
							mySubmissions: data.mySubmissionCount ?? data.mySubmissions ?? 0,
							pendingReview: data.pendingReviewCount ?? data.pendingReview ?? 0,
							unreadNotifications: data.unreadNotificationCount ?? data.unreadNotifications ?? 0
						}
						// 同步未读通知角标
						this.unreadCount = this.summary.unreadNotifications
					}
				} catch (e) {
					console.warn('summary error', e)
				}
			},
			/** 单独加载未读通知数（轻量调用，便于其他场景刷新角标） */
			async loadUnread() {
				try {
					const count = await notificationApi.getUnreadCount()
					// 后端可能返回数字或 { count: n }
					const n = typeof count === 'number' ? count : (count?.count ?? 0)
					this.unreadCount = n
					this.summary.unreadNotifications = n
				} catch (e) {
					// 静默失败，不影响首页其他模块
				}
			},
			/**
			 * 加载关键节点提醒（最多取前 5 条）
			 * @param {boolean} silent 静默模式（不打印错误日志）
			 */
			async loadUpcoming(silent) {
				if (this.loadingUpcoming) return
				this.loadingUpcoming = true
				try {
					const list = await taskApi.getUpcomingAll()
					this.upcoming = Array.isArray(list) ? list.slice(0, 5) : []
				} catch (e) {
					if (!silent) console.warn('upcoming error', e)
					this.upcoming = []
				} finally {
					this.loadingUpcoming = false
				}
			},
			/** 加载近期项目（兼容 {records}/{list}/数组 多种返回结构） */
			async loadRecent() {
				try {
					const res = await projectApi.getList({ page: 1, size: 5 })
					// 后端可能返回 {records,total} 数组或 {list,total}
					const list = (res && (res.records || res.list || res)) || []
					this.recentProjects = Array.isArray(list) ? list.slice(0, 5) : []
				} catch (e) {
					console.warn('recent error', e)
				}
			},
			/**
			 * 按当前角色并发加载差异化卡片所需数据
			 * 复用已加载的 stats/summary/unreadCount/upcoming，仅请求角色专属数据
			 */
			async loadRoleCards() {
				const role = this.currentRole
				const tasks = []
				if (role === 'admin') {
					tasks.push(this.loadPendingProgress(), this.loadDocTotal(), this.loadOverview())
				} else if (role === 'pm') {
					tasks.push(this.loadPendingProgress())
				} else if (role === 'engineer') {
					tasks.push(this.loadMyTasksFull())
				} else if (role === 'customer') {
					tasks.push(this.loadDocTotal())
				}
				await Promise.allSettled(tasks)
			},
			/** 待审核进度数（admin 待审核进度 / pm 待我审核） */
			async loadPendingProgress() {
				try {
					const list = await taskApi.listPendingProgress()
					this.roleData.pendingProgressCount = Array.isArray(list) ? list.length : 0
				} catch (e) {
					this.roleData.pendingProgressCount = 0
				}
			},
			/** 文档总数（admin / customer） */
			async loadDocTotal() {
				try {
					const res = await documentApi.getList({ page: 1, size: 1 })
					const total = (res && (res.total ?? res.totalCount)) || 0
					this.roleData.docTotal = total
				} catch (e) {
					this.roleData.docTotal = 0
				}
			},
			/** 统计概览，取合同总额（admin） */
			async loadOverview() {
				try {
					const data = await statisticsApi.getOverview()
					this.roleData.contractAmount = (data && (data.totalAmount ?? data.amount)) || 0
				} catch (e) {
					this.roleData.contractAmount = 0
				}
			},
			/**
			 * 加载我的任务全量列表并统计待办/逾期（engineer）
			 * 一次请求复用于 我的任务/待办任务/逾期任务 三张卡片
			 */
			async loadMyTasksFull() {
				try {
					const res = await dashboardApi.getMyTasks({ pageNum: 1, pageSize: 200 }) || {}
					const list = Array.isArray(res.records) ? res.records : []
					const today = new Date()
					let todo = 0, overdue = 0
					list.forEach(t => {
						const progress = Number(t.progress) || 0
						const done = t.status === '已完成' || progress >= 100
						if (!done) todo++
						if (t.endDate && !done && new Date(t.endDate) < today) overdue++
					})
					this.roleData.myTasksTotal = res.total != null ? res.total : list.length
					this.roleData.myTasksTodo = todo
					this.roleData.myTasksOverdue = overdue
				} catch (e) {
					this.roleData.myTasksTotal = this.summary.myTasks || 0
					this.roleData.myTasksTodo = 0
					this.roleData.myTasksOverdue = 0
				}
			},
			/** 金额格式化（亿/万） */
			formatAmount(n) {
				const num = Number(n) || 0
				if (num >= 1e8) return (num / 1e8).toFixed(2) + '亿'
				if (num >= 1e4) return (num / 1e4).toFixed(1) + '万'
				return String(num)
			},
			/** 角色卡片点击：分发到对应跳转/刷新方法 */
			onRoleCardClick(card) {
				if (!card || !card.action) return
				if (card.action === 'goTab' && card.target) {
					this.goTab(card.target)
				} else if (typeof this[card.action] === 'function') {
					this[card.action]()
				}
			},
			// statusText 由 statusMixin 提供
			/** 项目状态标签内联样式（文字色 + 背景色） */
			statusStyle(s) {
				const o = getProjectStatus(s)
				return { color: o.color, background: o.bg }
			},
			/** 跳转 tabBar 页面 */
			goTab(url) {
				uni.switchTab({ url })
			},
			/** 跳转「我的」tab */
			goProfile() {
				uni.switchTab({ url: '/pages/profile/profile' })
			},
			/** 跳转项目详情 */
			goProjectDetail(id) {
				if (!id) return
				uni.navigateTo({ url: `/pages/project/detail?id=${id}` })
			},
			/** 跳转进度填报页 */
			goTaskReport(taskId, projectId) {
				if (!taskId) return
				uni.navigateTo({ url: `/pages/progress/report?taskId=${taskId}&projectId=${projectId || ''}` })
			},
			/** 跳转我的任务页 */
			goMyTasks() {
				uni.navigateTo({ url: '/pages/my/tasks' })
			},
			/** 跳转我的填报页 */
			goMySubmissions() {
				uni.navigateTo({ url: '/pages/my/submissions' })
			},
			/** 跳转工作量填报页（项目组成员每日填报） */
			goWorkloadReport() {
				uni.navigateTo({ url: '/pages/workload/report' })
			},
			/** 跳转工作量审核页（PM） */
			goWorkloadReview() {
				uni.navigateTo({ url: '/pages/workload/review' })
			},
			/** 跳转审批待办中心（PM，聚合四类审核） */
			goApprovalTodo() {
				uni.navigateTo({ url: '/pages/approval/todo' })
			},
			/** 跳转进度审核页（PM） */
			goPendingReview() {
				uni.navigateTo({ url: '/pages/progress/review' })
			},
			/** 跳转风险预警看板页（PM） */
			goRiskBoard() {
				uni.navigateTo({ url: '/pages/pm/risk-board' })
			},
			/** 跳转客户管理页 */
			goCustomer() {
				uni.navigateTo({ url: '/pages/customer/list' })
			},
			/** 跳转项目授权转移页（PM） */
			goProjectAuth() {
				uni.navigateTo({ url: '/pages/pm/project-auth' })
			},
			/** 跳转通知列表页 */
			goNotification() {
				uni.navigateTo({ url: '/pages/notification/list' })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		padding-bottom: 40rpx;
	}

	/* Hero 区 */
	.hero {
		position: relative;
	}
	.hero-bg {
		position: absolute;
		top: 0; left: 0; right: 0;
		height: 360rpx;
		background: linear-gradient(135deg, #1890FF 0%, #096DD9 100%);
		border-bottom-left-radius: 40rpx;
		border-bottom-right-radius: 40rpx;
	}
	.hero-content {
		position: relative;
		z-index: 1;
		padding: 0 32rpx 32rpx;
	}
	.hero-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 32rpx;
	}
	.hero-actions {
		display: flex;
		align-items: center;
		gap: 20rpx;
	}
	/* 通知铃铛 */
	.bell {
		position: relative;
		width: 64rpx;
		height: 64rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.2);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.bell-icon {
		width: 36rpx;
		height: 36rpx;
	}
	.bell-badge {
		position: absolute;
		top: -4rpx;
		right: -4rpx;
		min-width: 32rpx;
		height: 32rpx;
		padding: 0 8rpx;
		border-radius: 16rpx;
		background: #FF4D4F;
		color: #FFFFFF;
		font-size: 20rpx;
		line-height: 32rpx;
		text-align: center;
		border: 2rpx solid #096DD9;
		box-sizing: content-box;
	}
	.greet-hi {
		font-size: 38rpx;
		font-weight: 700;
		color: #FFFFFF;
	}
	.greet-sub {
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.85);
		margin-top: 6rpx;
	}
	.avatar {
		width: 72rpx;
		height: 72rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.25);
		color: #FFFFFF;
		font-size: 32rpx;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
		border: 2rpx solid rgba(255, 255, 255, 0.4);
	}

	.stat-grid {
		background: rgba(255, 255, 255, 0.15);
		border-radius: 20rpx;
		padding: 28rpx 0;
		display: flex;
		align-items: center;
		backdrop-filter: blur(10rpx);
	}
	.stat-item {
		flex: 1;
		text-align: center;
	}
	.stat-num {
		font-size: 44rpx;
		font-weight: 700;
		color: #FFFFFF;
		line-height: 1.2;
	}
	.stat-num.primary { color: #BAE7FF; }
	.stat-num.success { color: #D9F7BE; }
	.stat-label {
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.85);
		margin-top: 6rpx;
	}
	.stat-divider {
		width: 1rpx;
		height: 56rpx;
		background: rgba(255, 255, 255, 0.3);
	}

	/* 工作台汇总卡片 */
	.summary-card {
		margin: -36rpx 24rpx 0;
		background: #FFFFFF;
		border-radius: 20rpx;
		padding: 32rpx 0;
		display: flex;
		align-items: center;
		box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
		position: relative;
		z-index: 2;
	}
	.summary-item {
		flex: 1;
		text-align: center;
	}
	.summary-num {
		font-size: 40rpx;
		font-weight: 700;
		color: #303133;
		line-height: 1.2;
	}
	.summary-num.warning { color: #D48806; }
	.summary-num.danger { color: #D4380D; }
	.summary-num.primary { color: #1890FF; }
	.summary-num.success { color: #52C41A; }
	.summary-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}
	.summary-divider {
		width: 1rpx;
		height: 56rpx;
		background: #F0F2F5;
	}

	/* 快捷入口 */
	.quick-card {
		margin: 20rpx 24rpx 0;
		background: #FFFFFF;
		border-radius: 20rpx;
		padding: 28rpx 0 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
		position: relative;
		z-index: 2;
	}
	.quick-row {
		display: flex;
	}
	.quick-row-gap {
		margin-top: 24rpx;
	}
	.quick-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.quick-icon {
		width: 84rpx;
		height: 84rpx;
		border-radius: 22rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 12rpx;
	}
	.icon-blue { background: #E6F7FF; }
	.icon-purple { background: #F9F0FF; }
	.icon-orange { background: #FFF7E6; }
	.icon-green { background: #F6FFED; }
	.icon-cyan { background: #E6FFFB; }
	.icon-red { background: #FFF1F0; }
	.icon-yellow { background: #FFFBE6; }
	.icon-green2 { background: #F6FFED; }
	.icon-indigo { background: #F0F5FF; }
	.svg-icon {
		width: 44rpx;
		height: 44rpx;
	}
	.svg-icon-lg {
		width: 80rpx;
		height: 80rpx;
	}
	.quick-text {
		font-size: 24rpx;
		color: #606266;
	}

	/* 区块 */
	.section {
		padding: 28rpx 24rpx 0;
	}
	.section-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
		padding: 0 4rpx;
	}
	.section-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
	}
	.section-more {
		font-size: 24rpx;
		color: #096DD9;
	}

	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 8rpx 24rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	.skeleton-line {
		position: relative;
		height: 80rpx;
		margin: 16rpx 0;
		background: #F2F4F7;
		border-radius: 8rpx;
		overflow: hidden;
	}
	/* GPU 加速：通过 ::after 叠加渐变光带并平移，避免 background-position 触发重绘 */
	.skeleton-line::after {
		content: '';
		position: absolute;
		top: 0;
		left: -150%;
		width: 150%;
		height: 100%;
		background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.6) 50%, transparent 100%);
		animation: sk 1.4s ease-in-out infinite;
		will-change: transform;
	}
	@keyframes sk {
		0%   { transform: translateX(0); }
		100% { transform: translateX(266%); }
	}

	/* 关键节点 */
	.node-item {
		display: flex;
		align-items: center;
		padding: 24rpx 0;
		border-bottom: 1rpx solid #F0F2F5;
	}
	.node-item.no-border { border-bottom: none; }
	.node-dot {
		width: 16rpx;
		height: 16rpx;
		border-radius: 50%;
		background: #1890FF;
		margin-right: 20rpx;
		flex-shrink: 0;
	}
	.node-dot.milestone {
		background: #FAAD14;
		box-shadow: 0 0 0 6rpx rgba(250, 173, 20, 0.18);
	}
	.node-body {
		flex: 1;
		min-width: 0;
	}
	.node-title {
		font-size: 28rpx;
		color: #303133;
		font-weight: 500;
		display: flex;
		align-items: center;
		overflow: hidden;
	}
	.node-name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		flex: 1;
		min-width: 0;
	}
	.tag {
		margin-left: 12rpx;
		padding: 2rpx 12rpx;
		border-radius: 6rpx;
		font-size: 20rpx;
		flex-shrink: 0;
	}
	.tag-milestone { background: #FFF7E6; color: #D48806; }
	.tag-key { background: #FFF1F0; color: #D4380D; }
	.node-meta {
		display: flex;
		justify-content: space-between;
		margin-top: 8rpx;
		font-size: 22rpx;
		color: #5C6370;
	}
	.node-progress {
		font-size: 26rpx;
		color: #096DD9;
		font-weight: 600;
		margin-left: 16rpx;
	}

	/* 项目项 */
	.project-item {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 24rpx 0;
		border-bottom: 1rpx solid #F0F2F5;
	}
	.project-item.no-border { border-bottom: none; }
	.proj-main {
		flex: 1;
		min-width: 0;
		margin-right: 20rpx;
	}
	.proj-title {
		font-size: 28rpx;
		color: #303133;
		font-weight: 500;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.proj-sub {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}
	.proj-right {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 8rpx;
	}
	.proj-progress {
		display: flex;
		align-items: center;
	}
	.progress-bar {
		width: 100rpx;
		height: 8rpx;
		background: #EBEEF5;
		border-radius: 4rpx;
		overflow: hidden;
		margin-right: 10rpx;
	}
	.progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 4rpx;
	}
	.progress-text {
		font-size: 22rpx;
		color: #606266;
	}

	.empty {
		padding: 80rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	.empty-icon {
		font-size: 80rpx;
		margin-bottom: 16rpx;
		display: flex;
		justify-content: center;
		align-items: center;
	}

	.bottom-safe {
		height: calc(40rpx + env(safe-area-inset-bottom));
	}
</style>
