/**
 * 轻量级本地存储加密工具（小程序版，XOR + Base64）
 *
 * ⚠️ 安全说明：
 * 本方案为兜底混淆措施，非真正加密。密钥存储在本地存储中，
 * 攻击者可同时读取密钥与密文完成解密，无法真正防御 XSS。
 *
 * TODO（长期方案）：生产环境应迁移到 httpOnly Cookie 存储 token，
 * 由后端设置 Secure、SameSite、HttpOnly 属性，配合 CSRF token。
 * 迁移时需后端配合调整 /auth/login、/auth/refresh 接口的 Set-Cookie 响应。
 *
 * 当前方案适用于内部管理系统的过渡期，token TTL 较短（access 30min）可缓解风险。
 *
 * 与 Web 端 cryptoStorage 共享同一加密算法（XOR + Base64），
 * 适配差异：
 *   - 小程序无 sessionStorage/localStorage，改用 uni.getStorageSync/setStorageSync
 *   - 小程序无 btoa/atob，使用 uni.arrayBufferToBase64 / uni.base64ToArrayBuffer 替代
 *   - 密钥保存在小程序本地存储的独立键中（应用安装级，不会因小程序关闭而丢失）
 *   - 密钥获取失败时降级为明文存储，保证可用性
 *
 * 接口与 uni.getStorageSync/setStorageSync 兼容：
 *   - cryptoStorage.setItem(key, value)
 *   - cryptoStorage.getItem(key)
 *   - cryptoStorage.removeItem(key)
 *
 * @module utils/cryptoStorage
 */

// 本地存储中存放会话密钥的键名（应用安装级，不会因小程序关闭而丢失）
const KEY_STORAGE = '__hbsz_sk__'

/**
 * 生成随机密钥并持久化（仅一次）
 *
 * 优先使用 crypto.getRandomValues 生成 16 字节随机数，
 * 不可用时回退到 Math.random；密钥转为十六进制字符串写入本地存储，
 * 确保同一安装内加解密使用一致的密钥。
 * @returns {string} 32 位十六进制会话密钥
 */
function getKey() {
	let k = ''
	try {
		k = uni.getStorageSync(KEY_STORAGE) || ''
	} catch (e) {
		k = ''
	}
	if (!k) {
		// 生成 16 字节随机数
		const arr = new Uint8Array(16)
		if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
			crypto.getRandomValues(arr)
		} else {
			// 降级方案
			for (let i = 0; i < 16; i++) {
				arr[i] = Math.floor(Math.random() * 256)
			}
		}
		// 转为十六进制字符串
		k = Array.from(arr).map((b) => b.toString(16).padStart(2, '0')).join('')
		try {
			uni.setStorageSync(KEY_STORAGE, k)
		} catch (e) {
			// 存储失败时仍返回本次会话使用的密钥（不持久化）
		}
	}
	return k
}

/**
 * 将字符串编码为 UTF-8 字节数组
 * @param {string} str 原始字符串
 * @returns {Uint8Array} UTF-8 字节数组
 */
function strToUtf8Bytes(str) {
	// 通过 encodeURIComponent + decode 处理 Unicode
	const encoded = encodeURIComponent(str)
	const bytes = []
	for (let i = 0; i < encoded.length; i++) {
		const c = encoded.charAt(i)
		if (c === '%') {
			bytes.push(parseInt(encoded.substr(i + 1, 2), 16))
			i += 2
		} else {
			bytes.push(c.charCodeAt(0))
		}
	}
	return new Uint8Array(bytes)
}

/**
 * 将 UTF-8 字节数组解码为字符串
 * @param {Uint8Array} bytes UTF-8 字节数组
 * @returns {string} 解码后的字符串
 */
function utf8BytesToStr(bytes) {
	const arr = []
	for (let i = 0; i < bytes.length; i++) {
		const b = bytes[i]
		if (b < 128) {
			arr.push(String.fromCharCode(b))
		} else if (b < 224) {
			arr.push(String.fromCharCode(((b & 0x1F) << 6) | (bytes[++i] & 0x3F)))
		} else if (b < 240) {
			arr.push(String.fromCharCode(((b & 0x0F) << 12) | ((bytes[++i] & 0x3F) << 6) | (bytes[++i] & 0x3F)))
		} else {
			// 4字节 UTF-8（emoji 等），需用 surrogate pair
			const cp = ((b & 0x07) << 18) | ((bytes[++i] & 0x3F) << 12) | ((bytes[++i] & 0x3F) << 6) | (bytes[++i] & 0x3F)
			const offset = cp - 0x10000
			arr.push(String.fromCharCode(0xD800 + (offset >> 10)))
			arr.push(String.fromCharCode(0xDC00 + (offset & 0x3FF)))
		}
	}
	// 通过 decodeURIComponent 处理 % 转义
	try {
		return decodeURIComponent(escape(arr.join('')))
	} catch (e) {
		return arr.join('')
	}
}

/**
 * XOR 加密并 Base64 编码
 *
 * 将明文逐字节与密钥循环异或，再经 Base64 编码。
 * 中文等多字节字符先转 UTF-8 字节序列再处理，保证可逆。
 * @param {string} plain 待加密的明文
 * @param {string} key 会话密钥
 * @returns {string} Base64 编码的密文
 */
