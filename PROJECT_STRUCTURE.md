# 项目目录结构说明文档

**项目名称**：IDC 数据中心综合布线项目管理系统
**文档日期**：2026-07-21
**适用版本**：v1.3（部署修复：mysql-grants + tmpfs + Nginx ^~ + 小程序图片路径适配）

---

## 一、项目根目录结构概述

项目采用**三端分离架构**，根目录下按职责划分为 5 个主要子目录：

```
Hbsz/
├── Hbsz_IDC_System/              # Web 前端（Vue3 + Element Plus）
├── Hbsz_IDC_System_end/          # 后端服务（Spring Boot + MyBatis Plus）
├── hbsz_-uniapp_-vue-master/     # 微信小程序（uni-app）
├── docker/                       # Docker 部署配置
├── docs/                         # 项目文档
├── .env.docker                   # Docker 环境变量
├── docker-compose.yml            # Docker Compose 编排文件
├── DEPLOYMENT.md                 # 部署指南
├── PRODUCTION_CHECKLIST.md       # 生产环境检查清单
├── CODE_WIKI.md                  # 代码 Wiki 文档（v1.2）
├── CODE_REVIEW_REPORT.md         # 全量代码审核报告（含修复状态）
├── CODE_REVIEW_REPORT_MINIAPP.md # 微信小程序代码审核报告
├── TECHNICAL_ANALYSIS.md         # 技术分析报告
├── CODE_LOGIC_ANALYSIS.md        # 代码逻辑分析报告
├── CORE_FLOWCHARTS.md            # 核心业务流程图
├── WCAG_COMPLIANCE_REPORT.md    # WCAG 合规性报告
└── PROJECT_STRUCTURE.md          # 本文档
```

### 三端关系

```
用户 → Nginx (80端口)
         ├── / → Web 前端静态资源（dist/）
         └── /api → 后端服务（8080端口）
                       └── MySQL + Redis

微信用户 → 小程序 → 后端 API（/api）
```

---

## 二、Web 前端目录结构（Hbsz_IDC_System/）

技术栈：Vue 3 + Element Plus + Pinia + Vue Router + Vite

```
Hbsz_IDC_System/
├── src/
│   ├── api/                    # API 请求模块（按业务域拆分）
│   ├── components/             # 全局公共组件
│   ├── constants/              # 全局常量定义
│   ├── layouts/                # 布局组件
│   ├── router/                 # 路由配置
│   ├── static/                 # 静态资源（图片等）
│   ├── stores/                 # Pinia 状态管理
│   ├── styles/                 # 全局样式
│   ├── utils/                  # 工具函数
│   ├── views/                  # 页面视图（按模块分目录）
│   ├── App.vue                 # 根组件
│   └── main.js                 # 应用入口
├── .env.development            # 开发环境变量
├── .env.production             # 生产环境变量
├── Dockerfile                  # 前端 Docker 构建文件
├── nginx.conf                  # Nginx 配置
├── vite.config.js              # Vite 构建配置
├── eslint.config.js            # ESLint 配置
├── index.html                  # HTML 入口
└── package.json                # 依赖管理
```

### 2.1 api/ — API 请求模块

按业务域拆分，每个文件对应一个后端 Controller：

| 文件 | 对应后端 Controller | 功能 |
|------|---------------------|------|
| auth.js | AuthController | 登录/登出/刷新Token |
| user.js | UserController | 当前用户信息 |
| project.js | ProjectController | 项目CRUD |
| task.js | TaskController | 任务与进度 |
| document.js | DocumentController | 文档管理 |
| customer.js | CustomerController | 客户管理 |
| statistics.js | StatisticsController | 统计查询 |
| dailyReport.js | DailyReportController | 日报导出 |
| dashboard.js | DashboardController | 工作台数据 |
| notification.js | NotificationController | 通知消息 |
| position.js | PositionController | 岗位管理 |
| system.js | SystemController | 系统配置/健康检查 |
| projectAuth.js | ProjectAuthController | 项目授权申请 |
| projectAuthorization.js | ProjectAuthorizationController | 项目授权审批 |
| projectTeam.js | ProjectTeamController | 项目组管理 |
| approvalFlow.js | ApprovalFlowController | 审批流程图 |

### 2.2 components/ — 全局公共组件

