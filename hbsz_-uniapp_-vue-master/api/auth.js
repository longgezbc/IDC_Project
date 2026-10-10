/**
 * 认证模块 API
 * 封装登录、登出、验证码、用户信息、Token 刷新等接口。
 * 底层统一走 utils/request，成功时 resolve 后端响应中的 data 字段。
 */
import request from '@/utils/request'

export const authApi = {
	/**
	 * 获取图形验证码
	 * @returns {Promise<Object>} 验证码信息（如 captchaId、图片数据）
	 */
	getCaptcha() {
		return request({ url: '/captcha', method: 'GET' })
	},

	/**
	 * 账号登录
	 * @param {Object} data 登录参数（username、password、captcha、remember 等）
	 * @returns {Promise<Object>} 登录结果（token、refreshToken、userInfo）
	 */
	login(data) {
		return request({ url: '/auth/login', method: 'POST', data })
	},

	/**
	 * 登出（需携带 Token）
	 * @returns {Promise<any>}
	 */
	logout() {
		return request({ url: '/auth/logout', method: 'POST', skipAuth: false })
	},

	/**
	 * 获取当前登录用户信息
	 * @returns {Promise<Object>} 用户信息
	 */
	getInfo() {
		return request({ url: '/auth/info', method: 'GET' })
	},

	/**
	 * 刷新 Token（跳过鉴权头，避免死循环）
	 * @param {string} refreshToken 刷新令牌
	 * @returns {Promise<Object>} 新的 token 信息
	 */
	refresh(refreshToken) {
		return request({
			url: '/auth/refresh',
			method: 'POST',
			data: { refreshToken },
			skipAuth: true
		})
	}
}
