# 05 - 前端 Web 模块

> 模块路径：`Hbsz_IDC_System/`
> 技术定位：PC 管理端（管理员 / 项目经理），Vue 3 单页应用
> 文档版本：v4.0（2026-08-23）

---

## 1. 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| vue | ^3.4.21 | 框架（Composition API） |
| vue-router | ^4.3.0 | 路由（HTML5 History 模式） |
| pinia + pinia-plugin-persistedstate | ^2.1.7 / ^3.2.3 | 状态管理 + 持久化（配合 cryptoStorage 加密存储） |
| element-plus + @element-plus/icons-vue | ^2.6.2 | UI 组件库（Ant Design 蓝品牌体系、WCAG AA） |
| echarts | ^5.5.0 | 图表（**必须全量引入** `import * as echarts`，渐变色依赖） |
| axios / dayjs | ^1.18.1 / ^1.11.10 | 请求 / 日期 |
| @stomp/stompjs + sockjs-client | ^7.0.0 / ^1.6.1 | WebSocket 实时通知 |
| element-china-area-data | ^6.1.0 | 省市区数据 |
| vite / vitest / eslint | ^5.2.0 / ^2.1.9 / ^9.0.0 | 构建 / 单测（happy-dom）/ 检查 |

## 2. 构建配置（vite.config.js）

- 别名：`@ → src`、`@shared → ../shared`（跨端共享纯函数）
- 开发代理：`/api → http://localhost:8080`、`/ws → WebSocket 代理（ws: true）`
- 生产构建：输出 `dist/`、**sourcemap 关闭**、**资产 hash 文件名**、手动分包（vue/vue-router/pinia、element-plus、echarts、axios/dayjs 四个 vendor chunk）、terser 压缩

环境文件：`.env.development` / `.env.production`（API 基地址等）。

## 3. 目录结构与职责

```
src/
├── api/           # 19 个接口模块（按后端域划分），见 §5
├── components/    # 通用组件（CrudTable、StatCard、StatusTag、GanttChart、
│                  # FingerprintIcon 自定义指纹图标…），见 §6
├── composables/   # 组合式函数：useCrudList、useImport、useKeepAliveRefresh、useCountUp
├── constants/     # options.js（下拉字典/枚举）
├── layouts/       # MainLayout.vue 主壳 + SidebarMenu / NotificationDrawer / RemindDrawer
├── router/        # index.js 全站路由 + 全局守卫
├── stores/        # 9 个 Pinia store，见 §4
├── styles/        # global.css（设计 Token：氛围色/glow 阴影/mono 数字字体/动画/无障碍）
├── utils/         # 18 个工具模块，见 §7
└── views/         # 页面组件（按菜单分组，如 workload/QuickReportPanel.vue、
                  # workload/WorkloadStatistics.vue、my/MyTasks.vue、my/RiskBoard.vue、
                  # app/AppDownload.vue），见 §8
```

## 4. 状态管理（stores/）

| Store | 职责 |
|-------|------|
| user | token/refreshToken/userInfo（cryptoStorage 加密恢复）、登录态、角色权限 getter |
| systemConfig | 系统配置（名称等）；请求需 `skipCancel: true` 防路由切换取消 |
| project / customer / document / progress / statistics / notification | 对应业务域列表与状态缓存 |
| system | 系统管理域 |

## 5. API 层（api/，19 个模块）

`auth`、`user`、`project`、`projectAuth`（授权申请）、`projectAuthorization`（转移/授权工程师）、`projectTeam`、`task`、`document`、`customer`、`dashboard`、`statistics`、`dailyReport`、`workload`、`notification`、`system`、`approval`、`approvalFlow`、`position`，以及各模块复用 `utils/request.js` 实例。函数命名与后端端点一一对应（如 `getMyReportContext()` → `GET /workload/my/report-context`）。

## 6. 通用组件（components/）

