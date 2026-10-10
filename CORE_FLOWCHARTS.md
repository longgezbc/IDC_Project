# 核心业务流程优化流程图

> 生成日期：2026-07-13
> 更新日期：2026-07-21（v1.3 部署修复：mysql-grants、tmpfs、Nginx ^~、小程序图片路径适配）
> 基于代码逻辑分析报告，梳理7个核心业务流程并标注优化点

---

## 1. 项目管理流程

### 1.1 创建项目

```mermaid
flowchart TD
    A([用户提交创建表单]) --> B[校验项目编号唯一<br/>selectCount by projectCode]
    B --> C{编号已存在?}
    C -->|是| D[抛出异常: 项目编号已存在]
    C -->|否| E[保存或查询客户<br/>saveOrGetCustomer]
    E --> F[日期校验<br/>startDate ≤ endDate]
    F --> G{校验通过?}
    G -->|否| H[抛出异常: 日期非法]
    G -->|是| I[构建项目实体<br/>设置字段/createdBy]
    I --> J[插入项目记录<br/>projectMapper.insert]
    J --> K{有设计图纸?}
    K -->|是| L[批量插入设计图纸<br/>drawingMapper.batchInsert]
    K -->|否| M[返回项目详情]
    L --> M
    M --> N([创建成功])

    style D fill:#ffcccc
    style H fill:#ffcccc
    style L fill:#ccffcc
```

### 1.2 更新项目

```mermaid
flowchart TD
    A([用户提交更新表单]) --> B[查询项目存在性]
    B --> C{项目存在?}
    C -->|否| D[抛出异常: 项目不存在]
    C -->|是| E[校验项目编号唯一<br/>排除自身]
    E --> F{PM变更?<br/>managerId ≠ 原值}
    F -->|是| G[PM变更委托transferLeader<br/>统一处理联动]
    F -->|否| H[直接设置managerName/managerId]
    G --> I
    H --> I[日期交叉校验<br/>合并dto与已有日期]
    I --> J{校验通过?}
    J -->|否| K[抛出异常: 日期非法]
    J -->|是| L[乐观锁更新<br/>version字段]
    L --> M{updateById返回0?}
    M -->|是| N[抛出异常: 数据已被修改请刷新]
    M -->|否| O{PM变更?}
    O -->|是| P[调用transferLeader<br/>更新team/assignment/审计日志]
    O -->|否| Q{有设计图纸?}
    P --> Q
    Q -->|是| R[先删后增设计图纸]
    Q -->|否| S[返回项目详情]
    R --> S
    S --> T([更新成功])

    style D fill:#ffcccc
    style K fill:#ffcccc
    style N fill:#ffcccc
    style I fill:#fffacd
    style L fill:#ccffcc
    style P fill:#ccffcc
```

> **优化点（黄色标记）**：日期交叉校验应合并DTO日期与已有日期做完整比较，当前仅校验DTO同时传入两个日期的情况。

### 1.3 删除项目（含优化建议）

```mermaid
flowchart TD
    A([用户请求删除项目]) --> B[查询项目存在性]
    B --> C{项目存在?}
    C -->|否| D[抛出异常: 项目不存在]
    C -->|是| E[校验关联任务]
    E --> F{存在关联任务?}
    F -->|是| G[拒绝删除: 项目下存在任务]
    F -->|否| H[校验关联文档]
    H --> I{存在关联文档?}
    I -->|是| J[拒绝删除: 项目下存在文档]
    I -->|否| K[删除项目记录]

    K --> L[级联清理: 项目组成员]
    L --> M[级联清理: 授权记录]
    M --> N[级联清理: 设计图纸]
    N --> O[级联清理: 归档记录]
    O --> P[级联清理: 授权申请]
    P --> Q[删除项目记录]
    Q --> R([删除成功])

    style D fill:#ffcccc
    style G fill:#ffcccc
    style J fill:#ffcccc
    style L fill:#ffcccc
    style M fill:#ffcccc
    style N fill:#ffcccc
    style O fill:#ffcccc
    style P fill:#ffcccc
```

> **优化点（红色虚线标记）**：当前实现缺少级联清理步骤（L-P），会导致项目组、授权记录、设计图纸、归档记录等成为孤儿数据。建议在删除项目记录前级联清理关联数据，或改为软删除（`is_deleted=1`）保留数据可恢复。

