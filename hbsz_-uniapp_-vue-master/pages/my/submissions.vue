<!--
  我的填报页
  业务用途：展示当前用户的进度填报记录列表，支持按审核状态筛选、
           下拉刷新、上拉加载更多。
  数据来源：dashboardApi.getMySubmissions（填报列表分页，支持 reviewStatus 过滤）。
  导航特性：非 tabBar 页，可由"我的"入口或工作台快捷入口跳入；
           点击列表项暂不跳转，点击图片调用 uni.previewImage 全屏预览。
-->
<template>
	<view class="page">
		<!-- 审核状态筛选 -->
		<scroll-view class="filter-bar" scroll-x="true" :show-scrollbar="false">
			<view
			v-for="tab in tabs"
			:key="tab.value"
			class="filter-pill"
			role="button"
			:class="{ active: activeTab === tab.value }"
			@click="onTabChange(tab.value)"
		>{{ tab.label }}</view>
		</scroll-view>

		<!-- 填报列表 -->
		<view v-if="list.length > 0" class="list">
			<view v-for="item in list" :key="item.id" class="card">
				<!-- 标题行：任务名 + 审核状态 -->
				<view class="card-head">
					<view class="card-title-wrap">
						<text class="card-title">{{ item.taskName || '未命名任务' }}</text>
						<text class="card-sub">{{ item.projectName || '未关联项目' }}</text>
					</view>
					<text class="review-badge" :class="'review-' + item.reviewStatus">{{ reviewText(item.reviewStatus) }}</text>
				</view>

				<!-- 进度 -->
				<view class="progress-row">
					<view class="progress-meta">
						<text class="meta-label">进度</text>
						<text class="meta-value">{{ item.progress || 0 }}%</text>
					</view>
					<view class="progress-bar">
						<view class="progress-inner" :style="{ width: (item.progress || 0) + '%' }"></view>
					</view>
				</view>

				<!-- 图片缩略图 -->
				<view v-if="parseImages(item.images).length > 0" class="image-strip">
					<image
					v-for="(img, idx) in parseImages(item.images).slice(0, 3)"
					:key="idx"
					class="img-thumb"
					:src="img"
					mode="aspectFill"
					role="button"
					:aria-label="'预览图片 ' + (idx + 1)"
					@click="onPreviewImage(item.images, idx)"
				/>
				</view>

				<!-- 审核信息 -->
				<view v-if="item.reviewStatus !== 'pending' && item.reviewerName" class="review-info">
					<text class="review-line">审核人：{{ item.reviewerName }}</text>
					<text class="review-line">审核时间：{{ formatDateTime(item.reviewTime) }}</text>
					<text v-if="item.reviewStatus === 'rejected' && item.reviewRemark" class="review-line reject-remark">
						驳回原因：{{ item.reviewRemark }}
					</text>
				</view>

				<!-- 提交信息 -->
				<view class="card-foot">
					<text class="foot-text">提交人：{{ item.reporterName || '-' }}</text>
					<text class="foot-text">提交时间：{{ formatDateTime(item.createTime) }}</text>
				</view>
			</view>
		</view>

		<!-- 空状态 -->
		<view v-else-if="!loading" class="empty">
			<text class="empty-icon">📭</text>
			<text class="empty-text">暂无填报记录</text>
		</view>

		<!-- 加载更多 / 没有更多 -->
		<view v-if="list.length > 0" class="load-status">
			<text v-if="loading">加载中...</text>
			<text v-else-if="!hasMore">没有更多了</text>
		</view>
	</view>
</template>

