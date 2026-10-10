<!--
  进度审核页（PM 视角）
  业务用途：PM 集中审核工程师提交的任务进度填报，可「通过」或「驳回」（驳回需填写原因）。
           审核结果通过 taskApi.reviewProgress 提交，通过/驳回后该条进度日志即时从待审核列表移除。
  数据来源：taskApi.listPendingProgress（待审核列表）；taskApi.reviewProgress（提交审核结果）。
  导航特性：非 tabBar 页；支持下拉刷新；列表为空时展示空态。
-->
<template>
	<view class="page">
		<!-- 头部：标题 + 待审核数量徽标 -->
		<view class="header">
			<text class="header-title">进度审核</text>
			<view class="header-badge" v-if="list.length">{{ list.length }}</view>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && list.length === 0">
			<view class="card skeleton-card" v-for="i in 3" :key="'sk' + i">
				<view class="sk-line sk-w240"></view>
				<view class="sk-line sk-w160"></view>
				<view class="sk-line sk-w-full"></view>
				<view class="sk-line sk-w80"></view>
			</view>
		</view>

		<!-- 待审核列表 -->
		<template v-else>
			<view
				v-for="log in list"
				:key="log.id"
				class="card review-card"
			>
				<!-- 汇报人信息 -->
				<view class="reporter-row">
					<view class="avatar">{{ initialOf(log.reporterName) }}</view>
					<view class="reporter-info">
						<text class="reporter-name">{{ log.reporterName || '未知' }}</text>
						<text class="reporter-time">{{ formatDateTime(log.createTime) }}</text>
					</view>
				</view>

				<!-- 任务信息 -->
				<view class="task-block">
					<view class="task-line">
						<text class="line-label">任务</text>
						<text class="line-value">{{ log.taskName || '-' }}</text>
					</view>
					<view class="task-line">
						<text class="line-label">项目</text>
						<text class="line-value">{{ log.projectName || '-' }}</text>
					</view>
				</view>

				<!-- 进度 -->
				<view class="progress-block">
					<view class="progress-head">
						<text class="block-label">提交进度</text>
						<text class="progress-num">{{ log.progress || 0 }}%</text>
					</view>
					<view class="progress-bar">
						<view class="progress-inner" :style="{ width: (log.progress || 0) + '%' }"></view>
					</view>
				</view>

				<!-- 进度说明 -->
				<view class="remark-block" v-if="log.remark">
					<text class="block-label">进度说明</text>
					<view class="remark-text">{{ log.remark }}</view>
				</view>

				<!-- 施工图片 -->
			<view class="image-block" v-if="parseImages(log).length">
				<text class="block-label">施工图片</text>
				<view class="image-strip" role="img" aria-label="施工图片">
					<image
						v-for="(img, idx) in parseImages(log)"
						:key="idx"
						class="image-thumb"
						:src="img"
						mode="aspectFill"
						@click="previewImage(log, idx)"
					/>
				</view>
			</view>

				<!-- 操作按钮 -->
				<view class="action-row">
					<button class="action-btn btn-reject" @click="openReject(log)">驳回</button>
					<button class="action-btn btn-approve" @click="confirmApprove(log)">通过</button>
				</view>
			</view>

			<!-- 空态 -->
			<view v-if="list.length === 0" class="empty">
				<view class="empty-icon" aria-hidden="true">
					<image class="svg-icon-lg" src="/static/icon/审核.svg" mode="aspectFit" />
				</view>
				<text>暂无待审核进度</text>
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
	import { taskApi } from '@/api/task'
	import { formatDateTime } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'
	import { canReviewProgress } from '@/utils/permission'
	import { API_BASE_URL } from '@/utils/config'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				list: [],            // 待审核进度日志列表
				loading: false,      // 列表加载中标记
				hasPermission: true, // 当前用户是否有审核权限
				rejectVisible: false, // 驳回弹窗显隐
				rejectRemark: '',     // 驳回原因
				currentLog: null,     // 当前操作的日志对象
				reviewing: false      // 审核提交中标记（防重复点击）
			}
		},
		// 页面加载：权限校验
		onLoad() {
			this.hasPermission = canReviewProgress()
			if (!this.hasPermission) {
				uni.showToast({ title: '无权限访问', icon: 'none' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
			}
		},
		// 页面显示：加载待审核列表（从其他页返回时刷新）
		onShow() {
			if (this.hasPermission) this.loadList()
		},
		// 下拉刷新：先校验权限，无权限时直接停止刷新动画
		onPullDownRefresh() {
			if (!this.hasPermission) {
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
			formatDateTime,
			/** 取汇报人姓名首字符作为头像展示 */
			initialOf(name) {
				if (!name) return '?'
				return String(name).charAt(0).toUpperCase()
			},
			/**
			 * 解析图片字段为完整 URL 数组
			 * 后端返回的是相对路径（如 2026/07/25/xxx.png），
			 * 小程序 <image> 需要完整 URL 才能加载
			 */
			parseImages(log) {
				const imgs = log && log.images
				if (!imgs) return []
				let arr = imgs
				if (typeof imgs === 'string') {
					try {
						const p = JSON.parse(imgs)
						arr = Array.isArray(p) ? p : []
					} catch (e) {
						return []
					}
				}
				if (!Array.isArray(arr)) return []
				// 将相对路径拼接为完整 URL
				return arr.map(p => {
					if (!p) return ''
					if (/^https?:\/\//.test(p) || /^\/static\//.test(p)) return p
					return API_BASE_URL + '/files/preview/' + p.replace(/^\//, '')
				})
			},
			/** 预览施工图片 */
			previewImage(log, idx) {
				const urls = this.parseImages(log)
				if (!urls.length) return
				uni.previewImage({ current: urls[idx], urls })
			},
			/**
			 * 加载待审核进度列表
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async loadList(stopPull) {
				this.loading = true
				try {
					const res = await taskApi.listPendingProgress({})
					this.list = Array.isArray(res) ? res : []
				} catch (e) {
					console.warn('load pending error', e)
					this.list = []
				} finally {
					this.loading = false
					if (stopPull) uni.stopPullDownRefresh()
				}
			},
			/** 打开驳回弹窗：重置原因并记录当前日志 */
			openReject(log) {
				this.currentLog = log
				this.rejectRemark = ''
				this.rejectVisible = true
			},
			/** 确认驳回：校验原因后提交 */
			async confirmReject() {
				if (this.reviewing) return
				const remark = (this.rejectRemark || '').trim()
				if (!remark) {
					return uni.showToast({ title: '请输入驳回原因', icon: 'none' })
				}
				const log = this.currentLog
				if (!log) return
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await taskApi.reviewProgress(log.id, { approve: false, remark })
					this.removeLog(log.id)
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
			confirmApprove(log) {
				if (this.reviewing) return
				uni.showModal({
					title: '确认审核',
					content: '确定通过该进度填报吗？',
					confirmText: '通过',
					confirmColor: '#52C41A',
					success: (res) => {
						if (res.confirm) this.doApprove(log)
					}
				})
			},
			/** 提交通过 */
			async doApprove(log) {
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await taskApi.reviewProgress(log.id, { approve: true })
					this.removeLog(log.id)
					uni.showToast({ title: '已通过', icon: 'success' })
				} catch (e) {
					console.error('approve error', e)
				} finally {
					this.reviewing = false
					uni.hideLoading()
				}
			},
			/**
			 * 审核完成后从列表移除该日志（即时反馈）
			 * @param {string|number} logId 日志ID
			 */
			removeLog(logId) {
				const idx = this.list.findIndex((l) => l.id === logId)
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
	.sk-w80 { width: 80rpx; }
	.sk-w-full { width: 100%; }
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}

	/* 汇报人 */
	.reporter-row {
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
	.reporter-info {
		margin-left: 16rpx;
		display: flex;
		flex-direction: column;
	}
	.reporter-name {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
	}
	.reporter-time {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 4rpx;
	}

	/* 任务信息 */
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

	/* 进度 */
	.progress-block {
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.progress-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 12rpx;
	}
	.progress-num {
		font-size: 30rpx;
		font-weight: 700;
		color: #096DD9;
	}
	.progress-bar {
		height: 12rpx;
		background: #EBEEF5;
		border-radius: 6rpx;
		overflow: hidden;
	}
	.progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 6rpx;
		transition: width 0.3s;
	}

	/* 进度说明 */
	.remark-block {
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
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

	/* 施工图片 */
	.image-block {
		padding: 20rpx 0;
	}
	.image-block .block-label {
		display: block;
		margin-bottom: 12rpx;
	}
	.image-strip {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
	}
	.image-thumb {
		width: 140rpx;
		height: 140rpx;
		border-radius: 8rpx;
		background: #F5F7FA;
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
