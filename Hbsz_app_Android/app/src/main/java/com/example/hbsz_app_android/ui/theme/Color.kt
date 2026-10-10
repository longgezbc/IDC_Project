package com.example.hbsz_app_android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 设计令牌 —— 与 Web 前端（Hbsz_IDC_System/src/styles/global.css）保持一致。
 *
 * 迁移说明：App 端不做运行时主题色切换，因此直接使用 Web 端 :root 变量值；
 * 命名保持与 CSS 变量一一对应，便于双端同步维护。
 */

// ===== 品牌主色（--primary-color 系列） =====
val Primary = Color(0xFF1890FF)        // --primary-color
val PrimaryLight = Color(0xFF40A9FF)   // --primary-light
val PrimaryDark = Color(0xFF096DD9)    // --primary-dark
val PrimaryContainer = Color(0xFFE6F7FF) // el-button--primary.is-plain 背景
val OnPrimaryContainer = Color(0xFF096DDC) // --link-color（WCAG AA 文本蓝）

// ===== 语义色（--success/warning/danger/info-color） =====
val Success = Color(0xFF52C41A)
val Warning = Color(0xFFFAAD14)
val Danger = Color(0xFFF5222D)
val Info = Color(0xFF6B7280)

// ===== 语义文本色（WCAG 2.1 AA：白底对比度 ≥ 4.5:1） =====
val SuccessText = Color(0xFF389E0D)
val WarningText = Color(0xFFD48806)
val DangerText = Color(0xFFD4380D)
val InfoText = Color(0xFF5C6370)

// ===== 语义浅底色（el-tag / plain 按钮的浅色容器） =====
val SuccessContainer = Color(0xFFF6FFED)
val WarningContainer = Color(0xFFFFFBE6)
val DangerContainer = Color(0xFFFFF1F0)
val InfoContainer = Color(0xFFF4F4F5)

// ===== 中性色（--text/border/bg 系列） =====
val TextPrimary = Color(0xFF1F2937)    // --text-primary
val TextRegular = Color(0xFF4B5563)    // --text-regular
val TextSecondary = Color(0xFF595F6B)  // --text-secondary
val TextPlaceholder = Color(0xFF6B7280) // --text-placeholder
val BorderColor = Color(0xFFE5E7EB)    // --border-color
val BorderLight = Color(0xFFF3F4F6)    // --border-light
val BgPage = Color(0xFFF5F7FA)         // --bg-page
val BgCard = Color(0xFFFFFFFF)         // --bg-card
val BgHover = Color(0xFFF9FAFC)        // --bg-hover

// ===== 深色模式扩展色 =====
val DarkSurface = Color(0xFF1C1F26)
val DarkSurfaceVariant = Color(0xFF2A2E37)
val DarkTextPrimary = Color(0xFFE5E7EB)
val DarkTextSecondary = Color(0xFF9CA3AF)
val DarkBorder = Color(0xFF3A3F4B)
val PrimaryDarkContainer = Color(0xFF0D2A4A)

// ===== 数据可视化色板（对齐 Web 端 utils/chartTheme.js，依据《ui设计标准.txt》） =====
// 设计原则：主色不超过 3 种、低饱和（15%-35%）、红色专属异常数据、色盲按明度可辨。
// 注意：本组仅用于图表/数据呈现层；品牌功能色（按钮/图标芯片）仍用上方 --primary 体系。
val ChartPrimary = Color(0xFF6C8EBF)    // 主色：核心数据序列（低饱和蓝灰）
val ChartSecondary = Color(0xFFB3D9D9)  // 辅助色：次要信息（低饱和浅青）
val ChartAccent = Color(0xFFD9C08A)     // 点缀色：高亮/重点关注（低饱和柔黄）

// 状态语义映射（图表中"状态→颜色"，色相延续用户认知、饱和度降档）
val ChartStatusActive = Color(0xFF6C8EBF) // 进行中（蓝）
val ChartStatusDone = Color(0xFF8CB89A)   // 已完成（低饱和绿）
val ChartStatusWarning = Color(0xFFD9C08A) // 临期/注意（低饱和黄）
val ChartStatusDanger = Color(0xFFC97E7E)  // 风险/异常（低饱和红）
val ChartStatusOther = Color(0xFFAEB6C2)   // 归档/其他（中性灰）
