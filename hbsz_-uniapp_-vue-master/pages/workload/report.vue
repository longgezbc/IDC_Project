<!--
  工作量填报页（工程师视角）
  业务用途：项目组授权成员选择项目与日期后，按工作量清单逐项填报当日完成数量，
           提交后进入待审核（pending）状态，由项目经理审核。
  审核语义：重复提交同一（清单项+日期+人员）时，pending/rejected 记录覆盖重置回待审核，
           approved 记录拒绝修改（需 PM 驳回后重新填报）。
  数据来源：projectApi.getList（项目选择）；workloadApi.listItems（清单+实时统计）；
           workloadApi.saveRecords（批量保存，填报人固定为当前登录用户）。
  导航特性：非 tabBar 页；支持下拉刷新；清单为空时展示空态。
-->
<template>
	<view class="page">
		<!-- 头部 -->
		<view class="header">
			<text class="header-title">工作量填报</text>
		</view>

		<!-- 筛选卡片：项目 + 日期 -->
		<view class="card filter-card">
			<view class="filter-row">
				<text class="filter-label">项目</text>
				<picker
					class="filter-picker"
					:range="projectNames"
					@change="onProjectChange"
				>
					<view class="picker-value">
						<text :class="['picker-text', !selectedProjectId && 'picker-placeholder']">
							{{ selectedProjectName || '请选择项目' }}
						</text>
						<text class="picker-arrow">▾</text>
					</view>
				</picker>
			</view>
			<view class="filter-row">
				<text class="filter-label">日期</text>
				<picker class="filter-picker" mode="date" :value="recordDate" @change="onDateChange">
					<view class="picker-value">
						<text class="picker-text">{{ recordDate }}</text>
						<text class="picker-arrow">▾</text>
					</view>
				</picker>
			</view>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && items.length === 0">
			<view class="card" v-for="i in 4" :key="'sk' + i">
				<view class="sk-line sk-w-full"></view>
				<view class="sk-line sk-w240"></view>
				<view class="sk-line sk-w160"></view>
			</view>
		</view>

		<!-- 清单填报列表 -->
		<template v-else>
			<view v-if="items.length" class="list-section">
				<view class="section-tip">
					<text>填写当日完成数量后点击提交，提交后进入待审核状态</text>
				</view>
				<view v-for="item in items" :key="item.id" class="card item-card">
					<view class="item-head">
						<text class="item-category">{{ item.category || '未分类' }}</text>
						<text class="item-unit">单位：{{ item.unit || '-' }}</text>
					</view>
					<view class="item-scope">{{ item.workScope || '-' }}</view>
					<view class="item-stats">
						<view class="stat">
							<text class="stat-num">{{ item.completedQuantity || 0 }}</text>
							<text class="stat-label">已完{{ item.unit || '' }}</text>
						</view>
						<view class="stat">
							<text class="stat-num warn">{{ item.remainingQuantity || 0 }}</text>
							<text class="stat-label">剩余{{ item.unit || '' }}</text>
						</view>
						<view class="stat">
							<text class="stat-num blue">{{ item.todayQuantity || 0 }}</text>
							<text class="stat-label">今日已填</text>
						</view>
					</view>
					<view class="item-input-row">
						<text class="input-label">当日完成</text>
						<input
							class="qty-input"
							type="digit"
							v-model="item.quantityInput"
							placeholder="0"
							placeholder-class="qty-placeholder"
						/>
						<text class="input-unit">{{ item.unit || '' }}</text>
					</view>
				</view>
			</view>

			<!-- 空态 -->
			<view v-else class="empty">
				<view class="empty-icon" aria-hidden="true">
					<image class="svg-icon-lg" src="/static/icon/填报.svg" mode="aspectFit" />
				</view>
				<text>{{ selectedProjectId ? '该项目暂无工作量清单' : '请先选择项目' }}</text>
			</view>
		</template>

		<!-- 提交按钮 -->
		<view class="submit-bar" v-if="items.length">
			<button class="submit-btn" :disabled="submitting" @click="submit">
				{{ submitting ? '提交中...' : '提交填报' }}
			</button>
		</view>
	</view>
</template>

