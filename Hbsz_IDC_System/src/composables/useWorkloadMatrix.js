/**
 * 工作量矩阵共享状态 composable
 *
 * 拉取某日统计矩阵（并附带项目组成员名单），状态供容器（汇总卡片）、
 * 统计总览表格、矩阵代填、审核角标多视图共用。
 *
 * 注意：matrixCells 每次整体赋新值（勿原地 mutation）——
 * 矩阵代填 Tab 依赖引用替换触发 watch 重建编辑矩阵。
 *
 * @module composables/useWorkloadMatrix
 */
import { ref } from 'vue'
import { workloadApi } from '@/api/workload'
import { feedback } from '@/utils/feedback'

/**
 * @param {Ref<number|null>} projectId 项目 ID（ref，空时跳过拉取）
 * @param {Ref<string>} statDate 统计日期（ref，YYYY-MM-DD）
 */
export function useWorkloadMatrix(projectId, statDate) {
  const items = ref([])
  const matrixWorkers = ref([])      // 当日有填报的人员（含历史自由文本，userId 可为 null）
  const matrixCells = ref({})        // itemId → workerName → {quantity, reviewStatus, reviewRemark}
  const workerPerformance = ref({})  // workerName → 已审核绩效（后端口径）
  const pendingCount = ref(0)
  const loadingMatrix = ref(false)
  const teamWorkers = ref([])        // 项目组在组成员（可填报人员来源）

  /**
   * 拉取某日统计矩阵（并附带项目组成员名单）。
   * 矩阵同时驱动：汇总卡片、统计总览表格、每日填报矩阵、审核角标。
   */
  async function fetchMatrix() {
    if (!projectId.value) return
    loadingMatrix.value = true
    try {
      const [matrixRes, workersRes] = await Promise.allSettled([
        workloadApi.getMatrix({ projectId: projectId.value, date: statDate.value }),
        workloadApi.listWorkers({ projectId: projectId.value })
      ])
      if (matrixRes.status === 'fulfilled') {
        const data = matrixRes.value.data || {}
        items.value = data.items || []
        matrixWorkers.value = data.workers || []
        matrixCells.value = data.matrix || {}
        workerPerformance.value = data.workerPerformance || {}
        pendingCount.value = Number(data.pendingCount || 0)
        // 编辑矩阵（各状态单元格数量回显）由矩阵代填 Tab watch matrixCells 重建
      } else {
        feedback.error(matrixRes.reason?.message || '填报矩阵加载失败')
      }
      if (workersRes.status === 'fulfilled') {
        teamWorkers.value = workersRes.value.data || []
      }
      // 成员名单加载失败时静默降级：仅影响新增列，已有矩阵列不受影响
    } finally {
      loadingMatrix.value = false
    }
  }

  return {
    items,
    matrixWorkers,
    matrixCells,
    workerPerformance,
    pendingCount,
    teamWorkers,
    loadingMatrix,
    fetchMatrix
  }
}
