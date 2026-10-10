<!--
  approval-flow-chart.vue - 通用审批流程图组件（小程序端）

  业务用途：
    可视化展示审批流程的节点状态流转，支持项目授权审批、
    任务进度审核、项目转移审批等场景。

  技术实现：
    由于小程序对 echarts 支持有限（需引入较重的 echarts-for-weixin 包），
    本组件采用纯 CSS+SVG 方案，更轻量且兼容性好。
    节点用 flex 横向布局，连线用 CSS 伪元素实现箭头效果。

  使用示例：
    <approval-flow-chart
      :nodes="nodes"
      :links="links"
      title="项目授权审批流程"
      @node-click="handleNodeClick"
    />

  数据结构（与 Web 端 ApprovalFlowChart.vue 一致）：
    nodes: [
      { id: 'start', name: '发起申请', status: 'done', operator: '张三', time: '2026-07-05 10:00', remark: '工程师发起' },
      { id: 'review', name: '项目经理审核', status: 'current' },
      { id: 'approve', name: '审批通过', status: 'pending' }
    ]
    links: [
      { source: 'start', target: 'review', label: '提交' },
      { source: 'review', target: 'approve', label: '通过' },
      { source: 'review', target: 'reject', label: '拒绝', type: 'reject' }
    ]

  @author hbsz
-->
<template>
	<view class="approval-flow-chart">
		<!-- 标题 -->
		<view v-if="title" class="chart-title">{{ title }}</view>

		<!-- 流程图主体：横向滚动容器，节点过多时可左右滑动 -->
		<scroll-view scroll-x class="chart-scroll" :show-scrollbar="false">
			<view class="chart-body" :style="{ width: chartWidth }">
				<view
					v-for="(node, idx) in nodes"
					:key="node.id"
					class="flow-node-wrapper"
				>
					<!-- 节点圆点 -->
					<view
						class="node-circle"
						:class="'status-' + (node.status || 'pending')"
						@click="onNodeClick(node, idx)"
					>
						<!-- 已完成节点显示对勾 -->
						<text v-if="node.status === 'done'" class="node-icon">✓</text>
						<!-- 当前节点显示动画脉冲 -->
						<view v-if="node.status === 'current'" class="node-pulse"></view>
						<!-- 已拒绝节点显示叉号 -->
						<text v-if="node.status === 'rejected'" class="node-icon">✕</text>
						<!-- 待处理节点显示序号 -->
						<text v-if="node.status === 'pending'" class="node-num">{{ idx + 1 }}</text>
					</view>

					<!-- 节点名称 -->
					<text class="node-name" :class="{ active: node.status === 'current' }">{{ node.name }}</text>

					<!-- 节点角色（可选） -->
					<text v-if="node.role" class="node-role">{{ node.role }}</text>

					<!-- 连线（非最后一个节点时显示） -->
					<view
						v-if="idx < nodes.length - 1"
						class="node-link"
						:class="{ 'link-reject': isRejectLink(node.id, nodes[idx + 1].id) }"
					>
						<!-- 连线标签 -->
						<text v-if="getLinkLabel(node.id, nodes[idx + 1].id)" class="link-label">{{ getLinkLabel(node.id, nodes[idx + 1].id) }}</text>
						<!-- 箭头 -->
						<view class="link-arrow"></view>
					</view>
				</view>
			</view>
		</scroll-view>

		<!-- 图例 -->
		<view class="legend">
			<view class="legend-item">
				<view class="legend-dot done"></view>
				<text>已完成</text>
			</view>
			<view class="legend-item">
				<view class="legend-dot current"></view>
				<text>当前节点</text>
			</view>
			<view class="legend-item">
				<view class="legend-dot pending"></view>
				<text>待处理</text>
			</view>
			<view class="legend-item">
				<view class="legend-dot rejected"></view>
				<text>已拒绝</text>
			</view>
		</view>

		<!-- 节点详情弹窗（点击节点后显示） -->
		<view v-if="selectedNode" class="node-detail-mask" @click="selectedNode = null">
			<view class="node-detail-card" @click.stop>
				<view class="detail-header">
					<text class="detail-title">{{ selectedNode.name }}</text>
					<text class="detail-close" @click="selectedNode = null">×</text>
				</view>
				<view class="detail-body">
					<view class="detail-row">
						<text class="detail-label">状态</text>
						<view class="detail-value">
							<view class="status-tag" :class="'status-' + (selectedNode.status || 'pending')"></view>
							<text>{{ getStatusLabel(selectedNode.status) }}</text>
						</view>
					</view>
					<view v-if="selectedNode.operator" class="detail-row">
						<text class="detail-label">操作人</text>
						<text class="detail-value">{{ selectedNode.operator }}{{ selectedNode.role ? '（' + selectedNode.role + '）' : '' }}</text>
					</view>
					<view v-if="selectedNode.time" class="detail-row">
						<text class="detail-label">时间</text>
						<text class="detail-value">{{ selectedNode.time }}</text>
					</view>
					<view v-if="selectedNode.remark" class="detail-row">
						<text class="detail-label">备注</text>
						<text class="detail-value">{{ selectedNode.remark }}</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	/**
	 * 通用审批流程图组件（小程序端）
	 *
	 * 与 Web 端 ApprovalFlowChart.vue 共享数据结构，但渲染方式不同：
	 * - Web 端：echarts graph 关系图，节点位置自由布局
	 * - 小程序端：纯 CSS 横向流程图，节点按数组顺序从左到右排列
	 *
	 * 状态枚举（与 Web 端一致）：
	 * - done: 已完成（绿色 #52C41A）
	 * - current: 当前节点（蓝色 #1890FF，带脉冲动画）
	 * - pending: 待处理（灰色 #D9D9D9）
	 * - rejected: 已拒绝（红色 #F5222D）
	 */
	export default {
		name: 'ApprovalFlowChart',
		props: {
			/** 节点列表（与 Web 端数据结构一致） */
			nodes: {
				type: Array,
				default: () => []
			},
			/** 连线列表（与 Web 端数据结构一致） */
			links: {
				type: Array,
				default: () => []
			},
			/** 流程标题 */
			title: {
				type: String,
				default: ''
			}
		},
		data() {
			return {
				// 当前选中的节点（用于详情弹窗展示）
				selectedNode: null
			}
		},
		computed: {
			/**
			 * 计算流程图总宽度
			 * 每个节点占 160rpx，确保节点过多时可横向滚动
			 */
			chartWidth() {
				const count = this.nodes.length || 1
				return count * 200 + 'rpx'
			}
		},
		methods: {
			/**
			 * 节点点击：弹出详情卡片，并 emit 事件给父组件
			 */
			onNodeClick(node, idx) {
				this.selectedNode = node
				this.$emit('node-click', { node, index: idx })
			},
			/**
			 * 判断连线是否为拒绝路径
			 * @param {string} sourceId 源节点 ID
			 * @param {string} targetId 目标节点 ID
			 * @returns {boolean}
			 */
			isRejectLink(sourceId, targetId) {
				const link = this.links.find((l) => l.source === sourceId && l.target === targetId)
				return link && link.type === 'reject'
			},
			/**
			 * 获取连线标签
			 */
			getLinkLabel(sourceId, targetId) {
				const link = this.links.find((l) => l.source === sourceId && l.target === targetId)
				return link ? link.label || '' : ''
			},
			/**
			 * 获取状态中文标签
			 */
			getStatusLabel(status) {
				const labels = {
					done: '已完成',
					current: '当前节点',
					pending: '待处理',
					rejected: '已拒绝'
				}
				return labels[status] || '未知'
			}
		}
	}
