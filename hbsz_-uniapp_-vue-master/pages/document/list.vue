<!--
  文档列表页（tabBar 页面）
  业务用途：展示文档列表，支持关键字搜索、分类筛选（含各分类计数）、分页、预览与下载。
  数据来源：documentApi.getList（分页）、documentApi.getCategoryStats（分类统计）、
           documentApi.download（下载）。
  导航特性：tabBar 页；支持下拉刷新与上拉加载更多；下载后可保存到相册或用系统能力打开。
-->
<template>
	<view class="page">
		<!-- 搜索栏 -->
		<view class="search-bar">
			<view class="search-input-wrap">
				<image class="search-icon" src="/static/icon/搜索.svg" mode="aspectFit" aria-hidden="true" />
			<input
				class="search-input"
				v-model="keyword"
				placeholder="搜索文档名称"
				placeholder-class="ph"
				confirm-type="search"
				aria-label="搜索文档"
				@confirm="onSearch"
			/>
			<text v-if="keyword" class="search-clear" role="button" aria-label="清空搜索" @click="clearKeyword">✕</text>
			</view>
			<!-- 上传文档按钮 -->
			<view class="upload-btn" role="button" hover-class="uni-hover" :hover-stay-time="80" @click="goUpload">+ 上传</view>
		</view>

		<!-- 分类筛选 -->
		<scroll-view class="filter-bar" scroll-x>
			<view
				v-for="item in categories"
				:key="item.value"
				class="filter-item"
				:class="{ active: currentCategory === item.value }"
				role="button"
				@click="changeCategory(item.value)"
			>
				{{ item.label }}<text v-if="item.count !== undefined" class="cat-count">{{ item.count }}</text>
			</view>
		</scroll-view>

		<!-- 文档列表 -->
		<view class="list">
			<view v-if="loading && list.length === 0" class="card">
				<view class="skeleton-line" v-for="i in 4" :key="i"></view>
			</view>
			<template v-else>
				<view
			v-for="doc in list"
			:key="doc.id"
			class="doc-card"
			hover-class="uni-hover"
			:hover-stay-time="80"
			role="link"
			@click="goPreview(doc)"
		>
					<view class="doc-icon" :style="{ background: fileColor(doc) }" aria-hidden="true">
						{{ fileExt(doc) }}
					</view>
					<view class="doc-body">
						<view class="doc-name">{{ doc.name }}</view>
						<view class="doc-meta">
							<text v-if="doc.projectName" class="meta-text">{{ doc.projectName }}</text>
							<text class="meta-text">{{ doc.size || '-' }}</text>
							<text class="meta-text">{{ formatDate(doc.uploadDate) }}</text>
						</view>
						<view v-if="doc.tags && doc.tags.length" class="doc-tags">
							<text v-for="(tag, i) in doc.tags" :key="i" class="doc-tag">{{ tag }}</text>
						</view>
					</view>
					<view class="doc-actions">
						<view class="action-btn preview" role="button" @click.stop="goPreview(doc)">预览</view>
						<view class="action-btn download" role="button" @click.stop="handleDownload(doc)">下载</view>
					</view>
				</view>

				<view v-if="list.length === 0" class="empty">
				<view class="empty-icon" aria-hidden="true"><image class="svg-icon-lg" src="/static/icon/资料.svg" mode="aspectFit" /></view>
				<text>暂无文档</text>
				</view>

				<view v-if="loading && list.length > 0" class="load-more">加载中...</view>
				<view v-else-if="noMore && list.length > 0" class="load-more">— 没有更多了 —</view>
			</template>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { documentApi } from '@/api/document'
	import { formatDate, getFileTypeColor, getExt, formatFileSize } from '@/utils/format'

	export default {
		data() {
			return {
				keyword: '',          // 搜索关键字
				currentCategory: '',  // 当前分类筛选值（''=全部）
				categories: [         // 分类筛选选项（count 由统计接口回填）
					{ label: '全部', value: '' },
					{ label: '设计图纸', value: '设计图纸' },
					{ label: '施工记录', value: '施工记录' },
					{ label: '验收报告', value: '验收报告' },
					{ label: '合同文件', value: '合同文件' },
					{ label: '技术文档', value: '技术文档' },
					{ label: '其他', value: '其他' }
				],
				list: [],             // 文档列表
				page: 1,              // 当前页码
				size: 10,             // 每页条数
				total: 0,             // 总条数
				loading: false,       // 加载中标记
				noMore: false         // 是否已无更多数据
			}
		},
		// 页面显示：首屏为空时加载列表，并刷新分类统计
		onShow() {
			if (this.list.length === 0) this.refresh()
			this.loadCategoryStats()
		},
		// 下拉刷新
		onPullDownRefresh() {
			this.refresh(true)
		},
		// 触底加载更多
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			formatDate,
			/** 文档图标显示的扩展名文本 */
			fileExt(doc) {
				return getExt(doc.name || doc.fileType) || (doc.fileType || 'FILE').substring(0, 3).toUpperCase()
			},
			/** 文档图标背景色（按文件类型区分） */
			fileColor(doc) {
				return getFileTypeColor(getExt(doc.name || doc.fileType) || doc.fileType)
			},
			/** 点击搜索：重新加载 */
			onSearch() { this.refresh() },
			/** 清空关键字并刷新 */
			clearKeyword() {
				this.keyword = ''
				this.refresh()
			},
			/** 切换分类并刷新 */
			changeCategory(v) {
				this.currentCategory = v
				this.refresh()
			},
			/** 加载分类统计并回填各分类计数 */
			async loadCategoryStats() {
				try {
					const stats = await documentApi.getCategoryStats()
					if (stats && Array.isArray(stats)) {
						const map = {}
						stats.forEach(s => { map[s.category || s.value] = s.count || s.total })
						this.categories = this.categories.map(c => ({
							...c,
							count: c.value ? map[c.value] : undefined
						}))
					}
				} catch (e) {
					console.warn('stats error', e)
				}
			},
			/**
			 * 刷新列表（重置到第一页）
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async refresh(stopPull) {
				this.page = 1
				this.noMore = false
				await this.loadList()
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载下一页（追加模式） */
			async loadMore() {
				if (this.loading || this.noMore) return
				await this.loadList(true, this.page + 1)
			},
			/**
			 * 加载文档列表
			 * @param {boolean} append 是否追加到现有列表
			 */
			async loadList(append = false, loadPage) {
				if (this.loading) return
				this.loading = true
				const page = loadPage || this.page
				try {
					const params = { page, size: this.size }
					if (this.keyword) params.keyword = this.keyword
					if (this.currentCategory) params.category = this.currentCategory
					const res = await documentApi.getList(params)
					const list = (res && (res.records || res.list || res)) || []
					const total = (res && (res.total || res.totalCount)) || list.length
					if (append) {
						this.list = this.list.concat(list)
					} else {
						this.list = list
					}
					this.total = total
					this.noMore = this.list.length >= total
					// 仅在加载成功后更新页码，避免请求失败时 page 已递增导致数据丢失
					this.page = page
				} catch (e) {
					console.warn('list error', e)
				} finally {
					this.loading = false
				}
			},
			/** 跳转文档预览页（携带文档名，便于预览页首屏展示） */
		goPreview(doc) {
			uni.navigateTo({ url: `/pages/document/preview?id=${doc.id}&name=${encodeURIComponent(doc.name)}` })
		},
		/** 跳转文档上传页 */
		goUpload() {
			uni.navigateTo({ url: '/pages/document/upload' })
		},
			/**
			 * 下载文档：下载完成后弹出操作菜单，支持保存到相册或打开文件
			 * @param {Object} doc 文档对象
			 */
			async handleDownload(doc) {
				uni.showLoading({ title: '下载中...', mask: true })
				try {
					const tempPath = await documentApi.download(doc.id)
					uni.hideLoading()
					// 微信小程序：保存文件到本地
					uni.showActionSheet({
						itemList: ['保存到手机相册（图片）', '打开文件'],
						success: (res) => {
							if (res.tapIndex === 0) {
								uni.saveImageToPhotosAlbum({
									filePath: tempPath,
									success: () => uni.showToast({ title: '已保存', icon: 'success' }),
									fail: () => uni.showToast({ title: '保存失败', icon: 'none' })
								})
							} else {
								uni.openDocument({
									filePath: tempPath,
									showMenu: true,
									fail: () => uni.showToast({ title: '打开失败', icon: 'none' })
								})
							}
						}
					})
				} catch (e) {
					uni.hideLoading()
					console.warn('download error', e)
				}
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page { min-height: 100vh; }

	.search-bar {
		display: flex;
		align-items: center;
		gap: 16rpx;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
	}
	.search-input-wrap {
		flex: 1;
		display: flex;
		align-items: center;
		background: #F5F7FA;
		border-radius: 32rpx;
		padding: 0 24rpx;
		height: 64rpx;
	}
	.search-icon {
		width: 28rpx;
		height: 28rpx;
		margin-right: 12rpx;
		color: #5C6370;
	}
	.search-input {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
	}
	.ph { color: #767E8A; }
	.search-clear {
		color: #767E8A;
		font-size: 24rpx;
		padding: 0 8rpx;
	}
	.upload-btn {
		flex-shrink: 0;
		height: 64rpx;
		line-height: 64rpx;
		padding: 0 24rpx;
		font-size: 26rpx;
		font-weight: 500;
		color: #FFFFFF;
		background: linear-gradient(135deg, #1890FF, #096DD9);
		border-radius: 32rpx;
		box-shadow: 0 4rpx 12rpx rgba(24, 144, 255, 0.3);
	}

	.filter-bar {
		white-space: nowrap;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
		border-bottom: 1rpx solid #EBEEF5;
	}
	.filter-item {
		display: inline-block;
		padding: 10rpx 24rpx;
		margin-right: 16rpx;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 28rpx;
	}
	.filter-item.active {
		background: #1890FF;
		color: #FFFFFF;
	}
	.cat-count {
		margin-left: 6rpx;
		font-size: 22rpx;
		opacity: 0.85;
	}

	.list { padding: 20rpx 24rpx; }
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
	}
	.skeleton-line {
		height: 180rpx;
		margin-bottom: 20rpx;
		border-radius: 16rpx;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
	}
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}

	.doc-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 20rpx;
		display: flex;
		align-items: center;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
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
	.doc-body {
		flex: 1;
		min-width: 0;
	}
	.doc-name {
		font-size: 30rpx;
		color: #303133;
		font-weight: 500;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.doc-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 16rpx;
		margin-top: 10rpx;
	}
	.meta-text {
		font-size: 22rpx;
		color: #5C6370;
	}
	.doc-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 8rpx;
		margin-top: 10rpx;
	}
	.doc-tag {
		font-size: 20rpx;
		color: #096DD9;
		background: #E6F7FF;
		padding: 2rpx 10rpx;
		border-radius: 4rpx;
	}
	.doc-actions {
		display: flex;
		flex-direction: column;
		gap: 12rpx;
		margin-left: 16rpx;
	}
	.action-btn {
		font-size: 22rpx;
		padding: 8rpx 20rpx;
		border-radius: 24rpx;
		text-align: center;
	}
	.action-btn.preview {
		background: #1890FF;
		color: #FFFFFF;
	}
	.action-btn.download {
		background: #F5F7FA;
		color: #606266;
	}

	.empty {
		padding: 160rpx 40rpx;
		text-align: center;
		color: #5C6370;
		font-size: 26rpx;
	}
	.empty-icon {
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
	.load-more {
		text-align: center;
		color: #767E8A;
		font-size: 24rpx;
		padding: 24rpx 0;
	}
	.bottom-safe { height: calc(40rpx + env(safe-area-inset-bottom)); }
</style>