---

## 2. 任务进度审核流程

### 2.1 任务创建与删除

```mermaid
flowchart TD
    subgraph 创建任务
        A1([创建任务]) --> B1[校验项目存在]
        B1 --> C1[日期校验<br/>startDate ≤ endDate]
        C1 --> D1[构建任务实体]
        D1 --> E1[插入任务记录]
        E1 --> F1[同步项目进度<br/>syncProjectProgress]
        F1 --> G1([创建成功])
    end

    subgraph 删除任务
        A2([删除任务]) --> B2[查询任务存在性]
        B2 --> C2{任务存在?}
        C2 -->|否| D2[抛出异常]
        C2 -->|是| E2[校验子任务存在性]
        E2 --> F2{存在子任务?<br/>parentId = id}
        F2 -->|是| G2[拒绝删除: 存在子任务]
        F2 -->|否| H2[删除任务记录]
        H2 --> I2[同步项目进度<br/>syncProjectProgress]
        I2 --> J2([删除成功])
    end

    style D2 fill:#ffcccc
    style G2 fill:#ffcccc
    style E2 fill:#ffcccc
```

> **优化点（红色标记）**：当前删除任务未校验子任务存在性（E2-F2-G2），可能导致子任务的parentId指向已删除的任务。建议删除前检查 `biz_task.parent_id = id` 的记录数。

### 2.2 进度填报与审核流程

```mermaid
flowchart TD
    A([工程师提交进度填报]) --> B[查询任务存在性]
    B --> C{任务存在?}
    C -->|否| D[抛出异常: 任务不存在]
    C -->|是| E[记录previousProgress<br/>当前task.progress]
    E --> F[创建pending状态进度日志<br/>不修改task.progress]
    F --> G[序列化施工图片URL<br/>JSON数组存储]
    G --> H[保存工作量描述与工时]
    H --> I[通知项目经理<br/>静默投递]
    I --> J([填报成功，等待审核])

    J --> K([项目经理发起审核])
    K --> L[查询进度日志]
    L --> M{日志存在?}
    M -->|否| N[抛出异常: 日志不存在]
    M -->|是| O{reviewStatus<br/>= pending?}
    O -->|否| P[拒绝: 不可重复审核]
    O -->|是| Q{审核结果?}

    Q -->|通过| R[标记日志 approved]
    Q -->|驳回| S[标记日志 rejected]

    R --> T[查询关联任务]
    T --> U[回写 task.progress]
    U --> V[联动任务状态<br/>100% → 已完成<br/>>0 → 进行中]
    V --> W[同步项目整体进度<br/>AVG子查询原子更新]
    W --> X[通知填报人: 审核通过]
    X --> Y([审核完成])

    S --> Z[通知填报人: 审核驳回]
    Z --> AA([审核完成])

    style D fill:#ffcccc
    style N fill:#ffcccc
    style P fill:#ffcccc
    style W fill:#ccffcc
    style F fill:#ccffcc
```

> **关键设计说明**：
> - 进度填报不直接修改任务进度，采用审核流程确保数据准确
> - 项目进度同步使用 `UPDATE ... SET progress = (SELECT AVG(progress) FROM biz_task WHERE project_id = ?)` 原子操作，消除并发覆盖
> - 建议确认SQL使用 `IFNULL(AVG(progress), 0)` 防止任务全部删除后返回NULL

---

## 3. 项目授权流程

