/**
 * 客户状态仓库（Pinia setup store）
 *
 * 缓存客户列表与分页总数，维护当前查看的客户详情与下拉用的全部启用客户。
 * 动作方法封装列表/详情/CRUD，写操作成功后统一弹出成功提示。
 * @module stores/customer
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { customerApi } from '@/api/customer'
import { feedback } from '@/utils/feedback'
import { isCancelError } from '@/utils/cancelToken'

export const useCustomerStore = defineStore('customer', () => {
  // ===== state =====
  /** @type {import('vue').Ref<Array>} 当前列表页的客户集合 */
  const customers = ref([])
  /** @type {import('vue').Ref<number>} 列表总记录数（用于分页） */
  const total = ref(0)
  /** @type {import('vue').Ref<boolean>} 列表加载中标记 */
  const loading = ref(false)
  /** @type {import('vue').Ref<Object|null>} 当前查看的客户详情 */
  const currentCustomer = ref(null)
  /** @type {import('vue').Ref<Array>} 全部启用客户（下拉用，不分页） */
  const allCustomers = ref([])

  // ===== getters =====
  /** 启用状态的客户数 */
  const enabledCount = computed(() => customers.value.filter(c => c.status === 1).length)

  /** fetchList 竞态保护序列号：快速翻页/搜索时丢弃乱序返回的过期响应 */
  let fetchListSeq = 0

  /**
   * 拉取客户列表
   *
   * 竞态保护（P2 修复）：先发的慢请求后返回会覆盖新数据，序列号机制确保仅最新请求的结果生效。
   * 副作用：更新 customers、total 与 loading；兼容后端 records/list 两种字段。
   * @param {Object} [params={}] 分页与筛选参数
   * @returns {Promise<Object>} 列表响应数据
   */
  async function fetchList(params = {}) {
    const currentSeq = ++fetchListSeq
    loading.value = true
    try {
      const res = await customerApi.getList(params)
      if (currentSeq !== fetchListSeq) return res.data
      customers.value = res.data.records || res.data.list || []
      total.value = res.data.total || 0
      return res.data
    } catch (error) {
      if (currentSeq !== fetchListSeq) return null
      if (isCancelError(error)) return null
      throw error
    } finally {
      if (currentSeq === fetchListSeq) {
        loading.value = false
      }
    }
  }

  /**
   * 拉取所有启用客户（下拉用）
   *
   * 副作用：更新 allCustomers。
   * @returns {Promise<Array>} 启用客户列表
   */
  async function fetchAllEnabled() {
    const res = await customerApi.listAll()
    allCustomers.value = res.data || []
    return res.data
  }

  /**
   * 拉取客户详情
   *
   * 副作用：更新 currentCustomer。
   * @param {number|string} id 客户 ID
   * @returns {Promise<Object>} 客户详情数据
   */
  async function fetchDetail(id) {
    const res = await customerApi.getDetail(id)
    currentCustomer.value = res.data
    return res.data
  }

  /**
   * 新建客户
   *
   * 副作用：成功后弹出成功提示。
   * @param {Object} data 客户字段数据
   * @returns {Promise<Object>} 新建后的客户数据
   */
  async function addCustomer(data) {
    const res = await customerApi.create(data)
    feedback.success('客户创建成功')
    return res.data
  }

  /**
   * 更新客户
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 客户 ID
   * @param {Object} data 待更新的客户字段
   * @returns {Promise<Object>} 更新后的客户数据
   */
  async function updateCustomer(id, data) {
    const res = await customerApi.update(id, data)
    feedback.success('客户更新成功')
    return res.data
  }

  /**
   * 删除客户
   *
   * 副作用：成功后弹出成功提示。
   * @param {number|string} id 客户 ID
   * @returns {Promise<void>}
   */
  async function deleteCustomer(id) {
    await customerApi.remove(id)
    feedback.success('客户已删除')
  }

  /**
   * 重置 store 状态到初始值
   * 修复 M21：登出时调用，避免下一个用户看到上一个用户缓存的客户列表/详情
   * @returns {void}
   */
  function reset() {
    customers.value = []
    total.value = 0
    loading.value = false
    currentCustomer.value = null
    allCustomers.value = []
  }

  return {
    customers,
    total,
    loading,
    currentCustomer,
    allCustomers,
    enabledCount,
    fetchList,
    fetchAllEnabled,
    fetchDetail,
    addCustomer,
    updateCustomer,
    deleteCustomer,
    reset
  }
})
