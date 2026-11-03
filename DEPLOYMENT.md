# 数据中心综合布线项目管理系统 - Docker 部署文档

> 适用环境：2核2G 轻量云服务器（本地构建产物上传部署）  
> 文档版本：v1.4  
> 更新日期：2026-11-03（v1.4：新增自动化部署脚本 deploy.sh + MySQL 账号分离 + 安全性能修复）  
> 编译状态：✅ 后端 mvn package 通过 | ✅ 前端 npm run build 通过
>
> **推荐：使用自动化部署脚本** `scripts/deploy.sh`，支持参数化部署、健康检查、失败自动回滚。  
> 快速开始：`./scripts/deploy.sh --branch main`　或　`./scripts/deploy.sh --help`  
> 本轮全部变更详见 [CHANGELOG.md](./CHANGELOG.md)

---

## 一、环境准备

### 1.1 系统要求

| 组件             | 要求                                    |
| -------------- | ------------------------------------- |
| 操作系统           | Ubuntu 22.04 / CentOS 8+ / Debian 11+ |
| CPU            | 2核                                    |
| 内存             | 2GB                                   |
| 磁盘             | 20GB+                                 |
| Docker Engine  | 24.0+                                 |
| Docker Compose | v2.20+                                |

### 1.2 安装 Docker

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | bash
sudo systemctl enable docker && sudo systemctl start docker

# 当前用户加入 docker 组（免 sudo，需重新登录生效）
sudo usermod -aG docker $USER

# 验证
docker --version
docker compose version
```

### 1.3 创建 Swap（2GB 内存必须配置）

2GB 内存服务器必须添加 Swap，否则 MySQL + JVM 可能触发 OOM：

```bash
# 创建 2GB Swap 文件
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 开机自动挂载
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 优化 swappiness（优先用内存，仅在内存不足时使用 Swap）
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

# 验证
free -h
# Swap 行应显示 2.0Gi
```

### 1.4 服务器目录结构

```bash
mkdir -p ~/hbsz
cd ~/hbsz
# 最终目录结构：
# ~/hbsz/
# ├── docker-compose.yml
# ├── .env.docker
# ├── docker/mysql/
# │   ├── my.cnf              # MySQL 性能配置
# │   ├── 01-schema.sql       # 完整表结构（28张表+6视图+2函数）
# │   └── 02-seed-data.sql    # 种子数据（部门/岗位/角色/权限/管理员）
# ├── Hbsz_IDC_System_end/
# │   ├── Dockerfile
# │   └── target/
# │       └── Hbsz_IDC_System_end.jar    # 后端 JAR 包
# └── Hbsz_IDC_System/
#     ├── Dockerfile
#     ├── nginx.conf
#     └── dist/                           # 前端构建产物
#         └── index.html
```

---

## 二、本地构建（在开发机上执行）

### 2.1 构建后端 JAR 包

```bash
cd Hbsz_IDC_System_end

# Maven 构建（跳过测试）
mvn clean package -DskipTests

# 验证 JAR 包
ls -lh target/Hbsz_IDC_System_end.jar
# 预期：约 80-100MB
```

### 2.2 构建前端 dist

```bash
cd Hbsz_IDC_System

# 安装依赖
npm install

# 构建生产版本
npm run build

# 验证产物
ls -la dist/
# 预期：index.html + assets/ 目录
```

### 2.3 上传到服务器

```bash
# 在服务器上创建目录
ssh user@your-server "mkdir -p ~/hbsz/Hbsz_IDC_System_end/target ~/hbsz/Hbsz_IDC_System/dist ~/hbsz/docker/mysql"

# 上传 Docker 配置文件
scp docker-compose.yml user@your-server:~/hbsz/
scp .env.docker user@your-server:~/hbsz/
scp -r docker/ user@your-server:~/hbsz/

# 上传后端 JAR 包
scp Hbsz_IDC_System_end/Dockerfile user@your-server:~/hbsz/Hbsz_IDC_System_end/
scp Hbsz_IDC_System_end/target/Hbsz_IDC_System_end.jar user@your-server:~/hbsz/Hbsz_IDC_System_end/target/

