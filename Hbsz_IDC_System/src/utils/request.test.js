/**
 * Axios 请求封装（utils/request.js）拦截器特征测试。
 *
 * 测试策略：通过 request.defaults.adapter 注入自定义适配器拦截真实 HTTP 出口，
 * 完整走一遍 axios 拦截器链（而非直接调用 handler 内部数组），断言对外可观察契约：
 *
 * 请求拦截器：
 *   - 登录态自动注入 Bearer Token；skipAuth 豁免（token 刷新自身）；
 *   - FormData 删除默认 Content-Type（让浏览器补 multipart boundary）；
 *   - skipCancel 豁免取消队列登记。
 *
 * 响应拦截器：
 *   - code=200 返回统一业务信封（envelope）而非 axios response；
 *   - 业务错误码 feedback.error + reject；2003 无权限 feedback.warning；
 *   - HTTP 404/403/500 状态映射覆盖后端自定义消息（当前实现特征）；
 *   - 非映射状态（如 400）保留后端 data.message；
 *   - 取消错误静默（无任何提示）；
 *   - 业务码 401：TokenRefreshCoordinator（真实实现）刷新成功 → 用新 token 重发原请求；
 *   - 业务码 401：刷新失败 → clearAuth + 提示 + 延迟 800ms 跳转登录页；
 *   - blob 响应携带 application/json 错误体时解析错误消息（M24 修复特征）。
 *
 * mock 边界：feedback / user store / router / cancelToken（spy addPending），
 * TokenRefreshCoordinator 与 axios 本体均为真实实现。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

const { feedbackMock, useUserStoreMock, storeState, routerPushMock } = vi.hoisted(() => {
  // 可变 store 桩：拦截器在请求生命周期内多次读取 token（刷新后变化），需保持引用稳定
  const storeState = { token: '', refresh: vi.fn(), clearAuth: vi.fn() }
  return {
    feedbackMock: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    useUserStoreMock: vi.fn(() => storeState),
    storeState,
    routerPushMock: vi.fn()
  }
})

vi.mock('@/utils/feedback', () => ({ feedback: feedbackMock }))
vi.mock('@/stores/user', () => ({ useUserStore: useUserStoreMock }))
// redirectToLogin 动态 import('@/router')，vi.mock 对动态导入同样生效
vi.mock('@/router', () => ({ default: { push: routerPushMock } }))
// cancelToken 保留真实实现，仅 spy addPending 供 skipCancel 断言
vi.mock('@/utils/cancelToken', async (importOriginal) => {
  const orig = await importOriginal()
  return { ...orig, addPending: vi.fn(orig.addPending) }
})

import request from '@/utils/request'
import { addPending } from '@/utils/cancelToken'

// ===== 自定义 axios 适配器：按队列依次返回预设响应/错误 =====
let adapterQueue = []
let adapterCalls = []
request.defaults.adapter = async (config) => {
  adapterCalls.push(config)
  const next = adapterQueue.length > 1 ? adapterQueue.shift() : adapterQueue[0]
  const result = typeof next === 'function' ? next(config) : next
  // 错误工厂（httpError）返回 Error 实例 → 必须 throw 才能进入 axios 错误拦截器
  if (result instanceof Error) {
    throw result
  }
  return result
}

/** 构造业务信封成功响应 */
function ok(data, extra = {}) {
  return (config) => ({
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
    data: { code: 200, message: 'success', data, ...extra }
  })
}

/** 构造 HTTP 层错误（axios 错误形态：config + response） */
function httpError(status, data = {}, message = 'Request failed') {
  const partial = (config) => {
    const err = new Error(message)
    err.config = config
    err.isAxiosError = true
    err.response = { status, statusText: String(status), headers: {}, config, data }
    return err
  }
  return partial
}

