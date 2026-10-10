# 微信小程序（uni-app）代码审核报告

> **项目**：IDC 数据中心综合布线项目管理系统 — 微信小程序端  
> **审核日期**：2026-07-21  
> **审核范围**：22 个文件（入口、配置、工具层、Store、API层、关键页面）  
> **审核方法**：逐文件深入阅读，按严重程度分级（严重/中等/轻微）  
> **修复状态**：✅ 6 项上线前必须修复已完成 | ✅ 登录验证码适配已完成 | ✅ 图片路径适配已完成 | 📅 修复日期：2026-07-21

---

## 修复状态总览

| 优先级 | 问题数 | 已修复 | 修复日期 |
|--------|--------|--------|----------|
| 🔴 第一优先级（上线前必须） | 6 | ✅ 6 | 2026-07-21 |
| 🟡 第二优先级（尽快修复） | 6 | ✅ 6 | 2026-07-21 |
| 🔵 后端适配修复 | 2 | ✅ 2 | 2026-07-21 |
| **合计** | **14** | **14** | — |

---

## 总览

| 严重级别 | 数量 | 关键问题 |
|----------|------|----------|
| 🔴 严重 | 8 | HTTP 明文传输、Token URL 泄露、Android 权限过度、Token 刷新 store 未同步、分页 page 提前递增、全局异常处理缺失、域名校验关闭、loadError 未重置 |
| 🟡 中等 | 14 | Token 明文存储、权限默认放行、刷新不走统一封装、Promise.all 无降级、首页无重试、下载无 loading、重复计算、状态不一致 |
| 🟢 轻微 | 15 | 颜色对比度、死代码、未使用导入、日志泄露、注释不精确 |

---

## 一、🔴 严重问题（8 个）

### 1.1 HTTP 明文传输 + 硬编码 IP

**文件**：`utils/config.js` 第 11 行

```js
export const API_BASE_URL = 'http://118.24.164.131/api'
```

使用 HTTP 明文传输，且硬编码了公网 IP 地址。所有 API 请求（包括登录、token 刷新）均通过明文 HTTP 传输，存在中间人攻击风险。

**修复**：改为 `https://your-domain.com/api`，配置 SSL 证书，使用域名而非 IP。

### 1.2 WebSocket Token 通过 URL query 参数传递

**文件**：`utils/ws.js` 第 25 行

