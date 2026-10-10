<!--
  approval/flow.vue - 审批流程图页面（小程序端，接入真实 API）

  业务用途：
    从后端查询真实审批业务数据，通过 approval-flow-chart 组件渲染流程图。
    支持三种审批场景：项目授权审批、任务进度审核、项目转移审批。

  交互流程：
    1. 选择审批场景（tab 切换）
    2. 从后端加载该场景的业务记录列表
    3. 选择某条记录
    4. 调用后端接口将记录转换为流程图 {nodes, links} 数据
    5. approval-flow-chart 组件渲染

  @author hbsz
-->
<template>
	<view class="page">
		<!-- 场景切换 tab -->
		<view class="tab-bar">
			<view
				v-for="tab in tabs"
				:key="tab.key"
				class="tab-item"
				:class="{ active: currentScene === tab.key }"
				role="button"
				@click="switchScene(tab.key)"
			>
				<text>{{ tab.label }}</text>
			</view>
		</view>

		<!-- 业务记录选择 -->
		<view class="record-picker">
			<picker
				mode="selector"
				:range="recordList"
				range-key="label"
				:value="selectedIdx"
				aria-label="业务记录选择"
				@change="onPickerChange"
			>
				<view class="picker-display">
					<text v-if="selectedIdx >= 0">{{ recordList[selectedIdx] && recordList[selectedIdx].label }}</text>
					<text v-else class="placeholder">请选择业务记录查看流程图</text>
					<text class="picker-arrow">▼</text>
				</view>
			</picker>
		</view>

		<!-- 流程图组件 -->
		<view v-if="flowData">
			<approval-flow-chart
				:nodes="flowData.nodes"
				:links="flowData.links"
				:title="flowData.title"
				@node-click="onNodeClick"
			/>

			<!-- 业务信息摘要 -->
			<view class="biz-info">
				<view class="biz-row">
					<text class="biz-label">流程类型</text>
					<text class="biz-value">{{ flowTypeLabel }}</text>
				</view>
				<view class="biz-row">
					<text class="biz-label">关联项目</text>
					<text class="biz-value">{{ flowData.projectName || '—' }}</text>
				</view>
			</view>
		</view>

		<!-- 无数据提示 -->
		<view v-else-if="!loading" class="empty-tip">
			<text class="empty-text">请选择业务记录查看审批流程图</text>
		</view>

		<!-- 加载状态 -->
		<view v-if="loading" class="loading-tip">
			<text class="loading-text">加载中...</text>
		</view>
	</view>
</template>

