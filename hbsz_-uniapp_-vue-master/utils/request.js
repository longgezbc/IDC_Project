/**
 * 网络请求封装
 * 基于 uni.request，统一处理 Token、错误码、401 跳登录、轻提示
 * 后端统一响应格式：{ code, message, data, timestamp }
 * 业务约定：code === 200 为成功；401/2001/2002 为认证失效
 */
import { API_BASE_URL, REQUEST_TIMEOUT } from './config'
import { auth } from './auth'

// 是否正在刷新 Token（并发请求遇到 401 时，仅由第一个请求触发刷新）
let isRefreshing = false
// 等待 Token 刷新的请求队列：刷新期间到来的请求先挂起，刷新成功后统一重放
let pendingQueue = []
// 队列上限保护：极端场景（大量并发 401）下防止队列无限堆积导致内存压力
const MAX_PENDING_QUEUE = 50

/** 刷新成功：用新 Token 依次唤醒队列中挂起的请求 */
function notifyQueue(newToken) {
	pendingQueue.forEach((cb) => cb(newToken))
	pendingQueue = []
}

/** 刷新失败：以错误依次拒绝队列中挂起的请求 */
function clearQueue(err) {
	pendingQueue.forEach((cb) => cb(null, err))
	pendingQueue = []
}

/** 跳转登录页（强制 reLaunch，清空栈） */
function redirectToLogin(message) {
	auth.clearAuth()
	if (message) {
		uni.showToast({ title: message, icon: 'none', duration: 1500 })
	}
	setTimeout(() => {
		uni.reLaunch({ url: '/pages/login/login' })
	}, 600)
}

/**
 * 使用 refreshToken 刷新 accessToken
 * @returns {Promise<string|null>} 新 token 或 null
 */
function refreshAccessToken() {
	const refreshToken = auth.getRefreshToken()
	if (!refreshToken) return Promise.resolve(null)
	return new Promise((resolve) => {
		uni.request({
			url: API_BASE_URL + '/auth/refresh',
			method: 'POST',
			data: { refreshToken },
			header: { 'Content-Type': 'application/json;charset=utf-8' },
			timeout: REQUEST_TIMEOUT,
			success: (res) => {
				const body = res.data || {}
				if (res.statusCode === 200 && body.code === 200 && body.data && body.data.token) {
					const { token, refreshToken: newRt, userInfo } = body.data
					auth.saveAuth({ token, refreshToken: newRt, userInfo })
					// 同步更新 userStore 响应式状态，避免 Token 刷新后 store 中仍是旧值
					// 使用动态 import 打破与 store/user.js 的循环依赖
					import('@/store/user').then(({ userStore }) => {
						userStore.state.token = token
						if (newRt) userStore.state.refreshToken = newRt
						if (userInfo) userStore.state.userInfo = userInfo
					}).catch(() => {})
					resolve(token)
				} else {
					resolve(null)
				}
			},
			fail: () => resolve(null)
		})
	})
}

/**
 * 发起请求
 * @param {Object} options { url, method, data, params, header, responseType, silent, skipAuth, fullUrl }
 * @returns {Promise<any>} 成功时 resolve(data 字段)，失败 reject(Error)
 */