```mermaid
flowchart TD
    A([申请人发起授权申请]) --> B[校验项目存在]
    B --> C{项目存在?}
    C -->|否| D[抛出异常: 项目不存在]
    C -->|是| E[校验目标工程师有效]
    E --> F{工程师有效?}
    F -->|否| G[抛出异常: 工程师不存在]
    F -->|是| H[校验申请人权限<br/>admin/PM/项目负责人]
    H --> I{有权申请?}
    I -->|否| J[抛出异常: 无权申请]
    I -->|是| K[幂等校验<br/>同项目同工程师无pending申请]
    K --> L{已有pending?}
    L -->|是| M[拒绝: 已有待审批申请]
    L -->|否| N[插入申请记录<br/>status=pending]
    N --> O[写审计日志]
    O --> P([申请创建成功])

    P --> Q([审批人发起审批])
    Q --> R[校验申请为pending状态]
    R --> S{pending状态?}
    S -->|否| T[拒绝: 不可重复审批]
    S -->|是| U{审批结果?}

    U -->|通过| V[校验已有有效授权<br/>防重复授权]
    V --> W{已存在授权?}
    W -->|是| X[拒绝: 已被授权]
    W -->|否| Y[更新申请为approved]
    Y --> Z[创建授权记录<br/>biz_project_assignment]
    Z --> AA[写审计日志]
    AA --> BB([审批通过完成])

    U -->|驳回| CC[更新申请为rejected]
    CC --> DD[写审计日志]
    DD --> EE([审批驳回完成])

    style D fill:#ffcccc
    style G fill:#ffcccc
    style J fill:#ffcccc
    style M fill:#ffcccc
    style T fill:#ffcccc
    style X fill:#ffcccc
    style V fill:#ccffcc
    style K fill:#ccffcc
```

> **关键设计说明**：
> - 双重幂等校验：申请阶段防重复pending申请，审批阶段防重复授权
> - 审计日志完整记录操作类型、操作人和意见
> - 事务保证审批状态更新与授权记录创建的原子性

---

## 4. 文档管理流程

### 4.1 文档CRUD

```mermaid
flowchart TD
    subgraph 创建文档
        A1([创建文档]) --> B1[校验项目存在]
        B1 --> C1[生成文档编号<br/>DOC + UUID前8位]
        C1 --> D1[插入文档记录]
        D1 --> E1[插入初始版本 V1.0]
        E1 --> F1[保存标签]
        F1 --> G1([创建成功])
    end

    subgraph 更新文档
        A2([更新文档]) --> B2[查询文档存在性]
        B2 --> C2[非空字段选择性更新]
        C2 --> D2{标签有变更?}
        D2 -->|是| E2[先删旧标签关联]
        E2 --> F2[保存新标签]
        D2 -->|否| G2[更新文档记录]
        F2 --> G2
        G2 --> H2([更新成功])
    end

    subgraph 删除文档
        A3([删除文档]) --> B3[查询文档存在性]
        B3 --> C3[事务内: 先删DB记录]
        C3 --> D3[注册afterCommit钩子]
        D3 --> E3{事务提交成功?}
        E3 -->|是| F3[删除物理文件<br/>失败仅记录日志]
        E3 -->|否| G3[事务回滚<br/>物理文件保留]
        F3 --> H3([删除成功])
        G3 --> I3([删除失败回滚])
    end

    style C1 fill:#ffcccc
    style F3 fill:#ccffcc
    style D3 fill:#ccffcc
```

> **优化点（红色标记）**：文档编号当前使用 `DOC + System.currentTimeMillis()`，并发时可能重复。建议改为 `DOC + UUID前8位` 确保唯一性。
>
> **安全设计（绿色标记）**：删除文档采用"先删DB后删文件"策略 + afterCommit钩子，确保事务回滚时物理文件不丢失。

### 4.2 文档版本管理（含优化建议）

```mermaid
flowchart TD
    A([新增文档版本]) --> B[查询文档存在性]
    B --> C{文档存在?}
    C -->|否| D[抛出异常: 文档不存在]
    C -->|是| E[校验版本号唯一性<br/>同文档下版本号不重复]
    E --> F{版本号已存在?}
    F -->|是| G[拒绝: 版本号已存在]
    F -->|否| H[插入新版本记录]
    H --> I[更新文档当前版本号]
    I --> J[更新上传日期]
    J --> K([版本新增成功])

    style E fill:#ffcccc
    style G fill:#ffcccc
```

> **优化点（红色标记）**：当前 `addVersion` 方法未校验版本号唯一性（E-F-G），可能产生重复版本号。建议在插入前检查 `biz_document_version` 表中同 `document_id` 下是否已存在相同 `version`。

---

## 5. 认证与Token刷新流程

### 5.1 登录与登出

