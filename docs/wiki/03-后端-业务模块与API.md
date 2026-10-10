# 03 - 后端业务模块与 API

> 承接 [02-后端-架构与基础设施.md](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/02-后端-架构与基础设施.md)
> Controller 目录：`Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/controller/`
> 所有接口实际前缀：`/api/v1`（context-path `/api` + 版本过滤器 `v1`）

---

## 1. Controller 总览（28 个）

| Controller | 路径前缀 | 职责 | 关键权限码 |
|------------|----------|------|-----------|
| AuthController | /auth | 登录/登出/用户信息/token 刷新 | 公开（登录需验证码） |
| CaptchaController | /captcha | 图形验证码 | 公开 |
| UserController | /users | 当前用户资料、改密 | 登录 |
| UserManageController | /system/users | 用户管理 CRUD、批量导入、重置密码、角色分配 | system:user:* |
| RoleController | /roles | 角色 CRUD、权限分配 | system:role:* |
| PermissionController | /permissions | 权限 CRUD | system:permission:* |
| PositionController | /positions | 岗位 CRUD | position:* |
| ProjectController | /projects | 项目 CRUD、归档、统计、批量导入、模板下载 | project:* |
| ProjectAuthorizationController | /projects/{id}/authorization… | 项目转移、授权工程师管理、转移审计日志 | project:transfer 等 |
| ProjectAuthController | /project-auth | 授权申请创建/审批/驳回、授权撤销/延期 | project:auth:request / approve |
| ProjectTeamController | /project-teams | 项目组与成员（含工人）管理 | project:team:manage |
| TaskController | /tasks | 任务 CRUD、进度填报、审核、待审列表、整体进度、关键节点 | task:* |
| DocumentController | /documents | 文档 CRUD、版本、标签、审核 | document:* |
| FileController | /files | 上传/下载/预览（magic number 校验） | 登录 |
| CustomerController | /customers | 客户 CRUD、导入 | customer:* |
| ArchiveController | /archive | 归档查询/统计、归档记录 | archive:* |
| DashboardController | /dashboard | 工作台汇总（统计卡、图表、近期动态） | 登录 |
| StatisticsController | /statistics | 多维统计（绕过数据隔离） | statistics:query |
| DailyReportController | /daily-report | 日报预览/导出 xlsx/历史/下载 | statistics:query |
| WorkloadController | /workload | 工作量清单/填报/快速填报上下文/矩阵/审核/绩效/导出 | workload:* |
| NotificationController | /notifications | 站内通知列表/已读/设置 | 登录 |
| SystemConfigController | /system/config | 系统配置读写（持久化到 DB） | system:config:edit |
| SystemController | /system | 系统信息 | 登录 |
| CacheController | /system/cache | 缓存管理（SCAN） | system:cache:manage |
| MonitorController | /monitor | 运行时监控 | 管理员 |
| OperationLogController | /operation-logs | 操作日志查询 | system:log:query |
| ApprovalFlowController | /approval/flow | 审批流定义/流转 | approval:* |
| ApprovalTodoController | /approval/todos | 审批待办聚合中心（四类审核） | 按实际审核权限过滤 |

## 2. 重点模块详解

### 2.1 认证模块（AuthController / AuthServiceImpl）

```java
// 登录：验证码 → 账号锁定检查 → 密码校验 → 签发双 token
@PostMapping("/login") Result<LoginVO> login(@Valid LoginDTO dto)   // captchaId + captcha 必填
@PostMapping("/refresh") Result<LoginVO> refresh(RefreshTokenDTO)   // 无感续期
@PostMapping("/logout") Result<Void> logout()                        // token 入 Redis 黑名单
@GetMapping("/info") Result<UserInfoVO> info()                        // 当前用户 + 角色 + 权限码
```

要点：验证码错误不递增锁定计数；连续密码错误触发账号锁定（Redis 计数）。

### 2.2 项目模块（ProjectController / ProjectServiceImpl）

