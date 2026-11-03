# 代码审核报告：性能与安全专项（2026-11-02）

> **审核范围**：后端 Java/Spring Boot、前端 uniapp/Vue、Docker 部署、MyBatis SQL
> **审核聚焦**：性能问题 + 安全隐患（不重复 2026-07-21 全量报告已修复的 92 项）
> **审核方法**：逐文件深读核心安全/性能模块，分级输出
> **结论**：7 月那轮修复质量很高（认证、文件上传、限流、CORS 加固、KEYS→SCAN 等都已落地）。本轮聚焦发现 **12 项新问题**，其中 2 项严重（前后端 WS 鉴权链路断裂 + 明文 HTTP）、6 项中等、4 项轻微。

---

## 修复状态（2026-11-03 已实施）

| 编号 | 问题 | 状态 | 说明 |
|------|------|------|------|
| P0-1 | WS 鉴权链路断裂 | ✅ 已修复 | `ws.js` 移除 URL token，改握手请求头 `Authorization` 传递；无 token 时降级轮询 |
| P0-2 | 明文 HTTP IP | ✅ 已修复 | `config.js` 改为按 `NODE_ENV` 区分，生产强制 HTTPS |
| P1-1 | token 明文存储 | ✅ 已修复 | `auth.js` 存储底层切换为 `cryptoStorage`（原死代码已激活） |
| P1-2 | MySQL 权限过大 | ✅ 已修复 | 账号分离：运行时 `hbsz_app` 仅 DML，新增迁移账号 `hbsz_migrate` 持 DDL+SYSTEM_USER（迁移脚本 PREPARE 动态 SQL 必需）；Flyway 配独立账号；`.env.docker` 补 `FLYWAY_PASSWORD` |
| P1-3 | CORS 默认值不安全 | ✅ 已修复 | 生产环境（prod profile）使用通配符 `*` 时启动直接失败 |
| P1-4 | 统计 SQL 索引失效 | ✅ 已修复 | `countByMonth` 的 `YEAR()` 改为 `[年初, 次年初)` 区间比较 |
| P1-5 | getOverview 串行查询 | ✅ 已修复 | 5 次聚合查询改 `CompletableFuture` 并行执行 |
| P1-6 | 文档越权 IDOR | ✅ 无需修复 | 经核 `DocumentServiceImpl` 全部读写方法已有 `canAccessProject()` 校验（review 时只看 controller 漏判） |
| P2-1 | JwtUtils 重复构造密钥 | ✅ 已修复 | `@PostConstruct` 缓存 `SecretKey` |
| P2-2 | BCrypt cost 偏弱 | ✅ 已修复 | 默认 cost 提升至 12，可配置 |
| P2-3 | 文件头校验未关流 | ✅ 已修复 | `validateFileSignature` 改 try-with-resources |
| P2-4 | healthcheck 带明文密码 | ✅ 已修复 | mysql 用 `MYSQL_PWD`、redis 用 `REDISCLI_AUTH` 环境变量认证，密码不再进 ps/docker inspect |

**验证**：后端 Maven `compile` 通过（offline）；前端 3 个 JS 文件 `node --check` 语法通过；`docker-compose.yml` 与 `application-prod.yml` 经 PyYAML 语法校验通过。

---

## 问题总览

| 级别 | 数量 | 类型分布 |
|------|------|----------|
| 🔴 严重 | 2 | 安全+功能 1 / 安全 1 |
| 🟡 中等 | 6 | 安全 3 / 性能 2 / 越权 1 |
| 🟢 轻微 | 4 | 性能 2 / 安全 2 |
| **合计** | **12** | — |

优先处理 P0-1（WS 鉴权断裂），它会同时导致**未授权访问**和**token 泄露**。

---

## 🔴 严重问题

### P0-1. WebSocket 鉴权链路断裂 —— 前端传 URL token，后端已不读，且前端非 STOMP 客户端

**文件**：
- 前端 `hbsz_-uniapp_-vue-master/utils/ws.js:25`
- 后端 `Hbsz_IDC_System_end/.../interceptor/JwtHandshakeInterceptor.java:57-70`
- 后端 `Hbsz_IDC_System_end/.../config/JwtChannelInterceptor.java:59`