beforeEach(() => {
  vi.clearAllMocks()
  adapterQueue = []
  adapterCalls = []
  storeState.token = ''
  storeState.refresh.mockReset().mockResolvedValue(true)
  storeState.clearAuth.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('请求拦截器', () => {
  it('登录态：自动注入 Authorization Bearer 头', async () => {
    storeState.token = 'tok-a'
    adapterQueue = [ok({ list: [1, 2] })]

    const res = await request({ url: '/projects', method: 'get' })

    expect(adapterCalls[0].headers.Authorization).toBe('Bearer tok-a')
    expect(res.code).toBe(200)
    expect(res.data.list).toEqual([1, 2])
  })

  it('未登录：不注入 Authorization 头', async () => {
    storeState.token = ''
    adapterQueue = [ok(null)]

    await request({ url: '/auth/captcha', method: 'get' })

    expect(adapterCalls[0].headers.Authorization).toBeUndefined()
  })

  it('skipAuth（token 刷新自身）：豁免 Authorization 注入', async () => {
    storeState.token = 'expired-tok'
    adapterQueue = [ok({ token: 'new' })]

    await request({ url: '/auth/refresh', method: 'post', skipAuth: true })

    expect(adapterCalls[0].headers.Authorization).toBeUndefined()
  })

  it('FormData 上传：删除默认 Content-Type（浏览器补 multipart boundary）', async () => {
    adapterQueue = [ok({ id: 1 })]

    const fd = new FormData()
    fd.append('file', new Blob(['x']), 'a.png')
    await request({ url: '/files/upload', method: 'post', data: fd })

    const headers = adapterCalls[0].headers
    const contentType =
      typeof headers.getContentType === 'function'
        ? headers.getContentType()
        : headers['Content-Type'] || headers['content-type']
    // 特征：拦截器删除默认 application/json（否则浏览器不会自动补 multipart boundary，
    // 后端收不到边界导致 Content-Type 解析失败——见项目记忆的历史教训）。
    // adapter 层看到的最终值属环境噪音：浏览器 XHR 会补 multipart/form-data，
    // happy-dom/axios 派发期可能回填 application/x-www-form-urlencoded，
    // 唯一跨环境恒定不变量是"不再是 application/json"。
    expect(contentType || '').not.toContain('application/json')
  })

  it('skipCancel：不登记取消队列；普通请求默认登记', async () => {
    adapterQueue = [ok(null), ok(null)]

    await request({ url: '/x1', method: 'post', skipCancel: true })
    expect(addPending).not.toHaveBeenCalled()

    await request({ url: '/x2', method: 'post' })
    expect(addPending).toHaveBeenCalledTimes(1)
  })
})

describe('响应拦截器：业务码分流', () => {
  it('code=200：返回业务信封本身（调用方拿到的是 res 而非 axios response）', async () => {
    adapterQueue = [ok({ value: 42 }, { message: 'done' })]

    const res = await request({ url: '/t', method: 'get' })

    expect(res.code).toBe(200)
    expect(res.message).toBe('done')
    expect(res.data).toEqual({ value: 42 })
  })

  it('业务错误码：feedback.error(后端消息) 并 reject 同名 Error', async () => {
    adapterQueue = [
      (config) => ({ status: 200, headers: {}, config, data: { code: 500, message: '库存不足' } })
    ]

    await expect(request({ url: '/t', method: 'get' })).rejects.toThrow('库存不足')
    expect(feedbackMock.error).toHaveBeenCalledWith('库存不足')
  })

  it('code=2003：feedback.warning(无操作权限) 并 reject', async () => {
    adapterQueue = [
      (config) => ({ status: 200, headers: {}, config, data: { code: 2003, message: 'forbidden' } })
    ]

    await expect(request({ url: '/t', method: 'delete' })).rejects.toThrow('无操作权限')
    expect(feedbackMock.warning).toHaveBeenCalledWith('无操作权限')
  })
})

describe('响应拦截器：HTTP 错误映射', () => {
  it('HTTP 404：映射为「请求的资源不存在」（覆盖后端自定义消息）', async () => {
    adapterQueue = [httpError(404, { message: '自定义消息' })]

    await expect(request({ url: '/missing', method: 'get' })).rejects.toThrow()
    expect(feedbackMock.error).toHaveBeenCalledWith('请求的资源不存在')
  })

  it('HTTP 403：映射为「无权限访问」（状态映射优先于后端消息——当前实现特征）', async () => {
    adapterQueue = [httpError(403, { message: '该项目已归档' })]

    await expect(request({ url: '/t', method: 'get' })).rejects.toThrow()
    expect(feedbackMock.error).toHaveBeenCalledWith('无权限访问')
  })

  it('HTTP 400（无映射状态）：保留后端 data.message', async () => {
    adapterQueue = [httpError(400, { message: '参数校验失败：projectId 必填' })]

    await expect(request({ url: '/t', method: 'post' })).rejects.toThrow()
    expect(feedbackMock.error).toHaveBeenCalledWith('参数校验失败：projectId 必填')
  })

  it('取消错误：静默处理，不弹任何提示', async () => {
    const canceled = Object.assign(new Error('canceled'), {
      name: 'CanceledError',
      __CANCEL__: true
    })
    adapterQueue = [canceled]

    await expect(request({ url: '/t', method: 'get' })).rejects.toBe(canceled)
    expect(feedbackMock.error).not.toHaveBeenCalled()
    expect(feedbackMock.warning).not.toHaveBeenCalled()
  })
})

describe('响应拦截器：业务码 401 与 Token 刷新协调（真实 TokenRefreshCoordinator）', () => {
  it('刷新成功：自动用新 token 重发原请求，调用方拿到重试后的成功结果', async () => {
    storeState.refresh.mockImplementation(() => {
      storeState.token = 'tok-new' // 刷新成功后 store 持有新 token
      return Promise.resolve(true)
    })
    // 第一次：业务码 401；第二次（重试）：成功
    adapterQueue = [
      (config) => ({ status: 200, headers: {}, config, data: { code: 401, message: '令牌过期' } }),
      (config) => ({ status: 200, headers: {}, config, data: { code: 200, message: 'ok', data: { retried: true } } })
    ]

    const res = await request({ url: '/projects/list', method: 'get' })

    expect(res.code).toBe(200)
    expect(res.data.retried).toBe(true)
    expect(adapterCalls).toHaveLength(2)
    // 重试请求携带刷新后的新 token（由 retry 回调或请求拦截器注入）
    expect(adapterCalls[1].headers.Authorization).toBe('Bearer tok-new')
    expect(storeState.clearAuth).not.toHaveBeenCalled()
  })

  it('刷新失败：清理登录态 + 提示失效 + 延迟 800ms 跳转登录页', async () => {
    vi.useFakeTimers()
    storeState.refresh.mockResolvedValue(false)
    adapterQueue = [
      (config) => ({ status: 200, headers: {}, config, data: { code: 401, message: '令牌过期' } })
    ]

    const p = request({ url: '/projects/list', method: 'get' })
    await expect(p).rejects.toThrow('token refresh failed')

    expect(storeState.clearAuth).toHaveBeenCalled()
    expect(feedbackMock.error).toHaveBeenCalledWith('登录状态已失效，请重新登录')

    // 延迟 800ms 后经动态 import 跳转登录页（SPA 路由优先）
    await vi.advanceTimersByTimeAsync(900)
    expect(routerPushMock).toHaveBeenCalledWith('/login')
  })

  it('__retried 守卫（P0 修复特征）：重试后仍 401 不再刷新，直接跳登录', async () => {
    vi.useFakeTimers()
    storeState.refresh.mockImplementation(() => {
      storeState.token = 'tok-new'
      return Promise.resolve(true)
    })
    // 两次都返回业务码 401：刷新成功但后端仍拒绝（如 refreshToken 被吊销）
    const unauthorized = (config) => ({
      status: 200,
      headers: {},
      config,
      data: { code: 401, message: '令牌无效' }
    })
    adapterQueue = [unauthorized, unauthorized]

    const p = request({ url: '/projects/list', method: 'get' })
    await expect(p).rejects.toThrow('令牌无效')

    // 刷新仅触发一次（第二次进入分支时 __retried 已置位）
    expect(storeState.refresh).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(900)
    expect(routerPushMock).toHaveBeenCalledWith('/login')
  })
})

describe('响应拦截器：blob 错误体解析（M24 修复特征）', () => {
  it('blob 响应携带 JSON 错误体：解析后端消息并 reject（而非返回损坏文件）', async () => {
    const errBody = { code: 500, message: '导出失败：所选日期无数据' }
    adapterQueue = [
      (config) => ({
        status: 200,
        headers: {},
        config,
        data: new Blob([JSON.stringify(errBody)], { type: 'application/json' })
      })
    ]

    await expect(
      request({ url: '/daily-report/export', method: 'get', responseType: 'blob' })
    ).rejects.toThrow('导出失败：所选日期无数据')
    expect(feedbackMock.error).toHaveBeenCalledWith('导出失败：所选日期无数据')
  })

  it('正常文件流：blob 原样返回（拿到完整 axios response）', async () => {
    const fileBlob = new Blob(['fake-xlsx-bytes'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    adapterQueue = [
      (config) => ({ status: 200, headers: {}, config, data: fileBlob })
    ]

    const response = await request({ url: '/files/download', method: 'get', responseType: 'blob' })

    expect(response.data).toBe(fileBlob)
    expect(feedbackMock.error).not.toHaveBeenCalled()
  })
})