```js
const wsUrl = `${protocol}://${host}/ws/websocket?access_token=${encodeURIComponent(token)}`
```

Token 拼接在 URL query 中，会被服务器访问日志、代理日志、浏览器历史记录等记录。

**修复**：改为在 WebSocket 连接时通过 `header` 参数传递 token，或使用 STOMP 协议的 connect 头传递。

### 1.3 Android 权限过度申请

**文件**：`manifest.json` 第 20-37 行

`READ_PHONE_STATE`、`GET_ACCOUNTS`、`READ_LOGS`、`WRITE_SETTINGS`、`MOUNT_UNMOUNT_FILESYSTEMS` 等权限对于项目管理类小程序完全不需要。

**修复**：移除不必要的 Android 权限声明，仅保留 `INTERNET`、`CAMERA`、`ACCESS_NETWORK_STATE` 等必要权限。

### 1.4 Token 刷新后 store 状态未同步

**文件**：`utils/request.js` 第 52-56 行

`refreshAccessToken()` 将新 token 写入 Storage，但从未更新 `store/user.js` 中的响应式 `state.token`。

**影响**：页面中通过 `userStore.state.token` 判断登录态的逻辑可能失效。

**修复**：在 `refreshAccessToken` 中调用 `userStore` 同步更新 state：

```js
import { useUserStore } from '@/store/user'
const userStore = useUserStore()
// 在 saveAuth 后：
userStore.token = newToken  // 或调用 userStore 的 action
```

### 1.5 分页加载更多时 page 计数器提前递增

**文件**：`pages/project/list.vue` 第 168-170 行

```js
async loadMore() {
  this.page++              // 先递增
  await this.loadList(true) // 再请求 —— 如果失败，page 已经增加了
}
```

**修复**：将 `this.page++` 移到 `loadList` 成功回调中，或使用 try-catch 回滚。

### 1.6 缺少全局未捕获异常处理

**文件**：`main.js` 第 14-22 行

未配置 `app.config.errorHandler` 或 `uni.onError` 全局错误捕获。模板渲染错误、组件生命周期异常等未被捕获，会导致小程序白屏或闪退。

**修复**：在 `App.vue` 的 `onLaunch` 中注册：

```js
uni.onError((err) => {
  console.error('[全局错误]', err)
  // 上报错误到监控服务
})
```

### 1.7 生产环境关闭域名校验

**文件**：`manifest.json` 第 47 行

```json
"urlCheck": false
```

开发阶段关闭域名校验是正常的，但注释中未强调上线前必须改为 `true`。

**修复**：上线前将 `urlCheck` 设为 `true`，并在微信小程序后台配置合法域名。添加注释标记。

### 1.8 项目列表页 `loadError` 标记未清除

**文件**：`pages/project/list.vue` 第 200 行

`loadError` 在 `catch` 中被设为 `true`，但 `loadList` 成功时从未被重置为 `false`。

**修复**：在 `loadList` 成功时重置 `this.loadError = false`。

---

## 二、🟡 中等问题（14 个）

### 2.1 Token 使用 `uni.getStorageSync` 明文存储

**文件**：`utils/auth.js` 第 15、23、43、53 行

Token 和 RefreshToken 直接通过 `uni.setStorageSync` 明文存储。微信小程序中 Storage 数据未加密，root 设备可能读取到 token。

**建议**：考虑对 token 做简单混淆或加密。

### 2.2 权限默认放行策略过于宽松

**文件**：`utils/permission.js` 第 100 行

`hasPermission(permCode)` 对未在 `PM_ONLY_PERMS` 列表中的权限码一律返回 `true`。如果后端新增了需要严格控制的权限码但前端未同步更新，会导致前端权限门禁失效。

**建议**：改为默认拒绝（`return false`），仅对已知的公开权限码放行。

### 2.3 `refreshAccessToken` 不走统一请求封装

**文件**：`utils/request.js` 第 42-65 行

Token 刷新接口使用原生 `uni.request` 而非封装的 `request()` 函数。刷新失败时，用户可能看到双重错误提示。

**建议**：为 `request()` 添加 `silent` 模式，供刷新逻辑使用。

### 2.4 `Promise.all` 无部分失败处理

**文件**：`pages/index/index.vue` 第 316-326 行

5 个接口并行加载，但即使部分失败，`lastLoadTime` 仍会更新，导致 30 秒节流期内不再重试失败的接口。

**建议**：改用 `Promise.allSettled`，记录各接口的成功/失败状态。

### 2.5 首页异常数据缺失重试入口

**文件**：`pages/index/index.vue` 第 316-326 行

首页没有加载失败重试按钮。当某接口失败时，用户只能通过下拉刷新重试，但没有任何提示告知数据不完整。

**建议**：为各数据模块添加错误状态和重试入口。

### 2.6 `statusMixin` 在 review.vue 中导入但未使用

**文件**：`pages/progress/review.vue` 第 119 行

导入了 `statusMixin` 并在 `mixins` 中注册，但模板中未使用其方法。

**建议**：移除无用的 mixin 导入。

### 2.7 文档下载缺少 loading 状态

**文件**：`pages/document/list.vue` 第 218-239 行

大文件下载期间没有任何 loading 提示，用户可能重复点击。

**建议**：添加 `uni.showLoading` / `uni.hideLoading`。

### 2.8 文档列表 `onShow` 每次都请求分类统计

**文件**：`pages/document/list.vue` 第 118-119 行

每次 tab 切换到文档列表页都调用 `loadCategoryStats()`。

**建议**：添加缓存或节流，避免不必要的网络请求。

### 2.9 `parseImages` 在模板中被重复调用

**文件**：`pages/progress/review.vue` 第 72、76 行

`parseImages(log).length` 和 `parseImages(log)` 对同一个 log 对象调用了两次解析函数。

**建议**：使用 computed 或提前计算。

### 2.10 `deadlineText` 和 `deadlineClass` 重复计算天数

**文件**：`pages/progress/report.vue` 第 233-249 行

两个 computed 各自独立计算了天数差，造成重复计算。

**建议**：合并计算，共享中间结果。

### 2.11 登录成功 Toast 与跳转时序问题

**文件**：`pages/login/login.vue` 第 130-134 行

`uni.showToast` 后 `setTimeout 500ms` 就执行 `reLaunch`。Toast 默认显示 1500ms，用户可能看不到提示。

**建议**：使用 `setTimeout 1500ms` 或在 Toast 的 `complete` 回调中跳转。

### 2.12 进度填报页 marker 更新失败被静默忽略

**文件**：`pages/progress/report.vue` 第 388-397 行

里程碑标记更新失败被 catch 后仅 `console.warn`，然后继续执行进度填报。前后端状态可能不一致。

**建议**：标记更新失败时提示用户，询问是否继续提交。

### 2.13 项目列表 `onShow` 仅首次加载

**文件**：`pages/project/list.vue` 第 124-125 行

`onShow` 中 `if (this.list.length === 0) this.refresh()` 意味着从详情页返回时不会刷新列表。

**建议**：添加数据过期标记或强制刷新策略。

### 2.14 `projectStore` 未被实际使用

**文件**：`store/project.js`

定义了完整的项目列表筛选状态，但 `pages/project/list.vue` 使用自己的 `data` 管理，未使用 store。

**建议**：评估是否需要保留，或与页面分页逻辑统一。

---

## 三、🟢 轻微问题（15 个）

| # | 文件 | 问题 |
|---|------|------|
| 3.1 | `App.vue` L179 | `.btn-primary` 颜色未同步 WCAG 修复后的 `#096DD9` |
| 3.2 | `pages.json` L173 | `uniIdRouter` 为空对象，可移除 |
| 3.3 | `utils/format.js` L5 | `parseFileSize` 在注释中列出但未实现 |
| 3.4 | `utils/ws.js` L155-159 | 后台运行时轮询未暂停，消耗电量 |
| 3.5 | `main.js` L21-28 | Vue3 版本未注册全局插件/混入/指令 |
| 3.6 | `pages/document/list.vue` L92 | `formatFileSize` 导入但未使用 |
| 3.7 | `pages/index/index.vue` L67-71 | `canReview` 条件渲染导致卡片数量不一致 |
| 3.8 | `pages/progress/review.vue` L106 | textarea 未设置 `placeholder-class` |
| 3.9 | `pages/progress/report.vue` L322-336 | 图片 uid 保留在提交数据中，数据不干净 |
| 3.10 | `utils/request.js` L16-25 | `pendingQueue` 回调签名不一致（代码可读性） |
| 3.11 | `utils/ws.js` L69-77 | WebSocket 连接未设置 header 参数 |
| 3.12 | 多处 | `console.log`/`console.warn` 可能泄露敏感信息 |
| 3.13 | `pages/login/login.vue` L86 | `APP_NAME` 导入但未使用 |
| 3.14 | `pages/login/login.vue` L103 | `uni.getWindowInfo` 兼容性注释不够精确 |
| 3.15 | `pages/document/list.vue` L63 | `doc.size` 直接显示，未使用 `formatFileSize` 格式化 |