<script>
	/**
	 * 审批流程图页面（小程序端）
	 *
	 * 通过后端 ApprovalFlowController 接口获取真实审批数据，
	 * 转换为流程图组件所需的 {nodes, links} 数据结构后渲染。
	 */
	import ApprovalFlowChart from '@/components/approval-flow-chart.vue'
	import { approvalFlowApi } from '@/api/approvalFlow'

	/** 场景 tab 配置 */
	const TABS = [
		{ key: 'auth', label: '项目授权审批' },
		{ key: 'progress', label: '任务进度审核' },
		{ key: 'transfer', label: '项目转移审批' }
	]

	export default {
		components: { ApprovalFlowChart },
		data() {
			return {
				tabs: TABS,
				currentScene: 'auth',
				recordList: [],
				selectedIdx: -1,
				flowData: null,
				loading: false
			}
		},
		computed: {
			/** 流程类型中文标签 */
			flowTypeLabel() {
				if (!this.flowData) return ''
				const labels = {
					auth_request: '项目授权审批',
					progress_review: '任务进度审核',
					project_transfer: '项目转移审批'
				}
				return labels[this.flowData.flowType] || this.flowData.flowType
			}
		},
		methods: {
			/** 切换场景 */
			switchScene(key) {
				this.currentScene = key
				this.selectedIdx = -1
				this.flowData = null
				this.loadRecordList()
			},

			/** 加载业务记录列表 */
			async loadRecordList() {
				this.loading = true
				this.recordList = []

				try {
					let list
					if (this.currentScene === 'auth') {
						list = await approvalFlowApi.listAuthRequests()
					} else if (this.currentScene === 'progress') {
						list = await approvalFlowApi.listProgressReviews()
					} else {
						list = await approvalFlowApi.listProjectTransfers()
					}

					// 格式化列表项 label
					this.recordList = list.map((item) => ({
						...item,
						label: this.formatLabel(item)
					}))

					// 自动选中第一条
					if (this.recordList.length > 0) {
						this.selectedIdx = 0
						await this.loadFlowData()
					}
				} catch (e) {
					uni.showToast({ title: '加载记录列表失败', icon: 'none' })
				} finally {
					this.loading = false
				}
			},

			/** picker 选择变更 */
			onPickerChange(e) {
				this.selectedIdx = e.detail.value
				this.loadFlowData()
			},

			/** 加载流程图数据 */
			async loadFlowData() {
				if (this.selectedIdx < 0 || !this.recordList[this.selectedIdx]) return

				const id = this.recordList[this.selectedIdx].id
				this.loading = true
				this.flowData = null

				try {
					if (this.currentScene === 'auth') {
						this.flowData = await approvalFlowApi.getAuthRequestFlow(id)
					} else if (this.currentScene === 'progress') {
						this.flowData = await approvalFlowApi.getProgressReviewFlow(id)
					} else {
						this.flowData = await approvalFlowApi.getProjectTransferFlow(id)
					}
				} catch (e) {
					uni.showToast({ title: '加载流程图失败', icon: 'none' })
				} finally {
					this.loading = false
				}
			},

			/** 格式化列表标签 */
			formatLabel(item) {
				if (this.currentScene === 'auth') {
					return `${item.projectName || '项目'} - ${item.applicantName || '?'} → ${item.targetName || '?'}`
				} else if (this.currentScene === 'progress') {
					return `${item.projectName || '项目'} - 进度${item.progress}%`
				} else {
					const actionLabels = { assign: '直接授权', revoke: '撤销', transfer: '转移', auth_request: '授权申请', approve: '审批通过', reject: '审批驳回', extend: '延期' }
					return `${item.projectName || '项目'} - ${actionLabels[item.actionType] || item.actionType}`
				}
			},

			/** 节点点击事件 */
			onNodeClick({ node }) {
				console.log('节点点击：', node.name)
			}
		},
		mounted() {
			this.loadRecordList()
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		min-height: 100vh;
		padding: 24rpx;
		background: #F5F7FA;
	}

	/* 场景切换 tab */
	.tab-bar {
		display: flex;
		background: #FFF;
		border-radius: 12rpx;
		padding: 8rpx;
		margin-bottom: 24rpx;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
	}

	.tab-item {
		flex: 1;
		text-align: center;
		padding: 16rpx 0;
		font-size: 26rpx;
		color: #606266;
		border-radius: 8rpx;
	}

	.tab-item.active {
		background: #1890FF;
		color: #FFFFFF;
		font-weight: 600;
	}

	/* picker 选择器 */
	.record-picker {
		margin-bottom: 24rpx;
	}

	.picker-display {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 20rpx 24rpx;
		background: #FFF;
		border-radius: 12rpx;
		font-size: 26rpx;
		color: #303133;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
	}

	.placeholder {
		color: #767E8A;
	}

	.picker-arrow {
		font-size: 20rpx;
		color: #767E8A;
	}

	/* 业务信息摘要 */
	.biz-info {
		margin-top: 24rpx;
		padding: 24rpx;
		background: #FFF;
		border-radius: 12rpx;
	}

	.biz-row {
		display: flex;
		align-items: center;
		margin-bottom: 12rpx;
		font-size: 24rpx;
	}

	.biz-label {
		width: 140rpx;
		color: #5C6370;
	}

	.biz-value {
		color: #303133;
	}

	/* 空数据提示 */
	.empty-tip {
		padding: 120rpx 0;
		text-align: center;
	}

	.empty-text {
		font-size: 26rpx;
		color: #767E8A;
	}

	/* 加载提示 */
	.loading-tip {
		padding: 60rpx 0;
		text-align: center;
	}

	.loading-text {
		font-size: 26rpx;
		color: #5C6370;
	}
</style>
