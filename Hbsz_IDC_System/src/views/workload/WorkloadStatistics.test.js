/**
 * WorkloadStatistics（工作量统计容器）拆分重构冒烟测试。
 *
 * 巨石组件（1630 行）拆分为容器 + 4 个 Tab 子组件 + useWorkloadMatrix composable，
 * 本测试锁定容器与子组件的数据协作契约（回归保护）：
 *   - 默认进入统计总览：自动选中首个未归档项目 → 并行拉取矩阵 + 成员名单，
 *     汇总卡片（清单项/填报人数/待审核）由共享矩阵驱动；
 *   - 填报审核/人员绩效 Tab：el-tabs 急切渲染所有 pane，子组件必须以 active 守卫
 *     ——未激活不拉数，激活后按项目维度拉取（listPendingRecords/getPerformance 参数）；
 *   - 矩阵代填/统计总览：访问共享 Tab 时容器重拉矩阵；
 *   - changed 联动：子组件数据变更（清单维护/提交填报/审核完成）emit('changed')
 *     → 容器统一重拉矩阵（汇总卡片与待审核角标即时更新）。
 *
 * 测试边界：API 层（workloadApi/dailyReportApi）、反馈层（feedback）、权限层
 * （hasPermission）、项目仓库（useProjectStore）均 mock；Element Plus 真实插件挂载。
 * 权限预设：无快速填报权限（workload:report=false）→ 默认 Tab 为统计总览，
 * 快速填报 pane 不渲染，其余 Tab 全部可见。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { workloadApiMock, feedbackMock, hasPermissionMock, projectStoreMock } = vi.hoisted(() => ({
  workloadApiMock: {
    getMatrix: vi.fn(),
    listWorkers: vi.fn(),
    listPendingRecords: vi.fn(),
    getPerformance: vi.fn(),
    exportExcel: vi.fn(),
    saveRecords: vi.fn(),
    reviewRecords: vi.fn(),
    createItem: vi.fn(),
    updateItem: vi.fn(),
    removeItem: vi.fn(),
    importTemplate: vi.fn(),
    listItems: vi.fn(),
    getMyReportContext: vi.fn()
  },
  feedbackMock: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), confirm: vi.fn() },
  hasPermissionMock: vi.fn(),
  projectStoreMock: {
    projects: [
      { id: 300, name: '综布仓一期', archived: false },
      { id: 999, name: '旧机房改造', archived: true }
    ],
    fetchList: vi.fn()
  }
}))

vi.mock('@/api/workload', () => ({ workloadApi: workloadApiMock }))
vi.mock('@/api/dailyReport', () => ({ dailyReportApi: { download: vi.fn() } }))
vi.mock('@/utils/feedback', () => ({ feedback: feedbackMock }))
vi.mock('@/utils/permission', () => ({ hasPermission: hasPermissionMock }))
vi.mock('@/stores/project', () => ({ useProjectStore: () => projectStoreMock }))
vi.mock('@/utils/download', () => ({ downloadBlob: vi.fn() }))

import WorkloadStatistics from '@/views/workload/WorkloadStatistics.vue'
import ReviewTab from '@/views/workload/components/ReviewTab.vue'

// happy-dom 无 ResizeObserver，Element Plus 弹层组件依赖（缺失会在挂载时抛错）
if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

/** 共享统计矩阵（2 清单项 + 1 填报人 + 2 条待审核） */
const MATRIX = {
  data: {
    items: [
      { id: 11, category: '桥架安装', workScope: '机房A区桥架敷设', unit: '米', ratedWorkload: 100, performanceScore: 1, totalQuantity: 1000, completedQuantity: 200, remainingQuantity: 800, todayQuantity: 50, remark: '' },
      { id: 12, category: '光缆熔接', workScope: '机房B区光缆熔接', unit: '芯', ratedWorkload: 50, performanceScore: 2, totalQuantity: 500, completedQuantity: 100, remainingQuantity: 400, todayQuantity: 24, remark: '' }
    ],
    workers: [{ userId: 600, name: '李四', roleInTeam: 'leader' }],
    matrix: { 11: { 李四: { quantity: 50, reviewStatus: 'pending', reviewRemark: null } } },
    workerPerformance: { 李四: '4.50' },
    pendingCount: 2
  }
}

const WORKERS = {
  data: [
    { userId: 600, name: '李四', roleInTeam: 'leader' },
    { userId: 601, name: '王五', roleInTeam: 'member' }
  ]
}