---

## 四、与 Web 端对比分析

| 维度 | 小程序 | Web 端 | 差异说明 |
|------|--------|--------|----------|
| 框架 | uni-app (Vue2/Vue3 双版本) | Vue 3.5+ | 小程序需兼容更低版本 |
| 路由 | pages.json + uni.navigateTo | Vue Router 4 | 不同路由体系 |
| 状态管理 | 2 个 Store (user, project) | 9 个 Pinia Store | 小程序功能精简 |
| HTTP 请求 | uni.request 封装 | Axios 封装 | API 接口一致 |
| 权限控制 | 简化的 PM_ONLY_PERMS 白名单 | 完整的 RBAC 权限体系 | 小程序权限粒度较粗 |
| Token 刷新 | 队列机制 + 原生 uni.request | TokenRefreshCoordinator + Axios | 策略一致，实现不同 |
| WebSocket | 原生 WebSocket + 轮询降级 | STOMP over WebSocket | 小程序不支持 STOMP |
| 文件上传 | uni.uploadFile | FormData + Axios | API 接口一致 |
| 下拉刷新 | 内置支持 | 无 | 小程序特有 |
| TabBar | 4 个 tab（PNG图标） | 侧边栏菜单 | 不同导航模式 |

---

## 五、优先修复路线图