| 组件 | 说明 |
|------|------|
| CrudTable.vue | 通用列表组件：data/loading/total/分页/骨架屏/树形表格配置，全站列表页复用 |
| StatCard.vue | 统计卡片：mono 数字字体、数字滚动入场（useCountUp）、tone 色 glow 悬浮 |
| StatusTag.vue | 统一状态标签（软色系，对应 utils/statusMap.js） |
| FingerprintIcon.vue | **自定义指纹图标**（2026-08-23 新增）：参照 Material Design 指纹图标的 SVG 组件，继承 `currentColor`、在 `<el-icon>` 内自动缩放。背景：`@element-plus/icons-vue`（截至 2.3.2）不提供指纹图标。在 [utils/icons.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/utils/icons.js) 注册为全局 `Fingerprint`，模板 `<el-icon><Fingerprint /></el-icon>` 用法与内置图标完全一致。用于 [AppDownload.vue](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/views/app/AppDownload.vue) 移动端指纹登录特性提示 |
| LoginParticles.vue | 登录页粒子星网背景（canvas 引擎，2026-08-22 新增）：2-6px 粒子、距离衰减连线、`lighter` 混合模式发光、8-15s 慢速漂移、光标聚合吸引；`prefers-reduced-motion` 时降级为静态；卸载时取消 rAF 并解绑全部事件 |
| GanttChart.vue | 甘特图（进度管理/日报预览） |
| EmptyState / TableSkeleton / ErrorBoundary / BaseButton | 空态 / 骨架 / 错误边界 / 按钮 |
| ApprovalFlowChart.vue | 审批流程图（演示页） |

## 7. 工具库（utils/）

| 模块 | 职责 |
|------|------|
| request.js | axios 封装：自动注入 Bearer、登记取消队列、统一错误处理（含 401 刷新逻辑） |
| cancelToken.js | 请求取消管理器：路由切换/登出 `cancelAllPending()`，自动去重写请求 |
| websocket.js | STOMP 客户端（订阅个人通知通道） |
| permission.js | `hasPermission(code|array)` / `isAdmin()`（与路由守卫、菜单共用） |
| sessionMonitor.js | 会话监控（token 过期前提醒/自动登出） |
| cryptoStorage.js | 加密本地存储（token 等敏感信息） |
| errorReporter.js / performance.js | 前端错误上报 / 性能埋点 |
| feedback.js | 统一 ElMessage/ElLoading 反馈 |
| download.js / upload.js / fileUrl.js | 下载 / 上传（100MB、进度）/ 文件地址拼接 |
| format.js | 格式化（与 `@shared/format.js` 同源） |
| statusMap.js | 状态码 → 文案/颜色映射（有单测） |
| submitGuard.js | 防重复提交守卫 |
| icons.js | Element Plus 图标**按需注册**（性能优化：从全量 280 个收敛到 40+ 个实际使用）。结构：① 顶部 import 块显式引入用到的图标；② `icons` 数组罗列需注册的组件；③ `registerElementPlusIcons(app)` 遍历数组 `app.component`，再单独注册自定义 `Fingerprint`。**⚠ 维护口诀**：图标增删时，import 块与 `icons` 数组两处必须同步——只删 import 保留数组里的裸标识符，Rollup 当全局变量（构建通过）→ 运行时 ReferenceError 致整站白屏（2026-08-23 事故） |

单测（vitest）：`request.test.js`、`statusMap.test.js`、`stores/user.test.js` 等，共 45 个用例（`npm run test`，要求全绿）。

## 8. 路由与页面（router/index.js）

全局守卫执行顺序：取消未完成请求 → 系统名拼接标题 → 登录态校验（未登录跳 `/login?redirect=`）→ `meta.permission` 权限校验（字符串=必须满足；数组=任一满足；`isAdmin()` 跳过）→ 放行。

