<!--
  客户详情页
  业务用途：展示客户基本信息（联系人/电话/邮箱/地址/行业/等级/备注/创建时间）、
           关联项目数及项目列表。PM/Admin 可编辑/删除客户，工程师仅可查看。
  数据来源：customerApi.getDetail（详情）。
  导航特性：非 tabBar 页，通过 url 参数 id 接收客户ID；
           可拨打电话、编辑（弹窗）、删除（确认后返回上一页）。
-->
<template>
	<view class="page">
		<!-- 客户信息头部卡片 -->
		<view class="card hero-card">
			<view class="cust-head">
				<view class="cust-name">{{ customer.name || '加载中...' }}</view>
				<view class="level-tag" :class="levelClass(customer.level)">{{ customer.level || '普通' }}</view>
			</view>
			<view class="info-row">
				<text class="info-label">联系人</text>
				<text class="info-val">{{ customer.contactPerson || '-' }}</text>
			</view>
			<view class="info-row clickable" role="button" @click="callPhone(customer.contactPhone)">
				<text class="info-label">联系电话</text>
				<view class="info-val phone-val">
					<text class="phone-text"><text aria-hidden="true">📞 </text>{{ customer.contactPhone || '-' }}</text>
					<text v-if="customer.contactPhone" class="call-btn">拨打</text>
				</view>
			</view>
			<view class="info-row">
				<text class="info-label">邮箱</text>
				<text class="info-val">{{ customer.email || '-' }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">地址</text>
				<text class="info-val">{{ customer.address || '-' }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">行业</text>
				<view class="info-val">
					<text v-if="customer.industry" class="industry-tag">{{ customer.industry }}</text>
					<text v-else>-</text>
				</view>
			</view>
			<view class="info-row">
				<text class="info-label">创建时间</text>
				<text class="info-val">{{ formatDate(customer.createTime) || '-' }}</text>
			</view>
			<view v-if="customer.remark" class="remark-block">
				<text class="remark-label">备注</text>
				<view class="remark-text">{{ customer.remark }}</view>
			</view>
		</view>

		<!-- 操作按钮（仅 PM/Admin 可见） -->
		<view v-if="canManage" class="action-bar">
			<button class="btn-edit" @click="openEdit">编辑</button>
			<button class="btn-delete" @click="confirmDelete">删除</button>
		</view>

		<!-- 关联项目 -->
		<view class="card">
			<view class="card-title-row">
				<text class="card-title">关联项目</text>
				<text class="count-tag">{{ customer.projectCount || 0 }} 个</text>
			</view>
			<view v-if="projects.length === 0" class="empty-mini">暂无关联项目</view>
			<view v-else>
				<view
					v-for="(p, idx) in projects"
					:key="p.id || idx"
					class="proj-item"
				role="link"
				:class="{ 'no-border': idx === projects.length - 1 }"
					hover-class="uni-hover"
					:hover-stay-time="80"
					@click="goProject(p)"
				>
					<view class="proj-name">{{ p.name }}</view>
					<view class="proj-meta">
						<text v-if="p.status" class="proj-status">{{ p.status }}</text>
						<text v-if="p.manager" class="proj-manager">负责人：{{ p.manager }}</text>
					</view>
				</view>
			</view>
		</view>

		<view class="bottom-safe"></view>

		<!-- 编辑表单弹窗 -->
		<view v-if="formVisible" class="popup-mask" role="button" aria-label="关闭弹窗" @click="formVisible=false">
			<view class="popup" @click.stop>
				<view class="popup-title">编辑客户</view>
				<scroll-view scroll-y class="popup-scroll">
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
				</scroll-view>
				<view class="popup-actions">
					<button class="btn-cancel" @click="formVisible=false">取消</button>
					<button class="btn-confirm" :loading="saving" :disabled="saving" @click="handleSave">保存</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { customerApi } from '@/api/customer'
	import { formatDate } from '@/utils/format'
	import { canManageCustomer } from '@/utils/permission'

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				customerId: '',        // 客户ID（来自路由参数）
				customer: {},          // 客户详情
				projects: [],          // 关联项目列表（若后端返回）
				canManage: canManageCustomer(), // 是否可管理客户（PM/admin）
				// 表单相关
				formVisible: false,
				saving: false,
				levelOptions: ['VIP', '普通'],
				form: this.emptyForm()
			}
		},
		// 页面加载：接收路由参数 id 并加载详情
		onLoad(opts) {
			this.customerId = opts.id
			this.loadDetail()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			formatDate,
			/** 空表单结构 */
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
			/** 加载客户详情（兼容 projects 字段） */
			async loadDetail() {
				uni.showLoading({ title: '加载中', mask: true })
				try {
					const data = await customerApi.getDetail(this.customerId)
					this.customer = data || {}
					if (Array.isArray(this.customer.projects)) {
						this.projects = this.customer.projects
					} else {
						this.projects = []
					}
				} catch (e) {
					console.warn('customer detail error', e)
					uni.showToast({ title: '加载失败', icon: 'none' })
				} finally {
					uni.hideLoading()
				}
			},
			/** 拨打联系电话 */
			callPhone(phone) {
				if (!phone) {
					return uni.showToast({ title: '暂无联系电话', icon: 'none' })
				}
				uni.makePhoneCall({ phoneNumber: String(phone) })
			},
			/** 打开编辑弹窗（回填表单） */
			openEdit() {
				this.form = {
					name: this.customer.name || '',
					contactPerson: this.customer.contactPerson || '',
					contactPhone: this.customer.contactPhone || '',
					email: this.customer.email || '',
					address: this.customer.address || '',
					industry: this.customer.industry || '',
					level: this.customer.level || '普通',
					remark: this.customer.remark || ''
				}
				this.formVisible = true
			},
			/** 删除确认 */
			confirmDelete() {
				uni.showModal({
					title: '提示',
					content: `确认删除客户「${this.customer.name}」吗？`,
					confirmColor: '#FF4D4F',
					success: async (res) => {
						if (!res.confirm) return
						uni.showLoading({ title: '删除中', mask: true })
						try {
							await customerApi.remove(this.customerId)
							uni.hideLoading()
							uni.showToast({ title: '删除成功', icon: 'success' })
							this.navBackTimer = setTimeout(() => uni.navigateBack(), 600)
						} catch (e) {
							uni.hideLoading()
							console.warn('delete error', e)
						}
					}
				})
			},
			/** 跳转项目详情 */
			goProject(p) {
				if (!p || !p.id) return
				uni.navigateTo({ url: `/pages/project/detail?id=${p.id}` })
			},
			/** 保存编辑 */
			async handleSave() {
				if (this.saving) return
				if (!this.form.name || !this.form.name.trim()) {
					return uni.showToast({ title: '请输入客户名称', icon: 'none' })
				}
				this.saving = true
				uni.showLoading({ title: '保存中', mask: true })
				try {
					await customerApi.update(this.customerId, { ...this.form })
					// 本地同步更新
					this.customer = { ...this.customer, ...this.form }
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

	/* 卡片通用 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 16rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.hero-card {
		padding: 32rpx 28rpx;
	}
	.cust-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20rpx;
		padding-bottom: 20rpx;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.cust-name {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
		flex: 1;
		margin-right: 16rpx;
	}
	.level-tag {
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.level-vip { background: #FFF7E6; color: #D48806; }
	.level-normal { background: #F4F4F5; color: #5C6370; }

	/* 信息行 */
	.info-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.info-row:last-of-type { border-bottom: none; }
	.info-row.clickable {
		&:active { background: #F5F7FA; }
	}
	.info-label {
		font-size: 26rpx;
		color: #5C6370;
		flex-shrink: 0;
	}
	.info-val {
		font-size: 26rpx;
		color: #303133;
		text-align: right;
		flex: 1;
		margin-left: 24rpx;
		display: flex;
		justify-content: flex-end;
		align-items: center;
	}
	.phone-val {
		gap: 16rpx;
	}
	.phone-text {
		color: #096DD9;
		font-size: 26rpx;
	}
	.call-btn {
		font-size: 22rpx;
		color: #FFFFFF;
		background: #1890FF;
		padding: 4rpx 16rpx;
		border-radius: 16rpx;
	}
	.industry-tag {
		font-size: 22rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
	}

	/* 备注 */
	.remark-block {
		margin-top: 16rpx;
		padding: 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
	}
	.remark-label {
		font-size: 24rpx;
		color: #5C6370;
		display: block;
		margin-bottom: 8rpx;
	}
	.remark-text {
		font-size: 26rpx;
		color: #606266;
		line-height: 1.6;
	}

	/* 操作按钮 */
	.action-bar {
		display: flex;
		gap: 16rpx;
		margin-bottom: 16rpx;
	}
	.btn-edit, .btn-delete {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		font-size: 28rpx;
		border-radius: 8rpx;
		border: none;
		margin: 0;
		&::after { border: none; }
	}
	.btn-edit {
		background: #1890FF;
		color: #FFFFFF;
	}
	.btn-delete {
		background: #FFFFFF;
		color: #D4380D;
		border: 1rpx solid #FF4D4F;
	}

	/* 关联项目 */
	.card-title-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.card-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
	}
	.count-tag {
		font-size: 24rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 4rpx 16rpx;
		border-radius: 16rpx;
	}
	.empty-mini {
		padding: 40rpx 0;
		text-align: center;
		font-size: 24rpx;
		color: #767E8A;
	}
	.proj-item {
		padding: 20rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.proj-item.no-border { border-bottom: none; }
	.proj-name {
		font-size: 28rpx;
		color: #303133;
		font-weight: 500;
		margin-bottom: 8rpx;
	}
	.proj-meta {
		display: flex;
		align-items: center;
		gap: 16rpx;
	}
	.proj-status {
		font-size: 22rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 2rpx 12rpx;
		border-radius: 4rpx;
	}
	.proj-manager {
		font-size: 22rpx;
		color: #5C6370;
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
</style>