**问题**：
前端 `buildWsUrl()` 把 token 拼在 URL 查询参数里：
```js
return `${base.replace('/api', '')}/ws/websocket?access_token=${encodeURIComponent(token || '')}`
```
但后端 `JwtHandshakeInterceptor` 已在 7 月"安全修复 #23"中**明确移除了从 URL 读取 token 的逻辑**，只认 HTTP `Authorization` 头；URL 里没 token 时直接 `return true` 放行握手，交给 `JwtChannelInterceptor` 在 STOMP CONNECT 帧阶段鉴权。

矛盾在于：`JwtChannelInterceptor.preSend()` **只对 STOMP CONNECT 帧生效**（`if (!StompCommand.CONNECT.equals(...)) return message;`），而前端 `ws.js` 头部注释明确说明采用的是「**轻量心跳 + REST 轮询降级**」策略，并非真正的 STOMP 客户端，很可能根本不发 CONNECT 帧。

**后果（双重风险）**：
1. **token 泄露**：token 出现在 URL 中，会被写入 Nginx/Browser 访问日志、Referer 头。这正是后端 7 月修复要消除的风险，但前端没同步改。
2. **未授权 WS 连接**：握手放行 + 不发 CONNECT 帧 → `JwtChannelInterceptor` 不触发 → WebSocket 连接在无任何身份校验的情况下建立。

**修复方案**（二选一，需前后端对齐）：
- **方案 A（推荐，保持后端 STOMP 鉴权）**：前端改用真正的 STOMP 客户端（`stompjs`/`@stomp/stompjs`），在 `connectHeaders` 里传 `Authorization: Bearer <token>`，移除 URL 中的 `access_token`。后端无需改动。
- **方案 B（前端保持原生 WS）**：后端 `JwtHandshakeInterceptor` 恢复从 HTTP `Authorization` 头（非 URL）读取 token，前端在 `uni.connectSocket` 的 `header` 里传 `Authorization`；同时确认 endpoint 仍走 STOMP 协议层（否则 `JwtChannelInterceptor` 形同虚设）。

无论哪种方案，**必须删除 `ws.js:25` 的 `?access_token=` 拼接**。

---

### P0-2. 前端 API_BASE_URL 硬编码明文 HTTP IP

**文件**：`hbsz_-uniapp_-vue-master/utils/config.js:11`
```js
export const API_BASE_URL = 'http://124.220.158.177/api'
```

**问题**：
- 明文 HTTP 传输，`request.js` 把 JWT 放在 `Authorization` 头里发送，**中间人可窃听全部请求含 token**。
- 生产服务器 IP 硬编码在代码仓库中，注释虽写"正式上线前必须改为 HTTPS 域名"，但当前代码即生产代码。
- 小程序后台也要求 request 合法域名为 HTTPS，该配置无法过审。

**修复**：
```js
// 通过环境变量/构建期注入，区分开发与生产
// #ifdef H5
export const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? 'https://your-domain.com/api'
  : '/api'
// #endif
// #ifndef H5
export const API_BASE_URL = 'https://your-domain.com/api'
// #endif
```
上线前务必走 HTTPS，并在小程序后台配置 request 合法域名。

---

## 🟡 中等问题

### P1-1. 前端 Token 明文存储，cryptoStorage 是死代码

**文件**：
- `utils/auth.js:14-45`（`getToken`/`saveAuth` 用 `uni.setStorageSync` 明文存取）
- `utils/cryptoStorage.js`（完整实现存在，但全项目 grep 仅自引用，无任何文件 import）

**问题**：JWT access token（30min TTL）与 refresh token（7 天 TTL）以明文存储在小程序本地存储中。`cryptoStorage.js` 已经写好了一套 XOR+Base64 混淆方案（虽非真正加密，但能抬高门槛），却完全没接入。XSS 或本地存储被读即可直接拿走 token。