| 组件 | 功能 |
|------|------|
| BaseButton.vue | 统一按钮组件（支持多种variant） |
| StatusTag.vue | 状态标签（项目/任务/文档状态） |
| GanttChart.vue | 甘特图组件（任务进度可视化） |
| EmptyState.vue | 空状态占位组件 |
| TableSkeleton.vue | 表格骨架屏加载组件 |
| ErrorBoundary.vue | 错误边界组件 |
| ApprovalFlowChart.vue | 审批流程图可视化组件 |

### 2.3 stores/ — Pinia 状态管理

| Store | 职责 |
|-------|------|
| user.js | 用户登录状态、Token、角色权限 |
| project.js | 项目列表缓存、筛选条件 |
| system.js | 系统配置缓存 |
| systemConfig.js | 系统配置读写 |
| notification.js | 通知未读数 |
| customer.js | 客户列表缓存 |
| document.js | 文档列表缓存 |
| progress.js | 进度数据缓存 |
| statistics.js | 统计数据缓存 |

### 2.4 utils/ — 工具函数

| 文件 | 功能 |
|------|------|
| request.js | Axios 封装（拦截器、Token注入、错误处理） |
| permission.js | 前端权限校验（hasPermission） |
| feedback.js | 统一操作反馈（ElMessage/ElMessageBox封装） |
| format.js | 格式化工具（日期、文件大小、金额） |
| icons.js | Element Plus 图标注册 |
| cancelToken.js | 请求取消（路由切换时取消pending请求） |
| submitGuard.js | 提交防抖（防止重复提交） |
| websocket.js | WebSocket 连接管理 |
| cryptoStorage.js | 加密存储（敏感数据） |
| sessionMonitor.js | 会话监控（超时提醒） |
| errorReporter.js | 前端错误上报 |
| performance.js | 性能监控 |

### 2.5 views/ — 页面视图

按业务模块分目录组织，每个 `.vue` 文件对应一条路由：

```
views/
├── Login.vue                   # 登录页
├── Dashboard.vue               # 工作台首页
├── NotFound.vue                # 404页面
├── ApprovalFlowDemo.vue        # 审批流程图演示
├── projects/                   # 项目管理
│   ├── ProjectList.vue         #   项目列表
│   ├── ProjectEdit.vue         #   新建/编辑项目
│   ├── ProjectDetail.vue       #   项目详情
│   └── TransferLogs.vue        #   项目转移日志
├── projectAuth/                # 项目授权
│   └── ProjectAuthManagement.vue
├── projectTeam/                # 项目组管理
│   └── ProjectTeamManagement.vue
├── documents/                  # 文档管理
│   └── DocumentManagement.vue
├── progress/                   # 进度管理
│   ├── ProgressManagement.vue  #   进度管理列表
│   └── ProjectProgress.vue     #   项目进度详情（含甘特图）
├── review/                     # 进度审核
│   └── PendingReview.vue
├── my/                         # 个人相关
│   ├── MyTasks.vue             #   我的任务
│   ├── MySubmissions.vue      #   我的填报历史
│   └── RiskBoard.vue          #   风险预警看板
├── customer/                   # 客户管理
│   └── CustomerManagement.vue
├── archive/                    # 归档管理
│   └── ArchiveManagement.vue
├── statistics/                 # 统计分析
│   └── Statistics.vue
├── dailyReport/                # 日报导出
│   └── DailyReport.vue
├── system/                     # 系统管理
│   ├── UserManagement.vue      #   用户管理
│   ├── RoleManagement.vue      #   角色管理
│   ├── PermissionManagement.vue#   权限管理
│   └── PositionManagement.vue  #   岗位管理
└── settings/                   # 系统设置
    └── Settings.vue
```

---

## 三、后端目录结构（Hbsz_IDC_System_end/）

技术栈：Spring Boot 3.0.2 + MyBatis Plus + Java 17 + Redis + MySQL

```
Hbsz_IDC_System_end/
├── src/
│   ├── main/
│   │   ├── java/com/example/hbsz_idc_system_end/
│   │   │   ├── aspect/             # AOP 切面
│   │   │   ├── common/             # 公共模块
│   │   │   ├── config/              # 配置类
│   │   │   ├── controller/          # 控制器层
│   │   │   ├── dto/                 # 数据传输对象
│   │   │   ├── entity/              # 实体类
│   │   │   ├── interceptor/         # 拦截器
│   │   │   ├── mapper/              # MyBatis Mapper 接口
│   │   │   ├── scheduler/           # 定时任务
│   │   │   ├── service/             # 服务层
│   │   │   ├── task/                # 异步任务
│   │   │   ├── utils/               # 工具类
│   │   │   └── HbszIdcSystemEndApplication.java  # 启动类
│   │   └── resources/
│   │       ├── db/migration/        # 数据库迁移脚本
│   │       ├── mapper/              # MyBatis XML 映射
│   │       ├── application.yml      # 主配置
│   │       ├── application-dev.yml  # 开发环境配置
│   │       ├── application-prod.yml # 生产环境配置
│   │       └── logback-spring.xml   # 日志配置
│   └── test/                        # 单元测试
├── exports/                         # 导出文件目录（运行时生成）
├── uploads/                         # 上传文件目录（运行时生成）
├── Dockerfile                       # 后端 Docker 构建文件
└── pom.xml                          # Maven 依赖管理
```

