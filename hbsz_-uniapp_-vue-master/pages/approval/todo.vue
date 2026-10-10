<!--
  审批待办中心页
  业务用途：聚合进度/工作量/文档/授权四类审核待办的统一入口（与 Web 端待办中心同源）。
           顶部展示分类计数，支持按来源筛选，分页加载，可快捷「通过/驳回」。
  审核分发：approvalTodoApi.reviewTodo 按 source 分发到各业务模块已有接口
           （后端 CAS 状态机 + 归属校验 + 通知联动零重复）。
  权限说明：后端按当前用户审核权限过滤——无任何审核权限返回空列表，页面不做前端权限拦截。
  数据来源：approvalTodoApi.getTodoCounts（分类计数）；approvalTodoApi.listTodos（分页待办）。
  导航特性：非 tabBar 页；支持下拉刷新 + 触底加载更多。
-->
<template>
	<view class="page">
		<!-- 头部 -->
		<view class="header">
			<text class="header-title">审批中心</text>
			<view class="header-badge" v-if="counts.total">{{ counts.total }}</view>
		</view>

		<!-- 分类计数卡片 -->
		<view class="counts-card">
			<view
				v-for="cat in categories"
				:key="cat.value"
				:class="['count-item', sourceFilter === cat.value && 'count-item-active']"
				role="button"
				@click="onFilterChange(cat.value)"
			>
				<view class="count-num" :class="cat.tone">{{ counts[cat.key] || 0 }}</view>
				<view class="count-label">{{ cat.label }}</view>
			</view>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && list.length === 0">
			<view class="card" v-for="i in 3" :key="'sk' + i">
				<view class="sk-line sk-w240"></view>
				<view class="sk-line sk-w-full"></view>
				<view class="sk-line sk-w160"></view>
			</view>
		</view>

		<!-- 待办列表 -->
		<template v-else>
			<view
				v-for="todo in list"
				:key="todo.source + '-' + todo.bizId"
				class="card todo-card"
			>
				<!-- 来源标签 + 项目 -->
				<view class="todo-head">
					<view class="source-tag" :class="'src-' + todo.source">
						<text>{{ sourceText(todo.source) }}</text>
					</view>
					<text class="todo-project">{{ todo.projectName || '-' }}</text>
				</view>

				<!-- 标题与提交人 -->
				<view class="todo-body">
					<text class="todo-title">{{ todo.title || '-' }}</text>
					<view class="todo-meta">
						<text class="meta-submitter">{{ todo.submitter || '-' }}</text>
						<text class="meta-time">{{ formatDateTime(todo.submitTime) }}</text>
					</view>
				</view>

				<!-- 详情摘要 -->
				<view class="todo-detail" v-if="todo.detail">{{ todo.detail }}</view>

				<!-- 操作按钮 -->
				<view class="action-row">
					<button class="action-btn btn-reject" :disabled="reviewing" @click="openReject(todo)">驳回</button>
					<button class="action-btn btn-approve" :disabled="reviewing" @click="confirmApprove(todo)">通过</button>
				</view>
			</view>

			<!-- 加载更多 -->
			<load-more
				v-if="list.length"
				:status="loadMoreStatus"
			/>

			<!-- 空态 -->
			<view v-if="list.length === 0" class="empty">
				<view class="empty-icon" aria-hidden="true">
					<image class="svg-icon-lg" src="/static/icon/审核.svg" mode="aspectFit" />
				</view>
				<text>暂无审批待办</text>
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
	import { approvalTodoApi } from '@/api/approvalTodo'
	import { formatDateTime } from '@/utils/format'

	/** 分页大小 */
	const PAGE_SIZE = 10

	export default {
		data() {
			return {
				sourceFilter: '',      // 来源筛选：'' 全部 / progress / workload / document / auth
				categories: [
					{ key: 'progress', value: 'progress', label: '进度', tone: 'tone-blue' },
					{ key: 'workload', value: 'workload', label: '工作量', tone: 'tone-green' },
					{ key: 'document', value: 'document', label: '文档', tone: 'tone-purple' },
					{ key: 'auth', value: 'auth', label: '授权', tone: 'tone-orange' }
				],
				counts: {},            // 分类计数 { progress, workload, document, auth, total }
				list: [],              // 待办列表（分页累计）
				total: 0,              // 筛选条件下的总条数
				pageNum: 1,            // 当前页码
				loading: false,        // 首次加载中
				loadingMore: false,    // 加载更多中
				rejectVisible: false,  // 驳回弹窗显隐
				rejectRemark: '',      // 驳回原因
				currentTodo: null,     // 当前操作的待办
				reviewing: false       // 审核提交中（防重复点击）
			}
		},
		computed: {
			/** 加载更多组件状态 */
			loadMoreStatus() {
				if (this.loadingMore) return 'loading'
				return this.list.length >= this.total ? 'noMore' : 'more'
			}
		},
		onShow() {
			// 每次进入刷新（从审核详情返回后保持数据新鲜）
			this.refresh()
		},
		onPullDownRefresh() {
			this.refresh().finally(() => uni.stopPullDownRefresh())
		},
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			formatDateTime,
			/** 来源枚举 → 中文文本 */
			sourceText(source) {
				const map = { progress: '进度审核', workload: '工作量', document: '文档审核', auth: '项目授权' }
				return map[source] || source || '-'
			},
			/** 刷新：计数 + 重置分页加载首屏 */
			async refresh() {
				this.loadCounts()
				this.pageNum = 1
				await this.loadPage()
			},
			/** 加载分类计数（静默失败，计数卡片非关键路径） */
			async loadCounts() {
				try {
					const res = await approvalTodoApi.getTodoCounts()
					this.counts = res || {}
				} catch (e) {
					console.warn('load counts error', e)
				}
			},
			/** 加载当前页数据（首屏） */
			async loadPage() {
				this.loading = true
				try {
					const params = { pageNum: this.pageNum, pageSize: PAGE_SIZE }
					if (this.sourceFilter) params.source = this.sourceFilter
					const res = await approvalTodoApi.listTodos(params)
					this.list = (res && res.records) || []
					this.total = (res && res.total) || 0
				} catch (e) {
					console.warn('load todos error', e)
					this.list = []
					this.total = 0
				} finally {
					this.loading = false
				}
			},
			/** 触底加载下一页（页码在请求成功后递增，避免失败导致页码错位） */
			async loadMore() {
				if (this.loading || this.loadingMore) return
				if (this.list.length >= this.total) return
				this.loadingMore = true
				try {
					const params = { pageNum: this.pageNum + 1, pageSize: PAGE_SIZE }
					if (this.sourceFilter) params.source = this.sourceFilter
					const res = await approvalTodoApi.listTodos(params)
					const records = (res && res.records) || []
					this.list = this.list.concat(records)
					this.total = (res && res.total) || 0
					// 请求成功后才递增页码（项目已知陷阱：提前递增会导致失败后数据丢失）
					this.pageNum++
				} catch (e) {
					console.warn('load more error', e)
				} finally {
					this.loadingMore = false
				}
			},
			/** 分类卡片点击：切换来源筛选并刷新 */
			onFilterChange(source) {
				const next = this.sourceFilter === source ? '' : source
				if (this.sourceFilter === next) return
				this.sourceFilter = next
				this.refresh()
			},
			/** 打开驳回弹窗 */
			openReject(todo) {
				this.currentTodo = todo
				this.rejectRemark = ''
				this.rejectVisible = true
			},
			/** 确认驳回：校验原因后按来源分发审核 */
			async confirmReject() {
				if (this.reviewing) return
				const remark = (this.rejectRemark || '').trim()
				if (!remark) {
					return uni.showToast({ title: '请输入驳回原因', icon: 'none' })
				}
				const todo = this.currentTodo
				if (!todo) return
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await approvalTodoApi.reviewTodo(todo, { approve: false, remark })
					this.removeTodo(todo)
					this.rejectVisible = false
					uni.showToast({ title: '已驳回', icon: 'success' })
				} catch (e) {
					console.error('reject todo error', e)
				} finally {
					this.reviewing = false
					uni.hideLoading()
				}
			},
			/** 通过：弹确认框后按来源分发审核 */
			confirmApprove(todo) {
				if (this.reviewing) return
				uni.showModal({
					title: '确认审批',
					content: `确定通过该${this.sourceText(todo.source)}待办吗？`,
					confirmText: '通过',
					confirmColor: '#52C41A',
					success: (res) => {
						if (res.confirm) this.doApprove(todo)
					}
				})
			},
			/** 提交通过 */
			async doApprove(todo) {
				this.reviewing = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await approvalTodoApi.reviewTodo(todo, { approve: true })
					this.removeTodo(todo)
					uni.showToast({ title: '已通过', icon: 'success' })
				} catch (e) {
					console.error('approve todo error', e)
				} finally {
					this.reviewing = false
					uni.hideLoading()
				}
			},
			/**
			 * 审核完成后移除待办并同步计数（即时反馈）
			 * @param {Object} todo 待办项（source + bizId 唯一）
			 */
			removeTodo(todo) {
				const idx = this.list.findIndex(
					(t) => t.source === todo.source && t.bizId === todo.bizId
				)
				if (idx > -1) this.list.splice(idx, 1)
				this.total = Math.max(0, this.total - 1)
				// 同步分类计数
				if (todo.source && this.counts[todo.source] > 0) {
					this.counts[todo.source]--
				}
				if (this.counts.total > 0) this.counts.total--
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

	/* 分类计数卡片 */
	.counts-card {
		display: flex;
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx 12rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.count-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 8rpx 0;
		border-radius: 12rpx;
	}
	.count-item-active {
		background: #F0F7FF;
	}
	.count-num {
		font-size: 40rpx;
		font-weight: 700;
		color: #303133;
	}
	.count-num.tone-blue { color: #1890FF; }
	.count-num.tone-green { color: #52C41A; }
	.count-num.tone-purple { color: #722ED1; }
	.count-num.tone-orange { color: #FAAD14; }
	.count-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 6rpx;
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

	/* 待办卡片 */
	.todo-head {
		display: flex;
		align-items: center;
		gap: 16rpx;
		margin-bottom: 16rpx;
	}
	.source-tag text {
		display: inline-block;
		font-size: 22rpx;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
	}
	.src-progress text {
		color: #1890FF;
		background: #E6F7FF;
	}
	.src-workload text {
		color: #52C41A;
		background: #F6FFED;
	}
	.src-document text {
		color: #722ED1;
		background: #F9F0FF;
	}
	.src-auth text {
		color: #FAAD14;
		background: #FFFBE6;
	}
	.todo-project {
		flex: 1;
		font-size: 24rpx;
		color: #5C6370;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.todo-body {
		padding-bottom: 16rpx;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.todo-title {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
		line-height: 1.5;
	}
	.todo-meta {
		display: flex;
		align-items: center;
		gap: 20rpx;
		margin-top: 10rpx;
	}
	.meta-submitter {
		font-size: 24rpx;
		color: #606266;
	}
	.meta-time {
		font-size: 22rpx;
		color: #909399;
	}

	.todo-detail {
		padding: 16rpx;
		margin: 16rpx 0 4rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 24rpx;
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
