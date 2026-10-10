# IDC数据中心综合布线项目管理系统 - 技术分析文档

## 目录

1. [系统概述](#1-系统概述)
2. [系统架构](#2-系统架构)
3. [技术栈与依赖组件](#3-技术栈与依赖组件)
4. [数据模型与数据库架构](#4-数据模型与数据库架构)
5. [认证授权体系](#5-认证授权体系)
6. [缓存策略与实现机制](#6-缓存策略与实现机制)
7. [API接口设计规范](#7-api接口设计规范)
8. [AOP切面与定时任务](#8-aop切面与定时任务)
9. [WebSocket实时推送](#9-websocket实时推送)
10. [文件上传安全机制](#10-文件上传安全机制)
11. [核心业务模块实现](#11-核心业务模块实现)
12. [工作量管理模块](#12-工作量管理模块)
13. [前端组件结构与交互逻辑](#13-前端组件结构与交互逻辑)
14. [安全措施](#14-安全措施)
15. [性能优化方案](#15-性能优化方案)
16. [潜在问题与改进建议](#16-潜在问题与改进建议)

---

## 1. 系统概述

**IDC数据中心综合布线项目管理系统**是一套面向数据中心工程项目管理的综合管理平台，涵盖项目全生命周期管理、任务进度追踪、文档管理、客户管理、统计分析等核心功能。系统采用前后端分离架构，支持Web端管理后台与微信小程序端工程师协同。

**核心业务目标：**

- 项目全生命周期管理（立项→执行→监控→归档）
- 任务进度填报与审核流程
- 项目协作与授权管理
- 文档与设计图纸管理
- 数据统计与日报导出
- 实时通知与关键节点提醒

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端层 (Client Tier)                        │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────┐  │
│  │   Web 管理后台    │    │  微信小程序端     │    │  移动端(H5)   │  │
│  │  Vue3 + Element  │    │   UniApp         │    │              │  │
│  └────────┬─────────┘    └────────┬─────────┘    └──────────────┘  │
│           │                       │                                 │
└───────────┼───────────────────────┼─────────────────────────────────┘
            │                       │
            ▼                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        接入层 (Gateway Tier)                         │
│                     Nginx 反向代理 + 负载均衡                        │
│              HTTPS 终止 / 请求限流 / 静态资源托管                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      应用服务层 (Application Tier)                   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │           Spring Boot 3.0.x 应用集群                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐ │   │
│  │  │ Auth API │ │ Project  │ │ Task API │ │ Document API  │ │   │
│  │  │ 用户认证  │ │ 项目管理  │ │ 任务管理  │ │ 文档管理      │ │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └───────────────┘ │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐ │   │
│  │  │ Customer │ │ Approval │ │ Notify   │ │ Statistics    │ │   │
│  │  │ 客户管理  │ │ 审核流程  │ │ 通知服务  │ │ 统计分析      │ │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └───────────────┘ │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                           │         │         │                     │
│                           ▼         ▼         ▼                     │
│                ┌─────────────┐ ┌───────────┐ ┌─────────────┐        │
│                │   AOP 切面   │ │ 拦截器     │ │ WebSocket   │        │
│                │ 权限/日志/   │ │ JWT认证    │ │ 实时推送    │        │
│                │ 限流/幂等    │ │            │ │             │        │
│                └─────────────┘ └───────────┘ └─────────────┘        │
└─────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   Redis 缓存  │         │   MySQL 数据库   │         │   本地文件存储   │
│   (会话/权限  │         │   (业务数据)     │         │   (上传文档)     │
│   /配置/限流) │         │                 │         │                 │
└───────────────┘         └─────────────────┘         └─────────────────┘
```

### 2.2 模块划分

| 模块        | 说明                 | 核心文件                                                                            |
| --------- | ------------------ | ------------------------------------------------------------------------------- |
| **认证模块**  | JWT认证、登录登出、Token刷新 | `JwtAuthInterceptor`, `AuthServiceImpl`, `user.js`                              |
| **权限模块**  | 权限校验、角色管理、数据隔离     | `PermissionAspect`, `PermissionServiceImpl`                                     |
| **项目模块**  | 项目CRUD、归档、批量导入导出   | `ProjectServiceImpl`, `ProjectController`                                       |
| **任务模块**  | 任务管理、进度填报、审核流程、工作量管理 | `TaskServiceImpl`, `TaskController`                                             |
| **文档模块**  | 文档管理、文件上传下载        | `DocumentServiceImpl`, `FileStorageServiceImpl`                                 |
| **客户模块**  | 客户管理、批量导入导出        | `CustomerServiceImpl`, `CustomerController`                                     |
| **通知模块**  | 站内通知、WebSocket推送   | `NotificationServiceImpl`, `WebSocketConfig`                                    |
| **统计模块**  | 数据统计、日报导出、工作量聚合    | `StatisticsServiceImpl`, `DailyReportController`, `DashboardServiceImpl`        |
| **AOP切面** | 限流、幂等、权限、操作日志      | `RateLimitAspect`, `IdempotentAspect`, `PermissionAspect`, `OperationLogAspect` |
| **定时任务**  | 任务到期通知、授权过期回收      | `TaskDueNotificationScheduler`, `AssignmentExpireTask`                          |

---

## 3. 技术栈与依赖组件

### 3.1 后端技术栈

| 类别        | 技术               | 版本      | 用途        |
| --------- | ---------------- | ------- | --------- |
| 框架        | Spring Boot      | 3.0.2   | 应用框架      |
| ORM       | MyBatis-Plus     | 3.5.3.1 | 数据访问      |
| 数据库       | MySQL            | 8.x     | 主数据库      |
| 缓存        | Redis            | 7.x     | 缓存/会话     |
| 连接池       | Druid            | 1.2.16  | 数据库连接池    |
| JWT       | jjwt             | 0.11.5  | 身份认证      |
| 文件处理      | EasyExcel        | 3.3.4   | Excel导入导出 |
| API文档     | Knife4j          | 4.1.0   | Swagger增强 |
| 工具集       | Hutool           | 5.8.22  | 工具类库      |
| WebSocket | Spring WebSocket | 3.0.x   | 实时推送      |

### 3.2 前端技术栈

| 类别        | 技术                 | 版本          | 用途    |
| --------- | ------------------ | ----------- | ----- |
| 框架        | Vue                | 3.4.21      | UI框架  |
| 构建工具      | Vite               | 5.2.0       | 构建工具  |
| 路由        | Vue Router         | 4.3.0       | 路由管理  |
| 状态管理      | Pinia              | 2.1.7       | 状态管理  |
| UI组件      | Element Plus       | 2.6.2       | UI组件库 |
| 图标        | Element Plus Icons | 2.3.1       | 图标组件  |
| HTTP客户端   | Axios              | 1.18.1      | 网络请求  |
| 图表        | ECharts            | 5.5.0       | 数据可视化 |
| 日期处理      | Dayjs              | 1.11.10     | 日期处理  |
| WebSocket | STOMP + SockJS     | 7.0.0/1.6.1 | 实时通信  |

### 3.3 小程序技术栈

- **框架**: UniApp (Vue3)
- **UI组件**: UniApp内置组件
- **网络请求**: uni.request

---

## 4. 数据模型与数据库架构

### 4.1 数据库表结构

**系统基础表：**

| 表名                    | 说明     | 关键字段                                               |
| --------------------- | ------ | -------------------------------------------------- |
| `sys_user`            | 用户信息   | id, username, password, name, positionId, status   |
| `sys_role`            | 角色信息   | id, roleCode, roleName, description                |
| `sys_permission`      | 权限信息   | id, permissionCode, permissionName, parentId       |
| `sys_user_role`       | 用户角色关联 | userId, roleId                                     |
| `sys_role_permission` | 角色权限关联 | roleId, permissionId                               |
| `sys_position`        | 岗位信息   | id, name, code, level, sort                        |
| `sys_notification`    | 通知消息   | id, userId, title, content, isRead, bizType, bizId |
| `sys_operation_log`   | 操作日志   | id, userId, operation, target, errorMsg            |
| `sys_system_config`   | 系统配置   | id, configKey, configValue, description            |

**业务表：**

| 表名                         | 说明   | 关键字段                                                                      |
| -------------------------- | ---- | ------------------------------------------------------------------------- |
| `biz_project`              | 项目信息 | id, projectCode, name, customerId, managerId, status, progress, version   |
| `biz_task`                 | 任务信息 | id, projectId, name, assigneeId, progress, status, isMilestone, isKeyTask, plannedWorkload, actualWorkload, workloadUnit |
| `biz_task_progress_log`    | 进度日志 | id, taskId, projectId, progress, reporterId, reviewStatus, workloadHours, workloadDescription           |
| `biz_project_assignment`   | 项目授权 | id, projectId, engineerId, permissionScope, isActive                      |
| `biz_project_team`         | 项目组  | id, projectId, leaderId                                                   |
| `biz_project_auth_request` | 授权请求 | id, projectId, requesterId, status                                        |
| `biz_project_transfer_log` | 转移日志 | id, projectId, fromUserId, toUserId                                       |
| `biz_customer`             | 客户信息 | id, customerCode, name, contactPerson, contactPhone                       |
| `biz_document`             | 文档信息 | id, projectId, name, filePath, fileSize                                   |
| `biz_design_drawing`       | 设计图纸 | id, projectId, name, fileSize, fileSizeBytes                              |
| `biz_archive_record`       | 归档记录 | id, projectId, operationType, archiveDate                                 |

### 4.2 实体关系图

```
sys_user ──(1:N)──> sys_user_role <──(1:N)── sys_role
sys_role ──(1:N)──> sys_role_permission <──(1:N)── sys_permission
sys_user ──(1:1)──> sys_position (positionId)

biz_project ──(1:N)──> biz_task
biz_project ──(1:N)──> biz_document
biz_project ──(1:N)──> biz_design_drawing
biz_project ──(1:1)──> biz_customer (customerId)
biz_project ──(1:N)──> biz_project_assignment
biz_project ──(1:N)──> biz_project_auth_request
biz_project ──(1:N)──> biz_project_transfer_log
biz_project ──(1:N)──> biz_archive_record

biz_task ──(1:N)──> biz_task_progress_log
biz_task ──(1:1)──> sys_user (assigneeId)

sys_user ──(1:N)──> sys_notification
sys_user ──(1:N)──> sys_operation_log
```

### 4.3 数据库迁移

系统使用 Flyway 进行数据库迁移，迁移脚本位于 `src/main/resources/db/migration/`：

| 迁移文件                                               | 说明           |
| -------------------------------------------------- | ------------ |
| `V20260704__backfill_manager_assignee_ids.sql`     | 回填项目经理和负责人ID |
| `V20260704__project_authorization_transfer.sql`    | 项目授权转移       |
| `V20260705__organization_architecture_upgrade.sql` | 组织架构升级       |
| `V20260712__add_optimistic_lock_version.sql`       | 添加乐观锁版本字段    |
| `V20260715__add_task_workload_fields.sql`          | 新增任务工作量字段（幂等设计） |

---

## 5. 认证授权体系

### 5.1 JWT认证流程

```
用户登录 ──> AuthController.login() ──> 验证用户名密码 ──> 生成JWT ──> 返回token + refreshToken + userInfo
    │
    ▼
后续请求 ──> JwtAuthInterceptor.preHandle()
                ├─ 检查OPTIONS预检请求 ──> 直接放行
                ├─ 检查Authorization头 ──> 缺失则401
                ├─ 检查Token黑名单 ──> 在黑名单则401
                ├─ 解析Token ──> 过期则401
                └─ 写入UserContext(ThreadLocal) ──> 放行

请求结束 ──> JwtAuthInterceptor.afterCompletion() ──> 清理UserContext
```

**JWT配置要点：**

- Access Token 有效期：30分钟（安全修复，原24小时）
- Refresh Token 有效期：7天
- 支持 issuer/audience 校验（防跨服务token误用）
- Token登出时加入Redis黑名单

### 5.2 权限校验机制

**双重权限校验：**

1. **路由级别校验**（前端）：`router.beforeEach` 拦截器检查 `meta.permission`
2. **方法级别校验**（后端）：`@RequiresPermission` 注解 + `PermissionAspect` 切面

**权限表达式：**

- `permission: 'project:query'` - 单权限校验
- `permission: ['project:query', 'admin']` - 数组表示满足任一即可
- `@RequiresPermission(value = {"a", "b"}, logicalAnd = true)` - AND关系

**超级管理员豁免：**

- 超级管理员（`role_code=admin`）直接返回 `[*]` 权限，跳过所有校验
- `PermissionAspect` 中检查角色码后直接放行

### 5.3 数据隔离策略

| 用户角色  | 数据可见性       |
| ----- | ----------- |
| 超级管理员 | 全部数据        |
| 项目经理  | 全部项目（全局只读）  |
| 工程师   | 仅可见被显式授权的项目 |
| 普通用户  | 仅可见被授权的项目   |

**实现方式：**

- 通过 `IProjectAuthorizationService.getCurrentUserVisibleProjectIds()` 获取可见项目列表
- 在SQL查询时注入 `WHERE id IN (visibleProjectIds)` 条件
- 非管理员且无 `statistics:query` 权限时启用数据隔离

---

## 6. 缓存策略与实现机制

### 6.1 Redis配置

**连接池配置：**

- max-active: 8
- max-idle: 8
- min-idle: 0
- max-wait: 2000ms（安全修复，原-1无限等待）

**序列化策略：**

- Key: StringRedisSerializer
- Value: GenericJackson2JsonRedisSerializer（带类型信息）

### 6.2 缓存策略

**分层TTL策略：**

| 缓存空间                       | TTL        | 用途         |
| -------------------------- | ---------- | ---------- |
| 默认                         | 30分钟       | 通用缓存       |
| `dashboardSummary`         | 60秒        | 工作台概览（需实时） |
| `dictData`                 | 1小时        | 字典数据（变化少）  |
| `permissionTree`           | 10分钟       | 权限树（变更较频繁） |
| `userProfile`              | 15分钟       | 用户信息       |
| `hbsz:user:perms:{userId}` | 7200秒(2小时) | 用户权限列表     |
| `hbsz:user:roles:{userId}` | 7200秒(2小时) | 用户角色列表     |

**缓存失效策略：**

- 权限/角色变更时主动清除用户缓存（`PermissionServiceImpl.clearUserCache`）
- 使用 SCAN 而非 KEYS 批量清除（避免阻塞Redis主线程）
- 登出时主动清除缓存

### 6.3 黑名单缓存优化

**Token黑名单本地缓存：**

- 仅缓存"在黑名单中"（true）的结果，避免登出绕过
- TTL: 60秒
- 容量阈值: 1024，超过时惰性清理
- 使用 `ConcurrentHashMap` 保证线程安全

---

## 7. API接口设计规范

### 7.1 接口命名规范

| HTTP方法 | 用途      | 示例                                          |
| ------ | ------- | ------------------------------------------- |
| GET    | 查询列表/详情 | `/api/v1/projects`, `/api/v1/projects/{id}` |
| POST   | 新增      | `/api/v1/projects`                          |
| PUT    | 更新      | `/api/v1/projects/{id}`                     |
| DELETE | 删除      | `/api/v1/projects/{id}`                     |
| POST   | 批量导入    | `/api/v1/projects/import`                   |
| GET    | 导出模板    | `/api/v1/projects/import/template`          |

### 7.2 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1699999999999
}
```

### 7.3 错误码规范

| 错误码 | 含义     | HTTP状态码 |
| --- | ------ | ------- |
| 200 | 成功     | 200     |
| 400 | 请求参数错误 | 400     |
| 401 | 未授权    | 401     |
| 403 | 无权限    | 403     |
| 404 | 数据不存在  | 404     |
| 500 | 系统错误   | 500     |

### 7.4 版本控制

- 接口前缀 `/api/v1/`
- 通过 `ApiVersionFilter` 实现版本控制
- 支持响应头返回API版本

---

## 8. AOP切面与定时任务

### 8.1 AOP切面体系

系统使用 Spring AOP 实现四个核心切面，通过注解驱动的方式实现横切关注点的解耦：

#### 8.1.1 权限校验切面（PermissionAspect）

**功能**：拦截标注了 `@RequiresPermission` 的方法，执行功能权限校验与岗位职级校验。

**校验流程**：

```
请求进入 ──> 获取注解权限配置 ──> 检查超级管理员豁免 ──> 校验功能权限 ──> 校验岗位职级 ──> 放行/拒绝
```

**关键特性**：

- 支持 AND/OR 逻辑组合（`logicalAnd` 参数）
- 岗位职级校验（`requirePosition` 参数）
- 参数脱敏（避免 DTO 的 toString 泄露敏感字段）
- 日志分级：常规路径 DEBUG，拒绝事件 WARN，异常 ERROR

**注解示例**：

```java
@RequiresPermission("project:create")
@RequiresPermission(value = {"a", "b"}, logicalAnd = true, requirePosition = {"PM", "ADMIN"})
```

#### 8.1.2 限流切面（RateLimitAspect）

**功能**：基于 Redis 计数实现固定时间窗口限流，保护接口在高并发下不被刷爆。

**实现原理**：

```
请求进入 ──> 构建限流key（方法+用户/IP） ──> Redis INCR计数 ──> 判断是否超限 ──> 放行/拒绝
```

**关键特性**：

- 限流维度：已登录按 userId，未登录按客户端 IP
- 安全修复：仅信任配置的代理 IP 白名单，防止伪造 X-Forwarded-For 绕过限流
- Redis 键前缀：`hbsz:rate_limit:`

**注解示例**：

```java
@RateLimit(key = "login", period = 60, count = 10)
```

#### 8.1.3 幂等切面（IdempotentAspect）

**功能**：基于 Redis + 请求头 `X-Request-Id` 实现接口幂等，防止重复提交。

**实现原理**：

```
请求进入 ──> 获取 X-Request-Id ──> 构建幂等key ──> Redis SETNX占位 ──> 占位成功放行/失败拒绝
```

**关键特性**：

- 业务异常时删除幂等 key，允许重试
- 成功后保留 key 直至过期（默认 5 分钟）
- Redis 键前缀：`hbsz:idempotent:`
- 用户隔离：幂等 key 包含 userId

**注解示例**：

```java
@Idempotent(key = "order_create", expire = 300, headerName = "X-Request-Id")
```

#### 8.1.4 操作日志切面（OperationLogAspect）

**功能**：自动采集操作模块、类型、请求 URL、IP、参数、耗时、执行状态等信息，持久化到数据库。

**关键特性**：

- 标注 `@OperationLog` 注解的方法自动记录
- 错误消息截断（最大 500 字符）
- 异步写入（不阻塞主流程）

### 8.2 定时任务

#### 8.2.1 任务到期通知（TaskDueNotificationScheduler）

**执行时间**：每日 08:00（`0 0 8 * * ?`）

**功能**：扫描未来 3 天内即将到期及已逾期未完成的任务，向负责人发送通知。

**优化策略**：

- **N+1 查询优化**：批量查询今日已发送通知构建去重集合，批量查询项目名称构建映射
- **去重机制**：同一任务同一天只通知一次

#### 8.2.2 授权过期回收（AssignmentExpireTask）

**执行时间**：每日 02:00（`0 0 2 * * ?`）

**功能**：扫描已过期但未回收的有效授权记录，自动回收权限并写入审计日志。

**关键修复**：

- 通过注入独立的 `AssignmentRevokeService` 调用 `revokeOne`，确保 `@Transactional` 注解生效（原同类内部调用绕过了 AOP 代理）

---

## 9. WebSocket实时推送

### 9.1 后端配置

**技术方案**：STOMP 协议 + SockJS 实现实时通知推送。

**配置要点**：

- 端点：`/ws`
- 消息代理前缀：`/topic`（广播）、`/queue`（点对点）
- 应用目的地前缀：`/app`
- 用户专属通道：`/user/{userId}/queue/notifications`

**安全机制**：

- **双层鉴权**：JwtHandshakeInterceptor（握手阶段）+ JwtChannelInterceptor（CONNECT 帧阶段）
- **Token传递**：通过 STOMP CONNECT 帧头 `Authorization` 传递，避免 URL 查询参数泄露
- **CORS白名单**：与 CorsConfig 复用同一配置

### 9.2 前端实现

**连接流程**：

```
登录成功 ──> connect(token, callback) ──> STOMP握手 ──> 订阅 /user/queue/notifications ──> 接收通知
```

**重连策略**：

- 指数退避：5s → 10s → 20s → 40s → 80s → 160s
- 最大重连次数：10 次
- 浏览器恢复在线时重置重连计数

**兜底机制**：

- 30 秒定时轮询未读数量，防止 WS 断连漏消息

### 9.3 通知投递流程

```
业务触发 ──> sendNotification(dto) ──> 检查用户通知偏好 ──> 写入 sys_notification ──> WebSocket推送
```

**容错设计**：

- 推送失败不影响主业务流程
- 用户离线时消息已落库，下次登录可见

---

## 10. 文件上传安全机制

### 10.1 服务端校验

**多层安全防护**：

| 校验层          | 措施                            | 说明                            |
| ------------ | ----------------------------- | ----------------------------- |
| 大小限制         | 100MB                         | 单文件最大 100MB                   |
| 扩展名白名单       | PDF/DOC/XLS/JPG/PNG/ZIP/DWG 等 | 仅允许指定类型                       |
| Magic number | 文件头校验                         | 防止伪装文件上传                      |
| 路径穿越防护       | 三重校验                          | 拒绝绝对路径、`..` 检测、normalize 前缀校验 |
| UUID重命名      | 随机文件名                         | 避免原始文件名泄露                     |

### 10.2 路径穿越防护详解

```java
// 1. 拒绝绝对路径
if (relativePath.startsWith("/") || relativePath.matches("^[A-Za-z]:.*")) {
    throw new BusinessException(...);
}
// 2. 拒绝包含 .. 的路径
if (relativePath.contains("..")) {
    throw new BusinessException(...);
}
// 3. normalize 后校验前缀
Path filePath = Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
if (!filePath.startsWith(basePath)) {
    throw new BusinessException(...);
}
```

### 10.3 文件存储结构

```
uploads/
└── yyyy/
    └── MM/
        └── dd/
            └── uuid.ext  (UUID文件名，无原始信息泄露)
```

---

## 11. 核心业务模块实现

### 11.1 项目管理模块

**核心功能：**

- 项目CRUD
- 批量导入导出（Excel）
- 项目归档/取消归档
- 项目经理变更（委托transferLeader）

**关键实现：**

```java
// 数据隔离
List<Long> visibleProjectIds = projectAuthorizationService.getCurrentUserVisibleProjectIds();

// 乐观锁并发控制
int updatedRows = projectMapper.updateById(project);
if (updatedRows == 0) {
    throw new BusinessException(ResultCode.BUSINESS_ERROR, "项目数据已被其他用户修改");
}

// N+1优化：批量填充设计图纸
fillDesignDrawingsBatch(result.getRecords());
```

### 11.2 任务进度模块

**核心流程：**

```
工程师提交进度 ──> reportProgress() ──> 创建pending状态日志 ──> 通知项目经理
    │
    ▼
项目经理审核 ──> reviewProgress()
    ├─ 审核通过 ──> 更新task.progress ──> 同步项目进度
    └─ 审核驳回 ──> 仅更新日志状态
```

**项目进度同步（原子操作）：**

```java
// 使用UPDATE子查询，消除并发覆盖
taskMapper.updateProjectProgressByTaskAvg(projectId);
```

**数据隔离策略：**

- 调用 `getCurrentUserVisibleTaskIds()` 获取可见任务列表
- null：不限制（admin/PM/高级工程师）
- 空列表：返回空数据（无权访问）
- 非空列表：用 IN 条件过滤

### 11.3 文档管理模块

**核心功能：**

- 文档CRUD、版本管理、标签管理
- 文件上传下载

**事务保障：**

- create/update/delete 均标注 `@Transactional(rollbackFor = Exception.class)`
- 保证文档主表、版本表、标签关联表多表写入的原子性

**性能优化：**

- 通过 `fillExtraInfoBatch` 批量加载版本与标签，规避 N+1 查询

### 11.4 通知模块

**通知类型：**

- progress：进度审核通知
- task_due：任务到期提醒
- doc_update：文档更新通知
- archive：项目归档通知
- system：系统通知

**通知优先级：**

- 1：低优先级
- 2：中优先级（即将到期）
- 3：高优先级（已逾期）

**用户通知偏好：**

- 用户可配置站内信、邮件、短信开关
- 可按通知类型单独开关

---

## 12. 工作量管理模块

> v1.1 新增模块。在原有任务进度管理基础上引入工作量基准与实际工时回写机制，实现项目工时量化跟踪与偏差预警，为项目管理提供数据驱动的决策依据。

### 12.1 模块定位与核心能力

**设计目标：**

- 量化任务工作量：通过 `plannedWorkload`（计划工作量基准）+ `workloadUnit`（单位：人时/人日/人月等）建立可度量基线
- 跟踪实际投入：进度审核通过时将 `workloadHours` 累加至任务 `actualWorkload`，形成真实投入曲线
- 偏差检测与预警：当实际工作量低于计划工作量 80% 且任务未完成时，自动标记为偏差任务并推送至风险看板
- 多维度统计：按项目/全局聚合工作量数据，支撑 Dashboard 概览与日报导出

**核心能力矩阵：**

| 能力点 | 实现位置 | 说明 |
|--------|----------|------|
| 基准设定 | `TaskServiceImpl.create/update`、`ProjectServiceImpl.importWorkload` | 创建任务或导入工作量模板时设置 `plannedWorkload` 基准 |
| 实际回写 | `TaskServiceImpl.reviewProgress` | 审核通过时 `actualWorkload += workloadHours`（BigDecimal 累加） |
| 偏差检测 | `DashboardServiceImpl.getRiskBoard` | `actualWorkload < plannedWorkload × 0.8 && status != '已完成'` |
| 聚合统计 | `BizTaskMapper.selectWorkloadStatistics/selectGlobalWorkloadStatistics` | 按项目ID集合/全局聚合 |
| 角色差异化填充 | `DashboardServiceImpl.getSummary` | admin 取全局未归档项目聚合，PM 取自己负责项目聚合 |

### 12.2 数据模型设计

**`biz_task` 表新增字段（V20260715 迁移）：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `planned_workload` | decimal(14,2) | NULL | 计划工作量基准（PM 设定） |
| `actual_workload` | decimal(14,2) | 0.00 | 实际累计工作量（审核回写累加） |
| `workload_unit` | varchar(20) | NULL | 工作量单位（人时/人日/人月） |

**`biz_task_progress_log` 表（已有字段复用）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `workload_hours` | decimal(10,2) | 本次填报工时（审核通过后累加至任务 actualWorkload） |
| `workload_description` | varchar(500) | 工作量描述（本次投入内容说明） |

**字段精度设计考量：**

- 任务级 `actualWorkload` 使用 `decimal(14,2)`，可承载百万级人时累加而不溢出
- 日志级 `workload_hours` 使用 `decimal(10,2)`，单次填报工时上限足够
- 迁移脚本采用幂等设计（`INFORMATION_SCHEMA.COLUMNS` 判存后再 ADD COLUMN），支持重复执行不报错

### 12.3 后端分层实现

#### 12.3.1 DTO/VO 层

| 类 | 新增字段 | 说明 |
|----|----------|------|
| `TaskSaveDTO` | `plannedWorkload`、`workloadUnit` | 任务创建/更新入参，PM 设定基准 |
| `TaskVO` | `plannedWorkload`、`actualWorkload`、`workloadUnit` | 任务详情回显，前端展示基准与累计实际 |
| `DashboardSummaryVO` | 工作量统计字段 | 工作台概览卡片数据 |
| `RiskBoardVO` | 工作量偏差任务列表/统计字段 | 风险看板偏差预警 |
| `ProjectStatisticsVO` | 工作量聚合字段 | 项目统计页工作量数据 |

#### 12.3.2 Service 层关键逻辑

**`TaskServiceImpl` —— 工作量生命周期管理：**

| 方法 | 工作量处理逻辑 |
|------|----------------|
| `create()` | 设置 `plannedWorkload` 基准，`actualWorkload` 初始化为 `BigDecimal.ZERO` |
| `update()` | 允许 PM 调整 `plannedWorkload`（基准可修订），不重置 `actualWorkload` |
| `reviewProgress()` | 审核通过时执行 `actualWorkload = actualWorkload.add(workloadHours)`（BigDecimal 累加） |
| `convertToVO()` | 透传 `plannedWorkload`/`actualWorkload`/`workloadUnit` 至 `TaskVO` |

**`ProjectServiceImpl` —— 项目级工作量：**

| 方法 | 工作量处理逻辑 |
|------|----------------|
| `getStatistics()` | 调用 `selectWorkloadStatistics(projectIds)` 聚合项目工作量数据 |
| `importWorkload()` | 工作量模板导入时设置 `plannedWorkload` 基准（批量初始化） |

**`DashboardServiceImpl` —— 角色差异化填充：**

| 方法 | 工作量处理逻辑 |
|------|----------------|
| `getSummary()` | admin：调用 `selectGlobalWorkloadStatistics()` 聚合全局未归档项目；PM：聚合自己负责的项目 |
| `getRiskBoard()` | 遍历任务执行偏差检测算法，标记偏差任务并填充统计卡片 |

**`DailyReportServiceImpl` —— 日报导出：**

- 日报 Excel 任务进度表格新增 3 列工作量数据（计划工作量/实际工作量/单位）
- 复用 EasyExcel 注解驱动导出，无需额外模板配置

#### 12.3.3 Mapper 层聚合查询

`BizTaskMapper` 新增两个聚合方法，均通过单条 SQL 完成聚合，避免 Java 端循环计算：

| 方法 | 入参 | 聚合维度 | 用途 |
|------|------|----------|------|
| `selectWorkloadStatistics(projectIds)` | 项目ID集合 | 按项目ID集合聚合 SUM(planned)/SUM(actual)/COUNT | PM/项目统计页 |
| `selectGlobalWorkloadStatistics()` | 无 | 全局聚合（排除已归档项目） | admin 工作台 |

### 12.4 偏差检测算法

**判定条件：**

```
actualWorkload < plannedWorkload × 0.8  AND  status != '已完成'
```

**算法实现要点：**

- 使用 `BigDecimal` 精确计算，阈值倍数 `0.8` 通过 `new BigDecimal("0.8")` 构造，避免 double 精度损失
- 偏差率计算使用 `divide(planned, 4, RoundingMode.HALF_UP)` 保留 4 位小数，四舍五入
- `plannedWorkload` 为 NULL 或 0 的任务跳过偏差检测（无基准不判定）
- 已完成任务不再触发偏差预警，避免历史数据噪声

**风险看板展示：**

- 偏差任务列表：展示任务名、计划工作量、实际工作量、偏差率、负责人
- 统计卡片：偏差任务数、偏差率分布、影响项目数

### 12.5 关键技术决策

| 决策点 | 方案 | 理由 |
|--------|------|------|
| 数值类型 | `BigDecimal` 全链路精确计算 | 工时累加涉及多次审核回写，double 易产生精度漂移 |
| 除法精度 | `divide(planned, 4, RoundingMode.HALF_UP)` | 偏差率展示需稳定精度，HALF_UP 符合常规四舍五入预期 |
| 缓存策略 | `@Cacheable` 复用 `dashboardSummary` 空间，TTL 60s | 工作量数据同步延迟最多 60s，平衡实时性与 DB 压力 |
| 角色数据填充 | admin 全局聚合 / PM 管理项目聚合 | 复用现有数据隔离策略，避免越权 |
| 基准可修订 | `update()` 允许 PM 调整 `plannedWorkload` | 适配项目变更场景，但保留 `actualWorkload` 累计值不重置 |
| 累加模式 | 读-改-写（`actualWorkload.add(workloadHours)`） | 当前实现简单，但存在潜在竞态条件（见 16.1） |

### 12.6 并发与一致性风险

**`actualWorkload` 累加竞态条件：**

当前 `reviewProgress()` 采用读-改-写模式：

```java
// 潜在竞态：并发审核同一任务的不同进度日志时可能丢失累加
BizTask task = taskMapper.selectById(taskId);
BigDecimal newActual = task.getActualWorkload().add(workloadHours);
task.setActualWorkload(newActual);
taskMapper.updateById(task);  // 覆盖写，可能覆盖并发事务的累加结果
```

**缓解建议：**

- 短期：对同一任务的审核操作加分布式锁（Redisson）
- 长期（推荐）：为 `BizTask` 添加 `@Version` 乐观锁字段（项目表已有该机制），审核时使用 `UPDATE ... SET actual_workload = actual_workload + ? WHERE id = ? AND version = ?` 原子累加

---

## 13. 前端组件结构与交互逻辑

### 13.1 组件分层

```
src/
├── layouts/
│   └── MainLayout.vue        # 主布局（侧边栏+顶部导航）
├── views/
│   ├── Dashboard.vue         # 工作台
│   ├── projects/             # 项目管理
│   ├── progress/             # 进度管理
│   ├── my/                   # 个人中心（我的任务/填报历史/风险看板）
│   ├── projectAuth/          # 项目授权
│   ├── projectTeam/          # 项目组管理
│   ├── documents/            # 文档管理
│   ├── customer/             # 客户管理
│   ├── archive/              # 归档管理
│   ├── statistics/           # 统计分析
│   ├── dailyReport/          # 日报导出
│   ├── system/               # 系统管理
│   ├── settings/             # 系统设置
│   ├── review/               # 进度审核
│   └── Login.vue             # 登录页
├── stores/                   # Pinia状态管理
├── api/                      # API接口定义
├── utils/                    # 工具函数
└── components/               # 公共组件
```

### 13.2 布局组件（MainLayout）

**功能：**

- 可折叠侧边栏菜单（权限动态显隐）
- 顶部导航栏（面包屑、全屏、通知、用户下拉）
- 关键节点提醒抽屉
- 站内通知抽屉

**会话管理：**

- 会话超时监控（30分钟无操作自动登出）
- Token自动刷新（过期前5分钟）
- 登出时重置所有业务store（防止数据泄露）

### 13.3 状态管理（Pinia）

| Store             | 用途               |
| ----------------- | ---------------- |
| `user.js`         | 用户认证状态、登录登出      |
| `systemConfig.js` | 系统配置             |
| `project.js`      | 项目数据             |
| `customer.js`     | 客户数据             |
| `document.js`     | 文档数据             |
| `progress.js`     | 进度任务数据           |
| `notification.js` | 通知数据 + WebSocket |
| `statistics.js`   | 统计数据             |

### 13.4 工具函数

| 文件                  | 用途                 |
| ------------------- | ------------------ |
| `request.js`        | Axios封装、请求拦截器、错误处理 |
| `permission.js`     | 权限校验函数             |
| `cryptoStorage.js`  | 加密localStorage     |
| `cancelToken.js`    | 请求取消机制             |
| `sessionMonitor.js` | 会话超时监控             |

### 13.5 工作量管理相关组件（v1.1 新增）

工作量管理机制在前端复用现有页面组件，通过条件渲染与角色判断集成：

| 组件 | 工作量相关改动 | 可见角色 |
|------|--------------|----------|
| `Dashboard.vue` | 新增工作量统计卡片（计划/实际/偏差率） | admin、PM |
| `RiskBoard.vue` | 新增工作量偏差预警列表 + 偏差任务统计卡片 | admin、PM |
| `ProjectProgress.vue` | 任务编辑对话框新增工作量基准设定（plannedWorkload/workloadUnit）；进度填报展示工作量基准 | PM、工程师 |
| `DailyReport.vue` | 任务进度表格新增工作量列（计划/实际/单位） | 有日报权限用户 |

**前端交互要点：**

- 工作量统计卡片通过 `v-if` 角色判断控制可见性，仅 admin/PM 渲染
- 偏差预警列表复用现有风险看板表格组件，新增偏差率列与颜色标识（红色高亮偏差任务）
- 任务编辑对话框中工作量基准字段与任务其他字段同表单提交，无需独立接口
- 日报工作量列复用 EasyExcel 导出能力，前端表格列与导出列保持一致

---

## 14. 安全措施

### 14.1 认证安全

| 措施                | 说明                           |
| ----------------- | ---------------------------- |
| JWT过期时间           | 缩短至30分钟，降低泄露风险               |
| Token黑名单          | 登出时加入Redis黑名单                |
| 双重Token           | Access Token + Refresh Token |
| Issuer/Audience校验 | 防止跨服务token误用                 |
| 密码加密              | BCrypt哈希存储                   |

### 14.2 权限安全

| 措施     | 说明                                     |
| ------ | -------------------------------------- |
| 前端路由守卫 | 路由级权限校验                                |
| 后端注解校验 | `@RequiresPermission`                  |
| 数据隔离   | 基于角色的数据可见性控制                           |
| 岗位职级校验 | `@RequiresPermission(requirePosition)` |

### 14.3 文件安全

| 措施             | 说明                      |
| -------------- | ----------------------- |
| 扩展名白名单         | 仅允许PDF/DOC/XLS/JPG/PNG等 |
| Magic number校验 | 文件头验证，防止伪装              |
| 路径穿越防护         | 三重校验（绝对路径/../normalize） |
| UUID重命名        | 避免原始文件名泄露               |
| 大小限制           | 100MB                   |

### 14.4 数据安全

| 措施      | 说明                               |
| ------- | -------------------------------- |
| 参数脱敏    | PermissionAspect中对方法参数脱敏         |
| SQL注入防护 | MyBatis-Plus预编译参数                |
| XSS防护   | XssFilter过滤                      |
| 异常堆栈泄露  | GlobalExceptionHandler仅返回message |
| 加密存储    | localStorage使用XOR加密              |

### 14.5 传输安全

| 措施     | 说明          |
| ------ | ----------- |
| HTTPS  | 生产环境强制HTTPS |
| CORS配置 | 限制允许的Origin |
| 请求签名   | 防篡改（预留）     |

---

## 15. 性能优化方案

### 15.1 数据库优化

| 优化项    | 说明                                      |
| ------ | --------------------------------------- |
| 批量查询   | `fillDesignDrawingsBatch` 解决N+1         |
| 原子更新   | `updateProjectProgressByTaskAvg` 消除并发覆盖 |
| 乐观锁    | 项目更新使用@Version                          |
| SQL聚合  | 统计查询使用单条聚合SQL                           |
| LIKE转义 | `SqlUtils.escapeLike` 防注入               |
| 工作量聚合  | `selectWorkloadStatistics` 单条SQL聚合，避免Java端循环累加 |
| BigDecimal精确计算 | 工作量累加/偏差率使用 `divide(planned, 4, RoundingMode.HALF_UP)` |

### 15.2 缓存优化

| 优化项        | 说明              |
| ---------- | --------------- |
| 分层TTL      | 不同缓存空间配置不同过期时间  |
| 缓存预热       | 登录时加载权限缓存       |
| SCAN替代KEYS | 批量清除缓存使用SCAN    |
| 本地缓存       | Token黑名单本地缓存60秒 |
| Dashboard工作量缓存 | `@Cacheable` 复用 `dashboardSummary` 空间，TTL 60s，工作量数据同步延迟最多60s |

### 15.3 前端优化

| 优化项          | 说明                          |
| ------------ | --------------------------- |
| Keep-alive缓存 | 列表页组件缓存                     |
| 请求取消         | 路由切换时取消未完成请求                |
| 懒加载路由        | 组件按需加载                      |
| 图片预览         | fetch+Bearer token+blob URL |

### 15.4 系统优化

| 优化项       | 说明                    |
| --------- | --------------------- |
| Druid连接池  | max-active=30         |
| Tomcat线程池 | max=200, min-spare=20 |
| Redis连接池  | max-wait=2000ms       |
| 日志级别优化    | 常规路径降为DEBUG           |

---

## 16. 潜在问题与改进建议

### 16.1 已识别问题

| 问题                   | 严重程度 | 状态  | 说明                          |
| -------------------- | ---- | --- | --------------------------- |
| transferLeader重复分配风险 | 中    | **已修复** | INSERT前添加selectCount查重逻辑 |
| localStorage XOR加密   | 中    | **长期计划** | 当前为过渡期方案，已在代码中添加TODO标记 |
| 通知批量发送               | 低    | **已优化** | 新增sendNotifications批量方法，TaskDueNotificationScheduler已改为批量发送 |
| 进度填报图片预览             | 已修复  | 已完成 | 使用fetch+token               |
| isRead参数类型           | 已修复  | 已完成 | 从Integer改为Boolean           |
| actualWorkload累加竞态条件 | 中    | **待处理** | `reviewProgress()` 采用读-改-写模式，并发审核同一任务的不同进度日志时可能丢失累加结果。建议添加 `@Version` 乐观锁或分布式锁 |

### 16.2 已实施优化

**安全类：**

1. **敏感操作二次确认**：角色权限分配已添加二次确认对话框，显示权限数量
2. **接口限流保护**：文件上传接口添加@RateLimit注解（60秒10次）
3. **操作日志完善**：文件上传接口添加@OperationLog注解，记录上传操作

**性能类：**

1. **通知批量发送**：新增sendNotifications批量方法，配合批量插入SQL，TaskDueNotificationScheduler已改为批量发送
2. **文件上传限流**：防止恶意上传消耗存储资源

**代码质量类：**

1. **transferLeader重复分配修复**：INSERT前添加查重逻辑，与addMember保持一致

### 16.3 待改进建议

**安全类：**

1. **XSS防护增强**：长期计划迁移到HttpOnly Cookie存储Token，需后端配合修改登录接口返回Set-Cookie，当前代码已添加TODO标记和详细注释
2. **CSRF防护**：迁移到HttpOnly Cookie后需要添加CSRF Token机制
3. **IP白名单**：管理后台限制访问IP

**性能类：**

1. **读写分离**：数据库配置主从复制
2. **分布式锁**：高并发场景使用Redis分布式锁
3. **消息队列**：异步任务（通知、日志）使用MQ解耦
4. **工作量累加乐观锁**：为 `BizTask` 添加 `@Version` 乐观锁字段，`reviewProgress()` 审核回写时使用 `UPDATE ... SET actual_workload = actual_workload + ? WHERE id = ? AND version = ?` 原子累加，消除并发审核丢更新风险（项目表已有该机制可复用）

**代码质量类：**

1. **单元测试**：补充Service层单元测试
2. **集成测试**：关键业务流程集成测试
3. **API契约测试**：前后端接口契约验证
4. **代码规范**：统一代码风格检查

**功能增强类：**

1. **数据备份**：定期数据库备份策略
2. **移动端适配**：响应式优化
3. **国际化**：多语言支持

---

## 附录：关键配置参数

### 后端配置

| 参数        | 开发环境           | 生产环境要求    |
| --------- | -------------- | --------- |
| 数据库       | localhost:3306 | 独立数据库实例   |
| Redis     | localhost:6379 | 独立Redis实例 |
| JWT密钥     | 默认值(含警告)       | 环境变量注入    |
| CORS      | *              | 指定域名      |
| Tomcat线程数 | max=200        | 根据负载调整    |
| Druid连接池  | max-active=30  | 根据负载调整    |

### 前端配置

| 参数      | 值                |
| ------- | ---------------- |
| API基础路径 | /api             |
| 请求超时    | 30000ms          |
| Token存储 | localStorage(加密) |
| 会话超时    | 30分钟             |

---

**文档版本**: v1.1  
**生成时间**: 2026-07-13  
**更新时间**: 2026-07-15（新增第12章 工作量管理模块）  
**适用项目**: IDC数据中心综合布线项目管理系统