<!--
  文档预览页
  业务用途：展示文档信息与版本历史，对图片类型在线预览，其他类型引导下载。
  数据来源：documentApi.getDetail（详情）；图片预览与下载走 uni.downloadFile（需注入 Token）。
  导航特性：非 tabBar 页，通过 url 参数 id、name 接收；支持下载打开与保存图片到相册。
-->
<template>
	<view class="page">
		<!-- 文档信息卡 -->
		<view class="card">
			<view class="doc-head">
				<view class="doc-icon" :style="{ background: fileColor }">{{ fileExt }}</view>
				<view class="doc-info">
					<view class="doc-name">{{ doc.name || '加载中...' }}</view>
					<view class="doc-meta">
						<text v-if="doc.projectName" class="meta-text">{{ doc.projectName }}</text>
						<text class="meta-text">{{ doc.size || '-' }}</text>
						<text class="meta-text">{{ formatDate(doc.uploadDate) }}</text>
					</view>
				</view>
			</view>

			<view v-if="doc.description" class="doc-desc">{{ doc.description }}</view>

			<view v-if="doc.versions && doc.versions.length" class="versions">
				<view class="version-title">版本历史</view>
				<view v-for="(v, i) in doc.versions" :key="i" class="version-item">
					<view class="version-main">
						<text class="version-no">v{{ v.version }}</text>
						<text class="version-date">{{ formatDate(v.date) }}</text>
					</view>
					<text class="version-uploader">{{ v.uploader }}</text>
					<text v-if="v.remark" class="version-remark">{{ v.remark }}</text>
				</view>
			</view>
		</view>

		<!-- 预览区 -->
		<view class="preview-card">
			<view class="preview-head">
				<text class="preview-title">文档预览</text>
			<text v-if="previewUrl" class="preview-refresh" role="button" @click="loadPreview">刷新</text>
			</view>

			<view v-if="loading" class="preview-loading">
			<view class="loading-icon" aria-hidden="true"></view>
			<text class="loading-text">加载中...</text>
		</view>

			<!-- 图片预览 -->
		<view v-else-if="isImage && previewUrl" class="image-preview" role="img" aria-label="文档预览图片">
			<image
				:src="previewUrl"
				mode="widthFix"
				class="preview-image"
				@error="onImgError"
				@click="previewImage"
			/>
		</view>

			<!-- 不支持的类型 -->
		<view v-else class="preview-unsupported">
			<view class="uns-icon" aria-hidden="true"><image class="svg-icon-lg" src="/static/icon/资料.svg" mode="aspectFit" /></view>
			<view class="uns-text">此文件类型不支持在线预览</view>
			<view class="uns-tip">请下载后查看</view>
			<view class="download-btn" role="button" @click="handleDownload">
				<text>下载文件</text>
			</view>
		</view>
		</view>

		<!-- 底部操作 -->
	<view class="bottom-bar">
		<view class="bottom-btn btn-download" role="button" @click="handleDownload">
			<text class="btn-icon" aria-hidden="true">⬇</text>
			<text>下载</text>
		</view>
		<view v-if="isImage && previewUrl" class="bottom-btn btn-save" role="button" @click="saveImage">
			<text class="btn-icon" aria-hidden="true">💾</text>
			<text>保存图片</text>
		</view>
	</view>
	</view>
</template>

