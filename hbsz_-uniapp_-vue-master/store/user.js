/**
 * 用户状态管理（轻量响应式 store，不依赖 pinia）
 * 通过 Vue 的 reactive 维护登录态，并与本地存储（utils/auth）双向同步：
 * 初始化时从 Storage 读取，登录/登出/刷新用户信息时同步更新 state 与 Storage。
 */
import { reactive, computed } from 'vue'
import { auth } from '@/utils/auth'
import { authApi } from '@/api/auth'
import { stopNotificationWatch } from '@/utils/ws'

// 响应式登录态：token、refreshToken、userInfo 初始值取自本地存储
const state = reactive({
	token: auth.getToken(),
	refreshToken: auth.getRefreshToken(),
	userInfo: auth.getUserInfo()
})

export const userStore = {
	state,

	/** 是否已登录（存在 token 即视为已登录） */
	get isLoggedIn() {
		return !!state.token
	},

	/** 用户显示名称（优先 name，其次 username） */
	get userName() {
		return state.userInfo?.name || state.userInfo?.username || ''
	},

	/** 用户头像 URL */
	get avatar() {
		return state.userInfo?.avatar || ''
	},

	/** 用户角色列表 */
	get roles() {
		return state.userInfo?.roles || []
	},

	/**
	 * 登录：调用登录接口，成功后同步保存到 Storage 与 state
	 * @param {Object} payload 登录参数（username、password、captchaId、captchaAnswer、remember）
	 * @returns {Promise<Object>} 登录结果
	 */
	async login({ username, password, captchaId, captchaAnswer, remember }) {
		const data = { username, password, remember: !!remember }
		if (captchaId) data.captchaId = captchaId
		if (captchaAnswer !== undefined) data.captcha = captchaAnswer
		const result = await authApi.login(data)
		const { token, refreshToken, userInfo } = result || {}
		auth.saveAuth({ token, refreshToken, userInfo })
		state.token = token
		state.refreshToken = refreshToken
		state.userInfo = userInfo
		return result
	},

	/**
	 * 登出：调用登出接口（失败忽略），本地登录态一定清除
	 * @returns {Promise<void>}
	 */
	async logout() {
		// 先停止 WebSocket 通知监听，避免连接泄露（try-catch 包住避免 ws 异常影响 logout）
		try {
			stopNotificationWatch()
		} catch (e) {
			console.warn('[user] stopNotificationWatch failed', e)
		}
		try {
			if (state.token) await authApi.logout()
		} catch (e) {
			// 忽略登出接口错误，本地一定清除
		}
		auth.clearAuth()
		state.token = ''
		state.refreshToken = ''
		state.userInfo = null
	},

	/**
	 * 拉取最新用户信息并同步到 state/Storage（静默失败）
	 * @returns {Promise<Object|null>} 用户信息或 null
	 */
	async fetchUserInfo() {
		if (!state.token) return null
		try {
			const info = await authApi.getInfo()
			if (info) {
				state.userInfo = info
				auth.saveUserInfo(info)
			}
			return info
		} catch (e) {
			return null
		}
	},

	/** 从本地存储同步到 state（App 启动时调用） */
	syncFromStorage() {
		state.token = auth.getToken()
		state.refreshToken = auth.getRefreshToken()
		state.userInfo = auth.getUserInfo()
	}
}

/** 组合式用法入口：返回全局单例 userStore */
export function useUserStore() {
	return userStore
}
