/**
 * 项目列表筛选状态（跨页面共享）
 * 维护项目列表页的筛选条件、分页数据与加载标记，便于返回列表时保留上下文。
 */
import { reactive } from 'vue'

const state = reactive({
	// 当前筛选条件
	filter: {
		keyword: '',
		status: '',
		priority: '',
		page: 1,
		size: 10
	},
	// 列表数据
	list: [],
	total: 0,
	loading: false,
	noMore: false
})

export const projectStore = {
	state,

	/** 重置所有筛选条件与列表数据到初始状态 */
	reset() {
		state.filter = { keyword: '', status: '', priority: '', page: 1, size: 10 }
		state.list = []
		state.total = 0
		state.loading = false
		state.noMore = false
	},

	/**
	 * 更新筛选条件（合并 patch 并重置到第 1 页）
	 * @param {Object} patch 需要更新的筛选字段
	 */
	setFilter(patch) {
		Object.assign(state.filter, patch, { page: 1 })
		state.noMore = false
	},

	/**
	 * 追加/替换列表数据：第 1 页为替换，其余页为拼接，并更新总数与「没有更多」标记
	 * @param {Array} items 本次返回的列表项
	 * @param {number} total 数据总条数
	 */
	appendList(items, total) {
		state.list = state.filter.page === 1 ? (items || []) : state.list.concat(items || [])
		state.total = total || state.list.length
		state.noMore = state.list.length >= state.total
	}
}

/** 组合式用法入口：返回全局单例 projectStore */
export function useProjectStore() {
	return projectStore
}