### 3.1 common/ — 公共模块

```
common/
├── annotation/               # 自定义注解
│   ├── RequiresPermission.java   # 权限校验注解
│   ├── OperationLog.java         # 操作日志注解
│   ├── Idempotent.java           # 幂等性注解
│   └── RateLimit.java            # 限流注解
├── constant/                 # 常量定义
│   ├── CommonConstant.java       # 通用常量（角色码等）
│   └── JwtConstant.java          # JWT 常量
├── exception/                # 异常处理
│   ├── BusinessException.java    # 业务异常
│   └── GlobalExceptionHandler.java # 全局异常处理器
└── result/                   # 统一响应
    ├── Result.java               # 统一响应体
    └── ResultCode.java           # 响应码枚举
```

### 3.2 controller/ — 控制器层

25 个 Controller，按业务域对应：

| Controller | 路径前缀 | 功能 |
|------------|----------|------|
| AuthController | /auth | 登录/登出/验证码 |
| UserController | /user | 当前用户信息 |
| UserManageController | /system/users | 用户管理CRUD |
| ProjectController | /projects | 项目CRUD+归档 |
| TaskController | /tasks | 任务与进度填报 |
| DocumentController | /documents | 文档管理 |
| CustomerController | /customers | 客户管理 |
| StatisticsController | /statistics | 统计查询 |
| DailyReportController | /daily-report | 日报导出 |
| DashboardController | /dashboard | 工作台 |
| NotificationController | /notifications | 通知消息 |
| PositionController | /system/positions | 岗位管理 |
| RoleController | /system/roles | 角色管理 |
| PermissionController | /system/permissions | 权限管理 |
| CacheController | /system/cache | 缓存管理 |
| SystemConfigController | /system/config | 系统配置 |
| SystemController | /system | 系统信息/健康检查 |
| ProjectAuthController | /project-auth | 项目授权申请 |
| ProjectAuthorizationController | /project-authorization | 项目授权审批 |
| ProjectTeamController | /project-teams | 项目组管理 |
| ArchiveController | /archive | 归档管理 |
| FileController | /files | 文件上传/下载 |
| ApprovalFlowController | /approval-flow | 审批流程图 |
| CaptchaController | /captcha | 验证码 |
| MonitorController | /monitor | 系统监控 |

### 3.3 service/ — 服务层

采用接口+实现的分层设计：

```
service/
├── I*.java                  # 服务接口（I前缀）
└── impl/
    └── *ServiceImpl.java    # 服务实现
```

| Service | 功能 |
|---------|------|
| AuthServiceImpl | 认证授权 |
| ProjectServiceImpl | 项目管理（含级联删除） |
| TaskServiceImpl | 任务管理（含子任务校验、工作量基准设定与审核回写） |
| DocumentServiceImpl | 文档管理（含版本控制、文档审核） |
| CustomerServiceImpl | 客户管理 |
| NotificationServiceImpl | 通知推送（并行WebSocket） |
| DailyReportServiceImpl | 日报导出（CompletableFuture并行、工作量统计列） |
| StatisticsServiceImpl | 统计查询 |
| FileStorageServiceImpl | 文件存储 |
| ProjectAuthServiceImpl | 项目授权申请 |
| ProjectAuthorizationServiceImpl | 项目授权审批+转移 |
| ProjectTeamServiceImpl | 项目组管理 |
| PermissionServiceImpl | 权限校验 |
| PermissionManageServiceImpl | 权限管理CRUD |
| RoleServiceImpl | 角色管理 |
| UserManageServiceImpl | 用户管理 |
| PositionServiceImpl | 岗位管理 |
| DashboardServiceImpl | 工作台数据聚合（含工作量统计、风险偏差预警） |
| ArchiveServiceImpl | 归档管理 |
| SystemConfigServiceImpl | 系统配置（@Cacheable） |
| AssignmentRevokeService | 授权撤销 |
| NotificationPushService | WebSocket推送 |