**修复**：将 `auth.js` 的存储底层切换为 `cryptoStorage`：
```js
import { cryptoStorage } from './cryptoStorage'
// getToken:
return cryptoStorage.getItem(TOKEN_KEY) || ''
// saveAuth:
cryptoStorage.setItem(TOKEN_KEY, token || '')
if (refreshToken) cryptoStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
if (userInfo) cryptoStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
```
注意 `getUserInfo` 需配合 `JSON.parse`。长期方案仍按 `cryptoStorage.js` 注释所述迁移到 httpOnly Cookie。

---

### P1-2. MySQL 应用用户权限过大，违反最小权限原则

**文件**：`docker/mysql/init-grants.sql:13,19`
```sql
GRANT ALL PRIVILEGES ON `hbsz_idc_system`.* TO 'hbsz_app'@'%';
GRANT SYSTEM_USER ON *.* TO 'hbsz_app'@'%';
```

**问题**：
- `ALL PRIVILEGES` 包含 DROP/ALTER/CREATE 等 DDL，应用账号不应持有（除 Flyway 迁移期）。一旦应用被 SQL 注入或 RCE，攻击者可直接删库删表。
- `SYSTEM_USER` 是高权限，可绕过部分权限校验。
- `'hbsz_app'@'%'` 允许任意主机连接（虽 MySQL 未映射端口，但内网任意容器可连）。

**修复**：应用运行时只需 DML，DDL 由独立迁移账号执行：
```sql
-- 应用账号：仅 DML
GRANT SELECT, INSERT, UPDATE, DELETE ON `hbsz_idc_system`.* TO 'hbsz_app'@'%';
-- 迁移账号（仅部署时用）：DDL，迁移后回收
-- 移除 SYSTEM_USER，Flyway 若需要动态 SQL 改用迁移账号执行
```
`@'%'` 收窄为应用容器来源网段（如 `hbsz_app@'10.%'` 或 docker 网段）。

---

### P1-3. CORS 默认值为通配符且允许凭证

**文件**：`Hbsz_IDC_System_end/.../config/CorsConfig.java:31,61-67`
```java
@Value("${hbsz.cors.allowed-origins:*}")
private String allowedOrigins;
// "*" 时：config.addAllowedOriginPattern("*"); // 且 allowCredentials=true
```

**问题**：配置类默认值是 `*`。`docker-compose.yml` 里给了默认值 `https://hbsz.example.com` 覆盖，但**非 docker 部署**（如本地 `java -jar` 直跑、测试环境）若未设环境变量，就会以 `*` + `allowCredentials=true` 运行，任意站点可携带 Cookie 跨域访问。代码里只有 `log.warn` 告警，不会拒绝启动。

**修复**：把默认值改为安全值，生产缺失配置时应启动失败而非告警：
```java
@Value("${hbsz.cors.allowed-origins:}")  // 默认空
private String allowedOrigins;
// 启动时校验：
@PostConstruct void check() {
    if (!StringUtils.hasText(allowedOrigins) || "*".equals(allowedOrigins.trim())) {
        if ("prod".equals(activeProfile)) {
            throw new IllegalStateException("生产环境必须配置 hbsz.cors.allowed-origins 白名单");
        }
        log.warn("CORS 为通配符，仅允许开发环境");
    }
}
```

---

### P1-4. 统计查询对日期列使用函数，索引失效致全表扫描

**文件**：`Hbsz_IDC_System_end/.../resources/mapper/BizProjectMapper.xml:169,203-204`
```xml
<!-- countByMonth -->
WHERE YEAR(created_at) = #{year}
<!-- selectArchiveStatistics -->
SUM(CASE WHEN YEAR(archive_date) = #{currentYear} ... )
SUM(CASE WHEN YEAR(archive_date) = #{currentYear} AND MONTH(archive_date) = #{currentMonth} ... )
```

**问题**：对列施加 `YEAR()`/`MONTH()` 函数后，即便 `created_at`/`archive_date` 建了索引也无法命中，全表扫描。项目数据量增长后，首页统计与归档统计接口会逐渐变慢。（XML 注释自己也承认了这一点。）

**修复**：改为区间比较，让优化器走索引：
```xml
<!-- countByMonth：用 [年初, 次年初) 区间替代 YEAR() -->
WHERE created_at >= CONCAT(#{year}, '-01-01 00:00:00')
  AND created_at <  CONCAT(#{year}+1, '-01-01 00:00:00')
```
归档统计同理，用 `archive_date >= 年初 and < 次年初`、`>= 月初 and < 次月初`。同时确认 `created_at`、`archive_date`、`is_archived`、`status` 已建索引。

