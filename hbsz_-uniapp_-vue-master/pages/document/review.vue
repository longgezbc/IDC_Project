<!--
  文档审核页（PM 视角）
  业务用途：PM 集中审核上传的施工文档，可「通过」或「驳回」（驳回需填写原因）。
           审核结果通过 documentApi.review 提交，通过/驳回后该文档即时从待审核列表移除。
           支持下载原文件、预览文档信息。
  数据来源：documentApi.getList（status=待审核 筛选）；documentApi.review（提交审核结果）；
           documentApi.download（下载原文件）。
  导航特性：非 tabBar 页；支持下拉刷新；上拉加载更多；列表为空时展示空态。
-->
<template>
	<view class="page">
		<!-- 头部：标题 + 待审核数量徽标 -->
		<view class="header">
			<text class="header-title">文档审核</text>
			<view class="header-badge" v-if="total > 0">{{ total > 99 ? '99+' : total }}</view>
		</view>

		<!-- 筛选条：项目筛选 -->
		<view class="filter-bar">
			<view
				class="filter-item"
				:class="{ active: currentProject === '' }"
				role="button"
				@click="changeProject('')"
			>全部</view>
			<scroll-view scroll-x class="filter-scroll" show-scrollbar="false">
				<view
					v-for="p in projectOptions"
					:key="p.id"
					class="filter-item"
					:class="{ active: currentProject === p.id }"
					role="button"
					@click="changeProject(p.id)"
				>{{ p.name }}</view>
			</scroll-view>
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
				v-for="doc in list"
				:key="doc.id"
				class="card review-card"
			>
				<!-- 上传人信息 -->
				<view class="reporter-row">
					<view class="avatar">{{ initialOf(doc.uploaderName) }}</view>
					<view class="reporter-info">
						<text class="reporter-name">{{ doc.uploaderName || '未知' }}</text>
						<text class="reporter-time">{{ formatDateTime(doc.uploadDate || doc.createTime) }}</text>
					</view>
					<view class="file-badge" :style="{ background: fileColor(doc) }" aria-hidden="true">
						{{ fileExt(doc) }}
					</view>
				</view>

				<!-- 文档信息 -->
				<view class="task-block">
					<view class="doc-name-line">
						<text class="doc-name">{{ doc.name }}</text>
					</view>
					<view class="task-line">
						<text class="line-label">项目</text>
						<text class="line-value">{{ doc.projectName || '-' }}</text>
					</view>
					<view class="task-line">
						<text class="line-label">分类</text>
						<text class="line-value">{{ doc.category || doc.documentType || '-' }}</text>
					</view>
					<view class="task-line">
						<text class="line-label">大小</text>
						<text class="line-value">{{ doc.size || '-' }}</text>
					</view>
					<view v-if="doc.description" class="task-line">
						<text class="line-label">说明</text>
						<text class="line-value desc">{{ doc.description }}</text>
					</view>
				</view>

				<!-- 操作按钮（前置） -->
				<view class="file-action-row">
					<view class="file-action" role="button" @click="goPreview(doc)">预览</view>
					<view class="file-action" role="button" @click="handleDownload(doc)">下载</view>
				</view>

				<!-- 审核操作按钮 -->
				<view class="action-row">
					<button class="action-btn btn-reject" @click="openReject(doc)">驳回</button>
					<button class="action-btn btn-approve" @click="confirmApprove(doc)">通过</button>
				</view>
			</view>

			<!-- 空态 -->
			<view v-if="list.length === 0" class="empty">
				<view class="empty-icon" aria-hidden="true">
					<image class="svg-icon-lg" src="/static/icon/审核.svg" mode="aspectFit" />
				</view>
				<text>暂无待审核文档</text>
			</view>

			<view v-if="loading && list.length > 0" class="load-more">加载中...</view>
			<view v-else-if="noMore && list.length > 0" class="load-more">— 没有更多了 —</view>
		</template>

		<!-- 驳回弹窗 -->
		<view v-if="rejectVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="rejectVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">驳回原因</view>
				<textarea
					v-model="rejectRemark"
					placeholder="请输入驳回原因..."
					maxlength="200"
					aria-label="驳回原因"
					class="popup-textarea"
				/>
				<view class="popup-actions">
					<button class="btn-cancel" @click="rejectVisible=false">取消</button>
					<button class="btn-confirm" :loading="reviewing" :disabled="reviewing" @click="confirmReject">确定</button>
				</view>
			</view>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { documentApi } from '@/api/document'
	import { projectApi } from '@/api/project'
	import { formatDateTime, getFileTypeColor, getExt } from '@/utils/format'
	import { canReviewProgress } from '@/utils/permission'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				list: [],               // 待审核文档列表
				loading: false,         // 列表加载中标记
				noMore: false,          // 是否已无更多
				page: 1,                // 当前页码
				size: 10,               // 每页条数
				total: 0,               // 总条数
				hasPermission: true,    // 当前用户是否有审核权限
				currentProject: '',     // 当前项目筛选（''=全部）
				projectOptions: [],     // 项目筛选项
				rejectVisible: false,   // 驳回弹窗显隐
				rejectRemark: '',       // 驳回原因
				currentDoc: null,        // 当前操作的文档对象
				reviewing: false        // 审核提交中标记（防重复点击）
			}
		},
		onLoad() {
			// 复用 PM 角色权限：能审核进度的也能审核文档
			this.hasPermission = canReviewProgress()
			if (!this.hasPermission) {
				uni.showToast({ title: '无权限访问', icon: 'none' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
				return
			}
			this.loadProjectOptions()
		},
		onShow() {
			if (this.hasPermission) this.refresh()
		},
		onPullDownRefresh() {
			if (!this.hasPermission) {
				uni.stopPullDownRefresh()
				return
			}
			this.refresh(true)
		},
		onReachBottom() {
			this.loadMore()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			formatDateTime,
			/** 取上传人姓名首字符作为头像展示 */
			initialOf(name) {
				if (!name) return '?'
				return String(name).charAt(0).toUpperCase()
			},
			/** 文档图标显示的扩展名文本 */
			fileExt(doc) {
				return (getExt(doc.name || doc.fileName) || (doc.fileType || 'FILE')).substring(0, 4).toUpperCase()
			},
			/** 文档图标背景色（按文件类型区分） */
			fileColor(doc) {
				return getFileTypeColor(getExt(doc.name || doc.fileName) || doc.fileType)
			},
			/** 加载项目筛选项（仅展示有文档的项目，简化为近期项目） */
			async loadProjectOptions() {
				try {
					const res = await projectApi.getList({ pageNum: 1, pageSize: 20 })
					const list = (res && (res.records || res.list || res)) || []
					this.projectOptions = list.map(p => ({ id: p.id, name: p.name }))
				} catch (e) {
					console.warn('project options error', e)
				}
			},
			/** 切换项目筛选 */
			changeProject(pid) {
				if (this.currentProject === pid) return
				this.currentProject = pid
				this.refresh()
			},
			/** 刷新列表（重置到第一页） */
			async refresh(stopPull) {
				this.page = 1
				this.noMore = false
				await this.loadList()
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载下一页 */
			async loadMore() {
				if (this.loading || this.noMore) return
				await this.loadList(true, this.page + 1)
			},
			/**
			 * 加载待审核文档列表
			 * 后端通过 status="待审核" 过滤；如接口字段为 reviewStatus 则同时兼容
			 * @param {boolean} append 是否追加
			 */
			async loadList(append = false, loadPage) {
				if (this.loading) return
				this.loading = true
				const page = loadPage || this.page
				try {
					const params = {
						pageNum: page,
						pageSize: this.size,
						// 兼容多种字段命名（status / reviewStatus）
						status: '待审核',
						reviewStatus: '待审核'
					}
					if (this.currentProject) params.projectId = this.currentProject
					const res = await documentApi.getList(params)
					const list = (res && (res.records || res.list || res)) || []
					const total = (res && (res.total || res.totalCount)) || 0
					if (append) {
						this.list = this.list.concat(list)
					} else {
						this.list = list
					}
					this.total = total
					this.noMore = this.list.length >= total
					this.page = page
				} catch (e) {
					console.warn('load review list error', e)
					if (!append) this.list = []
				} finally {
					this.loading = false
				}
			},
			/** 跳转文档预览 */
			goPreview(doc) {
				uni.navigateTo({ url: `/pages/document/preview?id=${doc.id}` })
			},
			/** 下载文档 */
			async handleDownload(doc) {
				try {
					await documentApi.download(doc.id)
				} catch (e) {
					console.warn('download error', e)
				}
			},
			/** 打开驳回弹窗 */
			openReject(doc) {
				this.currentDoc = doc
				this.rejectRemark = ''
				this.rejectVisible = true
			},
			/** 确认驳回 */
			async confirmReject() {
				if (this.reviewing) return
				const remark = (this.rejectRemark || '').trim()
				if (!remark) {
					return uni.showToast({ title: '请输入驳回原因', icon: 'none' })
				}
				const doc = this.currentDoc
				if (!doc) return
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await documentApi.review(doc.id, { approve: false, remark })
					this.removeDoc(doc.id)
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
			confirmApprove(doc) {
				if (this.reviewing) return
				uni.showModal({
					title: '确认审核',
					content: `确定通过文档「${doc.name}」吗？`,
					confirmText: '通过',
					confirmColor: '#52C41A',
					success: (res) => {
						if (res.confirm) this.doApprove(doc)
					}
				})
			},
			/** 提交通过 */
			async doApprove(doc) {
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await documentApi.review(doc.id, { approve: true })
					this.removeDoc(doc.id)
					uni.showToast({ title: '已通过', icon: 'success' })
				} catch (e) {
					console.error('approve error', e)
				} finally {
					this.reviewing = false
					uni.hideLoading()
				}
			},
			/** 审核完成后从列表移除该文档 */
			removeDoc(docId) {
				const idx = this.list.findIndex((d) => d.id === docId)
				if (idx > -1) {
					this.list.splice(idx, 1)
					this.total = Math.max(0, this.total - 1)
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

	/* 头部 */
	.header {
		display: flex;
		align-items: center;
		padding: 12rpx 4rpx 16rpx;
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

	/* 筛选条 */
	.filter-bar {
		display: flex;
		align-items: center;
		padding: 4rpx 0 20rpx;
	}
	.filter-scroll {
		flex: 1;
		white-space: nowrap;
	}
	.filter-item {
		display: inline-block;
		padding: 10rpx 24rpx;
		margin-right: 12rpx;
		font-size: 24rpx;
		color: #5C6370;
		background: #FFFFFF;
		border-radius: 24rpx;
		&.active {
			background: #1890FF;
			color: #FFFFFF;
		}
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

	/* 上传人 */
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
		flex: 1;
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
	.file-badge {
		padding: 6rpx 12rpx;
		border-radius: 8rpx;
		color: #FFFFFF;
		font-size: 20rpx;
		font-weight: 600;
		flex-shrink: 0;
	}

	/* 文档信息 */
	.task-block {
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.doc-name-line {
		margin-bottom: 12rpx;
	}
	.doc-name {
		font-size: 30rpx;
		font-weight: 600;
		color: #303133;
		line-height: 1.4;
	}
	.task-line {
		display: flex;
		align-items: flex-start;
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
	.line-value.desc {
		font-weight: 400;
		color: #606266;
		line-height: 1.5;
	}

	/* 文件操作（预览/下载） */
	.file-action-row {
		display: flex;
		gap: 16rpx;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.file-action {
		flex: 1;
		text-align: center;
		font-size: 26rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 12rpx 0;
		border-radius: 8rpx;
	}

	/* 操作按钮 */
	.action-row {
		display: flex;
		gap: 16rpx;
		padding-top: 20rpx;
	}
	.action-btn {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		border: none;
		padding: 0;
		margin: 0;
	}
	.btn-reject {
		background: #FFFFFF;
		color: #FF4D4F;
		border: 1rpx solid #FFA39E;
	}
	.btn-approve {
		background: #52C41A;
		color: #FFFFFF;
	}
	.btn-reject[disabled] { opacity: 0.6; }
	.btn-approve[disabled] { opacity: 0.6; }

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

	/* 加载更多 */
	.load-more {
		text-align: center;
		color: #909399;
		font-size: 24rpx;
		padding: 24rpx 0;
	}

	/* 驳回弹窗 */
	.popup-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 999;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 60rpx;
	}
	.popup {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 32rpx;
		width: 100%;
		max-width: 620rpx;
	}
	.popup-title {
		font-size: 32rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 20rpx;
	}
	.popup-textarea {
		width: 100%;
		min-height: 200rpx;
		padding: 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.popup-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 24rpx;
	}
	.popup-actions button {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		padding: 0;
		margin: 0;
		border: none;
	}
	.btn-cancel {
		background: #F5F7FA;
		color: #5C6370;
	}
	.btn-confirm {
		background: #1890FF;
		color: #FFFFFF;
	}

	.bottom-safe {
		height: env(safe-area-inset-bottom);
		padding-bottom: 20rpx;
	}
</style>
