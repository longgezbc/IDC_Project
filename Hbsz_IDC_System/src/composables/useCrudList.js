/**
 * 通用列表 CRUD 组合式函数（P1-2.2）
 *
 * 封装列表页通用的数据拉取、搜索、重置、分页状态管理逻辑，减少各列表页的重复脚本。
 * 业务页面仅需传入查询函数与初始查询参数，即可获得 list/total/loading/queryParams
 * 以及 fetchList/handleSearch/handleReset 三个标准方法。
 *
 * 用法：
 *   import { useCrudList } from '@/composables/useCrudList'
 *   import { positionApi } from '@/api/position'
 *
 *   const { list, total, loading, queryParams, fetchList, handleSearch, handleReset } =
 *     useCrudList(positionApi.query, { code: '', name: '', status: undefined })
 *
 *   onMounted(fetchList)
 *
 * 响应体兼容：支持 { records, total } 与 { list, total } 两种字段命名（后端逐步迁移期兼容）。
 *
 * 竞态保护：快速翻页/搜索时，先发的慢请求可能后返回，覆盖新数据。
 *          使用序列号机制——每次 fetchList 递增 seq，响应返回时比对 seq，
 *          若非最新 seq 则丢弃响应，确保只有最后一次请求的结果被写入。
 *
 * @param {Function} fetchFn 列表查询函数，接收 queryParams 参数，返回 { data: { records|list, total } }
 * @param {Object} initialQuery 初始业务查询参数（不含 pageNum/pageSize，由本函数注入默认值）
 * @returns {{
 *   list: import('vue').Ref<Array>,
 *   total: import('vue').Ref<number>,
 *   loading: import('vue').Ref<boolean>,
 *   error: import('vue').Ref<boolean>,
 *   queryParams: Object,
 *   fetchList: () => Promise<void>,
 *   handleSearch: () => Promise<void>,
 *   handleReset: () => Promise<void>
 * }}
 */
import { ref, reactive } from 'vue'

export function useCrudList(fetchFn, initialQuery = {}) {
  const list = ref([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref(false)
  // 分页参数与业务筛选字段合并；pageNum/pageSize 提供默认值，可被 initialQuery 覆盖
  const queryParams = reactive({
    pageNum: 1,
    pageSize: 10,
    ...initialQuery
  })

  // 竞态保护序列号：每次 fetchList 递增，响应返回时比对，非最新则丢弃
  let requestSeq = 0

  /**
   * 拉取列表数据
   * 异常时清空列表与总数，避免显示陈旧数据；loading 在 finally 中关闭确保必定复位。
   * 竞态保护：若快速连续调用 fetchList（如翻页），先发的慢请求返回时 seq 已过期，
   *          其结果被丢弃，只有最后一次请求的结果写入 list/total。
   */
  async function fetchList() {
    const currentSeq = ++requestSeq
    loading.value = true
    error.value = false
    try {
      const res = await fetchFn(queryParams)
      // 竞态保护：若期间又发起了新请求，丢弃本次过期响应
      if (currentSeq !== requestSeq) return
      // 兼容后端 records/list 两种字段命名
      list.value = res.data?.records || res.data?.list || []
      total.value = res.data?.total || 0
    } catch (e) {
      // 竞态保护：过期的请求失败也不影响最新状态
      if (currentSeq !== requestSeq) return
      console.error('[useCrudList] 查询列表失败:', e?.message)
      error.value = true
      list.value = []
      total.value = 0
    } finally {
      // 仅当本次请求是最新请求时才关闭 loading，避免被过期请求的 finally 提前关闭
      if (currentSeq === requestSeq) {
        loading.value = false
      }
    }
  }

  /**
   * 搜索：重置到第一页后拉取列表
   * 切换筛选条件后应回到第一页，避免停留在不存在的页码导致空列表。
   */
  function handleSearch() {
    queryParams.pageNum = 1
    return fetchList()
  }

  /**
   * 重置：将业务筛选字段恢复为初始值并回到第一页，然后拉取列表
   * 仅重置 initialQuery 中声明的字段，不影响分页参数结构。
   */
  function handleReset() {
    Object.keys(initialQuery).forEach(key => {
      queryParams[key] = initialQuery[key]
    })
    queryParams.pageNum = 1
    return fetchList()
  }

  return { list, total, loading, error, queryParams, fetchList, handleSearch, handleReset }
}