const PENDING_RECORDS = {
  data: [
    { id: 91, recordDate: '2026-09-20', workerName: '李四', category: '桥架安装', workScope: '机房A区桥架敷设', quantity: 50, unit: '米', reviewStatus: 'pending', reviewRemark: null, reviewerName: null, reviewTime: null, createdAt: '2026-09-20 09:00:00' }
  ]
}

const PERFORMANCE = {
  data: [
    { workerName: '李四', totalQuantity: 200, performanceScore: 18.5, recordDays: 5, avgDailyPerformance: 3.7 }
  ]
}

function mountPage() {
  return mount(WorkloadStatistics, { global: { plugins: [ElementPlus] } })
}

/** 按可见文本找 Tab 头（含角标数字，用 includes 定位最稳） */
function findTab(wrapper, text) {
  return wrapper.findAll('.el-tabs__item').find(t => t.text().includes(text))
}

describe('WorkloadStatistics 工作量统计容器', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hasPermissionMock.mockImplementation((p) => p !== 'workload:report')
    workloadApiMock.getMatrix.mockResolvedValue(MATRIX)
    workloadApiMock.listWorkers.mockResolvedValue(WORKERS)
    workloadApiMock.listPendingRecords.mockResolvedValue(PENDING_RECORDS)
    workloadApiMock.getPerformance.mockResolvedValue(PERFORMANCE)
  })

  it('默认统计总览：自动选中首个未归档项目拉取矩阵，汇总卡片由共享矩阵驱动', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // 项目清单预取 + 自动选中未归档项目（跳过已归档的"旧机房改造"）
    expect(projectStoreMock.fetchList).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100 })
    expect(workloadApiMock.getMatrix).toHaveBeenCalledWith({ projectId: 300, date: expect.any(String) })
    expect(workloadApiMock.listWorkers).toHaveBeenCalledWith({ projectId: 300 })

    // 汇总卡片：清单项 2 / 今日填报人数 1 / 待审核 2
    const cards = wrapper.findAll('.summary-card')
    expect(cards).toHaveLength(5)
    expect(cards[0].text()).toContain('2')
    expect(cards[1].text()).toContain('1')
    expect(cards[4].text()).toContain('2')

    // 统计总览表格渲染清单行；急切渲染的审核/绩效 Tab 未激活不拉数
    expect(wrapper.text()).toContain('机房A区桥架敷设')
    expect(workloadApiMock.listPendingRecords).not.toHaveBeenCalled()
    expect(workloadApiMock.getPerformance).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('切换填报审核：子组件激活后自拉待审核列表（active 守卫），角标显示待审核数', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const reviewTab = findTab(wrapper, '填报审核')
    expect(reviewTab.exists()).toBe(true)
    expect(reviewTab.text()).toContain('2') // 待审核角标
    await reviewTab.trigger('click')
    await flushPromises()

    expect(workloadApiMock.listPendingRecords).toHaveBeenCalledWith({ projectId: 300, reviewStatus: 'pending' })
    expect(wrapper.text()).toContain('机房A区桥架敷设') // 审核列表行渲染
    wrapper.unmount()
  })

  it('子组件 changed 联动：审核完成后通知容器重拉共享矩阵', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(workloadApiMock.getMatrix).toHaveBeenCalledTimes(1)

    await findTab(wrapper, '填报审核').trigger('click')
    await flushPromises()

    // 模拟 ReviewTab 审核成功后的 emit('changed')
    await wrapper.findComponent(ReviewTab).vm.$emit('changed')
    await flushPromises()

    expect(workloadApiMock.getMatrix).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('切换矩阵代填：访问共享 Tab 时容器重拉矩阵', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(workloadApiMock.getMatrix).toHaveBeenCalledTimes(1)

    await findTab(wrapper, '矩阵代填').trigger('click')
    await flushPromises()

    expect(workloadApiMock.getMatrix).toHaveBeenCalledTimes(2)
    // 矩阵代填表格（清单行 + 人员列）渲染
    expect(wrapper.text()).toContain('机房B区光缆熔接')
    wrapper.unmount()
  })

  it('切换人员绩效：子组件激活后按近 30 天区间拉取排名', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await findTab(wrapper, '人员绩效').trigger('click')
    await flushPromises()

    expect(workloadApiMock.getPerformance).toHaveBeenCalledWith({
      projectId: 300,
      startDate: expect.any(String),
      endDate: expect.any(String)
    })
    expect(wrapper.text()).toContain('18.5')
    wrapper.unmount()
  })
})
