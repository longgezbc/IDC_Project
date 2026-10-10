# 技术栈详细说明文档

> **项目**：IDC 数据中心综合布线项目管理系统
> **文档日期**：2026-07-25
> **适用版本**：v1.3
> **三端架构**：Web 前端 + 后端 API + 微信小程序

---

## 目录

1. [技术栈总览](#1-技术栈总览)
2. [后端技术栈](#2-后端技术栈)
3. [Web 前端技术栈](#3-web-前端技术栈)
4. [微信小程序技术栈](#4-微信小程序技术栈)
5. [数据库与中间件](#5-数据库与中间件)
6. [部署与运维技术](#6-部署与运维技术)
7. [关键技术实现点](#7-关键技术实现点)
8. [版本依赖全景表](#8-版本依赖全景表)

---

## 1. 技术栈总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                          客户端层                                    │
│  ┌────────────────────┐  ┌────────────────────┐  ┌──────────────┐ │
│  │   Web 管理后台      │  │   微信小程序        │  │              │ │
│  │ Vue 3.4 + Vite 5   │  │  uni-app + Vue 3   │  │              │ │
│  │ Element Plus 2.6   │  │  原生组件 + Pinia   │  │              │ │
│  └─────────┬──────────┘  └─────────┬──────────┘  └──────────────┘ │
│            │                        │                              │
└────────────┼────────────────────────┼──────────────────────────────┘
             │ HTTPS /api             │ HTTPS /api
             │ WebSocket /ws          │ WebSocket /ws
┌────────────┼────────────────────────┼──────────────────────────────┐
│            ▼                        ▼           网络层              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │             Nginx 1.27 (反向代理 + 静态资源)                  │  │
│  └────────────────────────┬─────────────────────────────────────┘  │
└───────────────────────────┼────────────────────────────────────────┘
                            │
┌───────────────────────────┼────────────────────────────────────────┐
│                           ▼     应用层                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │          Spring Boot 3.0.2 + Java 17                          │  │
│  │  ┌───────────────┐  ┌──────────────┐  ┌─────────────────┐    │  │
│  │  │ MyBatis Plus  │  │ Spring Cache │  │ Spring AOP      │    │  │
│  │  │   3.5.3.1     │  │  + Redis     │  │ (切面/事务)      │    │  │
│  │  └───────┬───────┘  └──────┬───────┘  └─────────────────┘    │  │
│  │          │                 │                                  │  │
│  │  ┌───────▼─────┐  ┌───────▼────────┐  ┌─────────────────┐  │  │
│  │  │  Druid 连接池 │  │  JWT (jjwt)    │  │  WebSocket STOMP │  │  │
│  │  │   1.2.16     │  │   0.11.5       │  │  站内通知推送     │  │  │
│  │  └───────┬─────┘  └────────────────┘  └─────────────────┘  │  │
│  └──────────┼──────────────────────────────────────────────────┘  │
└─────────────┼─────────────────────────────────────────────────────┘
              │
┌─────────────┼─────────────────────────────────────────────────────┐
│             ▼              数据层                                   │
│  ┌──────────────────┐    ┌──────────────────┐                     │
│  │  MySQL 8.0.40    │    │  Redis 7.2        │                     │
│  │  (utf8mb4_0900)  │    │  (AOF + LRU)      │                     │
│  └──────────────────┘    └──────────────────┘                     │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. 后端技术栈

后端服务基于 **Spring Boot 3.0.2 + Java 17** 构建，采用经典三层架构（Controller / Service / Mapper）。

### 2.1 核心框架

| 技术 | 版本 | 用途 | 配置位置 |
|------|------|------|----------|
| **Spring Boot** | 3.0.2 | 应用主框架，自动配置、内嵌 Tomcat | [pom.xml](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/pom.xml) |
| **Java** | 17 (LTS) | 编程语言，启用 Records/Sealed Classes 等新特性 | pom.xml `<java.version>` |
| **Spring Web MVC** | 6.0.4 | RESTful API 控制器层 | spring-boot-starter-web |
| **Spring Validation** | 8.0.2 | 参数校验（`@NotBlank`、`@Valid` 等） | spring-boot-starter-validation |
| **Spring AOP** | 6.0.4 | 切面编程，实现权限校验、操作日志、幂等控制 | spring-boot-starter-aop |
| **Spring WebSocket** | 6.0.4 | 站内通知实时推送（STOMP 协议） | spring-boot-starter-websocket |
| **Spring Actuator** | 3.0.2 | 健康检查、应用监控端点 | `/api/actuator/health` |

**配置示例**（[application.yml](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/resources/application.yml)）：

```yaml
server:
  servlet:
    context-path: /api          # 所有接口统一前缀
  forward-headers-strategy: framework   # 信任 Nginx 转发头

spring:
  application:
    name: hbsz-idc-system-end
```

### 2.2 数据访问层

| 技术 | 版本 | 用途 | 关键设计 |
|------|------|------|----------|
| **MyBatis-Plus** | 3.5.3.1 | ORM 框架，CRUD 增强、条件构造器、分页插件 | `map-underscore-to-camel-case: true`（但 Map 结果类型不生效，需用反引号别名） |
| **Druid** | 1.2.16 | 数据库连接池，提供 SQL 监控、慢查询统计 | `max-active: 30` |
| **MySQL Connector/J** | 8.0.31 | MySQL JDBC 驱动 | `runtime` scope |
| **Flyway** | 9.5.1 | 数据库版本迁移管理 | `baseline-on-migrate: true`，`validate-on-migrate: false` |

**MyBatis-Plus 配置**：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.example.hbsz_idc_system_end.entity
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false              # 关闭二级缓存，统一由 Redis 管理
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
  global-config:
    db-config:
      id-type: auto                   # 主键自增
      logic-delete-field: deleted     # 逻辑删除字段
      logic-delete-value: 1
      logic-not-delete-value: 0
```

**Flyway 配置**：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true         # 在已有数据库上启用
    baseline-version: 0               # 当前状态标记为版本 0
    validate-on-migrate: false        # 跳过历史脚本 checksum 校验
    table: flyway_schema_history
```

**迁移脚本命名规范**：`V{YYYYMMDD}__{description}.sql` 或 `V{YYYYMMDD}.{n}__{description}.sql`（同日多版本使用点号分隔，不允许下划线后缀）。

### 2.3 缓存与会话

| 技术 | 版本 | 用途 | 配置说明 |
|------|------|------|----------|
| **Spring Data Redis** | 3.0.2 | 缓存抽象层，配合 `@Cacheable` 注解 | Lettuce 客户端，连接池 `max-wait: 2000ms` |
| **Redis** | 7.2 | 内存数据库，缓存用户权限、Token 黑名单、系统配置 | `maxmemory 48mb`，`allkeys-lru` 淘汰策略 |
| **Apache Commons Pool2** | 2.11.1 | Redis 连接池底层实现 | commons-pool2 依赖 |

**缓存使用场景**：

```java
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    @Cacheable(value = "systemConfig", key = "#key")
    public String getConfigValue(String key) {
        // 首次查询DB，后续命中缓存
        return configMapper.selectByKey(key);
    }
}
```

**Redis 关键存储结构**：

| Key 前缀 | 数据类型 | 用途 | TTL |
|----------|----------|------|-----|
| `jwt:blacklist:{token}` | String | 登出 Token 黑名单 | Token 剩余有效期 |
| `user:roles:{userId}` | Set | 用户角色缓存 | 30 分钟 |
| `user:perms:{userId}` | Set | 用户权限缓存 | 30 分钟 |
| `systemConfig:{key}` | String | 系统配置缓存 | 永久（手动失效） |

### 2.4 认证与安全

| 技术 | 版本 | 用途 | 实现要点 |
|------|------|------|----------|
| **JJWT** | 0.11.5 | JWT 生成与解析 | 双 Token 机制（access 30min + refresh 7d） |
| **BCrypt** | Spring Security 实现 | 密码加密存储 | 内置于 `spring-security-crypto` |
| **Hutool** | 5.8.22 | 工具集，用于验证码生成、ID 生成等 | `cn.hutool:hutool-all` |

**JWT 双 Token 设计**：

```java
public class JwtUtils {
    // accessToken：30 分钟过期（1800000ms）
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return Jwts.builder()
            .setIssuer(issuer)        // 签发方校验，防止跨服务 token 误用
            .setAudience(audience)     // 受众校验
            .setSubject(username)
            .claim("userId", userId)
            .claim("roles", roles)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 1800000))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }

    // refreshToken：7 天过期（604800000ms）
    public String generateRefreshToken(Long userId) { ... }
}
```

**JWT 认证拦截器**（[JwtAuthInterceptor](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/src/main/java/com/example/hbsz_idc_system_end/interceptor)）：

- 排除 `/system/config` 等公开端点
- 三重校验：Token 有效性 → 黑名单检查 → 权限校验
- `UserContext` 基于 ThreadLocal，在 `afterCompletion` 中清理，防止泄漏

### 2.5 API 文档

| 技术 | 版本 | 用途 | 访问地址 |
|------|------|------|----------|
| **Knife4j** | 4.1.0 | Swagger 增强 UI，提供 API 文档在线浏览 | `/api/doc.html` |
| **SpringDoc OpenAPI** | 2.0.0 | OpenAPI 3 规范实现 | `/api/v3/api-docs` |

**配置**：

```yaml
knife4j:
  enable: true
  setting:
    language: zh_cn
    enable-version: true

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  group-configs:
    - group: default
      packages-to-scan: com.example.hbsz_idc_system_end.controller
```

### 2.6 报表与导出

| 技术 | 版本 | 用途 | 关键配置 |
|------|------|------|----------|
| **EasyExcel** | 3.3.4 | Excel 报表导出（日报、工作量统计） | 注解驱动 `@ExcelProperty`，多级表头 `List<List<String>>` |

**日报导出特性**：
- 每个项目一个 Sheet，包含项目信息头 + 任务详情表
- 工作量统计列（计划/实际/单位）
- 使用 `CompletableFuture` 并行查询多项目数据
- 同名项目 Sheet 自动加数字后缀（如 `(2)`）避免冲突

### 2.7 监控与运维

| 技术 | 版本 | 用途 | 配置 |
|------|------|------|------|
| **Spring Actuator** | 3.0.2 | 健康检查、应用信息端点 | `/api/actuator/health`（容器健康探针） |
| **Micrometer Prometheus** | 1.10.2 | 监控指标导出，配合 Prometheus + Grafana | micrometer-registry-prometheus |
| **Logback** | Spring Boot 内置 | 日志框架，按天滚动，保留 30 天 | `logback-spring.xml` |

**JVM 参数**（生产环境）：

```
-Xms128m -Xmx256m -XX:GCTimeRatio=4
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/heapdump.hprof
```

### 2.8 Lombok 与工具库

| 技术 | 版本 | 用途 |
|------|------|------|
| **Lombok** | 1.18.26 | `@Data`、`@Builder`、`@Slf4j` 等注解，减少样板代码 |
| **Hutool** | 5.8.22 | 通用工具集（验证码、ID 生成、加密等） |

---

## 3. Web 前端技术栈

Web 前端基于 **Vue 3.4 + Vite 5 + Element Plus 2.6** 构建，采用组合式 API（Composition API）。

### 3.1 核心框架

| 技术 | 版本 | 用途 | 关键配置 |
|------|------|------|----------|
| **Vue** | 3.4.21 | 视图框架，采用 Composition API | [package.json](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/package.json) |
| **Vue Router** | 4.3.0 | 路由管理，支持懒加载、守卫 | history 模式 |
| **Pinia** | 2.1.7 | 状态管理（9 个 Store） | 配合 `pinia-plugin-persistedstate` 持久化 |
| **Vite** | 5.2.0 | 构建工具与开发服务器 | [vite.config.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/vite.config.js) |
| **ESLint** | 9.0.0 | 代码规范检查 | 配合 `eslint-plugin-vue` |

**Vite 构建优化**（手动分包策略）：

```javascript
build: {
  minify: 'terser',
  terserOptions: {
    compress: {
      drop_console: 'production',    // 生产环境移除 console
      drop_debugger: 'production'
    }
  },
  sourcemap: mode !== 'production',
  rollupOptions: {
    output: {
      manualChunks: {
        'vue-vendor': ['vue', 'vue-router', 'pinia'],
        'element-vendor': ['element-plus', '@element-plus/icons-vue'],
        'echarts-vendor': ['echarts'],
        'utils-vendor': ['axios', 'dayjs']
      }
    }
  }
}
```

**开发服务器代理**：

```javascript
server: {
  port: 5173,
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true }
  }
}
```

### 3.2 UI 组件库

| 技术 | 版本 | 用途 | 注意事项 |
|------|------|------|----------|
| **Element Plus** | 2.6.2 | 企业级 UI 组件库 | 所有图标必须在 `src/utils/icons.js` 显式注册 |
| **@element-plus/icons-vue** | 2.3.1 | 图标库 | `Date` 图标因冲突改为 `Histogram` |
| **element-china-area-data** | 6.1.0 | 省市区级联数据 | 使用默认 `{value, label, children}` 结构，不要自定义 props |

### 3.3 工具库

| 技术 | 版本 | 用途 |
|------|------|------|
| **Axios** | 1.18.1 | HTTP 请求库，封装请求/响应拦截器、Token 注入、取消队列 |
| **Day.js** | 1.11.10 | 日期处理（替代 Moment.js，体积更小） |
| **ECharts** | 5.5.0 | 图表可视化（甘特图、统计图表） |

**Axios 封装特性**（[src/utils/request.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/utils)）：

- 请求拦截器：FormData 自动删除 `Content-Type`（让浏览器生成 boundary）
- `skipCancel: true`：系统配置请求在路由切换时不被取消
- Token 刷新协调器：`TokenRefreshCoordinator` 处理并发 401，"锁 + 队列"机制确保只刷新一次
- `withCancel`：基于 AbortController，`finally` 中仅在 `currentController === controller` 时清空（防竞态）

### 3.4 实时通信

| 技术 | 版本 | 用途 | 实现要点 |
|------|------|------|----------|
| **@stomp/stompjs** | 7.0.0 | STOMP 协议客户端，站内通知推送 | 配合后端 Spring WebSocket |
| **sockjs-client** | 1.6.1 | WebSocket 降级方案，兼容旧浏览器 | 仅在 WebSocket 不可用时启用 |

### 3.5 状态管理（9 个 Pinia Store）

| Store | 职责 | 持久化 |
|-------|------|--------|
| `user.js` | 登录态、Token、角色权限 | 是（localStorage） |
| `project.js` | 项目列表、筛选条件 | 否 |
| `system.js` | 系统配置缓存 | 否 |
| `systemConfig.js` | 系统配置读写 | 否 |
| `notification.js` | 通知未读数 | 否 |
| `customer.js` | 客户列表缓存 | 否 |
| `document.js` | 文档列表缓存 | 否 |
| `progress.js` | 进度数据缓存 | 否 |
| `statistics.js` | 统计数据缓存 | 否 |

### 3.6 关键组件

| 组件 | 功能 |
|------|------|
| `BaseButton.vue` | 统一按钮组件，支持 variant：`link`/`link-success`/`link-warning`/`link-danger` |
| `StatusTag.vue` | 状态标签，支持项目/任务/文档状态映射 |
| `GanttChart.vue` | 甘特图组件，里程碑标签 `max-width` + 省略号，关键节点用 `box-shadow inset` |
| `CrudTable.vue` | 通用 CRUD 表格，支持树表（5 个 props：`stripe`/`border`/`rowKey`/`treeProps`/`defaultExpandAll`） |
| `ApprovalFlowChart.vue` | 审批流程图可视化 |
| `ErrorBoundary.vue` | 错误边界组件 |

---

## 4. 微信小程序技术栈

小程序基于 **uni-app + Vue 3**，与 Web 端共用同一套后端 API。

### 4.1 核心框架

| 技术 | 版本 | 用途 | 说明 |
|------|------|------|------|
| **uni-app** | 3.x | 跨端框架，编译到微信小程序 | `vueVersion: "3"` |
| **Vue** | 3.x | 视图框架 | 组合式 API |
| **Pinia** | 2.x | 状态管理（2 个 Store：user/project） | 与 Web 端保持一致 |

**小程序特性**：
- `pages.json` 配置路由 + tabBar（4 个 tab，PNG 图标 81×81px）
- 灰色 `#5C6370` 默认态，蓝色 `#1890FF` 选中态
- `lazyCodeLoading: "requiredComponents"` 按需加载组件代码

### 4.2 小程序 API 封装

| 文件 | 功能 | 实现要点 |
|------|------|----------|
| `utils/request.js` | `uni.request` 封装 | Token 注入、错误处理、Token 刷新队列机制 |
| `utils/auth.js` | Token 管理 | `uni.getStorageSync` 明文存储（root 设备风险已知） |
| `utils/permission.js` | 权限判断 | 简化的 PM_ONLY_PERMS 白名单（与 Web 端 RBAC 简化版） |
| `utils/config.js` | 环境配置 | `API_BASE_URL` 必须使用 HTTPS 域名 |
| `utils/ws.js` | WebSocket 连接 | 原生 WebSocket + 轮询降级（小程序不支持 STOMP） |
| `utils/format.js` | 格式化工具 | 日期、文件大小格式化 |

### 4.3 安全配置

**Android 权限**（仅保留实际需要的）：

```json
"distribute": {
  "android": {
    "permissions": [
      "<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\"/>",
      "<uses-permission android:name=\"android.permission.ACCESS_WIFI_STATE\"/>"
    ]
  }
}
```

**生产环境配置**：

```json
"mp-weixin": {
  "setting": {
    "urlCheck": true,        // 生产环境必须开启域名校验
    "es6": true,
    "postcss": true,
    "minified": true
  }
}
```

### 4.4 与 Web 端对比

| 维度 | 小程序 | Web 端 |
|------|--------|--------|
| 框架 | uni-app (Vue 3) | Vue 3.4 + Vite |
| 路由 | `pages.json` + `uni.navigateTo` | Vue Router 4 |
| 状态管理 | 2 个 Store | 9 个 Pinia Store |
| HTTP | `uni.request` | Axios |
| WebSocket | 原生 WebSocket + 轮询降级 | STOMP over WebSocket |
| 文件上传 | `uni.uploadFile` | FormData + Axios |
| 权限 | 简化白名单 | 完整 RBAC |

---

## 5. 数据库与中间件

### 5.1 MySQL 数据库

| 配置项 | 值 | 说明 |
|--------|-----|------|
| **版本** | 8.0.40 (LTS) | 固定版本，避免拉取含新 CVE 的滚动 tag |
| **字符集** | `utf8mb4` | 支持 emoji 与 4 字节字符 |
| **排序规则** | `utf8mb4_0900_ai_ci` | MySQL 8.0 默认，基于 Unicode 9.0 |
| **最大连接数** | 100 | 适配 2 核 2G 服务器 |
| **InnoDB Buffer Pool** | 128M | 数据库主要内存消耗 |
| **慢查询日志** | 开启，阈值 3s | 用于性能调优 |
| **skip-name-resolve** | 启用 | 跳过 DNS 反查，加速连接 |

**SQL 编写规范**：
- Map 结果类型别名用反引号包裹 camelCase（如 `` `newCount` ``），否则 MySQL 转小写
- 避免 OR 条件，改用 `UNION + LIMIT` 防止索引失效
- 批量操作使用 `batchInsert`，避免循环单条插入

### 5.2 Redis 缓存

| 配置项 | 值 | 说明 |
|--------|-----|------|
| **版本** | 7.2-alpine | 固定版本 |
| **最大内存** | 48MB | LRU 淘汰策略 |
| **淘汰策略** | `allkeys-lru` | 所有 Key 参与 LRU |
| **持久化** | AOF + RDB 混合 | `appendfsync everysec`（每秒刷盘） |
| **密码** | 必须配置 | 通过 `--requirepass` 设置 |
| **SCAN 命令** | 强制使用 | 禁止 `KEYS` 命令，避免阻塞 |

### 5.3 Tomcat 内嵌容器

| 配置项 | 值 | 说明 |
|--------|-----|------|
| **最大线程数** | 200 | `server.tomcat.threads.max` |
| **最小空闲线程** | 20 | `server.tomcat.threads.min-spare` |
| **最大吞咽大小** | 100MB | 与文件上传限制一致 |

---

## 6. 部署与运维技术

### 6.1 容器化

| 技术 | 用途 | 配置文件 |
|------|------|----------|
| **Docker** | 容器运行时 | 各模块 Dockerfile |
| **Docker Compose** | 多容器编排 | [docker-compose.yml](file:///c:/Users/QianLong/Desktop/Hbsz/docker-compose.yml) |

**5 个服务容器**：

| 服务 | 容器名 | 镜像 | 端口 | 内存限制 |
|------|--------|------|------|----------|
| MySQL | hbsz-mysql | mysql:8.0.40 | 3306（内部） | 400M |
| MySQL Grants | hbsz-mysql-grants | mysql:8.0.40 | — | 48M |
| Redis | hbsz-redis | redis:7.2-alpine | 6379（内部） | 80M |
| 后端 | hbsz-backend | 本地构建 | 8080:8080 | 384M |
| 前端 | hbsz-frontend | 本地构建 | 80:8080 | 64M |

**容器安全加固**：

```yaml
security_opt:
  - no-new-privileges:true   # 防止子进程提权
cap_drop:
  - ALL                       # 移除所有 Linux capabilities
read_only: true               # 文件系统只读
tmpfs:                        # 临时可写空间
  - /tmp:size=64M
  - /var/cache/fontconfig:size=16M    # 验证码字体缓存
  - /app/.cache/fontconfig:size=16M
  - /app/.fontconfig:size=16M
  - /app/exports:size=128M            # 日报导出目录
```

### 6.2 mysql-grants 一次性容器

**解决的问题**：MySQL 数据卷复用时，`docker-entrypoint-initdb.d` 中的初始化脚本不会重新执行，导致权限不更新。

**实现**：

```yaml
mysql-grants:
  image: mysql:8.0.40
  depends_on:
    mysql:
      condition: service_healthy
  entrypoint:
    - bash
    - -c
    - |
      # 等待 MySQL 就绪后执行 init-grants.sql
      mysql -h mysql -u root -v < /init-grants.sql
  restart: "no"
```

**[init-grants.sql](file:///c:/Users/QianLong/Desktop/Hbsz/docker/mysql/init-grants.sql) 授权内容**：

```sql
-- DDL 权限（Flyway 需要 CREATE/ALTER/DROP）
GRANT ALL PRIVILEGES ON hbsz_idc_system.* TO 'hbsz_app'@'%';

-- performance_schema SELECT 权限（Flyway 读取系统变量）
GRANT SELECT ON performance_schema.* TO 'hbsz_app'@'%';

-- SYSTEM_USER 权限（MySQL 8.0 动态 SQL 需要）
GRANT SYSTEM_USER ON *.* TO 'hbsz_app'@'%';
```

### 6.3 Nginx 反向代理

| 配置项 | 值 | 说明 |
|--------|-----|------|
| **版本** | 1.27-alpine | 固定版本 |
| **监听端口** | 8080（容器内） | 宿主机 80 映射 |
| **运行用户** | nginx（非 root） | 监听非特权端口 |

**关键路由规则**（[nginx.conf](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/nginx.conf)）：

```nginx
# 后端 API 反向代理（必须用 ^~ 前缀匹配）
location ^~ /api/ {
    proxy_pass http://backend:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

# WebSocket 反向代理
location ^~ /ws/ {
    proxy_pass http://backend:8080/api/ws/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}

# 静态资源（正则匹配）
location ~* \.(png|jpg|jpeg|gif|ico|css|js)$ {
    expires 30d;
    add_header Cache-Control "public, immutable";
}
```

> **关键**：`/api/` 必须使用 `^~` 前缀匹配，优先级高于正则匹配，防止 `/api/files/preview/xxx.png` 被静态资源规则截获返回 404。

### 6.4 安全头配置

```nginx
# CSP 内容安全策略
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:;";

# 防点击劫持
add_header X-Frame-Options "SAMEORIGIN";

# Referrer 策略
add_header Referrer-Policy "strict-origin-when-cross-origin";

# HSTS（仅 HTTPS）
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

---

## 7. 关键技术实现点

### 7.1 RBAC 权限模型

**数据模型**：

```
sys_user ──┐
           │ sys_user_role (多对多)
sys_role ──┤
           │ sys_role_permission (多对多)
sys_permission ──┘
```

**实现要点**：

- 4 个角色：admin / 项目经理（role_id=2）/ 工程师 / 客户
- 76 个权限码：如 `system:user:query`、`statistics:query`
- 后端 `@RequiresPermission("task:review")` 注解 + `PermissionAspect` 切面
- 前端 `hasPermission(permCode)` 指令，`value == null` 才放行（避免 `''`/`0`/`false` 误判）
- 项目经理（role_id=2）必须拥有 `system:user:query`（ID=11）才能访问用户列表

### 7.2 JWT 双 Token 机制

```
登录 → 签发 accessToken（30min）+ refreshToken（7d）
        ↓
请求携带 accessToken
        ↓
accessToken 过期 → 调用 /auth/refresh
        ↓
TokenRefreshCoordinator 协调并发刷新（锁 + 队列）
        ↓
refreshToken 过期 → 跳转登录页
```

**登出黑名单**：Token 写入 Redis，TTL = 剩余有效期，请求时校验。

### 7.3 Spring AOP 自调用事务失效问题

**问题**：`@Transactional` 通过 AOP 代理实现，`this.method()` 自调用绕过代理，事务失效。

**解决方案**：自注入代理：

```java
@Service
public class ProjectServiceImpl implements ProjectService {

    @Lazy
    @Autowired
    private ProjectService self;   // 自注入代理

    public void outerMethod() {
        self.transactionalMethod();   // 通过代理调用，事务生效
    }

    @Transactional
    public void transactionalMethod() { ... }
}
```

### 7.4 CAS 原子更新防并发

**问题**：先 SELECT 后 UPDATE 的模式在并发场景下不安全，可能丢失更新。

**解决方案**：CAS（Compare-And-Swap）原子更新：

```sql
-- 审核进度日志：WHERE status = 'pending' 确保只更新待审核的记录
UPDATE biz_progress_log
SET review_status = 'approved', reviewer_id = ?, review_time = NOW()
WHERE id = ? AND review_status = 'pending';
-- 受影响行数为 0 表示并发冲突或状态已变更
```

**项目进度同步**（原子更新，防并发覆盖）：

```sql
UPDATE biz_project
SET progress = (
    SELECT IFNULL(AVG(progress), 0)
    FROM biz_task
    WHERE project_id = ? AND deleted = 0
)
WHERE id = ?;
```

### 7.5 Flyway 迁移管理

**脚本规范**：

| 命名 | 说明 | 示例 |
|------|------|------|
| `V{YYYYMMDD}__{desc}.sql` | 标准命名 | `V20260715__add_project_total_workload.sql` |
| `V{YYYYMMDD}.{n}__{desc}.sql` | 同日多版本 | `V20260704.1__add_index.sql` |
| ❌ `V{YYYYMMDD}_{n}__{desc}.sql` | 下划线被解析为描述，版本仍冲突 | — |

**幂等性要求**：所有脚本使用 `information_schema` 存在性检查：

```sql
-- 检查列是否存在，不存在才添加
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'biz_project'
      AND column_name = 'total_workload'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE biz_project ADD COLUMN total_workload DECIMAL(10,2)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

**注意**：
- 禁止 `USE` 语句（数据源 URL 已指定数据库）
- 使用 `CREATE TABLE IF NOT EXISTS` 而非 `DROP TABLE IF EXISTS + CREATE TABLE`（避免清空用户数据）

### 7.6 文件上传安全机制

**多重校验**：

1. **扩展名白名单**：限制允许的文件类型
2. **Magic Number 校验**：读取文件头判断真实类型
   - OOXML 格式（DOCX/XLSX/PPTX）共享 `PK\x03\x04` 签名
3. **文件重命名**：UUID + 日期目录组织
4. **SVG 不映射 Content-Type**：`FileController.preview` 故意不映射 SVG，强制下载防止 XSS

**FormData 请求处理**：

```javascript
// 前端：不能手动设置 Content-Type，让浏览器生成 boundary
axios.post('/api/files/upload', formData, {
    headers: { 'Content-Type': undefined }   // 或删除默认值
});

// request.js 拦截器自动处理
if (data instanceof FormData) {
    delete config.headers['Content-Type'];   // 让浏览器自动生成
}
```

### 7.7 WebSocket 站内通知

**架构**：

```
后端 Spring WebSocket (STOMP) ←→ 前端 @stomp/stompjs
                              ←→ 小程序原生 WebSocket + 轮询降级
```

**通知投递设计**：

- 独立事务 `REQUIRES_NEW`：通知投递失败不回滚主业务
- 静默投递：异常仅记录日志，不抛出给调用方
- 兜底机制：WebSocket 推送失败时消息已落库，用户登录后可见

### 7.8 tmpfs 解决 read_only 容器写权限

**问题**：后端容器 `read_only: true` 导致 fontconfig 无法写缓存（验证码生成报 Fontconfig error）、日报导出无法创建 `./exports` 目录（FileNotFoundException）。

**解决方案**：

```yaml
backend:
  read_only: true
  tmpfs:
    - /tmp:size=64M                              # JVM 临时文件
    - /var/cache/fontconfig:size=16M             # 系统字体缓存
    - /app/.cache/fontconfig:size=16M            # 应用字体缓存
    - /app/.fontconfig:size=16M                  # 字体配置
    - /app/exports:size=128M                     # 日报导出目录
```

**代码健壮性**：

```java
File exportDir = new File("/app/exports");
if (!exportDir.exists() && !exportDir.mkdirs()) {
    // mkdirs() 失败时抛出明确异常，避免后续 FileNotFoundException 定位困难
    throw new IllegalStateException("无法创建导出目录: " + exportDir.getAbsolutePath());
}
```

---

## 8. 版本依赖全景表

### 后端依赖

| 依赖 | 版本 | 类型 |
|------|------|------|
| Spring Boot | 3.0.2 | parent |
| Java | 17 (LTS) | language |
| Spring Web | 6.0.4 | starter |
| Spring Validation | 8.0.2 | starter |
| Spring AOP | 6.0.4 | starter |
| Spring Data Redis | 3.0.2 | starter |
| Spring WebSocket | 6.0.4 | starter |
| Spring Actuator | 3.0.2 | starter |
| MyBatis-Plus | 3.5.3.1 | ORM |
| Druid | 1.2.16 | connection pool |
| MySQL Connector/J | 8.0.31 | JDBC driver |
| Flyway | 9.5.1 | migration |
| Knife4j | 4.1.0 | API doc |
| JJWT | 0.11.5 | JWT |
| Hutool | 5.8.22 | utils |
| EasyExcel | 3.3.4 | Excel export |
| Lombok | 1.18.26 | code generation |
| Commons Pool2 | 2.11.1 | redis pool |
| Micrometer Prometheus | 1.10.2 | metrics |

### Web 前端依赖

| 依赖 | 版本 | 类型 |
|------|------|------|
| Vue | 3.4.21 | framework |
| Vue Router | 4.3.0 | router |
| Pinia | 2.1.7 | state |
| pinia-plugin-persistedstate | 3.2.3 | persist |
| Vite | 5.2.0 | build |
| Element Plus | 2.6.2 | UI |
| @element-plus/icons-vue | 2.3.1 | icons |
| element-china-area-data | 6.1.0 | area data |
| Axios | 1.18.1 | http |
| Day.js | 1.11.10 | date |
| ECharts | 5.5.0 | chart |
| @stomp/stompjs | 7.0.0 | websocket |
| sockjs-client | 1.6.1 | ws fallback |
| ESLint | 9.0.0 | lint |
| terser | 5.49.0 | minify |

### 部署依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Docker | 24.0+ | 容器运行时 |
| Docker Compose | 2.20+ | 编排 |
| MySQL | 8.0.40 | 数据库 |
| Redis | 7.2-alpine | 缓存 |
| Nginx | 1.27-alpine | 反向代理 |

---

## 附录：技术选型理由

| 场景 | 选型 | 理由 |
|------|------|------|
| 后端框架 | Spring Boot 3.0.2 | Java 生态成熟，企业级首选；3.0 支持 Java 17 + Jakarta EE 9 |
| ORM | MyBatis-Plus | 保留 MyBatis 灵活性，CRUD 增强减少样板代码 |
| 数据库迁移 | Flyway | 应用启动自动应用迁移，解决云服务器手动执行易遗漏问题 |
| 缓存 | Redis + Spring Cache | `@Cacheable` 注解解耦业务逻辑与缓存逻辑 |
| API 文档 | Knife4j | 比 Swagger UI 更适合中文团队，支持在线调试 |
| 报表导出 | EasyExcel | 注解驱动，低内存占用，适合大数据量导出 |
| 前端框架 | Vue 3.4 | Composition API 类型推导友好，生态轻量 |
| 构建工具 | Vite 5 | 开发服务器启动快，HMR 即时生效 |
| UI 库 | Element Plus 2.6 | 企业级组件完备，中文文档友好 |
| 状态管理 | Pinia | Vue 3 官方推荐，TS 支持优于 Vuex |
| 小程序 | uni-app | 一套代码多端编译，复用后端 API |
| 容器化 | Docker Compose | 单机部署足够，无需 K8s 复杂度 |

---

> **维护说明**：本文档随项目版本同步更新。如新增依赖或升级版本，请同步修改 [pom.xml](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System_end/pom.xml)、[package.json](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/package.json) 与本文档第 8 章。
