<!--
  统计分析页
  业务用途：移动端统计概览页，展示项目核心指标（总数 / 状态 / 金额 / 进度）、
           项目状态分布、项目类型分布、月度趋势，并附项目经理绩效与文档分类统计。
  数据来源：statisticsApi.getOverview / getStatusDistribution / getTypeDistribution /
           getProjectTrend，以及 documentApi.getCategoryStats（文档分类）。
  导航特性：非 tabBar 页，通过 navigateTo 进入；时间范围切换（全部/月/季/年）时
           重新加载所有图表数据；使用 Promise.allSettled 并发加载，互不影响。
  可视化：不引入 ECharts 等第三方库，状态分布使用 CSS 水平条形图，
         类型分布使用 SVG 饼图（data URI），月度趋势使用 SVG 折线图（data URI）。
-->
<template>
	<view class="page">
		<!-- 时间范围切换 tab（吸顶） -->
		<view class="range-bar">
			<view
				v-for="r in rangeOptions"
				:key="r.value"
				class="range-item"
				:class="{ active: timeRange === r.value }"
				role="button"
				hover-class="uni-hover"
				:hover-stay-time="80"
				@click="onRangeChange(r.value)"
			>{{ r.label }}</view>
		</view>

		<!-- 概览指标卡片（2x3 网格） -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">概览指标</text>
			</view>
			<view v-if="loading" class="overview-grid">
				<view class="metric-card" v-for="i in 6" :key="i">
					<view class="metric-skeleton"></view>
					<view class="metric-skeleton-label"></view>
				</view>
			</view>
			<view v-else class="overview-grid">
				<view class="metric-card" v-for="m in metricCards" :key="m.label">
					<view class="metric-num" :style="{ color: m.color }">{{ m.value }}</view>
					<view class="metric-label">{{ m.label }}</view>
				</view>
			</view>
		</view>

		<!-- 项目状态分布（水平条形图） -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">项目状态分布</text>
			</view>
			<view class="card">
				<view v-if="loading" class="bar-list">
					<view class="bar-row" v-for="i in 4" :key="i">
						<view class="skeleton-line"></view>
					</view>
				</view>
				<view v-else-if="statusDist.length" class="bar-list">
					<view v-for="(s, i) in statusDist" :key="i" class="bar-row">
						<view class="bar-meta">
							<text class="bar-name">{{ s.name }}</text>
							<text class="bar-value">{{ s.value }} 项</text>
						</view>
						<view class="bar-track">
							<view class="bar-fill" :style="{ width: barPercent(s.value) + '%', background: statusColor(s.name) }"></view>
						</view>
					</view>
				</view>
				<view v-else class="empty-mini">暂无数据</view>
			</view>
		</view>

		<!-- 项目类型分布（SVG 饼图） -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">项目类型分布</text>
			</view>
			<view class="card chart-card">
				<view v-if="loading" class="pie-skeleton-wrap">
					<view class="pie-skeleton"></view>
					<view class="legend-skeleton">
						<view class="skeleton-line" v-for="i in 4" :key="i"></view>
					</view>
				</view>
				<view v-else-if="typeDist.length" class="pie-wrap">
					<view class="pie-chart">
						<image class="pie-img" :src="pieChartSrc" mode="aspectFit" />
						<view class="pie-center">
							<text class="pie-total">{{ typeTotal }}</text>
							<text class="pie-total-label">总数</text>
						</view>
					</view>
					<view class="pie-legend">
						<view v-for="(t, i) in typeDist" :key="i" class="legend-item">
							<view class="legend-dot" :style="{ background: typeColor(i) }"></view>
							<text class="legend-name">{{ t.name }}</text>
							<text class="legend-value">{{ t.value }} ({{ typePercent(t.value) }}%)</text>
						</view>
					</view>
				</view>
				<view v-else class="empty-mini">暂无数据</view>
			</view>
		</view>

		<!-- 月度趋势（SVG 折线图） -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">月度趋势</text>
				<text v-if="trend.length" class="section-sub">新增项目数 / 月</text>
			</view>
			<view class="card chart-card">
				<view v-if="loading" class="trend-skeleton"></view>
				<view v-else-if="trend.length" class="trend-wrap">
					<image class="trend-img" :src="trendChartSrc" mode="widthFix" />
				</view>
				<view v-else class="empty-mini">暂无趋势数据</view>
			</view>
		</view>

		<!-- 项目经理绩效表 -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">项目经理绩效</text>
			</view>
			<view class="card table-card">
				<view class="table-row table-head">
					<text class="col col-name">姓名</text>
					<text class="col col-num">项目数</text>
					<text class="col col-num">已完成</text>
					<text class="col col-rate">完成率</text>
				</view>
				<view v-if="pmRows.length" class="table-body">
					<view v-for="(p, i) in pmRows" :key="i" class="table-row">
						<text class="col col-name">{{ p.name }}</text>
						<text class="col col-num">{{ p.total }}</text>
						<text class="col col-num">{{ p.completed }}</text>
						<view class="col col-rate">
							<view class="rate-bar">
								<view class="rate-fill" :style="{ width: p.rate + '%' }"></view>
							</view>
							<text class="rate-text">{{ p.rate }}%</text>
						</view>
					</view>
				</view>
				<view v-else class="empty-mini">暂无数据</view>
			</view>
		</view>

		<!-- 文档分类统计 -->
		<view class="section">
			<view class="section-head">
				<text class="section-title">文档分类统计</text>
			</view>
			<view class="card">
				<view v-if="loading" class="bar-list">
					<view class="bar-row" v-for="i in 3" :key="i">
						<view class="skeleton-line"></view>
					</view>
				</view>
				<view v-else-if="docStats.length" class="doc-list">
					<view v-for="(d, i) in docStats" :key="i" class="doc-row">
						<view class="doc-info">
							<text class="doc-name">{{ d.name }}</text>
							<text class="doc-value">{{ d.value }} 篇 · {{ docPercent(d.value) }}%</text>
						</view>
						<view class="bar-track">
							<view class="bar-fill" :style="{ width: docPercent(d.value) + '%', background: docColor(i) }"></view>
						</view>
					</view>
				</view>
				<view v-else class="empty-mini">暂无文档分类数据</view>
			</view>
		</view>

		<view class="bottom-safe"></view>
	</view>
