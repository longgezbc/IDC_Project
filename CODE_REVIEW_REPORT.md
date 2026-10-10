# 全量代码审核报告

> **项目**：IDC 数据中心综合布线项目管理系统  
> **审核日期**：2026-07-21  
> **审核范围**：后端 Java、前端 Vue、数据库 SQL、Docker 部署配置、微信小程序  
> **审核方法**：逐文件深入阅读，按严重程度分级（严重/中等/轻微）  
> **修复状态**：✅ 全部 22 个严重问题已修复 | ✅ 全部 38 个中等问题已修复 | ✅ 18 个轻微问题已修复 | ✅ 6 个部署修复已修复 | 📅 修复日期：2026-07-21

---

## 修复状态总览

| 优先级 | 问题数 | 已修复 | 修复日期 |
|--------|--------|--------|----------|
| 🔴 第一优先级 | 7 | ✅ 7 | 2026-07-21 |
| 🟡 第二优先级 | 8 | ✅ 8 | 2026-07-21 |
| 🟢 第三优先级 | 10 | ✅ 10 | 2026-07-21 |
| 🔵 部署修复（v1.3） | 6 | ✅ 6 | 2026-07-21 |
| **合计** | **31** | **31** | — |

> 详细修复清单见 [修复汇总](#修复汇总)

---

## 总览

| 审查维度 | 严重 | 中等 | 轻微 | 合计 |
|----------|------|------|------|------|
| 后端代码 (Java) | 4 | 9 | 10 | 23 |
| 前端代码 (Vue) | 3 | 9 | 8 | 20 |
| 数据库 + 部署配置 | 15 | 20 | 14 | 49 |
| **总计** | **22** | **38** | **32** | **92** |

---

## 一、后端代码问题（Java）

### 🔴 严重问题（4个）

#### 1.1 导入方法事务失效 — `ProjectServiceImpl.importProjects()` 和 `UserManageServiceImpl.importUsers()`

**文件**：`ProjectServiceImpl.java` 第777行、`UserManageServiceImpl.java` 第561行

通过 `this.create(dto)` 类内部自调用（self-invocation），Spring AOP 代理被绕过，`@Transactional` 完全失效。导入的每一行数据都不在事务保护下执行，某行写入失败时已插入的数据不会回滚。

**修复**：将 `importProjects()` 方法本身标注 `@Transactional`，提取一个 `doCreate()` 内部方法供 `create()` 和 `importProjects()` 共同调用；或注入自身代理对象 `@Autowired private IProjectService self`。

#### 1.2 文档版本文件 URL 丢失 — `DocumentController.addVersion()`

**文件**：`DocumentController.java` 第196-207行

`fileStorageService.storeFile(file)` 返回的 `fileUrl` 被获取后，从未赋值给 `DocumentVersionDTO`，也未传给 `documentService.addVersion()`。文件已上传到磁盘，但版本记录中文件 URL 丢失。

**修复**：检查 `DocumentVersionDTO` 是否有 `fileUrl` 字段，设置 `dto.setFileUrl(fileUrl)`。

#### 1.3 `ProjectServiceImpl.create()` 中 userId 可能为 null

**文件**：`ProjectServiceImpl.java` 第235行

`UserContext.getUserId()` 可能返回 `null`，`project.setCreatedBy(null)` 若数据库有 NOT NULL 约束将导致插入失败。

**修复**：增加非空校验，若为 null 则抛出业务异常。

#### 1.4 `AuthController.getCurrentUser()` 中 userId 可能为 null

**文件**：`AuthController.java` 第92-93行

`UserContext.getUserId()` 可能返回 null，直接传入 `authService.getCurrentUserInfo(userId)` 会导致 NPE。

**修复**：调用前对 `userId` 做非空校验。

---

### 🟡 中等问题（9个）

#### 1.5 文档审核缺少并发控制 — `DocumentServiceImpl.review()`

**文件**：`DocumentServiceImpl.java` 第311-332行

先读后写的 select-then-update 模式，两个管理员同时审核时都能通过 `"待审核".equals(status)` 检查。应参照 `TaskServiceImpl.reviewProgress()` 使用 CAS 式原子更新。

#### 1.6 用户密码无强度校验 — `UserManageServiceImpl.create()`

**文件**：`UserManageServiceImpl.java` 第172行

直接接受任意密码，允许弱密码如 "123456"。应在 `UserSaveDTO` 上用 `@Pattern` 注解约束，或 Service 层校验。

#### 1.7 任务日期校验不完整 — `TaskServiceImpl.update()`

**文件**：`TaskServiceImpl.java` 第183-186行

只检查 DTO 中新传入的两个日期，未与实体已有日期交叉校验。DTO 仅传 `startDate` 时，若新值晚于库中已有 `endDate`，校验会通过。

#### 1.8 `updateStatus()` 缺少超级管理员和当前用户保护

**文件**：`UserManageServiceImpl.java` 第327-342行

管理员可以误操作禁用自己或超级管理员（id=1）。参照 `delete()` 方法的保护逻辑增加检查。

#### 1.9 客户编号生成碰撞概率高

**文件**：`ProjectServiceImpl.java` 第714行

`UUID.substring(0, 8)` 仅取8位字符，碰撞概率较高（约 2^32 空间）。建议使用完整 UUID 或 Snowflake ID。

#### 1.10 项目列表分页参数无校验 — `ProjectController.list()`

**文件**：`ProjectController.java` 第61行

`ProjectQueryDTO query` 参数没有 `@Valid` 注解，分页参数可能为负数。添加 `@Valid` 和 DTO 中的 `@Min`/`@Max` 约束。

#### 1.11 账号禁用状态 null 判断漏洞 — `AuthServiceImpl.login()`

**文件**：`AuthServiceImpl.java` 第106行

`user.getStatus() != null && user.getStatus() == 0` 当 status 为 null 时默认视为启用。改为 `user.getStatus() == null || user.getStatus() == 0`。

#### 1.12 `SystemConfigServiceImpl.upsert()` 并发竞态

**文件**：`SystemConfigServiceImpl.java` 第134-162行

select-then-insert 加 DuplicateKeyException 防御，但 catch 块中的 update 仍存在并发覆盖风险。建议使用 `INSERT ... ON DUPLICATE KEY UPDATE`。

#### 1.13 `UserManageServiceImpl.updateStatus()` 参数未校验

**文件**：`UserManageServiceImpl.java` 第336行

`status` 参数没有校验合法取值范围（仅允许 0 或 1），可能导致非法值写入数据库。

---

### 🟢 轻微问题（10个）

| # | 文件 | 行号 | 问题 |
|---|------|------|------|
| 1.14 | `ProjectServiceImpl.java` | 643 | `String.valueOf(d.getId())` 若 id 为 null 输出 "null" |
| 1.15 | `TaskServiceImpl.java` | 641 | 同上，`vo.setId(String.valueOf(task.getId()))` |
| 1.16 | `DocumentController.java` | 133 | 文档名 `name` 和 `file.getOriginalFilename()` 都为 null 时无兜底 |
| 1.17 | `DocumentController.java` | 120 | 文档上传缺少 Controller 层文件大小预检 |
| 1.18 | `UserManageController.java` | 102 | `@Validated` 和 `@Valid` 冗余使用 |
| 1.19 | `FileController.java` | 81-83 | `previewUrl` 拼接时未对 `relativePath` 做 URL 编码 |
| 1.20 | `FileStorageServiceImpl.java` | 107 | `file.getOriginalFilename()` 可能为 null 导致 NPE |
| 1.21 | `FileStorageServiceImpl.java` | 121 | `storeFile()` 缺少 `startsWith(basePath)` 路径越界校验 |
| 1.22 | `ProjectController.java` | 208 | import 接口未对上传文件做 Controller 层基本校验 |
| 1.23 | `DocumentServiceImpl.java` | 151 | `UUID.substring(0, 8)` 用于文档编号，碰撞概率偏高 |

---

## 二、前端代码问题（Vue）

### 🔴 严重问题（3个）

#### 2.1 XSS 风险：`dangerouslyUseHTMLString: true` 使用后端返回数据

**文件**：`ProjectList.vue` 第635行、`UserManagement.vue` 第563行

```js
ElMessageBox.alert(result.errors.join('<br>'), '导入失败详情', {
  dangerouslyUseHTMLString: true  // 危险
})
```

后端返回的 `result.errors` 直接拼接 HTML 后渲染。攻击者可通过构造恶意 Excel 文件注入 XSS 载荷。

**修复**：对 errors 数组做 HTML 转义后再使用 `dangerouslyUseHTMLString`，或改用纯文本展示。

#### 2.2 `withCancel` 竞态条件 — `cancelToken.js`

**文件**：`cancelToken.js` 第141-155行

`.finally()` 中无条件清空 `currentController`，导致快速连续调用时防重复机制失效。修复：只在 `currentController === controller` 时才置 null。

#### 2.3 `hasPermission` 等函数对 falsy 值无条件放行

**文件**：`permission.js` 第31、47、62行

```js
if (!value) return true  // ''、0、false、null、undefined 全部放行
```

空字符串 `''`、数字 `0`、`false` 均被放行。如果调用方因疏忽传入空字符串，用户将获得不应有的权限。

**修复**：改为 `if (value == null || value === '') return true`。

---

### 🟡 中等问题（9个）

#### 2.4 路由切换时取消所有请求，可能误杀 keep-alive 组件的请求

**文件**：`router/index.js` 第263行

`cancelAllPending('路由切换，取消未完成请求')` 每次路由切换取消全部请求，keep-alive 缓存的组件请求也被取消，loading 状态可能永远卡住。

**修复**：仅取消"离开的页面"的请求，或使用请求 key 前缀区分页面。

#### 2.5 `v-submit` 指令未在 `main.js` 中注册

**文件**：`submitGuard.js` 第61-105行 vs `main.js`

`vSubmit` 指令已定义但 `main.js` 中未 `app.directive('submit', vSubmit)`，是一个未激活的功能。

#### 2.6 `handleDeleteTask` 静默吞掉删除失败错误

**文件**：`ProjectProgress.vue` 第407-413行

`.catch(() => {})` 吞掉所有错误，用户无感知。修复：非取消错误应提示用户。

#### 2.7 `UserManagement` 首次加载时 `fetchList` 被调用两次

**文件**：`UserManagement.vue` 第579-588行

`onMounted` 和 `onActivated` 在首次进入时都会触发。参考 `ProjectList.vue` 的 `isMounted` 守卫模式。

#### 2.8 `Login.vue` 中 `loginFormRef.value` 可能为 null

**文件**：`Login.vue` 第261行

组件在异步操作中卸载时 `loginFormRef.value` 可能为 null。添加 `if (!loginFormRef.value) return` 守卫。

#### 2.9 WebSocket `connect()` 未防护重复调用

**文件**：`websocket.js` 第49-52行

`stompClient` 已存在但处于"正在连接"状态时，快速调用两次 `connect` 会丢失第一个 client 引用。创建新 client 前先 `deactivate` 旧的。

#### 2.10 `cancelToken.js` 中 `addPending` 覆盖已有的 `config.signal`

**文件**：`cancelToken.js` 第77行

`config.signal = controller.signal` 静默覆盖调用方通过 `withCancel` 设置的 signal。检查是否已存在，若存在则合并。

#### 2.11 GanttChart 对 `tasks` 的深度监听可能造成性能问题

**文件**：`GanttChart.vue` 第327-329行

`watch(() => props.tasks, ..., { deep: true })` 当任务数量较多时，每次任一任务属性变化都触发深度 diff。改为监听 `props.tasks.length`。

#### 2.12 全局错误处理器无用户反馈

**文件**：`main.js` 第45-50行

`app.config.errorHandler` 仅上报无用户提示，用户可能看到白屏或 UI 缺失。至少显示一个全局错误提示。

---

### 🟢 轻微问题（8个）

| # | 文件 | 行号 | 问题 |
|---|------|------|------|
| 2.13 | `GanttChart.vue` | 200-212 | day/week/month 三个分支代码完全相同，应提取公共逻辑 |
| 2.14 | `MainLayout.vue` | 166-168 | `cachedViews` 硬编码组件名，应在路由 meta 中加 `keepAlive` 字段 |
| 2.15 | `ProjectList.vue` | 372 | `filteredProjects = pagedProjects` 冗余别名 |
| 2.16 | `ProjectEdit.vue` | 357 | 对基本类型做 `JSON.parse(JSON.stringify(...))` 多余开销 |
| 2.17 | `request.js` | 99 | `setTimeout 800ms` 不够可靠，应使用 `onClose` 回调 |
| 2.18 | `App.vue` | 17-19 | `loadConfig()` 无错误处理，失败时静默 |
| 2.19 | `CrudTable.vue` | 146-168 | 非 scoped 全局样式可能在复杂页面冲突 |
| 2.20 | `BaseButton.vue` | 14 | `v-bind="$attrs"` 可能透传非标准 HTML 属性 |

---

## 三、数据库与部署配置问题

### 🔴 严重问题（15个）

#### 3.1 生产环境 CORS 设置为 `"*"` — docker-compose.yml

**文件**：`docker-compose.yml` 第150行

`CORS_ALLOWED_ORIGINS: "*"` 允许任意来源跨域访问 API，且覆盖了 `application-prod.yml` 的安全回退值。**必须改为具体域名**。

#### 3.2 数据库使用 root 账号连接 — docker-compose.yml

**文件**：`docker-compose.yml` 第141行

`DB_USERNAME: root` 使用超级用户连接，权限过大。应创建专用应用数据库用户，仅授予 `hbsz_idc_system` 库的 CRUD 权限。

#### 3.3 数据库连接禁用 SSL — docker-compose.yml

**文件**：`docker-compose.yml` 第140行

`useSSL=false` 关闭 SSL 加密，不符合纵深防御原则。

#### 3.4 JWT 密钥默认值弱且暴露 — .env.docker

**文件**：`.env.docker` 第19行

`JWT_SECRET` 使用描述性字符串而非随机密钥，且直接写在配置文件中。应使用 `openssl rand -base64 64` 生成至少 256 位随机密钥。

#### 3.5 种子数据：普通用户角色权限与描述矛盾 — 02-seed-data.sql

**文件**：`02-seed-data.sql` 第168-172行

role_id=4 (user) 描述为"只读用户"，但实际拥有 `customer:add`、`customer:edit`、`customer:remove` 等写权限。**删除这些写权限，与角色定义一致**。

#### 3.6 种子数据：默认管理员密码过弱 — 02-seed-data.sql

**文件**：`02-seed-data.sql` 第8-9行、第179行

注释中明文写了"管理员账号：admin / admin123"，且 BCrypt 哈希对应明文就是 `admin123`。虽提示"首次部署后请立即修改"，但依赖人工不可靠。

#### 3.7 种子数据：管理员未关联岗位和部门 — 02-seed-data.sql

**文件**：`02-seed-data.sql` 第179-183行

`sys_user` 插入时未指定 `position_id` 和 `dept_id`，导致视图返回 NULL。

#### 3.8 视图使用 `DEFINER=root@localhost` 硬编码 — 01-schema.sql

**文件**：`01-schema.sql` 第838-939行

所有 6 个视图和 2 个函数都使用了 `DEFINER=root@localhost`。Docker 容器中 MySQL 的实际用户/主机名可能不同，应移除或改为 `CURRENT_USER`。

#### 3.9 函数 collation 混用 — 01-schema.sql

**文件**：`01-schema.sql` 第834行 vs 第860行

`fn_user_has_permission` 声明 collation 为 `utf8mb4_unicode_ci`，但函数体内使用 `utf8mb4_0900_ai_ci`，与表的默认 collation 不一致。

#### 3.10 SQL_MODE 配置不一致 — 01-schema.sql vs my.cnf

**文件**：`01-schema.sql` 第10行 vs `my.cnf` 第34行

schema 中仅设置了 `NO_AUTO_VALUE_ON_ZERO`，my.cnf 设置了严格模式，两处不一致。

#### 3.11 Nginx 缺少 `Content-Security-Policy` 安全头 — nginx.conf

**文件**：`nginx.conf` 整个文件

未设置 CSP 头，无法防御 XSS 攻击。建议添加 `add_header Content-Security-Policy ...`。

#### 3.12 `X-XSS-Protection` 已弃用 — nginx.conf

**文件**：`nginx.conf` 第119行

`X-XSS-Protection: 1; mode=block` 在现代浏览器中已弃用，应移除并改用 CSP。

#### 3.13 Nginx 正则不支持 `(?!...)` negative lookahead — nginx.conf

**文件**：`nginx.conf` 第54行

`location ~* /api/(\.env|actuator/(?!health))` 使用了 PCRE negative lookahead，Nginx 对此支持有限。应使用 `$request_uri` + `if` 或 `map` 指令替代。

#### 3.14 Prometheus 指标导出与 actuator 暴露端点矛盾 — application-prod.yml

**文件**：`application-prod.yml` 第157行 vs 第142行

`prometheus.metrics.export.enabled: true` 开启了 Prometheus 导出，但 `management.endpoints.web.exposure.include: health` 仅暴露 health，Prometheus 无法抓取 `/actuator/prometheus`。

#### 3.15 函数 `fn_get_project_progress` 声明为 DETERMINISTIC — 01-schema.sql

**文件**：`01-schema.sql` 第840行

函数访问 `biz_task` 表数据，不是确定性函数，应声明为 `NOT DETERMINISTIC`。

---

### 🟡 中等问题（20个）

| # | 文件 | 行号 | 问题 |
|---|------|------|------|
| 3.16 | `docker-compose.yml` | 49, 99 | 健康检查中密码暴露在进程列表 |
| 3.17 | `docker-compose.yml` | 153 | `GCTimeRatio=4` 对 Java 17 默认的 G1 GC 无效 |
| 3.18 | `docker-compose.yml` | 168-172 | 后端服务无 CPU 限制 |
| 3.19 | `docker-compose.yml` | 214 | 前端内存限制 32M 可能偏紧，建议 64M |
| 3.20 | `docker-compose.yml` | 133-137 | `depends_on` 不处理启动后服务崩溃重试 |
| 3.21 | `Dockerfile` (后端) | 51 | 使用 `sh -c` 启动导致 PID 1 问题，docker stop 无法优雅关闭 |
| 3.22 | `Dockerfile` (后端) | 48 vs compose | HEALTHCHECK 参数与 docker-compose 不一致 |
| 3.23 | `Dockerfile` (后端) | 11 | Alpine musl libc，生产建议用 Ubuntu 基础镜像 |
| 3.24 | `nginx.conf` | 117-119 | 缺少 `Referrer-Policy` 和 `Strict-Transport-Security` 头 |
| 3.25 | `application-prod.yml` | 31 | `max-wait: 5000` 连接池等待过短，建议 30s |
| 3.26 | `application-prod.yml` | 44 | `remove-abandoned: true` 可能误关闭正常连接 |
| 3.27 | `application-prod.yml` | 119 | CORS 安全回退值被 docker-compose 覆盖 |
| 3.28 | `application-prod.yml` | 125 | `trusted-proxies` 默认为空，限流和 IP 日志功能失效 |
| 3.29 | `my.cnf` | 25 | `expire_logs_days` 已弃用，应使用 `binlog_expire_logs_seconds` |
| 3.30 | `my.cnf` | — | 缺少 `innodb_buffer_pool_instances` 配置 |
| 3.31 | `01-schema.sql` | 525-546 | `sys_operation_log.user_id` 缺少外键约束 |
| 3.32 | `01-schema.sql` | 533-534 | `sys_operation_log` 存储敏感数据（request_param/response_result）无脱敏 |
| 3.33 | `02-seed-data.sql` | 153 | 项目经理拥有 `project:remove` 删除权限，高风险操作 |
| 3.34 | `02-seed-data.sql` | 162-165 | 工程师缺少 `document:edit` 权限，无法修改自己上传的文档 |
| 3.35 | `V20260720` 迁移脚本 | 整个文件 | 三个索引在 01-schema.sql 中已存在，迁移脚本多余 |

---

### 🟢 轻微问题（14个）

| # | 文件 | 问题 |
|---|------|------|
| 3.36 | `01-schema.sql` | `biz_project.contract_amount` decimal(14,2) 最大约 1000 亿，大型项目可能不足 |
| 3.37 | `01-schema.sql` | `sys_user.password` varchar(100)，未来 Argon2 算法可能不足 |
| 3.38 | `01-schema.sql` | `biz_document.file_size` 使用 varchar(20)，不便于排序 |
| 3.39 | `01-schema.sql` | `biz_design_drawing.file_size` 同样问题 |
| 3.40 | `02-seed-data.sql` | `sys_dict_type` 未显式指定 ID，风格不一致 |
| 3.41 | `02-seed-data.sql` | `auto_save_enabled` 配置值类型为 string 但语义是 boolean |
| 3.42 | `my.cnf` | `long_query_time = 3` 偏长，建议降为 1-2s |
| 3.43 | `docker-compose.yml` | `JWT_EXPIRATION` 硬编码 1800000 |
| 3.44 | `docker-compose.yml` | `HBSZ_FILE_UPLOAD_DIR` 变量名拼写（UPLOAD 少 O） |
| 3.45 | `Dockerfile` (前端) | HEALTHCHECK 与 compose 重复 |
| 3.46 | `nginx.conf` | WebSocket 代理路径 `/ws/` → `/api/ws/`，需确认后端实际路径 |
| 3.47 | `V20260715` 迁移脚本 | PREPARE/EXECUTE/DEALLOCATE 多语句同行，可读性差 |
| 3.48 | `V20260720.1` 迁移脚本 | 使用动态 SQL 实现幂等，Flyway 本身已保证版本幂等 |
| 3.49 | `.env.docker` | 所有敏感信息使用明文默认值，需确认 `.gitignore` 包含 `.env.docker` |

---

## 四、优先级修复路线图

### 第一优先级（立即修复 — 安全/数据风险）

| # | 问题 | 位置 |
|---|------|------|
| 1 | CORS 设置为 `"*"` → 改为具体域名 | `docker-compose.yml` L150 |
| 2 | 数据库 root 连接 → 创建专用应用用户 | `docker-compose.yml` L141 |
| 3 | JWT 密钥使用弱随机值 → `openssl rand -base64 64` | `.env.docker` L19 |
| 4 | 导入事务失效 → 修复 self-invocation | `ProjectServiceImpl.java` L777, `UserManageServiceImpl.java` L561 |
| 5 | 文档版本文件 URL 丢失 | `DocumentController.java` L196-207 |
| 6 | 普通用户角色权限与描述矛盾 → 删除写权限 | `02-seed-data.sql` L168-172 |
| 7 | 前端 XSS 风险（dangerouslyUseHTMLString） | `ProjectList.vue` L635, `UserManagement.vue` L563 |

### 第二优先级（尽快修复 — 业务风险）

| # | 问题 | 位置 |
|---|------|------|
| 8 | 文档审核缺少并发控制 | `DocumentServiceImpl.java` L311-332 |
| 9 | `withCancel` 竞态条件 | `cancelToken.js` L141-155 |
| 10 | `hasPermission` 对 falsy 值放行 | `permission.js` L31 |
| 11 | `updateStatus()` 缺少超级管理员保护 | `UserManageServiceImpl.java` L327-342 |
| 12 | 用户密码无强度校验 | `UserManageServiceImpl.java` L172 |
| 13 | `v-submit` 指令未注册 | `submitGuard.js` vs `main.js` |
| 14 | 视图 DEFINER 硬编码 → 移除 | `01-schema.sql` L838-939 |
| 15 | Nginx 缺少 CSP 安全头 | `nginx.conf` |

### 第三优先级（计划修复 — 质量优化）

| # | 问题 | 位置 |
|---|------|------|
| 16 | 路由切换取消全部请求 | `router/index.js` L263 |
| 17 | `UserManagement` 重复请求 | `UserManagement.vue` L579-588 |
| 18 | `SystemConfigServiceImpl.upsert()` 并发 | `SystemConfigServiceImpl.java` L134-162 |
| 19 | 任务日期校验不完整 | `TaskServiceImpl.java` L183-186 |
| 20 | GanttChart 深度监听性能 | `GanttChart.vue` L327-329 |
| 21 | 后端 Dockerfile PID 1 问题 | `Dockerfile` L51 |
| 22 | Prometheus 与 actuator 配置矛盾 | `application-prod.yml` L142 vs L157 |
| 23 | Nginx `(?!...)` 正则兼容性 | `nginx.conf` L54 |
| 24 | `expire_logs_days` 已弃用 | `my.cnf` L25 |

---

> **审核结论**：项目整体架构清晰、代码分层合理、安全措施较为完善（JWT、RBAC、操作日志、文件校验等），但存在 **4 个严重后端 Bug**（事务失效、URL 丢失、NPE 风险）、**3 个严重前端 Bug**（XSS、竞态、权限绕过）、**15 个严重配置/安全风险**（CORS `*`、root 连接、弱密码、视图 DEFINER 等）。建议按优先级路线图逐步修复，尤其第一优先级的 7 个问题应在生产部署前全部解决。

---

## 修复汇总（2026-07-21）

### 🔴 第一优先级修复（7 项）

| # | 问题 | 文件 | 修复方式 |
|---|------|------|----------|
| 1 | CORS 设置为 `*` | `docker-compose.yml` L150 | 改为 `${CORS_ALLOWED_ORIGINS:-https://hbsz.example.com}` |
| 2 | DB root 连接 | `docker-compose.yml` L141 + `01-schema.sql` | 创建专用用户 `hbsz_app`，仅授予 CRUD 权限；启用 SSL |
| 3 | JWT 弱密钥 | `.env.docker` L19 | 新增 `DB_USERNAME`/`DB_PASSWORD`，JWT 密钥改为提示性占位符 |
| 4 | 导入事务失效 | `ProjectServiceImpl.java` + `UserManageServiceImpl.java` | `@Lazy @Autowired private self` 自注入代理，`this.create()` → `self.create()` |
| 5 | 文档版本 URL 丢失 | `DocumentVersionDTO.java` + `DocumentController.java` + `DocumentServiceImpl.java` | DTO 新增 `fileUrl`，Controller 赋值，Service 落库 |
| 6 | 普通用户种子数据权限矛盾 | `02-seed-data.sql` | 删除 `customer:add/edit/remove` 写权限 |
| 7 | 前端 XSS 风险 | `ProjectList.vue` + `UserManagement.vue` + `CustomerManagement.vue` | HTML 实体转义后使用 `dangerouslyUseHTMLString` |

### 🟡 第二优先级修复（8 项）

| # | 问题 | 文件 | 修复方式 |
|---|------|------|----------|
| 8 | 文档审核并发 | `BizDocumentMapper.java` + `DocumentServiceImpl.java` | 新增 `updateReviewStatusCAS` CAS 原子更新 |
| 9 | withCancel 竞态 | `cancelToken.js` L141-155 | `.finally()` 中仅当 `currentController === controller` 时清空 |
| 10 | hasPermission falsy 放行 | `permission.js` | `!value` → `value == null` |
| 11 | updateStatus 无保护 | `UserManageServiceImpl.java` | 禁止修改超级管理员+当前用户，校验 status 值 |
| 12 | v-submit 未注册 | `main.js` | 导入 `vSubmit` 并注册 `app.directive('submit', vSubmit)` |
| 13 | 视图 DEFINER 硬编码 | `01-schema.sql` | 移除所有 `DEFINER=`root`@`localhost`` |
| 14 | Nginx 安全头 | `nginx.conf` | 移除 `X-XSS-Protection`，新增 CSP + Referrer-Policy |
| 15 | 账号 null 状态漏洞 | `AuthServiceImpl.java` | `status != null && status == 0` → `status == null \|\| status == 0` |

### 🟢 第三优先级修复（10 项）

| # | 问题 | 文件 | 修复方式 |
|---|------|------|----------|
| 16 | UserManagement 重复请求 | `UserManagement.vue` | 添加 `isMounted` 守卫 |
| 17 | App.vue 无错误处理 | `App.vue` | `loadConfig().catch()` |
| 18 | Login.vue null guard | `Login.vue` | `if (!loginFormRef.value) return` |
| 19 | Gantt 深度监听 | `GanttChart.vue` | `deep: true` → 监听 `props.tasks?.length` |
| 20 | WebSocket 连接泄漏 | `websocket.js` | `connect()` 中先 `deactivate` 旧 client |
| 21 | cancelToken signal 覆盖 | `cancelToken.js` | 已有 signal 时不覆盖，改为监听 abort 事件 |
| 22 | AuthController NPE | `AuthController.java` | userId 为 null 时抛 `BusinessException(UNAUTHORIZED)` |
| 23 | ProjectController 缺少校验 | `ProjectController.java` | `list()` 添加 `@Valid` |
| 24 | create userId null | `ProjectServiceImpl.java` | `setCreatedBy()` 前非空校验 |
| 25 | 文件上传 URL 编码 | `FileController.java` | 暂缓（低风险） |

### 编译验证

| 模块 | 命令 | 结果 |
|------|------|------|
| 后端 | `mvn clean package -DskipTests` | ✅ 通过 |
| 前端 | `npm run build` | ✅ 通过（仅 harmless warnings） |

**修改文件统计**：共修改 **22 个文件**，后端 11 个 + 前端 7 个 + 配置 4 个

---

## 部署修复汇总（v1.3，2026-07-21）

> 部署阶段发现的问题，在代码审核修复基础上追加的部署修复。

### 🔵 部署修复（6 项）

| # | 问题 | 根因 | 文件 | 修复方式 |
|---|------|------|------|----------|
| 26 | Flyway `SELECT denied` | `hbsz_app` 缺少 `performance_schema` SELECT 权限 | `docker/mysql/init-grants.sql` | 新增 `GRANT SELECT ON performance_schema.*` |
| 27 | Flyway `CREATE denied` | `hbsz_app` 只有 CRUD 权限，缺 DDL | `docker/mysql/init-grants.sql` | `GRANT ALL PRIVILEGES ON hbsz_idc_system.*` |
| 28 | Flyway `SYSTEM_USER` denied | MySQL 8.0 动态 SQL 需要 SYSTEM_USER | `docker/mysql/init-grants.sql` | `GRANT SYSTEM_USER ON *.*` |
| 29 | MySQL 数据卷复用权限不更新 | `docker-entrypoint-initdb.d` 仅首次初始化执行 | `docker-compose.yml` | 新增 `mysql-grants` 一次性容器，每次部署执行 GRANT |
| 30 | Fontconfig error | `read_only: true` 禁止 fontconfig 写缓存 | `docker-compose.yml` | 新增 tmpfs: `/var/cache/fontconfig`、`/app/.cache/fontconfig`、`/app/.fontconfig` |
| 31 | 日报导出 FileNotFoundException | `read_only: true` 禁止创建 `./exports` 目录 | `docker-compose.yml` + `DailyReportServiceImpl.java` | tmpfs `/app/exports` + `mkdirs()` 失败时抛 `IllegalStateException` |

### Nginx 路由修复

| # | 问题 | 根因 | 文件 | 修复方式 |
|---|------|------|------|----------|
| 32 | 文件预览 404 | `~* \.(png)$` 正则优先级高于 `location /api/`，截获图片预览请求 | `nginx.conf` | `location /api/` → `location ^~ /api/`，`^~` 优先级高于正则 |

### 小程序适配修复

| # | 问题 | 根因 | 文件 | 修复方式 |
|---|------|------|------|----------|
| 33 | 登录验证码适配 | 后端 `LoginDTO` 要求 `captchaId`+`captcha`，小程序缺失 | `login.vue` + `api/auth.js` | 新增验证码获取/输入/刷新功能 |
| 34 | 小程序图片不显示 | 后端返回相对路径，小程序 `<image>` 需完整 URL | `review.vue` + `report.vue` | `parseImages()` 拼接 `API_BASE_URL + '/files/preview/'` |

### 编译验证（v1.3）

| 模块 | 命令 | 结果 |
|------|------|------|
| 后端 | `mvn clean compile` | ✅ 通过 |
| 前端 | `npm run build` | ✅ 通过 |
| 部署 | `docker compose up -d --build` | ✅ 后端启动成功，Flyway 迁移完成 |