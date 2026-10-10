/**
 * QuickReportPanel（快速填报面板）组件特征测试。
 *
 * 锁定"30 秒卡片流"的核心契约（重构高危区，回归保护）：
 *   - 加载协议：onMounted 先不带 projectId 拉项目列表 → 单项目自动选中后
 *     带 projectId 二次请求拿完整上下文（两次调用的参数形状）；
 *   - 卡片组装：常用项 ∪ 今日已填（含驳回提醒）合并渲染，approved 锁定只读；
 *   - 复制昨天：仅填空值卡片（非零不覆盖、锁定跳过），
 *     昨日有值但不在卡片列表的项自动补卡；
 *   - 提交：只收集 value>0 且非锁定卡片，payload 携带 myUserId 回传的
 *     workerUserId（后端成员校验对称），成功后 emit('submitted') 并 reload；
 *   - 空态与错误：未加入项目组提示、加载失败走 feedback.error。
 *
 * 测试边界：API 层（workloadApi）与反馈层（feedback）均 mock；
 * Element Plus 以真实插件挂载（覆盖 v-loading 指令与受控组件行为）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { workloadApiMock, feedbackMock } = vi.hoisted(() => ({
  workloadApiMock: {
    getMyReportContext: vi.fn(),
    listItems: vi.fn(),
    saveRecords: vi.fn()
  },
  feedbackMock: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), confirm: vi.fn() }
}))

vi.mock('@/api/workload', () => ({ workloadApi: workloadApiMock }))
vi.mock('@/utils/feedback', () => ({ feedback: feedbackMock }))

import QuickReportPanel from '@/views/workload/QuickReportPanel.vue'

// happy-dom 无 ResizeObserver，Element Plus 弹层组件依赖（缺失会在挂载时抛错）
if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

/** 单项目用户完整上下文（含已通过锁定项 / 待审项 / 今日驳回项 / 昨日可复制项） */
const FULL_CONTEXT = {
  myUserId: 600,
  myName: '李四',
  projectId: 300,
  projectName: '综布仓一期',
  reportDate: '2026-08-21',
  projects: [{ projectId: 300, projectName: '综布仓一期', roleInTeam: 'member' }],
  todayRecords: {
    11: { quantity: 5, reviewStatus: 'approved', reviewRemark: null },
    12: { quantity: 3, reviewStatus: 'pending', reviewRemark: null },
    14: { quantity: 2, reviewStatus: 'rejected', reviewRemark: '数量与现场不符' }
  },
  yesterdayRecords: { 11: 5, 13: 8 },
  frequentItems: [
    { itemId: 11, category: '桥架安装', workScope: '机房A区桥架敷设', unit: '米', ratedWorkload: 100, fillCount: 3, lastQuantity: 5, lastDate: '2026-08-20' },
    { itemId: 12, category: '光缆熔接', workScope: '机房B区光缆熔接', unit: '芯', ratedWorkload: 50, fillCount: 1, lastQuantity: 3, lastDate: '2026-08-21' }
  ],
  totalItemCount: 106
}

/** 项目骨架响应（projectId 未定时后端仅返回项目列表） */
const PROJECT_LIST_ONLY = {
  data: {
    myUserId: 600,
    myName: '李四',
    projects: FULL_CONTEXT.projects,
    todayRecords: {},
    yesterdayRecords: {},
    frequentItems: [],
    totalItemCount: 0
  }
}

function mountPanel() {
  return mount(QuickReportPanel, { global: { plugins: [ElementPlus] } })
}

/** 按可见文本找按钮（工具栏含图标按钮，按文本定位最稳） */
function findButton(wrapper, text) {
  return wrapper.findAll('button').find(b => b.text().includes(text))
}

