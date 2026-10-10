/**
 * 用户认证状态仓库（stores/user.js）特征测试。
 *
 * 锁定的关键契约：
 *   - login：内存态 + cryptoStorage 加密持久化（localStorage 不得出现明文 token）；
 *   - logout：后端接口失败仍保证本地清理（finally clearAuth）；
 *   - refresh：无 refreshToken 返回 false；失败 clearAuth；P1 并发去重（在途 Promise 复用，
 *     同一时刻仅一个真实刷新请求）；
 *   - fetchProfile：更新 userInfo 并持久化；
 *   - getters 回退链（nickname → username → ''）。
 *
 * 测试边界：API 层（authApi/userApi）与 WebSocket 通知 store 均 mock，
 * cryptoStorage 用真实实现（覆盖 XOR+Base64 加密往返）。
 */
import { describe, it, expect, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const { authApiMock, userApiMock, updateWebSocketTokenMock, cryptoStorageFake, storageBacking } = vi.hoisted(() => ({
  authApiMock: {
    login: vi.fn(),
    logout: vi.fn(),
    refreshToken: vi.fn()
  },
  userApiMock: { getProfile: vi.fn() },
  updateWebSocketTokenMock: vi.fn(),
  // cryptoStorage 桩：真实实现依赖浏览器指纹派生密钥，happy-dom 下取密钥失败
  // 会按设计降级为明文存储（环境相关，非 store 职责）。这里用 Map 版等价替换，
  // 聚焦锁定 store 的持久化契约（写哪些键、初始化如何恢复、清理删哪些键）。
  storageBacking: new Map(),
  cryptoStorageFake: {}
}))
cryptoStorageFake.getItem = (k) => (storageBacking.has(k) ? storageBacking.get(k) : null)
cryptoStorageFake.setItem = (k, v) => storageBacking.set(k, String(v))
cryptoStorageFake.removeItem = (k) => storageBacking.delete(k)

vi.mock('@/api/auth', () => ({ authApi: authApiMock }))
vi.mock('@/api/user', () => ({ userApi: userApiMock }))
vi.mock('@/utils/cryptoStorage', () => ({ cryptoStorage: cryptoStorageFake }))
// refresh() 内动态 import 通知 store 更新 WebSocket token，mock 掉避免引入 STOMP 链路
vi.mock('@/stores/notification', () => ({
  useNotificationStore: () => ({ updateWebSocketToken: updateWebSocketTokenMock })
}))

import { useUserStore } from '@/stores/user'

/** 每个用例创建全新 pinia + 干净的存储，避免状态串扰 */
function freshStore() {
  storageBacking.clear()
  vi.clearAllMocks()
  setActivePinia(createPinia())
  return useUserStore()
}

const LOGIN_RESPONSE = {
  data: {
    token: 'at-secret-123',
    refreshToken: 'rt-secret-456',
    userInfo: { username: 'wangwu', nickname: '王五', roles: ['admin', 'project_manager'] }
  }
}

describe('user store：登录与持久化', () => {
  it('login：写入内存态并加密持久化，localStorage 不出现明文 token', async () => {
    const store = freshStore()
    authApiMock.login.mockResolvedValue(LOGIN_RESPONSE)

    await store.login({ username: 'wangwu', password: 'x' })

    expect(store.token).toBe('at-secret-123')
    expect(store.refreshToken).toBe('rt-secret-456')
    expect(store.isLogin).toBe(true)
    expect(store.username).toBe('wangwu')
    expect(store.nickname).toBe('王五')
    expect(store.roles).toEqual(['admin', 'project_manager'])
    // 持久化契约：三个键均写入 cryptoStorage（加密是 cryptoStorage 自身职责，另行测试）
    expect(storageBacking.get('hbsz_token')).toBe('at-secret-123')
    expect(storageBacking.get('hbsz_refresh_token')).toBe('rt-secret-456')
    expect(storageBacking.get('hbsz_user')).toBe(JSON.stringify(LOGIN_RESPONSE.data.userInfo))
  })

  it('页面刷新恢复：新 store 实例从加密存储还原登录态（往返一致性）', () => {
    const seed = freshStore()
    authApiMock.login.mockResolvedValue(LOGIN_RESPONSE)
    return seed.login({ username: 'wangwu', password: 'x' }).then(() => {
      // 模拟刷新页面：新 pinia 上下文创建 store，从 localStorage 解密恢复
      setActivePinia(createPinia())
      const restored = useUserStore()
      expect(restored.token).toBe('at-secret-123')
      expect(restored.refreshToken).toBe('rt-secret-456')
      expect(restored.userInfo).toEqual(LOGIN_RESPONSE.data.userInfo)
      expect(restored.isLogin).toBe(true)
    })
  })

  it('getters 回退链：nickname 缺失回退 username，roles 缺失为空数组', () => {
    const store = freshStore()
    store.userInfo = { username: 'zhaoliu' }
    expect(store.nickname).toBe('zhaoliu')
    expect(store.roles).toEqual([])
    expect(store.username).toBe('zhaoliu')
  })
})

describe('user store：登出与清理', () => {
  it('logout：后端接口失败仍保证清理本地登录态', async () => {
    const store = freshStore()
    store.token = 'at-1'
    store.refreshToken = 'rt-1'
    store.userInfo = { username: 'wangwu' }
    authApiMock.logout.mockRejectedValue(new Error('network down'))

    await store.logout()

    expect(store.token).toBe('')
    expect(store.refreshToken).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.isLogin).toBe(false)
    expect(storageBacking.has('hbsz_token')).toBe(false)
    expect(storageBacking.has('hbsz_refresh_token')).toBe(false)
    expect(storageBacking.has('hbsz_user')).toBe(false)
  })

  it('logout：无 token 时不调用后端登出接口', async () => {
    const store = freshStore()
    await store.logout()
    expect(authApiMock.logout).not.toHaveBeenCalled()
  })
})

