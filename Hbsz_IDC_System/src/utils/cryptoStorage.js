/**
 * 轻量级本地存储加密工具（XOR + Base64）
 *
 * ⚠️ 安全说明：
 * 本方案为兜底混淆措施，非真正加密。密钥存储在 localStorage 中，
 * XSS 攻击者可同时读取密钥与密文完成解密，无法真正防御 XSS。
 *
 * TODO（长期方案）：生产环境应迁移到 httpOnly Cookie 存储 token，
 * 由后端设置 Secure、SameSite、HttpOnly 属性，配合 CSRF token。
 * 迁移时需后端配合调整 /auth/login、/auth/refresh 接口的 Set-Cookie 响应。
 *
 * 当前方案适用于内部管理系统的过渡期，token TTL 较短（access 30min）可缓解风险。
 *
 * 说明：
 *   - 采用 XOR + Base64 的轻量方案，密钥按用户指纹派生，不追求强加密（前端无法做到真正安全）
 *   - 真正的防护应依赖 httpOnly cookie + 后端 CSRF token，此为兜底措施
 *   - 若密钥获取失败，降级为明文存储，保证可用性
 *
 * 安全说明（修复 L11）：
 *   注意：本方案仅为简单混淆，非真正加密。XOR+Base64 可被轻易逆向，
 *   密钥存储在 localStorage，XSS 攻击者仍可通过读取 localStorage 密钥
 *   完成解密，无法防御 XSS 攻击。当前方案仅能防止 localStorage 被直接
 *   明文读取（如开发者工具、第三方脚本误读取），不能作为安全防线。
 *   生产环境应改用 httpOnly Cookie 存储 token，由后端设置 Secure、
 *   SameSite、HttpOnly 属性，配合 CSRF token 才能提供真正的防护。
 *
 * 修复 #17：增强加密强度注释，添加明确 TODO 标记与长期迁移建议，便于后续安全升级追踪。
 * 修复 #18：密钥从 sessionStorage 迁移到 localStorage。原方案密钥存 sessionStorage，
 *   强制刷新（Ctrl+F5）会清空 sessionStorage 导致密钥丢失，无法解密 localStorage 中
 *   的 token 密文，用户被强制踢回登录页。迁移到 localStorage 后密钥与密文同生命周期，
 *   刷新页面可正常恢复登录态。
 * 修复 #19：密钥版本化（v2）。#18 修复后老用户首次访问会遗留用旧密钥（sessionStorage
 *   中已丢失）加密的旧密文，无法被新密钥解密，导致控制台出现"解密失败"警告且 token
 *   被静默丢弃。引入密钥版本号，首次生成 v2 密钥时主动一次性清理所有旧密文，避免脏数据
 *   残留与无意义日志噪声。后续如需再次轮换密钥，递增 VERSION 即可触发新一轮清理。
 */

// localStorage 中存放加密密钥的键名（持久化，与密文同生命周期）
// 注：原方案使用 sessionStorage 存放密钥，强制刷新会清空 sessionStorage 导致密钥丢失
// 密钥版本化：v2 起，密钥键名带版本后缀，便于后续密钥轮换时主动清理旧密文
const KEY_VERSION = 2
const KEY_STORAGE = `__hbsz_sk_v${KEY_VERSION}__`

// 需要参与自动清理的业务键名（所有用 cryptoStorage 加密存储的键）
// 首次升级或密钥轮换时，这些键的旧密文将被一次性清除
const MANAGED_KEYS = ['hbsz_token', 'hbsz_refresh_token', 'hbsz_user']

/**
 * 首次升级/密钥轮换时清理旧密文
 *
 * 触发条件：localStorage 中不存在当前版本的密钥（KEY_STORAGE）
 * 清理对象：MANAGED_KEYS 中所有键的旧密文
 * 副作用：清理后写入新版本密钥，后续不再触发（直到 VERSION 递增）
 *
 * 设计说明：
 *   - 旧密文用旧密钥加密，旧密钥已随 sessionStorage 清空而丢失，无法解密
 *   - 与其让每次 getItem 解密失败返回 null 并打日志，不如首次发现时一次性清理
 *   - 清理后用户需重新登录获取新 token（用新密钥加密存储），属合理的一次性切换成本
 */