| 路径 | 页面 | 权限 | 分组 |
|------|------|------|------|
| /login | Login.vue | 公开 | - |
| /dashboard | Dashboard.vue（WelcomeBanner/StatCard 仪表盘） | 登录 | 工作台 |
| /projects | ProjectList.vue | project:query | 项目管理 |
| /projects/create、/projects/:id/edit | ProjectEdit.vue（hidden） | project:add/edit | 项目管理 |
| /projects/transfer-logs | TransferLogs.vue（hidden） | project:query | 项目管理 |
| /projects/:id | ProjectDetail.vue（hidden，含授权工程师区/转移入口；**负责人关联告警**：`managerLinkWarning` computed 依据后端 `managerLinkStatus` 在项目经理栏展示 tag——mismatch「归属分叉」橙 / unlinked「未关联用户」红 / disabled「账号已停用」橙 / missing「关联失效」橙，hover tooltip 说明影响与修正路径，2026-08-24 行动项#4） | project:query | 项目管理 |
| /project-auth | ProjectAuthManagement.vue | project:auth:request | 项目协作 |
| /project-teams | ProjectTeamManagement.vue | project:team:manage | 项目协作 |
| /documents | DocumentManagement.vue（上传/版本/预览/审核/编辑弹窗） | document:query | 文档管理 |
| /progress | ProgressManagement.vue | task:query | 进度任务 |
| /progress/:projectId | ProjectProgress.vue（hidden） | task:query | 进度任务 |
| /review/pending | PendingReview.vue | task:progress:review | 进度任务 |
| /my/risk-board | RiskBoard.vue | task:progress:review | 进度任务 |
| /my/tasks | MyTasks.vue | task:query | 进度任务 |
| /my/submissions | MySubmissions.vue | task:query | 进度任务 |
| /customers | CustomerManagement.vue | customer:query | 客户管理 |
| /archive | ArchiveManagement.vue | archive:query | 归档管理 |
| /statistics | Statistics.vue（ECharts） | statistics:query | 数据分析 |
| /daily-report | DailyReport.vue | statistics:query | 数据分析 |
| /workload | WorkloadStatistics.vue（三 Tab：快速填报 QuickReportPanel / 矩阵填报 / 统计-审核-绩效） | workload:query | 数据分析 |
| /approval-flow | ApprovalFlowDemo.vue | 登录（演示页） | 数据分析 |
| /approval/todo | TodoCenter.vue（四类审核聚合） | [task:progress:review, workload:review, document:review, project:auth:approve] 任一 | 审批中心 |
| /app-download | AppDownload.vue（App 下载入口，二维码 + 直链，标注指纹快速登录特性） | 登录 | 系统设置 |
| /system/users | UserManagement.vue | system:user:query | 系统管理 |
| /system/roles | RoleManagement.vue | system:role:query | 系统管理 |
| /system/permissions | PermissionManagement.vue | system:permission:query | 系统管理 |
| /system/positions | PositionManagement.vue | position:query | 系统管理 |
| /system/logs | OperationLog.vue | system:log:query | 系统管理 |
| /settings | Settings.vue | 登录（改密为个人；系统配置由后端权限控制） | 系统设置 |
| /403、/:pathMatch(.*)* | NotFound.vue | - | 兜底 |

### QuickReportPanel（工作量快速填报，2026-08-21 重构核心）

- 数据源 `GET /workload/my/report-context`：我的项目 + 14 天历史自动生成「我的常用项」卡片
- 「复制昨日」一键预填（仅填空值）；卡片直接编辑，实时显示提交数
- 提交复用 `POST /workload/records`；语义状态色（已通过绿/待审橙/驳回红）
- **一键重报**（2026-08-22 新增）：卡片显示驳回原因；`resubmit(card)` 单卡重报、`resubmitRejected()` 状态条全部驳回项批量重报——依赖后端 upsert 语义（覆盖数量并重置回待审核）

## 9. UI 设计规范

- 品牌色：Ant Design 蓝体系；对比度符合 WCAG AA
- 界面**不使用 emoji 图标**（Element Plus 图标 + 自定义 FingerprintIcon + icons.js 映射）
- 设计 Token 集中在 `styles/global.css`（Glow + Grain 方向：氛围色、发光阴影、mono 数字字体、staggered 入场动画、无障碍处理）
- 数字展示统一 `tabular-nums` 对齐

## 10. 本地运行与构建

```powershell
cd Hbsz_IDC_System
npm install
npm run dev          # 开发服务器（代理到 localhost:8080 后端）
npm run lint:check   # ESLint 检查（0 error 要求）
npm run test         # vitest 单测
npm run build        # 生产构建 → dist/（sourcemap 关闭、hash 文件名）
npm run preview      # 本地预览构建产物（部署前必跑，可提前发现白屏/构建问题）
```

> **部署前自检**：`npm run preview` 在 localhost 预览 + 浏览器控制台检查是发现白屏问题的最后一道关卡，不可只靠 dist 内容 grep。生产部署后同样需浏览器运行时冒烟验证。
