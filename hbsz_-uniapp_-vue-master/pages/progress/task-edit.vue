<!--
  任务创建/编辑页
  业务用途：在指定项目下新建任务或编辑已有任务的核心字段（名称、父任务、负责人、
           计划起止日期、里程碑标记、任务描述）。编辑模式额外支持删除任务（需 task:remove 权限）。
  数据来源：
    - taskApi.getList（取项目下任务列表，作为父任务可选项 + 负责人下拉数据源）
    - taskApi.getDetail（编辑模式回填表单）
  提交：taskApi.create（新建，POST /projects/{projectId}/tasks）
        taskApi.update（编辑，PUT /tasks/{id}）
  导航特性：非 tabBar 页，通过 url 参数 projectId（必传）、id（可选，编辑模式）进入；
           提交或删除成功后返回上一页。
  注意：
    - 父任务（parentId）在编辑模式下只读，避免破坏已建立的子任务层级。
    - 工作量基准（plannedWorkload）字段暂不实现，保留在 Web 端维护。
-->
<template>
	<view class="page">
		<!-- 顶部导航栏：标题 + 删除操作（仅编辑模式 + 有权限时显示删除） -->
		<view class="nav-bar">
			<view class="nav-title">{{ isEdit ? '编辑任务' : '新建任务' }}</view>
			<view
				v-if="isEdit && canRemove"
				class="nav-action danger"
				role="button"
				aria-label="删除任务"
				@click="confirmDelete"
			>删除</view>
		</view>

		<!-- 表单卡片 -->
		<view class="card">
			<!-- 任务名称（必填，最长 100） -->
			<view class="form-item">
				<text class="form-label">任务名称<text class="req">*</text></text>
				<input
					class="form-input"
					v-model="form.name"
					maxlength="100"
					placeholder="请输入任务名称"
					placeholder-class="ph"
					aria-label="任务名称"
				/>
				<view class="field-counter">{{ form.name.length }}/100</view>
			</view>

			<!-- 父任务：新建模式可选；编辑模式只读 -->
			<!-- 编辑模式禁止修改 parentId 的原因：调整父子关系可能产生循环引用，
                 且会影响已有子任务的层级展示，需后端单独接口校验后处理 -->
			<view class="form-item" v-if="!isEdit">
				<text class="form-label">父任务</text>
				<picker :range="parentOptions" range-key="name" :value="parentIndex" @change="onParentChange">
					<view class="picker-input" role="button" aria-label="选择父任务">
						<text :class="['picker-text', !form.parentId && 'ph']">{{ parentDisplayText }}</text>
						<text class="picker-arrow">›</text>
					</view>
				</picker>
			</view>
			<view class="form-item" v-else>
				<text class="form-label">父任务</text>
				<view class="readonly-text">{{ parentDisplayText }}</view>
			</view>

			<!-- 负责人：picker 选择，数据源为项目下任务的 assignee 去重 -->
			<!-- TODO: 后端缺少无权限要求的「项目组成员列表」接口
                 （projectTeamApi.listMembers 需 project:team:manage 权限，工程师不可调用）。
                 临时方案：用 taskApi.getList 取项目下所有任务的 assignee 字段去重作为下拉源；
                 待后端提供开放的项目组成员列表接口后替换为成员列表。 -->
			<view class="form-item">
				<text class="form-label">负责人</text>
				<picker
					:range="assigneeOptions"
					:value="assigneeIndex"
					:disabled="assigneeOptions.length === 0"
					@change="onAssigneeChange"
				>
					<view class="picker-input" role="button" aria-label="选择负责人">
						<text :class="['picker-text', !form.assignee && 'ph']">
							{{ form.assignee || (assigneeOptions.length ? '请选择负责人' : '暂无可选负责人') }}
						</text>
						<text class="picker-arrow">›</text>
					</view>
				</picker>
			</view>

			<!-- 计划开始日期（必填） -->
			<view class="form-item">
				<text class="form-label">计划开始日期<text class="req">*</text></text>
				<picker mode="date" :value="form.startDate" @change="onStartChange">
					<view class="picker-input" role="button" aria-label="选择计划开始日期">
						<text :class="['picker-text', !form.startDate && 'ph']">{{ form.startDate || '请选择开始日期' }}</text>
						<text class="picker-arrow">›</text>
					</view>
				</picker>
			</view>

			<!-- 计划结束日期（必填，且需晚于起始日期） -->
			<view class="form-item">
				<text class="form-label">计划结束日期<text class="req">*</text></text>
				<picker mode="date" :value="form.endDate" :start="form.startDate" @change="onEndChange">
					<view class="picker-input" role="button" aria-label="选择计划结束日期">
						<text :class="['picker-text', !form.endDate && 'ph']">{{ form.endDate || '请选择结束日期' }}</text>
						<text class="picker-arrow">›</text>
					</view>
				</picker>
			</view>

			<!-- 是否里程碑 -->
			<view class="form-item form-item-row">
				<text class="form-label">是否里程碑</text>
				<switch :checked="form.isMilestone" color="#1890FF" @change="onMilestoneChange" />
			</view>

			<!-- 任务描述（500 字限制） -->
			<view class="form-item">
				<view class="form-label-row">
					<text class="form-label">任务描述</text>
					<text class="field-counter">{{ form.description.length }}/500</text>
				</view>
				<textarea
					class="form-textarea"
					v-model="form.description"
					maxlength="500"
					placeholder="请输入任务描述"
					placeholder-class="ph"
					:auto-height="false"
					aria-label="任务描述"
				/>
			</view>
		</view>

		<!-- 底部安全区占位（避免内容被固定底栏遮挡） -->
		<view class="bottom-safe"></view>

		<!-- 底部提交按钮栏（固定定位） -->
		<view class="submit-bar">
			<button
				class="submit-btn"
				:loading="submitting"
				:disabled="submitting"
				@click="handleSubmit"
			>{{ submitting ? '保存中...' : '保存' }}</button>
		</view>
	</view>