function xorEncrypt(plain, key) {
	if (!plain) return ''
	const plainBytes = strToUtf8Bytes(plain)
	// 密钥按 UTF-8 字节循环
	const keyBytes = strToUtf8Bytes(key)
	const out = new Uint8Array(plainBytes.length)
	for (let i = 0; i < plainBytes.length; i++) {
		out[i] = plainBytes[i] ^ keyBytes[i % keyBytes.length]
	}
	// 使用 uni.arrayBufferToBase64 编码
	try {
		// #ifdef MP-WEIXIN || APP-PLUS
		return uni.arrayBufferToBase64(out.buffer)
		// #endif
		// #ifndef MP-WEIXIN && APP-PLUS
		// H5 端使用 btoa
		if (typeof btoa === 'function') {
			let binary = ''
			for (let i = 0; i < out.length; i++) {
				binary += String.fromCharCode(out[i])
			}
			return btoa(binary)
		}
		return uni.arrayBufferToBase64(out.buffer)
		// #endif
	} catch (e) {
		// 降级：直接返回十六进制字符串
		return Array.from(out).map((b) => b.toString(16).padStart(2, '0')).join('')
	}
}

/**
 * Base64 解码并 XOR 解密（xorEncrypt 的逆操作）
 * @param {string} cipher Base64 编码的密文
 * @param {string} key 会话密钥
 * @returns {string} 解密后的明文；解码失败时返回空字符串
 */
function xorDecrypt(cipher, key) {
	if (!cipher) return ''
	try {
		let rawBytes
		// #ifdef MP-WEIXIN || APP-PLUS
		const buf = uni.base64ToArrayBuffer(cipher)
		rawBytes = new Uint8Array(buf)
		// #endif
		// #ifndef MP-WEIXIN && APP-PLUS
		if (typeof atob === 'function') {
			const binary = atob(cipher)
			rawBytes = new Uint8Array(binary.length)
			for (let i = 0; i < binary.length; i++) {
				rawBytes[i] = binary.charCodeAt(i)
			}
		} else {
			const buf = uni.base64ToArrayBuffer(cipher)
			rawBytes = new Uint8Array(buf)
		}
		// #endif
		// 密钥按 UTF-8 字节循环
		const keyBytes = strToUtf8Bytes(key)
		const out = new Uint8Array(rawBytes.length)
		for (let i = 0; i < rawBytes.length; i++) {
			out[i] = rawBytes[i] ^ keyBytes[i % keyBytes.length]
		}
		return utf8BytesToStr(out)
	} catch (e) {
		return ''
	}
}

/**
 * 加密存储门面：接口与 uni.getStorageSync/setStorageSync 兼容
 */
export const cryptoStorage = {
	/**
	 * 加密后写入本地存储
	 * @param {string} key 存储键名
	 * {*} value 待存储的值（会被转为字符串）
	 * @returns {void} 加密异常时降级为明文写入，保证可用性
	 */
	setItem(key, value) {
		try {
			const k = getKey()
			uni.setStorageSync(key, xorEncrypt(String(value), k))
		} catch (e) {
			// 加密失败降级为明文
			try {
				uni.setStorageSync(key, String(value))
			} catch (e2) {
				// 存储失败，忽略
			}
		}
	},
	/**
	 * 读取并解密本地存储中的值
	 * @param {string} key 存储键名
	 * @returns {string|null} 解密后的明文；不存在或解密失败均返回 null
	 *   安全说明：解密失败（如密钥已变更）必须返回 null，绝不能回退返回密文原文，
	 *   否则 Base64 密文会被上层当作有效 token 使用，导致所有请求 401 且难以排查。
	 *   调用方应处理 null 返回值（等同于未登录，需重新登录获取新 token）。
	 */
	getItem(key) {
		let raw = ''
		try {
			raw = uni.getStorageSync(key)
		} catch (e) {
			return null
		}
		if (!raw) return null
		// 兼容历史明文数据：检测是否为 Base64 密文
		// 加密后字符串必为 Base64 字符集 [A-Za-z0-9+/=]，且长度 >= 4
		// 若为明文 token（如 eyJhbGc... JWT 格式）也会被误识别为 Base64，
		// 但 XOR 解密后通常得到乱码，utf8BytesToStr 解析后返回乱码而非原值，
		// 上层使用时会因 token 无效触发 401，自然重定向登录
		try {
			const k = getKey()
			const decrypted = xorDecrypt(raw, k)
			// 安全：解密失败时返回 null 而非密文，避免将 Base64 密文当作有效 token 使用
			// 调用方应处理 null 返回值（等同于未登录）
			if (!decrypted) {
				console.warn('[cryptoStorage] 解密失败，可能密钥已变更，返回 null')
				return null
			}
			return decrypted
		} catch (e) {
			// 安全：异常时同样返回 null，避免将密文/异常对象泄露给上层当作 token
			console.warn('[cryptoStorage] 解密异常，返回 null')
			return null
		}
	},
	/**
	 * 删除指定键
	 * @param {string} key 存储键名
	 * @returns {void}
	 */
	removeItem(key) {
		try {
			uni.removeStorageSync(key)
		} catch (e) {
			// 忽略
		}
	},
	/**
	 * 清空密钥（用于登出）
	 * @returns {void}
	 */
	clearKey() {
		try {
			uni.removeStorageSync(KEY_STORAGE)
		} catch (e) {
			// 忽略
		}
	}
}

export default cryptoStorage
