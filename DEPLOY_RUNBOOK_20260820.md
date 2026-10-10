# 生产部署操作手册（2026-08-20）

> **适用场景**：生产环境有正在运行的业务数据，必须在完整备份前提下部署本批修复
> **服务器目录**：`~/hbsz/`（与 DEPLOYMENT.md 约定一致）
> **预计实际停机**：3-6 分钟（建议预约 30 分钟维护窗口）
> **硬性约定**：所有 `docker compose` 命令必须带 `--env-file .env.docker`（W3 修复后缺失变量会直接报错，含 `down`/`logs`）

---

## 〇、本次部署内容与影响范围

| 编号 | 修复 | 涉及文件 | 生效方式 |
|------|------|----------|----------|
| B1 | `.dockerignore` 不再排除 `dist/`（此前与 Dockerfile COPY 冲突，`--build` 必然失败） | `Hbsz_IDC_System/.dockerignore` | 服务器重建前端镜像 |
| B2 | `.dockerignore` 保留最终 JAR、排除其余构建杂物 | `Hbsz_IDC_System_end/.dockerignore` | 服务器重建后端镜像 |
| W3 | 4 个敏感凭据（MYSQL_ROOT_PASSWORD / DB_PASSWORD / REDIS_PASSWORD / JWT_SECRET）改 `${VAR:?}` 强制形式，缺失即拒绝启动 | `docker-compose.yml`、`.env.docker`（注释） | 服务器重建容器 |
| W1 | actuator 拦截由失效正则改为前缀匹配：仅放行 `/api/actuator/health`，其余 actuator 路径 404 | `Hbsz_IDC_System/nginx.conf` | 服务器重建前端镜像 |
| P0（上轮后端修复，随本次一并上线） | 审计日志 `operated_by` 可空化 + 授权过期回收任务修复 + 收敛审计写入单点 | 后端 JAR（含 Flyway 迁移 `V20260820.1`） | 后端启动时 Flyway 自动执行 |

**影响评估**：
- 停机：`down → up` 全程（MySQL 重启约 30-60s + grants 约 10s + JVM 启动约 60-120s）
- 若本次轮换 `JWT_SECRET`：**所有用户登录态失效，需重新登录**（请提前通知）
- 数据库变更：`V20260820.1` 仅将一列放宽为可空（元数据级操作，无数据重写，前向兼容——**代码回滚不需要还原数据库**）
- 未包含项（后续跟进，本次不动）：W2 限流 TRUSTED_PROXIES、W4 exports tmpfs 内存、S1 my.cnf 弃用参数

---

## 一、阶段一：生产数据备份（必须最先执行，全程业务无感）

### 1.0 前置检查

```bash
ssh user@your-server
cd ~/hbsz

# 磁盘空间：可用空间需 ≥（数据库大小×3 + uploads 大小×2 + 旧 JAR/dist 备份）
df -h .
export PW=$(grep '^MYSQL_ROOT_PASSWORD=' ~/hbsz/.env.docker | cut -d= -f2)
docker exec hbsz-mysql mysql -u root -p"$PW" -e \
  "SELECT ROUND(SUM(data_length+index_length)/1024/1024,1) AS db_mb FROM information_schema.tables WHERE table_schema='hbsz_idc_system';"
docker exec hbsz-mysql mysql -u root -p"$PW" -e \
  "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='hbsz_idc_system' ORDER BY table_rows DESC LIMIT 10;"
```

> 停止准则：磁盘可用空间不足时**不得继续**，先清理（`docker system prune` 需谨慎，勿删数据卷）。

### 1.1 备份当前生产配置与旧构建产物（回滚必需）

> B1/B2 的根因就是"仓库配置与服务器实际状态漂移"——此步同时用于捕捉服务器侧的历史手工修改。

```bash
mkdir -p ~/hbsz/backup/20260820/pre
cd ~/hbsz

# 配置文件（服务器当前真实状态）
cp docker-compose.yml .env.docker backup/20260820/pre/
cp Hbsz_IDC_System/nginx.conf Hbsz_IDC_System/Dockerfile Hbsz_IDC_System/.dockerignore backup/20260820/pre/ 2>/dev/null
cp Hbsz_IDC_System_end/Dockerfile Hbsz_IDC_System_end/.dockerignore backup/20260820/pre/ 2>/dev/null

# 旧构建产物（上传新 JAR/dist 前保留，代码回滚必需）
cp Hbsz_IDC_System_end/target/Hbsz_IDC_System_end.jar backup/20260820/pre/old_backend.jar
tar czf backup/20260820/pre/old_dist.tar.gz -C Hbsz_IDC_System/dist .

# 运行时状态快照
docker inspect hbsz-backend > backup/20260820/pre/backend-inspect.json
docker inspect hbsz-frontend > backup/20260820/pre/frontend-inspect.json
docker images > backup/20260820/pre/images.txt
```