</template>

<script>
	import { taskApi } from '@/api/task'
	import { hasPermission } from '@/utils/permission'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				projectId: '',      // 所属项目ID（路由参数，必传）
				taskId: '',         // 任务ID（路由参数，编辑模式才有）
				isEdit: false,      // 是否为编辑模式（有 id 即为编辑）
				canRemove: false,   // 是否有删除权限（task:remove）
				submitting: false,  // 提交中标记（防重复提交）
				taskList: [],       // 项目下所有任务（用于父任务选项 + 负责人下拉数据源）
				parentOptions: [],  // 父任务选项数组 [{ id, name }]，首项为"无（作为顶级任务）"
				assigneeOptions: [],// 负责人选项数组（去重后的 assignee 名称字符串数组）
				form: {
					name: '',           // 任务名称
					parentId: null,     // 父任务ID（null 表示顶级任务）
					assignee: '',       // 负责人名称
					startDate: '',      // 计划开始日期 YYYY-MM-DD
					endDate: '',        // 计划结束日期 YYYY-MM-DD
					isMilestone: false, // 是否里程碑
					description: ''     // 任务描述
				}
			}
		},
		computed: {
			/**
			 * 父任务选择器当前索引
			 * 匹配 parentOptions 中 id === form.parentId 的项；未匹配时返回 0（即"无"）
			 */
			parentIndex() {
				const idx = this.parentOptions.findIndex(o => o.id === this.form.parentId)
				return idx === -1 ? 0 : idx
			},
			/**
			 * 父任务展示文案
			 * 用于 picker 显示当前选中项 + 编辑模式只读展示
			 */
			parentDisplayText() {
				const p = this.parentOptions.find(o => o.id === this.form.parentId)
				return p ? p.name : '无（作为顶级任务）'
			},
			/**
			 * 负责人选择器当前索引
			 * 匹配 assigneeOptions 中等于 form.assignee 的项；未匹配时返回 0
			 */
			assigneeIndex() {
				const idx = this.assigneeOptions.indexOf(this.form.assignee)
				return idx === -1 ? 0 : idx
			}
		},
		/**
		 * 页面加载：接收路由参数，判断模式，初始化数据
		 * @param {Object} opts 路由参数
		 *   - projectId: 必传，所属项目ID
		 *   - id: 可选，任务ID（存在则为编辑模式）
		 */
		onLoad(opts) {
			this.projectId = opts && opts.projectId
			this.taskId = opts && opts.id
			this.isEdit = !!this.taskId
			// 删除权限：task:remove（当前 permission.js 中未列入 PM_ONLY_PERMS，默认放行；
			// 后续若收紧权限，只需在 PM_ONLY_PERMS 中追加 'task:remove' 即可生效）
			this.canRemove = hasPermission('task:remove')
			// 缺少项目参数时无法继续，提示后返回
			if (!this.projectId) {
				uni.showToast({ title: '缺少项目参数', icon: 'none' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
				return
			}
			// 动态设置原生导航栏标题（pages.json 默认标题为"任务编辑"）
			uni.setNavigationBarTitle({ title: this.isEdit ? '编辑任务' : '新建任务' })
			this.init()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			/**
			 * 初始化：加载项目任务列表，编辑模式还需加载任务详情回填表单
			 */
			async init() {
				uni.showLoading({ title: '加载中', mask: true })
				try {
					await this.loadTaskList()
					if (this.isEdit) {
						await this.loadTaskDetail()
					}
				} finally {
					uni.hideLoading()
				}
			},
			/**
			 * 加载项目下任务列表
			 * 用途：① 新建模式作为父任务选项来源；② 提取 assignee 去重作为负责人下拉源
			 * 兼容 {records}/{list}/数组 多种返回结构
			 */
			async loadTaskList() {
				try {
					const res = await taskApi.getList(this.projectId)
					const list = (res && (res.records || res.list || res)) || []
					this.taskList = Array.isArray(list) ? list : []
					// 构建父任务选项：首项固定为"无（作为顶级任务）"，编辑模式排除当前任务自身（避免自引用）
					const parentCandidates = this.taskList
						.filter(t => {
							if (!this.taskId) return true
							// 兼容 id 为数字或字符串
							return t.id !== Number(this.taskId) && t.id !== this.taskId
						})
						.map(t => ({ id: t.id, name: t.name }))
					this.parentOptions = [{ id: null, name: '无（作为顶级任务）' }, ...parentCandidates]
					// 构建负责人选项：从所有任务的 assignee 字段去重
					// 兼容 assignee / assigneeName 两种字段命名
					const names = this.taskList
						.map(t => t.assignee || t.assigneeName)
						.filter(Boolean)
					this.assigneeOptions = [...new Set(names)]
				} catch (e) {
					console.warn('load task list error', e)
					this.taskList = []
					this.parentOptions = [{ id: null, name: '无（作为顶级任务）' }]
					this.assigneeOptions = []
				}
			},
			/**
			 * 加载任务详情并回填表单（编辑模式）
			 */
			async loadTaskDetail() {
				try {
					const task = await taskApi.getDetail(this.taskId)
					if (!task) return
					this.form = {
						name: task.name || '',
						parentId: task.parentId ?? null,
						assignee: task.assignee || task.assigneeName || '',
						startDate: this.normalizeDate(task.startDate),
						endDate: this.normalizeDate(task.endDate),
						isMilestone: !!task.isMilestone,
						description: task.description || ''
					}
					// 若当前负责人不在选项列表中（如该负责人无其他任务），追加到选项末尾以保证可显示
					if (this.form.assignee && !this.assigneeOptions.includes(this.form.assignee)) {
						this.assigneeOptions.push(this.form.assignee)
					}
				} catch (e) {
					console.warn('load task detail error', e)
					uni.showToast({ title: '加载任务详情失败', icon: 'none' })
				}
			},
			/**
			 * 将日期字符串规范化为 YYYY-MM-DD（picker mode=date 需要）
			 * 兼容后端返回的 ISO 字符串或时间戳
			 * @param {string|number} d 日期值
			 * @returns {string} YYYY-MM-DD 或空字符串
			 */
			normalizeDate(d) {
				if (!d) return ''
				const date = new Date(d)
				if (isNaN(date.getTime())) return ''
				const y = date.getFullYear()
				const m = String(date.getMonth() + 1).padStart(2, '0')
				const day = String(date.getDate()).padStart(2, '0')
				return `${y}-${m}-${day}`
			},
			/**
			 * 父任务选择回调
			 * @param {Object} e picker change 事件，e.detail.value 为选中索引
			 */
			onParentChange(e) {
				const idx = e.detail.value
				const item = this.parentOptions[idx]
				this.form.parentId = item ? item.id : null
			},
			/**
			 * 负责人选择回调
			 * @param {Object} e picker change 事件，e.detail.value 为选中索引
			 */
			onAssigneeChange(e) {
				const idx = e.detail.value
				this.form.assignee = this.assigneeOptions[idx] || ''
			},
			/**
			 * 计划开始日期选择回调
			 * 若已选结束日期早于新的开始日期，清空结束日期提示用户重选
			 * @param {Object} e picker change 事件，e.detail.value 为 YYYY-MM-DD
			 */
			onStartChange(e) {
				this.form.startDate = e.detail.value
				if (this.form.endDate && this.form.endDate < this.form.startDate) {
					this.form.endDate = ''
				}
			},
			/**
			 * 计划结束日期选择回调
			 * @param {Object} e picker change 事件，e.detail.value 为 YYYY-MM-DD
			 */
			onEndChange(e) {
				this.form.endDate = e.detail.value
			},
			/**
			 * 里程碑开关回调
			 * @param {Object} e switch change 事件，e.detail.value 为布尔值
			 */
			onMilestoneChange(e) {
				this.form.isMilestone = e.detail.value
			},
			/**
			 * 表单校验
			 * @returns {string} 错误提示文案，通过校验返回空字符串
			 */
			validate() {
				const name = (this.form.name || '').trim()
				if (!name) return '请输入任务名称'
				if (name.length > 100) return '任务名称不能超过 100 字'
				if (!this.form.startDate) return '请选择计划开始日期'
				if (!this.form.endDate) return '请选择计划结束日期'
				// 结束日期必须晚于起始日期（同日允许，适配单日任务场景）
				if (this.form.endDate < this.form.startDate) {
					return '结束日期不能早于起始日期'
				}
				if ((this.form.description || '').length > 500) return '任务描述不能超过 500 字'
				return ''
			},
			/**
			 * 提交（新建或更新）
			 * 防重复提交：submitting 锁 + 按钮 :disabled
			 */
			async handleSubmit() {
				if (this.submitting) return
				const err = this.validate()
				if (err) return uni.showToast({ title: err, icon: 'none' })
				this.submitting = true
				uni.showLoading({ title: '保存中', mask: true })
				try {
					const payload = {
						name: this.form.name.trim(),
						assignee: this.form.assignee,
						startDate: this.form.startDate,
						endDate: this.form.endDate,
						isMilestone: this.form.isMilestone,
						description: this.form.description
					}
					// 新建模式传 parentId；编辑模式不传（parentId 只读，避免误改父子层级）
					if (!this.isEdit) {
						payload.parentId = this.form.parentId
					}
					if (this.isEdit) {
						await taskApi.update(this.taskId, payload)
					} else {
						await taskApi.create(this.projectId, payload)
					}
					uni.hideLoading()
					uni.showToast({ title: '保存成功', icon: 'success' })
					this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
				} catch (e) {
					uni.hideLoading()
					console.error('save task error', e)
				} finally {
					this.submitting = false
				}
			},
			/**
			 * 删除二次确认（仅编辑模式 + 有权限）
			 * 确认后调用 taskApi.remove，成功后返回上一页
			 */
			confirmDelete() {
				uni.showModal({
					title: '提示',
					content: `确认删除任务「${this.form.name}」吗？删除后不可恢复。`,
					confirmColor: '#FF4D4F',
					confirmText: '删除',
					success: async (res) => {
						if (!res.confirm) return
						uni.showLoading({ title: '删除中', mask: true })
						try {
							await taskApi.remove(this.taskId)
							uni.hideLoading()
							uni.showToast({ title: '删除成功', icon: 'success' })
							this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
						} catch (e) {
							uni.hideLoading()
							console.error('delete task error', e)
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
		/* 底部留出固定提交栏的空间 */
		padding: 24rpx 24rpx 160rpx;
	}

	/* 顶部导航栏（页内标题 + 操作区，参考 customer/list.vue 的 .nav-bar） */
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
		padding: 8rpx 20rpx;
		border-radius: 24rpx;
	}
	.nav-action.danger {
		color: #FF4D4F;
		background: #FFF1F0;
	}

	/* 表单卡片 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	/* 表单项（参考 customer/list.vue popup 表单的 .form-item） */
	.form-item {
		margin-bottom: 24rpx;
	}
	.form-item:last-child {
		margin-bottom: 0;
	}
	/* 行内表单项：标签与控件水平排列（用于 switch） */
	.form-item-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.form-item-row .form-label {
		margin-bottom: 0;
	}
	.form-label {
		display: block;
		font-size: 26rpx;
		color: #606266;
		margin-bottom: 12rpx;
	}
	/* 标签行：标签 + 计数器水平排列（用于 textarea） */
	.form-label-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 12rpx;
	}
	.form-label-row .form-label {
		margin-bottom: 0;
	}
	.req {
		color: #D4380D;
		margin-left: 4rpx;
	}
	.field-counter {
		font-size: 22rpx;
		color: #767E8A;
		text-align: right;
		margin-top: 8rpx;
	}

	/* 输入框（参考 customer/list.vue 的 .form-input） */
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

	/* 文本域 */
	.form-textarea {
		width: 100%;
		height: 200rpx;
		padding: 16rpx 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}

	/* 选择器输入框（picker 触发器，与 .form-input 视觉对齐） */
	.picker-input {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 72rpx;
		padding: 0 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
	}
	.picker-text {
		font-size: 26rpx;
		color: #303133;
	}
	.picker-text.ph {
		color: #767E8A;
	}
	.picker-arrow {
		font-size: 32rpx;
		color: #767E8A;
		line-height: 1;
	}

	/* 只读文本（编辑模式下的父任务展示） */
	.readonly-text {
		height: 72rpx;
		line-height: 72rpx;
		padding: 0 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #606266;
	}

	/* 占位符颜色 */
	.ph {
		color: #767E8A;
	}

	/* 底部安全区占位 */
	.bottom-safe {
		height: calc(40rpx + env(safe-area-inset-bottom));
	}

	/* 底部提交按钮栏（固定定位，参考 report.vue 的 .submit-bar） */
	.submit-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
		box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
		z-index: 100;
	}
	.submit-btn {
		width: 100%;
		height: 88rpx;
		line-height: 88rpx;
		background: linear-gradient(135deg, #1890FF 0%, #096DD9 100%);
		color: #FFFFFF;
		font-size: 32rpx;
		font-weight: 600;
		border-radius: 12rpx;
		border: none;
		&::after { border: none; }
		&[disabled] { background: #B3D9FF; color: #5C6370; }
	}
</style>
