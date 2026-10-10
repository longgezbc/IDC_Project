# 部署后验证检查清单

> 适用场景：服务器执行 `docker compose up -d --build` 后，按顺序执行以下 5 步验证系统是否正常运行。
> 前置条件：`.env.docker` 已配置 / 防火墙放行 80 端口 / MySQL 数据卷状态明确（全新或保留）。
> 预计耗时：5-10 分钟（含后端 120s 启动等待）

---

## ✅ 步骤 1：容器健康状态验证（30 秒）

**目标**：确认 MySQL / Redis / 后端 / 前端 4 个容器全部 Up 且 Healthy。

```bash
# 查看容器状态（需等 2 分钟后端 start_period 结束）
docker compose ps

# 查看资源占用（总内存应 ≤ 900MB）
docker stats --no-stream
```

**预期输出**：
- 4 个容器 STATUS 全部为 `Up (healthy)`
- MySQL / Redis 立即 healthy；后端需等待 120s start_period 后才 healthy；前端依赖后端 healthy

**失败处理**：

```bash
# 查看具体容器日志（替换 <service> 为 mysql/redis/backend/frontend）
docker compose logs --tail=100 <service>

# 常见问题：
# - MySQL 启动慢 → 检查磁盘 IO，必要时增加 healthcheck start_period
# - 后端 healthy 失败 → 检查 DB_URL / REDIS_HOST 环境变量是否正确解析
# - 前端 502 → 后端未 healthy，检查 depends_on: condition: service_healthy
```

---

## ✅ 步骤 2：Flyway 数据库迁移验证（含 V20260720 复合索引）

**目标**：确认 V20260720 迁移脚本成功执行，3 个复合索引已创建。

```bash
# 1. 查看 Flyway 迁移历史（应有 7 条记录，V20260720 success=1）
docker compose exec mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-hbsz_mysql_2026} hbsz_idc_system \
  -e "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank;"

# 2. 验证 3 个复合索引已存在（应返回 3 行）
docker compose exec mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-hbsz_mysql_2026} hbsz_idc_system \
  -e "SELECT table_name, index_name FROM information_schema.statistics \
      WHERE table_schema='hbsz_idc_system' \
      AND index_name IN ('idx_project_review_time','idx_project_status_category','idx_user_created') \
      GROUP BY table_name, index_name;"
```

**预期输出**：
- Flyway 历史表中有 7 条记录（V20260704×2 / V20260705 / V20260712 / V20260714 / V20260715 / V20260720），`success=1`
- 索引查询返回 3 行：
  - `biz_task_progress_log / idx_project_review_time`
  - `biz_document / idx_project_status_category`
  - `sys_operation_log / idx_user_created`

**失败处理**：
- 全新部署（空数据卷）：Docker init 的 `01-schema.sql` 已含这 3 个索引，Flyway V20260720 应幂等跳过 → 检查 schema.sql 是否同步
- 升级部署（保留数据卷）：检查后端日志 `docker compose logs backend | grep -i flyway`
- Flyway 校验失败（checksum 不匹配）：确认 `validate-on-migrate: false` 配置生效

---

## ✅ 步骤 3：登录与认证验证（含验证码强制校验）

**目标**：确认登录验证码机制和 JWT 颁发正常，`/system/config` 接口可匿名访问。

```bash
# 1. 获取验证码（应返回 captchaId + base64 图片）
curl -s http://localhost:8080/api/auth/captcha | head -c 300

# 2. 验证 /system/config 接口跳过 JWT 拦截（应返回 200）
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/system/config

# 3. 测试登录失败场景：缺验证码（应返回 400）
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | head -c 300
```

**预期输出**：
- `/api/auth/captcha` 返回 `{"code":200,"data":{"captchaId":"xxx","captcha":"data:image/png;base64,..."}}`
- `/api/system/config` 返回 `200`（无需 token，JwtAuthInterceptor 已排除）
- 缺验证码登录返回 `400` + `{"message":"验证码不能为空"}`（@NotBlank 校验生效）

