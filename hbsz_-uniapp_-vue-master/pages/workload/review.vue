<!--
  工作量审核页（PM 视角）
  业务用途：PM 选择项目后集中审核成员提交的每日工作量填报，可「通过」或「驳回」（驳回需填写原因）。
           审核通过后数据计入清单完成量与人员绩效；驳回后成员可修改重新提交。
  CAS 语义：仅待审核（pending）记录可审核；单条审核复用批量接口（ids 传单条）。
  数据来源：workloadApi.listPendingRecords（待审列表，按状态筛选）；
           workloadApi.reviewRecords（批量审核提交）。
  导航特性：非 tabBar 页；支持下拉刷新；列表为空时展示空态；仅 PM/admin 可访问。
-->
<template>
	<view class="page">
		<!-- 头部：标题 + 待审核数量徽标 -->
		<view class="header">
			<text class="header-title">工作量审核</text>
			<view class="header-badge" v-if="list.length">{{ list.length }}</view>
		</view>

		<!-- 项目筛选 -->
		<view class="card filter-card">
			<view class="filter-row">
				<text class="filter-label">项目</text>
				<picker class="filter-picker" :range="projectNames" @change="onProjectChange">
					<view class="picker-value">
						<text :class="['picker-text', !selectedProjectId && 'picker-placeholder']">
							{{ selectedProjectName || '请选择项目' }}
						</text>
						<text class="picker-arrow">▾</text>
					</view>
				</picker>
			</view>
			<view class="filter-row">
				<text class="filter-label">状态</text>
				<view class="status-tabs">
					<text
						v-for="opt in statusOptions"
						:key="opt.value"
						:class="['status-tab', reviewStatus === opt.value && 'status-tab-active']"
						@click="onStatusChange(opt.value)"
					>{{ opt.label }}</text>
				</view>
			</view>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && list.length === 0">
			<view class="card" v-for="i in 3" :key="'sk' + i">
				<view class="sk-line sk-w240"></view>
				<view class="sk-line sk-w160"></view>
				<view class="sk-line sk-w-full"></view>
				<view class="sk-line sk-w80"></view>
			</view>
		</view>

		<!-- 填报记录列表 -->
		<template v-else>
			<view
				v-for="rec in list"
				:key="rec.id"
				class="card record-card"
			>
				<!-- 人员信息 -->
				<view class="worker-row">
					<view class="avatar">{{ initialOf(rec.workerName) }}</view>
					<view class="worker-info">
						<text class="worker-name">{{ rec.workerName || '未知' }}</text>
						<text class="worker-time">{{ rec.recordDate || '-' }} 填报</text>
					</view>
					<!-- 审核状态标签（非待审列表展示） -->
					<view v-if="rec.reviewStatus && rec.reviewStatus !== 'pending'" class="status-tag">
						<text :style="reviewStatusStyle(rec.reviewStatus)">{{ reviewStatusText(rec.reviewStatus) }}</text>
					</view>
				</view>

				<!-- 作业信息 -->
				<view class="task-block">
					<view class="task-line">
						<text class="line-label">类别</text>
						<text class="line-value">{{ rec.category || '-' }}</text>
					</view>
					<view class="task-line">
						<text class="line-label">作业</text>
						<text class="line-value">{{ rec.workScope || '-' }}</text>
					</view>
				</view>

				<!-- 数量 -->
				<view class="qty-block">
					<text class="block-label">当日完成</text>
					<view class="qty-value">
						<text class="qty-num">{{ rec.quantity || 0 }}</text>
						<text class="qty-unit">{{ rec.unit || '' }}</text>
					</view>
				</view>

				<!-- 审核意见（已审核记录展示） -->
				<view class="remark-block" v-if="rec.reviewRemark">
					<text class="block-label">审核意见</text>
					<view class="remark-text">{{ rec.reviewRemark }}</view>
				</view>

				<!-- 操作按钮：仅待审核记录可操作 -->
				<view class="action-row" v-if="reviewStatus === 'pending'">
					<button class="action-btn btn-reject" :disabled="reviewing" @click="openReject(rec)">驳回</button>
					<button class="action-btn btn-approve" :disabled="reviewing" @click="confirmApprove(rec)">通过</button>
				</view>
			</view>

			<!-- 空态 -->
			<view v-if="list.length === 0" class="empty">
				<view class="empty-icon" aria-hidden="true">
					<image class="svg-icon-lg" src="/static/icon/审核.svg" mode="aspectFit" />
				</view>
				<text>{{ selectedProjectId ? '暂无相关填报记录' : '请先选择项目' }}</text>
			</view>
		</template>

		<!-- 驳回弹窗 -->
		<view v-if="rejectVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="rejectVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">驳回原因</view>
				<textarea v-model="rejectRemark" placeholder="请输入驳回原因..." maxlength="200" aria-label="驳回原因" />
				<view class="popup-actions">
					<button @click="rejectVisible=false">取消</button>
					<button @click="confirmReject">确定</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { projectApi } from '@/api/project'
	import { workloadApi } from '@/api/workload'
	import { statusMixin } from '@/utils/statusMixin'
	import { canReviewWorkload } from '@/utils/permission'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				navBackTimer: null,   // 延迟返回定时器（onUnload 清理）
				projectList: [],      // 可选项目列表
				selectedProjectId: null, // 当前选中项目ID
				selectedProjectName: '', // 当前选中项目名
				reviewStatus: 'pending', // 审核状态筛选：pending/approved/rejected
				statusOptions: [
					{ label: '待审核', value: 'pending' },
					{ label: '已通过', value: 'approved' },
					{ label: '已驳回', value: 'rejected' }
				],
				list: [],             // 填报记录列表
				loading: false,       // 列表加载中
				hasPermission: true,  // 当前用户是否有审核权限
				rejectVisible: false, // 驳回弹窗显隐
				rejectRemark: '',     // 驳回原因
				currentRec: null,     // 当前操作的记录对象
				reviewing: false      // 审核提交中（防重复点击）
			}
		},
		computed: {
			/** 项目下拉展示名列表 */
			projectNames() {
				return this.projectList.map((p) => p.name || p.projectName || `项目${p.id}`)
			}
		},
		// 页面加载：权限校验
		onLoad() {
			this.hasPermission = canReviewWorkload()
			if (!this.hasPermission) {
				uni.showToast({ title: '无权限访问', icon: 'none' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
				return
			}
			this.loadProjects()
		},
		// 页面显示：有项目选择时刷新列表
		onShow() {
			if (this.hasPermission && this.selectedProjectId) this.loadList()
		},
		// 下拉刷新
		onPullDownRefresh() {
			if (!this.hasPermission || !this.selectedProjectId) {
				uni.stopPullDownRefresh()
				return
			}
			this.loadList(true)
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			/** 加载项目下拉数据 */
			async loadProjects() {
				try {
					const res = await projectApi.getList({ pageNum: 1, pageSize: 100 })
					let list = []
					if (Array.isArray(res)) list = res
					else if (res && Array.isArray(res.records)) list = res.records
					else if (res && Array.isArray(res.list)) list = res.list
					this.projectList = list
				} catch (e) {
					console.warn('load projects error', e)
					this.projectList = []
				}
			},
			/** 项目切换：加载该项目的填报记录 */
			onProjectChange(e) {
				const idx = Number(e.detail.value)
				const project = this.projectList[idx]
				if (!project) return
				this.selectedProjectId = project.id
				this.selectedProjectName = project.name || project.projectName || ''
				this.loadList()
			},
			/** 状态筛选切换 */
			onStatusChange(status) {
				if (this.reviewStatus === status) return
				this.reviewStatus = status
				if (this.selectedProjectId) this.loadList()
			},
			/**
			 * 加载填报记录列表
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async loadList(stopPull) {
				if (!this.selectedProjectId) return
				this.loading = true
				try {
					const res = await workloadApi.listPendingRecords({
						projectId: this.selectedProjectId,
						reviewStatus: this.reviewStatus
					})
					this.list = Array.isArray(res) ? res : []
				} catch (e) {
					console.warn('load records error', e)
					this.list = []
				} finally {
					this.loading = false
					if (stopPull) uni.stopPullDownRefresh()
				}
			},
			/** 取填报人姓名首字符作为头像展示 */
			initialOf(name) {
				if (!name) return '?'
				return String(name).charAt(0).toUpperCase()
			},
			/** 打开驳回弹窗：重置原因并记录当前记录 */
			openReject(rec) {
				this.currentRec = rec
				this.rejectRemark = ''
				this.rejectVisible = true
			},
			/** 确认驳回：校验原因后提交（单条复用批量审核接口） */
			async confirmReject() {
				if (this.reviewing) return
				const remark = (this.rejectRemark || '').trim()
				if (!remark) {
					return uni.showToast({ title: '请输入驳回原因', icon: 'none' })
				}
				const rec = this.currentRec
				if (!rec) return
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await workloadApi.reviewRecords({ ids: [rec.id], approve: false, remark })
					this.removeRec(rec.id)
					this.rejectVisible = false
					uni.showToast({ title: '已驳回', icon: 'success' })
				} catch (e) {
					console.error('reject error', e)
				} finally {
					this.reviewing = false
					uni.hideLoading()
				}
			},
			/** 通过：弹简单确认框后提交 */
			confirmApprove(rec) {
				if (this.reviewing) return
				uni.showModal({
					title: '确认审核',
					content: `确定通过「${rec.workScope || '该项作业'} ${rec.quantity || 0}${rec.unit || ''}」吗？`,
					confirmText: '通过',
					confirmColor: '#52C41A',
					success: (res) => {
						if (res.confirm) this.doApprove(rec)
					}
				})
			},
			/** 提交通过（单条复用批量审核接口） */
			async doApprove(rec) {
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await workloadApi.reviewRecords({ ids: [rec.id], approve: true })
					this.removeRec(rec.id)
					uni.showToast({ title: '已通过', icon: 'success' })
				} catch (e) {
					console.error('approve error', e)
				} finally {
					this.reviewing = false
					uni.hideLoading()
				}
			},
			/**
			 * 审核完成后从列表移除该记录（即时反馈）
			 * @param {string|number} recId 记录ID
			 */
			removeRec(recId) {
				const idx = this.list.findIndex((l) => l.id === recId)
				if (idx > -1) this.list.splice(idx, 1)
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

	/* 头部 */
	.header {
		display: flex;
		align-items: center;
		padding: 12rpx 4rpx 20rpx;
	}
	.header-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
	}
	.header-badge {
		min-width: 40rpx;
		height: 40rpx;
		padding: 0 12rpx;
		margin-left: 16rpx;
		border-radius: 20rpx;
		background: #FF4D4F;
		color: #FFFFFF;
		font-size: 22rpx;
		line-height: 40rpx;
		text-align: center;
	}

	/* 通用卡片 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	/* 筛选卡片 */
	.filter-row {
		display: flex;
		align-items: center;
	}
	.filter-row + .filter-row {
		margin-top: 20rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid #F5F7FA;
	}
	.filter-label {
		width: 90rpx;
		font-size: 26rpx;
		color: #5C6370;
		flex-shrink: 0;
	}
	.filter-picker {
		flex: 1;
	}
	.picker-value {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 64rpx;
		padding: 0 20rpx;
		background: #F5F7FA;
		border-radius: 10rpx;
	}
	.picker-text {
		font-size: 26rpx;
		color: #303133;
		max-width: 440rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.picker-placeholder {
		color: #909399;
	}
	.picker-arrow {
		font-size: 22rpx;
		color: #909399;
	}

	/* 状态筛选 tabs */
	.status-tabs {
		flex: 1;
		display: flex;
		background: #F5F7FA;
		border-radius: 10rpx;
		padding: 6rpx;
	}
	.status-tab {
		flex: 1;
		height: 52rpx;
		line-height: 52rpx;
		text-align: center;
		font-size: 24rpx;
		color: #5C6370;
		border-radius: 8rpx;
	}
	.status-tab-active {
		background: #FFFFFF;
		color: #1890FF;
		font-weight: 600;
		box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.08);
	}

	/* 骨架屏 */
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
	.sk-w80 { width: 80rpx; }
	.sk-w-full { width: 100%; }
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}

	/* 填报人 */
	.worker-row {
		display: flex;
		align-items: center;
		padding-bottom: 20rpx;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.avatar {
		width: 64rpx;
		height: 64rpx;
		border-radius: 50%;
		background: #E6F7FF;
		color: #096DD9;
		font-size: 30rpx;
		font-weight: 600;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}
	.worker-info {
		margin-left: 16rpx;
		display: flex;
		flex-direction: column;
		flex: 1;
	}
	.worker-name {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
	}
	.worker-time {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 4rpx;
	}
	.status-tag {
		flex-shrink: 0;
	}
	.status-tag text {
		display: inline-block;
		font-size: 22rpx;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
	}

	/* 作业信息 */
	.task-block {
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.task-line {
		display: flex;
		align-items: center;
		margin-bottom: 12rpx;
	}
	.task-line:last-child { margin-bottom: 0; }
	.line-label {
		width: 80rpx;
		font-size: 24rpx;
		color: #5C6370;
		flex-shrink: 0;
	}
	.line-value {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
		font-weight: 500;
	}

	/* 区块通用标签 */
	.block-label {
		font-size: 24rpx;
		color: #5C6370;
	}

	/* 数量 */
	.qty-block {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 20rpx 0;
	}
	.qty-value {
		display: flex;
		align-items: baseline;
		gap: 8rpx;
	}
	.qty-num {
		font-size: 34rpx;
		font-weight: 700;
		color: #096DD9;
	}
	.qty-unit {
		font-size: 22rpx;
		color: #909399;
	}

	/* 审核意见 */
	.remark-block {
		padding: 0 0 4rpx;
	}
	.remark-block .block-label {
		display: block;
		margin-bottom: 12rpx;
	}
	.remark-text {
		padding: 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #606266;
		line-height: 1.6;
	}

	/* 操作按钮 */
	.action-row {
		display: flex;
		gap: 20rpx;
		margin-top: 24rpx;
	}
	.action-btn {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		font-weight: 600;
		border-radius: 12rpx;
		border: none;
		margin: 0;
		padding: 0;
		&::after { border: none; }
		&[disabled] {
			opacity: 0.6;
		}
	}
	.btn-reject {
		color: #D4380D;
		background: #FFF1F0;
		border: 2rpx solid #FF4D4F;
	}
	.btn-approve {
		color: #FFFFFF;
		background: #52C41A;
	}

	/* 空态 */
	.empty {
		padding: 160rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	.empty-icon {
		display: flex;
		justify-content: center;
		align-items: center;
		margin-bottom: 24rpx;
	}
	.svg-icon-lg {
		width: 96rpx;
		height: 96rpx;
	}

	/* 驳回弹窗 */
	.popup-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 1000;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.popup {
		width: 600rpx;
		background: #FFFFFF;
		border-radius: 24rpx;
		padding: 32rpx;
	}
	.popup-title {
		font-size: 32rpx;
		font-weight: 600;
		color: #303133;
		text-align: center;
		margin-bottom: 24rpx;
	}
	.popup textarea {
		width: 100%;
		height: 200rpx;
		padding: 20rpx;
		background: #F5F7FA;
		border-radius: 12rpx;
		font-size: 26rpx;
		color: #303133;
		line-height: 1.6;
		box-sizing: border-box;
	}
	.popup-actions {
		display: flex;
		gap: 20rpx;
		margin-top: 28rpx;
	}
	.popup-actions button {
		flex: 1;
		height: 76rpx;
		line-height: 76rpx;
		font-size: 28rpx;
		border-radius: 12rpx;
		border: none;
		margin: 0;
		padding: 0;
		&::after { border: none; }
	}
	.popup-actions button:first-child {
		color: #606266;
		background: #F5F7FA;
	}
	.popup-actions button:last-child {
		color: #FFFFFF;
		background: #FF4D4F;
	}
</style>