function request(options) {
	const {
		url,
		method = 'GET',
		data,
		params,
		header = {},
		responseType,
		silent = false,
		skipAuth = false,
		fullUrl = false
	} = options

	// 拼装 URL
	let finalUrl = fullUrl ? url : API_BASE_URL + url
	if (params && Object.keys(params).length) {
		const qs = Object.keys(params)
			.filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
			.map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
			.join('&')
		if (qs) finalUrl += (finalUrl.includes('?') ? '&' : '?') + qs
	}

	// 请求头
	const finalHeader = Object.assign({ 'Content-Type': 'application/json;charset=utf-8' }, header)
	if (!skipAuth) {
		const token = auth.getToken()
		if (token) finalHeader['Authorization'] = `Bearer ${token}`
	}

	return new Promise((resolve, reject) => {
		const task = uni.request({
			url: finalUrl,
			method: method.toUpperCase(),
			data,
			header: finalHeader,
			timeout: REQUEST_TIMEOUT,
			responseType,
			success: (res) => {
				// 文件下载（arraybuffer/binary）
				if (responseType === 'arraybuffer') {
					if (res.statusCode >= 200 && res.statusCode < 300) {
						resolve(res.data)
					} else {
						const err = new Error('下载失败')
						err.statusCode = res.statusCode
						reject(err)
					}
					return
				}

				const body = res.data || {}
				// HTTP 错误
				if (res.statusCode < 200 || res.statusCode >= 300) {
					// 401：尝试刷新
					if (res.statusCode === 401 && !skipAuth && !options.__retried) {
						handle401(options, resolve, reject)
						return
					}
					const msg = body.message || `请求失败(${res.statusCode})`
					if (!silent) uni.showToast({ title: msg, icon: 'none' })
					const err = new Error(msg)
					err.statusCode = res.statusCode
					err.body = body
					reject(err)
					return
				}

				// 业务成功
				if (body.code === 200) {
					resolve(body.data)
					return
				}

				// 认证失效：401 / 2001 / 2002
				if (body.code === 401 || body.code === 2001 || body.code === 2002) {
					if (!skipAuth && !options.__retried) {
						handle401(options, resolve, reject)
						return
					}
					redirectToLogin(body.message || '登录已失效')
					reject(new Error(body.message || '登录已失效'))
					return
				}

				// 2003：无权限
				if (body.code === 2003) {
					if (!silent) uni.showToast({ title: '无操作权限', icon: 'none' })
					reject(new Error('无操作权限'))
					return
				}

				// 其它业务错误
				const msg = body.message || '请求失败'
				if (!silent) uni.showToast({ title: msg, icon: 'none' })
				reject(new Error(msg))
			},
			fail: (err) => {
				const msg = '网络异常，请检查网络连接'
				if (!silent) uni.showToast({ title: msg, icon: 'none' })
				reject(new Error(msg))
			}
		})

		// 返回 task 以便外部可取消（如 abort）
		if (options.onTask) options.onTask(task)
	})
}

/**
 * 处理 401：刷新 token 后重试，或加入队列
 * 并发场景下仅第一个请求触发刷新（isRefreshing 标记），其余请求挂入 pendingQueue 等待；
 * 刷新成功后用新 Token 统一重放挂起请求，失败则清空队列并跳转登录页。
 * 通过 __retried 标记避免重试请求再次进入 401 处理导致死循环。
 * @param {Object} options 原始请求参数
 * @param {Function} resolve 原始 Promise 的 resolve
 * @param {Function} reject 原始 Promise 的 reject
 */
function handle401(options, resolve, reject) {
	if (isRefreshing) {
		// 队列上限保护：超过上限直接拒绝，避免极端场景下内存堆积
		if (pendingQueue.length >= MAX_PENDING_QUEUE) {
			reject(new Error('登录刷新队列已满，请稍后重试'))
			return
		}
		// 挂入队列等待
		pendingQueue.push((newToken, err) => {
			if (err || !newToken) {
				reject(err || new Error('登录已失效'))
				return
			}
			const retried = Object.assign({}, options, { __retried: true })
			request(retried).then(resolve).catch(reject)
		})
		return
	}

	isRefreshing = true
	refreshAccessToken()
		.then((newToken) => {
			isRefreshing = false
			if (!newToken) {
				clearQueue(new Error('登录已失效'))
				redirectToLogin('登录已失效，请重新登录')
				reject(new Error('登录已失效'))
				return
			}
			notifyQueue(newToken)
			const retried = Object.assign({}, options, { __retried: true })
			request(retried).then(resolve).catch(reject)
		})
		.catch((err) => {
			isRefreshing = false
			clearQueue(err)
			redirectToLogin('登录已失效，请重新登录')
			reject(err)
		})
}

export default request
