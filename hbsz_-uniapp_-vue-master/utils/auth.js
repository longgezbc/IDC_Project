/**
 * 认证信息本地存储管理
 * 统一封装 Token / RefreshToken / 用户信息在本地存储中的读写，
 * 底层使用 uni.getStorageSync/setStorageSync/removeStorageSync 同步读写，
 * 存储键名集中定义在 config.js（TOKEN_KEY 等），便于统一维护与登出清理。
 */
import { TOKEN_KEY, REFRESH_TOKEN_KEY, USER_INFO_KEY } from './config'

export const auth = {
	/**
	 * 获取 Token
	 * @returns {string} 本地存储的访问令牌，不存在时返回空字符串
	 */
	getToken() {
		return uni.getStorageSync(TOKEN_KEY) || ''
	},

	/**
	 * 获取 RefreshToken
	 * @returns {string} 本地存储的刷新令牌，不存在时返回空字符串
	 */
	getRefreshToken() {
		return uni.getStorageSync(REFRESH_TOKEN_KEY) || ''
	},

	/**
	 * 获取用户信息（兼容存储值为 JSON 字符串或对象两种情况）
	 * @returns {Object|null} 用户信息对象，不存在时返回 null
	 */
	getUserInfo() {
		const raw = uni.getStorageSync(USER_INFO_KEY)
		return raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : null
	},

	/**
	 * 保存登录信息（登录成功或刷新 Token 后调用）
	 * @param {Object} payload 登录数据
	 * @param {string} payload.token 访问令牌
	 * @param {string} [payload.refreshToken] 刷新令牌（存在才写入）
	 * @param {Object} [payload.userInfo] 用户信息（存在才写入）
	 */
	saveAuth({ token, refreshToken, userInfo }) {
		uni.setStorageSync(TOKEN_KEY, token || '')
		if (refreshToken) uni.setStorageSync(REFRESH_TOKEN_KEY, refreshToken)
		if (userInfo) uni.setStorageSync(USER_INFO_KEY, userInfo)
	},

	/**
	 * 更新用户信息
	 * @param {Object} userInfo 用户信息对象
	 */
	saveUserInfo(userInfo) {
		uni.setStorageSync(USER_INFO_KEY, userInfo)
	},

	/** 清除登录信息（登出或登录失效时调用，移除全部本地凭证） */
	clearAuth() {
		uni.removeStorageSync(TOKEN_KEY)
		uni.removeStorageSync(REFRESH_TOKEN_KEY)
		uni.removeStorageSync(USER_INFO_KEY)
	},

	/**
	 * 是否已登录
	 * @returns {boolean} 存在 Token 即视为已登录
	 */
	isLoggedIn() {
		return !!this.getToken()
	}
}