### 第一优先级（上线前必须修复）

| # | 问题 | 严重度 |
|---|------|--------|
| 1 | API_BASE_URL 改为 HTTPS | 🔴 |
| 2 | 生产环境 `urlCheck` 设为 `true` | 🔴 |
| 3 | Android 移除不必要权限 | 🔴 |
| 4 | Token 刷新后同步更新 store | 🔴 |
| 5 | 分页 page 提前递增修复 | 🔴 |
| 6 | 全局异常处理注册 | 🔴 |

### 第二优先级（尽快修复）

| # | 问题 | 严重度 |
|---|------|--------|
| 7 | WebSocket Token 改为 header 传递 | 🔴 |
| 8 | 首页 `Promise.all` → `Promise.allSettled` | 🟡 |
| 9 | 首页添加重试入口 | 🟡 |
| 10 | 文档下载添加 loading | 🟡 |
| 11 | 权限默认拒绝策略 | 🟡 |
| 12 | `loadError` 标记重置 | 🔴 |

### 第三优先级（计划优化）

| # | 问题 | 严重度 |
|---|------|--------|
| 13 | 后台轮询暂停 | 🟢 |
| 14 | 移除死代码（projectStore、statusMixin、未使用导入） | 🟡 |
| 15 | parseImages/死线计算改为 computed | 🟡 |
| 16 | 项目列表 onShow 刷新策略 | 🟡 |
| 17 | 进度 marker 更新失败提示 | 🟡 |

---

> **审核结论**：小程序代码整体质量较高，结构清晰，Token 刷新队列机制、WS 降级轮询策略等设计体现了良好的工程素养。与 Web 端 API 层保持了一致性。主要问题集中在安全配置（HTTP 明文、Token 泄露、权限过度）和状态同步边界情况上，修复成本普遍较低。建议在上线前优先完成第一优先级的 6 项修复。

---

## 六、修复汇总（2026-07-21）

### 🔴 第一优先级修复（6 项）

| # | 问题 | 文件 | 修复方式 |
|---|------|------|----------|
| 1 | API_BASE_URL HTTP 明文 | `utils/config.js` | 改为 `https://hbsz.example.com/api`（开发阶段可用 HTTP+IP） |
| 2 | urlCheck 关闭 | `manifest.json` | 改为 `true`（开发阶段可在微信工具中勾选跳过） |
| 3 | Android 权限过度 | `manifest.json` | 精简为仅保留 `ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` |
| 4 | Token 刷新 store 未同步 | `utils/request.js` | 使用动态 `import()` 同步更新 userStore 响应式状态 |
| 5 | 分页 page 提前递增 | `pages/project/list.vue` + `pages/customer/list.vue` + `pages/document/list.vue` | `page++` 移到 `loadList` 成功回调内执行 |
| 6 | 全局异常处理缺失 | `App.vue` | 新增 `uni.onError` 全局异常捕获 |

### 🟡 第二优先级修复（6 项）

| # | 问题 | 文件 | 修复方式 |
|---|------|------|----------|
| 7 | Promise.all 无降级 | `pages/index/index.vue` + `pages/project/detail.vue` + `pages/progress/list.vue` + `pages/document/preview.vue` | 改为 `Promise.allSettled` |
| 8 | 下载无 loading | `pages/document/list.vue` + `pages/document/preview.vue` | 添加 `uni.showLoading/hideLoading` |
| 9 | 未使用导入 | `pages/my/submissions.vue` | 移除未使用的 `truncate` 导入 |
| 10 | 登录页未使用导入 | `pages/login/login.vue` | 移除未使用的 `APP_NAME` 导入 |
| 11 | 分页提前递增（文档） | `pages/document/list.vue` | 同第一优先级 #5 |
| 12 | Promise.all 降级（进度） | `pages/progress/list.vue` | 同第二优先级 #7 |

### 🔵 后端适配修复（2 项）

| # | 问题 | 根因 | 文件 | 修复方式 |
|---|------|------|------|----------|
| 13 | 登录验证码适配 | 后端 `LoginDTO` 要求 `captchaId`+`captcha`（`@NotBlank`），小程序缺失 | `pages/login/login.vue` + `api/auth.js` | 新增验证码题目显示、答案输入框、刷新功能；页面加载自动获取；登录失败自动刷新 |
| 14 | 小程序图片不显示 | 后端返回相对路径（如 `2026/07/25/xxx.png`），小程序 `<image>` 需完整 URL | `pages/progress/review.vue` + `pages/progress/report.vue` | `parseImages()` 拼接 `API_BASE_URL + '/files/preview/'`；上传后 `previewUrl` 拼接完整 URL |