---

### P1-5. 统计概览 getOverview 串行 5 次 DB 查询

**文件**：`Hbsz_IDC_System_end/.../service/impl/StatisticsServiceImpl.java:58-79`

**问题**：`getOverview()` 依次调用 `selectOverviewStatistics`、`countByStatus`、`countByType`、`countByPriority`、`countByMonth` 共 5 次独立聚合查询，串行执行。而同类的 `exportReport()` 已经用 `CompletableFuture` + 自定义线程池并行化。首页 dashboard 是高频访问接口，5 次串行查询延迟累加。

**修复**：与 `exportReport` 一致，并行化：
```java
public StatisticsVO getOverview() {
    var f1 = CompletableFuture.supplyAsync(projectMapper::selectOverviewStatistics, statisticsExecutor);
    var f2 = CompletableFuture.supplyAsync(projectMapper::countByStatus, statisticsExecutor);
    var f3 = CompletableFuture.supplyAsync(projectMapper::countByType, statisticsExecutor);
    var f4 = CompletableFuture.supplyAsync(projectMapper::countByPriority, statisticsExecutor);
    var f5 = CompletableFuture.supplyAsync(() -> projectMapper.countByMonth(LocalDate.now().getYear()), statisticsExecutor);
    CompletableFuture.allOf(f1,f2,f3,f4,f5).join();
    // 组装 vo...
}
```
若 dashboard 非实时，可再加 Redis 缓存（TTL 5~10min）进一步降压。

---

### P1-6. 文档下载/预览潜在水平越权（IDOR）

**文件**：`Hbsz_IDC_System_end/.../controller/DocumentController.java:224,266`

**问题**：`download`/`preview` 接口先 `documentService.getEntityById(id)` 按文档 id 直查文件路径，再 `loadFile` 输出。权限校验只有功能级 `@RequiresPermission("document:download")`，**未见对"当前用户是否可访问该文档所属项目"的数据级校验**。而列表查询 `selectDocumentPage` 是有 `visibleProjectIds` 数据隔离的。这意味着：列表隔离了，但**只要拿到文档 id 就可能绕过隔离下载任意项目文档**（普通用户枚举 id 越权下载）。

**已确认（2026-11-03）**：经核 `DocumentServiceImpl` 的 `getEntityById`/`getDetail`/`create`/`update`/`delete`/`addVersion`/`review`/`listByProject` 全部已调用 `projectAuthorizationService.canAccessProject(projectId)` 做数据级校验，IDOR 已防住，**无需修复**。（review 时只看了 controller 层、未读 service 层导致的误判。）

**修复（若 service 层未校验）**：在 `getEntityById` 或 controller 层补充项目可见性校验：
```java
BizDocument document = documentService.getEntityById(id);
// 校验当前用户对该文档所属项目可见
projectVisibilityService.checkVisible(document.getProjectId(), UserContext.getUserId());
```
详情接口 `detail(@PathVariable Long id)` 同理。

---

## 🟢 轻微问题

### P2-1. JwtUtils 签名密钥每次请求重复构造

**文件**：`Hbsz_IDC_System_end/.../utils/JwtUtils.java:73-75`
```java
private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
}
```
**问题**：`parseToken`/`generateToken` 每次调用都重新构造 `SecretKey`。每个 HTTP 请求经 JWT 拦截器都会 `parseToken` 一次，重复的 `hmacShaKeyFor` 字节数组处理是浪费。

**修复**：`@PostConstruct` 中构造一次缓存为字段：
```java
private SecretKey signingKey;
@PostConstruct void init() { this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
// getSigningKey() 返回 signingKey
```

---

### P2-2. PasswordUtils BCrypt cost factor 偏弱

**文件**：`Hbsz_IDC_System_end/.../utils/PasswordUtils.java:27`
```java
return BCrypt.hashpw(rawPassword, BCrypt.gensalt());  // Hutool 默认 cost=10
```
**问题**：2026 年 cost=10 偏弱，GPU 暴力破解成本偏低。