- `GET /projects`（ProjectQueryDTO 分页筛选）、`POST/PUT/DELETE`（`@Idempotent` + `@OperationLog`）
- `POST /projects/import` 批量导入 + 模板下载
- `POST /projects/{id}/archive` / `cancel-archive` 归档管理
- `GET /projects/statistics` 项目统计
- 项目数据可见性由 `ProjectAccessGuard` 控制（管理员/PM/授权工程师/组员）
- **详情接口下发负责人关联健康度**（2026-08-24 行动项#4）：`GET /projects/{id}` 响应含 `managerId` / `managerLinkedName`（manager_id 对应用户姓名）/ `managerLinkStatus`（`ok`/`mismatch` 分叉/`unlinked` 未关联/`disabled` 已停用/`missing` 用户不存在），判定逻辑单点收敛在 `BizProjectMapper.xml` 的 `selectProjectDetailById`（LEFT JOIN sys_user + CASE WHEN）；前端项目详情页据此展示告警 tag（见 05 篇 §8）
- **项目经理自动关联**（2026-08-22 修复，2026-08-24 增强）：`biz_project.manager_name` 为自由文本，`manager_id` 才是审核通知/每日摘要/审批归属的用户关联。create/update 在前端未显式传 `managerId` 时按经理姓名与启用用户**唯一匹配**自动关联（重名不猜，保持 null 并 WARN）；存量数据由 `V20260822` 迁移回填。通知侧（Task/Workload）遇 PM 未关联时打 WARN 不再静默丢弃。**2026-08-24 增强（行动项#3）**：update 时显式 `managerId` 缺失但姓名文本变化、且按姓名唯一解析出与当前负责人**不同**的用户 → 视为 PM 变更，同样委托 `transferLeader` 单点处理（同步组长/授权/审计，防止文本改链造成 manager_id 与 team/assignment 分叉）；重名/无匹配/解析到同一用户则仅更新姓名文本，保持原关联（配套 `ProjectServiceImplManagerLinkTest` update 路径 4 个用例，总测试 138 个）

### 2.3 项目转移与授权（ProjectAuthorizationController / ProjectAuthorizationServiceImpl）

7 个端点，是 2026-07 后新增的核心协作能力：

| 端点 | 功能 |
|------|------|
| POST /projects/{id}/authorization/transfer | 项目转移（转移项目经理，三模式：转移+撤销旧授权/保留授权/移交全部） |
| POST /projects/{id}/authorization | 新增授权工程师 |
| DELETE /projects/{id}/authorization/{engineerId} | 撤销授权 |
| GET /projects/{id}/authorization | 查询授权工程师列表 |
| GET /projects/authorization/engineers | 活跃工程师下拉 |
| GET /projects/authorization/managers | 活跃项目经理下拉 |
| GET /projects/authorization/logs | 转移审计日志（BizProjectTransferLog，TransferLogVO） |

辅助：`ProjectTransferLogHelper` 统一记录转移日志；`AssignmentRevokeService` 撤销旧项目经理授权关系（唯一索引 `uk_project_engineer_active` 保证一项目一活跃 PM，见 V20260821 迁移）。

### 2.4 授权申请流（ProjectAuthController / ProjectAuthServiceImpl）

- `POST /project-auth` 创建授权申请（BizProjectAuthRequest，pending 唯一守卫见 V20260819.1）
- `GET /project-auth` 查询申请列表、`PUT /project-auth/{id}/approve` 审批、`PUT /project-auth/{id}/reject` 驳回
- 授权关系延期/撤销（需 `project:auth:approve`）
- 到期自动失效：`AssignmentExpireTask` 定时扫描

### 2.5 任务与进度（TaskController / TaskServiceImpl）

核心审核流（**业务主线**）：

```
工程师 GET /tasks/my              我的任务
        POST /tasks/{id}/progress 提交进度（ProgressReportDTO：
                                   必须上传施工图片；实际开始时间 ≤ 实际结束时间，跨字段校验）
                │  生成 BizTaskProgressLog（status=pending，不回写任务）
                ▼
项目经理 GET /tasks/progress/pending   待审核列表
        PUT /tasks/progress/{logId}/review  审核（ProgressReviewDTO：通过/驳回）
                │  通过 → 回写 BizTask 进度/实际日期；驳回 → 工程师重新填报
                ▼
        GET /tasks/project/{projectId}/progress  项目整体进度/甘特数据
```

其他：任务 CRUD（TaskSaveDTO/TaskVO）、`GET /tasks/project/{projectId}` 项目任务树、`GET /tasks/reminders` 关键节点提醒、`GET /tasks/my/risk` 风险看板（RiskBoardVO）、`GET /tasks/my/submissions` 我的填报历史。