### 3.4 entity/ — 实体类

按表前缀分两类：

| 前缀 | 类名 | 对应表 | 说明 |
|------|------|--------|------|
| Biz | BizProject | biz_project | 业务表 |
| Sys | SysUser | sys_user | 系统表 |

所有实体继承 `BaseEntity`（含 created_at、updated_at 字段）。

### 3.5 resources/ — 配置文件

| 文件 | 功能 |
|------|------|
| application.yml | 主配置（context-path=/api） |
| application-dev.yml | 开发环境（本地MySQL/Redis） |
| application-prod.yml | 生产环境（Docker环境变量注入） |
| logback-spring.xml | 日志配置（按天滚动，保留30天） |
| db/migration/ | 数据库迁移脚本（Flyway格式，含 V20260715 工作量字段迁移） |
| mapper/ | MyBatis XML 映射文件 |

---

## 四、小程序目录结构（hbsz_-uniapp_-vue-master/）

技术栈：uni-app + Vue 3 + Pinia

```
hbsz_-uniapp_-vue-master/
├── api/                    # API 请求（与Web端对应）
├── components/             # 公共组件
├── pages/                  # 页面（按模块分目录）
├── static/                 # 静态资源
│   ├── icon/               #   SVG 图标
│   └── tabbar/             #   tabBar PNG 图标（81x81px）
├── store/                  # Pinia 状态管理
├── styles/                 # 全局样式
├── utils/                  # 工具函数
├── App.vue                 # 根组件
├── main.js                 # 应用入口
├── manifest.json           # 小程序配置
├── pages.json              # 页面路由+tabBar配置
└── uni.scss                # 全局SCSS变量
```

### 4.1 pages/ — 页面结构

```
pages/
├── login/login.vue          # 登录
├── index/index.vue          # 工作台首页
├── project/list.vue         # 项目列表
├── project/detail.vue       # 项目详情
├── progress/list.vue        # 进度任务列表
├── progress/report.vue      # 进度填报（核心功能）
├── progress/review.vue      # 进度审核
├── my/tasks.vue             # 我的任务
├── my/submissions.vue       # 我的填报历史
├── pm/project-auth.vue      # 项目授权管理
├── pm/risk-board.vue        # 风险预警看板
├── customer/list.vue        # 客户列表
├── customer/detail.vue      # 客户详情
├── document/list.vue        # 文档列表
├── document/preview.vue     # 文档预览
├── notification/list.vue    # 通知列表
├── approval/flow.vue        # 审批流程图
└── profile/profile.vue     # 个人中心
```

### 4.2 static/ — 静态资源

```
static/
├── icon/          # SVG 功能图标（中文命名，如"项目.svg"、"搜索.svg"）
├── tabbar/        # tabBar 图标（PNG 81x81px，灰色#5C6370/蓝色#1890FF）
├── logo.jpg       # 品牌 Logo
└── logo.png       # 品牌 Logo（透明背景）
```

### 4.3 utils/ — 工具函数

| 文件 | 功能 |
|------|------|
| request.js | uni.request 封装（Token注入、错误处理） |
| auth.js | Token 管理（uni.storage） |
| permission.js | 角色权限判断（基于角色码简化版） |
| format.js | 格式化工具 |
| config.js | 环境配置（API_BASE_URL） |
| ws.js | WebSocket 连接 |
| statusMixin.js | 状态标签 Mixin |

---

## 五、Docker 部署目录（docker/）

```
docker/
└── mysql/
    ├── 01-schema.sql        # 数据库表结构（46个外键约束）+ hbsz_app 用户创建
    ├── 02-seed-data.sql     # 种子数据（4角色+76权限+角色映射）
    ├── init-grants.sql      # hbsz_app 权限初始化（DDL+performance_schema+SYSTEM_USER）
    └── my.cnf               # MySQL 配置（utf8mb4_0900_ai_ci）
```

### 部署相关文件（根目录）

| 文件 | 功能 |
|------|------|
| docker-compose.yml | 容器编排（MySQL+mysql-grants+Redis+Backend+Frontend） |
| .env.docker | 环境变量（数据库密码、JWT密钥等） |
| .dockerignore | Docker 构建忽略文件 |

---

## 六、文档目录（docs/）

| 文件 | 内容 |
|------|------|
| USER_GUIDE_ADMIN.md | 系统管理员使用说明 |
| USER_GUIDE_PM.md | 项目经理使用说明 |
| USER_GUIDE_ENGINEER.md | 工程师使用说明 |
| USER_GUIDE_USER.md | 普通用户使用说明 |