**浏览器端验证**（更直观）：
- 打开 `http://<server-ip>/` → 显示登录页
- 输入 admin / admin123 + 错误验证码 → 提示「验证码错误」，**账号锁定计数器不增加**（防 DoS）
- 输入正确验证码登录 → 跳转工作台，localStorage 中有 `token` + `refreshToken`

**失败处理**：
- 验证码加载失败 → 检查后端 Redis 连接（`docker compose exec redis redis-cli -a <密码> ping`）
- `/system/config` 返回 401 → 检查 `JwtAuthInterceptor` 配置是否排除该路径
- 登录返回 500 → 查看后端日志 `docker compose logs --tail=200 backend`

---

## ✅ 步骤 4：核心业务接口验证

**目标**：确认关键 API 端点返回正常数据（需先登录获取 token）。

```bash
# 从浏览器 localStorage 复制 token（或从登录响应中提取 data.token）
TOKEN="Bearer eyJxxx..."

# 1. 工作台统计接口（需 statistics:query 权限绕过数据隔离）
curl -s -H "Authorization: $TOKEN" \
  http://localhost:8080/api/dashboard/statistics | head -c 400

# 2. 项目列表（分页 + camelCase 字段）
curl -s -H "Authorization: $TOKEN" \
  "http://localhost:8080/api/projects?pageNum=1&pageSize=10" | head -c 400

# 3. 权限树（PermissionManagement 页面数据源，新迁移到 CrudTable 树形表格）
curl -s -H "Authorization: $TOKEN" \
  http://localhost:8080/api/system/permissions/tree | head -c 400

# 4. 进度总览（ProgressManagement 页面数据源，新迁移到 CrudTable 无分页）
curl -s -H "Authorization: $TOKEN" \
  http://localhost:8080/api/progress | head -c 400
```

**预期输出**：
- `/dashboard/statistics` 返回 `200` + 项目/任务统计 JSON（camelCase 字段如 `projectCount`、`taskCount`）
- `/projects` 返回 `200` + `{ "code":200, "data": { "records":[...], "total":N } }`
- `/permissions/tree` 返回 `200` + 权限树 JSON（含 `children` 嵌套数组）
- `/progress` 返回 `200` + 项目进度列表

**失败处理**：
- `401 Unauthorized` → token 过期，调用 `/api/auth/refresh` 刷新
- `403 Forbidden` → 权限缓存未刷新（Redis），重启后端或调用 `/api/auth/refresh`
- `500 Internal Server Error` → 查看后端日志 `docker compose logs --tail=200 backend`，常见原因：
  - 字段名不匹配（snake_case 未转 camelCase）
  - 缺失列（数据库未执行迁移）
  - Redis 缓存反序列化失败（升级后老版本数据格式不兼容）

---

## ✅ 步骤 5：前端列表页验证（16 个 CrudTable 迁移页）

**目标**：确认所有迁移到 CrudTable 的列表页正常渲染、分页、搜索、弹窗功能正常。

### 重点验证页面（本次新增 / 复杂场景）

| 页面 | URL | 验证要点 |
|------|-----|---------|
| 权限管理 | `/system/permissions` | **树形表格**展开/折叠（CrudTable 新增 treeProps / rowKey / defaultExpandAll） |
| 进度管理总览 | `/progress` | **无分页**表格 + 状态筛选 radio（`:show-pagination="false"`） |
| 文档管理 | `/documents` | **双视图**切换（列表 ↔ 网格）+ 分页参数共享 |
| 项目列表 | `/projects` | **双视图**切换（表格 ↔ 卡片）+ Tab 切换 |
| 用户管理 | `/system/users` | 标准 CrudTable + useCrudList |
| 角色管理 | `/system/roles` | 标准 CrudTable + 权限分配弹窗 |
| 客户管理 | `/customers` | 标准 CrudTable |
| 待审核填报 | `/review/pending` | 标准 CrudTable |
| 我的任务 | `/my/tasks` | 标准 CrudTable |
| 我的填报历史 | `/my/submissions` | 标准 CrudTable |
| 风险预警看板 | `/my/risk-board` | 非 CrudTable，本次新增页面 |