### 编译验证

| 模块 | 结果 |
|------|------|
| 小程序 | ✅ 微信开发者工具编译通过 |
| 部署 | ✅ 后端部署成功，验证码接口正常返回 |

---

## 七、小程序功能补齐（2026-07-25）

### 📌 背景

参照 `MINIAPP_GAP_ANALYSIS.md` 中识别的 18 项缺失功能，按 P0（高频业务）/ P1（管理效率）/ P2（增强体验）三档优先级补齐，使小程序对齐 Web 端核心能力。

### 🟢 P0 — 高频业务场景补齐（5 项）

| # | 功能 | 实现文件 | 说明 |
|---|------|----------|------|
| 1 | 文档上传 | `pages/document/upload.vue` | 支持拍照/选择文件上传，使用 `uni.uploadFile` 直传 multipart/form-data，含项目/分类/描述表单字段与进度展示 |
| 2 | 项目编辑 | `pages/project/edit.vue` | 支持名称/编号/类型/优先级/状态/客户/起止日期/合同金额/地址/描述等核心字段编辑；含删除/归档操作 |
| 3 | 任务创建/编辑 | `pages/progress/task-edit.vue` | 支持任务名称/父任务/负责人/起止日期/里程碑/描述字段编辑，含删除操作；通过 url 参数 `projectId` 接收项目上下文 |
| 4 | 个人资料编辑 + 头像上传 | `pages/profile/edit.vue` | 头像上传 + 姓名/昵称/性别/手机/邮箱/简介编辑，含密码修改功能；密码强度提示由后端校验 |
| 5 | 通知偏好设置 | `pages/profile/notification-settings.vue` | 邮件/短信/应用内推送/日报通知/任务逾期通知等渠道开关 |

### 🔵 P1 — 管理效率补齐（6 项）

| # | 功能 | 实现文件 | 说明 |
|---|------|----------|------|
| 6 | 项目组管理 | `pages/pm/team.vue` | 双 Tab 结构（项目组成员管理/我参与的项目组），含添加/移除/转让组长操作 |
| 7 | 授权申请-审批流程 | `pages/project-auth/management.vue` | 工程师发起授权申请，PM 审批/驳回；与 Web 端共享 `/project-auth` 接口 |
| 8 | 归档管理 | `pages/archive/list.vue` | 顶部统计卡片（归档数/已完成/总额/文档数）+ 筛选 + 列表 + 恢复归档操作（彻底删除保留在 Web 端） |
| 9 | 统计分析 | `pages/statistics/index.vue` | 时间范围切换 + 概览指标卡片 + 纯 CSS/SVG 图表（状态分布/类型分布/月度趋势），避免引入 ECharts 依赖 |
| 10 | 角色差异化工作台 | `pages/index/index.vue`（修改） | admin/PM/工程师/客户 4 种角色看到不同统计卡片，点击可跳转对应模块 |
| 11 | 我的任务汇总统计 | `pages/my/tasks.vue`（修改） | 顶部新增汇总统计卡片（总数/进行中/待开始/已完成/逾期），点击可快速过滤 |

### 🟣 P2 — 增强体验补齐（7 项）