通知触达（TaskServiceImpl 内置，2026-08-22 新增）：
- 任务创建/分配 → 通知被分配人（`任务「xx」已分配给你，分配人：…，截止：…`）
- 进度填报提交 → 通知项目经理「进度填报待审核」（含进度百分比与填报人）
- 进度审核完成 → 通知填报人「进度审核通过/驳回」（含审核人与意见）

### 2.6 工作量模块（WorkloadController / WorkloadServiceImpl）

2026-08-17 新增、2026-08-21 重构为「30 秒卡片流」：

| 端点 | 功能 | 权限 |
|------|------|------|
| GET /workload/items | 工作量清单（106 项标准条目，BizWorkloadItem） | workload:query |
| POST /workload/items/import | 标准模板导入（EasyExcel） | workload:edit |
| GET /workload/my/report-context | **快速填报上下文**（我的项目 + 14 天高频条目卡片 + 昨日记录，WorkloadMyReportVO） | workload:report |
| POST /workload/records | 保存填报（单条/批量，WorkloadRecordBatchDTO；只能选项目组成员，进入 pending 待审） | workload:report |
| GET /workload/matrix | 统计矩阵（WorkloadMatrixVO/WorkloadCellVO） | workload:query |
| GET /workload/pending | 待审核列表 | workload:review |
| PUT /workload/review | 批量审核（WorkloadReviewDTO：通过/驳回） | workload:review |
| GET /workload/performance | 人员绩效（WorkloadWorkerPerformanceVO） | workload:query |
| GET /workload/export | Excel 导出 | workload:export |

前端对应三个 Tab：快速填报（工人默认）/ 矩阵填报（PM 批量）/ 统计-审核-绩效。

通知触达（WorkloadServiceImpl 内置，2026-08-22 新增）：
- 填报保存 → 汇总当日待审条数，通知项目经理「工作量填报待审核」
- 批量审核完成 → 按工人分组回执「工作量审核通过/驳回」（含审核人与意见）
- 记录写入采用 **upsert 语义**：rejected 记录重新提交会覆盖数量并重置回待审核（支撑前端「一键重报」）

### 2.7 文档与文件（DocumentController / FileController）

- DocumentServiceImpl：文档 CRUD、版本（BizDocumentVersion）、标签（BizDocumentTag/Relation）、文档审核（V20260714 迁移新增审核位）、归档关联；审核完成 → 通知上传人「文档审核通过/驳回」回执（含审核人与意见，2026-08-22 新增）
- FileStorageServiceImpl + FileController：
  - `POST /files/upload`（100MB 上限；扩展名白名单 + **magic number 校验**，OOXML 统一 `PK\x03\x04`）
  - `GET /files/preview/{id}`：SVG 故意不映射 content-type → 强制下载（防存储型 XSS）
  - `GET /files/download/{id}`

### 2.8 日报导出（DailyReportController / DailyReportServiceImpl）

- `GET /daily-report/preview` 甘特图日报预览
- `GET /daily-report/export` 生成 **Excel(.xlsx)**（EasyExcel），记录 BizExportRecord
- `GET /daily-report/history` / `GET /daily-report/download/{id}` / `DELETE /daily-report/history/{id}` 导出历史管理

### 2.9 审批待办中心（ApprovalTodoController / ApprovalTodoServiceImpl）

聚合四类待审核事项（进度审核 / 工作量审核 / 文档审核 / 授权审批），返回 `TodoCountVO` + `TodoItemVO` 分页列表；按当前用户实际拥有的审核权限过滤来源。

### 2.10 系统管理（UserManage/Role/Permission/Position/SystemConfig/Cache/Monitor/OperationLog）

- UserManageServiceImpl：用户 CRUD、Excel 批量导入（UserImportDTO）、重置密码、启用/停用、角色分配（AssignRoleDTO）
- RoleServiceImpl / PermissionManageServiceImpl：RBAC 管理（AssignPermissionDTO）
- SystemConfigServiceImpl：系统配置**持久化 DB**（sys_system_config），请求需 `skipCancel: true` 防路由切换取消
- CacheController：Redis 缓存管理（**SCAN**，权限 `system:cache:manage`）
- OperationLogServiceImpl：`@OperationLog` 切面落库的审计日志查询（V20260819 新增查询权限）
- MonitorController：JVM/内存/线程运行时监控

