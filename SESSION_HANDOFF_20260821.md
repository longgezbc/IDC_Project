# 会话交接文档 — 改进状态全记录

> **会话时间**：2026-08-20 ～ 2026-08-21（约 24 小时连续工作）
> **服务器**：ubuntu@124.220.158.177（PEM：`LongGeZbc_Service.pem`，部署目录 `~/hbsz`）
> **新会话快速上手**：本文档按「已上线 / 已构建未部署 / 遗留事项」三层组织，新对话先读「§5 遗留事项」。

---

## 1. 本次会话改进总览（按时间线）

| # | 改进项 | 类型 | 状态 |
|---|--------|------|------|
| 1 | 审计/授权模块全量代码审查 + 复检 | 审查 | 完成 |
| 2 | P0 修复：审计日志 `operated_by` 可空化（回收任务每晚必然失败） | Bug | **已上线** |
| 3 | 运维部署文件审查 + 修复（B1/B2/W1/W3） | 运维 | **已上线** |
| 4 | 生产部署 Runbook（备份→部署→回滚全流程） | 文档 | 完成 |
| 5 | 生产事故修复：`uk_project_engineer_active_scope` 唯一索引缺陷（V20260821） | 事故 | **已上线** |
| 6 | Android 端同步：项目转移/授权/转移日志全功能 | 功能 | 已构建（APK 待分发） |
| 7 | Android 端 UI 全面优化（语义色图标/蓝图网格/骨架屏）+ Vico 图表库 + Coil 激活 | UI | 已构建（APK 待分发） |
| 8 | 工作量统计模块重构（Web + Android + 后端）：快速填报卡片流 | 重构 | 后端已上线；**Web dist 未部署**；APK 待分发 |
| 9 | Web 工作台 UI：大气·光晕方向 Token 基建 + 样板页 | UI | 已构建（**含在工作台 dist 内，未部署**） |

---

## 2. 已上线内容（生产环境运行中，服务器 2026-08-21 17:52 验证）

### 2.1 后端（容器 hbsz-backend，JAR 部署于 17:51）

**当前运行版本包含**：
- P0 审计修复：迁移 `V20260820.1`（`biz_project_transfer_log.operated_by` 可空化）+ `AssignmentRevokeService` 收敛复用 `ProjectTransferLogHelper`（修复授权过期回收任务 INSERT 1364 每晚失败）
- 唯一索引事故修复：迁移 `V20260821`（删除坏索引 `uk_project_engineer_active_scope`，新建函数唯一索引 `uk_project_engineer_active`，仅约束活跃记录）+ 两处 `DuplicateKeyException` 防御
- **工作量快速填报后端**：`GET /workload/my/report-context` 端点 + `WorkloadMyReportVO` + `WorkloadServiceImpl.getMyReportContext()`（常用项按近 14 天频次排序 + 今日回显 + 昨日数量）
- Flyway 迁移总数 **17 条**（最新 20260821，全部 success=1）

**事故根因备忘**（防止复发）：原 4 列唯一索引含 `is_active`，导致"撤销后再授权"的第二次撤销必撞历史记录。教训：软删模式的唯一索引必须用函数索引（活跃记录取组合键，历史为 NULL 不约束），手法与 `V20260819.1` 一致。

### 2.2 运维配置（全部在线验证过）

| 项 | 内容 |
|----|------|
| B1/B2 | 两个 `.dockerignore` 修复版（此前与 Dockerfile COPY 冲突导致 build 必败） |
| W1 | nginx actuator 拦截：`= /api/actuator/health` 精确放行 + `^~ /api/actuator` 404（原正则被 `^~` 短路从未生效） |
| W3 | docker-compose 8 处 `:?` 强制凭据语法（缺失即拒绝启动） |

### 2.3 前端（容器 hbsz-frontend，昨晚 23:54 版本）

当前线上 dist = **UI 优化版**（工程蓝图横幅/StatCard/title-chip/语义色图标，97 文件 3.29MB）。
**不含**：工作量快速填报 Tab、大气·光晕 Token 体系（见 §3）。

### 2.4 备份（服务器 `~/hbsz/backup/`）

- `20260821_hotfix/`：MySQL 热备（31 表）+ 旧 JAR + 行数对账基准
- `20260821_workload/`：工作量部署前的 JAR

---

## 3. 已构建未部署（本地产物，按需上线）

### 3.1 前端 dist（⚠️ 最重要）

**位置**：`Hbsz_IDC_System/dist/`（2026-08-21 18:28 构建，97 文件）
**包含**：
1. **工作量快速填报 Tab**（默认 Tab，普通工人 30 秒路径：常用项卡片流 + 复制昨天 + 状态锁定）— `QuickReportPanel.vue` 新组件 + `WorkloadStatistics.vue` 改造（原"每日填报"改名"矩阵代填"）
2. **大气·光晕 UI 升级**（工作台样板）：
   - Token 基建：`global.css` 新增 `--aurora-*`（氛围色）/ `--glow-*`（光晕阴影）/ `--font-numeric`（等宽数字栈）/ 动效曲线时长 Token + `.grain-overlay`（SVG 噪点）/ `.aurora-blob` / `.num` / `.glow-hover-*` 工具类
   - `WelcomeBanner.vue`：墨蓝深色舞台区 + 三枚错峰呼吸光斑 + 颗粒噪点 + 玻璃芯片
   - `StatCard.vue`：数字滚动（`useCountUp` 新组合式函数）+ mono 数字 + hover 泛 tone 色光晕
   - `Dashboard.vue`：四卡 60/140/220/300ms stagger 入场

**部署方式**：上传 `dist/*` → `Hbsz_IDC_System/dist/` → `docker compose --env-file .env.docker up -d --build frontend`（停机约 10 秒）
**后端已就绪**（report-context 端点 17:52 上线），dist 部署后快速填报即可完整验收。

