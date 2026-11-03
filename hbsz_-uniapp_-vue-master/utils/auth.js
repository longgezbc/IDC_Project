/**
 * 认证信息本地存储管理
 * 统一封装 Token / RefreshToken / 用户信息在本地存储中的读写，
 * 底层使用 cryptoStorage（XOR+Base64 混淆）存储，避免 JWT 明文落盘被 XSS 直接读取。
 *
 * 安全修复（P1-1）：原实现直接使用 uni.setStorageSync 明文存储 token，
 * cryptoStorage.js 虽已实现但未被任何模块引用（死代码）。
 * 现将存储底层切换为 cryptoStorage，使加密能力生效。
 *
 * 迁移说明：切换后，历史明文存储的 token 会因 XOR 解密失败而被 cryptoStorage 返回 null，
 * 等同于未登录，用户需重新登录一次获取新 token（一次性影响，可接受）。
 *
 * 存储键名集中定义在 config.js（TOKEN_KEY 等），便于统一维护与登出清理。
 */
import { TOKEN_KEY, REFRESH_TOKEN_KEY, USER_INFO_KEY } from './config'
import { cryptoStorage } from './cryptoStorage'

export const auth = {
	/**
	 * 获取 Token
	 * @returns {string} 本地存储的访问令牌（解密后），不存在或解密失败时返回空字符串
	 */
	getToken() {
		return cryptoStorage.getItem(TOKEN_KEY) || ''
	},

	/**
	 * 获取 RefreshToken
	 * @returns {string} 本地存储的刷新令牌（解密后），不存在或解密失败时返回空字符串
	 */
	getRefreshToken() {
		return cryptoStorage.getItem(REFRESH_TOKEN_KEY) || ''
	},

	/**
	 * 获取用户信息（兼容存储值为 JSON 字符串或对象两种情况）
	 * @returns {Object|null} 用户信息对象，不存在或解密失败时返回 null
	 */
	getUserInfo() {
		const raw = cryptoStorage.getItem(USER_INFO_KEY)
		if (!raw) return null
		try {
			return typeof raw === 'string' ? JSON.parse(raw) : raw
		} catch (e) {
			console.warn('[auth] userInfo 解析失败，返回 null')
			return null
		}
	},

	/**
	 * 保存登录信息（登录成功或刷新 Token 后调用）
	 * @param {Object} payload 登录数据
	 * @param {string} payload.token 访问令牌
	 * @param {string} [payload.refreshToken] 刷新令牌（存在才写入）
	 * @param {Object} [payload.userInfo] 用户信息（存在才写入，会序列化为 JSON）
	 */
	saveAuth({ token, refreshToken, userInfo }) {
		if (token) cryptoStorage.setItem(TOKEN_KEY, token)
		if (refreshToken) cryptoStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
		if (userInfo) cryptoStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
	},

	/**
	 * 更新用户信息
	 * @param {Object} userInfo 用户信息对象（会序列化为 JSON 后加密存储）
	 */
	saveUserInfo(userInfo) {
		cryptoStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
	},

	/** 清除登录信息（登出或登录失效时调用，移除全部本地凭证） */
	clearAuth() {
		cryptoStorage.removeItem(TOKEN_KEY)
		cryptoStorage.removeItem(REFRESH_TOKEN_KEY)
		cryptoStorage.removeItem(USER_INFO_KEY)
	},

	/**
	 * 是否已登录
	 * @returns {boolean} 存在 Token 即视为已登录
	 */
	isLoggedIn() {
		return !!this.getToken()
	}
}
