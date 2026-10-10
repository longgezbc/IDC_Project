/**
 * 数据可视化统一色板（依据《ui设计标准.txt》色彩规范）
 *
 * 设计原则：
 *   - 主色不超过 3 种：主色（核心数据）+ 辅助色（次要信息）+ 点缀色（高亮/异常）；
 *   - 低饱和度：全部色值饱和度控制在 15%-35%，替代 Ant 高饱和色（#1890ff 等），
 *     降低长时间盯屏的视觉疲劳；
 *   - 色盲友好：序列色按"同色相不同明度"展开，配合图表已有的图例文字/边框/
 *     圆角差异，红绿色觉障碍用户可通过明度差异辨别；
 *   - 语义色仅保留"状态映射"用途（进行中=蓝、完成=绿、注意=黄、异常=红），
 *     红色保留给异常数据专用（标准：点缀色用于高亮异常或重要数据）。
 *
 * 使用方式：
 *   import { SERIES_COLORS, STATUS_SEMANTIC, areaGradient, barGradient } from '@/utils/chartTheme'
 *
 * 配套约定：
 *   - 渐变叠加透明度控制在 5%-25%（标准要求），areaGradient 默认顶部 25%；
 *   - 品牌功能色（按钮/链接/标签的 --primary-color 等 Element Plus 体系）不在
 *     本色板管辖范围——标准针对数据呈现层，不改业务操作控件的品牌色。
 */

/** 三色体系核心（标准推荐低饱和方向：蓝灰 + 浅青 + 柔黄） */
export const CHART_COLORS = {
  /** 主色：核心数据序列（低饱和蓝灰） */
  primary: '#6C8EBF',
  /** 辅助色：次要信息序列（低饱和浅青） */
  secondary: '#B3D9D9',
  /** 点缀色：高亮/重点关注（低饱和柔黄） */
  accent: '#D9C08A'
}

/**
 * 分类序列色板（饼图/多序列柱状图）
 * 主色 → 辅助色 → 点缀色 → 三者的明度变体，明度阶梯递进保证色盲可辨。
 */
export const SERIES_COLORS = [
  '#6C8EBF', // 主色（深）
  '#B3D9D9', // 辅助（浅）
  '#D9C08A', // 点缀（柔黄，白底上加深一档保证对比）
  '#8FA8C9', // 主色·浅
  '#CFE3E3', // 辅助·更浅
  '#C97E7E'  // 异常专用低饱和红（超出 3 主色的语义扩展，仅状态映射使用）
]

/**
 * 状态语义映射（状态分布饼图等"状态→颜色"场景）
 * 色相延续用户既有认知（蓝=进行中、绿=完成、黄=注意、红=风险、灰=归档），
 * 饱和度统一降至低饱和档位。
 */
export const STATUS_SEMANTIC = {
  active: '#6C8EBF',   // 进行中（蓝）
  done: '#8CB89A',     // 已完成（低饱和绿）
  warning: '#D9C08A',  // 临期/注意（低饱和黄）
  danger: '#C97E7E',   // 风险/异常（低饱和红）
  other: '#AEB6C2'     // 归档/其他（中性灰）
}

/** 状态分布饼图默认色序（与原 [#1890ff,#52c41a,#faad14,#f5222d,#909399] 语义对位） */
export const STATUS_SERIES = [
  STATUS_SEMANTIC.active,
  STATUS_SEMANTIC.done,
  STATUS_SEMANTIC.warning,
  STATUS_SEMANTIC.danger,
  STATUS_SEMANTIC.other
]

/**
 * 面积渐变（折线图 areaStyle）：顶部 25% → 底部透明。
 * 标准要求渐变叠加 5%-25% 透明度，默认取上限保证可见性。
 * @param {string} color 基色（hex）
 * @param {number} [topAlpha=0.25] 顶部透明度（0.05-0.25）
 * @returns {{color1: string, color2: string}} 供 LinearGradient 的 stops
 */
export function areaStops(color, topAlpha = 0.25) {
  const alpha = Math.min(Math.max(topAlpha, 0.05), 0.25)
  return {
    color1: `${color}${Math.round(alpha * 255).toString(16).padStart(2, '0')}`,
    color2: `${color}00`
  }
}

/**
 * 柱状图纵向渐变：基色 → 提亮色（顶部亮、底部深，保持低饱和观感）。
 * 与 echarts.graphic.LinearGradient(0,0,0,1) 配合使用。
 * @param {string} color 基色（hex）
 * @returns {{color1: string, color2: string}} [顶部, 底部]
 */
export function barStops(color) {
  return { color1: lighten(color, 0.18), color2: color }
}

/**
 * 颜色提亮（简单线性混合白色，仅用于图表渐变的亮端）。
 * @param {string} hex #RRGGBB
 * @param {number} ratio 0-1 提亮比例
 * @returns {string} #RRGGBB
 */
function lighten(hex, ratio) {
  const n = parseInt(hex.slice(1), 16)
  const r = Math.min(255, Math.round(((n >> 16) & 255) + (255 - ((n >> 16) & 255)) * ratio))
  const g = Math.min(255, Math.round(((n >> 8) & 255) + (255 - ((n >> 8) & 255)) * ratio))
  const b = Math.min(255, Math.round((n & 255) + (255 - (n & 255)) * ratio))
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`
}