</script>

<style lang="scss" scoped>
	.approval-flow-chart {
		background: #FAFAFA;
		border-radius: 16rpx;
		padding: 24rpx;
		border: 1rpx solid #F0F2F5;
	}

	.chart-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #303133;
		margin-bottom: 24rpx;
		padding-left: 8rpx;
	}

	.chart-scroll {
		white-space: nowrap;
		padding: 20rpx 0;
	}

	.chart-body {
		display: flex;
		align-items: center;
		min-width: 100%;
		padding: 20rpx 16rpx;
	}

	.flow-node-wrapper {
		display: flex;
		align-items: center;
		flex-shrink: 0;
	}

	/* 节点圆点 */
	.node-circle {
		width: 80rpx;
		height: 80rpx;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		position: relative;
		border: 4rpx solid transparent;
		transition: all 0.3s;
	}

	/* 节点状态颜色 — 渐变+虚线边框优化 */
	.status-done {
		background: linear-gradient(135deg, #73D13D, #389E0D);
		border-color: #237804;
		box-shadow: 0 0 12rpx rgba(82, 196, 26, 0.35);
	}

	.status-current {
		background: linear-gradient(135deg, #69C0FF, #096DD9);
		border-color: #0050B3;
		box-shadow: 0 0 24rpx rgba(24, 144, 255, 0.7);
	}

	/* 待处理：白色填充 + 浅蓝虚线边框，与灰色实心圆明显区分 */
	.status-pending {
		background: #FFFFFF;
		border: 4rpx dashed #91D5FF;
	}

	.status-rejected {
		background: linear-gradient(135deg, #FF7875, #CF1322);
		border-color: #820014;
		box-shadow: 0 0 12rpx rgba(245, 34, 45, 0.4);
	}

	/* 节点图标 */
	.node-icon {
		color: #FFFFFF;
		font-size: 36rpx;
		font-weight: 600;
	}

	/* 待处理节点序号：白色背景上用深蓝色 */
	.node-num {
		color: #096DD9;
		font-size: 28rpx;
		font-weight: 600;
	}

	/* 当前节点脉冲动画 */
	.node-pulse {
		position: absolute;
		top: -8rpx;
		left: -8rpx;
		right: -8rpx;
		bottom: -8rpx;
		border-radius: 50%;
		border: 4rpx solid #1890FF;
		animation: pulse 1.5s ease-out infinite;
	}

	@keyframes pulse {
		0% {
			transform: scale(1);
			opacity: 1;
		}
		100% {
			transform: scale(1.5);
			opacity: 0;
		}
	}

	/* 节点名称 */
	.node-name {
		font-size: 24rpx;
		color: #606266;
		margin: 16rpx 0 0;
		width: 120rpx;
		text-align: center;
		position: absolute;
		margin-top: 130rpx;
		margin-left: -20rpx;
		white-space: normal;
		line-height: 1.3;
	}

	.node-name.active {
		color: #096DD9;
		font-weight: 600;
	}

	/* 节点角色 */
	.node-role {
		font-size: 20rpx;
		color: #5C6370;
		position: absolute;
		margin-top: 175rpx;
		margin-left: -20rpx;
		width: 120rpx;
		text-align: center;
	}

	/* 连线 */
	.node-link {
		width: 80rpx;
		height: 4rpx;
		background: #BFBFBF;
		margin: 0 8rpx;
		position: relative;
	}

	/* 拒绝路径：红色虚线 */
	.node-link.link-reject {
		background: transparent;
		border-top: 4rpx dashed #F5222D;
		height: 0;
	}

	/* 连线标签 */
	.link-label {
		position: absolute;
		top: -36rpx;
		left: 50%;
		transform: translateX(-50%);
		font-size: 20rpx;
		color: #5C6370;
		background: #FFF;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		white-space: nowrap;
	}

	.link-reject .link-label {
		color: #F5222D;
	}

	/* 箭头 */
	.link-arrow {
		position: absolute;
		right: -4rpx;
		top: 50%;
		transform: translateY(-50%);
		width: 0;
		height: 0;
		border-left: 12rpx solid #BFBFBF;
		border-top: 8rpx solid transparent;
		border-bottom: 8rpx solid transparent;
	}

	.link-reject .link-arrow {
		border-left-color: #F5222D;
	}

	/* 图例 */
	.legend {
		display: flex;
		flex-wrap: wrap;
		gap: 24rpx;
		padding: 20rpx 16rpx 4rpx;
		margin-top: 60rpx;
		border-top: 1rpx solid #F0F2F5;
	}

	.legend-item {
		display: flex;
		align-items: center;
		font-size: 22rpx;
		color: #606266;
	}

	.legend-dot {
		width: 16rpx;
		height: 16rpx;
		border-radius: 50%;
		margin-right: 8rpx;
	}

	.legend-dot.done {
		background: linear-gradient(135deg, #73D13D, #389E0D);
	}

	.legend-dot.current {
		background: linear-gradient(135deg, #69C0FF, #096DD9);
		box-shadow: 0 0 8rpx rgba(24, 144, 255, 0.6);
	}

	.legend-dot.pending {
		background: #FFFFFF;
		border: 2rpx dashed #91D5FF;
	}

	.legend-dot.rejected {
		background: linear-gradient(135deg, #FF7875, #CF1322);
	}

	/* 节点详情弹窗 */
	.node-detail-mask {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		z-index: 999;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.node-detail-card {
		width: 80%;
		max-width: 600rpx;
		background: #FFF;
		border-radius: 16rpx;
		overflow: hidden;
	}

	.detail-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 24rpx;
		border-bottom: 1rpx solid #EBEEF5;
	}

	.detail-title {
		font-size: 32rpx;
		font-weight: 600;
		color: #303133;
	}

	.detail-close {
		font-size: 40rpx;
		color: #5C6370;
		padding: 0 8rpx;
	}

	.detail-body {
		padding: 24rpx;
	}

	.detail-row {
		display: flex;
		align-items: center;
		margin-bottom: 20rpx;
		font-size: 26rpx;
	}

	.detail-label {
		width: 140rpx;
		color: #5C6370;
		flex-shrink: 0;
	}

	.detail-value {
		color: #303133;
		flex: 1;
		display: flex;
		align-items: center;
	}

	.status-tag {
		width: 16rpx;
		height: 16rpx;
		border-radius: 50%;
		margin-right: 12rpx;
	}

	.status-tag.status-done {
		background: linear-gradient(135deg, #73D13D, #389E0D);
	}

	.status-tag.status-current {
		background: linear-gradient(135deg, #69C0FF, #096DD9);
	}

	.status-tag.status-pending {
		background: #FFFFFF;
		border: 2rpx dashed #91D5FF;
	}

	.status-tag.status-rejected {
		background: linear-gradient(135deg, #FF7875, #CF1322);
	}
</style>
