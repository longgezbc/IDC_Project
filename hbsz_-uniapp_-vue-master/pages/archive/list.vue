<!--
  归档管理列表页
  业务用途：展示已归档项目列表，支持关键字搜索、项目类型与归档日期范围筛选、
           分页加载与下拉刷新。PM/admin 可恢复归档；彻底删除仅保留在 Web 端。
  数据来源：archiveApi.getList（分页查询）/ archiveApi.getStatistics（统计）。
  导航特性：非 tabBar 页；支持下拉刷新（onPullDownRefresh）与上拉加载更多（onReachBottom）；
           点击卡片或「查看详情」进入项目详情；恢复归档需二次确认。
-->
<template>
	<view class="page">
		<!-- 顶部标题栏：使用全局 PageHeader 组件（统一非 tabBar 页头部样式） -->
		<page-header title="归档管理" />

		<!-- 统计卡（4 列） -->
		<view class="stat-card">
			<view class="stat-item">
				<view class="stat-value">{{ statistics.archivedCount || 0 }}</view>
				<view class="stat-label">归档项目数</view>
			</view>
			<view class="stat-item">
				<view class="stat-value">{{ statistics.completedCount || 0 }}</view>
				<view class="stat-label">已完成归档</view>
			</view>
			<view class="stat-item">
				<view class="stat-value">{{ formatAmount(statistics.totalAmount) }}</view>
				<view class="stat-label">归档总额</view>
			</view>
			<view class="stat-item">
				<view class="stat-value">{{ statistics.documentCount || 0 }}</view>
				<view class="stat-label">归档文档数</view>
			</view>
		</view>

		<!-- 搜索栏：使用全局 SearchBar 组件（内置防抖与清空按钮） -->
		<search-bar
			v-model="keyword"
			placeholder="搜索项目名/编号/客户"
			aria-label="搜索归档项目"
			:debounce="400"
			@search="onSearch"
		/>

		<!-- 筛选区：项目类型 + 归档日期范围 -->
		<view class="filter-row">
			<picker class="filter-picker" :range="typeOptions" :value="typeIndex" @change="onTypeChange">
				<view class="picker-value" :class="{ placeholder: !filterType }">
					{{ filterType || '项目类型' }}
				</view>
			</picker>
			<picker class="filter-picker date" mode="date" :value="startDate" @change="onStartDateChange">
				<view class="picker-value" :class="{ placeholder: !startDate }">
					{{ startDate || '开始日期' }}
				</view>
			</picker>
			<text class="filter-sep">~</text>
			<picker class="filter-picker date" mode="date" :value="endDate" @change="onEndDateChange">
				<view class="picker-value" :class="{ placeholder: !endDate }">
					{{ endDate || '结束日期' }}
				</view>
			</picker>
			<view v-if="hasFilter" class="filter-reset" role="button" @click="resetFilter">重置</view>
		</view>

		<!-- 列表 -->
		<view class="list">
			<!-- 首次加载骨架屏 -->
			<template v-if="loading && list.length === 0">
				<skeleton-card :rows="4" />
				<skeleton-card :rows="4" />
				<skeleton-card :rows="4" />
			</template>
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
						<status-tag status="已归档" type="project" />
					</view>
					<view class="proj-info">
						<text class="info-item">编号：{{ p.code || '-' }}</text>
						<text class="info-item">客户：{{ p.customerName || '-' }}</text>
					</view>
					<view class="proj-info">
						<text class="info-item">归档日期：{{ formatDate(p.archivedAt || p.updateTime) }}</text>
					</view>
					<view class="proj-amount">
						<text class="amount-label">合同金额</text>
						<text class="amount-val">¥{{ formatAmount(p.contractAmount) }}</text>
					</view>
					<!-- 操作按钮 -->
					<view class="proj-actions">
						<view class="act-btn detail" role="button" @click.stop="goDetail(p.id)">查看详情</view>
						<view
							v-if="canRestore"
							class="act-btn restore"
							role="button"
							@click.stop="confirmRestore(p)"
						>恢复归档</view>
					</view>
				</view>

				<!-- 空状态：使用全局 EmptyState 组件，加载失败时显示重试按钮
			     失败时增加 role="alert" 让屏幕阅读器主动播报 -->
			<empty-state
				v-if="list.length === 0"
				:role="loadError ? 'alert' : 'text'"
				icon="/static/icon/项目.svg"
				:text="loadError ? '加载失败' : '暂无归档项目'"
				:action-text="loadError ? '重试' : ''"
				@action="retryLoad"
			/>

				<!-- 加载更多：使用全局 LoadMore 组件 -->
				<load-more
					v-if="list.length > 0"
					:status="loading ? 'loading' : (noMore ? 'nomore' : 'hidden')"
				/>
			</template>
		</view>

		<view class="bottom-safe"></view>

		<!-- 恢复归档确认弹窗：使用全局 ConfirmDialog 组件（替代 uni.showModal） -->
		<confirm-dialog
			v-model:visible="confirmVisible"
			title="恢复归档"
			:content="confirmContent"
			confirm-text="恢复"
			:loading="submitting"
			@confirm="onConfirmRestore"
		/>
	</view>
