# IDC数据中心综合布线项目管理系统 - 前后端代码逻辑分析报告

> 生成日期：2026-07-13
> 更新日期：2026-07-15（v1.1 新增工作量管理机制代码逻辑分析）
> 分析范围：后端（Spring Boot 3 + MyBatis Plus + Redis）、前端（Vue3 + Element Plus + Pinia）

---

## 目录

1. [代码结构概述](#1-代码结构概述)
2. [核心业务逻辑流程分析](#2-核心业务逻辑流程分析)
3. [前后端数据流转路径](#3-前后端数据流转路径)
4. [接口设计合理性与安全性](#4-接口设计合理性与安全性)
5. [问题清单](#5-问题清单)
6. [优化建议](#6-优化建议)

---

## 1. 代码结构概述

### 1.1 后端分层架构

```
Controller层（接口入口）
  ├── AuthController          认证（登录/登出/刷新/当前用户）
  ├── ProjectController       项目管理
  ├── TaskController          任务进度
  ├── DocumentController      文档管理
  ├── FileController          文件上传下载
  ├── NotificationController 站内通知
  ├── UserController          用户管理
  ├── RoleController          角色管理
  ├── PermissionController    权限管理
  ├── ProjectAuthController   项目授权
  ├── ProjectTeamController   项目组管理
  ├── CustomerController      客户管理
  ├── StatisticsController    统计分析
  ├── SystemConfigController 系统配置
  └── PositionController      岗位管理

Service层（业务逻辑）
  ├── *ServiceImpl           核心业务实现（@Transactional 事务管理）
  ├── PermissionAspect       权限切面（@RequiresPermission 注解驱动）
  ├── RateLimitAspect        限流切面（Redis 计数 + 受信代理白名单）
  ├── IdempotentAspect       幂等切面（Redis SETNX + X-Request-Id）
  └── OperationLogAspect     操作日志切面（异步写入）

基础设施层
  ├── JwtAuthInterceptor     JWT认证拦截器（Bearer校验 + 黑名单本地缓存）
  ├── GlobalExceptionHandler 全局异常处理（分类处理 + 堆栈脱敏）
  ├── UserContext            ThreadLocal用户上下文（afterCompletion清理）
  ├── RedisUtils             Redis操作工具
  └── FileStorageServiceImpl 文件存储服务（五层安全防护）
```

### 1.2 前端分层架构

```
视图层
  ├── views/                 页面组件（Dashboard/ProjectList/ProjectEdit/...）
  ├── layouts/MainLayout.vue 全局布局（侧边栏 + 顶栏 + 内容区）
  └── components/            公共组件（BaseTable/BaseButton/BaseDialog/...）

状态管理层（Pinia）
  ├── stores/user.js         用户状态（token/角色/权限/登录登出）
  ├── stores/project.js     项目状态（列表/详情/CRUD）
  ├── stores/system.js       系统状态（角色/权限/配置）
  └── stores/systemConfig.js 系统配置（名称/Logo等）

工具层
  ├── utils/request.js       Axios封装（拦截器/取消队列/Token刷新协调器）
  ├── utils/cryptoStorage.js 加密存储（XOR + Base64）
  ├── utils/permission.js    权限校验（hasPermission/isAdmin）
  ├── utils/websocket.js     WebSocket连接（指数退避重连）
  ├── utils/cancelToken.js   请求取消（AbortController）
  └── utils/icons.js         Element Plus图标注册

路由层
  └── router/index.js        路由配置（权限守卫/取消请求/页面标题）
```

---

## 2. 核心业务逻辑流程分析

### 2.1 项目管理流程

```
创建项目
  ├── 校验项目编号唯一（selectCount）
  ├── 保存或查询客户（saveOrGetCustomer）
  ├── 校验日期区间（startDate <= endDate）
  ├── 插入项目记录
  ├── 批量插入设计图纸（batchInsert）
  └── 返回项目详情

更新项目
  ├── 查询项目存在性
  ├── 校验项目编号唯一（排除自身）
  ├── 字段选择性更新（非空才覆盖）
  ├── 日期交叉校验
  ├── 乐观锁更新（version字段，updateById返回0则提示刷新）
  ├── PM变更 → 委托transferLeader统一处理
  │     ├── 更新 biz_project.manager_id/manager_name
  │     ├── 更新 biz_project_team leader
  │     ├── 更新 biz_project_assignment（查重后插入）
  │     └── 写入审计日志
  └── 设计图纸先删后增

删除项目
  ├── 校验关联任务（存在则拒绝）
  ├── 校验关联文档（存在则拒绝）
  └── 删除项目记录

归档项目
  ├── 标记 isArchived=1, status=已归档
  ├── 记录归档日期
  └── 写入归档操作流水
```

**关键设计评价：**
- 乐观锁机制完善，并发更新有保护
- PM变更委托统一路径，避免数据不一致
- 删除前关联数据校验，防止孤儿数据
- 设计图纸先删后增策略合理（DTO无id字段，diff更新不现实）

### 2.2 任务进度审核流程

```
工程师提交进度（reportProgress）
  ├── 查询任务存在性
  ├── 创建 pending 状态进度日志（不直接修改task.progress）
  ├── 序列化施工图片URL列表（JSON数组）
  ├── 通知项目经理（静默投递）
  └── 返回日志VO

项目经理审核（reviewProgress）
  ├── 校验日志为pending状态（防重复审核）
  ├── 审核通过：
  │     ├── 回写 task.progress
  │     ├── 联动任务状态（100%→已完成, >0→进行中）
  │     ├── 同步项目整体进度（syncProjectProgress）
  │     └── 通知填报人审核通过
  └── 审核驳回：
        ├── 仅更新日志状态为rejected
        └── 通知填报人审核驳回

项目进度同步（syncProjectProgress）
  └── UPDATE biz_project SET progress = (SELECT AVG(progress) FROM biz_task WHERE project_id = ?)
      └── 原子操作，消除并发覆盖风险
```

**关键设计评价：**
- 进度填报采用审核流程，不直接修改任务进度，设计合理
- 项目进度同步使用SQL子查询原子更新，避免Java端循环计算和并发覆盖
- 审核日志详尽，包含前后值对比和影响行数检查
- 数据隔离完善：工程师仅可见自己填报的日志，空列表时直接返回（修复H3越权漏洞）

### 2.3 项目授权流程

```
创建授权申请
  ├── 校验项目存在
  ├── 校验目标工程师有效
  ├── 校验申请人有权申请（admin/PM/项目负责人）
  ├── 幂等校验（同项目同工程师不可有pending申请）
  ├── 插入申请记录（status=pending）
  └── 写审计日志

审批通过
  ├── 校验申请为pending状态
  ├── 校验目标工程师存在
  ├── 校验是否已存在有效授权（防重复授权）
  ├── 更新申请状态为approved
  ├── 创建 biz_project_assignment 记录
  └── 写审计日志

审批驳回
  ├── 校验申请为pending状态
  ├── 更新申请状态为rejected
  └── 写审计日志
```

**关键设计评价：**
- 双重校验（pending状态 + 已有有效授权），防止重复授权
- 审计日志完整，记录操作类型和操作人
- 事务处理正确，审批和授权记录在同一事务内

### 2.4 文档管理流程

```
创建文档
  ├── 校验项目存在
  ├── 插入文档记录
  ├── 插入初始版本
  └── 保存标签

更新文档
  ├── 非空字段选择性更新
  └── 标签全量覆盖（先删后增）

删除文档（安全修复 #27）
  ├── 事务内：先删DB记录
  ├── 注册 afterCommit 钩子
  └── 事务提交后：删除物理文件（失败仅记录日志）

新增版本
  ├── 插入新版本记录
  └── 更新文档当前版本号
```

**关键设计评价：**
- 删除顺序优化（先DB后文件），afterCommit钩子确保事务一致性
- 物理文件删除失败不回滚DB记录，避免文件丢失
- 版本管理机制完整

### 2.5 认证与Token刷新流程

```
登录
  ├── 校验用户名密码
  ├── 生成 JWT（accessToken + refreshToken）
  ├── 返回 token + 用户信息 + 角色 + 权限

前端Token刷新（TokenRefreshCoordinator）
  ├── 401/2001/2002 触发刷新
  ├── 首个请求获取锁，调用 /auth/refresh
  ├── 后续请求进入等待队列
  ├── 刷新成功 → 重发所有队列请求
  └── 刷新失败 → 跳转登录页

安全防护
  ├── __retried 标记防止无限重试循环
  ├── /auth/refresh 自身401直接跳登录
  └── FormData请求自动删除Content-Type
```

**关键设计评价：**
- Token刷新协调器设计优秀，"锁+队列"避免并发刷新
- __retried标记和URL检查双重防护死循环
- 路由守卫取消未完成请求，防止内存泄漏
- Blob响应体检测application/json类型，处理下载错误

### 2.6 通知批量发送流程

```
单条通知（sendNotification）
  ├── 检查用户通知偏好
  ├── 写入 sys_notification 表
  └── WebSocket 推送（静默投递）

批量通知（sendNotifications）
  ├── 过滤已关闭通知类型的用户
  ├── 批量插入通知记录（batchInsert）
  └── 逐个 WebSocket 推送

定时任务到期通知（TaskDueNotificationScheduler）
  ├── 08:00 扫描未来3天到期及已逾期任务
  ├── 批量查询今日已发通知（去重集合）
  ├── 批量查询项目名称（映射）
  └── 使用 sendNotifications 批量发送
```

**关键设计评价：**
- 批量发送优化数据库往返次数
- 定时任务N+1查询已优化（批量查询已发通知和项目名称）
- 通知投递失败不影响主业务流程

### 2.7 工作量管理流程（v1.1 新增）

工作量管理机制在原有任务进度审核流程基础上，引入"基准设定 → 实际回写 → 偏差检测"三段式逻辑，形成工作量闭环管理。

#### 2.7.1 工作量基准设定

```
创建任务（TaskServiceImpl.create）
  ├── 构建任务实体
  ├── 设置 plannedWorkload 基准（来自 TaskSaveDTO）
  ├── 设置 workloadUnit（人时/人日/人月）
  ├── actualWorkload 初始化为 BigDecimal.ZERO
  └── 插入任务记录

更新任务（TaskServiceImpl.update）
  ├── 查询任务存在性
  ├── 允许 PM 调整 plannedWorkload（基准可修订）
  ├── workloadUnit 同步更新
  ├── actualWorkload 不重置（保留累计实际值）
  └── 乐观锁更新

工作量模板导入（ProjectServiceImpl.importWorkload）
  ├── 解析 Excel 模板
  ├── 匹配项目下任务（按任务名/编号）
  ├── 批量设置 plannedWorkload 基准
  └── 批量更新任务记录
```

**关键设计评价：**
- 基准设定复用现有任务 CRUD 路径，无需独立接口
- `update()` 允许基准修订但保留 `actualWorkload` 累计值，避免历史工时数据丢失
- `importWorkload()` 支持批量初始化基准，适配项目立项阶段的工作量估算场景

#### 2.7.2 工作量审核回写

审核回写是工作量管理的核心环节，复用现有 `reviewProgress()` 审核流程，在审核通过分支增加工作量累加逻辑：

```
项目经理审核（reviewProgress）
  ├── 校验日志为 pending 状态（防重复审核）
  ├── 审核通过分支：
  │     ├── 标记日志 approved
  │     ├── 回写 task.progress
  │     ├── 联动任务状态（100%→已完成, >0→进行中）
  │     ├── 【新增】actualWorkload = actualWorkload.add(workloadHours)
  │     │     └── BigDecimal 精确累加，避免 double 精度漂移
  │     ├── 同步项目整体进度（syncProjectProgress）
  │     └── 通知填报人审核通过
  └── 审核驳回分支：
        ├── 仅更新日志状态为 rejected
        └── actualWorkload 不累加（驳回的工时不计入实际投入）

convertToVO（TaskServiceImpl.convertToVO）
  └── 透传 plannedWorkload / actualWorkload / workloadUnit 至 TaskVO
```

**关键设计评价：**
- 审核回写复用现有审核流程，仅在通过分支增加累加逻辑，改动侵入性小
- 驳回的工时不累加，确保 `actualWorkload` 仅反映已审核通过的真实投入
- 使用 `BigDecimal.add()` 精确累加，工时数据多次审核回写不会产生精度漂移

**潜在并发风险：**

`actualWorkload` 累加采用读-改-写模式，当两个项目经理（或同一PM的并发请求）同时审核同一任务的不同进度日志时，可能发生丢失更新：

```java
// 事务A：读取 actualWorkload = 10.00
BizTask taskA = taskMapper.selectById(taskId);  // 10.00
taskA.setActualWorkload(taskA.getActualWorkload().add(new BigDecimal("2.00")));  // 12.00

// 事务B（并发）：读取 actualWorkload = 10.00
BizTask taskB = taskMapper.selectById(taskId);  // 10.00
taskB.setActualWorkload(taskB.getActualWorkload().add(new BigDecimal("3.00")));  // 13.00

// 事务A先提交：actualWorkload = 12.00
// 事务B后提交：actualWorkload = 13.00（覆盖了事务A的 2.00，丢失更新）
```

> 该问题已记录至问题清单 H5，建议添加 `@Version` 乐观锁或分布式锁。

#### 2.7.3 工作量偏差检测

偏差检测在 `DashboardServiceImpl.getRiskBoard()` 中实现，遍历任务列表执行偏差判定算法：

```
风险看板（DashboardServiceImpl.getRiskBoard）
  ├── 查询可见项目下的任务列表
  ├── 遍历每个任务执行偏差判定：
  │     ├── plannedWorkload 为 NULL 或 0 → 跳过（无基准不判定）
  │     ├── status == '已完成' → 跳过（已完成不预警）
  │     ├── 计算 threshold = plannedWorkload × 0.8
  │     │     └── new BigDecimal("0.8") 构造，避免 double 精度损失
  │     ├── actualWorkload < threshold → 标记为偏差任务
  │     └── 计算偏差率 = actualWorkload.divide(plannedWorkload, 4, RoundingMode.HALF_UP)
  ├── 收集偏差任务列表
  └── 填充统计卡片（偏差任务数/偏差率分布/影响项目数）

角色差异化数据填充（DashboardServiceImpl.getSummary）
  ├── admin 角色：
  │     └── 调用 selectGlobalWorkloadStatistics() 全局聚合未归档项目
  └── PM 角色：
        └── 调用 selectWorkloadStatistics(管理项目ID集合) 聚合自己负责项目
```

**偏差判定条件：**

```
actualWorkload < plannedWorkload × 0.8  AND  status != '已完成'
```

**关键设计评价：**
- 偏差检测在 Dashboard 服务层完成，复用现有风险看板数据查询路径
- 阈值 `0.8` 通过 `new BigDecimal("0.8")` 构造字符串构造，避免 `0.8d` 的 double 精度问题
- 偏差率使用 `divide(planned, 4, RoundingMode.HALF_UP)`，4 位小数 + 四舍五入，展示稳定
- 角色差异化填充复用现有数据隔离策略，admin 全局聚合、PM 管理项目聚合，避免越权
- `@Cacheable` 复用 `dashboardSummary` 缓存空间（TTL 60s），工作量数据同步延迟最多 60s

#### 2.7.4 工作量聚合统计

`BizTaskMapper` 新增两个聚合方法，通过单条 SQL 完成聚合，避免 Java 端循环计算：

```
selectWorkloadStatistics(projectIds)
  └── SELECT SUM(planned_workload), SUM(actual_workload), COUNT(*)
      FROM biz_task WHERE project_id IN (#{projectIds})
      └── 用于 PM 工作台 / 项目统计页

selectGlobalWorkloadStatistics()
  └── SELECT SUM(planned_workload), SUM(actual_workload), COUNT(*)
      FROM biz_task t
      JOIN biz_project p ON t.project_id = p.id
      WHERE p.is_archived = 0
      └── 用于 admin 工作台全局聚合
```

**日报导出工作量列：**

`DailyReportServiceImpl` 日报 Excel 任务进度表格新增 3 列工作量数据（计划工作量/实际工作量/单位），复用 EasyExcel 注解驱动导出。

**关键设计评价：**
- 聚合查询使用单条 SQL，避免 Java 端循环累加，性能更优
- 全局聚合通过 JOIN 项目表过滤已归档项目，避免历史归档项目污染统计
- 日报工作量列复用现有 EasyExcel 导出能力，无需额外模板配置

---

## 3. 前后端数据流转路径

### 3.1 请求流（前端 → 后端）

```
用户操作
  → Vue组件调用API（api/*.js）
  → Axios请求拦截器
    ├── FormData请求：删除Content-Type
    ├── 非skipCancel请求：addPending（AbortController）
    └── 注入 Authorization: Bearer {token}
  → 后端JwtAuthInterceptor
    ├── OPTIONS放行
    ├── 校验Bearer Token
    ├── 检查黑名单（本地缓存+Redis）
    └── 设置UserContext（ThreadLocal）
  → PermissionAspect
    ├── 检查@RequiresPermission
    ├── admin角色豁免
    └── 岗位职级校验
  → Controller
  → Service（@Transactional）
  → Mapper（MyBatis Plus）
  → MySQL
```

### 3.2 响应流（后端 → 前端）

```
Service返回结果
  → Controller包装为Result<T>
    { code: 200, message: "success", data: T }
  → GlobalExceptionHandler（异常时）
    ├── BusinessException：返回对应错误码
    ├── MethodArgumentNotValidException：返回参数校验错误
    └── 其他异常：返回500（不暴露堆栈）
  → Axios响应拦截器
    ├── Blob响应：检测application/json错误体
    ├── code=200：返回res
    ├── code=401/2001/2002：Token刷新协调器
    ├── code=2003：无操作权限提示
    └── 其他：ElMessage.error提示
  → Vue组件渲染
```

### 3.3 实时数据流（WebSocket）

```
后端触发通知
  → NotificationService.sendNotification()
  → 写入sys_notification表
  → WebSocketPushService.pushToUser()
  → STOMP消息代理 /user/{userId}/queue/notifications
  → 前端websocket.js接收
  → 更新通知未读数
  → 30秒轮询兜底
```

---

## 4. 接口设计合理性与安全性

### 4.1 API设计规范

| 维度 | 评估 | 说明 |
|------|------|------|
| URL规范 | ✅ 良好 | RESTful风格，/api前缀由context-path统一配置 |
| HTTP方法 | ✅ 良好 | GET查询/POST创建/PUT更新/DELETE删除语义正确 |
| 参数校验 | ✅ 良好 | DTO使用@NotBlank/@Pattern/@Size等注解 |
| 权限注解 | ✅ 良好 | @RequiresPermission + @RateLimit + @OperationLog组合使用 |
| 响应格式 | ✅ 良好 | 统一Result<T>封装，code/message/data三段式 |
| 错误码 | ✅ 良好 | 业务错误码体系完整（2001/2002/2003/...） |

### 4.2 安全机制评估

| 安全维度 | 评估 | 说明 |
|----------|------|------|
| SQL注入 | ✅ 安全 | 全部使用MyBatis-Plus LambdaQueryWrapper，无${}拼接 |
| XSS防护 | ⚠️ 一般 | 前端无v-html使用，但Token存储在localStorage（XOR混淆非真正加密） |
| CSRF防护 | ℹ️ 不适用 | 当前使用Bearer Token认证，非Cookie认证，CSRF风险低 |
| 文件上传 | ✅ 安全 | 扩展名白名单+Magic number+路径穿越三重防护+UUID重命名 |
| 越权访问 | ✅ 已修复 | 任务级数据隔离完善，工程师仅可见自己填报的进度日志 |
| 敏感信息泄露 | ✅ 已修复 | GlobalExceptionHandler不暴露堆栈，PermissionAspect参数脱敏 |
| 限流防刷 | ✅ 完善 | 登录接口+文件上传接口已添加@RateLimit |
| 幂等控制 | ✅ 完善 | @Idempotent基于Redis SETNX，业务异常时删除key允许重试 |
| Token黑名单 | ✅ 完善 | 登出时写入Redis黑名单，拦截器本地缓存+Redis双重检查 |

### 4.3 IP伪造防护

[RateLimitAspect.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/aspect/RateLimitAspect.java#L48-L54) 实现了受信代理白名单机制：
- 默认使用Servlet容器感知的remoteAddr（TCP直连IP，不可伪造）
- 仅当remoteAddr命中配置的白名单时，才信任X-Forwarded-For
- 有效防止攻击者伪造X-Forwarded-For绕过限流

---

## 5. 问题清单

### 5.1 高优先级问题

| 编号 | 模块 | 问题描述 | 风险等级 | 状态 |
|------|------|----------|----------|------|
| H1 | 项目删除 | 删除项目时未清理关联的项目组、授权记录、设计图纸、归档记录等 | 中 | 待处理 |
| H2 | 任务删除 | 删除任务时未校验子任务存在性（parentId关联） | 中 | 待处理 |
| H3 | 项目更新 | 更新时仅校验dto中同时传入两个日期的情况，若只传startDate且早于已有endDate则不校验 | 低 | 已知限制 |
| H4 | 文档版本 | 新增版本时未校验版本号唯一性，可能产生重复版本号 | 低 | 待处理 |
| H5 | 工作量管理 | `reviewProgress()` 审核 `actualWorkload` 累加采用读-改-写模式，并发审核同一任务的不同进度日志时存在丢失更新风险 | 中 | 待处理 |

**H1 详细说明：**

[ProjectServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/ProjectServiceImpl.java#L340-L366) 的 delete 方法仅校验了任务和文档，但未清理：
- `biz_project_team`（项目组成员）
- `biz_project_assignment`（项目授权记录）
- `biz_design_drawing`（设计图纸）
- `biz_archive_record`（归档记录）
- `biz_project_auth_request`（授权申请）

建议：删除项目时级联清理或软删除关联数据。

**H5 详细说明：**

[TaskServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/TaskServiceImpl.java) 的 `reviewProgress()` 方法在审核通过时执行工作量累加：

```java
BizTask task = taskMapper.selectById(taskId);
task.setActualWorkload(task.getActualWorkload().add(workloadHours));
taskMapper.updateById(task);  // 覆盖写
```

该读-改-写模式在并发场景下存在丢失更新风险：当两个事务同时审核同一任务的不同进度日志时，后提交的事务会覆盖先提交事务的累加结果。项目表已通过 `@Version` 乐观锁解决类似问题，但 `BizTask` 表当前未启用乐观锁。

建议：为 `BizTask` 添加 `@Version` 乐观锁字段，审核回写时使用 `UPDATE ... SET actual_workload = actual_workload + ? WHERE id = ? AND version = ?` 原子累加，或对同一任务的审核操作加分布式锁。

### 5.2 中优先级问题

| 编号 | 模块 | 问题描述 | 风险等级 |
|------|------|----------|----------|
| M1 | 前端Token存储 | localStorage + XOR混淆无法真正防御XSS窃取Token | 中 |
| M2 | 通知WebSocket推送 | 批量通知方法中WebSocket推送仍为逐个循环推送，大量通知时可能阻塞 | 低 |
| M3 | 项目进度同步 | syncProjectProgress使用AVG计算，若项目下任务为0则AVG返回NULL，需确认数据库处理 | 低 |
| M4 | 操作日志 | 部分Controller方法缺少@OperationLog注解，操作审计不完整 | 低 |
| M5 | 前端路由守卫 | cancelAllPending在每次路由切换时取消所有请求，可能取消正在进行的必要请求（如系统配置） | 低 |

**M3 详细说明：**

[TaskServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/TaskServiceImpl.java#L688-L692) 调用 `taskMapper.updateProjectProgressByTaskAvg(projectId)`，当项目下所有任务被删除后，AVG返回NULL，需确认SQL是否使用 `IFNULL(AVG(progress), 0)` 兜底。

### 5.3 低优先级问题

| 编号 | 模块 | 问题描述 |
|------|------|----------|
| L1 | 日期校验 | 更新时交叉校验不完整（只传一个日期时不与已有值比较） |
| L2 | 文档编号 | 文档编号使用 `DOC + System.currentTimeMillis()`，并发时可能重复 |
| L3 | 前端权限 | 路由守卫权限校验在前端完成，理论上可被绕过（后端有@RequiresPermission兜底） |
| L4 | 缓存TTL | 角色/权限缓存TTL为2小时，期间权限变更不实时生效（需登出重新登录） |
| L5 | WebSocket重连 | 指数退避最大10次，长时间网络中断后不会自动重连 |

---

## 6. 优化建议

### 6.1 安全类优化

**S1：项目删除级联清理**

```java
// 在 ProjectServiceImpl.delete() 中添加级联清理
// 方案一：硬删除关联数据
projectTeamMapper.deleteByProjectId(id);
assignmentMapper.deleteByProjectId(id);
drawingMapper.deleteByProjectId(id);
// 方案二（推荐）：软删除项目（is_deleted=1），保留数据可恢复
```

**S2：任务删除子任务校验**

```java
// 在 TaskServiceImpl.delete() 中添加子任务校验
long subTaskCount = taskMapper.selectCount(
    new LambdaQueryWrapper<BizTask>().eq(BizTask::getParentId, id));
if (subTaskCount > 0) {
    throw new BusinessException(ResultCode.BUSINESS_ERROR, "存在子任务，无法删除");
}
```

**S3：文档版本号唯一性校验**

```java
// 在 DocumentServiceImpl.addVersion() 中添加版本号校验
long versionExists = versionMapper.selectCount(
    new LambdaQueryWrapper<BizDocumentVersion>()
        .eq(BizDocumentVersion::getDocumentId, id)
        .eq(BizDocumentVersion::getVersion, dto.getVersion()));
if (versionExists > 0) {
    throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "版本号已存在");
}
```

### 6.2 性能类优化

**P1：syncProjectProgress SQL兜底**

```sql
-- 确保AVG结果为NULL时返回0
UPDATE biz_project p
SET p.progress = IFNULL(
    (SELECT AVG(t.progress) FROM biz_task t WHERE t.project_id = p.id), 0)
WHERE p.id = #{projectId}
```

**P2：WebSocket批量推送优化**

当前 `sendNotifications` 方法中WebSocket推送仍为逐个循环，大量通知时可能阻塞通知写入。建议改为异步推送：

```java
// 使用 @Async 异步推送
@Async
public void pushBatch(List<Long> userIds, NotificationVO vo) {
    userIds.parallelStream().forEach(userId -> pushToUser(userId, vo));
}
```

**P3：文档编号生成优化**

```java
// 使用 UUID + 序列号替代时间戳
document.setDocumentCode("DOC-" + UUID.randomUUID().toString().substring(0, 8));
// 或使用数据库序列
```

**P4：工作量累加原子化（对应 H5）**

当前 `reviewProgress()` 审核回写 `actualWorkload` 采用读-改-写模式，存在并发丢失更新风险。建议复用项目表已有的 `@Version` 乐观锁机制，改为原子累加：

```java
// 方案一（推荐）：BizTask 添加 @Version 字段 + 原子累加 SQL
@Update("UPDATE biz_task SET actual_workload = actual_workload + #{workloadHours}, " +
        "version = version + 1 WHERE id = #{taskId} AND version = #{version}")
int updateActualWorkloadAtomic(@Param("taskId") Long taskId,
                               @Param("workloadHours") BigDecimal workloadHours,
                               @Param("version") Integer version);
// 返回0时提示用户刷新重试，与项目更新乐观锁处理一致

// 方案二：Redisson 分布式锁（短期过渡）
RLock lock = redissonClient.getLock("task:review:" + taskId);
try {
    if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
        // 执行读-改-写累加逻辑
    }
} finally {
    if (lock.isHeldByCurrentThread()) lock.unlock();
}
```

### 6.3 代码质量类优化

**Q1：更新时日期交叉校验完善**

```java
// 合并 dto 日期和已有日期做交叉校验
LocalDate effectiveStart = dto.getStartDate() != null ? dto.getStartDate() : project.getStartDate();
LocalDate effectiveEnd = dto.getEndDate() != null ? dto.getEndDate() : project.getEndDate();
if (effectiveStart != null && effectiveEnd != null && effectiveStart.isAfter(effectiveEnd)) {
    throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
}
```

**Q2：前端路由守卫取消请求优化**

```javascript
// skipCancel 标记已经在 request.js 中支持，确保关键请求（如系统配置）标记 skipCancel: true
// 已在 systemConfig 请求中实现，其他关键请求也应注意标记
```

**Q3：操作日志补全**

为以下关键操作添加 `@OperationLog` 注解：
- 项目归档操作
- 项目授权审批操作
- 任务进度审核操作
- 文档版本新增操作

### 6.4 架构演进建议

| 方向 | 当前状态 | 建议 |
|------|----------|------|
| Token存储 | localStorage + XOR混淆 | 长期计划迁移到HttpOnly Cookie |
| 缓存一致性 | TTL 2小时 + 登出清理 | 考虑权限变更时主动清除缓存 |
| WebSocket重连 | 最大10次指数退避 | 增加浏览器online事件监听重置计数 |
| 消息队列 | 无 | 通知/日志异步化使用MQ解耦 |
| 读写分离 | 单库 | 数据量增长后配置主从复制 |

---

## 附录：核心文件索引

### 后端

| 文件 | 职责 |
|------|------|
| [ProjectServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/ProjectServiceImpl.java) | 项目管理（创建/更新/删除/归档） |
| [TaskServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/TaskServiceImpl.java) | 任务进度（填报/审核/进度同步/工作量基准与回写） |
| [DashboardServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/DashboardServiceImpl.java) | 工作台（概览/风险看板/工作量统计与偏差检测） |
| [DailyReportServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/DailyReportServiceImpl.java) | 日报导出（Excel含工作量列） |
| [ProjectAuthServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/ProjectAuthServiceImpl.java) | 项目授权（申请/审批/驳回） |
| [DocumentServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/DocumentServiceImpl.java) | 文档管理（CRUD/版本/安全删除） |
| [NotificationServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/NotificationServiceImpl.java) | 通知服务（单条/批量发送） |
| [JwtAuthInterceptor.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/interceptor/JwtAuthInterceptor.java) | JWT认证拦截器 |
| [PermissionAspect.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/aspect/PermissionAspect.java) | 权限校验切面 |
| [RateLimitAspect.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/aspect/RateLimitAspect.java) | 限流切面 |
| [IdempotentAspect.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/aspect/IdempotentAspect.java) | 幂等切面 |
| [GlobalExceptionHandler.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/common/exception/GlobalExceptionHandler.java) | 全局异常处理 |
| [FileStorageServiceImpl.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/FileStorageServiceImpl.java) | 文件存储安全 |
| [TaskDueNotificationScheduler.java](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/scheduler/TaskDueNotificationScheduler.java) | 定时任务通知 |

### 前端

| 文件 | 职责 |
|------|------|
| [request.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/utils/request.js) | Axios封装（拦截器/Token刷新） |
| [router/index.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/router/index.js) | 路由配置（权限守卫） |
| [stores/user.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/stores/user.js) | 用户状态管理 |
| [websocket.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/utils/websocket.js) | WebSocket连接 |
| [cryptoStorage.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/utils/cryptoStorage.js) | 加密存储 |
| [permission.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/utils/permission.js) | 权限校验 |