</template>

<script>
	import { statisticsApi } from '@/api/statistics'
	import { documentApi } from '@/api/document'

	// 项目状态颜色映射（与全局规范一致：进行中蓝/已完成绿/已归档灰/已暂停橙）
	const STATUS_COLORS = {
		'进行中': '#1890FF',
		'已完成': '#52C41A',
		'已归档': '#8C8C8C',
		'暂停': '#FAAD14',
		'已暂停': '#FAAD14',
		'延期': '#FF4D4F'
	}

	// 项目类型分布色板（按顺序循环）
	const TYPE_COLORS = ['#1890FF', '#52C41A', '#FAAD14', '#722ED1', '#13C2C2', '#EB2F96', '#FA541C', '#8C8C8C']
	// 文档分类色板
	const DOC_COLORS = ['#1890FF', '#52C41A', '#FAAD14', '#722ED1', '#13C2C2', '#EB2F96', '#FA541C', '#8C8C8C']

	export default {
		data() {
			return {
				// 时间范围选项：全部 / 月 / 季 / 年
				rangeOptions: [
					{ label: '全部', value: 'all' },
					{ label: '月', value: 'month' },
					{ label: '季', value: 'quarter' },
					{ label: '年', value: 'year' }
				],
				timeRange: 'all',     // 当前时间范围
				loading: false,        // 加载中标记（控制骨架屏）
				overview: {           // 概览数据（后端 OverviewVO）
					totalProjects: 0,
					activeProjects: 0,
					completedProjects: 0,
					archivedProjects: 0,
					totalAmount: 0,
					avgProgress: 0
				},
				statusDist: [],       // 状态分布 [{ name, value }]
				typeDist: [],          // 类型分布 [{ name, value }]
				trend: [],             // 月度趋势 [{ month, count }]
				docStats: []           // 文档分类统计 [{ name, value }]
			}
		},
		computed: {
			/** 概览指标卡片数据（2x3 网格：总数/进行中/已完成/已归档/合同总额/平均进度） */
			metricCards() {
				return [
					{ label: '总项目数', value: this.overview.totalProjects ?? 0, color: '#1890FF' },
					{ label: '进行中', value: this.overview.activeProjects ?? 0, color: '#1890FF' },
					{ label: '已完成', value: this.overview.completedProjects ?? 0, color: '#52C41A' },
					{ label: '已归档', value: this.overview.archivedProjects ?? 0, color: '#8C8C8C' },
					{ label: '合同总额(万元)', value: this.formatAmount(this.overview.totalAmount), color: '#722ED1' },
					{ label: '平均进度(%)', value: this.overview.avgProgress ?? 0, color: '#FAAD14' }
				]
			},
			/**
			 * 项目经理绩效表行（后端无对应接口，从 overview 数据派生汇总行）
			 * 仅展示全体 PM 总数与完成率，无法按个人拆分。
			 */
			pmRows() {
				const total = this.overview.totalProjects ?? 0
				const completed = this.overview.completedProjects ?? 0
				if (!total) return []
				const rate = total ? Math.round((completed / total) * 100) : 0
				return [{ name: '全体 PM', total, completed, rate }]
			},
			/** 状态分布最大值（用于水平条形图比例：最大值填满轨道） */
			statusMax() {
				return Math.max(1, ...this.statusDist.map(s => Number(s.value) || 0))
			},
			/** 类型分布总数（用于饼图与占比计算） */
			typeTotal() {
				return this.typeDist.reduce((sum, t) => sum + (Number(t.value) || 0), 0)
			},
			/** 文档分类总数（用于占比计算，至少为 1 避免除零） */
			docTotal() {
				return Math.max(1, this.docStats.reduce((sum, d) => sum + (Number(d.value) || 0), 0))
			},
			/** 趋势图最大值（用于 Y 轴比例） */
			trendMax() {
				return Math.max(1, ...this.trend.map(t => Number(t.count) || 0))
			},
			/** 饼图 SVG data URI（绑定到 <image> 渲染，避开小程序不支持内联 <svg> 的限制） */
			pieChartSrc() {
				const total = this.typeTotal
				if (!total) return ''
				// donut 图：圆心(60,60)，半径 r=40，stroke-width=24
				const r = 40
				const cx = 60
				const cy = 60
				const circ = 2 * Math.PI * r
				let offset = 0
				const slices = this.typeDist.map((t, i) => {
					const val = Number(t.value) || 0
					const len = (val / total) * circ
					// stroke-dasharray: 实线长度 + 间隔长度；stroke-dashoffset 负值表示顺时针偏移
					const slice = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${TYPE_COLORS[i % TYPE_COLORS.length]}" stroke-width="24" stroke-dasharray="${len} ${circ - len}" stroke-dashoffset="${-offset}" transform="rotate(-90 ${cx} ${cy})"/>`
					offset += len
					return slice
				}).join('')
				const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">${slices}</svg>`
				return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
			},
			/** 折线图 SVG data URI（含网格线、Y 轴刻度、X 轴月份、面积填充与折线） */
			trendChartSrc() {
				if (!this.trend.length) return ''
				const w = 320
				const h = 160
				const padL = 30
				const padR = 12
				const padT = 16
				const padB = 28
				const innerW = w - padL - padR
				const innerH = h - padT - padB
				const max = this.trendMax
				const n = this.trend.length
				// 计算 X/Y 坐标：单点时居中，多点均匀分布
				const points = this.trend.map((t, i) => {
					const val = Number(t.count) || 0
					const x = padL + (n === 1 ? innerW / 2 : (i * innerW / (n - 1)))
					const y = padT + innerH - (val / max) * innerH
					return { x, y, val, label: t.month || '' }
				})
				// 折线 points 字符串
				const linePath = points.map(p => `${p.x},${p.y}`).join(' ')
				// 区域填充 path：从左下 → 各点 → 右下 → 闭合
				const last = points[points.length - 1]
				const areaPath = `M ${padL},${padT + innerH} L ${points.map(p => `${p.x},${p.y}`).join(' L ')} L ${last.x},${padT + innerH} Z`
				// 网格线（3 条横向虚线，将图表等分为 4 段）
				const grids = []
				for (let i = 1; i <= 3; i++) {
					const y = padT + (innerH / 3) * i
					grids.push(`<line x1="${padL}" y1="${y}" x2="${w - padR}" y2="${y}" stroke="#EBEEF5" stroke-dasharray="2,2"/>`)
				}
				// Y 轴刻度（从 max 到 0，4 档）
				const yLabels = []
				for (let i = 0; i <= 3; i++) {
					const v = Math.round(max - (max / 3) * i)
					const y = padT + (innerH / 3) * i
					yLabels.push(`<text x="${padL - 4}" y="${y + 3}" font-size="9" fill="#767E8A" text-anchor="end">${v}</text>`)
				}
				// X 轴月份标签（数据点超过 8 个时跳采避免拥挤，取后 5 位如 "2026-07" → "07"）
				const xLabels = points.map((p, i) => {
					if (n > 8 && i % Math.ceil(n / 8) !== 0) return ''
					const lbl = String(p.label).slice(5) || ''
					return `<text x="${p.x}" y="${h - 8}" font-size="9" fill="#767E8A" text-anchor="middle">${lbl}</text>`
				}).join('')
				// 数据点
				const dots = points.map(p => `<circle cx="${p.x}" cy="${p.y}" r="2.5" fill="#1890FF"/>`).join('')
				const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}">
					${grids.join('')}
					${yLabels.join('')}
					${xLabels}
					<path d="${areaPath}" fill="rgba(24,144,255,0.12)"/>
					<polyline points="${linePath}" fill="none" stroke="#1890FF" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round"/>
					${dots}
				</svg>`
				return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
			}
		},
		// 页面加载：首次拉取全部数据
		onLoad() {
			this.loadAll()
		},
		// 下拉刷新：重新加载并在完成后停止刷新动画
		onPullDownRefresh() {
			this.loadAll(true)
		},
		methods: {
			/**
			 * 格式化合同金额：保留 2 位小数
			 * 单位「万元」由前端标签承担展示，后端可能返回元或万元，原样展示避免二次换算误差。
			 * @param {number} v 后端返回的金额数值
			 * @returns {string} 两位小数字符串
			 */
			formatAmount(v) {
				const n = Number(v) || 0
				return n.toFixed(2)
			},
			/**
			 * 时间范围切换：变化时重新加载所有数据
			 * @param {string} v all/month/quarter/year
			 */
			onRangeChange(v) {
				if (this.timeRange === v) return
				this.timeRange = v
				this.loadAll()
			},
			/**
			 * 并发加载所有数据，使用 Promise.allSettled 互不影响
			 * @param {boolean} stopPull 是否为下拉刷新触发
			 */
			async loadAll(stopPull) {
				this.loading = true
				await Promise.allSettled([
					this.loadOverview(),
					this.loadStatusDist(),
					this.loadTypeDist(),
					this.loadTrend(),
					this.loadDocStats()
				])
				this.loading = false
				if (stopPull) uni.stopPullDownRefresh()
			},
			/** 加载概览指标（接口本身无时间参数，按后端口径展示） */
			async loadOverview() {
				try {
					const data = await statisticsApi.getOverview() || {}
					// 兼容后端多种字段命名：totalProjects/total、activeProjects/inProgress、...
					this.overview = {
						totalProjects: data.totalProjects ?? data.total ?? 0,
						activeProjects: data.activeProjects ?? data.inProgress ?? 0,
						completedProjects: data.completedProjects ?? data.completed ?? 0,
						archivedProjects: data.archivedProjects ?? data.archived ?? 0,
						totalAmount: data.totalAmount ?? data.amount ?? 0,
						avgProgress: data.avgProgress ?? data.averageProgress ?? 0
					}
				} catch (e) {
					console.warn('overview error', e)
				}
			},
			/** 加载项目状态分布（传入时间范围参数；后端不识别时将忽略并返回全量） */
			async loadStatusDist() {
				try {
					const list = await statisticsApi.getStatusDistribution({ range: this.timeRange })
					this.statusDist = this.normalizeList(list)
				} catch (e) {
					console.warn('status dist error', e)
					this.statusDist = []
				}
			},
			/** 加载项目类型分布 */
			async loadTypeDist() {
				try {
					const list = await statisticsApi.getTypeDistribution({ range: this.timeRange })
					this.typeDist = this.normalizeList(list)
				} catch (e) {
					console.warn('type dist error', e)
					this.typeDist = []
				}
			},
			/** 加载月度趋势（按时间范围计算 startMonth/endMonth 传给后端） */
			async loadTrend() {
				try {
					const { startMonth, endMonth } = this.computeMonthRange()
					const list = await statisticsApi.getProjectTrend({ startMonth, endMonth, range: this.timeRange })
					this.trend = this.normalizeTrendList(list)
				} catch (e) {
					console.warn('trend error', e)
					this.trend = []
				}
			},
			/** 加载文档分类统计（与项目时间范围无关） */
			async loadDocStats() {
				try {
					const list = await documentApi.getCategoryStats()
					this.docStats = this.normalizeList(list)
				} catch (e) {
					console.warn('doc stats error', e)
					this.docStats = []
				}
			},
			/**
			 * 列表数据规整（兼容 {name,value} / {label,count} / {category,count} 等字段命名）
			 * @param {Array} list 后端返回列表
			 * @returns {Array<{name:string,value:number}>}
			 */
			normalizeList(list) {
				if (!Array.isArray(list)) return []
				return list.map(item => ({
					name: item.name || item.label || item.category || '未命名',
					value: Number(item.value ?? item.count ?? 0)
				}))
			},
			/**
			 * 趋势列表规整（兼容 {month,count} / {monthLabel,value} / {date,total} 等字段）
			 * @param {Array} list
			 * @returns {Array<{month:string,count:number}>}
			 */
			normalizeTrendList(list) {
				if (!Array.isArray(list)) return []
				return list.map(item => ({
					month: item.month || item.monthLabel || item.date || '',
					count: Number(item.count ?? item.value ?? item.total ?? 0)
				}))
			},
			/**
			 * 根据当前时间范围计算趋势查询的月区间
			 * - all：近 12 个月
			 * - month：当月
			 * - quarter：当前季度起点至当月
			 * - year：当年 1 月至当月
			 * @returns {{startMonth:string,endMonth:string}} yyyy-MM 格式
			 */
			computeMonthRange() {
				const now = new Date()
				const y = now.getFullYear()
				const m = now.getMonth()
				const fmt = (yy, mm) => `${yy}-${String(mm + 1).padStart(2, '0')}`
				if (this.timeRange === 'month') {
					return { startMonth: fmt(y, m), endMonth: fmt(y, m) }
				}
				if (this.timeRange === 'quarter') {
					// 季度起点：1/4/7/10 月
					const qStart = Math.floor(m / 3) * 3
					return { startMonth: fmt(y, qStart), endMonth: fmt(y, m) }
				}
				if (this.timeRange === 'year') {
					return { startMonth: fmt(y, 0), endMonth: fmt(y, m) }
				}
				// 全部：近 12 个月（跨年处理）
				const startY = m < 11 ? y - 1 : y
				const startM = (m + 1) % 12
				return { startMonth: fmt(startY, startM), endMonth: fmt(y, m) }
			},
			/**
			 * 状态分布条形图百分比（按最大值比例：最大值填满轨道）
			 * @param {number} v 当前状态值
			 * @returns {number} 0-100
			 */
			barPercent(v) {
				const n = Number(v) || 0
				return Math.round((n / this.statusMax) * 100)
			},
			/**
			 * 项目状态颜色（按名称映射，未知状态取灰色兜底）
			 * @param {string} name 状态中文名
			 * @returns {string} 16 进制颜色
			 */
			statusColor(name) {
				return STATUS_COLORS[name] || '#8C8C8C'
			},
			/** 项目类型分布色板（按索引循环取色） */
			typeColor(i) {
				return TYPE_COLORS[i % TYPE_COLORS.length]
			},
			/** 文档分类色板 */
			docColor(i) {
				return DOC_COLORS[i % DOC_COLORS.length]
			},
			/** 类型分布占比（百分比，向下取整） */
			typePercent(v) {
				const n = Number(v) || 0
				return this.typeTotal ? Math.round((n / this.typeTotal) * 100) : 0
			},
			/** 文档分类占比 */
			docPercent(v) {
				const n = Number(v) || 0
				return Math.round((n / this.docTotal) * 100)
			}
		}
	}
</script>

<style lang="scss" scoped>
	@import '@/styles/common.scss';

	.page {
		min-height: 100vh;
		background: #F5F7FA;
		padding-bottom: 40rpx;
	}

	/* 时间范围切换 tab（吸顶） */
	.range-bar {
		display: flex;
		padding: 16rpx 24rpx;
		background: #FFFFFF;
		gap: 16rpx;
		border-bottom: 1rpx solid #EBEEF5;
		position: sticky;
		top: 0;
		z-index: 10;
	}
	.range-item {
		flex: 1;
		text-align: center;
		padding: 14rpx 0;
		font-size: 26rpx;
		color: #606266;
		background: #F5F7FA;
		border-radius: 10rpx;
	}
	.range-item.active {
		background: #1890FF;
		color: #FFFFFF;
	}

	/* 区块通用 */
	.section {
		padding: 24rpx 24rpx 0;
	}
	.section-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 16rpx;
		padding: 0 4rpx;
	}
	.section-title {
		font-size: 30rpx;
		font-weight: 700;
		color: #303133;
	}
	.section-sub {
		font-size: 22rpx;
		color: #767E8A;
	}

	/* 概览指标卡片 2x3 网格 */
	.overview-grid {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 16rpx;
	}
	.metric-card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx 16rpx;
		text-align: center;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.metric-num {
		font-size: 40rpx;
		font-weight: 700;
		line-height: 1.2;
		color: #303133;
	}
	.metric-label {
		font-size: 22rpx;
		color: #5C6370;
		margin-top: 8rpx;
	}
	.metric-skeleton {
		height: 48rpx;
		margin: 0 auto;
		width: 80%;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
		border-radius: 8rpx;
	}
	.metric-skeleton-label {
		height: 22rpx;
		margin: 16rpx auto 0;
		width: 60%;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
		border-radius: 6rpx;
	}

	/* 通用卡片 */
	.card {
		background: #FFFFFF;
		border-radius: 16rpx;
		padding: 24rpx;
		box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
	}
	.chart-card {
		padding: 20rpx;
	}
	@keyframes sk {
		0% { background-position: 100% 50%; }
		100% { background-position: 0 50%; }
	}
	.skeleton-line {
		height: 28rpx;
		margin: 12rpx 0;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
		border-radius: 6rpx;
	}

	/* 状态分布水平条形图 */
	.bar-list {
		display: flex;
		flex-direction: column;
		gap: 16rpx;
	}
	.bar-row {
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}
	.bar-meta {
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 24rpx;
	}
	.bar-name {
		color: #303133;
		font-weight: 500;
	}
	.bar-value {
		color: #5C6370;
	}
	.bar-track {
		height: 14rpx;
		background: #F0F2F5;
		border-radius: 7rpx;
		overflow: hidden;
	}
	.bar-fill {
		height: 100%;
		border-radius: 7rpx;
		transition: width 0.3s ease;
	}

	/* 饼图（项目类型分布） */
	.pie-wrap {
		display: flex;
		align-items: center;
		gap: 24rpx;
	}
	.pie-chart {
		position: relative;
		width: 200rpx;
		height: 200rpx;
		flex-shrink: 0;
	}
	.pie-img {
		width: 100%;
		height: 100%;
	}
	.pie-center {
		position: absolute;
		top: 50%;
		left: 50%;
		transform: translate(-50%, -50%);
		text-align: center;
	}
	.pie-total {
		font-size: 36rpx;
		font-weight: 700;
		color: #303133;
		display: block;
		line-height: 1.2;
	}
	.pie-total-label {
		font-size: 20rpx;
		color: #767E8A;
	}
	.pie-legend {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 12rpx;
	}
	.legend-item {
		display: flex;
		align-items: center;
		font-size: 24rpx;
	}
	.legend-dot {
		width: 14rpx;
		height: 14rpx;
		border-radius: 50%;
		margin-right: 12rpx;
		flex-shrink: 0;
	}
	.legend-name {
		flex: 1;
		color: #303133;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		margin-right: 12rpx;
	}
	.legend-value {
		color: #5C6370;
		flex-shrink: 0;
	}
	.pie-skeleton-wrap {
		display: flex;
		align-items: center;
		gap: 24rpx;
	}
	.pie-skeleton {
		width: 200rpx;
		height: 200rpx;
		border-radius: 50%;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
		flex-shrink: 0;
	}
	.legend-skeleton {
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: center;
	}

	/* 趋势图 */
	.trend-wrap {
		width: 100%;
	}
	.trend-img {
		width: 100%;
		display: block;
	}
	.trend-skeleton {
		height: 240rpx;
		background: linear-gradient(90deg, #F2F4F7 25%, #E6E8EB 37%, #F2F4F7 63%);
		background-size: 400% 100%;
		animation: sk 1.4s ease infinite;
		border-radius: 8rpx;
	}

	/* 项目经理绩效表 */
	.table-card {
		padding: 0;
		overflow: hidden;
	}
	.table-row {
		display: flex;
		align-items: center;
		padding: 20rpx 24rpx;
		border-bottom: 1rpx solid #F0F2F5;
	}
	.table-row:last-child {
		border-bottom: none;
	}
	.table-head {
		background: #FAFAFA;
		font-size: 22rpx;
		color: #767E8A;
		font-weight: 600;
	}
	.col {
		flex: 1;
		font-size: 26rpx;
		color: #303133;
	}
	.col-name {
		flex: 1.4;
	}
	.col-rate {
		display: flex;
		align-items: center;
	}
	.rate-bar {
		flex: 1;
		height: 8rpx;
		background: #EBEEF5;
		border-radius: 4rpx;
		overflow: hidden;
		margin-right: 8rpx;
	}
	.rate-fill {
		height: 100%;
		background: linear-gradient(90deg, #1890FF, #52C41A);
		border-radius: 4rpx;
	}
	.rate-text {
		font-size: 22rpx;
		color: #096DD9;
		font-weight: 600;
		min-width: 60rpx;
		text-align: right;
	}

	/* 文档分类统计 */
	.doc-list {
		display: flex;
		flex-direction: column;
		gap: 16rpx;
	}
	.doc-row {
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}
	.doc-info {
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 24rpx;
	}
	.doc-name {
		color: #303133;
		font-weight: 500;
	}
	.doc-value {
		color: #5C6370;
	}

	/* 空状态（小尺寸，适合卡片内） */
	.empty-mini {
		padding: 60rpx 0;
		text-align: center;
		color: #767E8A;
		font-size: 24rpx;
	}

	.bottom-safe {
		height: calc(40rpx + env(safe-area-inset-bottom));
	}
</style>