</template>

<script>
	import { archiveApi } from '@/api/archive'
	import { hasPermission } from '@/utils/permission'
	import { formatDate } from '@/utils/format'

	export default {
		data() {
			return {
				// 统计数据
				statistics: {},                  // 归档统计（archivedCount/completedCount/totalAmount/documentCount）
				// 权限：恢复归档仅 PM/admin 可见
				canRestore: hasPermission('project:archive'),
				// 搜索 & 筛选
				keyword: '',                     // 搜索关键字（项目名/编号/客户）
			filterType: '',                  // 项目类型筛选值
			startDate: '',                   // 归档开始日期
			endDate: '',                     // 归档结束日期
			typeOptions: ['数据中心布线', '综合布线', '机房工程', '弱电工程', '其他'],
			// 列表分页
			list: [],                        // 归档项目列表
			page: 1,                         // 当前页码
			size: 10,                        // 每页条数
			total: 0,                        // 总条数
			loading: false,                  // 加载中标记
			noMore: false,                   // 是否已无更多
			loadError: false,                // 加载失败标记（列表为空时展示重试）
			// 恢复归档锁：防止重复提交
			submitting: false,
			// ConfirmDialog 相关
			confirmVisible: false,           // ConfirmDialog 显隐
			confirmContent: '',              // ConfirmDialog 内容文案
			pendingRestore: null             // 待恢复的项目对象
			}
		},
		computed: {
			/** 项目类型当前选中下标（未选时返回 0，picker 默认显示占位文案） */
			typeIndex() {
				return Math.max(0, this.typeOptions.indexOf(this.filterType))
			},
			/** 是否存在任意筛选条件（用于显隐「重置」按钮） */
			hasFilter() {
				return !!(this.filterType || this.startDate || this.endDate)
			}
		},
		// 页面加载：拉取统计 + 首屏列表
		onLoad() {
			this.loadStatistics()
			this.refresh()
		},
		// 下拉刷新：同步刷新统计与列表
		onPullDownRefresh() {
			this.loadStatistics()
			this.refresh(true)
		},
		// 触底加载更多
		onReachBottom() {
			this.loadMore()
		},
		// 注：原 onUnload 清理 searchTimer 的逻辑已迁移至 SearchBar 组件的 beforeUnmount
		methods: {
			formatDate,
			/**
			 * 金额格式化：千分位 + 两位小数（手动实现，兼容小程序环境）
			 * @param {number|string} n 金额
			 * @returns {string}
			 */
			formatAmount(n) {
				const v = Number(n || 0)
				if (isNaN(v)) return '0.00'
				const fixed = v.toFixed(2)
				const [intPart, decPart] = fixed.split('.')
				const intWithSep = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
				return intWithSep + '.' + decPart
			},
			/** 点击搜索按钮（confirm 触发；防抖由 SearchBar 内部处理） */
			onSearch() { this.refresh() },
			/** 项目类型变更 */
			onTypeChange(e) {
				this.filterType = this.typeOptions[e.detail.value]
				this.refresh()
			},
			/** 归档开始日期变更 */
			onStartDateChange(e) {
				const v = e.detail.value
				// 结束日期早于开始日期时清空结束日期，避免无效区间
				if (this.endDate && v > this.endDate) this.endDate = ''
				this.startDate = v
				this.refresh()
			},
			/** 归档结束日期变更 */
			onEndDateChange(e) {
				const v = e.detail.value
				// 开始日期晚于结束日期时清空开始日期，避免无效区间
				if (this.startDate && v < this.startDate) this.startDate = ''
				this.endDate = v
				this.refresh()
			},
			/** 重置全部筛选条件并刷新 */
			resetFilter() {
				this.filterType = ''
				this.startDate = ''
				this.endDate = ''
				this.refresh()
			},
			/**
			 * 拉取归档统计
			 * 失败时静默处理（不阻塞列表加载）
			 */
			async loadStatistics() {
				try {
					const res = await archiveApi.getStatistics()
					this.statistics = res || {}
				} catch (e) {
					console.warn('archive statistics error', e)
				}
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
					const params = { pageNum: page, pageSize: this.size }
					if (this.keyword) params.keyword = this.keyword
					if (this.filterType) params.type = this.filterType
					if (this.startDate) params.archiveStartDate = this.startDate
					if (this.endDate) params.archiveEndDate = this.endDate
					const res = await archiveApi.getList(params)
					// 兼容 {records}/{list}/数组 多种返回结构
					const list = (res && (res.records || res.list || res)) || []
					const total = (res && (res.total || res.totalCount)) || 0
					if (append) {
						this.list = this.list.concat(list)
					} else {
						this.list = list
					}
					this.total = total
					this.noMore = this.list.length >= total
					// 仅在加载成功后更新页码，避免请求失败时 page 已递增导致数据丢失
					this.page = page
					// 列表加载成功后清除失败标记
					this.loadError = false
				} catch (e) {
					console.warn('archive list error', e)
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
			},
			/**
			 * 恢复归档二次确认：打开 ConfirmDialog（替代 uni.showModal）
			 * 实际恢复逻辑在 onConfirmRestore 中执行
			 * @param {Object} p 项目对象
			 */
			confirmRestore(p) {
				if (this.submitting) return
				this.pendingRestore = p
				this.confirmContent = `确认将项目「${p.name}」恢复为正常状态吗？`
				this.confirmVisible = true
			},
			/** ConfirmDialog 确认回调：执行实际恢复 */
			async onConfirmRestore() {
				const p = this.pendingRestore
				if (!p || this.submitting) return
				this.submitting = true
				uni.showLoading({ title: '恢复中', mask: true })
				try {
					await archiveApi.restore(p.id)
					uni.hideLoading()
					uni.showToast({ title: '恢复成功', icon: 'success' })
					// 本地移除已恢复项，并同步统计
					const idx = this.list.findIndex((x) => x.id === p.id)
					if (idx >= 0) this.list.splice(idx, 1)
					this.total = Math.max(0, this.total - 1)
					this.loadStatistics()
					// 关闭弹窗并清理待恢复对象
					this.confirmVisible = false
					this.pendingRestore = null
				} catch (e) {
					uni.hideLoading()
					console.warn('restore error', e)
				} finally {
					this.submitting = false
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

	/* 统计卡（4 列） */
	.stat-card {
		display: flex;
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx 0;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.stat-item {
		flex: 1;
		text-align: center;
		position: relative;
		/* 列间分隔线 */
		&:not(:last-child)::after {
			content: '';
			position: absolute;
			right: 0;
			top: 12rpx;
			bottom: 12rpx;
			width: 1rpx;
			background: #EBEEF5;
		}
	}
	.stat-value {
		font-size: 34rpx;
		font-weight: 700;
		color: #096DD9;
		line-height: 1.4;
		word-break: break-all;
	}
	.stat-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}

	/* 筛选区 */
	.filter-row {
		display: flex;
		align-items: center;
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 16rpx 20rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.filter-picker {
		flex: 1;
		min-width: 0;
	}
	.filter-picker.date {
		flex: 0 0 auto;
	}
	.picker-value {
		font-size: 24rpx;
		color: #303133;
		background: #F5F7FA;
		border-radius: 8rpx;
		padding: 12rpx 16rpx;
		text-align: center;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.picker-value.placeholder {
		color: #767E8A;
	}
	.filter-sep {
		font-size: 24rpx;
		color: #5C6370;
		padding: 0 8rpx;
	}
	.filter-reset {
		font-size: 24rpx;
		color: #096DD9;
		padding: 12rpx 16rpx;
		margin-left: 8rpx;
		flex-shrink: 0;
	}

	/* 列表 */
	.list {
		padding-top: 4rpx;
	}
	.loading-tip {
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
		padding: 80rpx 0;
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
		background: #F4F4F5;
		color: #909399;
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
	.proj-amount {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 12rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid #F5F7FA;
	}
	.amount-label {
		font-size: 24rpx;
		color: #5C6370;
	}
	.amount-val {
		font-size: 30rpx;
		font-weight: 600;
		color: #D4380D;
	}
	.proj-actions {
		display: flex;
		justify-content: flex-end;
		gap: 16rpx;
		margin-top: 20rpx;
	}
	.act-btn {
		font-size: 24rpx;
		padding: 12rpx 28rpx;
		border-radius: 8rpx;
		line-height: 1.2;
	}
	.act-btn.detail {
		color: #096DD9;
		background: #E6F7FF;
	}
	.act-btn.restore {
		color: #FFFFFF;
		background: #1890FF;
	}

	/* 空状态 */
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
	/* 注：原 .nav-bar/.nav-title/.search-bar/.search-input-wrap/.search-icon/
	   .search-input/.ph/.search-clear 样式已迁移至 PageHeader / SearchBar 组件 */
</style>
