<!--
  进度填报页
  业务用途：填报单个任务的进度百分比、里程碑/关键节点标记、进度说明、施工图片。
           提交后生成待审核进度日志（biz_task_progress_log），不立即更新任务进度，
           需 PM 审核通过后才同步任务与项目进度。
  数据来源：taskApi.getDetail（任务详情）；提交时调用 taskApi.update（标记）+ reportProgress（进度）。
  导航特性：非 tabBar 页，通过 url 参数 taskId、projectId 接收；提交成功后返回上一页。
-->
<template>
	<view class="page">
		<!-- 任务信息 -->
		<view class="card">
			<view class="task-title">
				<text class="task-name">{{ task.name || '加载中...' }}</text>
				<text v-if="task.isMilestone" class="tag tag-milestone">里程碑</text>
				<text v-else-if="task.isKey" class="tag tag-key">关键</text>
			</view>
			<view class="task-meta">
				<text class="meta-item"><image class="meta-svg" src="/static/icon/日期.svg" mode="aspectFit" aria-hidden="true" /> {{ formatDate(task.startDate) }} ~ {{ formatDate(task.endDate) }}</text>
			<text v-if="task.assignee" class="meta-item"><image class="meta-svg" src="/static/icon/我的.svg" mode="aspectFit" aria-hidden="true" /> {{ task.assignee }}</text>
			</view>
			<view v-if="task.description" class="task-desc">{{ task.description }}</view>
		</view>

		<!-- 当前进度 -->
		<view class="card">
			<view class="card-title">当前进度</view>
			<view class="current-progress">
				<view class="progress-ring-large" :style="ringStyle">
					<view class="ring-inner">
						<view class="ring-num">{{ task.progress || 0 }}<text class="unit">%</text></view>
						<view class="ring-label">已填报</view>
					</view>
				</view>
				<view class="current-info">
					<view class="info-line">
						<text class="info-label">任务状态</text>
						<view class="info-val" :style="statusStyle(task.status)">{{ taskStatusText(task.status) }}</view>
					</view>
					<view class="info-line">
						<text class="info-label">距离截止</text>
						<text class="info-val" :class="deadlineClass">{{ deadlineText }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 进度填报 -->
		<view class="card">
			<view class="card-title">进度填报</view>

			<!-- 进度百分比 -->
			<view class="field-block">
				<view class="field-label">
					<text class="label-text">进度百分比</text>
					<text class="label-value">{{ form.progress }}%</text>
				</view>
				<slider
					class="slider"
					:min="0"
					:max="100"
					:step="5"
					:value="form.progress"
					activeColor="#1890FF"
					backgroundColor="#EBEEF5"
					block-size="24"
					@change="onProgressChange"
				/>
				<view class="quick-progress">
					<view class="qp-btn" role="button" @click="setProgress(25)">25%</view>
					<view class="qp-btn" role="button" @click="setProgress(50)">50%</view>
					<view class="qp-btn" role="button" @click="setProgress(75)">75%</view>
					<view class="qp-btn" role="button" @click="setProgress(100)">完成</view>
				</view>
			</view>

			<!-- 关键节点标记 -->
			<view class="field-block">
				<view class="field-label">
					<text class="label-text">关键节点标记</text>
				</view>
				<view class="marker-row">
					<view
					class="marker-item"
					:class="{ active: form.isMilestone }"
					role="button"
					@click="form.isMilestone = !form.isMilestone"
				>
					<image class="marker-icon" src="/static/icon/奖杯.svg" mode="aspectFit" aria-hidden="true" />
					<text class="marker-text">里程碑</text>
				</view>
					<view
					class="marker-item"
					:class="{ active: form.isKey }"
					role="button"
					@click="form.isKey = !form.isKey"
				>
					<image class="marker-icon" src="/static/icon/五角星.svg" mode="aspectFit" aria-hidden="true" />
					<text class="marker-text">关键节点</text>
				</view>
				</view>
				<view class="marker-tip">标记为里程碑或关键节点后，将在首页关键节点提醒中展示</view>
			</view>

			<!-- 实际开工/结束时间 -->
			<view class="field-block">
				<view class="field-label">
					<text class="label-text">实际开工时间</text>
					<text class="required-mark">*</text>
				</view>
				<picker mode="date" :value="form.actualStartDate" @change="onActualStartChange">
					<view class="date-input" role="button" aria-label="选择实际开工时间">
						<text :class="['date-text', !form.actualStartDate && 'ph']">
							{{ form.actualStartDate || '请选择实际开工时间' }}
						</text>
						<text class="date-arrow">›</text>
					</view>
				</picker>
			</view>
			<view class="field-block">
				<view class="field-label">
					<text class="label-text">实际结束时间</text>
					<text class="required-mark">*</text>
				</view>
				<picker mode="date" :value="form.actualEndDate" @change="onActualEndChange">
					<view class="date-input" role="button" aria-label="选择实际结束时间">
						<text :class="['date-text', !form.actualEndDate && 'ph']">
							{{ form.actualEndDate || '请选择实际结束时间' }}
						</text>
						<text class="date-arrow">›</text>
					</view>
				</picker>
			</view>

			<!-- 进度说明 -->
			<view class="field-block">
				<view class="field-label">
					<text class="label-text">进度说明</text>
					<text class="label-counter">{{ form.remark.length }}/500</text>
				</view>
				<textarea
					class="textarea"
					v-model="form.remark"
					placeholder="请输入本次进度更新的文字描述，如：完成XX阶段、遇到XX问题、下一步计划..."
					placeholder-class="ph"
					maxlength="500"
					:auto-height="false"
					aria-label="进度说明"
				/>
			</view>
		</view>

		<!-- 施工图片上传 -->
		<view class="card">
			<view class="card-title">
				施工图片
				<text class="card-tip">最多 6 张，记录施工进展</text>
			</view>
			<view class="image-grid">
				<!-- 已选图片预览 -->
				<view
				v-for="(img, idx) in form.images"
				:key="idx"
				class="image-item"
				role="img"
				:aria-label="'施工图片第' + (idx + 1) + '张'"
			>
				<image class="image-preview" :src="img.previewUrl || img.url" mode="aspectFill" @click="previewImage(idx)" />
				<view class="image-del" role="button" :aria-label="'删除第' + (idx + 1) + '张图片'" @click="removeImage(idx)">×</view>
					<view v-if="img.uploading" class="image-mask">
						<view class="upload-spinner"></view>
						<text class="uploading-text">上传中</text>
					</view>
				</view>
				<!-- 添加图片按钮 -->
				<view
				v-if="form.images.length < 6"
				class="image-add"
				hover-class="uni-hover"
				:hover-stay-time="80"
				role="button"
				aria-label="上传施工图片"
				@click="chooseImage"
			>
					<text class="add-icon">+</text>
					<text class="add-text">{{ form.images.length }}/6</text>
				</view>
			</view>
		</view>

		<!-- 提交按钮 -->
		<view class="submit-bar">
			<button class="submit-btn" :loading="submitting" :disabled="submitting" @click="handleSubmit">
				{{ submitting ? '提交中...' : '提交进度' }}
			</button>
		</view>
	</view>
</template>

<script>
	import { taskApi } from '@/api/task'
	import { formatDate, getTaskStatus } from '@/utils/format'
	import { statusMixin } from '@/utils/statusMixin'
	import { API_BASE_URL } from '@/utils/config'

	export default {
		mixins: [statusMixin],
		data() {
			return {
				navBackTimer: null,  // 延迟返回定时器（onUnload 清理）
				taskId: '',        // 任务ID（来自路由参数）
				projectId: '',     // 项目ID（来自路由参数）
				task: {},          // 任务详情
				form: {            // 填报表单
				progress: 0,       // 进度百分比
				remark: '',        // 进度说明
				isMilestone: false,// 是否里程碑
				isKey: false,      // 是否关键节点
				images: [],        // 施工图片数组 [{ url, previewUrl, uploading }]
				actualStartDate: '',      // 实际开工时间（必填，YYYY-MM-DD）
				actualEndDate: ''        // 实际结束时间（必填，YYYY-MM-DD）
			},
				submitting: false  // 提交中标记
			}
		},
		computed: {
			/** 进度环的 conic-gradient 内联样式（按当前任务进度渲染） */
			ringStyle() {
				const p = this.task.progress || 0
				return {
					background: `conic-gradient(#1890FF 0deg, #52C41A ${p * 3.6}deg, #EBEEF5 ${p * 3.6}deg)`
				}
			},
			/** 距离截止日期的文案（逾期/今日截止/剩余天数） */
			deadlineText() {
				if (!this.task.endDate) return '-'
				const end = new Date(this.task.endDate)
				const now = new Date()
				const days = Math.ceil((end - now) / (1000 * 60 * 60 * 24))
				if (days < 0) return `已逾期 ${Math.abs(days)} 天`
				if (days === 0) return '今日截止'
				return `还剩 ${days} 天`
			},
			/** 截止文案的样式类：逾期红/临近黄/正常绿 */
			deadlineClass() {
				if (!this.task.endDate) return ''
				const days = Math.ceil((new Date(this.task.endDate) - new Date()) / (1000 * 60 * 60 * 24))
				if (days < 0) return 'danger'
				if (days <= 3) return 'warning'
				return 'success'
			}
		},
		// 页面加载：接收路由参数并加载任务详情
		onLoad(opts) {
			this.taskId = opts.taskId
			this.projectId = opts.projectId
			this.loadTask()
		},
		onUnload() {
			if (this.navBackTimer) {
				clearTimeout(this.navBackTimer)
				this.navBackTimer = null
			}
		},
		methods: {
			formatDate,
			// taskStatusText 由 statusMixin 提供
			/** 任务状态标签内联样式 */
			statusStyle(s) {
				const o = getTaskStatus(s)
				return { color: o.color, background: o.color + '20', padding: '4rpx 16rpx', borderRadius: '8rpx', fontSize: '24rpx' }
			},
			/** 加载任务详情并回填表单 */
			async loadTask() {
				if (!this.taskId) return
				uni.showLoading({ title: '加载中', mask: true })
				try {
					const task = await taskApi.getDetail(this.taskId)
					this.task = task || {}
					this.form.progress = this.task.progress || 0
					this.form.remark = this.task.remark || ''
					this.form.isMilestone = !!this.task.isMilestone
					this.form.isKey = !!this.task.isKey
				} catch (e) {
					console.warn('task error', e)
				} finally {
					uni.hideLoading()
				}
			},
			/** slider 拖动回调：更新进度值 */
			onProgressChange(e) {
				this.form.progress = e.detail.value
			},
			/** 快捷设置进度百分比 */
			setProgress(v) {
				this.form.progress = v
			},
			/** 实际开工时间选择回调 */
			onActualStartChange(e) {
				this.form.actualStartDate = e.detail.value
			},
			/** 实际结束时间选择回调 */
			onActualEndChange(e) {
				this.form.actualEndDate = e.detail.value
			},
			/**
			 * 选择施工图片：调用 uni.chooseImage 最多 6 张，逐张上传
			 */
			chooseImage() {
				const remain = 6 - this.form.images.length
				if (remain <= 0) {
					return uni.showToast({ title: '最多上传 6 张', icon: 'none' })
				}
				uni.chooseImage({
					count: remain,
					sizeType: ['compressed'],
					sourceType: ['album', 'camera'],
					success: (res) => {
						const paths = res.tempFilePaths || []
						paths.forEach((p) => this.uploadOne(p))
					}
				})
			},
			/**
			 * 上传单张图片
			 * @param {string} filePath 临时文件路径
			 */
			async uploadOne(filePath) {
				// 使用唯一 uid 标识占位图，避免上传期间用户删除前置图片导致下标错位
				const uid = Date.now() + '_' + Math.random().toString(36).slice(2, 8)
				// 先占位，上传中显示遮罩
				const placeholder = { url: filePath, previewUrl: filePath, uploading: true, uid }
				this.form.images.push(placeholder)
				try {
					const data = await taskApi.uploadFile(filePath)
					// 上传成功：按 uid 查找并替换占位图，不依赖下标
					const idx = this.form.images.findIndex(i => i.uid === uid)
					if (idx !== -1) {
						// 后端返回 url 为相对路径（如 2026/07/25/xxx.png），
						// previewUrl 为 /files/preview/相对路径
						// 小程序需要完整 URL 才能显示图片
						const relativeUrl = data.url || ''
						const fullPreviewUrl = relativeUrl
							? API_BASE_URL + '/files/preview/' + relativeUrl.replace(/^\//, '')
							: filePath
						this.form.images.splice(idx, 1, {
							url: relativeUrl || filePath,
							previewUrl: fullPreviewUrl,
							uploading: false,
							uid
						})
					}
				} catch (e) {
					// 上传失败：按 uid 查找并移除占位图
					const idx = this.form.images.findIndex(i => i.uid === uid)
					if (idx !== -1) {
						this.form.images.splice(idx, 1)
					}
					// 错误提示已由 uploadFile 内部处理
				}
			},
			/**
			 * 预览图片
			 * @param {number} index 当前点击的图片索引
			 */
			previewImage(index) {
				const urls = this.form.images.map((i) => i.previewUrl || i.url)
				uni.previewImage({ current: urls[index], urls })
			},
			/**
			 * 移除已选图片
			 * @param {number} index 图片索引
			 */
			removeImage(index) {
				this.form.images.splice(index, 1)
			},
			/**
			* 提交填报：先更新关键节点标记，再提交进度（含图片/实际时间），
			* 成功后返回上一页
			*/
		async handleSubmit() {
			if (this.submitting) return
			const { progress, remark, isMilestone, isKey, images, actualStartDate, actualEndDate } = this.form
			if (progress < 0 || progress > 100) {
				return uni.showToast({ title: '进度范围 0-100', icon: 'none' })
			}
			if (!actualStartDate) {
				return uni.showToast({ title: '请选择实际开工时间', icon: 'none' })
			}
			if (!actualEndDate) {
				return uni.showToast({ title: '请选择实际结束时间', icon: 'none' })
			}
			if (actualStartDate > actualEndDate) {
				return uni.showToast({ title: '开工时间不能晚于结束时间', icon: 'none' })
			}
			// 校验是否有图片上传中
			if (images.some((i) => i.uploading)) {
				return uni.showToast({ title: '图片上传中，请稍候', icon: 'none' })
			}
			this.submitting = true
			try {
				// 1. 先更新关键节点标记（如接口支持）
				try {
					await taskApi.update(this.taskId, {
						name: this.task.name,
						isMilestone,
						isKey,
						projectId: this.projectId
					})
				} catch (e) {
					console.warn('update marker failed', e)
				}
				// 2. 填报进度（含图片 URL 数组、实际开工/结束时间）
				await taskApi.reportProgress(this.taskId, {
					progress,
					remark,
					images: images.map((i) => i.url),
					actualStartDate,
					actualEndDate
				})
				uni.showToast({ title: '进度填报成功', icon: 'success' })
				this.navBackTimer = setTimeout(() => uni.navigateBack(), 800)
			} catch (e) {
				console.error('report error', e)
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
		padding: 24rpx 24rpx 160rpx;
	}
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.task-title {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		font-size: 32rpx;
		font-weight: 700;
		color: #303133;
		margin-bottom: 12rpx;
	}
	.task-name {
		flex: 1;
		min-width: 0;
	}
	.tag {
		margin-left: 12rpx;
		padding: 4rpx 14rpx;
		border-radius: 6rpx;
		font-size: 22rpx;
		flex-shrink: 0;
	}
	.tag-milestone { background: #FFF7E6; color: #D48806; }
	.tag-key { background: #FFF1F0; color: #D4380D; }
	.task-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
		margin-bottom: 12rpx;
	}
	.meta-item {
		font-size: 24rpx;
		color: #5C6370;
		display: flex;
		align-items: center;
	}
	.meta-svg {
		width: 24rpx;
		height: 24rpx;
		margin-right: 6rpx;
	}
	.task-desc {
		padding: 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 24rpx;
		color: #606266;
		line-height: 1.6;
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

	.current-progress {
		display: flex;
		align-items: center;
	}
	.progress-ring-large {
		width: 200rpx;
		height: 200rpx;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 32rpx;
		flex-shrink: 0;
	}
	.ring-inner {
		width: 160rpx;
		height: 160rpx;
		border-radius: 50%;
		background: #FFFFFF;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}
	.ring-num {
		font-size: 48rpx;
		font-weight: 700;
		color: #303133;
		line-height: 1;
	}
	.unit {
		font-size: 24rpx;
		color: #5C6370;
		margin-left: 2rpx;
	}
	.ring-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}
	.current-info {
		flex: 1;
	}
	.info-line {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #F5F7FA;
	}
	.info-line:last-child { border-bottom: none; }
	.info-label {
		font-size: 26rpx;
		color: #5C6370;
	}
	.info-val {
		font-size: 26rpx;
		color: #303133;
		font-weight: 500;
	}
	.info-val.warning { color: #D48806; }
	.info-val.danger { color: #D4380D; }
	.info-val.success { color: #389E0D; }

	.field-block {
		margin-bottom: 32rpx;
	}
	.field-block:last-child { margin-bottom: 0; }
	.field-label {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.label-text {
		font-size: 28rpx;
		font-weight: 600;
		color: #303133;
	}
	.label-value {
		font-size: 36rpx;
		font-weight: 700;
		color: #096DD9;
	}
	.label-counter {
		font-size: 22rpx;
		color: #767E8A;
	}

	.slider {
		margin: 0 4rpx;
	}

	.quick-progress {
		display: flex;
		gap: 12rpx;
		margin-top: 12rpx;
	}
	.qp-btn {
		flex: 1;
		text-align: center;
		padding: 14rpx 0;
		font-size: 24rpx;
		color: #096DD9;
		background: #E6F7FF;
		border-radius: 10rpx;
	}
	.qp-btn.small {
		flex: 0 0 auto;
		padding: 10rpx 20rpx;
		font-size: 22rpx;
	}

	.marker-row {
		display: flex;
		gap: 16rpx;
	}
	.marker-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 24rpx;
		background: #F5F7FA;
		border-radius: 12rpx;
		border: 2rpx solid transparent;
	}
	.marker-item.active {
		background: #E6F7FF;
		border-color: #1890FF;
	}
	.marker-icon {
		width: 40rpx;
		height: 40rpx;
		margin-bottom: 8rpx;
	}
	.marker-text {
		font-size: 26rpx;
		color: #606266;
	}
	.marker-item.active .marker-text {
		color: #096DD9;
		font-weight: 600;
	}
	.marker-tip {
		margin-top: 12rpx;
		font-size: 22rpx;
		color: #5C6370;
		line-height: 1.5;
	}

	/* 必填标记 */
	.required-mark {
		color: #D4380D;
		font-size: 28rpx;
		margin-left: 4rpx;
	}

	/* 日期选择器 */
	.date-input {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 88rpx;
		padding: 0 24rpx;
		background: #F5F7FA;
		border-radius: 12rpx;
	}
	.date-text {
		font-size: 28rpx;
		color: #303133;
	}
	.date-text.ph {
		color: #767E8A;
	}
	.date-arrow {
		font-size: 36rpx;
		color: #767E8A;
		line-height: 1;
	}

	.textarea {
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

	/* 图片上传 */
	.image-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 16rpx;
	}
	.image-item {
		position: relative;
		width: 200rpx;
		height: 200rpx;
		border-radius: 12rpx;
		overflow: hidden;
	}
	.image-preview {
		width: 100%;
		height: 100%;
		display: block;
	}
	.image-del {
		position: absolute;
		top: 4rpx;
		right: 4rpx;
		width: 40rpx;
		height: 40rpx;
		border-radius: 50%;
		background: rgba(0, 0, 0, 0.55);
		color: #FFFFFF;
		font-size: 28rpx;
		line-height: 40rpx;
		text-align: center;
	}
	.image-mask {
		position: absolute;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}
	.upload-spinner {
		width: 40rpx;
		height: 40rpx;
		border: 4rpx solid rgba(255, 255, 255, 0.3);
		border-top-color: #FFFFFF;
		border-radius: 50%;
		animation: spin 0.8s linear infinite;
	}
	@keyframes spin {
		to { transform: rotate(360deg); }
	}
	.uploading-text {
		color: #FFFFFF;
		font-size: 20rpx;
		margin-top: 8rpx;
	}
	.image-add {
		width: 200rpx;
		height: 200rpx;
		border-radius: 12rpx;
		border: 2rpx dashed #C0C4CC;
		background: #FAFAFA;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}
	.add-icon {
		font-size: 56rpx;
		color: #767E8A;
		line-height: 1;
	}
	.add-text {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}

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
