/**
 * uni API Promise 化适配器
 * 通过 uni.addInterceptor 拦截 API 返回值：当返回的是 Promise（thenable）时，
 * 将 UniApp 内部 [error, result] 形式的结果转换为标准 Promise（resolve/reject），
 * 使 uni.request 等异步 API 可直接使用 await / then 链式调用。
 */
uni.addInterceptor({
  returnValue (res) {
    // 非 Promise（thenable）返回值直接透传，不做处理
    if (!(!!res && (typeof res === "object" || typeof res === "function") && typeof res.then === "function")) {
      return res;
    }
    // 将 [err, data] 形式的结果规范化为标准 Promise：有错误则 reject，否则 resolve 数据
    return new Promise((resolve, reject) => {
      res.then((res) => {
        if (!res) return resolve(res) 
        return res[0] ? reject(res[0]) : resolve(res[1])
      });
    });
  },
});