```mermaid
flowchart LR
    subgraph 登录
        A1([用户输入账号密码]) --> B1[AuthController.login]
        B1 --> C1[查询用户<br/>username]
        C1 --> D1{用户存在?}
        D1 -->|否| E1[返回错误: 用户名或密码错误]
        D1 -->|是| F1[BCrypt密码校验]
        F1 --> G1{密码正确?}
        G1 -->|否| H1[返回错误: 用户名或密码错误]
        G1 -->|是| I1{账号启用?}
        I1 -->|否| J1[返回错误: 账号已禁用]
        I1 -->|是| K1[生成JWT<br/>accessToken + refreshToken]
        K1 --> L1[复用缓存查询角色<br/>permissionService.getRolesByUserId]
        L1 --> M1[返回token + 用户信息 + 角色权限]
        M1 --> N1([登录成功])
    end

    subgraph 登出
        A2([用户点击登出]) --> B2[AuthController.logout]
        B2 --> C2[Token写入Redis黑名单<br/>TTL = token剩余有效期]
        C2 --> D2[清除用户角色/权限缓存<br/>clearUserCache]
        D2 --> E2[清除前端本地状态]
        E2 --> F2([登出成功])
    end

    style E1 fill:#ffcccc
    style H1 fill:#ffcccc
    style J1 fill:#ffcccc
    style L1 fill:#ccffcc
    style C2 fill:#ccffcc
```

> **优化点（绿色标记）**：登录时复用缓存查询角色（L1），避免直接查DB。登出时Token写入Redis黑名单 + 清除用户缓存，双重保障。

### 5.2 Token刷新协调器流程

```mermaid
flowchart TD
    A([请求返回401/2001/2002]) --> B{是 /auth/refresh 或 /auth/logout?}
    B -->|是| C[直接跳转登录页]
    B -->|否| D{已有 __retried 标记?}
    D -->|是| E[直接跳转登录页<br/>防止无限重试]
    D -->|否| F[标记 __retried = true]
    F --> G[委托TokenRefreshCoordinator]

    G --> H{刷新锁状态?}
    H -->|锁空闲| I[获取锁]
    H -->|锁被占| J[进入等待队列]

    I --> K[调用 /auth/refresh]
    K --> L{刷新成功?}
    L -->|是| M[用新Token重发原请求]
    L -->|否| N[通知队列所有请求失败]
    N --> O[跳转登录页]

    J --> P{等待结果}
    P -->|刷新成功| Q[用新Token重发队列请求]
    P -->|刷新失败| R[跳转登录页]

    M --> S([请求完成])
    Q --> S

    style C fill:#ffcccc
    style E fill:#ffcccc
    style O fill:#ffcccc
    style R fill:#ffcccc
    style I fill:#ccffcc
    style M fill:#ccffcc
    style Q fill:#ccffcc
```

> **关键设计说明**：
> - `__retried` 标记防止同一请求无限重试（P0修复：业务码401死循环）
> - `/auth/refresh` 自身返回401时直接跳登录，避免死锁
> - "锁+队列"机制确保并发401只触发一次刷新
> - 刷新成功后所有队列请求自动重发

### 5.3 请求拦截与认证流程

```mermaid
flowchart TD
    A([前端发起请求]) --> B{是 FormData?}
    B -->|是| C[删除默认Content-Type<br/>让浏览器自动设置boundary]
    B -->|否| D[保持默认Content-Type]
    C --> E{skipCancel?}
    D --> E
    E -->|否| F[加入pending队列<br/>AbortController]
    E -->|是| G[跳过取消队列]
    F --> H[注入Authorization: Bearer token]
    G --> H
    H --> I[发送请求到后端]

    I --> J[JwtAuthInterceptor]
    J --> K{是OPTIONS请求?}
    K -->|是| L[放行预检请求]
    K -->|否| M{有Authorization头?}
    M -->|否| N[返回401未认证]
    M -->|是| O[解析JWT Token]
    O --> P{Token有效?}
    P -->|否| Q[返回401 Token无效]
    P -->|是| R{Token在黑名单?}
    R -->|是| S[返回401 Token已失效]
    R -->|否| T[设置UserContext<br/>ThreadLocal]
    T --> U[PermissionAspect校验]
    U --> V{有权限?}
    V -->|否| W[返回2003无权限]
    V -->|是| X[执行业务逻辑]
    X --> Y([请求完成])

    style C fill:#ccffcc
    style F fill:#ccffcc
    style T fill:#ccffcc
    style N fill:#ffcccc
    style Q fill:#ffcccc
    style S fill:#ffcccc
    style W fill:#ffcccc
```