# 上传前端 dist 和配置
scp Hbsz_IDC_System/Dockerfile user@your-server:~/hbsz/Hbsz_IDC_System/
scp Hbsz_IDC_System/nginx.conf user@your-server:~/hbsz/Hbsz_IDC_System/
scp -r Hbsz_IDC_System/dist/* user@your-server:~/hbsz/Hbsz_IDC_System/dist/
```

> **快捷方式**：可将整个项目打包上传，再在服务器上解压：
> 
> ```bash
> # 本地打包（排除不需要的文件）
> tar --exclude='node_modules' --exclude='target' --exclude='.git' \
>     --exclude='Hbsz_IDC_System_end/src' --exclude='Hbsz_IDC_System/src' \
>     -czf hbsz-deploy.tar.gz \
>     docker-compose.yml .env.docker docker/ \
>     Hbsz_IDC_System_end/Dockerfile Hbsz_IDC_System_end/.dockerignore \
>     Hbsz_IDC_System_end/target/Hbsz_IDC_System_end.jar \
>     Hbsz_IDC_System/Dockerfile Hbsz_IDC_System/nginx.conf Hbsz_IDC_System/.dockerignore \
>     Hbsz_IDC_System/dist/
> 
> # 上传并解压
> scp hbsz-deploy.tar.gz user@your-server:~/hbsz/
> ssh user@your-server "cd ~/hbsz && tar xzf hbsz-deploy.tar.gz"
> ```

---

## 三、配置与启动

### 3.1 修改环境变量

```bash
cd ~/hbsz
vi .env.docker
```

修改密码为安全值：

```env
# MySQL root 密码（Docker 初始化用，必须修改）
MYSQL_ROOT_PASSWORD=你的强密码_至少16位

# 应用数据库用户（后端服务使用，非 root，仅拥有 hbsz_idc_system 库权限）
DB_USERNAME=hbsz_app
DB_PASSWORD=你的强密码_至少16位

# Redis（必须修改）
REDIS_PASSWORD=你的强密码_至少16位

# JWT 密钥（必须修改，生成方式：openssl rand -base64 64）
JWT_SECRET=你的JWT密钥

# CORS 允许的域名（生产环境改为实际域名，多个用逗号分隔）
CORS_ALLOWED_ORIGINS=https://你的域名.com

# 端口（一般不需要改）
FRONTEND_PORT=80
BACKEND_PORT=8080
```

生成安全密码：

```bash
# MySQL root 密码
openssl rand -base64 24

# 应用数据库用户密码
openssl rand -base64 24

# Redis 密码
openssl rand -base64 24

# JWT 密钥（256位以上）
openssl rand -base64 64
```

### 3.2 启动服务

```bash
cd ~/hbsz

# 构建并启动（首次执行，约 2-3 分钟）
docker compose --env-file .env.docker up -d --build
```

### 3.3 查看启动状态

```bash
# 查看容器状态
docker compose ps

# 预期输出：
# NAME              IMAGE                    STATUS                   PORTS
# hbsz-frontend     hbsz-frontend             Up (healthy)             0.0.0.0:80->80/tcp
# hbsz-backend      hbsz-backend              Up (healthy)             0.0.0.0:8080->8080/tcp
# hbsz-mysql        mysql:8.0                Up (healthy)             3306/tcp
# hbsz-redis        redis:7-alpine           Up (healthy)             6379/tcp

# 查看后端启动日志（等待 "Started" 消息）
docker compose logs -f backend

# 看到以下日志表示成功：
# "Started HbszIdcSystemEndApplication in X.XXX seconds"
```

### 3.4 验证访问

```bash
# 前端页面
curl http://localhost/
# 返回 HTML 即正常

# 后端健康检查（通过 Nginx 代理）
curl http://localhost/api/actuator/health
# 预期：{"status":"UP"}

# 登录接口测试
curl -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 浏览器访问
# http://你的服务器IP/
```

---

## 四、内存分配说明

### 4.1 2GB 内存分配表

| 组件          | 内存限制      | 实际占用       | 说明                                    |
| ----------- | --------- | ---------- | ------------------------------------- |
| 系统 + Docker | -         | ~300MB     | 操作系统 + Docker 守护进程                    |
| MySQL       | 400MB     | ~200MB     | buffer_pool=128M + 连接 + 缓存            |
| Redis       | 80MB      | ~20MB      | maxmemory=48mb + 开销                   |
| 后端 JVM      | 288MB     | ~250MB     | 堆 96-160m + 元空间 + 代码缓存 + 线程栈 + 堆外（SerialGC） |
| 前端 Nginx    | 64MB      | ~15MB      | 静态资源服务                                |
| **合计**      | **832MB** | **~785MB** |                                       |
| 剩余可用        | -         | **~1.2GB** | 文件缓存 + 突发流量                           |
| Swap        | 2GB       | -          | 内存不足时兜底                               |

> **2G 服务器调优说明**：原配置 JVM 堆 128-256m + 容器限制 384M 在 2G 服务器上启动峰值易触发 OOM Killer。
> 现降为堆 96-160m + SerialGC + 限制元空间/代码缓存/线程栈，容器限制 288M，预留更多启动余量。

### 4.2 如需调整

编辑 `docker-compose.yml` 中各服务的 `memory` 限制：

```yaml
# 例如内存充足的服务器（4G+），可增大后端内存
deploy:
  resources:
    limits:
      memory: 512M    # 288M → 512M
```

同时修改 `JAVA_OPTS`：

```yaml
# 2G 服务器（当前配置）
JAVA_OPTS: "-Xms96m -Xmx160m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:ThreadStackSize=512k -XX:+UseSerialGC ..."

# 4G+ 服务器（可恢复 G1GC + 更大堆）
JAVA_OPTS: "-Xms256m -Xmx384m -XX:+UseG1GC ..."
```

---

## 五、常用运维命令

### 5.1 服务管理

```bash
# 启动
docker compose --env-file .env.docker up -d

# 停止（保留数据）
docker compose --env-file .env.docker down

# 重启单个服务
docker compose --env-file .env.docker restart backend

# 重新构建后端（代码更新后）
# 本地重新 mvn clean package -DskipTests
# 上传新的 JAR 包
scp Hbsz_IDC_System_end/target/Hbsz_IDC_System_end.jar user@server:~/hbsz/Hbsz_IDC_System_end/target/
# 服务器上重建
docker compose --env-file .env.docker up -d --build backend

# 重新构建前端（代码更新后）
# 本地重新 npm run build
# 上传新的 dist/
scp -r Hbsz_IDC_System/dist/* user@server:~/hbsz/Hbsz_IDC_System/dist/
# 服务器上重建
docker compose --env-file .env.docker up -d --build frontend
```

### 5.2 日志查看

```bash
# 实时日志
docker compose logs -f backend

# 最近100行
docker compose logs --tail 100 backend

# MySQL 日志
docker compose logs --tail 50 mysql
```

### 5.3 数据库操作

```bash
# 进入 MySQL
docker exec -it hbsz-mysql mysql -u root -p

# 备份数据库
docker exec hbsz-mysql mysqldump -u root -p密码 hbsz_idc_system > backup_$(date +%Y%m%d).sql

# 导入数据
docker exec -i hbsz-mysql mysql -u root -p密码 hbsz_idc_system < backup.sql
```

### 5.3.1 数据库迁移脚本（v1.2 升级必执行）

v1.2 版本新增数据库专用用户 `hbsz_app`（非 root），需确保 schema 初始化时已创建该用户并进行授权：

| 脚本 | 功能 | 说明 |
|------|------|------|
| 01-schema.sql（更新） | 新增 `hbsz_app` 应用用户创建 + 授权 | 已于 v1.2 schema 中新增，首次部署自动生效 |
| 02-seed-data.sql（更新） | 普通用户角色权限修正（移除 `customer:add/edit/remove`） | 已修正为只读用户 |

**升级注意**：

1. 若从 v1.1 升级到 v1.2，需手动执行以下 SQL 创建应用用户：

```bash
# 进入 MySQL
docker exec -it hbsz-mysql mysql -u root -p

# 执行以下 SQL
CREATE USER IF NOT EXISTS 'hbsz_app'@'%' IDENTIFIED BY '你的应用用户密码';
GRANT ALL PRIVILEGES ON `hbsz_idc_system`.* TO 'hbsz_app'@'%';
-- Flyway 需要读取 performance_schema 中的系统变量（v1.2.1 新增）
GRANT SELECT ON performance_schema.* TO 'hbsz_app'@'%';
-- MySQL 8.0+ 迁移脚本中使用 SET @变量 + PREPARE 动态 SQL 需要 SYSTEM_USER 权限
GRANT SYSTEM_USER ON *.* TO 'hbsz_app'@'%';
FLUSH PRIVILEGES;
```

2. 修改 `.env.docker`，新增 `DB_USERNAME` 和 `DB_PASSWORD` 变量（替代原来的 root 连接）。
3. 后端会自动使用 `hbsz_app` 用户连接，无需修改代码。

### 5.4 Redis 操作

```bash
# 进入 Redis
docker exec -it hbsz-redis redis-cli -a 密码

# 查看所有键
docker exec hbsz-redis redis-cli -a 密码 KEYS "hbsz:*"

# 清空缓存
docker exec hbsz-redis redis-cli -a 密码 FLUSHDB
```

---

## 六、数据持久化

### 6.1 数据卷

| 数据卷               | 说明            |
| ----------------- | ------------- |
| hbsz-mysql-data   | MySQL 数据文件    |
| hbsz-redis-data   | Redis AOF 持久化 |
| hbsz-uploads-data | 用户上传文件        |
| hbsz-logs-data    | 后端日志          |

### 6.2 自动备份

```bash
# 创建备份脚本
cat > ~/hbsz/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR=~/hbsz/backup
mkdir -p $BACKUP_DIR
docker exec hbsz-mysql mysqldump -u root -p$(grep MYSQL_ROOT_PASSWORD ~/hbsz/.env.docker | cut -d= -f2) hbsz_idc_system | gzip > $BACKUP_DIR/mysql_$(date +%Y%m%d_%H%M%S).sql.gz
find $BACKUP_DIR -name 'mysql_*.sql.gz' -mtime +7 -delete
echo "[$(date)] Backup completed"
EOF
chmod +x ~/hbsz/backup.sh

# 添加定时任务（每天凌晨2点备份）
crontab -e
# 添加：0 2 * * * ~/hbsz/backup.sh >> ~/hbsz/backup.log 2>&1
```

---

## 七、常见问题排查

### Q1: 后端启动失败，提示数据库连接超时

```bash
# MySQL 初始化需要时间，检查 MySQL 状态
docker compose ps mysql
# 状态应为 healthy

# 如果 MySQL 还在 starting，等待即可
# 后端会自动重试，如果 5 次失败后手动重启：
docker compose restart backend
```

### Q2: 容器频繁重启，内存不足（OOM）

```bash
# 查看是否 OOM Killed
docker inspect hbsz-backend --format='OOMKilled={{.State.OOMKilled}} ExitCode={{.State.ExitCode}}'
docker inspect hbsz-mysql --format='OOMKilled={{.State.OOMKilled}} ExitCode={{.State.ExitCode}}'

# 查看内存使用
docker stats --no-stream

# 如果 OOM，确认 Swap 已配置
free -h

# 2G 服务器当前已优化为最低内存配置：
#   JVM 堆 96-160m + SerialGC + 限制元空间/代码缓存/线程栈，容器限制 288M
# 如仍 OOM，可进一步降低：
#   MySQL: innodb-buffer-pool-size=96M
#   后端: JAVA_OPTS="-Xms64m -Xmx128m -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=32m ..."
#   后端容器内存限制: memory: 224M
# 若持续 OOM 建议升级到 4G 内存服务器
```

### Q3: 前端页面空白

```bash
# 检查 dist 是否正确上传
docker exec hbsz-frontend ls /usr/share/nginx/html/
# 应该有 index.html 和 assets/ 目录

# 如果为空，重新上传 dist 并重建
docker compose --env-file .env.docker up -d --build frontend
```

### Q4: API 返回 502

```bash
# 后端是否正常运行
docker compose ps backend
# 状态应为 healthy

# 查看后端日志
docker compose logs --tail 30 backend

# 手动测试后端
docker exec hbsz-frontend wget -qO- http://backend:8080/api/actuator/health
```

### Q5: WebSocket 连接失败（通知不推送）

```bash
# 检查 Nginx 配置
docker exec hbsz-frontend cat /etc/nginx/conf.d/default.conf | grep -A5 "location /ws"
# 应有 proxy_set_header Upgrade 和 Connection
```

### Q6: 端口 80 被占用

```bash
# 修改 .env.docker
FRONTEND_PORT=8081

# 重启
docker compose --env-file .env.docker up -d
```

### Q7: 磁盘空间不足

```bash
# 清理 Docker 无用资源
docker system prune -a

# 清理旧日志
docker exec hbsz-backend sh -c "find /app/logs -name '*.log' -mtime +7 -delete"

# 查看磁盘
df -h
```

### Q8: 需要从外部连接 MySQL

```bash
# 编辑 docker-compose.yml，取消 MySQL 端口注释
ports:
  - "3306:3306"

# 重启
docker compose --env-file .env.docker up -d mysql

# 连接
mysql -h 服务器IP -P 3306 -u root -p
```

---

## 八、更新部署流程

### 8.1 更新后端代码

```bash
# 本地
cd Hbsz_IDC_System_end
git pull
mvn clean package -DskipTests

# 上传 JAR
scp target/Hbsz_IDC_System_end.jar user@server:~/hbsz/Hbsz_IDC_System_end/target/

# 服务器上重建后端
docker compose --env-file .env.docker up -d --build backend
```

### 8.2 更新前端代码

```bash
# 本地
cd Hbsz_IDC_System
git pull
npm install
npm run build

# 上传 dist
scp -r dist/* user@server:~/hbsz/Hbsz_IDC_System/dist/

# 服务器上重建前端
docker compose --env-file .env.docker up -d --build frontend
```

---

## 九、安全建议

1. **修改所有默认密码**：`.env.docker` 中的 MYSQL_ROOT_PASSWORD、DB_PASSWORD、REDIS_PASSWORD、JWT_SECRET 全部修改
2. **使用专用数据库用户**：后端服务使用 `hbsz_app` 用户（非 root），仅拥有 `hbsz_idc_system` 库的 CRUD 权限（v1.2 已内置）
3. **配置 CORS 白名单**：生产环境将 `CORS_ALLOWED_ORIGINS` 设为实际域名，切勿使用 `*`
4. **配置防火墙**：仅开放 80（前端）和 22（SSH）端口
   
   ```bash
   sudo ufw default deny incoming
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw enable
   ```
5. **MySQL/Redis 不对外暴露**：docker-compose.yml 中已注释端口映射
6. **HTTPS**：生产环境建议使用 Caddy 或 certbot 配置 SSL 证书
7. **Nginx 安全头**（v1.2 已内置）：CSP、X-Frame-Options、X-Content-Type-Options、Referrer-Policy
8. **定期备份**：配置 crontab 自动备份 MySQL
9. **系统更新**：定期 `sudo apt update && sudo apt upgrade`
10. **微信小程序上线注意**：生产环境需将 `manifest.json` 中 `urlCheck` 设为 `true`，并在微信后台配置合法域名

---

## 十、更新日志

### v1.3 (2026-07-21) — 部署修复：mysql-grants + tmpfs + Nginx ^~ + 小程序图片路径

**Docker 编排修复**：
- 新增 `mysql-grants` 一次性容器：在 MySQL 健康检查通过后用 root 执行 `init-grants.sql`，为 `hbsz_app` 授予 DDL + performance_schema + SYSTEM_USER 权限。后端依赖其 `service_completed_successfully` 后才启动。**解决 MySQL 数据卷复用时 `docker-entrypoint-initdb.d` 脚本不重新执行的问题**
- 新增 `docker/mysql/init-grants.sql` 独立权限初始化脚本，与 `01-schema.sql` 分离
- 后端 tmpfs 新增：`/var/cache/fontconfig`、`/app/.cache/fontconfig`、`/app/.fontconfig`（解决验证码生成时 Fontconfig error）、`/app/exports`（解决日报导出 FileNotFoundException）
- 前端内存限制从 32M 调整为 64M

**Nginx 路由修复**：
- `location /api/` → `location ^~ /api/`，`location /ws/` → `location ^~ /ws/`
- 根因：`~* \.(png)$` 正则匹配优先级高于普通前缀匹配，导致 `/api/files/preview/xxx.png` 被静态资源规则截获返回 404
- `^~` 前缀匹配优先级高于正则，确保所有 `/api/` 路径都代理到后端

**后端代码修复**：
- `DailyReportServiceImpl.java`：`mkdirs()` 失败时抛 `IllegalStateException`，避免后续模糊的 `FileNotFoundException`

**小程序图片路径适配**：
- `pages/progress/review.vue`：`parseImages()` 将后端返回的相对路径拼接为 `API_BASE_URL + '/files/preview/'` 完整 URL
- `pages/progress/report.vue`：上传成功后 `previewUrl` 拼接完整 URL；`:src` 改用 `previewUrl`
- 根因：后端返回相对路径（如 `2026/07/25/xxx.png`），小程序 `<image>` 需完整 URL 才能加载

**v1.3 升级步骤**：
```bash
# 无需删卷，直接重新部署即可，mysql-grants 会自动执行权限初始化
docker compose --env-file .env.docker down
docker compose --env-file .env.docker up -d --build
```

### v1.2.1 (2026-07-21) — Flyway 权限修复 + 小程序适配

**配置修复**：
- `01-schema.sql`：`hbsz_app` 用户新增 `GRANT SELECT ON performance_schema.*` 权限，解决 Flyway 启动时 `SELECT command denied to user 'hbsz_app' for table 'user_variables_by_thread'` 错误

**小程序适配**（8个文件）：
- `pages/login/login.vue`：新增验证码功能（captchaId + captcha 字段），适配后端登录验证码强制校验
- `utils/config.js`：API_BASE_URL 从 HTTP+IP 改为 HTTPS 域名
- `utils/request.js`：Token 刷新后同步更新 userStore 响应式状态
- `pages/project/list.vue`、`pages/customer/list.vue`、`pages/document/list.vue`：分页 page++ 移到请求成功回调内执行，防止失败时页码错位
- `pages/document/list.vue`、`pages/document/preview.vue`：下载操作添加 showLoading/hideLoading
- `pages/*/`：4处 Promise.all 改为 Promise.allSettled，防止单请求失败阻塞其他模块
- `manifest.json`：urlCheck 改为 true；Android 权限精简为仅保留网络权限
- `App.vue`：新增 uni.onError 全局异常捕获

**升级注意**：v1.3 已通过 `mysql-grants` 容器自动执行权限初始化，无需手动执行 SQL。若仍使用 v1.2.1 部署，需手动执行：
```sql
-- 扩展 hbsz_app 权限以支持 Flyway DDL + 动态 SQL
GRANT ALL PRIVILEGES ON `hbsz_idc_system`.* TO 'hbsz_app'@'%';
GRANT SELECT ON performance_schema.* TO 'hbsz_app'@'%';
GRANT SYSTEM_USER ON *.* TO 'hbsz_app'@'%';
FLUSH PRIVILEGES;
```

### v1.2 (2026-07-21) — 代码审核修复 + 安全加固

**后端修复**：
- 修复 `ProjectServiceImpl` / `UserManageServiceImpl` 导入事务失效（self-invocation → 自注入代理）
- 修复 `DocumentController.addVersion()` 文件 URL 丢失
- 修复 `DocumentServiceImpl.review()` 并发审核无保护（新增 CAS 原子更新）
- 修复 `UserManageServiceImpl.updateStatus()` 缺少超级管理员/当前用户保护
- 修复 `AuthServiceImpl` 账号 null 状态漏洞
- 修复 `AuthController.info()` / `ProjectServiceImpl.create()` userId 为 null 的 NPE 风险
- 修复 `ProjectController.list()` 缺少 `@Valid` 参数校验

**前端修复**：
- 修复 `ProjectList.vue` / `UserManagement.vue` / `CustomerManagement.vue` XSS 风险（dangerouslyUseHTMLString 加 HTML 转义）
- 修复 `cancelToken.js` withCancel 竞态条件
- 修复 `permission.js` hasPermission 对 falsy 值放行
- 修复 `UserManagement.vue` onMounted + onActivated 重复请求
- 修复 `Login.vue` loginFormRef null guard
- 修复 `GanttChart.vue` 深度监听性能（改为 watch length）
- 修复 `websocket.js` connect() 连接泄漏
- 注册 `v-submit` 防抖指令

**配置/安全加固**：
- `docker-compose.yml`：CORS 从 `*` 改为环境变量控制；DB 连接从 root 改为专用用户 `hbsz_app`；启用 SSL
- `.env.docker`：新增 `DB_USERNAME`、`DB_PASSWORD`、`CORS_ALLOWED_ORIGINS` 变量
- `01-schema.sql`：新增 `hbsz_app` 用户创建 + 授权；移除所有视图/函数 `DEFINER` 硬编码
- `02-seed-data.sql`：普通用户角色移除 `customer:add/edit/remove` 写权限
- `nginx.conf`：移除已弃用 `X-XSS-Protection`，新增 CSP + Referrer-Policy 安全头

### v1.1 (2026-07-15) — 新增工作量管理机制

- 新增实际开工/结束日期、文档审核功能
- 新增任务工作量字段 + 工作量单位字典
- 新增 Flyway 迁移脚本 V20260714、V20260715