### 根目录文档

| 文件 | 内容 |
|------|------|
| DEPLOYMENT.md | 部署指南 |
| PRODUCTION_CHECKLIST.md | 生产环境检查清单 |
| TECHNICAL_ANALYSIS.md | 技术分析与改进建议 |
| CODE_LOGIC_ANALYSIS.md | 代码逻辑分析报告 |
| CORE_FLOWCHARTS.md | 核心业务流程图 |
| WCAG_COMPLIANCE_REPORT.md | WCAG 2.1 合规性报告 |
| PROJECT_STRUCTURE.md | 本文档 |

---

## 七、关键文件作用解释

### 7.1 根目录关键文件

| 文件 | 作用 |
|------|------|
| docker-compose.yml | 定义 6 个容器（mysql/mysql-grants/redis/backend/frontend）的编排配置，包含端口映射、数据卷、tmpfs、健康检查、资源限制 |
| .env.docker | 敏感信息（数据库密码、JWT密钥、Redis密码），不纳入版本控制 |

### 7.2 Web 前端关键文件

| 文件 | 作用 |
|------|------|
| vite.config.js | Vite 构建配置：alias 别名、proxy 代理、自动导入 Element Plus |
| src/router/index.js | 路由配置：40+ 路由，基于 meta.permission 控制菜单可见性 |
| src/utils/request.js | Axios 封装：请求/响应拦截器、Token 自动注入、401 跳转登录、skipCancel 控制 |
| src/layouts/MainLayout.vue | 全局布局：侧边栏菜单+顶部栏+面包屑+内容区，含 skip-to-content 无障碍链接 |
| src/styles/global.css | 全局样式：CSS 变量、WCAG 合规色彩、骨架屏、焦点环、响应式断点 |
| nginx.conf | Nginx 配置：SPA 路由回退、/api 反代后端、WebSocket 代理、100MB 上传限制 |

### 7.3 后端关键文件

| 文件 | 作用 |
|------|------|
| HbszIdcSystemEndApplication.java | Spring Boot 启动类，含 @MapperScan、@EnableScheduling |
| application-prod.yml | 生产配置：Druid(max-active=50)、Lettuce(max-wait=3000ms)、Tomcat(max=200) |
| interceptor/JwtAuthInterceptor.java | JWT 认证拦截器：排除 /system/config 等公开路径，异常日志 WARN 级别 |
| aspect/PermissionAspect.java | 权限切面：拦截 @RequiresPermission 注解，校验当前用户权限 |
| aspect/OperationLogAspect.java | 操作日志切面：自动记录增删改操作到 sys_operation_log |
| aspect/RateLimitAspect.java | 限流切面：基于 Redis 的接口限流 |
| aspect/IdempotentAspect.java | 幂等切面：防止重复提交 |
| utils/ConvertUtils.java | 类型转换工具：java.sql.Date/Date 格式化、Map 类型转换 |
| logback-spring.xml | 日志配置：按天滚动、保留30天、ERROR 单独文件 |

### 7.4 小程序关键文件

| 文件 | 作用 |
|------|------|
| pages.json | 页面路由+tabBar（4个tab：工作台/项目/文档/我的） |
| manifest.json | 小程序配置（AppID、基础库版本、权限声明） |
| uni.scss | 全局 SCSS 变量（WCAG 合规色彩值） |
| utils/permission.js | 角色权限判断（admin/pm/engineer/user 四级） |

---

## 八、文件命名规范

### 8.1 通用规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Java 类 | UpperCamelCase | `ProjectServiceImpl.java` |
| Java 方法/变量 | lowerCamelCase | `getProjectById()` |
| Vue 组件文件 | UpperCamelCase | `BaseButton.vue` |
| Vue 页面文件 | UpperCamelCase | `ProjectList.vue` |
| JS 工具文件 | lowerCamelCase 或 camelCase | `request.js`、`format.js` |
| CSS/SCSS 文件 | kebab-case | `global.css`、`common.scss` |
| SQL 文件 | 数字前缀+kebab-case | `01-schema.sql`、`02-seed-data.sql` |
| 数据库迁移 | V{日期}__{描述}.sql | `V20260704__project_authorization_transfer.sql` |
| Markdown 文档 | UPPER_SNAKE_CASE | `USER_GUIDE_ADMIN.md` |
| 配置文件 | kebab-case 或点分 | `application-prod.yml`、`.env.docker` |

