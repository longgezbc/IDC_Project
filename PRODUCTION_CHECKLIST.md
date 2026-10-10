# 生产环境部署检查清单

> 适用：2核2G 轻量云服务器 | Docker Compose 部署方式

---

## 一、服务器环境（部署前）

### 1.1 系统基础

- [ ] 操作系统已更新至最新补丁：`sudo apt update && sudo apt upgrade`
- [ ] 时区设置为 Asia/Shanghai：`sudo timedatectl set-timezone Asia/Shanghai`
- [ ] Swap 已配置 2GB（2G 内存服务器必须）：
  ```bash
  free -h  # Swap 行应显示 2.0Gi
  cat /etc/fstab | grep swap  # 应有开机自动挂载
  cat /etc/sysctl.conf | grep swappiness  # 应为 10
  ```
- [ ] 文件描述符限制已提高：
  ```bash
  # /etc/security/limits.conf 添加：
  * soft nofile 65535
  * hard nofile 65535
  ```
- [ ] 磁盘空间充足（≥15GB 可用）：`df -h`

### 1.2 Docker 环境

- [ ] Docker Engine 24.0+ 已安装：`docker --version`
- [ ] Docker Compose v2.20+ 已安装：`docker compose version`
- [ ] Docker 开机自启：`sudo systemctl is-enabled docker` → `enabled`
- [ ] Docker 日志限制已配置（防止磁盘撑满）：
  ```bash
  # /etc/docker/daemon.json
  {
    "log-driver": "json-file",
    "log-opts": { "max-size": "10m", "max-file": "3" }
  }
  ```
  ```bash
  sudo systemctl restart docker
  ```

---

## 二、安全配置

### 2.1 密码与密钥

- [ ] `.env.docker` 中 MySQL 密码已修改（非默认值）：
  ```bash
  # 确认不是 hbsz_mysql_2026
  grep MYSQL_ROOT_PASSWORD .env.docker
  ```
- [ ] `.env.docker` 中 Redis 密码已修改（非默认值）：
  ```bash
  # 确认不是 hbsz_redis_2026
  grep REDIS_PASSWORD .env.docker
  ```
- [ ] `.env.docker` 中 JWT 密钥已替换为随机值：
  ```bash
  # 确认长度 ≥48 字符且非默认值
  grep JWT_SECRET .env.docker
  # 生成方式：openssl rand -base64 48
  ```
- [ ] 管理员默认密码 admin123 已修改（部署后首次登录即改）

### 2.2 防火墙

- [ ] UFW 已启用，仅开放必要端口：
  ```bash
  sudo ufw status verbose
  # 预期规则：
  # 22/tcp    ALLOW IN    （SSH）
  # 80/tcp    ALLOW IN    （前端 HTTP）
  # 8080/tcp  DENY        （后端不对外，仅容器内访问）
  # 3306/tcp  DENY        （MySQL 不对外）
  # 6379/tcp  DENY        （Redis 不对外）
  ```
- [ ] SSH 端口已修改为非 22（可选但推荐）
- [ ] SSH 禁止 root 密码登录，仅允许密钥：
  ```bash
  # /etc/ssh/sshd_config
  PermitRootLogin no
  PasswordAuthentication no
  PubkeyAuthentication yes
  ```

### 2.3 容器安全

- [ ] MySQL/Redis 端口未映射到宿主机（docker-compose.yml 中 ports 被注释）：
  ```bash
  grep -A2 "ports:" docker-compose.yml | grep -E "3306|6379"
  # 预期：无输出（端口未对外暴露）
  ```
- [ ] 仅前端 80 端口对外暴露：
  ```bash
  docker compose ps --format "table {{.Name}}\t{{.Ports}}"
  # 预期：只有 frontend 映射 0.0.0.0:80->80
  ```
- [ ] 容器以非 root 用户运行（后端 JRE 镜像默认非 root，无需额外配置）
- [ ] Docker 镜像来源可信（mysql:8.0 / redis:7-alpine / eclipse-temurin:17-jre-alpine 官方镜像）

### 2.4 应用层安全

- [ ] CORS 配置已收紧（生产环境不应为 `*`）：
  ```yaml
  # docker-compose.yml backend 环境变量
  CORS_ALLOWED_ORIGINS: "https://your-domain.com"
  ```
