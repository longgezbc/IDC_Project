<!--
  ApprovalFlowChart.vue - 通用审批流程图组件

  业务用途：
    可视化展示任意审批流程的节点状态流转，支持以下场景：
    - 项目授权审批（申请 → 审批 → 通过/拒绝）
    - 任务进度审核（填报 → 审核 → 通过/拒绝）
    - 项目转移审批（发起 → 审批 → 完成）

  技术实现：
    基于 echarts graph 关系图，手动布局横向流程。
    节点颜色按状态区分（done/current/pending/rejected），
    当前节点带高亮动画，拒绝路径用红色虚线。

  使用示例：
    <ApprovalFlowChart
      :nodes="nodes"
      :links="links"
      title="项目授权审批流程"
      @node-click="handleNodeClick"
    />

  数据结构：
    nodes: [
      { id: 'start', name: '发起申请', status: 'done', operator: '张三', time: '2026-07-05 10:00', remark: '工程师发起' },
      { id: 'review', name: '项目经理审核', status: 'current', operator: null, time: null, remark: '待审核' },
      { id: 'approve', name: '审批通过', status: 'pending' },
      { id: 'reject', name: '审批拒绝', status: 'pending' }
    ]
    links: [
      { source: 'start', target: 'review', label: '提交' },
      { source: 'review', target: 'approve', label: '通过' },
      { source: 'review', target: 'reject', label: '拒绝', type: 'reject' }
    ]

  @author hbsz
-->
<template>
  <div
    class="approval-flow-chart"
    :style="{ height: chartHeight }"
  >
    <div
      ref="chartRef"
      class="chart-container"
    />
    <!-- 图例说明 -->
    <div class="legend">
      <span class="legend-item"><i class="dot done" />已完成</span>
      <span class="legend-item"><i class="dot current" />当前节点</span>
      <span class="legend-item"><i class="dot pending" />待处理</span>
      <span class="legend-item"><i class="dot rejected" />已拒绝</span>
    </div>
  </div>
</template>

<script setup>
/**
 * 通用审批流程图组件
 *
 * 基于 echarts graph 关系图实现，支持节点状态高亮、拒绝路径标红、
 * 点击节点查看详情。组件不关心具体业务，通过 props 接收节点和连线数据。
 */
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  /**
   * 节点列表
   * @type {Array<{id: string, name: string, status: 'done'|'current'|'pending'|'rejected', operator?: string, time?: string, remark?: string, role?: string}>}
   */
  nodes: {
    type: Array,
    required: true,
    default: () => []
  },
  /**
   * 连线列表
   * @type {Array<{source: string, target: string, label?: string, type?: 'normal'|'reject'}>}
   */
  links: {
    type: Array,
    required: true,
    default: () => []
  },
  /** 流程标题（显示在图表左上角） */
  title: {
    type: String,
    default: ''
  },
  /** 图表高度 */
  height: {
    type: String,
    default: '320px'
  }
})

/** 节点点击事件：emit(node) */
const emit = defineEmits(['node-click'])

/** echarts 实例引用 */
const chartRef = ref(null)
let chartInstance = null

/** 计算后的图表高度（含图例） */
const chartHeight = ref(props.height)

/**
 * 节点状态视觉配置
 *
 * 优化策略：通过渐变色、阴影、边框粗细三重维度区分节点状态，
 * 确保色觉障碍用户也能通过亮度和边框差异辨别状态。
 *
 * 配色依据《ui设计标准.txt》：低饱和色板（对齐 utils/chartTheme.js 语义色），
 * 红色渐变保留给 rejected（异常语义），饱和度降至 25% 左右。
 *
 * - done（已完成）：低饱和绿渐变，2px 实线边框，微阴影
 * - current（当前节点）：主色低饱和蓝渐变，4px 粗边框，强发光 + 脉冲环
 * - pending（待处理）：浅蓝灰虚线边框，白色填充，中等亮度
 * - rejected（已拒绝）：低饱和红渐变，2px 实线边框，淡红色光晕
 */
const STATUS_COLORS = {
  done: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
    { offset: 0, color: '#A9CDB4' },
    { offset: 1, color: '#7FA98D' }
  ]),
  current: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
    { offset: 0, color: '#9FB8D6' },
    { offset: 1, color: '#6C8EBF' }
  ]),
  pending: '#FFFFFF',
  rejected: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
    { offset: 0, color: '#E0A5A5' },
    { offset: 1, color: '#C97E7E' }
  ])
}