<script>
	import { dashboardApi } from '@/api/dashboard'
	import { formatDateTime } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				// 审核状态筛选
				tabs: [
					{ label: '全部', value: '' },
					{ label: '待审核', value: 'pending' },
					{ label: '已通过', value: 'approved' },
					{ label: '已驳回', value: 'rejected' }
				],
				activeTab: '',
				// 列表数据
				list: [],
				pageNum: 1,
				pageSize: 10,
				total: 0,
				hasMore: true,
				loading: false
			}
		},
		onLoad() {
			this.loadList(true)
		},
		// 下拉刷新
		onPullDownRefresh() {
			this.loadList(true).finally(() => {
				uni.stopPullDownRefresh()
			})
		},
		// 上拉加载更多
		onReachBottom() {
			if (this.hasMore && !this.loading) {
				this.loadList(false)
			}
		},
		methods: {
			formatDateTime,
			/** 审核状态文案 */
			reviewText(status) {
				const map = { pending: '待审核', approved: '已通过', rejected: '已驳回' }
				return map[status] || '未知'
			},
			/** 切换筛选 tab */
			onTabChange(value) {
				if (this.activeTab === value) return
				this.activeTab = value
				this.loadList(true)
			},
			/**
			 * 加载列表
			 * @param {boolean} reset 是否重置到第一页
			 */
			async loadList(reset) {
				if (this.loading) return
				this.loading = true
				if (reset) {
					this.pageNum = 1
					this.hasMore = true
				}
				if (reset) uni.showLoading({ title: '加载中', mask: true })
				try {
					const params = {
						pageNum: this.pageNum,
						pageSize: this.pageSize
					}
					if (this.activeTab) {
						params.reviewStatus = this.activeTab
					}
					const res = await dashboardApi.getMySubmissions(params)
					const records = (res && res.records) || []
					this.total = (res && res.total) || 0
					if (reset) {
						this.list = records
					} else {
						this.list = this.list.concat(records)
					}
					this.hasMore = this.list.length < this.total
					if (this.hasMore) this.pageNum++
				} catch (e) {
					console.warn('loadList error', e)
					if (reset) this.list = []
				} finally {
					this.loading = false
					uni.hideLoading()
				}
			},
			/**
			 * 解析 images 字段（兼容 JSON 字符串或数组）
			 * @param {string|string[]} images
			 * @returns {string[]}
			 */
			parseImages(images) {
				if (!images) return []
				try {
					return typeof images === 'string' ? JSON.parse(images) : (Array.isArray(images) ? images : [])
				} catch (e) {
					return []
				}
			},
			/**
			 * 预览图片
			 * @param {string|string[]} images 字段原值
			 * @param {number} idx 当前点击索引
			 */
			onPreviewImage(images, idx) {
				const urls = this.parseImages(images)
				if (urls.length === 0) return
				uni.previewImage({ current: urls[idx], urls })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		padding: 24rpx 24rpx 40rpx;
		background: #F5F7FA;
	}

	/* 筛选 tab */
	.filter-bar {
		white-space: nowrap;
		margin-bottom: 20rpx;
		padding: 4rpx 0;
	}
	.filter-pill {
		display: inline-block;
		padding: 12rpx 32rpx;
		margin-right: 16rpx;
		font-size: 26rpx;
		color: #606266;
		background: #FFFFFF;
		border-radius: 40rpx;
		border: 2rpx solid #EBEEF5;
	}
	.filter-pill.active {
		color: #FFFFFF;
		background: #1890FF;
		border-color: #096DD9;
		font-weight: 600;
	}

	/* 列表 */
	.list {
		display: flex;
		flex-direction: column;
	}
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.card-head {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 16rpx;
	}
	.card-title-wrap {
		flex: 1;
		min-width: 0;
		margin-right: 16rpx;
	}
	.card-title {
		display: block;
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
		line-height: 1.4;
	}
	.card-sub {
		display: block;
		font-size: 24rpx;
		color: #5C6370;
		margin-top: 6rpx;
	}
	.review-badge {
		flex-shrink: 0;
		padding: 6rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		line-height: 1.4;
	}
	.review-pending {
		color: #5C6370;
		background: #F4F4F5;
	}
	.review-approved {
		color: #389E0D;
		background: #F6FFED;
	}
	.review-rejected {
		color: #D4380D;
		background: #FFF1F0;
	}

	/* 进度 */
	.progress-row {
		margin-bottom: 16rpx;
	}
	.progress-meta {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 8rpx;
	}
	.meta-label {
		font-size: 24rpx;
		color: #5C6370;
	}
	.meta-value {
		font-size: 26rpx;
		font-weight: 600;
		color: #096DD9;
	}
	.progress-bar {
		width: 100%;
		height: 12rpx;
		background: #EBEEF5;
		border-radius: 6rpx;
		overflow: hidden;
	}
	.progress-inner {
		height: 100%;
		background: linear-gradient(90deg, #1890FF 0%, #69C0FF 100%);
		border-radius: 6rpx;
		transition: width 0.3s;
	}

	/* 图片缩略图 */
	.image-strip {
		display: flex;
		gap: 12rpx;
		margin-bottom: 16rpx;
	}
	.img-thumb {
		width: 160rpx;
		height: 160rpx;
		border-radius: 8rpx;
		background: #F5F7FA;
	}

	/* 审核信息 */
	.review-info {
		padding: 16rpx 20rpx;
		background: #F5F7FA;
		border-radius: 12rpx;
		margin-bottom: 16rpx;
	}
	.review-line {
		display: block;
		font-size: 24rpx;
		color: #606266;
		line-height: 1.6;
	}
	.reject-remark {
		color: #D4380D;
	}

	/* 卡片底部 */
	.card-foot {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding-top: 16rpx;
		border-top: 1rpx solid #F5F7FA;
	}
	.foot-text {
		font-size: 22rpx;
		color: #5C6370;
	}

	/* 空状态 */
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 120rpx 0;
	}
	.empty-icon {
		font-size: 96rpx;
		margin-bottom: 20rpx;
	}
	.empty-text {
		font-size: 28rpx;
		color: #5C6370;
	}

	/* 加载状态 */
	.load-status {
		text-align: center;
		padding: 24rpx 0;
		font-size: 24rpx;
		color: #5C6370;
	}
</style>
