/**
 * 状态映射工具（utils/statusMap.js）特征测试。
 *
 * 审计背景：项目存在 format.js 与 statusMap.js 双轨状态映射且键集不一致
 * （format.js 有 '暂停'/'已归档'，statusMap.js 只有 'paused'/'已暂停'）。
 * 本测试锁定 statusMap 当前实际行为，作为后续收敛双轨时的回归基线。
 */
import { describe, it, expect } from 'vitest'
import {
  getStatusText,
  getStatusTagType,
  getReviewStatusText,
  getReviewStatusTagType,
  getRiskTagType,
  getRiskLevelText
} from '@/utils/statusMap'

describe('statusMap：项目/任务状态', () => {
  it('英文枚举 → 中文文本', () => {
    expect(getStatusText('completed')).toBe('已完成')
    expect(getStatusText('in_progress')).toBe('进行中')
    expect(getStatusText('pending')).toBe('未开始')
    expect(getStatusText('delayed')).toBe('已延期')
    expect(getStatusText('paused')).toBe('已暂停')
  })

  it('空值返回 -，未知值原样返回（保留后端新枚举可读性）', () => {
    expect(getStatusText('')).toBe('-')
    expect(getStatusText(null)).toBe('-')
    expect(getStatusText('archiving')).toBe('archiving')
  })

  it('状态 → el-tag type（含中文别名），未知返回 info', () => {
    expect(getStatusTagType('completed')).toBe('success')
    expect(getStatusTagType('已完成')).toBe('success')
    expect(getStatusTagType('in_progress')).toBe('primary')
    expect(getStatusTagType('delayed')).toBe('danger')
    expect(getStatusTagType('paused')).toBe('warning')
    expect(getStatusTagType('whatever')).toBe('info')
  })

  it('双轨分歧基线（审计发现）：statusMap 无「暂停」键，返回 info 而非 format.js 的 warning', () => {
    // 收敛双轨时若把「暂停」补进 statusMap，此断言需同步更新——这正是特征测试的意义
    expect(getStatusTagType('暂停')).toBe('info')
    expect(getStatusText('暂停')).toBe('暂停')
  })
})

describe('statusMap：审核状态', () => {
  it('pending/approved/rejected → 文本与 tag', () => {
    expect(getReviewStatusText('pending')).toBe('待审核')
    expect(getReviewStatusText('approved')).toBe('已通过')
    expect(getReviewStatusText('rejected')).toBe('已驳回')
    expect(getReviewStatusTagType('pending')).toBe('warning')
    expect(getReviewStatusTagType('approved')).toBe('success')
    expect(getReviewStatusTagType('rejected')).toBe('danger')
    expect(getReviewStatusTagType('unknown')).toBe('info')
  })
})

describe('statusMap：风险等级', () => {
  it('high/medium/low → tag 与文本，未知按低风险处理', () => {
    expect(getRiskTagType('high')).toBe('danger')
    expect(getRiskTagType('medium')).toBe('warning')
    expect(getRiskTagType('low')).toBe('success')
    expect(getRiskLevelText('high')).toBe('高风险')
    expect(getRiskLevelText('nonsense')).toBe('低风险')
  })
})