/** 节点状态对应的边框色（低饱和档） */
const STATUS_BORDER_COLORS = {
  done: '#5E8A6E',
  current: '#4F7096',
  pending: '#AEC6DE',
  rejected: '#A85F5F'
}

/** 节点状态对应的边框类型（solid / dashed） */
const STATUS_BORDER_TYPES = {
  done: 'solid',
  current: 'solid',
  pending: 'dashed',
  rejected: 'solid'
}

/** 节点状态对应的阴影配置（光晕色对齐低饱和色板；current 强光晕为唯一流光动效，保留辨识度） */
const STATUS_SHADOWS = {
  done: { blur: 6, color: 'rgba(140, 184, 154, 0.25)' },
  current: { blur: 24, color: 'rgba(108, 142, 191, 0.55)' },
  pending: { blur: 0, color: 'transparent' },
  rejected: { blur: 8, color: 'rgba(201, 126, 126, 0.3)' }
}

/** tooltip 中状态圆点使用的纯色（不支持渐变的场景；低饱和档） */
const STATUS_DOT_COLORS = {
  done: '#8CB89A',
  current: '#6C8EBF',
  pending: '#AEC6DE',
  rejected: '#C97E7E'
}

/** 节点状态对应的中文名（tooltip 展示用） */
const STATUS_LABELS = {
  done: '已完成',
  current: '当前节点',
  pending: '待处理',
  rejected: '已拒绝'
}

/**
 * 自动布局：将节点按拓扑顺序横向排列
 * - 每个节点分配 x 坐标（基于在 nodes 数组中的位置）
 * - 同一层级多个节点时，y 坐标分散
 * @returns {Array} echarts nodes 配置
 */
function buildNodes() {
  const total = props.nodes.length
  if (total === 0) return []

  return props.nodes.map((node, idx) => {
    const x = (idx / Math.max(total - 1, 1)) * 800 - 400
    // 交替上下排列，避免节点重叠
    const y = (idx % 2 === 0 ? -60 : 60) * (idx >= 4 ? 2 : 1)
    const status = node.status || 'pending'
    const color = STATUS_COLORS[status]
    const borderColor = STATUS_BORDER_COLORS[status]
    const shadow = STATUS_SHADOWS[status]

    // 待处理节点用浅蓝灰虚线边框 + 白色填充，内部显示序号文字
    // 已完成/当前/已拒绝用渐变填充 + 实线边框
    return {
      id: node.id,
      name: node.name,
      x,
      y,
      symbolSize: status === 'current' ? 80 : status === 'pending' ? 56 : 64,
      itemStyle: {
        color,
        borderColor,
        borderWidth: status === 'current' ? 4 : status === 'pending' ? 3 : 2,
        borderType: STATUS_BORDER_TYPES[status],
        shadowBlur: shadow.blur,
        shadowColor: shadow.color
      },
      label: {
        show: true,
        position: 'bottom',
        distance: 8,
        color: status === 'current' ? '#0050B3' : status === 'rejected' ? '#820014' : '#303133',
        fontSize: status === 'current' ? 14 : 13,
        fontWeight: status === 'current' ? 700 : status === 'done' ? 600 : 400,
        formatter: '{b}'
      },
      // 自定义数据，tooltip 中使用
      nodeData: node
    }
  })
}

/**
 * 构建连线配置
 * - 拒绝路径（type='reject'）用红色虚线
 * - 普通路径用灰色实线
 * - 连线标签显示在连线中点
 * @returns {Object} echarts links 配置
 */
function buildLinks() {
  return props.links.map((link) => {
    const isReject = link.type === 'reject'
    // 判断连线源节点状态：源为 done 则连线用绿色（已完成路径），否则用灰色
    const sourceNode = props.nodes.find((n) => n.id === link.source)
    const isDonePath = sourceNode && sourceNode.status === 'done' && !isReject
    const lineColor = isReject ? '#F5222D' : isDonePath ? '#52C41A' : '#BFBFBF'

    return {
      source: link.source,
      target: link.target,
      label: {
        show: !!link.label,
        formatter: link.label || '',
        color: isReject ? '#F5222D' : isDonePath ? '#389E0D' : '#909399',
        fontSize: 12,
        backgroundColor: '#FFF',
        padding: [2, 6],
        borderRadius: 4
      },
      lineStyle: {
        color: lineColor,
        width: isReject ? 2 : isDonePath ? 2 : 1.5,
        curveness: 0.2,
        type: isReject ? 'dashed' : 'solid'
      }
    }
  })
}