<script>
	import { projectApi } from '@/api/project'
	import { workloadApi } from '@/api/workload'
	import { userStore } from '@/store/user'

	/** 今日日期字符串 YYYY-MM-DD（本地时区） */
	function today() {
		const d = new Date()
		const m = String(d.getMonth() + 1).padStart(2, '0')
		const day = String(d.getDate()).padStart(2, '0')
		return `${d.getFullYear()}-${m}-${day}`
	}

	export default {
		data() {
			return {
				projectList: [],        // 可选项目列表
				selectedProjectId: null, // 当前选中项目ID
				selectedProjectName: '', // 当前选中项目名
				recordDate: today(),     // 填报日期（默认今天）
				items: [],               // 工作量清单（含 quantityInput 输入态）
				loading: false,          // 清单加载中
				submitting: false        // 提交中（防重复点击）
			}
		},
		computed: {
			/** 项目下拉展示名列表 */
			projectNames() {
				return this.projectList.map((p) => p.name || p.projectName || `项目${p.id}`)
			}
		},
		onLoad() {
			this.loadProjects()
		},
		onPullDownRefresh() {
			const done = () => uni.stopPullDownRefresh()
			this.loadProjects().then(() => {
				if (this.selectedProjectId) return this.loadItems().then(done)
				done()
			}).catch(done)
		},
		methods: {
			/** 加载项目下拉数据（取前 100 个进行中项目） */
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
			/** 项目切换：记录选择并加载清单 */
			onProjectChange(e) {
				const idx = Number(e.detail.value)
				const project = this.projectList[idx]
				if (!project) return
				this.selectedProjectId = project.id
				this.selectedProjectName = project.name || project.projectName || ''
				this.loadItems()
			},
			/** 日期切换：重新加载清单（"今日已填"随统计日期变化） */
			onDateChange(e) {
				this.recordDate = e.detail.value
				if (this.selectedProjectId) this.loadItems()
			},
			/** 加载项目工作量清单（statisticsDate 决定"今日工作量"归属日） */
			async loadItems() {
				if (!this.selectedProjectId) return
				this.loading = true
				try {
					const res = await workloadApi.listItems({
						projectId: this.selectedProjectId,
						statisticsDate: this.recordDate
					})
					const list = Array.isArray(res) ? res : []
					// 挂载输入态字段（输入框内容与清单数据分离，避免污染展示）
					this.items = list.map((it) => ({ ...it, quantityInput: '' }))
				} catch (e) {
					console.warn('load items error', e)
					this.items = []
				} finally {
					this.loading = false
				}
			},
			/** 提交填报：仅提交填写了数量的清单项，填报人固定为当前用户 */
			async submit() {
				if (this.submitting) return
				const userId = userStore.state.userInfo && userStore.state.userInfo.id
				if (!userId) {
					return uni.showToast({ title: '登录信息失效，请重新登录', icon: 'none' })
				}
				// 收集有效输入：非空且为非负数字
				const records = []
				for (const it of this.items) {
					const raw = (it.quantityInput || '').trim()
					if (raw === '') continue
					const qty = Number(raw)
					if (isNaN(qty) || qty < 0) {
						return uni.showToast({ title: `「${(it.workScope || '').slice(0, 10)}」数量无效`, icon: 'none' })
					}
					records.push({ itemId: it.id, workerUserId: userId, quantity: qty })
				}
				if (!records.length) {
					return uni.showToast({ title: '请至少填写一项完成数量', icon: 'none' })
				}
				this.submitting = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await workloadApi.saveRecords({
						projectId: this.selectedProjectId,
						recordDate: this.recordDate,
						records
					})
					uni.showToast({ title: '提交成功，待审核', icon: 'success' })
					// 重新加载清单以刷新"今日已填"统计
					this.loadItems()
				} catch (e) {
					console.error('save records error', e)
				} finally {
					this.submitting = false
					uni.hideLoading()
				}
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		background: #F5F7FA;
		padding: 24rpx 24rpx 160rpx;
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
		max-width: 480rpx;
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

	/* 提示 */
	.section-tip {
		padding: 4rpx 8rpx 16rpx;
		font-size: 22rpx;
		color: #909399;
	}

	/* 清单项卡片 */
	.item-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 12rpx;
	}
	.item-category {
		font-size: 22rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
	}
	.item-unit {
		font-size: 22rpx;
		color: #909399;
	}
	.item-scope {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
		line-height: 1.5;
		margin-bottom: 20rpx;
	}
	.item-stats {
		display: flex;
		background: #F5F7FA;
		border-radius: 10rpx;
		padding: 16rpx 0;
		margin-bottom: 20rpx;
	}
	.stat {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.stat + .stat {
		border-left: 1rpx solid #EBEEF5;
	}
	.stat-num {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
	}
	.stat-num.warn { color: #FAAD14; }
	.stat-num.blue { color: #1890FF; }
	.stat-label {
		font-size: 20rpx;
		color: #909399;
		margin-top: 4rpx;
	}
	.item-input-row {
		display: flex;
		align-items: center;
	}
	.input-label {
		width: 130rpx;
		font-size: 26rpx;
		color: #5C6370;
		flex-shrink: 0;
	}
	.qty-input {
		flex: 1;
		height: 68rpx;
		background: #F5F7FA;
		border-radius: 10rpx;
		padding: 0 20rpx;
		font-size: 28rpx;
		color: #303133;
		text-align: right;
	}
	.qty-placeholder {
		color: #C0C4CC;
	}
	.input-unit {
		width: 90rpx;
		font-size: 24rpx;
		color: #909399;
		text-align: right;
		flex-shrink: 0;
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

	/* 底部提交栏 */
	.submit-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
		background: #FFFFFF;
		box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
	}
	.submit-btn {
		height: 84rpx;
		line-height: 84rpx;
		font-size: 30rpx;
		font-weight: 600;
		color: #FFFFFF;
		background: #1890FF;
		border-radius: 12rpx;
		border: none;
		margin: 0;
		padding: 0;
		&::after { border: none; }
		&[disabled] {
			background: #A0CFFF;
			color: #FFFFFF;
		}
	}
</style>
