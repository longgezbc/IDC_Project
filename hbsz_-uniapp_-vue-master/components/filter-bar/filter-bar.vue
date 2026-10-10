<!--
  FilterBar 筛选栏组件

  业务用途：
    统一展示列表页顶部的横向筛选条，支持"均分布局"和"自由布局"两种模式。
    替代各页面散落的 .filter-bar / .filter-item / .filter-item.active 样式。

  使用示例：
    1. 均分布局（最常见）：选项自动均分宽度
       <filter-bar :options="filterOptions" v-model="activeKey" @change="onFilterChange" />
       // filterOptions: [{ key: 'all', label: '全部' }, { key: 'milestone', label: '里程碑' }]

    2. 自由布局（带特殊按钮如"甘特图"）：通过插槽自定义
       <filter-bar v-model="activeKey" :options="filterOptions" @change="onFilterChange">
         <view class="filter-gantt" @click="goGantt">📊 甘特图</view>
       </filter-bar>

  Props：
    - modelValue / v-model: 当前选中的 key
    - options: 选项数组 [{ key, label, badge? }]
    - activeClass: 选中态自定义类名（默认 'active'）

  Events：
    - update:modelValue / v-model: 选项切换
    - change: 选项切换，payload 为新选中的 option 对象

  Slots：
    - extra: 右侧额外内容（如甘特图按钮）
-->
<template>
	<view class="filter-bar">
		<view
			v-for="opt in options"
			:key="opt.key"
			class="filter-item"
			:class="{ active: modelValue === opt.key }"
			role="button"
			@click="onSelect(opt)"
		>
			<text class="filter-label">{{ opt.label }}</text>
			<text v-if="opt.badge != null" class="filter-badge">{{ opt.badge }}</text>
		</view>
		<!-- 右侧额外内容插槽：放置甘特图按钮等 -->
		<slot name="extra" />
		<slot />
	</view>
</template>

<script>
	export default {
		name: 'FilterBar',
		props: {
			/** 当前选中的 key（支持 v-model） */
			modelValue: {
				type: [String, Number],
				default: ''
			},
			/**
			 * 选项数组
			 * - key: 选项唯一标识
			 * - label: 显示文案
			 * - badge?: 可选，角标数字
			 */
			options: {
				type: Array,
				default: () => []
			}
		},
		methods: {
			/** 选中某个选项 */
			onSelect(opt) {
				if (this.modelValue === opt.key) return
				this.$emit('update:modelValue', opt.key)
				this.$emit('change', opt)
			}
		}
	}
</script>

<style lang="scss" scoped>
	.filter-bar {
		display: flex;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
		gap: 16rpx;
		border-bottom: 1rpx solid #EBEEF5;
		align-items: center;
	}
	.filter-item {
		flex: 1;
		text-align: center;
		padding: 14rpx 0;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 10rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 6rpx;
	}
	.filter-item.active {
		background: #1890FF;
		color: #FFFFFF;
	}
	.filter-item.active .filter-badge {
		background: rgba(255, 255, 255, 0.3);
		color: #FFFFFF;
	}
	.filter-label {
		line-height: 1;
	}
	.filter-badge {
		min-width: 28rpx;
		height: 28rpx;
		line-height: 28rpx;
		padding: 0 6rpx;
		font-size: 20rpx;
		border-radius: 14rpx;
		background: #E6E8EB;
		color: #5C6370;
	}
</style>