- [ ] JWT 过期时间合理（access 30分钟，refresh 7天）：
  ```bash
  grep JWT_EXPIRATION .env.docker  # 1800000 (30min)
  grep JWT_REFRESH_EXPIRATION .env.docker  # 604800000 (7day)
  ```
- [ ] 登录限流已生效（5次/60秒，超限锁定）
- [ ] 幂等性控制已生效（防止重复提交）
- [ ] Nginx 安全头已配置：
  ```bash
  docker exec hbsz-frontend cat /etc/nginx/conf.d/default.conf | grep "X-Frame-Options"
  # 预期：X-Frame-Options: SAMEORIGIN
  ```
- [ ] Nginx 版本号已隐藏：
  ```bash
  docker exec hbsz-frontend cat /etc/nginx/conf.d/default.conf | grep "server_tokens"
  # 预期：server_tokens off
  ```

### 2.5 HTTPS（强烈推荐）

- [ ] 域名已解析到服务器 IP
- [ ] SSL 证书已配置（以下任选其一）：
  - **方案 A：Caddy 反向代理**（自动申请 Let's Encrypt 证书）
  - **方案 B：Nginx + Certbot**：
    ```bash
    sudo apt install certbot python3-certbot-nginx
    sudo certbot --nginx -d your-domain.com
    ```
  - **方案 C：云服务商免费证书**（腾讯云/阿里云 SSL）
- [ ] HTTP 自动跳转 HTTPS

---

## 三、性能优化

### 3.1 MySQL 优化

- [ ] innodb_buffer_pool_size 合理（128M / 2GB 服务器）：
  ```bash
  docker exec hbsz-mysql mysql -u root -p -e "SHOW VARIABLES LIKE 'innodb_buffer_pool_size'"
  # 预期：134217728 (128MB)
  ```
- [ ] max_connections 适中（100 连接 / 2GB 服务器）：
  ```bash
  docker exec hbsz-mysql mysql -u root -p -e "SHOW VARIABLES LIKE 'max_connections'"
  # 预期：100
  ```
- [ ] 慢查询日志已开启：
  ```bash
  docker exec hbsz-mysql mysql -u root -p -e "SHOW VARIABLES LIKE 'slow_query_log'"
  # 预期：ON
  ```
- [ ] skip-name-resolve 已配置（跳过 DNS 反查加速连接）：
  ```bash
  docker exec hbsz-mysql mysql -u root -p -e "SHOW VARIABLES LIKE 'skip_name_resolve'"
  # 预期：ON
  ```
- [ ] 字符集统一为 utf8mb4：
  ```bash
  docker exec hbsz-mysql mysql -u root -p -e "SHOW VARIABLES LIKE 'character_set_server'"
  # 预期：utf8mb4
  ```

### 3.2 Redis 优化

- [ ] maxmemory 已限制（48MB / 2GB 服务器）：
  ```bash
  docker exec hbsz-redis redis-cli -a 密码 CONFIG GET maxmemory
  # 预期：50331648 (48MB)
  ```
- [ ] 淘汰策略为 allkeys-lru：
  ```bash
  docker exec hbsz-redis redis-cli -a 密码 CONFIG GET maxmemory-policy
  # 预期：allkeys-lru
  ```
- [ ] AOF 持久化已开启：
  ```bash
  docker exec hbsz-redis redis-cli -a 密码 CONFIG GET appendonly
  # 预期：yes
  ```

### 3.3 后端 JVM 优化

- [ ] JVM 堆内存配置合理（128-256m / 2GB 服务器）：
  ```bash
  docker exec hbsz-backend java -XX:+PrintFlagsFinal -version 2>&1 | grep -E "InitialHeapSize|MaxHeapSize"
  # 预期：InitialHeapSize ≈ 128MB, MaxHeapSize ≈ 256MB
  ```
- [ ] GC 策略已优化（UseGCTimeRatio=4）：
  ```bash
  docker exec hbsz-backend env | grep JAVA_OPTS
  # 预期包含：-XX:+UseGCTimeRatio=4
  ```
- [ ] OOM 时自动生成堆转储：
  ```bash
  docker exec hbsz-backend env | grep JAVA_OPTS
  # 预期包含：-XX:+HeapDumpOnOutOfMemoryError
  ```

### 3.4 Nginx 优化