> **关键设计说明**：
> - FormData请求自动删除Content-Type（修复文件上传boundary缺失问题）
> - JWT认证三重校验：Token有效性 + 黑名单检查 + 权限校验
> - UserContext在afterCompletion中清理，防止ThreadLocal泄漏

---

## 6. 通知批量发送流程

### 6.1 单条通知投递

```mermaid
flowchart TD
    A([业务模块触发通知]) --> B[构建NotificationSendDTO]
    B --> C[检查用户通知偏好<br/>sys_notification_config]
    C --> D{用户开启该类型?}
    D -->|否| E[跳过投递]
    D -->|是| F[写入sys_notification表<br/>独立事务 REQUIRES_NEW]
    F --> G{WebSocket推送成功?}
    G -->|是| H[用户实时收到通知]
    G -->|否| I[记录警告日志<br/>不影响主业务]
    I --> J[用户下次登录可见<br/>通知已落库]
    E --> K([投递完成])
    H --> K
    J --> K

    style F fill:#ccffcc
    style I fill:#fffacd
```

> **关键设计说明**：
> - 独立事务 `REQUIRES_NEW`：通知投递失败不回滚主业务事务
> - 静默投递：异常仅记录日志，不抛出给调用方
> - 兜底机制：WebSocket推送失败时消息已落库，用户登录后可见

### 6.2 批量通知投递（含优化建议）

```mermaid
flowchart TD
    A([定时任务/批量触发]) --> B[构建通知DTO列表]
    B --> C[过滤: 检查每个用户通知偏好]
    C --> D{过滤后列表非空?}
    D -->|否| E([无需发送])
    D -->|是| F[批量插入通知记录<br/>batchInsert单条SQL]
    F --> G[逐个WebSocket推送]
    G --> H[记录发送日志]
    H --> I([批量投递完成])

    style F fill:#ccffcc
    style G fill:#ffcccc
```

> **优化点（红色标记）**：当前WebSocket推送（G）仍为逐个循环，大量通知时可能阻塞。建议改为异步并行推送：
>
> ```java
> // 优化方案：使用并行流异步推送
> notifications.parallelStream().forEach(n -> {
>     try {
>         pushService.pushToUser(n.getUserId(), convertToVO(n));
>     } catch (Exception e) {
>         log.warn("[批量通知] 推送失败: userId={}, error={}", n.getUserId(), e.getMessage());
>     }
> });
> ```

### 6.3 定时任务到期通知流程

```mermaid
flowchart TD
    A([定时触发 08:00]) --> B[扫描未来3天到期<br/>及已逾期未完成任务]
    B --> C[批量查询今日已发通知<br/>构建去重集合]
    C --> D[批量查询项目名称<br/>构建ID→名称映射]
    D --> E[遍历任务列表]
    E --> F{今天已通知过?<br/>dedupKey命中}
    F -->|是| G[跳过]
    F -->|否| H[判断逾期状态<br/>endDate < today]
    H --> I[构建NotificationSendDTO<br/>逾期=优先级3, 即将到期=优先级2]
    I --> J{有待发送通知?}
    J -->|是| K[调用sendNotifications批量发送]
    J -->|否| L([扫描完成])
    K --> M[记录统计日志<br/>总计/已发送/跳过]
    M --> L

    style C fill:#ccffcc
    style D fill:#ccffcc
    style K fill:#ccffcc
```

> **优化设计（绿色标记）**：
> - N+1查询优化：批量查询已发通知（C）和项目名称（D），避免逐条查询
> - 去重机制：同一任务同一天只通知一次，dedupKey = `taskId:assigneeId`
> - 批量发送：使用 `sendNotifications` 批量方法，减少数据库往返

---

## 7. 工作量管理流程（v1.1 新增）

工作量管理机制在任务进度审核流程基础上，引入"基准设定 → 实际回写 → 偏差检测"三段式闭环，实现项目工时量化跟踪与预警。

### 7.1 工作量基准设定流程

