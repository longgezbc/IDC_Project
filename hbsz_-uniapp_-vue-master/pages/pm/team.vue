<!--
  项目组管理页
  业务用途：集中管理项目组成员——添加成员、移除成员、转让组长，
           并查看当前用户参与的全部项目组。
  数据来源：projectTeamApi（addMember/listMembers/removeMember/transferLeader/myTeams）；
           projectApi.getList（项目下拉）；userApi（用户下拉，listAll 暂未实现，降级 getProfile）。
  导航特性：非 tabBar 页；双 Tab 结构；支持下拉刷新；Tab 1 成员列表支持触底分页。
  权限要求：Tab 1 写操作需 project:team:manage（PM/admin）；Tab 2 登录用户均可访问。
-->
<template>
	<view class="page">
		<!-- 顶部标题栏 -->
		<view class="nav-bar">
			<view class="nav-title">项目组管理</view>
			<view v-if="activeTab === 'members' && canManage" class="nav-action" role="button" @click="openAddForm">+ 添加成员</view>
		</view>

		<!-- Tab 切换 -->
		<view class="tabs">
			<view class="tab" :class="{ active: activeTab === 'members' }" role="button" @click="switchTab('members')">项目组成员管理</view>
			<view class="tab" :class="{ active: activeTab === 'my' }" role="button" @click="switchTab('my')">我参与的项目组</view>
		</view>

		<!-- ============ Tab 1：项目组成员管理 ============ -->
		<view v-if="activeTab === 'members'">
			<!-- 顶部筛选 -->
			<view class="filter-bar">
				<picker class="filter-picker" :range="projectFilterOptions" range-key="name" :value="projectFilterIndex" @change="onProjectFilterChange">
					<view class="picker-text">{{ projectFilterText }} ▾</view>
				</picker>
				<picker class="filter-picker" :range="roleFilterOptions" range-key="label" :value="roleFilterIndex" @change="onRoleFilterChange">
					<view class="picker-text">{{ roleFilterText }} ▾</view>
				</picker>
				<picker class="filter-picker" :range="activeFilterOptions" range-key="label" :value="activeFilterIndex" @change="onActiveFilterChange">
					<view class="picker-text">{{ activeFilterText }} ▾</view>
				</picker>
			</view>

			<!-- 成员列表 -->
			<view class="list">
				<view v-if="membersLoading && memberList.length === 0" class="loading-tip">加载中...</view>
				<template v-else>
					<view
						v-for="m in memberList"
						:key="m.id"
						class="member-card"
						hover-class="uni-hover"
						:hover-stay-time="80"
					>
						<view class="member-head">
							<view class="member-name">{{ m.realName || m.username || ('用户#' + m.userId) }}</view>
							<view class="role-tag" :class="m.roleInTeam === 'leader' ? 'tag-leader' : 'tag-member'">{{ m.roleInTeam === 'leader' ? '负责人' : '成员' }}</view>
						</view>
						<view class="member-row">
							<text class="row-label">用户名</text>
							<text class="row-val">{{ m.username || '-' }}</text>
						</view>
						<view class="member-row">
							<text class="row-label">加入时间</text>
							<text class="row-val">{{ formatDate(m.joinedAt || m.createTime) }}</text>
						</view>
						<view class="member-row">
							<text class="row-label">状态</text>
							<text class="row-val">
								<text class="status-tag" :class="m.isActive ? 'status-active' : 'status-inactive'">{{ m.isActive ? '有效' : '已失效' }}</text>
							</text>
						</view>
						<view v-if="m.projectName" class="member-row">
							<text class="row-label">所属项目</text>
							<text class="row-val">{{ m.projectName }}</text>
						</view>
						<view v-if="canManage && m.isActive" class="member-actions">
							<text class="act-btn act-transfer" role="button" @click.stop="openTransfer(m)">转让组长</text>
							<text class="act-btn act-remove" role="button" @click.stop="openRemove(m)">移除</text>
						</view>
					</view>

					<!-- 空状态 -->
					<view v-if="memberList.length === 0" class="empty">
						<view class="empty-icon">👥</view>
						<text class="empty-text">暂无成员记录</text>
					</view>
					<view v-if="membersLoading && memberList.length > 0" class="load-more">加载中...</view>
					<view v-else-if="membersNoMore && memberList.length > 0" class="load-more">— 没有更多了 —</view>
				</template>
			</view>
		</view>

		<!-- ============ Tab 2：我参与的项目组 ============ -->
		<view v-if="activeTab === 'my'">
			<view class="list">
				<view v-if="myLoading && myTeamList.length === 0" class="loading-tip">加载中...</view>
				<template v-else>
					<view
						v-for="t in myTeamList"
						:key="(t.projectId || '') + '_' + (t.id || '')"
						class="member-card"
						hover-class="uni-hover"
						:hover-stay-time="80"
						role="link"
						@click="goProjectDetail(t.projectId)"
					>
						<view class="member-head">
							<view class="member-name">{{ t.projectName || ('项目#' + t.projectId) }}</view>
							<view class="role-tag" :class="t.roleInTeam === 'leader' ? 'tag-leader' : 'tag-member'">{{ t.roleInTeam === 'leader' ? '负责人' : '成员' }}</view>
						</view>
						<view class="member-row">
							<text class="row-label">加入时间</text>
							<text class="row-val">{{ formatDate(t.joinedAt || t.createTime) }}</text>
						</view>
						<view class="member-row">
							<text class="row-label">负责人</text>
							<text class="row-val">{{ t.leaderName || '-' }}</text>
						</view>
						<view class="member-row">
							<text class="row-label">成员数</text>
							<text class="row-val">{{ t.memberCount != null ? (t.memberCount + ' 人') : '-' }}</text>
						</view>
						<view v-if="!t.isActive" class="invalid-tip">已失效</view>
					</view>

					<view v-if="myTeamList.length === 0" class="empty">
						<view class="empty-icon">📁</view>
						<text class="empty-text">暂未参与任何项目组</text>
					</view>
				</template>
			</view>
		</view>

		<view class="bottom-safe"></view>

		<!-- ============ 添加成员弹窗 ============ -->
		<view v-if="addFormVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="closeAddForm">
			<view class="popup" @click.stop>
				<view class="popup-title">添加成员</view>
				<scroll-view scroll-y class="popup-scroll">
					<view class="form-item">
						<text class="form-label">项目<text class="req">*</text></text>
						<picker :range="projectList" range-key="name" :value="addForm.projectIndex" @change="onAddProjectChange">
							<view class="form-picker" :class="{ ph: addForm.projectIndex < 0 }">{{ addFormProjectText }}</view>
						</picker>
					</view>
					<view class="form-item">
						<text class="form-label">用户<text class="req">*</text></text>
						<picker :range="userOptions" range-key="displayName" :value="addForm.userIndex" @change="onAddUserChange">
							<view class="form-picker" :class="{ ph: addForm.userIndex < 0 }">{{ addFormUserText }}</view>
						</picker>
					</view>
					<view class="form-item">
						<text class="form-label">角色</text>
						<picker :range="roleOptions" range-key="label" :value="addForm.roleIndex" @change="onAddRoleChange">
							<view class="form-picker">{{ roleOptions[addForm.roleIndex].label }}</view>
						</picker>
					</view>
					<view class="form-item">
						<text class="form-label">备注</text>
						<textarea class="form-textarea" v-model="addForm.remark" aria-label="备注" placeholder="请输入备注（选填）" placeholder-class="ph" maxlength="200" />
					</view>
				</scroll-view>
				<view class="popup-actions">
					<button class="btn-cancel" @click="closeAddForm">取消</button>
					<button class="btn-confirm" :disabled="submitting" @click="confirmAdd">确定</button>
				</view>
			</view>
		</view>

		<!-- ============ 移除成员弹窗 ============ -->
		<view v-if="removeVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="removeVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">移除成员</view>
				<view class="popup-desc">确定移除「{{ removeTarget ? (removeTarget.realName || removeTarget.username || '') : '' }}」？请输入移除原因。</view>
				<textarea class="form-textarea" v-model="removeReason" aria-label="移除原因" placeholder="请输入移除原因" placeholder-class="ph" maxlength="200" />
				<view class="popup-actions">
					<button class="btn-cancel" @click="removeVisible=false">取消</button>
					<button class="btn-confirm" :disabled="submitting" @click="confirmRemove">确定移除</button>
				</view>
			</view>
		</view>

		<!-- ============ 转让组长弹窗 ============ -->
		<view v-if="transferVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="transferVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">转让组长</view>
				<view class="popup-desc">选择新的组长，转让后将同步更新项目负责人。</view>
				<view class="form-item">
					<text class="form-label">新组长<text class="req">*</text></text>
					<picker :range="transferCandidates" range-key="displayName" :value="transferIndex" @change="onTransferPickChange">
						<view class="form-picker" :class="{ ph: transferIndex < 0 }">{{ transferPickText }}</view>
					</picker>
				</view>
				<view class="popup-actions">
					<button class="btn-cancel" @click="transferVisible=false">取消</button>
					<button class="btn-confirm" :disabled="submitting" @click="confirmTransfer">确认转让</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { projectTeamApi } from '@/api/projectTeam'
	import { projectApi } from '@/api/project'
	import { userApi } from '@/api/user'
	import { hasPermission } from '@/utils/permission'
	import { formatDate } from '@/utils/format'

	export default {
		data() {
			return {
				canManage: hasPermission('project:team:manage'), // 是否可管理项目组（PM/admin）
				activeTab: 'members',          // 当前 Tab：'members' | 'my'

				// 项目列表（筛选 + 添加表单共用）
				projectList: [],

				// 顶部筛选下标
				projectFilterIndex: 0,
				roleFilterIndex: 0,
				activeFilterIndex: 0,

				// 成员列表（分页）
				memberList: [],
				membersLoading: false,
				memberPageNum: 1,
				memberPageSize: 10,
				memberTotal: 0,

				// 我参与的项目组
				myTeamList: [],
				myLoading: false,
				myLoaded: false,

				// 用户候选（添加成员用）
				userOptions: [],
				userOptionsLoaded: false,

				// 添加成员表单
				addFormVisible: false,
				addForm: {
					projectIndex: -1,
					userIndex: -1,
					roleIndex: 0,
					remark: ''
				},

				// 移除成员
				removeVisible: false,
				removeTarget: null,
				removeReason: '',

				// 转让组长
				transferVisible: false,
				transferTarget: null,          // 触发转让的成员记录（提供 projectId）
				transferCandidates: [],        // 该项目当前有效成员候选
				transferIndex: -1,

				// 提交锁（防重复提交）
				submitting: false
			}
		},
		computed: {
			/** 项目筛选选项（含「全部项目」） */
			projectFilterOptions() {
				return [{ id: '', name: '全部项目' }, ...this.projectList.map(p => ({ id: p.id, name: p.name || '-' }))]
			},
			projectFilterText() {
				const opt = this.projectFilterOptions[this.projectFilterIndex]
				return opt ? opt.name : '全部项目'
			},
			/** 角色筛选选项 */
			roleFilterOptions() {
				return [
					{ value: '', label: '全部角色' },
					{ value: 'leader', label: '负责人' },
					{ value: 'member', label: '成员' }
				]
			},
			roleFilterText() {
				return this.roleFilterOptions[this.roleFilterIndex].label
			},
			/** 有效性筛选选项 */
			activeFilterOptions() {
				return [
					{ value: '', label: '全部' },
					{ value: true, label: '有效' },
					{ value: false, label: '已失效' }
				]
			},
			activeFilterText() {
				return this.activeFilterOptions[this.activeFilterIndex].label
			},
			/** 添加表单角色选项（不含「全部」） */
			roleOptions() {
				return [
					{ value: 'member', label: '成员' },
					{ value: 'leader', label: '负责人' }
				]
			},
			addFormProjectText() {
				if (this.addForm.projectIndex < 0) return '请选择项目'
				const p = this.projectList[this.addForm.projectIndex]
				return p ? (p.name || '-') : '请选择项目'
			},
			addFormUserText() {
				if (this.addForm.userIndex < 0) return '请选择用户'
				const u = this.userOptions[this.addForm.userIndex]
				return u ? u.displayName : '请选择用户'
			},
			transferPickText() {
				if (this.transferIndex < 0) return '请选择新组长'
				const c = this.transferCandidates[this.transferIndex]
				return c ? c.displayName : '请选择新组长'
			},
			/** 成员列表是否已无更多 */
			membersNoMore() {
				return this.memberList.length >= this.memberTotal && this.memberList.length > 0
			}
		},
		// 页面加载：按权限选择默认 Tab 并拉取初始数据
		onLoad() {
			if (!this.canManage) {
				// 无管理权限的用户默认进入「我参与的项目组」
				this.activeTab = 'my'
				this.loadMyTeams()
			} else {
				this.loadProjects().then(() => this.loadMembers(true))
			}
		},
		// 页面显示：从其他页返回时刷新当前 Tab（首载由 onLoad 处理，列表为空时跳过避免重复请求）
		onShow() {
			if (this.activeTab === 'members' && this.canManage && this.memberList.length > 0) {
				this.loadMembers(true)
			} else if (this.activeTab === 'my' && this.myTeamList.length > 0) {
				this.loadMyTeams()
			}
		},
		// 下拉刷新：刷新当前 Tab
		onPullDownRefresh() {
			if (this.activeTab === 'members') {
				this.refreshMembers()
			} else {
				this.loadMyTeams().finally(() => uni.stopPullDownRefresh())
			}
		},
		// 触底加载：Tab 1 成员分页
		onReachBottom() {
			if (this.activeTab === 'members') this.loadMoreMembers()
		},
		methods: {
			formatDate,

			/** 切换 Tab：首次进入时按需加载 */
			switchTab(tab) {
				if (this.activeTab === tab) return
				this.activeTab = tab
				if (tab === 'members' && this.canManage && this.memberList.length === 0) {
					this.loadProjects().then(() => this.loadMembers(true))
				} else if (tab === 'my' && !this.myLoaded) {
					this.loadMyTeams()
				}
			},

			/** 加载项目列表（兼容数组 / {records} / {list} 三种返回） */
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

			/** 加载用户候选列表
			 * TODO: userApi.listAll / getList 暂未实现，当前降级为仅获取当前登录用户作为候选。
			 *       后端提供用户列表接口后，应改用 userApi.listAll() 获取全部可选用户。
			 */
			async loadUserOptions() {
				if (this.userOptionsLoaded) return
				let list = []
				if (typeof userApi.listAll === 'function') {
					try {
						const res = await userApi.listAll()
						list = Array.isArray(res) ? res : (res && Array.isArray(res.records) ? res.records : [])
					} catch (e) {
						console.warn('load users (listAll) error', e)
						list = []
					}
				} else if (typeof userApi.getList === 'function') {
					try {
						const res = await userApi.getList({ page: 1, size: 200 })
						list = Array.isArray(res) ? res : (res && Array.isArray(res.records) ? res.records : [])
					} catch (e) {
						console.warn('load users (getList) error', e)
						list = []
					}
				} else {
					// 降级：仅获取当前登录用户
					try {
						const profile = await userApi.getProfile()
						list = profile ? [profile] : []
					} catch (e) {
						console.warn('load profile error', e)
						list = []
					}
				}
				// 归一化显示名，供 picker range-key 使用
				this.userOptions = list.map(u => ({
					...u,
					displayName: u.realName || u.username || ('用户#' + u.id)
				}))
				this.userOptionsLoaded = true
			},

			/** 筛选变更：重置到第一页 */
			onProjectFilterChange(e) {
				this.projectFilterIndex = e.detail.value
				this.loadMembers(true)
			},
			onRoleFilterChange(e) {
				this.roleFilterIndex = e.detail.value
				this.loadMembers(true)
			},
			onActiveFilterChange(e) {
				this.activeFilterIndex = e.detail.value
				this.loadMembers(true)
			},

			/** 构造成员列表查询参数 */
			buildMemberParams() {
				const params = { pageNum: this.memberPageNum, pageSize: this.memberPageSize }
				const projectId = this.projectFilterOptions[this.projectFilterIndex].id
				if (projectId) params.projectId = projectId
				const role = this.roleFilterOptions[this.roleFilterIndex].value
				if (role) params.roleInTeam = role
				const active = this.activeFilterOptions[this.activeFilterIndex].value
				if (active !== '' && active !== null && active !== undefined) params.isActive = active
				return params
			},

			/** 加载成员列表
			 * @param {boolean} reset 是否重置到第一页
			 */
			async loadMembers(reset) {
				if (this.membersLoading) return
				this.membersLoading = true
				if (reset) {
					this.memberPageNum = 1
					this.memberList = []
				}
				try {
					const params = this.buildMemberParams()
					const res = await projectTeamApi.listMembers(params)
					let records = []
					let total = 0
					if (Array.isArray(res)) {
						records = res
						total = res.length
					} else if (res) {
						records = Array.isArray(res.records) ? res.records : []
						total = res.total || 0
					}
					this.memberList = reset ? records : this.memberList.concat(records)
					this.memberTotal = total
				} catch (e) {
					console.warn('load members error', e)
					if (reset) this.memberList = []
				} finally {
					this.membersLoading = false
				}
			},

			/** 触底加载更多成员 */
			async loadMoreMembers() {
				if (this.membersLoading) return
				if (this.memberList.length >= this.memberTotal) return
				this.memberPageNum += 1
				await this.loadMembers(false)
			},

			/** 下拉刷新成员列表 */
			refreshMembers() {
				this.loadMembers(true).finally(() => uni.stopPullDownRefresh())
			},

			/** 加载我参与的项目组 */
			async loadMyTeams() {
				this.myLoading = true
				try {
					const res = await projectTeamApi.myTeams()
					this.myTeamList = Array.isArray(res) ? res : []
					this.myLoaded = true
				} catch (e) {
					console.warn('load my teams error', e)
					this.myTeamList = []
				} finally {
					this.myLoading = false
				}
			},

			/** 跳转项目详情 */
			goProjectDetail(projectId) {
				if (!projectId) return
				uni.navigateTo({ url: `/pages/project/detail?id=${projectId}` })
			},

			// ============ 添加成员 ============
			/** 打开添加成员弹窗 */
			async openAddForm() {
				if (!this.canManage) return
				if (this.projectList.length === 0) {
					return uni.showToast({ title: '暂无可选项目', icon: 'none' })
				}
				await this.loadUserOptions()
				if (this.userOptions.length === 0) {
					return uni.showToast({ title: '暂无可选用户', icon: 'none' })
				}
				this.addForm = { projectIndex: -1, userIndex: -1, roleIndex: 0, remark: '' }
				this.addFormVisible = true
			},
			closeAddForm() {
				this.addFormVisible = false
			},
			onAddProjectChange(e) {
				this.addForm.projectIndex = e.detail.value
			},
			onAddUserChange(e) {
				this.addForm.userIndex = e.detail.value
			},
			onAddRoleChange(e) {
				this.addForm.roleIndex = e.detail.value
			},
			/** 确认添加成员 */
			async confirmAdd() {
				if (this.submitting) return
				if (this.addForm.projectIndex < 0) {
					return uni.showToast({ title: '请选择项目', icon: 'none' })
				}
				if (this.addForm.userIndex < 0) {
					return uni.showToast({ title: '请选择用户', icon: 'none' })
				}
				const project = this.projectList[this.addForm.projectIndex]
				const user = this.userOptions[this.addForm.userIndex]
				const role = this.roleOptions[this.addForm.roleIndex].value
				this.submitting = true
				uni.showLoading({ title: '提交中', mask: true })
				try {
					await projectTeamApi.addMember({
						projectId: project.id,
						userId: user.id,
						roleInTeam: role,
						remark: this.addForm.remark
					})
					this.addFormVisible = false
					uni.showToast({ title: '添加成功', icon: 'success' })
					await this.loadMembers(true)
				} catch (e) {
					console.error('add member error', e)
				} finally {
					this.submitting = false
					uni.hideLoading()
				}
			},

			// ============ 移除成员 ============
			/** 打开移除弹窗（输入原因） */
			openRemove(m) {
				if (!this.canManage) return
				this.removeTarget = m
				this.removeReason = ''
				this.removeVisible = true
			},
			/** 确认移除：先二次确认再提交 */
			confirmRemove() {
				if (this.submitting || !this.removeTarget) return
				const reason = (this.removeReason || '').trim()
				if (!reason) {
					return uni.showToast({ title: '请输入移除原因', icon: 'none' })
				}
				const target = this.removeTarget
				uni.showModal({
					title: '二次确认',
					content: `确定移除「${target.realName || target.username || ''}」？`,
					confirmColor: '#FF4D4F',
					success: async (res) => {
						if (!res.confirm) return
						this.submitting = true
						uni.showLoading({ title: '移除中', mask: true })
						try {
							await projectTeamApi.removeMember(target.id, reason)
							this.removeVisible = false
							uni.showToast({ title: '已移除', icon: 'success' })
							await this.loadMembers(true)
						} catch (e) {
							console.error('remove member error', e)
						} finally {
							this.submitting = false
							uni.hideLoading()
						}
					}
				})
			},

			// ============ 转让组长 ============
			/** 打开转让弹窗：加载该项目有效成员作为候选 */
			async openTransfer(m) {
				if (!this.canManage) return
				if (!m || !m.projectId) {
					return uni.showToast({ title: '缺少项目信息', icon: 'none' })
				}
				this.transferTarget = m
				this.transferIndex = -1
				this.transferCandidates = []
				uni.showLoading({ title: '加载候选', mask: true })
				try {
					const res = await projectTeamApi.listMembers({
						projectId: m.projectId,
						isActive: true,
						pageNum: 1,
						pageSize: 200
					})
					let list = []
					if (Array.isArray(res)) list = res
					else if (res && Array.isArray(res.records)) list = res.records
					// 候选排除当前点击成员，避免转让给自己
					list = list.filter(x => x.userId !== m.userId)
					if (list.length === 0) {
						uni.hideLoading()
						return uni.showToast({ title: '暂无可转让的候选成员', icon: 'none' })
					}
					this.transferCandidates = list.map(x => ({
						...x,
						displayName: x.realName || x.username || ('用户#' + x.userId)
					}))
					this.transferVisible = true
				} catch (e) {
					console.warn('load transfer candidates error', e)
					uni.showToast({ title: '加载候选失败', icon: 'none' })
				} finally {
					uni.hideLoading()
				}
			},
			onTransferPickChange(e) {
				this.transferIndex = e.detail.value
			},
			/** 确认转让：二次确认后提交
			 * 注意：transferLeader 会同步更新项目负责人（project.leader_id） */
			confirmTransfer() {
				if (this.submitting) return
				if (this.transferIndex < 0) {
					return uni.showToast({ title: '请选择新组长', icon: 'none' })
				}
				const candidate = this.transferCandidates[this.transferIndex]
				const target = this.transferTarget
				uni.showModal({
					title: '确认转让',
					content: `确定将「${target.projectName || ('项目#' + target.projectId)}」的组长转让给「${candidate.displayName}」？转让后将同步更新项目负责人。`,
					confirmColor: '#FA8C16',
					success: async (res) => {
						if (!res.confirm) return
						this.submitting = true
						uni.showLoading({ title: '转让中', mask: true })
						try {
							// 转让组长：后端会同步更新项目负责人（project.leader_id）
							await projectTeamApi.transferLeader(target.projectId, candidate.userId)
							this.transferVisible = false
							uni.showToast({ title: '转让成功', icon: 'success' })
							await this.loadMembers(true)
						} catch (e) {
							console.error('transfer leader error', e)
						} finally {
							this.submitting = false
							uni.hideLoading()
						}
					}
				})
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

	/* 顶部标题栏 */
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

	/* 顶部筛选栏 */
	.filter-bar {
		display: flex;
		gap: 16rpx;
		margin-bottom: 20rpx;
	}
	.filter-picker {
		flex: 1;
		background: #FFFFFF;
		border-radius: 12rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.picker-text {
		padding: 20rpx 16rpx;
		font-size: 26rpx;
		color: #303133;
		text-align: center;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
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

	/* 成员卡片 */
	.member-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.member-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.member-name {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
		flex: 1;
		margin-right: 16rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.role-tag {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.tag-leader { background: #E6F7FF; color: #096DD9; }
	.tag-member { background: #F4F4F5; color: #5C6370; }

	.member-row {
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
	.status-tag {
		font-size: 22rpx;
		padding: 2rpx 14rpx;
		border-radius: 6rpx;
	}
	.status-active { background: #F6FFED; color: #52C41A; }
	.status-inactive { background: #FFF1F0; color: #FF4D4F; }

	.invalid-tip {
		margin-top: 12rpx;
		font-size: 22rpx;
		color: #FF4D4F;
		background: #FFF1F0;
		padding: 6rpx 14rpx;
		border-radius: 6rpx;
		display: inline-block;
	}

	/* 行内操作按钮 */
	.member-actions {
		display: flex;
		justify-content: flex-end;
		gap: 24rpx;
		margin-top: 16rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid #F5F7FA;
	}
	.act-btn {
		font-size: 24rpx;
		padding: 6rpx 20rpx;
		border-radius: 8rpx;
	}
	.act-transfer {
		color: #096DD9;
		background: #E6F7FF;
	}
	.act-remove {
		color: #D4380D;
		background: #FFF1F0;
	}

	/* 空状态与加载更多 */
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

	/* 弹窗 */
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
		max-height: 80vh;
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
		margin-bottom: 20rpx;
	}
	.popup-desc {
		font-size: 26rpx;
		color: #606266;
		line-height: 1.6;
		margin-bottom: 20rpx;
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
	.form-picker.ph {
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
	.ph {
		color: #767E8A;
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
		&[disabled] { opacity: 0.5; }
	}
	.btn-cancel {
		background: #F5F7FA;
		color: #606266;
	}
	.btn-confirm {
		background: #1890FF;
		color: #FFFFFF;
	}
</style>