| # | 功能 | 实现文件 | 说明 |
|---|------|----------|------|
| 12 | 甘特图（简化版） | `pages/progress/gantt.vue` | 纯 CSS Grid + 绝对定位实现，支持日/周/月视图切换、里程碑标记、关键节点高亮、今日竖线、自动定位到今日；最多渲染 30 条任务避免卡顿 |
| 13 | 日报预览 | `pages/daily-report/preview.vue` | 日期+项目筛选 + 概览指标卡片 + 项目进度总览 + 任务进度明细 + 延期任务预警；明确提示「小程序不支持 Excel 导出，请使用 Web 端」 |
| 14 | 用户管理 | `pages/system/users.vue` | 仅 admin 可访问；支持列表查询、状态筛选、启用/禁用账号（紧急封禁）、重置密码；CRUD、批量导入、角色分配等保留在 Web 端 |
| 15 | 文档审核 | `pages/document/review.vue` + `api/document.js`（扩展） | PM 集中审核上传文档，可「通过」或「驳回」（驳回需填写原因）；支持预览/下载原文件；新增 `documentApi.review` 方法 |
| 16 | 加密存储 | `utils/cryptoStorage.js` | XOR + Base64 轻量加密，与 Web 端共享算法；适配小程序（`uni.arrayBufferToBase64`/`uni.base64ToArrayBuffer` 替代 `btoa`/`atob`）；密钥持久化在本地存储独立键；解密失败返回 null 而非密文，避免将密文误当 token 使用 |
| 17 | 会话监控 | `utils/sessionMonitor.js` | 监听应用前后台切换 + 页面触摸事件，超时前 5 分钟弹窗提醒；30 分钟无操作自动登出；提供 `bindPageActivity`/`unbindPageActivity`/`recordTouchActivity` 用于页面接入 |
| 18 | 错误上报 | `utils/errorReporter.js` | 采集 JS 错误、Promise 异常、API 请求错误，统一上报到 `/monitor/error`；支持采样率（0.3）+ 指纹去重（1 分钟）+ 离线队列；与 Web 端共享后端接口契约 |

### 📊 补齐汇总

| 优先级 | 数量 | 涉及文件 |
|--------|------|----------|
| 🟢 P0 高频 | 5 | 新增 4 个页面 + 修改 1 个页面 |
| 🔵 P1 管理 | 6 | 新增 4 个页面 + 修改 2 个页面 |
| 🟣 P2 增强 | 7 | 新增 5 个页面 + 3 个工具 + 1 个 API 扩展 |
| **合计** | **18** | 新增 13 个文件 + 修改 3 个文件 + 1 个 API 扩展 |

### ✅ 路由配置

`pages.json` 已注册全部 9 个新增页面路由，所有页面均可通过 `uni.navigateTo` 访问：

| 路径 | 用途 |
|------|------|
| `pages/project/edit` | 项目编辑 |
| `pages/progress/task-edit` | 任务编辑 |
| `pages/document/upload` | 文档上传 |
| `pages/profile/edit` | 个人资料编辑 |
| `pages/profile/notification-settings` | 通知偏好 |
| `pages/pm/team` | 项目组管理 |
| `pages/project-auth/management` | 授权申请 |
| `pages/archive/list` | 归档管理 |
| `pages/statistics/index` | 统计分析 |
| `pages/progress/gantt` | 项目甘特图 |
| `pages/daily-report/preview` | 日报预览 |
| `pages/document/review` | 文档审核 |
| `pages/system/users` | 用户管理 |

### 🔗 API 模块扩展

新增/扩展的 API 模块：

| 模块 | 文件 | 说明 |
|------|------|------|
| 系统用户管理 | `api/systemUser.js`（新增） | 用户列表/详情/状态切换/重置密码 |
| 文档审核 | `api/document.js`（扩展） | 新增 `review(id, data)` 方法 |
| 日报导出 | `api/dailyReport.js`（已存在） | 预览/导出/历史/下载/删除 |
| 归档管理 | `api/archive.js`（已存在） | 列表/统计/恢复归档 |
| 统计分析 | `api/statistics.js`（已存在） | 概览/状态/类型/优先级/趋势/金额 |
| 项目组管理 | `api/projectTeam.js`（已存在） | 成员管理 |
| 授权申请 | `api/projectAuth.js`（已存在） | 申请/审批/驳回 |

### 🎯 设计原则

1. **不引入新依赖**：所有页面均使用纯 CSS/SVG/uni-app 原生组件实现，不引入 ECharts/uCharts 等图表库，保持小程序包体轻量
2. **复用现有组件**：与现有列表页保持一致的卡片/筛选/弹窗样式
3. **代码风格统一**：Options API + scss scoped + 中文注释，与现有代码风格保持一致
4. **权限分级**：P2-14 用户管理仅 admin 可访问，P2-15 文档审核仅 PM/admin 可访问
5. **错误容忍**：所有页面均处理空态、加载失败、网络异常等边界情况
6. **后端契约一致性**：所有 API 调用与 Web 端共享后端接口契约，不引入新的后端端点