```mermaid
flowchart TD
    A([PM 发起工作量基准设定]) --> B{设定方式?}

    B -->|单任务设定| C[TaskServiceImpl.create/update]
    B -->|批量导入| D[ProjectServiceImpl.importWorkload]

    C --> E[从 TaskSaveDTO 读取<br/>plannedWorkload / workloadUnit]
    E --> F{是创建任务?}
    F -->|是| G[actualWorkload 初始化为 BigDecimal.ZERO]
    F -->|否| H[保留已有 actualWorkload<br/>不重置累计值]
    G --> I[插入/更新任务记录]
    H --> I

    D --> J[解析 Excel 工作量模板]
    J --> K[匹配项目下任务<br/>按任务名/编号]
    K --> L{任务匹配成功?}
    L -->|否| M[记录跳过: 任务未匹配]
    L -->|是| N[批量设置 plannedWorkload 基准]
    N --> O[批量更新任务记录]

    I --> P([基准设定完成])
    O --> P
    M --> P

    style G fill:#ccffcc
    style H fill:#ccffcc
    style N fill:#ccffcc
    style M fill:#fffacd
```

> **关键设计说明（绿色标记）**：
> - `create()` 时 `actualWorkload` 初始化为 0，`update()` 时保留累计实际值不重置，避免历史工时丢失
> - 基准设定复用现有任务 CRUD 路径，无需独立接口
> - `importWorkload()` 支持批量初始化，适配项目立项阶段的工作量估算

### 7.2 工作量审核回写流程

```mermaid
flowchart TD
    A([工程师提交进度填报<br/>携带 workloadHours]) --> B[创建 pending 状态进度日志<br/>保存 workloadHours / workloadDescription]
    B --> C[通知项目经理审核]
    C --> D([项目经理发起审核])

    D --> E[查询进度日志]
    E --> F{日志存在?}
    F -->|否| G[抛出异常: 日志不存在]
    F -->|是| H{reviewStatus = pending?}
    H -->|否| I[拒绝: 不可重复审核]
    H -->|是| J{审核结果?}

    J -->|通过| K[标记日志 approved]
    K --> L[回写 task.progress]
    L --> M[联动任务状态<br/>100%→已完成, >0→进行中]
    M --> N[工作量累加回写<br/>actualWorkload = actualWorkload.add workloadHours]
    N --> O[同步项目整体进度<br/>syncProjectProgress]
    O --> P[通知填报人: 审核通过]
    P --> Q([审核完成])

    J -->|驳回| R[标记日志 rejected]
    R --> S[actualWorkload 不累加<br/>驳回工时不计入实际投入]
    S --> T[通知填报人: 审核驳回]
    T --> U([审核完成])

    N --> V{并发审核同一任务?}
    V -->|是| W[潜在丢失更新风险<br/>读-改-写模式]
    V -->|否| X[正常累加]

    style G fill:#ffcccc
    style I fill:#ffcccc
    style N fill:#ccffcc
    style S fill:#ccffcc
    style W fill:#ffcccc
```

> **关键设计说明**：
> - 工作量累加（N，绿色）复用现有审核流程，仅在通过分支增加 `BigDecimal.add()` 累加，侵入性小
> - 驳回工时不累加（S，绿色），确保 `actualWorkload` 仅反映已审核通过的真实投入
> - **并发风险（W，红色）**：`actualWorkload` 累加采用读-改-写模式，并发审核同一任务的不同进度日志时可能丢失更新。建议添加 `@Version` 乐观锁原子累加（详见附录 O8）

### 7.3 工作量偏差检测流程

```mermaid
flowchart TD
    A([访问风险看板<br/>DashboardServiceImpl.getRiskBoard]) --> B[查询可见项目下任务列表]
    B --> C[遍历每个任务执行偏差判定]

    C --> D{plannedWorkload<br/>为 NULL 或 0?}
    D -->|是| E[跳过: 无基准不判定]
    D -->|否| F{status == 已完成?}

    F -->|是| G[跳过: 已完成不预警]
    F -->|否| H[计算阈值<br/>threshold = plannedWorkload × 0.8<br/>new BigDecimal 0.8]

    H --> I{actualWorkload < threshold?}
    I -->|否| J[标记为正常任务]
    I -->|是| K[标记为偏差任务]

    K --> L[计算偏差率<br/>actualWorkload.divide planned, 4, HALF_UP]
    L --> M[加入偏差任务列表]

    C --> N{遍历完成?}
    N -->|否| C
    N -->|是| O[填充统计卡片<br/>偏差任务数/偏差率分布/影响项目数]
    O --> P[返回风险看板数据]
    J --> N
    M --> N
    E --> N
    G --> N

    P --> Q{命中 dashboardSummary 缓存?}
    Q -->|是| R[直接返回缓存<br/>TTL 60s]
    Q -->|否| S[查询DB并写入缓存]
    S --> T([展示完成])
    R --> T

    style E fill:#fffacd
    style G fill:#fffacd
    style K fill:#ffcccc
    style H fill:#ccffcc
    style L fill:#ccffcc
    style R fill:#ccffcc
```

