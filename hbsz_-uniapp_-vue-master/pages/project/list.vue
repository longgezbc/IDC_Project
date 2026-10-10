<!--
  项目列表页（tabBar 页面）
  业务用途：展示项目列表，支持关键字搜索、状态筛选、分页加载与加载失败重试。
  数据来源：projectApi.getList（分页查询）。
  导航特性：tabBar 页；支持下拉刷新（onPullDownRefresh）与上拉加载更多（onReachBottom）；
           点击项目卡片进入详情页。
-->
<template>
	<view class="page">
		<!-- 搜索栏 -->
		<view class="search-bar">
			<view class="search-input-wrap">
				<image class="search-icon" src="/static/icon/搜索.svg" mode="aspectFit" aria-hidden="true" />
			<input
				class="search-input"
				v-model="keyword"
				placeholder="搜索项目名称/编号"
				placeholder-class="ph"
				confirm-type="search"
				aria-label="搜索项目"
				@confirm="onSearch"
			/>
			<text v-if="keyword" class="search-clear" role="button" aria-label="清空搜索" @click="clearKeyword">✕</text>
			</view>
		</view>

		<!-- 状态筛选 -->
		<scroll-view class="filter-bar" scroll-x>
			<view
				v-for="item in statusFilters"
				:key="item.value"
				class="filter-item"
				:class="{ active: currentStatus === item.value }"
				role="button"
				@click="changeStatus(item.value)"
			>
				{{ item.label }}
			</view>
		</scroll-view>

		<!-- 列表 -->
		<view class="list">
			<view v-if="loading && list.length === 0" class="card">
				<view class="skeleton-block" v-for="i in 4" :key="i"></view>
			</view>
			<template v-else>
				<view
			v-for="p in list"
			:key="p.id"
			class="proj-card"
			hover-class="uni-hover"
			:hover-stay-time="80"
			role="link"
			@click="goDetail(p.id)"
		>
					<view class="proj-head">
						<view class="proj-name">{{ p.name }}</view>
						<view class="tag" :style="statusStyle(p.status)">{{ statusText(p.status) }}</view>
					</view>
					<view class="proj-info">
						<text class="info-item">编号：{{ p.code || '-' }}</text>
						<text class="info-item">负责人：{{ p.manager || '-' }}</text>
					</view>
					<view class="proj-info">
						<text class="info-item">周期：{{ formatDate(p.startDate) }} ~ {{ formatDate(p.endDate) }}</text>
					</view>
					<view class="proj-progress">
						<view class="progress-bar">
							<view class="progress-inner" :style="{ width: (p.progress || 0) + '%' }"></view>
						</view>
						<text class="progress-text">{{ p.progress || 0 }}%</text>
					</view>
				</view>

				<!-- 加载失败且列表为空：展示重试按钮，让用户可主动恢复
			     role="alert" 让屏幕阅读器主动播报错误状态 -->
			<view v-if="loadError && list.length === 0" class="empty" role="alert">
				<view class="empty-icon" aria-hidden="true"><image class="svg-icon-lg" src="/static/icon/项目.svg" mode="aspectFit" /></view>
				<text>加载失败</text>
				<button class="retry-btn" size="mini" @click="retryLoad">重试</button>
			</view>
				<view v-else-if="list.length === 0" class="empty">
				<view class="empty-icon" aria-hidden="true"><image class="svg-icon-lg" src="/static/icon/项目.svg" mode="aspectFit" /></view>
				<text>暂无项目</text>
				</view>

				<view v-if="loading && list.length > 0" class="load-more">加载中...</view>
				<view v-else-if="noMore && list.length > 0" class="load-more">— 没有更多了 —</view>
			</template>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { projectApi } from '@/api/project'
	import { formatDate, getProjectStatus } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				keyword: '',          // 搜索关键字
				currentStatus: '',    // 当前选中的状态筛选值（''=全部）
				statusFilters: [      // 状态筛选选项
					{ label: '全部', value: '' },
					{ label: '进行中', value: '进行中' },
					{ label: '已完成', value: '已完成' },
					{ label: '暂停', value: '暂停' },
					{ label: '延期', value: '延期' }
				],
				list: [],             // 项目列表数据
				page: 1,              // 当前页码
				size: 10,             // 每页条数
				total: 0,             // 总条数
				loading: false,       // 加载中标记
				noMore: false,        // 是否已无更多数据
				// 加载失败标记：用于在 UI 上展示重试按钮
				loadError: false
			}
		},
		// 页面显示：首屏为空时自动加载（tab 切换/返回场景）
		onShow() {
			// tab 切换或返回时刷新首屏
			if (this.list.length === 0) this.refresh()
		},
		// 下拉刷新
		onPullDownRefresh() {
			this.refresh(true)
		},
		// 触底加载更多
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			formatDate,
			// statusText 由 statusMixin 提供
			/** 项目状态标签内联样式（文字色 + 背景色） */
			statusStyle(s) {
				const o = getProjectStatus(s)
				return { color: o.color, background: o.bg }
			},
			/** 点击搜索：从第一页重新加载 */
			onSearch() { this.refresh() },
			/** 清空关键字并刷新 */
			clearKeyword() {
				this.keyword = ''
				this.refresh()
			},
			/** 切换状态筛选并刷新 */
			changeStatus(v) {
				this.currentStatus = v
				this.refresh()
			},
			/**
			 * 刷新列表（重置到第一页）
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async refresh(stopPull) {
				this.page = 1
				this.noMore = false
				await this.loadList()
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载下一页（追加模式） */
			async loadMore() {
				if (this.loading || this.noMore) return
				await this.loadList(true, this.page + 1)
			},
			/**
			 * 加载列表数据
			 * @param {boolean} append 是否追加到现有列表（false 为替换）
			 */
			async loadList(append = false, loadPage) {
				if (this.loading) return
				this.loading = true
				const page = loadPage || this.page
				try {
					const params = {
						page,
						size: this.size
					}
					if (this.keyword) params.keyword = this.keyword
					if (this.currentStatus) params.status = this.currentStatus
					const res = await projectApi.getList(params)
					// 兼容 {records}/{list}/数组 多种返回结构
					const list = (res && (res.records || res.list || res)) || []
					const total = (res && (res.total || res.totalCount)) || list.length
					if (append) {
						this.list = this.list.concat(list)
					} else {
						this.list = list
					}
					this.total = total
					this.noMore = this.list.length >= total
					// 仅在加载成功后更新页码，避免请求失败时 page 已递增导致数据丢失
					this.page = page
				} catch (e) {
					console.warn('list error', e)
					// 标记加载失败：当列表为空时展示重试按钮，避免用户无感知
					this.loadError = true
					uni.showToast({ title: '加载失败，请重试', icon: 'none' })
				} finally {
					this.loading = false
				}
			},
			/** 重试加载：用户点击重试按钮时调用 */
			retryLoad() {
				this.loadError = false
				this.refresh()
			},
			/** 跳转项目详情 */
			goDetail(id) {
				uni.navigateTo({ url: `/pages/project/detail?id=${id}` })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
	}

	.search-bar {
		padding: 16rpx 24rpx;
		background: #FFFFFF;
	}
	.search-input-wrap {
		display: flex;
		align-items: center;
		background: #F5F7FA;
		border-radius: 32rpx;
		padding: 0 24rpx;
		height: 64rpx;
	}
	.search-icon {
		width: 28rpx;
		height: 28rpx;
		margin-right: 12rpx;
		color: #5C6370;
	}
	.search-input {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
	}
	.ph { color: #767E8A; }
	.search-clear {
		color: #767E8A;
		font-size: 24rpx;
		padding: 0 8rpx;
	}

	.filter-bar {
		white-space: nowrap;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
		border-bottom: 1rpx solid #EBEEF5;
	}
	.filter-item {
		display: inline-block;
		padding: 10rpx 28rpx;
		margin-right: 16rpx;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 28rpx;
	}
	.filter-item.active {
		background: #1890FF;
		color: #FFFFFF;
	}

	.list {
		padding: 20rpx 24rpx;
	}
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 20rpx;
	}
	.skeleton-block {
		height: 200rpx;
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

	.proj-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.proj-head {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 16rpx;
	}
	.proj-name {
		font-size: 32rpx;
		font-weight: 600;
		color: #303133;
		flex: 1;
		margin-right: 16rpx;
	}
	.tag {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.proj-info {
		display: flex;
		flex-wrap: wrap;
		margin-bottom: 8rpx;
	}
	.info-item {
		font-size: 24rpx;
		color: #606266;
		margin-right: 24rpx;
		line-height: 1.8;
	}
	.proj-progress {
		display: flex;
		align-items: center;
		margin-top: 16rpx;
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

	.empty {
		padding: 160rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	/* 重试按钮：失败时给用户明确的恢复入口 */
	.retry-btn {
		margin-top: 24rpx;
		background: #4080ff;
		color: #fff;
		font-size: 26rpx;
		border-radius: 8rpx;
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
	.load-more {
		text-align: center;
		color: #767E8A;
		font-size: 24rpx;
		padding: 24rpx 0;
	}
	.bottom-safe { height: calc(40rpx + env(safe-area-inset-bottom)); }
</style>