- [ ] Gzip 压缩已开启：
  ```bash
  docker exec hbsz-frontend nginx -T 2>/dev/null | grep gzip
  # 预期：gzip on
  ```
- [ ] 静态资源缓存策略已配置（1年长期缓存）：
  ```bash
  docker exec hbsz-frontend nginx -T 2>/dev/null | grep "expires"
  # 预期：expires 1y
  ```
- [ ] index.html 不缓存（保证发布即时生效）：
  ```bash
  docker exec hbsz-frontend nginx -T 2>/dev/null | grep "no-cache"
  # 预期：Cache-Control: no-cache, no-store, must-revalidate
  ```
- [ ] 文件上传大小限制已配置（100MB）：
  ```bash
  docker exec hbsz-frontend nginx -T 2>/dev/null | grep "client_max_body_size"
  # 预期：100m
  ```
- [ ] WebSocket 代理已配置（通知实时推送）：
  ```bash
  docker exec hbsz-frontend nginx -T 2>/dev/null | grep "Upgrade"
  # 预期：proxy_set_header Upgrade $http_upgrade
  ```

### 3.5 资源限制验证

- [ ] 各容器内存限制已生效：
  ```bash
  docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"
  # 预期各容器均未超出限制
  ```
- [ ] 宿主机内存使用率正常（< 80%）：
  ```bash
  free -h | awk '/Mem/{printf "%.0f%", $3/$2*100}'
  # 预期：< 80%
  ```
- [ ] 宿主机 CPU 负载正常：
  ```bash
  uptime
  # 预期：load average 接近 CPU 核数（2.0 以下）
  ```

---

## 四、健康检查与可用性

### 4.1 容器健康状态

- [ ] 所有容器状态为 healthy：
  ```bash
  docker compose ps
  # 预期 4 个容器全部 Up (healthy)
  ```
- [ ] 后端 actuator 健康检查正常：
  ```bash
  curl -s http://localhost/api/actuator/health
  # 预期：{"status":"UP"}
  ```

### 4.2 自动重启策略

- [ ] restart 策略为 unless-stopped：
  ```bash
  docker inspect hbsz-backend --format='{{.HostConfig.RestartPolicy.Name}}'
  # 预期：unless-stopped
  ```

### 4.3 日志检查

- [ ] 后端启动日志无 ERROR：
  ```bash
  docker compose logs backend | grep -i "error\|exception" | head -20
  # 预期：无关键错误
  ```
- [ ] MySQL 无异常错误：
  ```bash
  docker compose logs mysql | grep -i "error" | head -10
  # 预期：无关键错误
  ```
- [ ] Nginx 无 502/504 错误：
  ```bash
  docker compose logs frontend | grep -E "50[24]"
  # 预期：无输出
  ```

---

## 五、功能验证

### 5.1 核心业务流程

- [ ] 登录功能正常：
  ```bash
  curl -s -X POST http://localhost/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"新密码"}' | python3 -m json.tool
  # 预期：返回 token 和 userInfo
  ```
- [ ] 前端页面加载正常：浏览器访问 `http://服务器IP/`
- [ ] API 代理正常：`curl http://localhost/api/actuator/health`
- [ ] WebSocket 连接正常：浏览器控制台无 ws 连接错误
- [ ] 文件上传功能正常：测试上传一个文件
- [ ] 站内通知推送正常：发送一条测试通知

### 5.2 工作量管理功能验证（v1.1 新增）

- [ ] 工作量字段已添加到 biz_task 表：
  ```bash
  docker exec hbsz-mysql mysql -u root -p hbsz_idc_system -e \
    "SHOW COLUMNS FROM biz_task LIKE 'planned_workload'; SHOW COLUMNS FROM biz_task LIKE 'actual_workload'; SHOW COLUMNS FROM biz_task LIKE 'workload_unit'"
  # 预期：返回 3 行
  ```
- [ ] 工作量单位字典数据已初始化：
  ```bash
  docker exec hbsz-mysql mysql -u root -p hbsz_idc_system -e \
    "SELECT COUNT(*) FROM sys_dict_data WHERE dict_type='workload_unit'"
  # 预期：8（条/个/台/芯/端口/机架/米/人天）
  ```