### 1.2 MySQL 逻辑备份（核心数据）

> `--single-transaction`：InnoDB 一致性快照，不锁表，业务无感知，无需停机。

```bash
cd ~/hbsz
TS=$(date +%Y%m%d_%H%M%S)
docker exec hbsz-mysql mysqldump -u root -p"$PW" \
  --single-transaction --routines --triggers --set-gtid-purged=OFF \
  hbsz_idc_system | gzip > backup/20260820/mysql_${TS}.sql.gz
```

### 1.3 备份验证（三重校验，缺一不可）

```bash
# 1) gzip 完整性
gzip -t backup/20260820/mysql_${TS}.sql.gz && echo "[OK] gzip 完整"

# 2) 表数量一致：dump 内 CREATE TABLE 数 = 库内实际表数
zcat backup/20260820/mysql_${TS}.sql.gz | grep -c 'CREATE TABLE'
docker exec hbsz-mysql mysql -u root -p"$PW" -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hbsz_idc_system';"

# 3) 业务数据非空 + 核心表行数对账（此记录同时是恢复后的验收基准）
zcat backup/20260820/mysql_${TS}.sql.gz | grep -c 'INSERT INTO'   # 应 > 0
docker exec hbsz-mysql mysql -u root -p"$PW" hbsz_idc_system -e \
  "SELECT 'sys_user' AS tbl, COUNT(*) AS cnt FROM sys_user
   UNION ALL SELECT 'biz_project', COUNT(*) FROM biz_project
   UNION ALL SELECT 'biz_task', COUNT(*) FROM biz_task
   UNION ALL SELECT 'biz_project_assignment', COUNT(*) FROM biz_project_assignment
   UNION ALL SELECT 'biz_document', COUNT(*) FROM biz_document
   UNION ALL SELECT 'biz_project_transfer_log', COUNT(*) FROM biz_project_transfer_log;" \
  | tee backup/20260820/rowcounts_before.txt
```

> 停止准则：任一校验失败（gzip 损坏 / 表数不符 / INSERT 为 0）→ **不得部署**，重做备份。

### 1.4 uploads 数据卷备份（用户上传文件，不在数据库内）

```bash
docker run --rm -v hbsz-uploads-data:/data:ro -v ~/hbsz/backup/20260820:/backup alpine \
  tar czf /backup/uploads_${TS}.tar.gz -C /data .
gzip -t backup/20260820/uploads_${TS}.tar.gz && echo "[OK] uploads 备份完整"
```

### 1.5 Redis 备份（可选）

> Redis 仅存缓存/验证码/幂等键，丢失可接受（幂等键 300s 自动过期；若轮换 JWT_SECRET 用户本就需重登）。做一份以求完整：

```bash
export RP=$(grep '^REDIS_PASSWORD=' ~/hbsz/.env.docker | cut -d= -f2)
docker exec hbsz-redis redis-cli -a "$RP" BGSAVE
sleep 2
docker run --rm -v hbsz-redis-data:/data:ro -v ~/hbsz/backup/20260820:/backup alpine \
  cp /data/dump.rdb /backup/redis_${TS}.rdb
```

### 1.6 异机保存（防勒索/误删——同机备份与生产同生共死）

```bash
cd ~/hbsz/backup/20260820
sha256sum * > SHA256SUMS
```

```bash
# 在本地开发机执行（将备份拉离生产服务器）：
scp -r user@your-server:~/hbsz/backup/20260820 ./backup_20260820_生产备份/
```

> **检查点 ①：备份完成**——mysql dump + uploads tar + 旧 JAR/dist + 配置均已生成、校验通过、已拉取到本地。未达此检查点不得进入阶段三。

---

## 二、阶段二：本地构建（开发机执行）

```bash
# 后端（含上轮 P0 修复；构建前跑全量测试，上轮基线 106/106 通过）
cd Hbsz_IDC_System_end
mvn clean package
ls -lh target/Hbsz_IDC_System_end.jar    # 预期约 80-100MB
# 确认迁移脚本已打进 JAR：
# jar tf target/Hbsz_IDC_System_end.jar | grep V20260820.1
# 预期：BOOT-INF/classes/db/migration/V20260820.1__transfer_log_operated_by_nullable.sql

# 前端
cd ../Hbsz_IDC_System
npm install
npm run build
ls dist/    # 预期：index.html + assets/（hash 文件名）
```