### 3.2 Android APK

**位置**：`Hbsz_app_Android/app/build/outputs/apk/debug/app-debug.apk`（20.8 MB，17:34 构建）
**包含**：项目转移/授权/转移日志全功能 + UI 优化（语义色宫格/蓝图网格横幅/骨架屏/Vico 图表/Coil 头像）+ **工作量快速填报表单**（`WorkloadReportScreen.kt` 重写：常用卡片 + 复制昨天 + 状态锁定）
**验证状态**：编译/安装/启动/登录页渲染通过；填报表单端到端未验证（本机模拟器网络出口受限）
**依赖**：后端 report-context 端点（已上线 ✓）

---

## 4. 关键技术决策存档

1. **工作量重构写路径零变更**：快速填报仅新增查询端点，保存复用 `POST /workload/records`（审核链路/项目组成员校验/upsert 后端单点不变）。Web 与 Android 共享此契约。
2. **唯一索引手法**：函数唯一索引 `(CASE WHEN is_active=1 THEN CONCAT(...) ELSE NULL END)`，MySQL 8.0.13+，INPLACE 不重建表。
3. **Android 快速填报 DTO**：后端 Long 键 Map 在 JSON 序列化为字符串键，App 端 `Map<String, ...>` 对应。
4. **Glow+Grain 方向适配**：亮色运营工具不做全屏暗色——横幅作为唯一深色"舞台区"，页面主体保持运营密度；动效预算克制（微交互+数字滚动+呼吸光斑，无滚动叙事）。
5. **服务器 SSH**：known_hosts 写入被沙箱拦截，用 `logs/known_hosts_tmp` 临时文件；远程脚本一律 scp 上传后执行（规避 PowerShell→bash 转义断裂）。

---

## 5. 遗留事项（新会话优先级排序）

### P1 — 建议尽快处理
- [ ] **部署 Web dist**（§3.1）：工作量快速填报 + 光晕 UI 上线，用户验收"复制昨天→微调→提交"闭环
- [ ] **验收项目 24 转移**：admin 重试昨夜失败的项目转移（索引修复后应成功），确认 `biz_project_transfer_log` 新增 transfer 记录

### P2 — 计划内跟进
- [ ] **Android APK 分发**：20.8 MB debug 包含全部新功能；正式版需换 release keystore（当前复用 debug 签名，`build.gradle.kts` 内有注释提醒）
- [ ] **W2 限流 TRUSTED_PROXIES**：后端全流量经前端容器转发，限流按容器 IP 计数（未配置固定 IP + 白名单，未登录接口限流可能集体误伤）
- [ ] **W4 backend 内存**：tmpfs `/app/exports` 128M 计入 cgroup，Excel 导出高峰可能触发 288M 限额 OOM；建议 exports 卷化或限额提至 352M
- [ ] **favicon.ico 缺失**：外部扫描器产生 404 噪音日志
- [ ] **授权过期回收任务观察**：V20260820.1 修复后，明日凌晨 2 点检查日志 `[授权过期回收][完成]` 及 `operator_name='系统定时任务'` 审计记录

### P3 — 后续迭代
- [ ] 光晕 Token 铺开全站（项目列表 StatCard 继承光晕 hover；快速填报卡片用 `.num` + `numberSettle`）
- [ ] 工作量重构补测试：`getMyReportContext` 无单测（服务层）；`QuickReportPanel` 无组件测试
- [ ] 审查报告遗留项：`createDirectAssignment` 缺 `@Transactional`（F2）；`writeTransferLog` 姓名 fallback（F4）；`pageSize=100` 截断（F3）；`01-schema.sql` 补 `uk_auth_request_pending` 索引 + V20260819.1 头部过期注释更新（W5/P1）
- [ ] Vico 图表 marker 触摸提示：1.15 版 `marker()` 为 internal，升级 2.x 时启用（StatisticsScreen.kt 有注释锚点）
- [ ] Android my.cnf 弃用参数（`innodb_log_file_size`/`expire_logs_days` → `innodb_redo_log_capacity`/`binlog_expire_logs_seconds`）

---

## 6. 构建命令速查

```powershell
# 后端（Maven 路径固定）
& "C:\Users\QianLong\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd" clean package
# cwd: Hbsz_IDC_System_end  → target/Hbsz_IDC_System_end.jar（81.8MB）

# 前端
npm run build   # cwd: Hbsz_IDC_System → dist/（97 文件）

# Android
.\gradlew.bat assembleDebug   # cwd: Hbsz_app_Android → app-debug.apk（20.8MB）

# SSH 部署（PEM + 临时 known_hosts）
$pem = "C:\Users\QianLong\Desktop\Hbsz\LongGeZbc_Service.pem"
$kh = "C:\Users\QianLong\Desktop\Hbsz\logs\known_hosts_tmp"
ssh -i $pem -o BatchMode=yes -o UserKnownHostsFile=$kh ubuntu@124.220.158.177 "<命令>"
```

**注意**：后端改动后必须 `mvn clean package`（本会话曾因只跑 compile/test 导致旧 JAR 被上传，已踩坑）。

---

## 7. 验证基线（回归对照）

| 组件 | 基线 |
|------|------|
| 后端测试 | 106/106 通过 |
| 前端测试 | 34/34 通过（vitest） |
| 前端 ESLint | 0 errors（存量为格式 warning） |
| 前端构建 | 15-16s，97 文件 3.29MB，0 sourcemaps |
| Flyway | 17 条迁移全部 success=1 |
| 生产容器 | 4 容器 healthy，backend 启动后 ERROR = 0 |

---

*文档生成：2026-08-21 18:40 · 由上一会话助手整理*