- [ ] 任务编辑可设定计划工作量基准（PM 角色）
- [ ] 工作台展示工作量统计卡片（admin/PM 角色）
- [ ] 风险看板展示工作量偏差预警（落后超过20%的任务）
- [ ] 日报导出 Excel 包含工作量统计列（计划/已完成/完成率）
- [ ] 工作量模板导入后任务自动设置 plannedWorkload 基准

### 5.3 数据一致性

- [ ] 数据库表结构完整：
  ```bash
  docker exec hbsz-mysql mysql -u root -p -e \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='hbsz_idc_system'"
  # 预期：28（表）+ 6（视图）= 34
  ```
- [ ] 种子数据完整：
  ```bash
  docker exec hbsz-mysql mysql -u root -p hbsz_idc_system -e \
    "SELECT (SELECT COUNT(*) FROM sys_department) AS depts, (SELECT COUNT(*) FROM sys_role) AS roles, (SELECT COUNT(*) FROM sys_permission) AS perms, (SELECT COUNT(*) FROM sys_user) AS users"
  # 预期：depts=9, roles=4, perms=57, users=1
  ```
- [ ] Redis 缓存键命名正确：
  ```bash
  docker exec hbsz-redis redis-cli -a 密码 KEYS "hbsz:*"
  # 预期：所有键以 hbsz: 开头
  ```

---

## 六、备份与恢复

- [ ] MySQL 自动备份脚本已配置：
  ```bash
  crontab -l | grep backup
  # 预期：0 2 * * * ~/hbsz/backup.sh
  ```
- [ ] 备份文件保留策略已配置（保留 7 天）
- [ ] 已验证备份文件可正常恢复：
  ```bash
  # 测试恢复流程（在测试环境验证）
  docker exec -i hbsz-mysql mysql -u root -p hbsz_idc_system < backup.sql
  ```
- [ ] 数据卷已映射（确保容器删除后数据不丢失）：
  ```bash
  docker volume ls | grep hbsz
  # 预期：hbsz-mysql-data, hbsz-redis-data, hbsz-uploads-data, hbsz-logs-data
  ```

---

## 七、监控（可选但推荐）

### 7.1 基础监控

- [ ] 磁盘空间监控（> 80% 报警）：
  ```bash
  # crontab
  */5 * * * * df -h | awk '$5+0>80{print "DISK WARNING: "$0}' >> /var/log/disk-alert.log
  ```
- [ ] 内存使用监控（> 85% 报警）：
  ```bash
  */5 * * * * free -h | awk '/Mem/{if($3/$2>0.85) print "MEM WARNING"}' >> /var/log/mem-alert.log
  ```
- [ ] 容器状态监控（异常退出报警）：
  ```bash
  */5 * * * * docker compose ps | grep -v "healthy\|NAME" && echo "CONTAINER DOWN" >> /var/log/container-alert.log
  ```

### 7.2 日志轮转

- [ ] 后端日志自动清理（保留 7 天）：
  ```bash
  docker exec hbsz-backend crontab -l 2>/dev/null
  # 或在宿主机配置
  0 3 * * * docker exec hbsz-backend find /app/logs -name '*.log' -mtime +7 -delete
  ```
- [ ] Docker 日志限制已配置（见 1.2 节）

---

## 八、部署后检查总结

执行以下一键检查脚本：

```bash
#!/bin/bash
echo "========== 生产环境检查 =========="

echo "--- 1. 容器状态 ---"
docker compose ps --format "table {{.Name}}\t{{.Status}}"

echo "--- 2. 内存使用 ---"
free -h

echo "--- 3. 磁盘使用 ---"
df -h /

echo "--- 4. 后端健康 ---"
curl -s http://localhost/api/actuator/health

echo ""
echo "--- 5. Redis 连接 ---"
docker exec hbsz-redis redis-cli -a $(grep REDIS_PASSWORD .env.docker | cut -d= -f2) ping

echo ""
echo "--- 6. 数据库表数 ---"
docker exec hbsz-mysql mysql -u root -p$(grep MYSQL_ROOT_PASSWORD .env.docker | cut -d= -f2) -e \
  "SELECT COUNT(*) AS table_count FROM information_schema.TABLES WHERE TABLE_SCHEMA='hbsz_idc_system'"

echo "--- 7. 容器内存占用 ---"
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"

echo "========== 检查完成 =========="
```

将上述脚本保存为 `~/hbsz/check.sh` 并执行 `chmod +x check.sh && ./check.sh`，所有项目预期输出正常即部署成功。