function cleanupStaleCipherIfFirstRun() {
  // 若当前版本密钥已存在，说明已升级过，无需清理
  if (localStorage.getItem(KEY_STORAGE)) return
  // 首次升级：清理所有受管理键的旧密文
  let cleaned = 0
  for (const k of MANAGED_KEYS) {
    if (localStorage.getItem(k) !== null) {
      localStorage.removeItem(k)
      cleaned++
    }
  }
  if (cleaned > 0) {
    // 密钥升级属异常状态（旧密文全部失效），用 warn 级别保留线上排查线索
    console.warn(`[cryptoStorage] 检测到密钥升级至 v${KEY_VERSION}，已清理 ${cleaned} 个旧密文，请重新登录`)
  }
}

/**
 * 生成随机密钥并持久化（仅一次）
 *
 * 优先使用 crypto.getRandomValues 生成 16 字节随机数，
 * 不可用时回退到 Math.random；密钥转为十六进制字符串写入 localStorage，
 * 确保持久化存储中的密文能被稳定解密（包括页面刷新、标签页关闭重开场景）。
 * @returns {string} 32 位十六进制加密密钥
 */
function getKey() {
  // 首次访问时先清理旧版本密文（若存在）
  cleanupStaleCipherIfFirstRun()
  let k = localStorage.getItem(KEY_STORAGE)
  if (!k) {
    const arr = new Uint8Array(16)
    ;(crypto?.getRandomValues || (() => { for (let i = 0; i < 16; i++) arr[i] = Math.floor(Math.random() * 256) }))()
    k = Array.from(arr).map((b) => b.toString(16).padStart(2, '0')).join('')
    localStorage.setItem(KEY_STORAGE, k)
  }
  return k
}

/**
 * XOR 加密并 Base64 编码
 *
 * 将明文逐字符与密钥循环异或，再经 unescape(encodeURIComponent) 处理
 * Unicode 后 Base64 编码，保证中文等多字节字符可被 btoa 安全处理。
 * @param {string} plain 待加密的明文
 * @param {string} key 会话密钥
 * @returns {string} Base64 编码的密文
 */
function xorEncrypt(plain, key) {
  const out = []
  for (let i = 0; i < plain.length; i++) {
    out.push(String.fromCharCode(plain.charCodeAt(i) ^ key.charCodeAt(i % key.length)))
  }
  // btoa 处理 Unicode
  return btoa(unescape(encodeURIComponent(out.join(''))))
}

/**
 * Base64 解码并 XOR 解密（xorEncrypt 的逆操作）
 * @param {string} cipher Base64 编码的密文
 * @param {string} key 会话密钥
 * @returns {string} 解密后的明文；解码失败时返回空字符串
 */
function xorDecrypt(cipher, key) {
  try {
    const raw = decodeURIComponent(escape(atob(cipher)))
    const out = []
    for (let i = 0; i < raw.length; i++) {
      out.push(String.fromCharCode(raw.charCodeAt(i) ^ key.charCodeAt(i % key.length)))
    }
    return out.join('')
  } catch (e) {
    return ''
  }
}

/**
 * 加密存储门面：接口与 localStorage 兼容（setItem/getItem/removeItem）
 */
export const cryptoStorage = {
  /**
   * 加密后写入 localStorage
   * @param {string} key 存储键名
   * @param {*} value 待存储的值（会被转为字符串）
   * @returns {void} 加密异常时降级为明文写入，保证可用性
   */
  setItem(key, value) {
    try {
      const k = getKey()
      localStorage.setItem(key, xorEncrypt(String(value), k))
    } catch (e) {
      // 加密失败降级为明文
      localStorage.setItem(key, String(value))
    }
  },
  /**
   * 读取并解密 localStorage 中的值
   * @param {string} key 存储键名
   * @returns {string|null} 解密后的明文；不存在或解密失败均返回 null
   *   安全说明：解密失败（如密钥已变更）必须返回 null，绝不能回退返回密文原文，
   *   否则 Base64 密文会被上层当作有效 token 使用，导致所有请求 401 且难以排查。
   *   调用方应处理 null 返回值（等同于未登录，需重新登录获取新 token）。
   */
  getItem(key) {
    const raw = localStorage.getItem(key)
    if (!raw) return null
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
    localStorage.removeItem(key)
  }
}
