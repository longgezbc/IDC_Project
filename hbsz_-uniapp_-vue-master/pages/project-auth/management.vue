<!--
  项目授权申请-审批流程页
  业务用途：双 Tab 管理「项目授权申请」全流程：
    - Tab 1 授权申请：当前用户发起的申请列表，支持状态筛选、触底分页、创建申请、点击展开详情；
      已通过的申请可查看授权关系。
    - Tab 2 待审批：仅 PM/admin 可见，展示待当前用户审批的申请，支持通过/驳回（弹窗输入备注）。
  数据来源：
    - projectAuthApi（createAuthRequest / listAuthRequests / pendingAuthRequests /
      approveAuthRequest / rejectAuthRequest / listActiveEngineers / listAssignments）；
    - projectApi.getList（项目选择）。
  导航特性：非 tabBar 页；支持下拉刷新；Tab 1 支持触底分页加载。
  权限：Tab 2 通过 hasPermission('project:auth:approve') 或 canTransferProject() 控制可见。
-->
<template>
	<view class="page">
		<!-- 顶部导航栏 -->
		<view class="nav-bar">
			<view class="nav-title">项目授权</view>
			<view v-if="activeTab === 'apply'" class="nav-action" role="button" @click="openCreate">+ 申请</view>
		</view>

		<!-- Tab 切换 -->
		<view class="tabs">
			<view class="tab" :class="{ active: activeTab === 'apply' }" role="button" @click="switchTab('apply')">授权申请</view>
			<view v-if="canApprove" class="tab" :class="{ active: activeTab === 'approve' }" role="button" @click="switchTab('approve')">待审批</view>
		</view>

		<!-- ============ Tab 1：授权申请 ============ -->
		<view v-if="activeTab === 'apply'">
			<!-- 状态筛选 -->
			<scroll-view scroll-x class="filter-bar" :show-scrollbar="false">
				<view class="filter-row">
					<view
						class="filter-chip"
						:class="{ active: statusFilter === f.value }"
						v-for="f in statusFilters"
						:key="f.value"
						role="button"
						@click="setStatusFilter(f.value)"
					>{{ f.label }}</view>
				</view>
			</scroll-view>

			<!-- 申请列表 -->
			<view class="list">
				<view v-if="applyLoading && applyList.length === 0" class="loading-tip">加载中...</view>
				<template v-else>
					<view
						class="req-card"
						v-for="r in applyList"
						:key="r.id"
						role="button"
						hover-class="uni-hover"
						:hover-stay-time="80"
						@click="toggleExpand(r.id)"
					>
						<view class="req-head">
							<view class="req-project">{{ r.projectName || ('项目#' + r.projectId) }}</view>
							<view class="status-tag" :class="'st-' + r.status">{{ statusText(r.status) }}</view>
						</view>
						<view class="req-row">
							<text class="row-label">目标工程师</text>
							<text class="row-val">{{ r.targetEngineerName || r.engineerName || '-' }}</text>
						</view>
						<view class="req-row">
							<text class="row-label">申请原因</text>
							<text class="row-val">{{ r.reason || '-' }}</text>
						</view>
						<view class="req-row">
							<text class="row-label">申请时间</text>
							<text class="row-val">{{ formatDateTime(r.createTime) }}</text>
						</view>

						<!-- 展开详情 -->
						<view v-if="expandedId === r.id" class="req-detail">
							<view v-if="r.scope" class="req-row">
								<text class="row-label">授权范围</text>
								<text class="row-val">{{ scopeText(r.scope) }}</text>
							</view>
							<view v-if="r.expireAt" class="req-row">
								<text class="row-label">期望过期</text>
								<text class="row-val">{{ formatDate(r.expireAt) }}</text>
							</view>
							<view v-if="r.applicantName" class="req-row">
								<text class="row-label">申请人</text>
								<text class="row-val">{{ r.applicantName }}</text>
							</view>
							<view v-if="r.approverName" class="req-row">
								<text class="row-label">审批人</text>
								<text class="row-val">{{ r.approverName }}</text>
							</view>
							<view v-if="r.approveTime" class="req-row">
								<text class="row-label">审批时间</text>
								<text class="row-val">{{ formatDateTime(r.approveTime) }}</text>
							</view>
							<view v-if="r.approveOpinion || r.opinion" class="req-row">
								<text class="row-label">审批意见</text>
								<text class="row-val">{{ r.approveOpinion || r.opinion }}</text>
							</view>

							<!-- 已通过可看授权关系 -->
							<view v-if="r.status === 'approved'" class="assignment-box">
								<view class="assignment-title">授权关系</view>
								<view class="req-row">
									<text class="row-label">授权状态</text>
									<text class="row-val">{{ r.assignmentActive === false ? '已失效' : '有效' }}</text>
								</view>
								<view v-if="r.assignmentExpireAt" class="req-row">
									<text class="row-label">授权到期</text>
									<text class="row-val">{{ formatDate(r.assignmentExpireAt) }}</text>
								</view>
								<view v-if="r.assignmentScope" class="req-row">
									<text class="row-label">授权范围</text>
									<text class="row-val">{{ scopeText(r.assignmentScope) }}</text>
								</view>
								<view v-if="!r.assignmentExpireAt && !r.assignmentScope && r.assignmentActive === undefined" class="assignment-tip">
									授权已生效，可在项目管理中查看详情
								</view>
							</view>
						</view>
					</view>

					<!-- 空状态 -->
					<view v-if="applyList.length === 0" class="empty">
						<view class="empty-icon">📝</view>
						<text class="empty-text">暂无申请记录</text>
					</view>
					<view v-if="applyLoading && applyList.length > 0" class="load-more">加载中...</view>
					<view v-else-if="applyNoMore && applyList.length > 0" class="load-more">— 没有更多了 —</view>
				</template>
			</view>
		</view>

		<!-- ============ Tab 2：待审批（仅 PM/admin） ============ -->
		<view v-if="activeTab === 'approve' && canApprove">
			<view class="list">
				<view v-if="pendingLoading && pendingList.length === 0" class="loading-tip">加载中...</view>
				<template v-else>
					<view
						class="req-card approve-card"
						v-for="r in pendingList"
						:key="r.id"
					>
						<view class="req-head">
							<view class="req-project">{{ r.projectName || ('项目#' + r.projectId) }}</view>
							<view class="status-tag st-pending">待审核</view>
						</view>
						<view class="req-row">
							<text class="row-label">申请人</text>
							<text class="row-val">{{ r.applicantName || '-' }}</text>
						</view>
						<view class="req-row">
							<text class="row-label">目标工程师</text>
							<text class="row-val">{{ r.targetEngineerName || r.engineerName || '-' }}</text>
						</view>
						<view class="req-row">
							<text class="row-label">申请原因</text>
							<text class="row-val">{{ r.reason || '-' }}</text>
						</view>
						<view v-if="r.scope" class="req-row">
							<text class="row-label">授权范围</text>
							<text class="row-val">{{ scopeText(r.scope) }}</text>
						</view>
						<view v-if="r.expireAt" class="req-row">
							<text class="row-label">期望过期</text>
							<text class="row-val">{{ formatDate(r.expireAt) }}</text>
						</view>
						<view class="req-row">
							<text class="row-label">申请时间</text>
							<text class="row-val">{{ formatDateTime(r.createTime) }}</text>
						</view>

						<!-- 操作按钮 -->
						<view class="card-actions">
							<button class="btn-reject" :disabled="submitting" @click.stop="openReject(r)">驳回</button>
							<button class="btn-approve" :disabled="submitting" @click.stop="openApprove(r)">通过</button>
						</view>
					</view>

					<!-- 空状态 -->
					<view v-if="pendingList.length === 0" class="empty">
						<view class="empty-icon">✅</view>
						<text class="empty-text">暂无待审批申请</text>
					</view>
				</template>
			</view>
		</view>

		<view class="bottom-safe"></view>

		<!-- ============ 创建申请表单弹窗 ============ -->
		<view v-if="formVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="formVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">创建授权申请</view>
				<scroll-view scroll-y class="popup-scroll">
					<!-- 项目选择 -->
					<view class="form-item">
						<text class="form-label">项目<text class="req">*</text></text>
						<picker :range="projectList" range-key="name" @change="onProjectChange">
							<view class="form-picker" :class="{ placeholder: form.projectIndex < 0 }">
								{{ form.projectIndex >= 0 ? (projectList[form.projectIndex].name || '-') : '请选择项目' }}
							</view>
						</picker>
					</view>
					<!-- 目标工程师 -->
					<view class="form-item">
						<text class="form-label">目标工程师<text class="req">*</text></text>
						<picker :range="engineerList" range-key="name" @change="onEngineerChange">
							<view class="form-picker" :class="{ placeholder: form.engineerIndex < 0 }">
								{{ form.engineerIndex >= 0 ? (engineerList[form.engineerIndex].name || '-') : '请选择工程师' }}
							</view>
						</picker>
					</view>
					<!-- 授权范围 -->
					<view class="form-item">
						<text class="form-label">授权范围<text class="req">*</text></text>
						<picker :range="scopeOptions" range-key="label" @change="onScopeChange">
							<view class="form-picker">
								{{ scopeLabel(form.scope) }}
							</view>
						</picker>
					</view>
					<!-- 过期时间 -->
					<view class="form-item">
						<text class="form-label">过期时间</text>
						<picker mode="date" :value="form.expireAt" @change="onDateChange">
							<view class="form-picker" :class="{ placeholder: !form.expireAt }">
								{{ form.expireAt || '可选，不填则长期有效' }}
							</view>
						</picker>
					</view>
					<!-- 申请原因 -->
					<view class="form-item">
						<text class="form-label">申请原因<text class="req">*</text></text>
						<textarea
							class="form-textarea"
							v-model="form.reason"
							aria-label="申请原因"
							placeholder="请简要说明申请原因（必填，200字以内）"
							placeholder-class="ph"
							maxlength="200"
							:auto-height="false"
						/>
						<view class="char-count">{{ (form.reason || '').length }}/200</view>
					</view>
				</scroll-view>
				<view class="popup-actions">
					<button class="btn-cancel" @click="formVisible=false">取消</button>
					<button class="btn-confirm" :loading="formSaving" :disabled="formSaving" @click="handleSubmit">提交申请</button>
				</view>
			</view>
		</view>

		<!-- ============ 审批备注弹窗 ============ -->
		<view v-if="remarkVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="remarkVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">{{ remarkMode === 'approve' ? '审批通过' : '驳回申请' }}</view>
				<view class="remark-hint">{{ remarkMode === 'approve' ? '可填写审批意见（选填）' : '请填写驳回理由（选填）' }}</view>
				<textarea
					class="form-textarea remark-textarea"
					v-model="remarkInput"
					aria-label="备注"
					placeholder="请输入备注"
					placeholder-class="ph"
					maxlength="200"
					:auto-height="false"
				/>
				<view class="popup-actions">
					<button class="btn-cancel" @click="remarkVisible=false">取消</button>
					<button class="btn-confirm" :class="{ 'btn-danger': remarkMode === 'reject' }" :loading="submitting" :disabled="submitting" @click="confirmRemark">确定</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { projectAuthApi } from '@/api/projectAuth'
	import { projectApi } from '@/api/project'
	import { canTransferProject, hasPermission } from '@/utils/permission'
	import { formatDate, formatDateTime } from '@/utils/format'
	import { userStore } from '@/store/user'

	export default {
		data() {
			return {
				activeTab: 'apply',                 // 当前 Tab：'apply' | 'approve'
				canApprove: hasPermission('project:auth:approve') || canTransferProject(), // 是否可见待审批 Tab

				/* ====== Tab 1：授权申请 ====== */
				statusFilter: '',                   // 状态筛选：'' | 'pending' | 'approved' | 'rejected'
				statusFilters: [
					{ label: '全部', value: '' },
					{ label: '待审核', value: 'pending' },
					{ label: '已通过', value: 'approved' },
					{ label: '已驳回', value: 'rejected' }
				],
				applyList: [],
				applyPage: 1,
				applySize: 10,
				applyTotal: 0,
				applyLoading: false,
				applyNoMore: false,
				applyLoaded: false,                 // 是否加载过（避免 onShow 重复首载）
				expandedId: null,                   // 当前展开详情的申请 ID

				/* ====== Tab 2：待审批 ====== */
				pendingList: [],
				pendingLoading: false,
				pendingLoaded: false,

				/* ====== 创建申请表单 ====== */
				formVisible: false,
				formSaving: false,                  // 防重复提交锁
				projectList: [],                    // 可选项目列表
				engineerList: [],                   // 可选工程师列表
				engineerLoaded: false,              // 工程师列表是否加载过（带缓存）
				form: this.emptyForm(),
				scopeOptions: [
					{ value: 'read', label: '只读' },
					{ value: 'read_write', label: '读写' }
				],

				/* ====== 审批备注弹窗 ====== */
				remarkVisible: false,
				remarkMode: '',                     // 'approve' | 'reject'
				remarkInput: '',
				pendingItem: null,                  // 当前正在审批的申请
				submitting: false                   // 防重复提交锁
			}
		},
		// 页面加载：首次拉取申请列表
		onLoad() {
			this.loadApplyList(true)
		},
		// 页面显示：从其他页返回时刷新当前 Tab 数据（仅当已加载过时）
		onShow() {
			if (this.activeTab === 'apply' && this.applyLoaded) this.loadApplyList(true)
			if (this.activeTab === 'approve' && this.pendingLoaded) this.loadPending()
		},
		// 下拉刷新：刷新当前 Tab
		onPullDownRefresh() {
			if (this.activeTab === 'apply') {
				this.loadApplyList(true).finally(() => uni.stopPullDownRefresh())
			} else {
				this.loadPending().finally(() => uni.stopPullDownRefresh())
			}
		},
		// 触底加载：仅 Tab 1 支持分页
		onReachBottom() {
			if (this.activeTab === 'apply') this.loadMoreApply()
		},
		methods: {
			formatDate,
			formatDateTime,

			/** 空表单结构（避免引用污染） */
			emptyForm() {
				return {
					projectIndex: -1,
					engineerIndex: -1,
					scope: 'read',
					expireAt: '',
					reason: ''
				}
			},

			/** 状态文案映射 */
			statusText(status) {
				const map = { pending: '待审核', approved: '已通过', rejected: '已驳回' }
				return map[status] || status || '-'
			},

			/** 授权范围文案映射 */
			scopeText(scope) {
				const map = { read: '只读', read_write: '读写', readonly: '只读', readwrite: '读写' }
				return map[scope] || scope || '-'
			},

			/** 授权范围 label（表单回显） */
			scopeLabel(value) {
				const opt = this.scopeOptions.find((o) => o.value === value)
				return opt ? opt.label : '请选择'
			},

			/** 切换 Tab：切到待审批时首次加载 */
			switchTab(tab) {
				if (this.activeTab === tab) return
				this.activeTab = tab
				if (tab === 'approve' && !this.pendingLoaded) {
					this.loadPending()
				}
			},

			/** 切换状态筛选：重置到第一页 */
			setStatusFilter(value) {
				if (this.statusFilter === value) return
				this.statusFilter = value
				this.expandedId = null
				this.loadApplyList(true)
			},

			/** 展开/收起详情 */
			toggleExpand(id) {
				this.expandedId = this.expandedId === id ? null : id
			},

			/** 当前用户 ID（用于「我的申请」过滤） */
			currentUserId() {
				return userStore.state.userInfo && userStore.state.userInfo.id
			},

			/**
			 * 加载我的申请列表（分页 + 状态筛选）
			 * @param {boolean} reset 是否重置到第一页
			 * @returns {Promise<boolean>} 是否加载成功
			 */
			async loadApplyList(reset = false) {
				if (this.applyLoading) return false
				this.applyLoading = true
				if (reset) {
					this.applyPage = 1
					this.applyList = []
					this.applyNoMore = false
				}
				try {
					const params = { pageNum: this.applyPage, pageSize: this.applySize }
					if (this.statusFilter) params.status = this.statusFilter
					const uid = this.currentUserId()
					if (uid) params.applicantId = uid
					const res = await projectAuthApi.listAuthRequests(params)
					// 兼容 {records,total}/{list,total}/数组 多种返回结构
					let list = []
					let total = 0
					if (Array.isArray(res)) {
						list = res
						total = res.length
					} else if (res) {
						list = Array.isArray(res.records) ? res.records : (Array.isArray(res.list) ? res.list : [])
						total = res.total || res.totalCount || 0
					}
					this.applyList = reset ? list : this.applyList.concat(list)
					this.applyTotal = total
					this.applyNoMore = this.applyList.length >= total
					this.applyLoaded = true
					return true
				} catch (e) {
					console.warn('load apply list error', e)
					uni.showToast({ title: '加载失败，请重试', icon: 'none' })
					return false
				} finally {
					this.applyLoading = false
				}
			},

			/** 触底加载更多申请 */
			async loadMoreApply() {
				if (this.applyLoading || this.applyNoMore) return
				const oldPage = this.applyPage
				this.applyPage += 1
				const ok = await this.loadApplyList(false)
				if (!ok) this.applyPage = oldPage // 失败回滚页码，防止跳页
			},

			/** 加载待审批列表 */
			async loadPending() {
				if (this.pendingLoading) return
				this.pendingLoading = true
				try {
					const res = await projectAuthApi.pendingAuthRequests()
					this.pendingList = Array.isArray(res) ? res : (res && Array.isArray(res.records) ? res.records : [])
					this.pendingLoaded = true
				} catch (e) {
					console.warn('load pending error', e)
					uni.showToast({ title: '加载失败，请重试', icon: 'none' })
				} finally {
					this.pendingLoading = false
				}
			},

			/** 加载项目列表（兼容数组 / {records,total} 两种返回） */
			async loadProjects() {
				if (this.projectList.length > 0) return
				try {
					const res = await projectApi.getList({ pageNum: 1, pageSize: 50 })
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

			/** 加载启用状态工程师（带缓存，仅首次拉取） */
			async loadEngineers() {
				if (this.engineerLoaded) return
				try {
					const res = await projectAuthApi.listActiveEngineers()
					this.engineerList = Array.isArray(res) ? res : []
					this.engineerLoaded = true
				} catch (e) {
					console.warn('load active engineers error', e)
					this.engineerList = []
					this.engineerLoaded = true
				}
			},

			/** 打开创建申请弹窗 */
			async openCreate() {
				this.form = this.emptyForm()
				this.formVisible = true
				// 并行加载项目与工程师列表
				await Promise.all([this.loadProjects(), this.loadEngineers()])
				if (this.engineerList.length === 0) {
					uni.showToast({ title: '暂无可选工程师', icon: 'none' })
				}
			},

			/** 项目选择变更 */
			onProjectChange(e) {
				const idx = Number(e.detail.value)
				if (isNaN(idx)) return
				this.form.projectIndex = idx
			},

			/** 工程师选择变更 */
			onEngineerChange(e) {
				const idx = Number(e.detail.value)
				if (isNaN(idx)) return
				this.form.engineerIndex = idx
			},

			/** 授权范围选择变更 */
			onScopeChange(e) {
				const idx = Number(e.detail.value)
				if (isNaN(idx)) return
				const opt = this.scopeOptions[idx]
				if (opt) this.form.scope = opt.value
			},

			/** 过期时间选择变更 */
			onDateChange(e) {
				this.form.expireAt = e.detail.value || ''
			},

			/** 提交创建申请 */
			async handleSubmit() {
				if (this.formSaving) return
				// 表单校验
				if (this.form.projectIndex < 0) {
					return uni.showToast({ title: '请选择项目', icon: 'none' })
				}
				if (this.form.engineerIndex < 0) {
					return uni.showToast({ title: '请选择目标工程师', icon: 'none' })
				}
				const reason = (this.form.reason || '').trim()
				if (!reason) {
					return uni.showToast({ title: '请填写申请原因', icon: 'none' })
				}
				if (reason.length > 200) {
					return uni.showToast({ title: '申请原因不超过200字', icon: 'none' })
				}
				const project = this.projectList[this.form.projectIndex]
				const engineer = this.engineerList[this.form.engineerIndex]
				if (!project || !project.id) {
					return uni.showToast({ title: '项目信息异常，请重新选择', icon: 'none' })
				}
				if (!engineer || !engineer.id) {
					return uni.showToast({ title: '工程师信息异常，请重新选择', icon: 'none' })
				}

				this.formSaving = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					// 组装申请参数：过期时间格式化为 ISO 字符串
					const payload = {
						projectId: project.id,
						targetEngineerId: engineer.id,
						scope: this.form.scope,
						reason
					}
					if (this.form.expireAt) {
						payload.expireAt = `${this.form.expireAt}T00:00:00`
					}
					await projectAuthApi.createAuthRequest(payload)
					uni.hideLoading()
					this.formVisible = false
					uni.showToast({ title: '申请已提交', icon: 'success' })
					// 刷新申请列表
					await this.loadApplyList(true)
				} catch (e) {
					uni.hideLoading()
					console.warn('create auth request error', e)
				} finally {
					this.formSaving = false
				}
			},

			/** 打开审批通过弹窗 */
			openApprove(item) {
				if (this.submitting) return
				this.pendingItem = item
				this.remarkMode = 'approve'
				this.remarkInput = ''
				this.remarkVisible = true
			},

			/** 打开驳回弹窗 */
			openReject(item) {
				if (this.submitting) return
				this.pendingItem = item
				this.remarkMode = 'reject'
				this.remarkInput = ''
				this.remarkVisible = true
			},

			/** 确认审批：通过 / 驳回 */
			async confirmRemark() {
				if (this.submitting || !this.pendingItem) return
				const item = this.pendingItem
				const opinion = (this.remarkInput || '').trim()
				this.submitting = true
				uni.showLoading({ title: '处理中', mask: true })
				try {
					if (this.remarkMode === 'approve') {
						await projectAuthApi.approveAuthRequest(item.id, { approve: true, opinion })
					} else if (this.remarkMode === 'reject') {
						await projectAuthApi.rejectAuthRequest(item.id, { opinion })
					}
					uni.hideLoading()
					this.remarkVisible = false
					uni.showToast({ title: this.remarkMode === 'approve' ? '已通过' : '已驳回', icon: 'success' })
					// 本地移除已处理的待审批项
					const idx = this.pendingList.findIndex((x) => x.id === item.id)
					if (idx >= 0) this.pendingList.splice(idx, 1)
				} catch (e) {
					uni.hideLoading()
					console.warn('approve/reject error', e)
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

	/* 顶部导航栏 */
	.nav-bar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 8rpx 4rpx 16rpx;
	}
	.nav-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
	}
	.nav-action {
		font-size: 28rpx;
		color: #096DD9;
		padding: 8rpx 20rpx;
		background: #E6F7FF;
		border-radius: 24rpx;
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

	/* 状态筛选 */
	.filter-bar {
		white-space: nowrap;
		width: 100%;
		margin-bottom: 16rpx;
	}
	.filter-row {
		display: inline-flex;
		gap: 16rpx;
		padding: 4rpx 0;
	}
	.filter-chip {
		display: inline-block;
		padding: 12rpx 28rpx;
		font-size: 26rpx;
		color: #606266;
		background: #FFFFFF;
		border-radius: 999rpx;
		flex-shrink: 0;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
	}
	.filter-chip.active {
		background: #1890FF;
		color: #FFFFFF;
		font-weight: 600;
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

	/* 申请卡片 */
	.req-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.req-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.req-project {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
		flex: 1;
		margin-right: 16rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.status-tag {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	/* 状态标签颜色：pending 黄色 / approved 绿色 / rejected 红色 */
	.st-pending { background: #FFF7E6; color: #D48806; }
	.st-approved { background: #F6FFED; color: #389E0D; }
	.st-rejected { background: #FFF1F0; color: #D4380D; }

	.req-row {
		display: flex;
		align-items: flex-start;
		margin-bottom: 8rpx;
	}
	.row-label {
		font-size: 24rpx;
		color: #5C6370;
		width: 160rpx;
		flex-shrink: 0;
	}
	.row-val {
		font-size: 26rpx;
		color: #303133;
		flex: 1;
		word-break: break-all;
	}

	/* 展开详情 */
	.req-detail {
		margin-top: 16rpx;
		padding-top: 16rpx;
		border-top: 1rpx dashed #E5E7EB;
	}

	/* 授权关系区块 */
	.assignment-box {
		margin-top: 16rpx;
		padding: 16rpx;
		background: #F6FFED;
		border-radius: 8rpx;
		border: 1rpx solid #D9F7BE;
	}
	.assignment-title {
		font-size: 24rpx;
		font-weight: 600;
		color: #389E0D;
		margin-bottom: 8rpx;
	}
	.assignment-tip {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 4rpx;
	}

	/* 待审批卡片操作按钮 */
	.approve-card {
		border-left: 6rpx solid #FAAD14;
	}
	.card-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 20rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid #F5F7FA;
	}
	.card-actions button {
		flex: 1;
		height: 76rpx;
		line-height: 76rpx;
		font-size: 28rpx;
		border-radius: 10rpx;
		border: none;
		margin: 0;
		padding: 0;
		&::after { border: none; }
		&[disabled] { opacity: 0.5; }
	}
	.btn-approve {
		color: #FFFFFF;
		background: #52C41A;
	}
	.btn-reject {
		color: #D4380D;
		background: #FFF1F0;
		border: 2rpx solid #FFCCC7;
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

	/* 弹窗（参考 customer/list.vue 结构） */
	.popup-mask {
		position: fixed;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 999;
	}
	.popup {
		width: 640rpx;
		max-height: 84vh;
		background: #FFFFFF;
		border-radius: 24rpx;
		padding: 32rpx;
		display: flex;
		flex-direction: column;
	}
	.popup-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #303133;
		text-align: center;
		margin-bottom: 24rpx;
	}
	.popup-scroll {
		max-height: 60vh;
	}
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
	.form-picker {
		width: 100%;
		height: 72rpx;
		line-height: 72rpx;
		padding: 0 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.form-picker.placeholder {
		color: #767E8A;
	}
	.form-textarea {
		width: 100%;
		height: 160rpx;
		padding: 16rpx 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.remark-textarea {
		height: 200rpx;
	}
	.char-count {
		text-align: right;
		font-size: 22rpx;
		color: #767E8A;
		margin-top: 6rpx;
	}
	.remark-hint {
		font-size: 24rpx;
		color: #5C6370;
		margin-bottom: 16rpx;
		text-align: center;
	}
	.popup-actions {
		display: flex;
		gap: 16rpx;
		margin-top: 16rpx;
	}
	.btn-cancel, .btn-confirm {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		border: none;
		margin: 0;
		&::after { border: none; }
	}
	.btn-cancel {
		background: #F5F7FA;
		color: #606266;
	}
	.btn-confirm {
		background: #1890FF;
		color: #FFFFFF;
		&[disabled] { background: #B3D9FF; color: #5C6370; }
	}
	.btn-confirm.btn-danger {
		background: #FF4D4F;
		&[disabled] { background: #FFB3B4; }
	}
	.ph { color: #767E8A; }
</style>