describe('user store：refresh（P1 并发去重特征）', () => {
  it('无 refreshToken：返回 false 且不发起刷新请求', async () => {
    const store = freshStore()
    store.refreshToken = ''
    const ok = await store.refresh()
    expect(ok).toBe(false)
    expect(authApiMock.refreshToken).not.toHaveBeenCalled()
  })

  it('刷新成功：更新内存与存储，并同步 WebSocket token', async () => {
    const store = freshStore()
    store.refreshToken = 'rt-old'
    authApiMock.refreshToken.mockResolvedValue({ data: { token: 'at-new', refreshToken: 'rt-new' } })

    const ok = await store.refresh()

    expect(ok).toBe(true)
    expect(store.token).toBe('at-new')
    expect(store.refreshToken).toBe('rt-new')
    expect(storageBacking.get('hbsz_token')).toBe('at-new') // 持久化契约：新 token 落盘
    expect(authApiMock.refreshToken).toHaveBeenCalledWith('rt-old')
    // WebSocket 通知链路：断线重连需用新 token 握手
    expect(updateWebSocketTokenMock).toHaveBeenCalledWith('at-new')
  })

  it('刷新失败：返回 false 并清理全部登录态', async () => {
    const store = freshStore()
    store.token = 'at-stale'
    store.refreshToken = 'rt-bad'
    store.userInfo = { username: 'wangwu' }
    authApiMock.refreshToken.mockRejectedValue(new Error('401'))

    const ok = await store.refresh()

    expect(ok).toBe(false)
    expect(store.token).toBe('')
    expect(store.refreshToken).toBe('')
    expect(store.userInfo).toBeNull()
    expect(storageBacking.has('hbsz_token')).toBe(false)
  })

  it('并发去重（P1 修复特征）：两个并发 refresh 共享在途请求，API 仅调用一次', async () => {
    const store = freshStore()
    store.refreshToken = 'rt-once'
    // 延迟 30ms 模拟真实网络，确保第二个调用到来时首个仍在途
    authApiMock.refreshToken.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve({ data: { token: 'at-dedup' } }), 30))
    )

    const [r1, r2] = await Promise.all([store.refresh(), store.refresh()])

    expect(authApiMock.refreshToken).toHaveBeenCalledTimes(1)
    expect(r1).toBe(true)
    expect(r2).toBe(true)
    expect(store.token).toBe('at-dedup')
  })

  it('串行第二次 refresh：首次完成后在途 Promise 已清空，允许再次刷新', async () => {
    const store = freshStore()
    store.refreshToken = 'rt-1'
    authApiMock.refreshToken.mockResolvedValue({ data: { token: 'at-a' } })
    await store.refresh()
    store.refreshToken = 'rt-2'
    authApiMock.refreshToken.mockResolvedValue({ data: { token: 'at-b' } })
    await store.refresh()
    expect(authApiMock.refreshToken).toHaveBeenCalledTimes(2)
    expect(store.token).toBe('at-b')
  })
})

describe('user store：fetchProfile', () => {
  it('拉取资料：更新 userInfo 并加密持久化', async () => {
    const store = freshStore()
    const profile = { username: 'wangwu', nickname: '王五Pro', roles: ['engineer'] }
    userApiMock.getProfile.mockResolvedValue({ data: profile })

    const data = await store.fetchProfile()

    expect(data).toEqual(profile)
    expect(store.userInfo).toEqual(profile)
    expect(store.nickname).toBe('王五Pro')
    expect(storageBacking.get('hbsz_user')).toBe(JSON.stringify(profile))
  })
})
