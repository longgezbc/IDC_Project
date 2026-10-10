<!--
  客户列表页
  业务用途：展示客户列表，支持关键字搜索、分页加载与下拉刷新。
           PM/Admin 可新增/编辑/删除客户，工程师仅可查看。
  数据来源：customerApi.getList（分页查询）。
  导航特性：非 tabBar 页；支持下拉刷新（onPullDownRefresh）与上拉加载更多（onReachBottom）；
           点击客户卡片进入详情页；长按卡片（有权限时）弹出编辑/删除操作。
-->
<template>
	<view class="page">
		<!-- 顶部标题栏：使用全局 PageHeader 组件（统一非 tabBar 页头部样式） -->
		<page-header title="客户管理">
			<view v-if="canManage" class="nav-action" role="button" @click="openCreate">+ 新增</view>
		</page-header>

		<!-- 搜索栏：使用全局 SearchBar 组件（内置防抖与清空按钮，替代手写 .search-input-wrap） -->
		<search-bar
			v-model="keyword"
			placeholder="搜索客户名称/联系人"
			aria-label="搜索客户"
			:debounce="400"
			@search="onSearch"
		/>

		<!-- 客户列表 -->
		<view class="list">
			<view v-if="loading && list.length === 0" class="loading-tip">加载中...</view>
			<template v-else>
				<view
				v-for="c in list"
				:key="c.id"
				class="cust-card"
				role="link"
				hover-class="uni-hover"
				:hover-stay-time="80"
				@click="goDetail(c.id)"
				@longpress="onLongPress(c)"
			>
					<view class="cust-head">
						<view class="cust-name">{{ c.name }}</view>
						<view class="level-tag" :class="levelClass(c.level)">{{ c.level || '普通' }}</view>
					</view>
					<view class="cust-row">
						<text class="row-label">联系人</text>
						<text class="row-val">{{ c.contactPerson || '-' }}</text>
					</view>
					<view class="cust-row">
						<text class="row-label">📞 电话</text>
						<text class="row-val phone" role="button" @click.stop="callPhone(c.contactPhone)">{{ c.contactPhone || '-' }}</text>
					</view>
					<view class="cust-footer">
						<view v-if="c.industry" class="industry-tag">{{ c.industry }}</view>
						<view v-else class="industry-tag placeholder">未分类</view>
						<view class="project-count">关联项目 {{ c.projectCount || 0 }} 个</view>
					</view>
				</view>

				<!-- 空状态 -->
				<view v-if="list.length === 0" class="empty">
					<view class="empty-icon">🏢</view>
					<text class="empty-text">暂无客户</text>
				</view>
				<view v-if="loading && list.length > 0" class="load-more">加载中...</view>
				<view v-else-if="noMore && list.length > 0" class="load-more">— 没有更多了 —</view>
			</template>
		</view>

		<view class="bottom-safe"></view>

		<!-- 新增/编辑表单弹窗：使用全局 FormDialog 组件（统一遮罩/标题/滚动/底部按钮） -->
		<form-dialog
			v-model:visible="formVisible"
			:title="editMode ? '编辑客户' : '新增客户'"
			:loading="saving"
			@submit="handleSave"
		>
			<view class="form-item">
				<text class="form-label">客户名称<text class="req">*</text></text>
				<input class="form-input" v-model="form.name" aria-label="客户名称" placeholder="请输入客户名称" placeholder-class="ph" />
			</view>
			<view class="form-item">
				<text class="form-label">联系人</text>
				<input class="form-input" v-model="form.contactPerson" aria-label="联系人" placeholder="请输入联系人" placeholder-class="ph" />
			</view>
			<view class="form-item">
				<text class="form-label">联系电话</text>
				<input class="form-input" v-model="form.contactPhone" type="number" aria-label="联系电话" placeholder="请输入联系电话" placeholder-class="ph" />
			</view>
			<view class="form-item">
				<text class="form-label">邮箱</text>
				<input class="form-input" v-model="form.email" aria-label="联系邮箱" placeholder="请输入邮箱" placeholder-class="ph" />
			</view>
			<view class="form-item">
				<text class="form-label">地址</text>
				<input class="form-input" v-model="form.address" aria-label="客户地址" placeholder="请输入地址" placeholder-class="ph" />
			</view>
			<view class="form-item">
				<text class="form-label">行业</text>
				<input class="form-input" v-model="form.industry" aria-label="行业" placeholder="请输入行业" placeholder-class="ph" />
			</view>
			<view class="form-item">
				<text class="form-label">客户等级</text>
				<view class="level-picker">
					<view
						v-for="opt in levelOptions"
						:key="opt"
						class="level-option"
						role="button"
						:class="[{ active: form.level === opt, 'opt-vip': opt === 'VIP' }]"
						@click="form.level = opt"
					>{{ opt }}</view>
				</view>
			</view>
			<view class="form-item">
				<text class="form-label">备注</text>
				<textarea class="form-textarea" v-model="form.remark" aria-label="备注" placeholder="请输入备注" placeholder-class="ph" maxlength="200" :auto-height="false" />
			</view>
		</form-dialog>

		<!-- 删除确认弹窗：使用全局 ConfirmDialog 组件（替代 uni.showModal） -->
		<confirm-dialog
			v-model:visible="confirmVisible"
			title="确认删除"
			:content="deleteContent"
			confirm-text="删除"
			danger
			:loading="deleting"
			@confirm="onConfirmDelete"
		/>
	</view>