**修复**：`BCrypt.gensalt(12)`（或 13）。可配置化：
```java
@Value("${hbsz.password.bcrypt-cost:12}") private int bcryptCost;
return BCrypt.hashpw(rawPassword, BCrypt.gensalt(bcryptCost));
```

---

### P2-3. 文件头校验未关闭 InputStream

**文件**：`Hbsz_IDC_System_end/.../service/impl/FileStorageServiceImpl.java:236-242`
```java
header = new byte[expected.length];
int read = file.getInputStream().read(header);  // 流未关闭
```
**问题**：`MultipartFile.getInputStream()` 打开的流未 try-with-resources 关闭。虽请求结束后容器会清理，但不规范，并发上传多时可能撑开文件句柄。

**修复**：
```java
try (InputStream is = file.getInputStream()) {
    header = new byte[expected.length];
    int read = is.read(header);
    if (read < expected.length) { /* throw */ }
}
```

---

### P2-4. healthcheck 命令携带明文密码

**文件**：`docker-compose.yml:56,157`
```yaml
test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD:-...}"]
test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-...}", "ping"]
```
**问题**：密码会出现在 `docker inspect` 输出与容器进程列表中，本机有其他用户时可被 `ps` 看到。

**修复**：MySQL 用 `mysqladmin ping` 配合 `MYSQL_PWD` 环境变量（容器内已设 `MYSQL_PWD`，可省去 `-p`）；Redis 用 `REDISCLI_AUTH` 环境变量替代 `-a`。或改用 `mysql -e "select 1"` / `redis-cli ping`（已 requirepass 时需 auth，可用 `--user`+`--password` 从 secrets 文件读）。

---

## 已确认良好的实践（本轮无需改动）

下列做法扎实，体现了 7 月修复的成果，本轮不再调整：
- **登录防爆破**：验证码一次性使用 + Redis 失败计数（Lua 原子 INCR+EXPIRE）+ 账号锁定；验证码失败不累加锁定计数防 DoS。
- **refreshToken 防重放**：SETNX 一次性使用标记 + 登出时间戳使登出前 refreshToken 全部失效。
- **文件上传安全**：扩展名白名单 + magic number 校验 + 路径穿越三重防护（绝对路径拒绝 + `..` 检测 + normalize 前缀校验）。
- **权限缓存**：用户角色/权限走 Redis 缓存，`clearAllUserCache` 用 SCAN 替代 KEYS 避免阻塞主线程。
- **SQL 注入防护**：全部 Mapper XML 使用 `#{}` 参数绑定，无 `${}` 拼接；LIKE 通配符有 `SqlUtils.escapeLike` 转义。
- **限流**：Redis Lua 原子计数 + 用户/IP 维度 + 可信代理白名单防 X-Forwarded-For 伪造。
- **数据隔离**：项目/文档列表查询通过 `visibleProjectIds` IN 过滤，空列表 `AND 1=0`。
- **JWT 加固**：HS256 + issuer/audience 校验 + access 30min/refresh 7d TTL 配置化。
- **容器安全**：cap_drop ALL、read_only、no-new-privileges、非 root 用户、端口绑 127.0.0.1、敏感变量 `${VAR:?required}` 强制。
- **日志脱敏**：`PermissionAspect.sanitizeArgs` 不调用 DTO 的 `toString`，避免 password/phone 泄露。

---

## 修复优先级建议

| 优先级 | 问题 | 理由 |
|--------|------|------|
| **立即** | P0-1 WS 鉴权断裂 | 未授权访问 + token 泄露，双重风险 |
| **立即** | P0-2 明文 HTTP IP | token 全程可被窃听 |
| **本周** | P1-1 token 明文存储 | 配合 P0-2 一起改前端 |
| **本周** | P1-6 文档越权 | 确认后补数据级校验 |
| **本周** | P1-2 MySQL 权限过大 | 收窄应用账号 DDL 权限 |
| **下周** | P1-3 CORS 默认值 | 改为缺失即启动失败 |
| **下周** | P1-4/P1-5 统计性能 | 改写函数条件 + 概览并行化 |
| **择期** | P2-1~P2-4 轻微项 | 常规优化 |