### 通用验证清单（每页都需要走一遍）

- [ ] 表格数据加载（骨架屏 → 数据行正常显示）
- [ ] 分页器翻页（page-size 切换 10/20/50/100、跳转到指定页）
- [ ] 搜索表单（查询条件生效、重置按钮清空）
- [ ] 新增/编辑弹窗（表单提交成功、字段校验生效）
- [ ] 删除操作（二次确认弹窗 → 删除成功后列表刷新）
- [ ] **权限管理特殊**：树形表格展开/折叠、子节点缩进显示
- [ ] **文档/项目特殊**：列表 ↔ 网格/卡片视图切换，分页参数同步

### 浏览器开发者工具检查

- F12 Network 面板：无 4xx/5xx 错误请求
- F12 Console 面板：无红色 JS 错误（黄色警告可忽略）
- F12 Application → Local Storage：token / refreshToken / pinia 持久化数据存在

**失败处理**：
- 表格空白 → Network 面板查看 API 请求是否成功
- 骨架屏一直转 → 后端 API 500，查看后端日志
- 弹窗不显示 → Console 检查是否有 JS 错误（如 Element Plus icon 未注册）
- 视图切换异常 → 检查 `dist/assets/` 中是否包含最新打包的 CrudTable 组件

---

## 🎉 全部通过后

- [ ] 修改 admin 默认密码（admin123 → 强密码）：登录后 → 系统设置 → 修改密码
- [ ] 配置邮件通知（可选）：系统配置 → 通知配置
- [ ] 长期监控资源使用：`docker stats` 定期观察内存/CPU 占用趋势
- [ ] 备份 MySQL 数据卷：`docker run --rm -v hbsz-mysql-data:/data -v $(pwd):/backup alpine tar czf /backup/mysql-backup-$(date +%Y%m%d).tar.gz /data`

---

## 📝 备注

| 项目 | 说明 |
|------|------|
| 后端启动等待 | healthy 检查 `start_period=120s`，部署后请耐心等待 2 分钟再执行步骤 1 |
| MySQL 数据卷 | `hbsz-mysql-data` 保留则不重新初始化；清空需 `docker compose down -v`（**会丢失全部数据**） |
| 前端 dist | 已构建完成，含 16 个 CrudTable 迁移页 + 新增的树形表格/无分页/双视图模式支持 |
| 后端 JAR | 已构建完成，含 Flyway V20260720 迁移脚本（幂等，重复执行不报错） |
| 默认账号 | `admin / admin123`（BCrypt 加密），**首次登录后请立即修改密码** |
| 端口暴露 | 前端 80（宿主机）→ 8080（容器内）；后端 8080（仅内部网络，未对外暴露） |

---

## 🆘 紧急回滚

如部署后发现严重问题需回滚到上一个版本：

```bash
# 1. 停止新版本容器（保留数据卷）
docker compose down

# 2. 恢复旧版本 JAR 和 dist
# （将旧版本的 Hbsz_IDC_System_end.jar 和 dist/ 覆盖回去）

# 3. 重新启动
docker compose up -d --build

# 4. 如 Flyway 已执行 V20260720，可手动回滚索引（谨慎操作）：
# docker compose exec mysql mysql -uroot -p<密码> hbsz_idc_system \
#   -e "ALTER TABLE biz_task_progress_log DROP INDEX idx_project_review_time;
#       ALTER TABLE biz_document DROP INDEX idx_project_status_category;
#       ALTER TABLE sys_operation_log DROP INDEX idx_user_created;
#       DELETE FROM flyway_schema_history WHERE version='20260720';"
```
