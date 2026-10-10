/**
 * Token 刷新协调器（跨端共享）
 *
 * 背景：Web 与小程序的 request.js 各自实现了 401 刷新逻辑，核心算法一致：
 *      - 并发请求同时收到 401 时，只允许一个发起 refresh，其余挂起等待
 *      - refresh 成功后通知队列用新 token 重发
 *      - refresh 失败则全部拒绝并跳登录
 * 但两端在 token 存储、refresh 调用、重发请求、跳登录等细节上不同。
 * 本协调器只抽取"锁 + 队列"的纯算法，与运行时解耦，两端各自注入依赖即可。
 *
 * 用法：
 *   const coordinator = new TokenRefreshCoordinator({
 *     doRefresh: () => userStore.refresh(),        // 返回 Promise<boolean>
 *     retry: (config, newToken) => request(config), // 用新 token 重发
 *     onFail: () => redirectToLogin()               // 刷新失败回调
 *   })
 *
 *   // 在 401 处理处：
 *   return coordinator.handle(config)
 */

/**
 * @typedef {Object} CoordinatorOptions
 * @property {() => Promise<boolean>} doRefresh 执行 refresh，resolve(true) 表示成功
 * @property {(config: any, newToken: string) => Promise<any>} retry 用新 token 重发原请求
 * @property {() => void} onFail 刷新失败时的回调（如跳登录）
 * @property {() => string} getToken 获取当前最新 token
 */

export class TokenRefreshCoordinator {
  /**
   * @param {CoordinatorOptions} options
   */
  constructor(options) {
    this.options = options
    this.isRefreshing = false
    /** @type {Array<(newToken: string|null) => void>} */
    this.pendingQueue = []
  }

  /**
   * 处理 401 请求：必要时发起刷新，刷新成功后重发
   * @param {any} config 原始请求配置，将传给 retry
   * @returns {Promise<any>} 重发后的响应 Promise
   */
  handle(config) {
    // 已在刷新中：挂起到队列，等刷新结果
    if (this.isRefreshing) {
      return new Promise((resolve, reject) => {
        this.pendingQueue.push((newToken) => {
          if (!newToken) {
            reject(new Error('token refresh failed'))
            return
          }
          this.options.retry(config, newToken).then(resolve).catch(reject)
        })
      })
    }

    // 发起刷新
    this.isRefreshing = true
    return this.options
      .doRefresh()
      .then((ok) => {
        this.isRefreshing = false
        if (!ok) {
          this.notifyQueue(null)
          this.options.onFail()
          return Promise.reject(new Error('token refresh failed'))
        }
        const newToken = this.options.getToken()
        this.notifyQueue(newToken)
        return this.options.retry(config, newToken)
      })
      .catch((err) => {
        this.isRefreshing = false
        this.notifyQueue(null)
        this.options.onFail()
        return Promise.reject(err)
      })
  }

  /** 通知队列中的所有请求 */
  notifyQueue(newToken) {
    this.pendingQueue.forEach((cb) => cb(newToken))
    this.pendingQueue = []
  }
}
