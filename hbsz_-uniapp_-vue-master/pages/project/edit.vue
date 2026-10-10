<!--
  项目编辑页（新建/编辑）
  业务用途：新建或编辑项目核心信息。
           - 无 id 参数：新建模式，调用 projectApi.create 提交（后端权限 project:add）
           - 有 id 参数：编辑模式，先拉取 projectApi.getDetail 回填表单，调用 projectApi.update 提交（后端权限 project:edit）
           编辑模式下，有权限的用户可执行删除（project:remove）与归档/取消归档（project:archive）。
  数据来源：projectApi.getDetail（详情回填）、customerApi.listAll（客户下拉，失败时降级到 getList）、
           userApi.listProjectManagers（项目经理下拉，接口未实现，标 TODO，暂用详情中的 managerName 只读展示）。
  导航特性：非 tabBar 页；通过 url 参数 id 接收项目ID；保存/删除/归档成功后 navigateBack。
  注意：复杂字段（设计图纸/级联地区/批量导入）不在本页实现范围。
-->
<template>
	<view class="page">
		<!-- 顶部标题栏（参考客户列表 .nav-bar 样式）：标题 + 保存按钮 -->
		<view class="nav-bar">
			<view class="nav-title">{{ isEdit ? '编辑项目' : '新建项目' }}</view>
			<view
				class="nav-action"
				role="button"
				:class="{ disabled: submitting }"
				@click="handleSubmit"
			>{{ submitting ? '保存中' : '保存' }}</view>
		</view>

		<!-- 表单卡片 -->
		<view class="form-card">
			<!-- 项目名称 -->
			<view class="form-item">
				<text class="form-label">项目名称<text class="req">*</text></text>
				<input
					class="form-input"
					v-model="form.name"
					aria-label="项目名称"
					placeholder="请输入项目名称"
					placeholder-class="ph"
					maxlength="100"
				/>
			</view>

			<!-- 项目编号（编辑时只读，不可修改） -->
			<view class="form-item">
				<text class="form-label">项目编号<text class="req">*</text></text>
				<input
					class="form-input"
					:class="{ readonly: isEdit }"
					v-model="form.code"
					aria-label="项目编号"
					:placeholder="isEdit ? '项目编号不可修改' : '请输入项目编号'"
					placeholder-class="ph"
					maxlength="50"
					:disabled="isEdit"
				/>
			</view>

			<!-- 项目类型 -->
			<view class="form-item">
				<text class="form-label">项目类型<text class="req">*</text></text>
				<picker :range="typeOptions" :value="typeIndex" @change="onTypeChange">
					<view class="form-input picker-value" :class="{ placeholder: !form.type }">
						{{ form.type || '请选择项目类型' }}
					</view>
				</picker>
			</view>

			<!-- 优先级 -->
			<view class="form-item">
				<text class="form-label">优先级</text>
				<picker :range="priorityOptions" :value="priorityIndex" @change="onPriorityChange">
					<view class="form-input picker-value" :class="{ placeholder: !form.priority }">
						{{ form.priority || '请选择优先级' }}
					</view>
				</picker>
			</view>

			<!-- 状态 -->
			<view class="form-item">
				<text class="form-label">状态</text>
				<picker :range="statusOptions" :value="statusIndex" @change="onStatusChange">
					<view class="form-input picker-value" :class="{ placeholder: !form.status }">
						{{ form.status || '请选择状态' }}
					</view>
				</picker>
			</view>

			<!-- 客户 -->
			<view class="form-item">
				<text class="form-label">客户</text>
				<picker
					:range="customerNames"
					:value="customerIndex"
					:disabled="customerOptions.length === 0"
					@change="onCustomerChange"
				>
					<view class="form-input picker-value" :class="{ placeholder: !form.customerId }">
						{{ displayCustomerName || (customerOptions.length ? '请选择客户' : '加载中...') }}
					</view>
				</picker>
			</view>

			<!-- 项目经理 -->
			<view class="form-item">
				<text class="form-label">项目经理</text>
				<!-- TODO: 后端缺少项目经理列表接口 userApi.listProjectManagers，暂使用项目详情中的 managerName 只读展示 -->
				<picker
					v-if="managerOptions.length > 0"
					:range="managerNames"
					:value="managerIndex"
					@change="onManagerChange"
				>
					<view class="form-input picker-value" :class="{ placeholder: !form.managerId }">
						{{ form.managerName || '请选择项目经理' }}
					</view>
				</picker>
				<view v-else class="form-input picker-value" :class="{ placeholder: !form.managerName }">
					{{ form.managerName || (isEdit ? '未指定' : '暂无法选择（接口未实现）') }}
				</view>
			</view>

			<!-- 起始日期 -->
			<view class="form-item">
				<text class="form-label">起始日期<text class="req">*</text></text>
				<picker mode="date" :value="form.startDate" @change="onStartDateChange">
					<view class="form-input picker-value" :class="{ placeholder: !form.startDate }">
						{{ form.startDate || '请选择起始日期' }}
					</view>
				</picker>
			</view>

			<!-- 结束日期 -->
			<view class="form-item">
				<text class="form-label">结束日期<text class="req">*</text></text>
				<picker mode="date" :value="form.endDate" @change="onEndDateChange">
					<view class="form-input picker-value" :class="{ placeholder: !form.endDate }">
						{{ form.endDate || '请选择结束日期' }}
					</view>
				</picker>
			</view>

			<!-- 合同金额（type=digit 支持小数） -->
			<view class="form-item">
				<text class="form-label">合同金额</text>
				<input
					class="form-input"
					v-model="form.contractAmount"
					type="digit"
					aria-label="合同金额"
					placeholder="请输入合同金额"
					placeholder-class="ph"
				/>
			</view>

			<!-- 项目地址 -->
			<view class="form-item">
				<text class="form-label">项目地址</text>
				<input
					class="form-input"
					v-model="form.address"
					aria-label="项目地址"
					placeholder="请输入项目地址"
					placeholder-class="ph"
					maxlength="200"
				/>
			</view>

			<!-- 项目描述（500 字限制） -->
			<view class="form-item">
				<text class="form-label">项目描述</text>
				<textarea
					class="form-textarea"
					v-model="form.description"
					aria-label="项目描述"
					placeholder="请输入项目描述（最多 500 字）"
					placeholder-class="ph"
					maxlength="500"
					:auto-height="false"
				/>
				<view class="char-count">{{ (form.description || '').length }}/500</view>
			</view>
		</view>

		<!-- 编辑模式：归档/取消归档按钮（有权限时显示） -->
		<view v-if="isEdit && canArchive" class="action-card">
			<button class="btn-action" @click="handleArchive">
				{{ form.archived ? '取消归档' : '归档项目' }}
			</button>
		</view>

		<!-- 编辑模式：删除按钮（有权限时显示） -->
		<view v-if="isEdit && canRemove" class="action-card">
			<button class="btn-danger" @click="handleDelete">删除项目</button>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { projectApi } from '@/api/project'
	import { customerApi } from '@/api/customer'
	import { userApi } from '@/api/user'
	import { hasPermission } from '@/utils/permission'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				projectId: '',      // 项目ID（编辑模式下来自路由参数）
				isEdit: false,      // 是否为编辑模式
				submitting: false,  // 防重复提交锁
				// 权限标记（页面加载时一次性计算，角色在会话内不变）
				canRemove: hasPermission('project:remove'),
				canArchive: hasPermission('project:archive'),
				// 下拉选项（静态）
				typeOptions: ['数据中心布线', '综合布线', '机房工程', '弱电工程', '其他'],
				priorityOptions: ['高', '中', '低'],
				statusOptions: ['进行中', '已完成', '已暂停', '已取消'],
				// 下拉选项（动态，由接口加载）
				customerOptions: [],  // 客户列表
				managerOptions: [],   // 项目经理列表（接口未实现，可能为空）
				// 表单数据
				form: this.emptyForm()
			}
		},
		computed: {
		/** 项目类型当前选中下标 */
		typeIndex() { return Math.max(0, this.typeOptions.indexOf(this.form.type)) },
		/** 优先级当前选中下标 */
		priorityIndex() { return Math.max(0, this.priorityOptions.indexOf(this.form.priority)) },
		/** 状态当前选中下标 */
		statusIndex() { return Math.max(0, this.statusOptions.indexOf(this.form.status)) },
		/**
		 * 客户名称数组（picker range 仅支持字符串数组）
		 * 显示格式：「客户名称（联系人）」，无联系人时仅显示客户名称
		 * 兼容 name / customerName 两种字段命名，并强制转为字符串避免数字被 picker 当作下标
		 */
		customerNames() {
			return this.customerOptions.map(c => {
				const name = String(c.name || c.customerName || '')
				const contact = String(c.contactPerson || '').trim()
				return contact ? `${name}（${contact}）` : name
			})
		},
		/**
		 * 客户当前选中下标（按 id 匹配）
		 * 用 String() 统一类型比较，避免后端数字 id 与前端字符串 id 不匹配导致 findIndex 返回 -1
		 */
		customerIndex() {
			if (this.form.customerId == null) return -1
			const target = String(this.form.customerId)
			return this.customerOptions.findIndex(c => String(c.id) === target)
		},
		/**
		 * 客户显示名称（用于模板展示）
		 * 优先从 customerOptions 中按 customerId 查找并格式化为「客户名称（联系人）」，
		 * 兜底使用 form.customerName（后端返回的纯名称），避免编辑模式下 customerOptions
		 * 未加载完时显示空白或异常值（如数字 ID）。
		 */
		displayCustomerName() {
			// customerOptions 已加载：按 id 查找并格式化
			if (this.customerOptions.length > 0 && this.form.customerId != null) {
				const target = String(this.form.customerId)
				const c = this.customerOptions.find(c => String(c.id) === target)
				if (c) {
					const name = String(c.name || c.customerName || '')
					const contact = String(c.contactPerson || '').trim()
					return contact ? `${name}（${contact}）` : name
				}
			}
			// 兜底：使用 form.customerName（强制字符串，避免数字 ID 被显示）
			return String(this.form.customerName || '')
		},
		/** 项目经理名称数组（兼容 realName/name/username 多种字段命名） */
		managerNames() { return this.managerOptions.map(m => m.realName || m.name || m.username) },
		/** 项目经理当前选中下标（按 id 匹配，同样用 String 统一类型） */
		managerIndex() {
			if (this.form.managerId == null) return -1
			const target = String(this.form.managerId)
			return this.managerOptions.findIndex(m => String(m.id) === target)
		}
	},
		// 页面加载：接收路由参数 id，判断模式并加载所需数据
		onLoad(options) {
			if (options && options.id) {
				this.projectId = options.id
				this.isEdit = true
				this.loadAll()
			} else {
				this.isEdit = false
				// 新建模式仍需加载客户下拉数据
				this.loadCustomers()
				this.loadManagers()
			}
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			/** 空表单结构（避免引用污染，每次返回新对象） */
			emptyForm() {
				return {
					name: '',
					code: '',
					type: '',
					priority: '中',      // 默认中优先级
					status: '进行中',    // 默认进行中
					customerId: null,
					customerName: '',
					managerId: null,
					managerName: '',
					startDate: '',
					endDate: '',
					contractAmount: '',
					address: '',
					description: '',
					archived: false      // 归档状态（仅用于切换归档按钮文案，不参与 create/update 提交）
				}
			},
			/** 并行加载详情与下拉数据（编辑模式） */
			async loadAll() {
				uni.showLoading({ title: '加载中', mask: true })
				await Promise.allSettled([
					this.loadDetail(),
					this.loadCustomers(),
					this.loadManagers()
				])
				uni.hideLoading()
			},
			/** 加载项目详情并回填表单 */
			async loadDetail() {
				try {
					const detail = await projectApi.getDetail(this.projectId)
					this.fillForm(detail)
				} catch (e) {
					console.warn('load detail error', e)
					uni.showToast({ title: '加载详情失败', icon: 'none' })
				}
			},
			/** 用详情数据回填表单（兼容多种字段命名，强制 name 为字符串避免显示数字） */
		fillForm(d) {
			if (!d) return
			this.form = {
				name: d.name || '',
				code: d.code || '',
				type: d.type || '',
				priority: d.priority || '中',
				status: d.status || '进行中',
				customerId: d.customerId || null,
				// 强制转为字符串，避免后端返回数字 ID 时 picker 显示数字
				customerName: String(d.customerName || d.customer || ''),
				managerId: d.managerId || d.managerUserId || null,
				managerName: d.manager || d.managerName || '',
				startDate: this.normalizeDate(d.startDate),
				endDate: this.normalizeDate(d.endDate),
				contractAmount: d.contractAmount != null ? String(d.contractAmount) : '',
				address: d.address || '',
				description: d.description || '',
				archived: !!d.archived
			}
		},
			/**
			 * 将日期字符串规范化为 yyyy-MM-dd
			 * picker mode=date 要求 value 为 yyyy-MM-dd 格式
			 */
			normalizeDate(d) {
				if (!d) return ''
				return String(d).substring(0, 10)
			},
			/** 加载启用客户列表（下拉用，优先 listAll，失败降级到 getList 分页取全部） */
			async loadCustomers() {
				try {
					const res = await customerApi.listAll()
					// 兼容数组 / {records} / {list} 多种返回结构
					this.customerOptions = Array.isArray(res) ? res : (res && (res.records || res.list)) || []
				} catch (e) {
					console.warn('load customers error', e)
					// listAll 失败时降级到分页接口取全部
					try {
						const res = await customerApi.getList({ pageNum: 1, pageSize: 1000 })
						this.customerOptions = (res && (res.records || res.list)) || []
					} catch (e2) {
						console.warn('load customers fallback error', e2)
						this.customerOptions = []
					}
				}
				// 加载完成后，若处于编辑模式且已回填 customerId，从 options 中查找并修正 customerName
				// 修复场景：fillForm 时后端返回的 customerName 为 null 或异常值（如数字 ID），此时
				// displayCustomerName 计算属性会兜底，但同步修正 form.customerName 可保证提交数据正确
				if (this.form.customerId != null && this.customerOptions.length > 0) {
					const target = String(this.form.customerId)
					const c = this.customerOptions.find(c => String(c.id) === target)
					if (c) {
						const correctName = String(c.name || c.customerName || '')
						// 仅当当前 form.customerName 与 options 中的真实名称不一致时才更新
						if (correctName && this.form.customerName !== correctName) {
							this.form.customerName = correctName
						}
					}
				}
			},
			/** 加载项目经理列表 */
			async loadManagers() {
				// TODO: 后端缺少项目经理列表接口 userApi.listProjectManagers，暂使用项目详情中的 managerName 只读展示
				if (typeof userApi.listProjectManagers === 'function') {
					try {
						const res = await userApi.listProjectManagers()
						this.managerOptions = Array.isArray(res) ? res : (res && (res.records || res.list)) || []
					} catch (e) {
						console.warn('load managers error', e)
						this.managerOptions = []
					}
				} else {
					this.managerOptions = []
				}
			},
			/** 项目类型变更 */
			onTypeChange(e) { this.form.type = this.typeOptions[e.detail.value] },
			/** 优先级变更 */
			onPriorityChange(e) { this.form.priority = this.priorityOptions[e.detail.value] },
			/** 状态变更 */
			onStatusChange(e) { this.form.status = this.statusOptions[e.detail.value] },
			/** 客户变更（同步记录 id 与 name，兼容字段命名并强制 name 为字符串） */
		onCustomerChange(e) {
			const c = this.customerOptions[e.detail.value]
			if (c) {
				this.form.customerId = c.id
				// 强制转为字符串，避免后端返回数字 name 时 picker 显示数字
				this.form.customerName = String(c.name || c.customerName || '')
			}
		},
			/** 项目经理变更（同步记录 id 与 name） */
			onManagerChange(e) {
				const m = this.managerOptions[e.detail.value]
				if (m) {
					this.form.managerId = m.id
					this.form.managerName = m.realName || m.name || m.username
				}
			},
			/** 起始日期变更 */
			onStartDateChange(e) { this.form.startDate = e.detail.value },
			/** 结束日期变更 */
			onEndDateChange(e) { this.form.endDate = e.detail.value },
			/**
			 * 表单校验
			 * 必填：名称/编号/类型/起止日期；结束日期必须晚于起始日期
			 * @returns {boolean} 校验通过返回 true，失败返回 false 并 toast 提示
			 */
			validateForm() {
				const f = this.form
				if (!f.name || !f.name.trim()) {
					uni.showToast({ title: '请输入项目名称', icon: 'none' })
					return false
				}
				if (!f.code || !f.code.trim()) {
					uni.showToast({ title: '请输入项目编号', icon: 'none' })
					return false
				}
				if (!f.type) {
					uni.showToast({ title: '请选择项目类型', icon: 'none' })
					return false
				}
				if (!f.startDate) {
					uni.showToast({ title: '请选择起始日期', icon: 'none' })
					return false
				}
				if (!f.endDate) {
					uni.showToast({ title: '请选择结束日期', icon: 'none' })
					return false
				}
				// 结束日期必须晚于起始日期（等于也不合法）
				if (new Date(f.endDate) <= new Date(f.startDate)) {
					uni.showToast({ title: '结束日期必须晚于起始日期', icon: 'none' })
					return false
				}
				return true
			},
			/** 构造提交 payload（剔除展示用字段，金额转数字） */
			buildPayload() {
				const f = this.form
				const payload = {
					name: f.name.trim(),
					code: f.code.trim(),
					type: f.type,
					priority: f.priority,
					status: f.status,
					customerId: f.customerId || null,
					managerId: f.managerId || null,
					startDate: f.startDate,
					endDate: f.endDate,
					address: f.address,
					description: f.description
				}
				// 金额转数字；空值不传，避免后端解析失败
				if (f.contractAmount !== '' && f.contractAmount != null) {
					payload.contractAmount = Number(f.contractAmount)
				}
				return payload
			},
			/** 提交（新建或更新），含防重复提交锁 */
			async handleSubmit() {
				if (this.submitting) return
				if (!this.validateForm()) return
				this.submitting = true
				uni.showLoading({ title: '保存中', mask: true })
				try {
					const payload = this.buildPayload()
					if (this.isEdit) {
						await projectApi.update(this.projectId, payload)
					} else {
						await projectApi.create(payload)
					}
					uni.hideLoading()
					uni.showToast({ title: '保存成功', icon: 'success' })
					// 短暂延迟后返回，确保 toast 可见
					this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
				} catch (e) {
					uni.hideLoading()
					console.warn('submit error', e)
				} finally {
					this.submitting = false
				}
			},
			/** 删除项目（二次确认 + 调用 remove） */
			handleDelete() {
				uni.showModal({
					title: '提示',
					content: `确认删除项目「${this.form.name}」吗？此操作不可恢复。`,
					confirmColor: '#FF4D4F',
					success: async (res) => {
						if (!res.confirm) return
						uni.showLoading({ title: '删除中', mask: true })
						try {
							await projectApi.remove(this.projectId)
							uni.hideLoading()
							uni.showToast({ title: '删除成功', icon: 'success' })
							this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
						} catch (e) {
							uni.hideLoading()
							console.warn('delete error', e)
						}
					}
				})
			},
			/** 归档 / 取消归档（二次确认，根据当前归档状态切换接口） */
			handleArchive() {
				const archived = this.form.archived
				const action = archived ? '取消归档' : '归档'
				uni.showModal({
					title: '提示',
					content: `确认${action}项目「${this.form.name}」吗？`,
					success: async (res) => {
						if (!res.confirm) return
						uni.showLoading({ title: '处理中', mask: true })
						try {
							const api = archived ? projectApi.unarchive : projectApi.archive
							await api(this.projectId)
							uni.hideLoading()
							uni.showToast({ title: `${action}成功`, icon: 'success' })
							// 本地切换归档状态，刷新按钮文案
							this.form.archived = !archived
						} catch (e) {
							uni.hideLoading()
							console.warn('archive error', e)
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

	/* 顶部标题栏（参考客户列表 .nav-bar/.nav-title/.nav-action 样式） */
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
	.nav-action.disabled {
		color: #B3D9FF;
	}

	/* 表单卡片 */
	.form-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.form-item {
		margin-bottom: 24rpx;
	}
	.form-item:last-child {
		margin-bottom: 0;
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
	/* 只读输入框样式（编辑模式下的项目编号） */
	.form-input.readonly {
		color: #909399;
		background: #F4F4F5;
	}
	/* picker 触发器样式（行内垂直居中） */
	.picker-value {
		line-height: 72rpx;
	}
	.picker-value.placeholder {
		color: #767E8A;
	}
	.ph {
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
	.char-count {
		text-align: right;
		font-size: 22rpx;
		color: #909399;
		margin-top: 8rpx;
	}

	/* 操作区卡片（归档/删除） */
	.action-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 16rpx 24rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.btn-action {
		width: 100%;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		border: none;
		margin: 0;
		background: #FFF7E6;
		color: #D48806;
		&::after { border: none; }
	}
	.btn-danger {
		width: 100%;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		border: none;
		margin: 0;
		background: #FFF1F0;
		color: #FF4D4F;
		&::after { border: none; }
	}

	.bottom-safe {
		height: calc(40rpx + env(safe-area-inset-bottom));
	}
</style>
