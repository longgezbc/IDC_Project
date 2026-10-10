<!--
  项目详情页
  业务用途：展示单个项目的头部信息（状态/优先级/整体进度）、基本信息、项目描述、
           任务进度（前 5 条）与项目资料（前 5 条）。
  数据来源：projectApi.getDetail（详情）、taskApi.getList（任务）、
           projectApi.getDocumentsByProject（文档）。
  导航特性：非 tabBar 页，通过 url 参数 id 接收项目ID；可跳转任务列表/进度填报/文档预览。
-->
<template>
	<view class="page">
		<!-- 项目头部 -->
		<view class="hero">
			<view class="hero-bg"></view>
			<view class="hero-content">
				<view class="proj-name">{{ project.name || '加载中...' }}</view>
				<view class="proj-code">编号：{{ project.code || '-' }}</view>
				<view class="proj-status-row">
					<view class="tag" :style="statusStyle(project.status)">{{ statusText(project.status) }}</view>
					<view class="tag" :style="priorityStyle(project.priority)">{{ priorityText(project.priority) }}</view>
					<view v-if="project.archived" class="tag tag-archived">已归档</view>
				</view>

				<!-- 操作按钮组：编辑 + 甘特图 -->
				<view v-if="projectId" class="proj-actions">
					<view class="proj-btn" role="button" hover-class="uni-hover" :hover-stay-time="80" @click="goEdit">编辑</view>
					<view class="proj-btn" role="button" hover-class="uni-hover" :hover-stay-time="80" @click="goGantt">甘特图</view>
				</view>

				<!-- 进度环 -->
				<view class="progress-block">
					<view class="progress-ring">
						<view class="ring-num">{{ project.progress || 0 }}<text class="ring-unit">%</text></view>
						<view class="ring-label">整体进度</view>
					</view>
					<view class="progress-meta">
						<view class="meta-item">
							<view class="meta-label">开始日期</view>
							<view class="meta-val">{{ formatDate(project.startDate) || '-' }}</view>
						</view>
						<view class="meta-item">
							<view class="meta-label">结束日期</view>
							<view class="meta-val">{{ formatDate(project.endDate) || '-' }}</view>
						</view>
					</view>
				</view>
			</view>
		</view>

		<!-- 基本信息卡 -->
		<view class="card">
			<view class="card-title">基本信息</view>
			<view class="info-row">
				<text class="info-label">负责人</text>
				<text class="info-val">{{ project.manager || '-' }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">联系电话</text>
				<text class="info-val">{{ project.managerPhone || '-' }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">项目地址</text>
				<text class="info-val">{{ project.address || '-' }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">合同金额</text>
				<text class="info-val">{{ project.contractAmount ? ('¥' + project.contractAmount) : '-' }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">客户名称</text>
				<text class="info-val">{{ project.customerName || '-' }}</text>
			</view>
		</view>

		<!-- 项目描述 -->
		<view v-if="project.description" class="card">
			<view class="card-title">项目描述</view>
			<view class="desc-text">{{ project.description }}</view>
		</view>

		<!-- 任务进度 -->
		<view class="card">
			<view class="card-title-row">
				<text class="card-title">任务进度</text>
			<text class="card-more" role="link" @click="goTaskList">全部任务</text>
			</view>
			<view v-if="tasks.length === 0" class="empty-mini">暂无任务</view>
			<view v-else>
				<view
			v-for="(t, idx) in tasks.slice(0, 5)"
			:key="t.id"
			class="task-item"
			:class="{ 'no-border': idx === Math.min(tasks.length, 5) - 1 }"
			hover-class="uni-hover"
			:hover-stay-time="80"
			role="link"
			@click="goReport(t)"
		>
					<view class="task-left">
						<view class="task-dot" :class="{ milestone: t.isMilestone, key: t.isKey && !t.isMilestone }"></view>
						<view class="task-body">
							<view class="task-name">
								<text>{{ t.name }}</text>
								<text v-if="t.isMilestone" class="mini-tag tag-milestone">里程碑</text>
								<text v-else-if="t.isKey" class="mini-tag tag-key">关键</text>
							</view>
							<view class="task-meta">{{ formatDate(t.startDate) }} ~ {{ formatDate(t.endDate) }}</view>
						</view>
					</view>
					<view class="task-right">
						<view class="task-progress-text">{{ t.progress || 0 }}%</view>
						<view class="task-action">填报</view>
					</view>
				</view>
			</view>
		</view>

		<!-- 项目资料 -->
		<view class="card">
			<view class="card-title-row">
				<text class="card-title">项目资料</text>
			<text class="card-more" role="link" @click="goProjectDocs">查看全部</text>
			</view>
			<view v-if="docs.length === 0" class="empty-mini">暂无资料</view>
			<view v-else>
				<view
			v-for="(d, idx) in docs.slice(0, 5)"
			:key="d.id"
			class="doc-item"
			:class="{ 'no-border': idx === Math.min(docs.length, 5) - 1 }"
			hover-class="uni-hover"
			:hover-stay-time="80"
			role="link"
			@click="goPreview(d)"
		>
					<view class="doc-icon" :style="{ background: fileColor(d) }">{{ fileExt(d) }}</view>
					<view class="doc-body">
						<view class="doc-name">{{ d.name }}</view>
						<view class="doc-meta">{{ d.size || '-' }} · {{ formatDate(d.uploadDate) }}</view>
					</view>
					<text class="doc-arrow">›</text>
				</view>
			</view>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { projectApi } from '@/api/project'
	import { taskApi } from '@/api/task'
	import { formatDate, getProjectStatus, getPriority, getFileTypeColor, getExt } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				projectId: '',   // 项目ID（来自路由参数）
				project: {},     // 项目详情
				tasks: [],       // 任务列表
				docs: []         // 项目文档列表
			}
		},
		// 页面加载：接收路由参数 id 并加载全部数据
		onLoad(opts) {
			this.projectId = opts.id
			this.loadAll()
		},
		// 页面显示：从填报页返回时刷新任务进度
		onShow() {
			if (this.projectId) this.loadTasks()
		},
		methods: {
			formatDate,
			// statusText 由 statusMixin 提供
			/** 项目状态标签内联样式 */
			statusStyle(s) {
				const o = getProjectStatus(s)
				return { color: o.color, background: o.bg }
			},
			/** 优先级文本 */
			priorityText(p) { return getPriority(p).text },
			/** 优先级标签内联样式（白字 + 优先级色背景） */
			priorityStyle(p) {
				const o = getPriority(p)
				return { color: '#FFFFFF', background: o.color }
			},
			/** 文档图标显示的扩展名文本 */
			fileExt(d) {
				return getExt(d.name || d.fileType) || (d.fileType || 'FILE').substring(0, 3).toUpperCase()
			},
			/** 文档图标背景色（按文件类型区分） */
			fileColor(d) {
				return getFileTypeColor(getExt(d.name || d.fileType) || d.fileType)
			},
			/** 并行加载详情、任务与文档（带全局 loading） */
			async loadAll() {
				uni.showLoading({ title: '加载中', mask: true })
				await Promise.allSettled([
					this.loadDetail(),
					this.loadTasks(),
					this.loadDocs()
				])
				uni.hideLoading()
			},
			/** 加载项目详情 */
			async loadDetail() {
				try {
					this.project = await projectApi.getDetail(this.projectId)
				} catch (e) {
					console.warn('detail error', e)
				}
			},
			/** 加载任务列表（兼容多种返回结构） */
			async loadTasks() {
				try {
					const res = await taskApi.getList(this.projectId)
					const list = (res && (res.records || res.list || res)) || []
					this.tasks = Array.isArray(list) ? list : []
				} catch (e) {
					console.warn('tasks error', e)
					this.tasks = []
				}
			},
			/** 加载项目文档列表 */
			async loadDocs() {
				try {
					const res = await projectApi.getDocumentsByProject(this.projectId)
					const list = (res && (res.records || res.list || res)) || []
					this.docs = Array.isArray(list) ? list : []
				} catch (e) {
					console.warn('docs error', e)
					this.docs = []
				}
			},
			/** 跳转任务列表页 */
		goTaskList() {
			uni.navigateTo({ url: `/pages/progress/list?projectId=${this.projectId}` })
		},
		/** 跳转项目编辑页 */
		goEdit() {
			uni.navigateTo({ url: `/pages/project/edit?id=${this.projectId}` })
		},
		/** 跳转项目甘特图页 */
		goGantt() {
			const name = encodeURIComponent(this.project.name || '')
			uni.navigateTo({ url: `/pages/progress/gantt?projectId=${this.projectId}&projectName=${name}` })
		},
			/** 跳转进度填报页 */
			goReport(task) {
				uni.navigateTo({ url: `/pages/progress/report?taskId=${task.id}&projectId=${this.projectId}` })
			},
			/** 跳转资料 tab */
			goProjectDocs() {
				uni.switchTab({ url: '/pages/document/list' })
			},
			/** 跳转文档预览页 */
			goPreview(doc) {
				uni.navigateTo({ url: `/pages/document/preview?id=${doc.id}` })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page { min-height: 100vh; padding-bottom: 40rpx; }

	.hero {
		position: relative;
		margin-bottom: 20rpx;
	}
	.hero-bg {
		position: absolute;
		top: 0; left: 0; right: 0;
		height: 340rpx;
		background: linear-gradient(135deg, #1890FF 0%, #096DD9 100%);
		border-bottom-left-radius: 40rpx;
		border-bottom-right-radius: 40rpx;
	}
	.hero-content {
		position: relative;
		z-index: 1;
		padding: 40rpx 32rpx 32rpx;
		color: #FFFFFF;
	}
	/* 操作按钮组：右上角悬浮，玻璃拟态风格 */
	.proj-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 20rpx;
		margin-bottom: 8rpx;
	}
	.proj-btn {
		flex: 1;
		max-width: 200rpx;
		text-align: center;
		padding: 14rpx 0;
		font-size: 26rpx;
		font-weight: 500;
		color: #FFFFFF;
		background: rgba(255, 255, 255, 0.18);
		border: 1rpx solid rgba(255, 255, 255, 0.35);
		border-radius: 32rpx;
		backdrop-filter: blur(8rpx);
	}
	.proj-btn:first-child {
		background: rgba(255, 255, 255, 0.95);
		color: #096DD9;
		border-color: transparent;
		box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);
	}
	.proj-name {
		font-size: 38rpx;
		font-weight: 700;
		margin-bottom: 8rpx;
		line-height: 1.3;
	}
	.proj-code {
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.85);
		margin-bottom: 16rpx;
	}
	.proj-status-row {
		display: flex;
		gap: 12rpx;
		margin-bottom: 32rpx;
	}
	.tag {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		background: rgba(255, 255, 255, 0.25);
		color: #FFFFFF;
	}
	.tag-archived { background: rgba(0, 0, 0, 0.25); }

	.progress-block {
		display: flex;
		align-items: center;
		background: rgba(255, 255, 255, 0.15);
		border-radius: 20rpx;
		padding: 24rpx;
	}
	.progress-ring {
		width: 160rpx;
		height: 160rpx;
		border-radius: 50%;
		background: conic-gradient(#FFFFFF 0deg, #FFFFFF calc(var(--p, 0) * 3.6deg), rgba(255,255,255,0.2) calc(var(--p, 0) * 3.6deg));
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		position: relative;
		margin-right: 24rpx;
	}
	.progress-ring::before {
		content: '';
		position: absolute;
		width: 124rpx;
		height: 124rpx;
		border-radius: 50%;
		background: rgba(24, 144, 255, 0.85);
	}
	.ring-num {
		position: relative;
		font-size: 44rpx;
		font-weight: 700;
		color: #FFFFFF;
		line-height: 1;
	}
	.ring-unit {
		font-size: 24rpx;
		margin-left: 2rpx;
	}
	.ring-label {
		position: relative;
		font-size: 20rpx;
		color: rgba(255, 255, 255, 0.9);
		margin-top: 6rpx;
	}
	.progress-meta {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 16rpx;
	}
	.meta-item {}
	.meta-label {
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.75);
		margin-bottom: 4rpx;
	}
	.meta-val {
		font-size: 28rpx;
		color: #FFFFFF;
		font-weight: 500;
	}

	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin: 0 24rpx 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.card-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 16rpx;
	}
	.card-title-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.card-more {
		font-size: 24rpx;
		color: #096DD9;
	}
	.info-row {
		display: flex;
		justify-content: space-between;
		padding: 12rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.info-row:last-child { border-bottom: none; }
	.info-label {
		font-size: 26rpx;
		color: #5C6370;
	}
	.info-val {
		font-size: 26rpx;
		color: #303133;
		text-align: right;
		flex: 1;
		margin-left: 24rpx;
	}
	.desc-text {
		font-size: 26rpx;
		color: #606266;
		line-height: 1.7;
	}

	.task-item {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.task-item.no-border { border-bottom: none; }
	.task-left {
		display: flex;
		align-items: center;
		flex: 1;
		min-width: 0;
	}
	.task-dot {
		width: 14rpx;
		height: 14rpx;
		border-radius: 50%;
		background: #1890FF;
		margin-right: 16rpx;
		flex-shrink: 0;
	}
	.task-dot.milestone { background: #FAAD14; }
	.task-dot.key { background: #FF4D4F; }
	.task-body { flex: 1; min-width: 0; }
	.task-name {
		font-size: 28rpx;
		color: #303133;
		display: flex;
		align-items: center;
	}
	.mini-tag {
		margin-left: 10rpx;
		padding: 2rpx 10rpx;
		border-radius: 4rpx;
		font-size: 18rpx;
	}
	.tag-milestone { background: #FFF7E6; color: #D48806; }
	.tag-key { background: #FFF1F0; color: #D4380D; }
	.task-meta {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 6rpx;
	}
	.task-right {
		display: flex;
		align-items: center;
		gap: 16rpx;
	}
	.task-progress-text {
		font-size: 24rpx;
		color: #096DD9;
		font-weight: 600;
	}
	.task-action {
		font-size: 22rpx;
		color: #FFFFFF;
		background: #1890FF;
		padding: 6rpx 18rpx;
		border-radius: 20rpx;
	}

	.doc-item {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.doc-item.no-border { border-bottom: none; }
	.doc-icon {
		width: 72rpx;
		height: 72rpx;
		border-radius: 12rpx;
		color: #FFFFFF;
		font-size: 22rpx;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 20rpx;
		flex-shrink: 0;
	}
	.doc-body { flex: 1; min-width: 0; }
	.doc-name {
		font-size: 28rpx;
		color: #303133;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.doc-meta {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 6rpx;
	}
	.doc-arrow {
		font-size: 36rpx;
		color: #767E8A;
		margin-left: 16rpx;
	}

	.empty-mini {
		padding: 40rpx 0;
		text-align: center;
		font-size: 24rpx;
		color: #767E8A;
	}
	.bottom-safe { height: calc(40rpx + env(safe-area-inset-bottom)); }
</style>