---

## 三、阶段三：上传与部署前校验（业务仍在线）

### 3.1 上传

> **严禁上传 `.env.docker`**：服务器上可能已配置真实强密码，用本地弱值覆盖会同时破坏 MySQL root 认证与后端连接。

```bash
scp docker-compose.yml user@your-server:~/hbsz/
scp Hbsz_IDC_System/Dockerfile Hbsz_IDC_System/nginx.conf Hbsz_IDC_System/.dockerignore user@your-server:~/hbsz/Hbsz_IDC_System/
scp Hbsz_IDC_System_end/Dockerfile Hbsz_IDC_System_end/.dockerignore user@your-server:~/hbsz/Hbsz_IDC_System_end/
scp Hbsz_IDC_System_end/target/Hbsz_IDC_System_end.jar user@your-server:~/hbsz/Hbsz_IDC_System_end/target/
scp -r Hbsz_IDC_System/dist/* user@your-server:~/hbsz/Hbsz_IDC_System/dist/
```

### 3.2 服务器侧校验

```bash
cd ~/hbsz

# 1) compose 语法 + 强制变量校验（W3：任何必需变量缺失在此直接暴露）
docker compose --env-file .env.docker config --quiet && echo "[OK] compose 配置合法"

# 2) nginx 配置校验（借运行中的 hbsz-network 解析 backend 主机名）
docker run --rm --network hbsz-network \
  -v ~/hbsz/Hbsz_IDC_System/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  nginx:1.27-alpine nginx -t
# 预期：syntax is ok / test is successful

# 3) 配置漂移核对：差异必须逐项确认为 B1/B2/W3/W1 的预期修复
#    若发现服务器侧存在历史手工修改被本次上传覆盖，先手工并回再继续
diff backup/20260820/pre/docker-compose.yml docker-compose.yml
diff backup/20260820/pre/nginx.conf Hbsz_IDC_System/nginx.conf
```

### 3.3 `.env.docker` 密码决策表（在服务器上原地编辑，勿覆盖）

| 变量 | 本次动作 | 说明 |
|------|----------|------|
| MYSQL_ROOT_PASSWORD | **不改** | 数据卷首次初始化后改此值不会变更实际 root 密码，只会导致 mysql-grants/健康检查失配 |
| DB_PASSWORD | 可选轮换 | 改后 mysql-grants 容器自动 `ALTER USER` 同步（前提：备份已完成） |
| REDIS_PASSWORD | 可选轮换 | redis 与 backend 同次重建，自动一致 |
| JWT_SECRET | 若仍为弱值/占位符 → **强烈建议轮换**（`openssl rand -base64 64`）；已是强随机值 → 不动 | 轮换 = 全员重新登录 |
| CORS_ALLOWED_ORIGINS | 核对为实际域名 | 占位符 `hbsz.example.com` 会导致跨域请求被拒 |

> **检查点 ②：校验全绿**——compose config OK、nginx -t OK、diff 逐项确认、密码决策已执行。

---

## 四、阶段四：执行部署（维护窗口内）

```bash
cd ~/hbsz
docker compose --env-file .env.docker down
docker compose --env-file .env.docker up -d --build
```

### 启动观察（三个关键信号）

```bash
# 1) 容器状态（等待全部 healthy；backend start_period 180s，2G 机器 JVM 启动约 1-2 分钟）
docker compose --env-file .env.docker ps

# 2) grants 一次性容器成功退出
docker inspect hbsz-mysql-grants --format 'ExitCode={{.State.ExitCode}}'   # 预期 0

# 3) 后端日志：Flyway 应用新迁移 + 应用启动完成
docker compose --env-file .env.docker logs backend | grep -E "Flyway|Successfully applied|Started HbszIdcSystemEndApplication"
# 必须看到：Successfully applied ... V20260820.1__transfer_log_operated_by_nullable
```

> 停止准则（满足任一 → 执行阶段六回滚）：backend 反复重启 / Flyway 迁移报错且不自动恢复 / 前端 502 持续超过 10 分钟 / grants 容器非 0 退出且日志显示认证失败。

---

## 五、阶段五：部署后验证