### 2.11 通知（NotificationController / NotificationServiceImpl / NotificationPushService）

- 通知 CRUD、已读/全部已读、通知偏好设置（SysNotificationConfig）
- NotificationPushService：经 WebSocket（STOMP `/ws`）向指定用户实时推送
- TaskDueNotificationScheduler：任务到期前定时提醒
- DailyTodoDigestScheduler：每日 09:00 推送「今日待办摘要」（详见 02 篇 §8）

业务触发通知全景（notifyType → 场景，2026-08-22 全量上线）：

| notifyType | 触发点 | 接收人 |
|------------|--------|--------|
| task | 任务创建/分配 | 被分配人 |
| progress | 进度填报提交 / 进度审核结果 | 项目经理 / 填报人 |
| workload | 工作量填报提交 / 批量审核结果 | 项目经理 / 各工人（按人分组回执） |
| document | 文档审核结果 | 上传人 |
| todo_digest | 每日 09:00 待办摘要（四类待审汇总） | 项目经理 + admin |

### 2.12 工作台与统计（DashboardController / StatisticsController）

- DashboardServiceImpl：汇总卡片（进行中项目、待办任务、待审核、通知未读）、图表数据、近期动态
- StatisticsServiceImpl：多维度统计（项目状态分布、工作量趋势等），需 `statistics:query` 权限绕过数据隔离

## 3. Service 层清单（26 个）

| 接口 | 实现 | 职责要点 |
|------|------|----------|
| IAuthService | AuthServiceImpl | 登录/登出/刷新/锁定计数 |
| IUserService | UserServiceImpl | 当前用户资料/改密 |
| IUserManageService | UserManageServiceImpl | 用户管理/导入/角色分配 |
| IRoleService | RoleServiceImpl | 角色管理 |
| IPermissionService | PermissionServiceImpl | 权限校验（供切面调用） |
| IPermissionManageService | PermissionManageServiceImpl | 权限管理 |
| IPositionService | PositionServiceImpl | 岗位管理 |
| IProjectService | ProjectServiceImpl | 项目全生命周期 |
| IProjectAuthorizationService | ProjectAuthorizationServiceImpl | 转移/授权工程师/转移日志 |
| IProjectAuthService | ProjectAuthServiceImpl | 授权申请审批流 |
| IProjectTeamService | ProjectTeamServiceImpl | 项目组/成员/工人 |
| - | AssignmentRevokeService | 授权撤销（转移场景） |
| ITaskService | TaskServiceImpl | 任务/进度/审核流 |
| IDocumentService | DocumentServiceImpl | 文档/版本/标签/审核 |
| IFileStorageService | FileStorageServiceImpl | 文件存储与安全校验 |
| ICustomerService | CustomerServiceImpl | 客户管理 |
| IArchiveService | ArchiveServiceImpl | 归档查询/统计 |
| IDashboardService | DashboardServiceImpl | 工作台聚合 |
| IStatisticsService | StatisticsServiceImpl | 统计分析 |
| IDailyReportService | DailyReportServiceImpl | 日报导出 |
| IWorkloadService | WorkloadServiceImpl | 工作量全套 |
| INotificationService | NotificationServiceImpl | 站内通知 |
| - | NotificationPushService | WebSocket 推送 |
| IOperationLogService | OperationLogServiceImpl | 审计日志 |
| ISystemConfigService | SystemConfigServiceImpl | 系统配置 |
| IApprovalFlowService | ApprovalFlowServiceImpl | 审批流 |
| IApprovalTodoService | ApprovalTodoServiceImpl | 待办聚合 |

## 4. 权限码总表（主要）

```
project:query/add/edit/delete/archive/transfer/team:manage/auth:request/auth:approve
task:query/add/edit/delete/progress:report/progress:review
workload:query/report/edit/export/review
document:query/add/edit/delete/review
customer:query/add/edit/delete
archive:query
statistics:query
system:user:query/add/edit/delete system:role:query system:permission:query
system:log:query system:config:edit system:cache:manage
position:query/add/edit/delete
```

> 角色约定：role_id=1 超级管理员；role_id=2 项目经理（必须持有 `system:user:query`(ID=11) 以访问用户列表）。