### 8.2 后端分层命名约定

| 层级 | 前缀/后缀 | 示例 |
|------|----------|------|
| Controller | Controller | `ProjectController.java` |
| Service 接口 | I 前缀 | `IProjectService.java` |
| Service 实现 | ServiceImpl 后缀 | `ProjectServiceImpl.java` |
| Mapper 接口 | Mapper 后缀 | `BizProjectMapper.java` |
| Entity | Biz/Sys 前缀 | `BizProject.java`、`SysUser.java` |
| DTO（入参） | DTO 后缀 | `ProjectSaveDTO.java` |
| VO（出参） | VO 后缀 | `ProjectVO.java` |
| 查询 DTO | QueryDTO 后缀 | `ProjectQueryDTO.java` |

### 8.3 前端命名约定

| 类型 | 规范 | 示例 |
|------|------|------|
| API 文件 | 与后端 Controller 对应 | `project.js` → `ProjectController` |
| Store 文件 | 与业务域对应 | `project.js` |
| 视图目录 | kebab-case | `projectAuth/`、`dailyReport/` |
| 组件名 | 语义化 UpperCamelCase | `EmptyState`、`TableSkeleton` |

### 8.4 小程序命名约定

| 类型 | 规范 | 示例 |
|------|------|------|
| 页面文件 | kebab-case 或 lowerCamelCase | `list.vue`、`report.vue` |
| 图标文件 | 中文语义命名 | `项目.svg`、`搜索.svg` |
| TabBar 图标 | {功能}-{状态}.png | `project-active.png` |

---

## 九、目录结构设计逻辑与原则

### 9.1 设计原则

1. **三端分离**：Web、后端、小程序各自独立目录，互不干扰，可独立构建部署
2. **按职责分层**：后端严格遵循 Controller → Service → Mapper 三层架构
3. **按业务域分模块**：前端 views/ 和后端 controller/ 均按业务域组织
4. **公共能力下沉**：通用组件、工具函数、常量定义抽取到独立目录
5. **配置外置**：环境变量通过 .env 文件注入，不硬编码在代码中
6. **文档与代码分离**：所有文档统一放在 docs/ 或根目录，不混入源码目录

### 9.2 后端分层逻辑

```
请求 → JwtAuthInterceptor（认证）
      → Controller（参数校验）
      → PermissionAspect（权限校验）
      → OperationLogAspect（日志记录）
      → Service（业务逻辑）
      → Mapper（数据访问）
      → MySQL
```

### 9.3 前端路由权限逻辑

```
用户登录 → 获取角色权限列表
         → router.beforeEach 检查 meta.permission
         → 有权限 → 渲染页面
         → 无权限 → 重定向到工作台
         → 侧边栏菜单根据权限过滤显示
```

### 9.4 数据库外键策略

| 策略 | 适用场景 | 示例 |
|------|----------|------|
| CASCADE | 业务数据跟随删除 | biz_project_team.project_id |
| SET NULL | 审计/元数据保留置空 | biz_archive_record.operator_id |
| NO ACTION | 树形结构防误删父节点 | sys_department.parent_id |

### 9.5 静态资源策略

- **Web 前端**：Vite 构建后产物在 `dist/`，由 Nginx 托管静态资源
- **小程序**：`static/` 目录存放图标和 tabbar 图片，不经过构建处理
- **后端**：`uploads/` 目录存储用户上传文件，`exports/` 存储导出的 Excel

### 9.6 环境配置策略

| 环境 | 配置文件 | 特点 |
|------|----------|------|
| 开发 | `.env.development` / `application-dev.yml` | 本地 MySQL/Redis，热更新 |
| 生产 | `.env.production` / `application-prod.yml` | Docker 环境变量注入 |
| Docker | `.env.docker` | 敏感信息（密码、密钥） |

---

## 十、项目技术栈速查

| 端 | 框架 | 版本 | 构建工具 |
|----|------|------|----------|
| Web 前端 | Vue 3 + Element Plus | Vue 3.5+ / EP 2.x | Vite 5.x |
| 后端 | Spring Boot + MyBatis Plus | 3.0.2 / 3.5.x | Maven 3.9.x |
| 小程序 | uni-app + Vue 3 | HBuilderX / CLI | uni-app build |
| 数据库 | MySQL | 8.0 | — |
| 缓存 | Redis | 7.x | — |
| 部署 | Docker Compose | 24.x | Docker |
| 反向代理 | Nginx | 1.25+ | — |