/**
 * HTML 转义：将特殊字符替换为实体，防止 tooltip 拼接时 XSS
 * 修复 H11：node.name/operator/role/time/remark 均可能来自用户输入或后端数据，
 * 直接拼接到 HTML 会导致 XSS（如 name="<img src=x onerror=alert(1)>"）。
 * @param {string} str 待转义的原始字符串
 * @returns {string} 转义后的安全字符串
 */
function escapeHtml(str) {
  if (str === null || str === undefined) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 构建 tooltip HTML 内容
 * 展示节点名称、状态、操作人、时间、备注
 */
function buildTooltipHTML(node) {
  const status = node.status || 'pending'
  const statusColor = STATUS_DOT_COLORS[status]
  const statusLabel = STATUS_LABELS[status]

  // 修复 H11：所有用户可控字段经 escapeHtml 转义后再拼接，避免 XSS
  let html = `<div style="font-size:14px;font-weight:600;color:#303133;margin-bottom:8px;">${escapeHtml(node.name)}</div>`
  html += `<div style="margin-bottom:4px;"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${statusColor};margin-right:8px;"></span>状态：${escapeHtml(statusLabel)}</div>`
  if (node.operator) {
    html += `<div style="color:#606266;margin-bottom:4px;">操作人：${escapeHtml(node.operator)}${node.role ? '（' + escapeHtml(node.role) + '）' : ''}</div>`
  }
  if (node.time) {
    html += `<div style="color:#909399;margin-bottom:4px;">时间：${escapeHtml(node.time)}</div>`
  }
  if (node.remark) {
    html += `<div style="color:#909399;margin-top:4px;border-top:1px solid #EBEEF5;padding-top:4px;">${escapeHtml(node.remark)}</div>`
  }
  return html
}

/**
 * 构建 echarts 完整配置项
 */
function buildOption() {
  return {
    title: props.title
      ? {
        text: props.title,
        left: 'left',
        top: 10,
        textStyle: { fontSize: 14, color: '#303133', fontWeight: 600 }
      }
      : undefined,
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'node' && params.data.nodeData) {
          return buildTooltipHTML(params.data.nodeData)
        }
        return ''
      },
      backgroundColor: '#FFF',
      borderColor: '#EBEEF5',
      borderWidth: 1,
      padding: 12,
      extraCssText: 'box-shadow: 0 2px 12px rgba(0,0,0,0.08); border-radius: 8px;'
    },
    series: [
      {
        type: 'graph',
        layout: 'none',
        symbol: 'circle',
        roam: false,
        label: { show: true },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [0, 8],
        data: buildNodes(),
        links: buildLinks(),
        lineStyle: { opacity: 0.9 },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 }
        },
        // 动画配置：当前节点带脉冲效果
        animationDuration: 1500,
        animationEasingUpdate: 'cubicOut'
      }
    ]
  }
}

/**
 * 渲染图表
 * 初始化或更新 echarts 实例
 */
function renderChart() {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    // 节点点击事件
    chartInstance.on('click', (params) => {
      if (params.dataType === 'node' && params.data.nodeData) {
        emit('node-click', params.data.nodeData)
      }
    })
  }

  chartInstance.setOption(buildOption(), true)
}

/** 监听数据变化，重新渲染 */
watch(
  [() => props.nodes, () => props.links, () => props.title],
  () => {
    nextTick(renderChart)
  },
  { deep: true }
)

/** 窗口 resize 时重绘 */
function handleResize() {
  chartInstance && chartInstance.resize()
}

onMounted(() => {
  nextTick(renderChart)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.approval-flow-chart {
  position: relative;
  background: #FAFAFA;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #F0F0F0;
}

.chart-container {
  width: 100%;
  height: 100%;
}

.legend {
  display: flex;
  gap: 16px;
  padding: 8px 12px;
  background: #FFF;
  border-radius: 6px;
  position: absolute;
  bottom: 12px;
  right: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.legend-item {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #606266;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 6px;
}

.dot.done {
  background: linear-gradient(135deg, #73D13D, #389E0D);
}

.dot.current {
  background: linear-gradient(135deg, #69C0FF, #096DD9);
  box-shadow: 0 0 6px rgba(24, 144, 255, 0.6);
}

.dot.pending {
  background: #FFF;
  border: 1.5px dashed #91D5FF;
}

.dot.rejected {
  background: linear-gradient(135deg, #FF7875, #CF1322);
}
</style>
