<!--
  文档上传页
  业务用途：上传项目相关文档（技术文档/合同文件/验收报告/施工图纸/其他）到后端，
           供项目资料中心展示与预览。提交时使用 multipart/form-data 直传文件到 /documents。
  数据来源：projectApi.getList（拉取未归档项目作为下拉选项，客户端过滤 status === '已归档'）；
           提交目标 POST /documents（与 Web 端共享后端接口契约，权限 document:add）。
  导航特性：非 tabBar 页；可通过 url 参数 projectId 预选所属项目；提交成功后 navigateBack 返回上一页。
-->
<template>
	<view class="page">
		<!-- 表单卡片 -->
		<view class="card">
			<!-- 所属项目（picker，仅未归档项目） -->
			<view class="form-item">
				<text class="form-label">所属项目<text class="req">*</text></text>
				<picker
					:range="projectNames"
					:value="projectIndex"
					@change="onProjectChange"
				>
					<view class="form-input picker-input" role="button" aria-label="选择所属项目">
						<text :class="['picker-text', projectIndex < 0 && 'ph']">
							{{ projectIndex >= 0 ? projectNames[projectIndex] : '请选择所属项目' }}
						</text>
						<text class="picker-arrow">›</text>
					</view>
				</picker>
			</view>

			<!-- 文档类型（picker） -->
			<view class="form-item">
				<text class="form-label">文档类型<text class="req">*</text></text>
				<picker
					:range="docTypes"
					:value="docTypeIndex"
					@change="onDocTypeChange"
				>
					<view class="form-input picker-input" role="button" aria-label="选择文档类型">
						<text :class="['picker-text', docTypeIndex < 0 && 'ph']">
							{{ docTypeIndex >= 0 ? docTypes[docTypeIndex] : '请选择文档类型' }}
						</text>
						<text class="picker-arrow">›</text>
					</view>
				</picker>
			</view>

			<!-- 文档名称（必填） -->
			<view class="form-item">
				<text class="form-label">文档名称<text class="req">*</text></text>
				<input
					class="form-input"
					v-model="form.name"
					placeholder="请输入文档名称"
					placeholder-class="ph"
					maxlength="100"
					aria-label="文档名称"
				/>
			</view>

			<!-- 描述（可选，200 字限制） -->
			<view class="form-item">
				<text class="form-label">
					描述
					<text class="label-counter">{{ form.description.length }}/200</text>
				</text>
				<textarea
					class="form-textarea"
					v-model="form.description"
					placeholder="可选，请输入文档描述（不超过 200 字）"
					placeholder-class="ph"
					maxlength="200"
					:auto-height="false"
					aria-label="文档描述"
				/>
			</view>

			<!-- 文件选择（uni.chooseMessageFile） -->
			<view class="form-item">
				<text class="form-label">选择文件<text class="req">*</text></text>
				<!-- 未选文件：展示选择入口 -->
				<view
					v-if="!file"
					class="file-picker"
					hover-class="uni-hover"
					:hover-stay-time="80"
					role="button"
					aria-label="选择文件"
					@click="chooseFile"
				>
					<text class="file-picker-icon">＋</text>
					<text class="file-picker-text">点击选择文件</text>
					<text class="file-picker-tip">支持 pdf/doc/docx/xls/xlsx/jpg/png，不超过 100MB</text>
				</view>
				<!-- 已选文件：展示文件名 + 大小，支持移除重新选择 -->
				<view v-else class="file-card">
					<view class="file-info">
						<text class="file-name">{{ file.name }}</text>
						<text class="file-size">{{ formatFileSize(file.size) }}</text>
					</view>
					<view
						class="file-remove"
						hover-class="uni-hover"
						:hover-stay-time="80"
						role="button"
						aria-label="移除文件并重新选择"
						@click="removeFile"
					>重新选择</view>
				</view>
			</view>
		</view>

		<!-- 提交按钮（底部固定栏） -->
		<view class="submit-bar">
			<button
				class="submit-btn"
				:loading="submitting"
				:disabled="submitting"
				@click="handleSubmit"
			>{{ submitting ? '上传中...' : '上传文档' }}</button>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { projectApi } from '@/api/project'
	import { auth } from '@/utils/auth'
	import { API_BASE_URL } from '@/utils/config'
	import { formatFileSize } from '@/utils/format'

	// 允许选择的文件扩展名（与后端配置一致）
	const ALLOWED_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'jpg', 'png']
	// 文件大小上限：100MB（与后端配置一致）
	const MAX_FILE_SIZE = 100 * 1024 * 1024

	export default {
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				// 路由参数：预选项目 ID（可选，由上一页通过 url 传入）
				presetProjectId: '',
				// 项目下拉选项数据源（仅未归档项目）
				projects: [],
				// 项目 picker 当前选中下标（-1 表示未选中）
				projectIndex: -1,
				// 文档类型选项
				docTypes: ['技术文档', '合同文件', '验收报告', '施工图纸', '其他'],
				// 文档类型 picker 当前选中下标（-1 表示未选中）
				docTypeIndex: -1,
				// 表单字段
				form: {
					name: '',            // 文档名称（必填）
					description: ''      // 描述（可选，200 字限制）
				},
				// 已选文件 { name, size, path }；null 表示未选择
				file: null,
				// 提交中标记（防重复提交锁）
				submitting: false,
				// 项目列表加载中标记
				loadingProjects: false
			}
		},
		computed: {
			/** 项目 picker 显示用的名称数组 */
			projectNames() {
				return this.projects.map((p) => p.name || '')
			},
			/** 当前选中的项目对象（用于提交时取 projectId） */
			selectedProject() {
				return this.projectIndex >= 0 ? this.projects[this.projectIndex] : null
			}
		},
		// 页面加载：接收路由参数并拉取项目列表
		onLoad(opts) {
			this.presetProjectId = (opts && opts.projectId) || ''
			this.loadProjects()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			formatFileSize,
			/**
			 * 加载未归档项目列表
			 * 拉取一页较大条数（200），客户端过滤掉 status === '已归档' 的项目，
			 * 作为项目下拉选项数据源；若路由传入 presetProjectId 则自动定位到对应下标。
			 */
			async loadProjects() {
				this.loadingProjects = true
				try {
					const res = await projectApi.getList({ page: 1, size: 200 })
					// 兼容 {records}/{list}/数组 多种返回结构
					const list = (res && (res.records || res.list || res)) || []
					// 仅保留未归档项目
					this.projects = list.filter((p) => p.status !== '已归档')
					// 路由预选项目：按 id 匹配定位下标
					if (this.presetProjectId) {
						const idx = this.projects.findIndex(
							(p) => String(p.id) === String(this.presetProjectId)
						)
						if (idx >= 0) this.projectIndex = idx
					}
				} catch (e) {
					console.warn('load projects error', e)
					uni.showToast({ title: '项目列表加载失败', icon: 'none' })
				} finally {
					this.loadingProjects = false
				}
			},
			/** 项目 picker 选择回调 */
			onProjectChange(e) {
				this.projectIndex = Number(e.detail.value)
			},
			/** 文档类型 picker 选择回调 */
			onDocTypeChange(e) {
				this.docTypeIndex = Number(e.detail.value)
			},
			/**
			 * 选择文件：调用 uni.chooseMessageFile（微信小程序可用）
			 * 限制 count=1、按扩展名过滤；选中后校验大小并回填 file 字段
			 */
			chooseFile() {
				uni.chooseMessageFile({
					count: 1,
					extension: ALLOWED_EXTENSIONS,
					success: (res) => {
						const f = (res.tempFiles && res.tempFiles[0]) || null
						if (!f) return
						// 大小校验：超过 100MB 拒绝，提示用户压缩或拆分
						if (f.size > MAX_FILE_SIZE) {
							uni.showToast({
								title: '文件超过 100MB，请压缩或拆分后上传',
								icon: 'none'
							})
							return
						}
						this.file = {
							name: f.name,
							size: f.size,
							path: f.path
						}
					},
					fail: (err) => {
						// 用户取消选择时不提示（errMsg 含 cancel）
						if (err && err.errMsg && err.errMsg.includes('cancel')) return
						console.warn('chooseFile error', err)
					}
				})
			},
			/** 移除已选文件，允许重新选择 */
			removeFile() {
				this.file = null
			},
			/**
			 * 提交上传
			 * 1. 校验必填字段（项目/类型/名称/文件）
			 * 2. 使用 uni.uploadFile 直传到 /documents：
			 *    - header 注入 Authorization: Bearer ${token}（不手动设置 Content-Type，由底层自动生成 boundary）
			 *    - formData 携带 projectId/documentType/name/description
			 *    - name: 'file'
			 * 3. 通过 progress 回调更新 uni.showLoading 标题展示上传百分比
			 * 4. 成功（body.code === 200）提示并 navigateBack；失败提示具体错误
			 * 5. submitting 锁防止重复提交
			 */
			handleSubmit() {
				// 防重复提交锁
				if (this.submitting) return
				// 必填字段校验
				if (!this.selectedProject) {
					return uni.showToast({ title: '请选择所属项目', icon: 'none' })
				}
				if (this.docTypeIndex < 0) {
					return uni.showToast({ title: '请选择文档类型', icon: 'none' })
				}
				if (!this.form.name || !this.form.name.trim()) {
					return uni.showToast({ title: '请输入文档名称', icon: 'none' })
				}
				if (!this.file) {
					return uni.showToast({ title: '请选择要上传的文件', icon: 'none' })
				}

				this.submitting = true
				uni.showLoading({ title: '上传中 0%', mask: true })

				const token = auth.getToken()
				const uploadTask = uni.uploadFile({
					url: `${API_BASE_URL}/documents`,
					filePath: this.file.path,
					name: 'file',
					// 注意：不能手动设置 Content-Type，需由底层自动生成 multipart boundary
					header: {
						Authorization: token ? `Bearer ${token}` : ''
					},
					formData: {
						projectId: String(this.selectedProject.id),
						documentType: this.docTypes[this.docTypeIndex],
						name: this.form.name.trim(),
						description: this.form.description || ''
					},
					success: (res) => {
						uni.hideLoading()
						// res.data 为字符串，需手动 parse 后端响应体
						let body = {}
						try {
							body = typeof res.data === 'string' ? JSON.parse(res.data) : (res.data || {})
						} catch (e) {
							console.warn('parse upload response error', e)
						}
						// HTTP 状态非 2xx 视为失败
						if (res.statusCode < 200 || res.statusCode >= 300) {
							const msg = body.message || `上传失败(${res.statusCode})`
							uni.showToast({ title: msg, icon: 'none' })
							return
						}
						// 业务成功：提示并返回上一页
						if (body.code === 200) {
							uni.showToast({ title: '上传成功', icon: 'success' })
							this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
						} else {
							// 业务失败：展示后端返回的具体错误信息
							const msg = body.message || '上传失败'
							uni.showToast({ title: msg, icon: 'none' })
						}
					},
					fail: (err) => {
						uni.hideLoading()
						console.warn('upload fail', err)
						uni.showToast({ title: '上传失败，请检查网络后重试', icon: 'none' })
					},
					complete: () => {
						// 无论成功失败，统一释放提交锁
						this.submitting = false
					}
				})

				// progress 回调：实时更新 loading 标题展示上传百分比
				if (uploadTask && uploadTask.onProgressUpdate) {
					uploadTask.onProgressUpdate((res) => {
						const percent = res.progress || 0
						uni.showLoading({ title: `上传中 ${percent}%`, mask: true })
					})
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

	/* 表单卡片 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	/* 表单项结构（参考 pages/customer/list.vue） */
	.form-item {
		margin-bottom: 28rpx;
		&:last-child { margin-bottom: 0; }
	}
	.form-label {
		display: flex;
		align-items: center;
		justify-content: space-between;
		font-size: 26rpx;
		color: #606266;
		margin-bottom: 12rpx;
	}
	.req {
		color: #D4380D;
		margin-left: 4rpx;
	}
	.label-counter {
		font-size: 22rpx;
		color: #909399;
		font-weight: 400;
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
		height: 160rpx;
		padding: 16rpx 20rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 26rpx;
		color: #303133;
		box-sizing: border-box;
	}
	.ph { color: #767E8A; }

	/* picker 输入框样式 */
	.picker-input {
		display: flex;
		align-items: center;
		justify-content: space-between;
		line-height: 72rpx;
	}
	.picker-text {
		flex: 1;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.picker-arrow {
		color: #909399;
		font-size: 32rpx;
		margin-left: 12rpx;
	}

	/* 文件选择区 */
	.file-picker {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 56rpx 24rpx;
		background: #F5F7FA;
		border: 2rpx dashed #C0C4CC;
		border-radius: 12rpx;
	}
	.file-picker-icon {
		font-size: 56rpx;
		color: #909399;
		line-height: 1;
		margin-bottom: 12rpx;
	}
	.file-picker-text {
		font-size: 28rpx;
		color: #303133;
		margin-bottom: 8rpx;
	}
	.file-picker-tip {
		font-size: 22rpx;
		color: #909399;
	}

	/* 已选文件卡片 */
	.file-card {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20rpx;
		background: #F5F7FA;
		border-radius: 12rpx;
	}
	.file-info {
		flex: 1;
		min-width: 0;
		margin-right: 16rpx;
	}
	.file-name {
		display: block;
		font-size: 28rpx;
		color: #303133;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.file-size {
		display: block;
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 6rpx;
	}
	.file-remove {
		flex-shrink: 0;
		font-size: 24rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 8rpx 20rpx;
		border-radius: 24rpx;
	}

	/* 提交按钮栏（底部固定） */
	.submit-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		padding: 20rpx 24rpx;
		background: #FFFFFF;
		box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
		z-index: 10;
	}
	.submit-btn {
		width: 100%;
		height: 88rpx;
		line-height: 88rpx;
		font-size: 30rpx;
		background: #1890FF;
		color: #FFFFFF;
		border-radius: 12rpx;
		border: none;
		margin: 0;
		&::after { border: none; }
		&[disabled] {
			background: #B3D9FF;
			color: #FFFFFF;
		}
	}
	.bottom-safe { height: calc(40rpx + env(safe-area-inset-bottom)); }
</style>