</template>

<script>
	import { customerApi } from '@/api/customer'
	import { canManageCustomer } from '@/utils/permission'

	export default {
		data() {
		return {
			canManage: canManageCustomer(), // 是否可管理客户（PM/admin）
			keyword: '',          // 搜索关键字（由 SearchBar 通过 v-model 同步）
			list: [],             // 客户列表
			page: 1,              // 当前页码
			size: 10,             // 每页条数
			total: 0,             // 总条数
			loading: false,       // 加载中标记
			noMore: false,        // 是否已无更多
			// 表单相关
			formVisible: false,   // 表单弹窗显隐
			editMode: false,      // 是否为编辑模式
			editingId: null,      // 正在编辑的客户ID
			saving: false,        // 保存中标记
			levelOptions: ['VIP', '普通'],
			form: this.emptyForm(),
			// 删除确认弹窗相关
			confirmVisible: false,     // ConfirmDialog 显隐
			deleting: false,           // 删除中标记
			pendingDelete: null,       // 待删除的客户对象
			deleteContent: ''          // ConfirmDialog 内容文案
		}
	},
	// 页面加载：首次拉取列表
	onLoad() {
		this.refresh()
	},
	// 页面显示：从其他页返回时刷新当前页数据（保留 page，避免回到第 1 页）
	// 首次加载由 onLoad 处理，此时 list 为空，跳过避免重复请求
	onShow() {
		if (this.list.length > 0) this.loadList()
	},
	// 下拉刷新
	onPullDownRefresh() {
		this.refresh(true)
	},
	// 触底加载更多
	onReachBottom() {
		this.loadMore()
	},
	// 注：原 onUnload 清理 searchTimer 的逻辑已迁移至 SearchBar 组件的 beforeUnmount
	methods: {
		/** 空表单结构（避免引用污染） */
		emptyForm() {
			return {
				name: '',
				contactPerson: '',
				contactPhone: '',
				email: '',
				address: '',
				industry: '',
				level: '普通',
				remark: ''
			}
		},
		/** 客户等级样式类 */
		levelClass(level) {
			return level === 'VIP' ? 'level-vip' : 'level-normal'
		},
		/**
		 * 搜索回调：SearchBar 触发 search 事件时调用
		 * 防抖由 SearchBar 内部处理（:debounce="400"）
		 */
		onSearch() { this.refresh() },
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
					const res = await customerApi.getList(params)
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
				} catch (e) {
					console.warn('customer list error', e)
					uni.showToast({ title: '加载失败，请重试', icon: 'none' })
				} finally {
					this.loading = false
				}
			},
			/** 跳转客户详情 */
			goDetail(id) {
				uni.navigateTo({ url: `/pages/customer/detail?id=${id}` })
			},
			/** 拨打电话 */
			callPhone(phone) {
				if (!phone) return
				uni.makePhoneCall({ phoneNumber: String(phone) })
			},
			/** 长按卡片：有权限时显示编辑/删除操作 */
			onLongPress(c) {
				if (!this.canManage) return
				uni.showActionSheet({
					itemList: ['编辑', '删除'],
					success: (res) => {
						if (res.tapIndex === 0) this.openEdit(c)
						else if (res.tapIndex === 1) this.confirmDelete(c)
					}
				})
			},
			/** 打开新增弹窗 */
			openCreate() {
				this.editMode = false
				this.editingId = null
				this.form = this.emptyForm()
				this.formVisible = true
			},
			/** 打开编辑弹窗（回填表单） */
			openEdit(c) {
				this.editMode = true
				this.editingId = c.id
				this.form = {
					name: c.name || '',
					contactPerson: c.contactPerson || '',
					contactPhone: c.contactPhone || '',
					email: c.email || '',
					address: c.address || '',
					industry: c.industry || '',
					level: c.level || '普通',
					remark: c.remark || ''
				}
				this.formVisible = true
			},
			/**
		 * 删除确认：打开 ConfirmDialog（替代 uni.showModal）
		 * 实际删除逻辑在 onConfirmDelete 中执行
		 */
		confirmDelete(c) {
			this.pendingDelete = c
			this.deleteContent = `确认删除客户「${c.name}」吗？`
			this.confirmVisible = true
		},
		/** ConfirmDialog 确认回调：执行实际删除 */
		async onConfirmDelete() {
			const c = this.pendingDelete
			if (!c || this.deleting) return
			this.deleting = true
			uni.showLoading({ title: '删除中', mask: true })
			try {
				await customerApi.remove(c.id)
				uni.hideLoading()
				uni.showToast({ title: '删除成功', icon: 'success' })
				// 本地移除
				const idx = this.list.findIndex((x) => x.id === c.id)
				if (idx >= 0) this.list.splice(idx, 1)
				this.total = Math.max(0, this.total - 1)
				// 关闭弹窗并清理待删除对象
				this.confirmVisible = false
				this.pendingDelete = null
			} catch (e) {
				uni.hideLoading()
				console.warn('delete error', e)
			} finally {
				this.deleting = false
			}
		},
			/** 保存（新增或更新） */
			async handleSave() {
				if (this.saving) return
				if (!this.form.name || !this.form.name.trim()) {
					return uni.showToast({ title: '请输入客户名称', icon: 'none' })
				}
				this.saving = true
				uni.showLoading({ title: '保存中', mask: true })
				try {
					const payload = { ...this.form }
					if (this.editMode) {
						await customerApi.update(this.editingId, payload)
						// 本地同步更新
						const idx = this.list.findIndex((x) => x.id === this.editingId)
						if (idx >= 0) {
							this.list.splice(idx, 1, { ...this.list[idx], ...payload })
						}
					} else {
						const created = await customerApi.create(payload)
						if (created && created.id) {
							this.list.unshift(created)
							this.total += 1
						} else {
							// 后端未返回完整对象时刷新列表
							this.refresh()
						}
					}
					uni.hideLoading()
					this.formVisible = false
					uni.showToast({ title: '保存成功', icon: 'success' })
				} catch (e) {
					uni.hideLoading()
					console.warn('save error', e)
				} finally {
					this.saving = false
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

	/* 右侧操作按钮（PageHeader 插槽内容，仍由本页控制样式） */
	.nav-action {
		font-size: 28rpx;
		color: #096DD9;
		padding: 8rpx 20rpx;
		background: #E6F7FF;
		border-radius: 24rpx;
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
	.cust-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.cust-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.cust-name {
		font-size: 32rpx;
		font-weight: 700;
		color: #303133;
		flex: 1;
		margin-right: 16rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.level-tag {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.level-vip { background: #FFF7E6; color: #D48806; }
	.level-normal { background: #F4F4F5; color: #5C6370; }

	.cust-row {
		display: flex;
		align-items: center;
		margin-bottom: 8rpx;
	}
	.row-label {
		font-size: 24rpx;
		color: #5C6370;
		width: 140rpx;
	}
	.row-val {
		font-size: 26rpx;
		color: #303133;
		flex: 1;
	}
	.row-val.phone {
		color: #096DD9;
	}

	.cust-footer {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 16rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid #F5F7FA;
	}
	.industry-tag {
		font-size: 22rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
	}
	.industry-tag.placeholder {
		color: #767E8A;
		background: #F4F4F5;
	}
	.project-count {
		font-size: 22rpx;
		color: #5C6370;
	}

	/* 空状态 */
	.empty {
		padding: 160rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	.empty-icon {
		font-size: 96rpx;
		margin-bottom: 24rpx;
	}
	.empty-text {
		display: block;
	}
	.load-more {
		text-align: center;
		color: #767E8A;
		font-size: 24rpx;
		padding: 24rpx 0;
	}
	.bottom-safe { height: calc(40rpx + env(safe-area-inset-bottom)); }

	/* 表单字段样式（FormDialog 插槽内容，仍由本页控制） */
	.form-item {
		margin-bottom: 24rpx;
	}
	.form-label {
		display: block;
		font-size: 26rpx;
		color: #606266;
		margin-bottom: 12rpx;
	}
	.req {
		color: #D4380D;
		margin-left: 4rpx;
	}
	.form-input {
		width: 100%;
		height: 72rpx;
		padding: 0 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.form-textarea {
		width: 100%;
		height: 140rpx;
		padding: 16rpx 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.level-picker {
		display: flex;
		gap: 16rpx;
	}
	.level-option {
		flex: 1;
		text-align: center;
		padding: 14rpx 0;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 8rpx;
		border: 2rpx solid transparent;
	}
	.level-option.active {
		color: #096DD9;
		background: #E6F7FF;
		border-color: #096DD9;
		font-weight: 600;
	}
	.level-option.opt-vip.active {
		color: #D48806;
		background: #FFF7E6;
		border-color: #D48806;
	}
	/* 注：原 .popup-mask/.popup/.popup-title/.popup-scroll/.popup-actions/.btn-cancel/.btn-confirm
	   样式已迁移至 FormDialog 组件，避免重复 */
</style>
