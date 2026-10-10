/**
 * 审批待办中心 API（小程序/App 端）
 *
 * 与 Web 端共享后端接口契约（/approval/todos）。
 * 聚合四类审核待办（进度/工作量/文档/授权）的统一查询入口。
 * 审核动作不新建接口——直接复用各业务模块已有 API
 * （后端 CAS/权限/通知逻辑零重复），与 Web 端 reviewTodo 分发规则一致：
 *   - 进度：taskApi.reviewProgress(logId, { approve, remark })
 *   - 工作量：workloadApi.reviewRecords({ ids, approve, remark })
 *   - 文档：documentApi.review(id, { approve, remark })
 *   - 授权：projectAuthApi.approveAuthRequest(id, { approve, comment })
 *
 * @module api/approvalTodo
 */
import request from '@/utils/request'
import { taskApi } from '@/api/task'
import { workloadApi } from '@/api/workload'
import { documentApi } from '@/api/document'
import { projectAuthApi } from '@/api/projectAuth'

export const approvalTodoApi = {
	/**
	 * 分页查询当前用户的审批待办
	 * @param {Object} params { source?: 'progress'|'workload'|'document'|'auth', pageNum, pageSize }
	 * @returns {Promise<Object>} 待办分页结果（提交时间倒序）
	 */
	listTodos(params) {
		return request({ url: '/approval/todos', method: 'GET', params })
	},

	/**
	 * 待办分类计数（统计卡片/角标）
	 * @returns {Promise<Object>} { progress, workload, document, auth, total }
	 */
	getTodoCounts() {
		return request({ url: '/approval/todos/count', method: 'GET' })
	},

	/**
	 * 快捷审核：按来源分发到对应业务模块的审核接口。
	 * 各接口后端均含 CAS 状态机 + 归属校验，前端仅做参数适配。
	 *
	 * @param {Object} todo 待办项（source/bizId 来自待办列表）
	 * @param {{ approve: boolean, remark?: string }} payload 审核参数
	 * @returns {Promise<any>} 对应模块的审核结果
	 */
	reviewTodo(todo, payload) {
		switch (todo.source) {
			case 'progress':
				return taskApi.reviewProgress(todo.bizId, payload)
			case 'workload':
				// 批量接口传单条 ID 数组，复用后端 CAS 批量审核
				return workloadApi.reviewRecords({ ids: [todo.bizId], ...payload })
			case 'document':
				return documentApi.review(todo.bizId, payload)
			case 'auth':
				// 后端 approve 接口内部按 approve=false 转发驳回
				return projectAuthApi.approveAuthRequest(todo.bizId, {
					approve: payload.approve,
					comment: payload.remark
				})
			default:
				return Promise.reject(new Error(`未知的待办来源：${todo.source}`))
		}
	}
}
