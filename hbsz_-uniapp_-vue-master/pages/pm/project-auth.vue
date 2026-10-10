<!--
  项目授权转移页（PM 视角）
  业务用途：PM 集中管理项目授权——为项目「添加授权」工程师、「撤销」已有授权、
           「转移项目」给目标工程师（替换模式），并查看全部授权操作的审计日志。
  数据来源：projectAuthApi（assign/revoke/transfer/listProjectEngineers/listActiveEngineers/transferLogs）；
           projectApi.getList（项目选择下拉）。
  导航特性：非 tabBar 页；支持下拉刷新；操作日志 Tab 支持触底分页加载。
-->
<template>
	<view class="page">
		<!-- 头部 -->
		<view class="header">
			<text class="header-title">项目授权</text>
		</view>

		<!-- Tab 切换 -->
		<view class="tabs">
			<view class="tab" :class="{ active: activeTab === 'auth' }" role="button" @click="switchTab('auth')">项目授权</view>
			<view class="tab" :class="{ active: activeTab === 'logs' }" role="button" @click="switchTab('logs')">操作日志</view>
		</view>

		<!-- ============ Tab 1：项目授权 ============ -->
		<view v-if="activeTab === 'auth'">
			<!-- 项目选择（横向芯片） -->
			<view class="card">
				<view class="card-title">选择项目</view>
				<scroll-view scroll-x class="chip-scroll" :show-scrollbar="false">
					<view class="chip-row">
						<view
							class="chip"
							:class="{ active: p.id === selectedProjectId }"
							v-for="p in projectList"
							:key="p.id"
							@click="selectProject(p)"
						role="button"
					>{{ p.name || '-' }}</view>
						<view v-if="projectList.length === 0" class="chip-empty">暂无可选项目</view>
					</view>
				</scroll-view>
			</view>

			<!-- 已授权工程师 -->
			<view class="card">
				<view class="card-title">
					已授权工程师
					<text class="card-tip" v-if="assignedEngineers.length">{{ assignedEngineers.length }} 人</text>
				</view>

				<view v-if="!selectedProjectId" class="empty-inline">请先选择项目</view>
				<view v-else-if="assignedLoading" class="empty-inline">加载中...</view>
				<view v-else-if="assignedEngineers.length === 0" class="empty-inline">暂无授权工程师</view>

				<view
					v-for="e in assignedEngineers"
					:key="e.id"
					class="engineer-item"
				>
					<view class="engineer-avatar">{{ initialOf(e.name) }}</view>
					<view class="engineer-info">
						<view class="engineer-row">
							<text class="engineer-name">{{ e.name || '-' }}</text>
							<text class="btn-revoke" role="button" @click="openRevoke(e)">撤销</text>
						</view>
						<text class="engineer-time">授权于 {{ formatDate(e.assignedAt) }}</text>
						<text v-if="e.remark" class="engineer-remark">{{ e.remark }}</text>
					</view>
				</view>
			</view>

			<!-- 操作按钮 -->
			<view class="action-bar">
				<button
					class="btn btn-primary"
					:disabled="!selectedProjectId || submitting"
					@click="openAssignPicker"
				>添加授权</button>
				<button
					class="btn btn-outline"
					:disabled="!selectedProjectId || submitting"
					@click="openTransferPicker"
				>转移项目</button>
			</view>
		</view>

		<!-- ============ Tab 2：操作日志 ============ -->
		<view v-if="activeTab === 'logs'">
			<!-- 项目筛选 -->
			<view class="card">
				<view class="card-title">筛选项目</view>
				<scroll-view scroll-x class="chip-scroll" :show-scrollbar="false">
					<view class="chip-row">
						<view
							class="chip"
							:class="{ active: !logFilterProjectId }"
						@click="filterProject(null)"
						role="button"
					>全部</view>
						<view
							class="chip"
							:class="{ active: p.id === logFilterProjectId }"
							v-for="p in projectList"
							:key="p.id"
							@click="filterProject(p)"
						role="button"
					>{{ p.name || '-' }}</view>
					</view>
				</scroll-view>
			</view>

			<!-- 日志列表 -->
			<view
				v-for="log in logList"
				:key="log.id"
				class="card log-card"
			>
				<view class="log-head">
					<text class="log-project">{{ log.projectName || ('项目#' + log.projectId) }}</text>
					<text class="log-tag" :class="'tag-' + log.action">{{ actionText(log.action) }}</text>
				</view>

				<view class="log-flow">
					<text class="flow-label">{{ flowLabel(log) }}</text>
					<text class="flow-text">{{ flowText(log) }}</text>
				</view>

				<view class="log-meta">
					<text class="meta-operator">操作人：{{ log.operatorName || '-' }}</text>
					<text class="meta-time">{{ formatDateTime(log.createTime) }}</text>
				</view>

				<view v-if="log.remark" class="log-remark">{{ log.remark }}</view>
			</view>

			<!-- 状态提示 -->
			<view v-if="logLoading && logList.length === 0" class="empty">加载中...</view>
			<view v-else-if="logList.length === 0" class="empty">暂无操作日志</view>
			<view v-if="logLoading && logList.length > 0" class="load-more">加载中...</view>
			<view v-else-if="logNoMore && logList.length > 0" class="load-more">没有更多了</view>
		</view>

		<!-- ============ 工程师选择弹层（底部 sheet） ============ -->
		<view v-if="engineerPickerVisible" class="sheet-mask" @click="engineerPickerVisible = false">
			<view class="sheet" @click.stop>
				<view class="sheet-title">选择工程师</view>
				<scroll-view scroll-y class="sheet-list">
					<view v-if="activeEngineers.length === 0" class="sheet-empty">暂无可选工程师</view>
					<view
						v-for="e in activeEngineers"
						:key="e.id"
						class="sheet-item"
					@click="selectEngineer(e)"
					role="button"
					>
						<view class="engineer-name">{{ e.name }}</view>
						<view class="engineer-dept">{{ e.department || '' }}</view>
					</view>
				</scroll-view>
				<view class="sheet-cancel" role="button" @click="engineerPickerVisible = false">取消</view>
			</view>
		</view>

		<!-- ============ 备注输入弹窗 ============ -->
		<view v-if="remarkVisible" class="popup-mask" @click="remarkVisible = false">
			<view class="popup" @click.stop>
				<view class="popup-title">{{ remarkTitle }}</view>
				<textarea v-model="remarkInput" aria-label="备注" placeholder="请输入备注（选填）" placeholder-class="ph" maxlength="200" />
				<view class="popup-actions">
					<button @click="remarkVisible = false">取消</button>
					<button :disabled="submitting" @click="confirmRemark">确定</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { projectAuthApi } from '@/api/projectAuthorization'
	import { projectApi } from '@/api/project'
	import { formatDate, formatDateTime } from '@/utils/format'
	import { canTransferProject } from '@/utils/permission'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				hasPermission: true,
				activeTab: 'auth',          // 当前 Tab：'auth' | 'logs'

				// 项目列表
				projectList: [],
				selectedProjectId: '',      // 当前选中项目（授权 Tab）

				// 已授权工程师
				assignedEngineers: [],
				assignedLoading: false,

				// 工程师选择弹层
				activeEngineers: [],
				engineerPickerVisible: false,
				pickerMode: '',             // 'assign' | 'transfer'

				// 备注弹窗
				remarkVisible: false,
				remarkTitle: '',
				remarkInput: '',
				remarkContext: '',          // 'assign' | 'transfer' | 'revoke'
				pendingEngineer: null,      // assign/transfer 时选中的工程师
				revokeTarget: null,         // revoke 时目标工程师

				// 提交锁
				submitting: false,

				// 操作日志
				logList: [],
				logLoading: false,
				logPageNum: 1,
				logPageSize: 10,
				logTotal: 0,
				logFilterProjectId: '',
				logLoaded: false             // 日志是否加载过（避免切换 Tab 重复首载）
			}
		},
		computed: {
			/** 日志是否已全部加载 */
			logNoMore() {
				return this.logList.length >= this.logTotal && this.logList.length > 0
			}
		},
		// 页面加载：权限校验 + 加载项目列表
		onLoad() {
			this.hasPermission = canTransferProject()
			if (!this.hasPermission) {
				uni.showToast({ title: '无权限访问', icon: 'none' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 1000)
				return
			}
			this.loadProjects()
		},
		// 页面显示：若已选项目则刷新授权列表（从其他页返回时刷新）
		onShow() {
			if (!this.hasPermission) return
			if (this.selectedProjectId) this.loadAssignedEngineers()
		},
		// 下拉刷新：刷新当前 Tab
		onPullDownRefresh() {
			if (this.activeTab === 'auth') {
				this.refreshAuth()
			} else {
				this.refreshLogs()
			}
		},
		// 触底加载：日志分页
		onReachBottom() {
			if (this.activeTab === 'logs') this.loadMoreLogs()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			formatDate,
			formatDateTime,

			/** 取姓名首字符作为头像展示 */
			initialOf(name) {
				if (!name) return '?'
				return String(name).charAt(0).toUpperCase()
			},

			/** 切换 Tab：切换到日志时首次加载 */
			switchTab(tab) {
				if (this.activeTab === tab) return
				this.activeTab = tab
				if (tab === 'logs' && !this.logLoaded) {
					this.loadLogs(true)
				}
			},

			/** 加载项目列表（兼容数组 / {records,total} 两种返回） */
			async loadProjects() {
				try {
					const res = await projectApi.getList({ page: 1, size: 50 })
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

			/** 选择项目：更新选中并加载授权工程师 */
			selectProject(p) {
				if (!p || p.id === this.selectedProjectId) return
				this.selectedProjectId = p.id
				this.loadAssignedEngineers()
			},

			/** 加载当前项目的已授权工程师 */
			async loadAssignedEngineers() {
				if (!this.selectedProjectId) return
				this.assignedLoading = true
				try {
					const res = await projectAuthApi.listProjectEngineers(this.selectedProjectId)
					this.assignedEngineers = Array.isArray(res) ? res : []
				} catch (e) {
					console.warn('load assigned error', e)
					this.assignedEngineers = []
				} finally {
					this.assignedLoading = false
				}
			},

			/** 加载启用状态工程师（带缓存，仅首次拉取） */
			async loadActiveEngineers() {
				if (this.activeEngineers.length > 0) return
				try {
					const res = await projectAuthApi.listActiveEngineers()
					this.activeEngineers = Array.isArray(res) ? res : []
				} catch (e) {
					console.warn('load active engineers error', e)
					this.activeEngineers = []
				}
			},

			/** 打开添加授权选择器 */
			async openAssignPicker() {
				if (!this.selectedProjectId) {
					return uni.showToast({ title: '请先选择项目', icon: 'none' })
				}
				await this.loadActiveEngineers()
				if (this.activeEngineers.length === 0) {
					return uni.showToast({ title: '暂无可选工程师', icon: 'none' })
				}
				this.pickerMode = 'assign'
				this.engineerPickerVisible = true
			},

			/** 打开转移项目选择器 */
			async openTransferPicker() {
				if (!this.selectedProjectId) {
					return uni.showToast({ title: '请先选择项目', icon: 'none' })
				}
				await this.loadActiveEngineers()
				if (this.activeEngineers.length === 0) {
					return uni.showToast({ title: '暂无可选工程师', icon: 'none' })
				}
				this.pickerMode = 'transfer'
				this.engineerPickerVisible = true
			},

			/** 选中工程师：按模式走不同流程 */
			selectEngineer(e) {
				if (!e) return
				this.engineerPickerVisible = false
				this.pendingEngineer = e
				if (this.pickerMode === 'assign') {
					// 添加授权：直接弹备注
					this.remarkContext = 'assign'
					this.remarkTitle = '添加授权'
					this.remarkInput = ''
					this.remarkVisible = true
				} else if (this.pickerMode === 'transfer') {
					// 转移项目：先确认再弹备注
					this.confirmTransfer(e)
				}
			},

			/** 转移确认弹窗 */
			confirmTransfer(e) {
				uni.showModal({
					title: '确认转移',
					content: `确定将项目转移给 ${e.name || ''}？`,
					confirmText: '继续',
					confirmColor: '#FA8C16',
					success: (res) => {
						if (res.confirm) {
							this.remarkContext = 'transfer'
							this.remarkTitle = '转移项目备注'
							this.remarkInput = ''
							this.remarkVisible = true
						}
					}
				})
			},

			/** 打开撤销流程：先确认再弹备注 */
			openRevoke(e) {
				uni.showModal({
					title: '确认撤销',
					content: `确定撤销 ${e.name || ''} 的授权？`,
					confirmText: '继续',
					confirmColor: '#FF4D4F',
					success: (res) => {
						if (res.confirm) {
							this.revokeTarget = e
							this.remarkContext = 'revoke'
							this.remarkTitle = '撤销备注'
							this.remarkInput = ''
							this.remarkVisible = true
						}
					}
				})
			},

			/** 备注确认：按上下文分发 */
			confirmRemark() {
				const remark = (this.remarkInput || '').trim()
				if (this.remarkContext === 'assign') {
					this.doAssign(this.pendingEngineer, remark)
				} else if (this.remarkContext === 'transfer') {
					this.doTransfer(this.pendingEngineer, remark)
				} else if (this.remarkContext === 'revoke') {
					this.doRevoke(this.revokeTarget, remark)
				}
			},

			/** 执行添加授权 */
			async doAssign(engineer, remark) {
				if (this.submitting || !engineer) return
				this.submitting = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await projectAuthApi.assign(this.selectedProjectId, { toEngineerId: engineer.id, remark })
					this.remarkVisible = false
					uni.showToast({ title: '授权成功', icon: 'success' })
					await this.loadAssignedEngineers()
				} catch (e) {
					console.error('assign error', e)
				} finally {
					this.submitting = false
					uni.hideLoading()
				}
			},

			/** 执行转移项目 */
			async doTransfer(engineer, remark) {
				if (this.submitting || !engineer) return
				this.submitting = true
				uni.showLoading({ title: '转移中', mask: true })
				try {
					await projectAuthApi.transfer(this.selectedProjectId, { toEngineerId: engineer.id, remark })
					this.remarkVisible = false
					uni.showToast({ title: '转移成功', icon: 'success' })
					await this.loadAssignedEngineers()
				} catch (e) {
					console.error('transfer error', e)
				} finally {
					this.submitting = false
					uni.hideLoading()
				}
			},

			/** 执行撤销授权 */
			async doRevoke(engineer, remark) {
				if (this.submitting || !engineer) return
				this.submitting = true
				uni.showLoading({ title: '撤销中', mask: true })
				try {
					await projectAuthApi.revoke(this.selectedProjectId, engineer.id, remark)
					this.remarkVisible = false
					uni.showToast({ title: '已撤销', icon: 'success' })
					await this.loadAssignedEngineers()
				} catch (e) {
					console.error('revoke error', e)
				} finally {
					this.submitting = false
					uni.hideLoading()
				}
			},

			/** 刷新授权 Tab */
			refreshAuth() {
				this.loadProjects().then(() => {
					if (this.selectedProjectId) return this.loadAssignedEngineers()
				}).finally(() => uni.stopPullDownRefresh())
			},

			/** 操作：动作文案映射 */
			actionText(action) {
				const map = { assign: '直接授权', transfer: '转移', revoke: '撤销', auth_request: '授权申请', approve: '审批通过', reject: '审批驳回', extend: '延期' }
				return map[action] || action || '-'
			},

			/** 日志流向标签 */
			flowLabel() {
				return '工程师'
			},

			/** 日志流向内容：transfer 显示 from→to；assign 显示授权给 to；revoke 显示被撤销者 */
			flowText(log) {
				const from = log.fromEngineerName || ''
				const to = log.toEngineerName || ''
				if (log.action === 'transfer') {
					return `${from || '-'} → ${to || '-'}`
				}
				if (log.action === 'assign') {
					return `授权给 ${to || '-'}`
				}
				if (log.action === 'revoke') {
					return `撤销 ${from || to || '-'}`
				}
				return from || to || '-'
			},

			/** 日志按项目筛选 */
			filterProject(p) {
				const id = p ? p.id : ''
				if (id === this.logFilterProjectId) return
				this.logFilterProjectId = id
				this.loadLogs(true)
			},

			/** 加载日志（reset 时回到第一页）
			 * @param {boolean} reset 是否重置到第一页
			 * @returns {Promise<boolean>} 是否加载成功（供 loadMoreLogs 判断是否回滚页码）
			 */
			async loadLogs(reset) {
				if (this.logLoading) return false
				this.logLoading = true
				if (reset) {
					this.logPageNum = 1
					this.logList = []
				}
				try {
					const params = {
						pageNum: this.logPageNum,
						pageSize: this.logPageSize
					}
					if (this.logFilterProjectId) params.projectId = this.logFilterProjectId
					const res = await projectAuthApi.transferLogs(params)
					let records = []
					let total = 0
					if (Array.isArray(res)) {
						records = res
						total = res.length
					} else if (res) {
						records = Array.isArray(res.records) ? res.records : []
						total = res.total || 0
					}
					this.logList = reset ? records : this.logList.concat(records)
					this.logTotal = total
					this.logLoaded = true
					return true
				} catch (e) {
					console.warn('load logs error', e)
					return false
				} finally {
					this.logLoading = false
				}
			},

			/** 触底加载更多日志 */
			async loadMoreLogs() {
				if (this.logLoading) return
				if (this.logList.length >= this.logTotal) return
				// 记录原页码：加载失败时回滚，避免下次点击下一页时跳页
				const oldPageNum = this.logPageNum
				this.logPageNum += 1
				const ok = await this.loadLogs(false)
				if (!ok) {
					// 加载失败，回滚页码到原值，防止跳页
					this.logPageNum = oldPageNum
				}
			},

			/** 刷新日志 Tab */
			refreshLogs() {
				this.loadLogs(true).finally(() => uni.stopPullDownRefresh())
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
		padding: 12rpx 4rpx 20rpx;
	}
	.header-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
	}

	/* Tab 切换 */
	.tabs {
		display: flex;
		background: #FFFFFF;
		border-radius: 16rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
		overflow: hidden;
	}
	.tab {
		flex: 1;
		text-align: center;
		padding: 24rpx 0;
		font-size: 28rpx;
		color: #5C6370;
		position: relative;
	}
	.tab.active {
		color: #096DD9;
		font-weight: 700;
	}
	.tab.active::after {
		content: '';
		position: absolute;
		left: 50%;
		bottom: 0;
		width: 64rpx;
		height: 4rpx;
		background: #1890FF;
		border-radius: 2rpx;
		transform: translateX(-50%);
	}

	/* 通用卡片 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.card-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 20rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.card-tip {
		font-size: 22rpx;
		font-weight: 400;
		color: #5C6370;
	}

	/* 项目芯片（横向滚动） */
	.chip-scroll {
		white-space: nowrap;
		width: 100%;
	}
	.chip-row {
		display: inline-flex;
		gap: 16rpx;
		padding: 4rpx 0;
	}
	.chip {
		display: inline-block;
		padding: 14rpx 28rpx;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 999rpx;
		flex-shrink: 0;
	}
	.chip.active {
		background: #1890FF;
		color: #FFFFFF;
		font-weight: 600;
	}
	.chip-empty {
		display: inline-block;
		font-size: 24rpx;
		color: #767E8A;
		padding: 14rpx 0;
	}

	.empty-inline {
		padding: 24rpx 0;
		text-align: center;
		color: #5C6370;
		font-size: 24rpx;
	}

	/* 已授权工程师 */
	.engineer-item {
		display: flex;
		align-items: flex-start;
		padding: 24rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.engineer-item:last-child {
		border-bottom: none;
		padding-bottom: 0;
	}
	.engineer-item:first-child {
		padding-top: 0;
	}
	.engineer-avatar {
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
	.engineer-info {
		flex: 1;
		min-width: 0;
		margin-left: 16rpx;
	}
	.engineer-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.engineer-name {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
	}
	.engineer-time {
		display: block;
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 6rpx;
	}
	.engineer-remark {
		display: block;
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 8rpx;
		padding: 12rpx 16rpx;
		line-height: 1.5;
	}

	/* 撤销按钮 */
	.btn-revoke {
		font-size: 24rpx;
		color: #D4380D;
		padding: 6rpx 16rpx;
		flex-shrink: 0;
	}

	/* 操作按钮区 */
	.action-bar {
		display: flex;
		gap: 20rpx;
		margin-top: 8rpx;
	}
	.btn {
		flex: 1;
		height: 84rpx;
		line-height: 84rpx;
		font-size: 30rpx;
		font-weight: 600;
		border-radius: 12rpx;
		border: none;
		margin: 0;
		padding: 0;
		&::after { border: none; }
		&[disabled] { opacity: 0.5; }
	}
	.btn-primary {
		color: #FFFFFF;
		background: #1890FF;
	}
	.btn-outline {
		color: #096DD9;
		background: #E6F7FF;
		border: 2rpx solid #1890FF;
	}

	/* 日志卡片 */
	.log-card {
		// 复用 .card 样式
	}
	.log-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}
	.log-project {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		margin-right: 16rpx;
	}
	.log-tag {
		font-size: 22rpx;
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		flex-shrink: 0;
	}
	.tag-assign { background: #E6F7FF; color: #096DD9; }
	.tag-transfer { background: #FFF7E6; color: #D48806; }
	.tag-revoke { background: #FFF1F0; color: #D4380D; }

	.log-flow {
		display: flex;
		align-items: center;
		padding: 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		margin-bottom: 16rpx;
	}
	.flow-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-right: 16rpx;
		flex-shrink: 0;
	}
	.flow-text {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
		font-weight: 500;
	}

	.log-meta {
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 22rpx;
		color: #5C6370;
	}
	.meta-operator {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		margin-right: 16rpx;
	}
	.log-remark {
		margin-top: 16rpx;
		padding: 16rpx;
		background: #FFFBE6;
		border-radius: 8rpx;
		font-size: 24rpx;
		color: #606266;
		line-height: 1.5;
	}

	/* 空态与加载更多 */
	.empty {
		padding: 120rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	.load-more {
		text-align: center;
		color: #767E8A;
		font-size: 24rpx;
		padding: 24rpx 0;
	}

	/* 工程师选择底部 sheet */
	.sheet-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 1000;
		display: flex;
		align-items: flex-end;
	}
	.sheet {
		width: 100%;
		background: #FFFFFF;
		border-radius: 24rpx 24rpx 0 0;
		padding: 32rpx 24rpx;
		padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
		animation: slideUp 0.25s ease;
	}
	@keyframes slideUp {
		from { transform: translateY(100%); }
		to { transform: translateY(0); }
	}
	.sheet-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #303133;
		text-align: center;
		margin-bottom: 24rpx;
	}
	.sheet-list {
		max-height: 720rpx;
	}
	.sheet-empty {
		padding: 60rpx 0;
		text-align: center;
		color: #767E8A;
		font-size: 26rpx;
	}
	.sheet-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 28rpx 20rpx;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.sheet-item:last-child {
		border-bottom: none;
	}
	.sheet-item .engineer-name {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
	}
	.engineer-dept {
		font-size: 24rpx;
		color: #5C6370;
	}
	.sheet-cancel {
		margin-top: 24rpx;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		background: #F5F7FA;
		border-radius: 12rpx;
		font-size: 30rpx;
		color: #606266;
	}

	/* 备注弹窗 */
	.popup-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 1100;
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
	.ph {
		color: #767E8A;
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
		&[disabled] { opacity: 0.5; }
	}
	.popup-actions button:first-child {
		color: #606266;
		background: #F5F7FA;
	}
	.popup-actions button:last-child {
		color: #FFFFFF;
		background: #1890FF;
	}
</style>
