# 更新日志（CHANGELOG）

本项目所有显著变更均记录于此。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/)。

---

## [Unreleased] - 2026-11-03

### 安全修复
- **[P0-1] WebSocket 鉴权链路断裂** — 前端 `ws.js` 移除 URL `?access_token=` 拼接（token 泄露到日志/Referer），改为 `uni.connectSocket` 握手请求头 `Authorization` 传递；无 token 时降级轮询，杜绝未授权 WS 连接
- **[P0-2] 前端明文 HTTP IP** — `config.js` 的 `API_BASE_URL` 改为按 `NODE_ENV` 区分，生产强制 HTTPS（原硬编码 `http://124.220.158.177`）
- **[P1-1] Token 明文存储** — `auth.js` 存储底层切换为 `cryptoStorage`（XOR+Base64 混淆），激活原死代码
- **[P1-2] MySQL 账号分离** — 运行时 `hbsz_app` 收窄为 DML（SELECT/INSERT/UPDATE/DELETE），新增迁移账号 `hbsz_migrate` 持 DDL+SYSTEM_USER（迁移脚本 PREPARE 动态 SQL 必需）；Flyway 配独立账号
- **[P1-3] CORS 默认值** — `CorsConfig` 生产环境（prod profile）使用通配符 `*` 时启动直接失败
- **[P2-1] JwtUtils 签名密钥缓存** — `@PostConstruct` 构造一次 `SecretKey`，避免每请求重复构造
- **[P2-2] BCrypt cost 提升** — 10 → 12，可配置 `hbsz.password.bcrypt-cost`
- **[P2-3] 文件头校验资源泄漏** — `validateFileSignature` 改 try-with-resources 关闭 InputStream
- **[P2-4] healthcheck 明文密码** — mysql 用 `MYSQL_PWD`、redis 用 `REDISCLI_AUTH` 环境变量认证，密码不再进 ps/docker inspect

### 性能优化
- **[P1-4] 统计 SQL 索引失效** — `BizProjectMapper.xml` 的 `countByMonth` 由 `WHERE YEAR(created_at)=` 改为 `[年初, 次年初)` 区间比较，走索引
- **[P1-5] 统计概览并行化** — `StatisticsServiceImpl.getOverview` 5 次串行 DB 查询改 `CompletableFuture` 并行执行

### 新增功能
- **自动化部署脚本** — `scripts/deploy.sh`：参数化部署，含环境校验、依赖检查、构建打包、服务停启、健康检查、失败自动回滚（镜像级优先→代码级）、彩色日志
- **环境变量模板** — `.env.docker.template`：含所有变量说明与强密码生成提示

### 文档
- 新增 `CODE_REVIEW_SECURITY_PERF_20261102.md`：性能与安全专项审核报告（12 项问题分级与修复状态）
- `.gitignore` 收紧：排除含凭证的本地运维脚本（`deploy_helper.py` 等 10 个）、`.workbuddy/`、`.deploy-backup/`；放行 `.env.docker.template` 模板

### 已确认无需修复
- **[P1-6] 文档越权 IDOR** — 经核 `DocumentServiceImpl` 全部读写方法已有 `canAccessProject()` 数据级校验

### 破坏性变更
- 无（所有改动向后兼容；`hbsz_app` 账号权限收窄后，需确保 Flyway 用 `hbsz_migrate` 账号，见 `application-prod.yml` 的 `spring.flyway.user`）

### 升级注意
1. **部署前**：在 `.env.docker` 新增 `FLYWAY_PASSWORD`（参考 `.env.docker.template`）
2. **BCrypt cost 提升**：cost 12 不影响存量密码校验（BCrypt 自适应），仅新注册/改密码时生效
3. **前端 token 存储**：切换到 `cryptoStorage` 后，历史明文 token 用户需重新登录一次

---

## 详细审核报告
完整的代码审核过程、问题分级与修复详情见 [CODE_REVIEW_SECURITY_PERF_20261102.md](./CODE_REVIEW_SECURITY_PERF_20261102.md)。