<script>
	import { documentApi } from '@/api/document'
	import { auth } from '@/utils/auth'
	import { API_BASE_URL } from '@/utils/config'
	import { formatDate, getFileTypeColor, getExt } from '@/utils/format'

	export default {
		data() {
			return {
				docId: '',        // 文档ID（来自路由参数）
				doc: {},          // 文档详情
				loading: true,    // 预览加载中标记
				previewUrl: '',   // 图片预览的临时文件路径
				tempPath: ''      // 已下载文件的临时路径
			}
		},
		computed: {
			/** 文档图标显示的扩展名文本 */
			fileExt() {
				return getExt(this.doc.name || this.doc.fileType) || (this.doc.fileType || 'FILE').substring(0, 3).toUpperCase()
			},
			/** 文档图标背景色（按文件类型区分） */
			fileColor() {
				return getFileTypeColor(getExt(this.doc.name || this.doc.fileType) || this.doc.fileType)
			},
			/** 是否为可在线预览的图片类型 */
			isImage() {
				const ext = getExt(this.doc.name || this.doc.fileType)
				return ['JPG', 'JPEG', 'PNG', 'GIF', 'BMP'].includes(ext)
			}
		},
		// 页面加载：接收路由参数，先用传入名称占位，再加载详情与预览
		onLoad(opts) {
			this.docId = opts.id
			if (opts.name) {
				this.doc = { name: decodeURIComponent(opts.name) }
			}
			this.loadAll()
		},
		methods: {
			formatDate,
			/** 并行加载详情与预览，完成后关闭 loading */
			async loadAll() {
				await Promise.allSettled([this.loadDetail(), this.loadPreview()])
				this.loading = false
			},
			/** 加载文档详情 */
			async loadDetail() {
				try {
					this.doc = await documentApi.getDetail(this.docId)
				} catch (e) {
					console.warn('detail error', e)
				}
			},
			/** 加载图片预览（仅图片类型；下载临时文件后展示） */
			async loadPreview() {
			// 图片类型：用 uni.downloadFile 拉取临时路径后展示，避免 base64 占用数倍内存
			// 其他类型：后端 preview 接口对图片/PDF 直接返回，其他类型会跳下载
			if (this.isImage) {
				try {
					const token = auth.getToken()
					const tempPath = await new Promise((resolve, reject) => {
						uni.downloadFile({
							url: `${API_BASE_URL}/documents/${this.docId}/preview`,
							header: { Authorization: token ? `Bearer ${token}` : '' },
							success: (r) => {
								if (r.statusCode === 200) resolve(r.tempFilePath)
								else reject(new Error('preview failed'))
							},
							fail: reject
						})
					})
					this.previewUrl = tempPath
					this.tempPath = tempPath
				} catch (e) {
					console.warn('preview error', e)
					this.previewUrl = ''
				}
			}
		},
			/** 图片加载失败：回退到不支持预览提示 */
			onImgError() {
				this.previewUrl = ''
			},
			/** 点击图片全屏预览 */
			previewImage() {
				if (!this.previewUrl) return
				uni.previewImage({
					urls: [this.previewUrl],
					current: this.previewUrl
				})
			},
			/** 下载文档并用系统能力打开 */
			async handleDownload() {
				uni.showLoading({ title: '下载中...', mask: true })
				try {
					const tempPath = await documentApi.download(this.docId)
					uni.hideLoading()
					this.tempPath = tempPath
					uni.openDocument({
						filePath: tempPath,
						showMenu: true,
						fail: () => {
							uni.showToast({ title: '本机暂不支持打开此文件，已下载', icon: 'none' })
						}
					})
				} catch (e) {
					uni.hideLoading()
					console.warn('download error', e)
				}
			},
			/** 保存图片到相册（先下载临时文件再保存） */
			async saveImage() {
				if (!this.previewUrl) return
				// 先下载到临时目录，再保存
				try {
					const tempPath = await documentApi.download(this.docId)
					uni.saveImageToPhotosAlbum({
						filePath: tempPath,
						success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
						fail: (err) => {
							// 用户拒绝相册权限：引导去设置开启
							if (err.errMsg.includes('auth deny')) {
								uni.showToast({ title: '请在设置中开启相册权限', icon: 'none' })
							} else {
								uni.showToast({ title: '保存失败', icon: 'none' })
							}
						}
					})
				} catch (e) {
					uni.showToast({ title: '保存失败', icon: 'none' })
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
		padding: 24rpx;
		margin-bottom: 20rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.doc-head {
		display: flex;
		align-items: center;
	}
	.doc-icon {
		width: 96rpx;
		height: 96rpx;
		border-radius: 14rpx;
		color: #FFFFFF;
		font-size: 24rpx;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 20rpx;
		flex-shrink: 0;
	}
	.doc-info { flex: 1; min-width: 0; }
	.doc-name {
		font-size: 32rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 10rpx;
	}
	.doc-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 16rpx;
	}
	.meta-text {
		font-size: 22rpx;
		color: #5C6370;
	}
	.doc-desc {
		margin-top: 16rpx;
		padding: 16rpx;
		background: #F5F7FA;
		border-radius: 8rpx;
		font-size: 24rpx;
		color: #606266;
		line-height: 1.6;
	}

	.versions {
		margin-top: 24rpx;
		border-top: 1rpx solid #F0F2F5;
		padding-top: 20rpx;
	}
	.version-title {
		font-size: 26rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 12rpx;
	}
	.version-item {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 16rpx;
		padding: 12rpx 0;
		font-size: 24rpx;
		color: #606266;
	}
	.version-main {
		display: flex;
		gap: 16rpx;
		align-items: center;
	}
	.version-no {
		color: #096DD9;
		font-weight: 600;
	}
	.version-date {
		color: #5C6370;
	}
	.version-uploader {
		color: #606266;
	}
	.version-remark {
		width: 100%;
		color: #5C6370;
		font-size: 22rpx;
	}

	.preview-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.preview-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20rpx;
	}
	.preview-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
	}
	.preview-refresh {
		font-size: 24rpx;
		color: #096DD9;
	}

	.preview-loading {
		padding: 120rpx 0;
		text-align: center;
	}
	.loading-icon {
		width: 56rpx;
		height: 56rpx;
		border: 4rpx solid #EBEEF5;
		border-top-color: #1890FF;
		border-radius: 50%;
		margin: 0 auto 16rpx;
		animation: spin 0.8s linear infinite;
	}
	@keyframes spin {
		to { transform: rotate(360deg); }
	}
	.loading-text {
		font-size: 24rpx;
		color: #5C6370;
	}

	.image-preview {
		text-align: center;
	}
	.preview-image {
		width: 100%;
		border-radius: 8rpx;
	}

	.preview-unsupported {
		padding: 80rpx 40rpx;
		text-align: center;
	}
	.uns-icon {
		font-size: 96rpx;
		margin-bottom: 24rpx;
		display: flex;
		justify-content: center;
		align-items: center;
	}
	.svg-icon-lg {
		width: 96rpx;
		height: 96rpx;
	}
	.uns-text {
		font-size: 28rpx;
		color: #606266;
		margin-bottom: 8rpx;
	}
	.uns-tip {
		font-size: 24rpx;
		color: #5C6370;
		margin-bottom: 32rpx;
	}
	.download-btn {
		display: inline-block;
		padding: 18rpx 56rpx;
		background: #1890FF;
		color: #FFFFFF;
		font-size: 28rpx;
		border-radius: 12rpx;
	}

	.bottom-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		background: #FFFFFF;
		box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
		padding: 16rpx 24rpx;
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
		gap: 16rpx;
		z-index: 100;
	}
	.bottom-btn {
		flex: 1;
		height: 80rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 28rpx;
		border-radius: 12rpx;
		gap: 8rpx;
	}
	.btn-download {
		background: #1890FF;
		color: #FFFFFF;
	}
	.btn-save {
		background: #F5F7FA;
		color: #303133;
	}
	.btn-icon {
		font-size: 26rpx;
	}
</style>