```bash
# 1) 前端静态资源就位（验证 B1 修复：dist 已正确打入镜像）
docker exec hbsz-frontend ls /usr/share/nginx/html/    # 预期 index.html + assets/

# 2) W1 修复验证（核心安全项）
curl -s -o /dev/null -w "health=%{http_code}\n" http://localhost/api/actuator/health   # 预期 200
curl -s -o /dev/null -w "env=%{http_code}\n"    http://localhost/api/actuator/env      # 预期 404（修复前会被代理到后端）
curl -s -o /dev/null -w "base=%{http_code}\n"   http://localhost/api/actuator          # 预期 404

# 3) 业务冒烟（浏览器执行）：
#    登录（注意验证码）→ 工作台 → 项目列表 → 文件预览（验证 ^~ /api/ 路由未回归）
#    → 站内通知（验证 WebSocket）→ 任一导出（验证 /app/exports tmpfs）

# 4) 登录接口连通性（脚本层）
curl -s -X POST http://localhost/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"test_probe","password":"x","captchaId":"x","captcha":"x"}'
# 预期：业务错误响应（账号或验证码错误）而非 404/502 —— 证明链路通

# 5) P0 修复验证（次日自动触发）：
docker compose --env-file .env.docker logs backend 2>&1 | grep "授权过期回收" || echo "无过期授权时无输出，属正常"
# 若存在过期授权，预期：[授权过期回收][完成] 总数=N, 成功=N, 失败=0
# 且 biz_project_transfer_log 出现 operator_name='系统定时任务' 的 revoke 记录
```

> **检查点 ③：验收通过**——记录验证结果与时间。后续 24 小时内留意 `docker compose ps` 与错误日志。

---

## 六、阶段六：回滚预案

### 6.0 回滚决策表

| 症状 | 动作 | 数据影响 |
|------|------|----------|
| 后端起不来 / 依赖报错 / Flyway 失败 | 6.1 回滚后端 | 无（V20260820.1 前向兼容，库不用动） |
| 前端异常 / nginx 配置问题 | 6.2 回滚前端 | 无 |
| compose 变量报错 | 检查服务器 .env.docker（阶段三应已拦截） | 无 |
| 数据损坏 / 误删 | 6.3 数据恢复（最后手段） | 恢复到备份时点，之后的写入丢失 |

### 6.1 回滚后端（旧 JAR 已在 1.1 备份）

```bash
cd ~/hbsz
cp backup/20260820/pre/old_backend.jar Hbsz_IDC_System_end/target/Hbsz_IDC_System_end.jar
docker compose --env-file .env.docker up -d --build backend
```

### 6.2 回滚前端（旧配置与 dist 已在 1.1 备份）

```bash
cd ~/hbsz
cp backup/20260820/pre/nginx.conf Hbsz_IDC_System/nginx.conf
rm -rf Hbsz_IDC_System/dist && mkdir Hbsz_IDC_System/dist
tar xzf backup/20260820/pre/old_dist.tar.gz -C Hbsz_IDC_System/dist
docker compose --env-file .env.docker up -d --build frontend
```

### 6.3 数据恢复（仅数据损坏时，危险操作）

> 会将数据库整体恢复到备份时点。先停后端防写入，恢复后核对阶段 1.3 的行数基准。

```bash
cd ~/hbsz
docker compose --env-file .env.docker stop backend
docker exec hbsz-mysql mysql -u root -p"$PW" -e \
  "DROP DATABASE hbsz_idc_system; CREATE DATABASE hbsz_idc_system CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
zcat backup/20260820/mysql_*.sql.gz | docker exec -i hbsz-mysql mysql -u root -p"$PW" hbsz_idc_system
# 核对行数（应与 rowcounts_before.txt 一致）后，回滚到旧 JAR 再启动：
docker compose --env-file .env.docker up -d --build
```

uploads 卷恢复（仅文件丢失时）：

```bash
docker compose --env-file .env.docker stop backend
docker run --rm -v hbsz-uploads-data:/data -v ~/hbsz/backup/20260820:/backup alpine \
  sh -c "rm -rf /data/* && tar xzf /backup/uploads_*.tar.gz -C /data"
docker compose --env-file .env.docker start backend
```

---

## 七、应急停止

```bash
# 紧急全停（数据卷保留）
docker compose --env-file .env.docker down
# 或只停出问题的服务
docker stop hbsz-backend
# W3 副作用提醒：不带 --env-file 的 compose 命令会因必需变量缺失而解析失败，
# 应急时也可直接操作容器：docker stop / docker logs hbsz-backend
```

---

## 附：时间线参考

| 阶段 | 耗时 | 业务影响 |
|------|------|----------|
| 一、备份（含异机拷贝） | 10-30 分钟（视 uploads 体量） | 无（在线热备） |
| 二、本地构建 | 10-20 分钟 | 无 |
| 三、上传与校验 | 10-20 分钟 | 无 |
| 四、部署 | 实际停机 3-6 分钟 | 全站不可用 |
| 五、验证 | 10-15 分钟 | 无 |