describe('QuickReportPanel 快速填报面板', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    workloadApiMock.getMyReportContext.mockImplementation((params) => {
      if (!params.projectId) return Promise.resolve(PROJECT_LIST_ONLY)
      return Promise.resolve({ data: FULL_CONTEXT })
    })
  })

  it('单项目自动选中：先无 projectId 拉项目列表，再带 projectId 二次请求完整上下文', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    expect(workloadApiMock.getMyReportContext).toHaveBeenCalledTimes(2)
    const [firstCall, secondCall] = workloadApiMock.getMyReportContext.mock.calls
    expect(firstCall[0]).not.toHaveProperty('projectId')
    expect(firstCall[0].date).toEqual(expect.any(String))
    expect(secondCall[0]).toMatchObject({ projectId: 300 })

    // 单项目：显示项目名而非下拉
    expect(wrapper.find('.qr-project-name').text()).toBe('综布仓一期')
    wrapper.unmount()
  })

  it('卡片组装：常用项 ∪ 今日已填合并，approved 锁定只读，驳回卡显示原因', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    // 11(常用) 12(常用) 14(今日已填、不在常用列表 → 合并补卡)
    const cards = wrapper.findAll('.qr-card')
    expect(cards).toHaveLength(3)
    expect(cards[0].find('.qr-card-category').text()).toBe('桥架安装')
    expect(cards[2].find('.qr-card-category').text()).toBe('今日填报')
    expect(cards[2].find('.qr-card-reject').text()).toContain('数量与现场不符')

    // approved 卡锁定：输入禁用且带锁定样式
    expect(cards[0].find('input').attributes('disabled')).toBeDefined()
    expect(cards[0].classes()).toContain('qr-card--approved')
    // pending / rejected 卡可编辑
    expect(cards[1].find('input').attributes('disabled')).toBeUndefined()
    expect(cards[2].classes()).toContain('qr-card--rejected')

    // 状态条汇总：今日 3 项 = 已通过 1 + 待审核 1 + 已驳回 1
    const statusBar = wrapper.find('.qr-status-bar').text()
    expect(statusBar).toContain('已提交')
    expect(statusBar).toContain('待审核')
    expect(statusBar).toContain('已通过')
    expect(statusBar).toContain('已驳回')

    // 今日已有数量回显为输入初值（:precision="2" → 两位小数格式）
    expect(cards[0].find('input').element.value).toBe('5.00')
    expect(cards[1].find('input').element.value).toBe('3.00')

    // 清单总数提示
    expect(wrapper.find('.qr-more-tip').text()).toContain('106')
    wrapper.unmount()
  })

  it('复制昨天：空值卡片带入昨日量、非零不覆盖、锁定跳过，昨日独有项自动补卡', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await findButton(wrapper, '复制昨天').trigger('click')
    await flushPromises()

    // 13 昨日有值但无卡片 → 自动补卡，值 8
    const cards = wrapper.findAll('.qr-card')
    expect(cards).toHaveLength(4)
    const card13 = cards[3]
    expect(card13.find('input').element.value).toBe('8.00')

    // 11 锁定跳过（值仍为今日 5）；12 非零不覆盖（仍为 3）
    expect(cards[0].find('input').element.value).toBe('5.00')
    expect(cards[1].find('input').element.value).toBe('3.00')

    expect(feedbackMock.success).toHaveBeenCalledWith('已复制 1 项昨日数量，可直接微调后提交')
    wrapper.unmount()
  })

  it('提交：仅收集未锁定且数量>0 的卡片，payload 回传 workerUserId，成功后 emit submitted 并 reload', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    // 复制昨天后：12(3) 14(2) 13(8) 可提交，11 锁定排除
    await findButton(wrapper, '复制昨天').trigger('click')
    await flushPromises()

    const submitBtn = findButton(wrapper, '提交审核')
    expect(submitBtn.text()).toContain('3 项')
    expect(submitBtn.attributes('disabled')).toBeUndefined()

    workloadApiMock.saveRecords.mockResolvedValueOnce({ data: 3 })
    await submitBtn.trigger('click')
    await flushPromises()

    const payload = workloadApiMock.saveRecords.mock.calls[0][0]
    expect(payload.projectId).toBe(300)
    expect(payload.recordDate).toEqual(expect.any(String))
    expect(payload.records).toEqual([
      { itemId: 12, workerUserId: 600, quantity: 3 },
      { itemId: 14, workerUserId: 600, quantity: 2 },
      { itemId: 13, workerUserId: 600, quantity: 8 }
    ])

    expect(feedbackMock.success).toHaveBeenCalledWith('已提交 3 项，等待项目经理审核')
    expect(wrapper.emitted('submitted')).toHaveLength(1)
    // 成功后 reload 上下文（初始 2 次 + reload 1 次）
    expect(workloadApiMock.getMyReportContext).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('提交失败：走 feedback.error 且不 emit submitted', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await findButton(wrapper, '复制昨天').trigger('click')
    await flushPromises()

    workloadApiMock.saveRecords.mockRejectedValueOnce(new Error('服务器繁忙'))
    await findButton(wrapper, '提交审核').trigger('click')
    await flushPromises()

    expect(feedbackMock.error).toHaveBeenCalledWith('服务器繁忙')
    expect(wrapper.emitted('submitted')).toBeUndefined()
    wrapper.unmount()
  })

  it('多项目：自动选中第一个项目二次请求，渲染项目下拉而非项目名文本', async () => {
    const TWO_PROJECTS = [
      { projectId: 300, projectName: '综布仓一期', roleInTeam: 'member' },
      { projectId: 200, projectName: '数据机房二期', roleInTeam: 'leader' }
    ]
    workloadApiMock.getMyReportContext.mockReset()
    workloadApiMock.getMyReportContext.mockImplementation((params) => {
      if (!params.projectId) {
        return Promise.resolve({
          data: { myUserId: 600, myName: '李四', projects: TWO_PROJECTS, todayRecords: {}, yesterdayRecords: {}, frequentItems: [], totalItemCount: 0 }
        })
      }
      // 二次请求同样回传完整项目列表（与后端行为一致，供下拉切换）
      return Promise.resolve({ data: { ...FULL_CONTEXT, projects: TWO_PROJECTS } })
    })
    const wrapper = mountPanel()
    await flushPromises()

    // 自动选中列表首个项目并二次请求其上下文（下拉可再切换）
    expect(workloadApiMock.getMyReportContext).toHaveBeenCalledTimes(2)
    expect(workloadApiMock.getMyReportContext.mock.calls[1][0]).toMatchObject({ projectId: 300 })
    // 多项目：渲染下拉选择器（后端已按组长置前排序，首项即默认选中）
    expect(wrapper.find('.el-select').exists()).toBe(true)
    expect(wrapper.find('.qr-project-name').exists()).toBe(false)
    wrapper.unmount()
  })

  it('未加入项目组：展示引导空态，不渲染提交按钮', async () => {
    workloadApiMock.getMyReportContext.mockReset()
    workloadApiMock.getMyReportContext.mockResolvedValueOnce({
      data: { myUserId: 600, myName: '李四', projects: [], todayRecords: {}, yesterdayRecords: {}, frequentItems: [], totalItemCount: 0 }
    })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('.el-empty').text()).toContain('您还未加入任何项目组')
    expect(findButton(wrapper, '提交审核')).toBeUndefined()
    expect(workloadApiMock.getMyReportContext).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('上下文加载失败：feedback.error 且不崩（空态兜底）', async () => {
    workloadApiMock.getMyReportContext.mockReset()
    workloadApiMock.getMyReportContext.mockRejectedValueOnce(new Error('网络中断'))
    const wrapper = mountPanel()
    await flushPromises()

    expect(feedbackMock.error).toHaveBeenCalledWith('网络中断')
    expect(wrapper.find('.qr-cards-area').exists()).toBe(true)
    wrapper.unmount()
  })

  it('一键重报（状态条入口）：全部驳回项以当前数量重新提交', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    workloadApiMock.saveRecords.mockResolvedValueOnce({ data: 1 })
    await findButton(wrapper, '一键重报').trigger('click')
    await flushPromises()

    const payload = workloadApiMock.saveRecords.mock.calls[0][0]
    expect(payload.projectId).toBe(300)
    expect(payload.records).toEqual([
      { itemId: 14, workerUserId: 600, quantity: 2 }
    ])
    expect(feedbackMock.success).toHaveBeenCalledWith('已重新提交 1 项，等待项目经理审核')
    expect(wrapper.emitted('submitted')).toHaveLength(1)
    // 重报成功后 reload 上下文（初始 2 次 + reload 1 次）
    expect(workloadApiMock.getMyReportContext).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('单卡重报：驳回卡片入口仅提交该卡片当前数量', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    // 14 为驳回卡（索引 2），卡片内"重报"按钮仅提交该卡片
    const rejectedCard = wrapper.findAll('.qr-card')[2]
    expect(rejectedCard.find('.qr-card-reject').text()).toContain('数量与现场不符')
    workloadApiMock.saveRecords.mockResolvedValueOnce({ data: 1 })
    const resubmitBtn = rejectedCard.findAll('button').find(b => b.text().includes('重报'))
    await resubmitBtn.trigger('click')
    await flushPromises()

    const payload = workloadApiMock.saveRecords.mock.calls[0][0]
    expect(payload.records).toEqual([
      { itemId: 14, workerUserId: 600, quantity: 2 }
    ])
    expect(wrapper.emitted('submitted')).toHaveLength(1)
    wrapper.unmount()
  })

  it('重报数量为 0：提示先填写数量且不调用保存接口', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    // 将驳回卡数量清零后点状态条一键重报
    const rejectedCard = wrapper.findAll('.qr-card')[2]
    await rejectedCard.find('input').setValue('0')
    workloadApiMock.saveRecords.mockClear()
    await findButton(wrapper, '一键重报').trigger('click')
    await flushPromises()

    expect(workloadApiMock.saveRecords).not.toHaveBeenCalled()
    expect(feedbackMock.warning).toHaveBeenCalledWith('被驳回项当前数量为 0，请先填写数量后再重报')
    expect(wrapper.emitted('submitted')).toBeUndefined()
    wrapper.unmount()
  })
})