> **关键设计说明**：
> - 无基准/已完成任务跳过判定（黄色），避免无意义预警和历史数据噪声
> - 阈值 `0.8` 通过 `new BigDecimal("0.8")` 字符串构造（H，绿色），避免 double 精度损失
> - 偏差率使用 `divide(planned, 4, RoundingMode.HALF_UP)`（L，绿色），4 位小数四舍五入，展示稳定
> - 偏差任务标记为红色，前端风险看板红色高亮展示
> - `@Cacheable` 复用 `dashboardSummary` 缓存空间（R，绿色），TTL 60s，工作量数据同步延迟最多 60s

### 7.4 工作量统计与日报导出流程

```mermaid
flowchart TD
    A([触发工作量统计]) --> B{用户角色?}

    B -->|admin| C[selectGlobalWorkloadStatistics<br/>全局聚合未归档项目]
    B -->|PM| D[selectWorkloadStatistics<br/>按管理项目ID集合聚合]

    C --> E[单条SQL聚合<br/>SUM planned / SUM actual / COUNT]
    D --> E

    E --> F[填充 DashboardSummaryVO<br/>工作量统计字段]
    F --> G([统计完成])

    H([导出日报]) --> I[DailyReportServiceImpl]
    I --> J[查询任务进度数据]
    J --> K[EasyExcel 注解驱动导出]
    K --> L[任务进度表格新增3列<br/>计划工作量/实际工作量/单位]
    L --> M([导出完成])

    style E fill:#ccffcc
    style L fill:#ccffcc
```

> **关键设计说明**：
> - 聚合统计使用单条 SQL（E，绿色），避免 Java 端循环累加，性能更优
> - 角色差异化：admin 全局聚合未归档项目，PM 聚合自己负责项目，复用现有数据隔离策略
> - 全局聚合通过 JOIN 项目表过滤 `is_archived = 0`，避免历史归档项目污染统计
> - 日报工作量列（L，绿色）复用 EasyExcel 注解驱动导出，无需额外模板配置

---

## 附录：优化点汇总

| 编号 | 所属流程 | 优化内容 | 优先级 | 代码位置 |
|------|----------|----------|--------|----------|
| O1 | 项目删除 | 级联清理关联数据（项目组/授权/图纸/归档） | 高 | [ProjectServiceImpl.delete](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/ProjectServiceImpl.java#L339-L366) |
| O2 | 项目更新 | 日期交叉校验合并DTO与已有日期 | 低 | [ProjectServiceImpl.update](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/ProjectServiceImpl.java#L246-L334) |
| O3 | 任务删除 | 校验子任务存在性 | 中 | [TaskServiceImpl.delete](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/TaskServiceImpl.java#L207-L217) |
| O4 | 进度同步 | 确认AVG SQL使用IFNULL兜底 | 中 | [TaskServiceImpl.syncProjectProgress](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/TaskServiceImpl.java#L688-L692) |
| O5 | 文档编号 | 使用UUID替代时间戳 | 低 | [DocumentServiceImpl.create](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/DocumentServiceImpl.java#L142-L177) |
| O6 | 文档版本 | 新增版本号唯一性校验 | 中 | [DocumentServiceImpl.addVersion](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/DocumentServiceImpl.java#L258-L280) |
| O7 | 批量通知 | WebSocket推送改为并行流 | 低 | [NotificationServiceImpl.sendNotifications](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/NotificationServiceImpl.java#L179-L227) |
| O8 | 工作量审核回写 | `actualWorkload` 累加改为 `@Version` 乐观锁原子累加，消除并发丢失更新风险 | 中 | [TaskServiceImpl.reviewProgress](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/service/impl/TaskServiceImpl.java) |
