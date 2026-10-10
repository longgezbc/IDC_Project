# IDC_Project

[![Frontend CI](https://github.com/longgezbc/IDC_Project/actions/workflows/frontend.yml/badge.svg)](https://github.com/longgezbc/IDC_Project/actions/workflows/frontend.yml)
[![Backend CI](https://github.com/longgezbc/IDC_Project/actions/workflows/backend.yml/badge.svg)](https://github.com/longgezbc/IDC_Project/actions/workflows/backend.yml)

数据中心综合布线项目管理系统（IDC 项目管理系统）——覆盖项目立项、进度跟踪、工作量统计、审批流转、文档归档的全流程管理平台，提供 Web 管理后台、Android App 与微信小程序三端入口。

## 功能特性

- **项目管理**：项目全生命周期管理、项目团队、项目授权、项目经理移交（含审计日志）
- **进度与任务**：项目进度跟踪、甘特图、任务编辑、进度上报与审核流程
- **工作量统计**：工作量录入、逐级审核、团队/个人绩效、报表导出（Excel）
- **审批中心**：可配置审批流、待办中心、我的提交、审批通知
- **文档管理**：版本控制、上传审核、在线预览、标签体系、竣工归档
- **日常协作**：日报管理、客户管理、风险看板、统计看板（ECharts 可视化）
- **系统管理**：用户 / 角色 / 权限 / 岗位管理、操作日志审计、系统配置（数据库持久化）
- **实时通知**：WebSocket（STOMP）站内消息、通知 / 提醒抽屉、每日待办摘要
- **安全机制**：JWT 双 Token 认证、图形验证码、接口限流、幂等控制、登录态会话监控

## 技术栈

| 端 | 技术 | 说明 |
|---|---|---|
| Web 前端 | Vue 3 + Element Plus + Vite 5 + Pinia + ECharts | ESLint 代码检查、Vitest 单元测试 |
| 后端 | Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis 7 | Flyway 迁移、Knife4j 接口文档、Druid 连接池、POI 导出 |
| Android | Kotlin 2.0 + Jetpack Compose + Hilt + Retrofit | 生物识别登录、应用内自更新 |
| 小程序 | uni-app + Vue 3 | 与 Web 端共享 `shared/` 纯函数模块 |
| 部署 | Docker Compose + Nginx 反向代理 | 前后端容器化，WebSocket 独立代理 |

## 项目结构

```
IDC_Project/
├── Hbsz_IDC_System/            # Web 前端（Vue 3 管理后台）
│   ├── src/api/                # 后端接口封装
│   ├── src/views/              # 页面模块（项目/进度/工作量/审批/文档/系统）
│   ├── src/stores/             # Pinia 状态管理
│   └── src/components/          # 通用组件（表格、甘特图、审批流图等）
├── Hbsz_IDC_System_end/        # 后端（Spring Boot 3）
│   ├── src/main/java/.../controller/   # REST 接口（统一 /api 前缀）
│   ├── src/main/java/.../service/     # 业务逻辑（含工作量统计域拆分）
│   ├── src/main/resources/db/migration/  # Flyway 数据库迁移脚本
│   └── src/test/               # JUnit 单元测试（H2 内存库独立运行）
├── Hbsz_app_Android/           # Android App（Compose + 自更新）
├── hbsz_-uniapp_-vue-master/   # 微信小程序（uni-app）
├── shared/                     # Web 与小程序共享的纯函数模块
├── docker/                     # 部署辅助文件
├── docker-compose.yml          # 容器编排（MySQL/Redis/后端/前端）
├── docs/                       # 项目文档
└── .github/workflows/          # CI 工作流（前端/后端/Android）
```

## 快速开始

### 环境要求

- Node.js 20 LTS（前端构建）
- JDK 17 + Maven 3.8+（后端构建）
- Docker & Docker Compose（容器化部署）

### 方式一：Docker 一键部署（推荐）

1. 在项目根目录创建 `.env.docker`（敏感凭据文件，已 gitignore，不入库）：

   ```env
   MYSQL_ROOT_PASSWORD=<MySQL root 密码>
   DB_PASSWORD=<业务库 hbsz_app 密码>
   REDIS_PASSWORD=<Redis 密码>
   JWT_SECRET=<JWT 签名密钥，建议 64 位随机十六进制>
   SPRING_PROFILES_ACTIVE=prod
   ```

2. 启动全部服务：

   ```bash
   docker compose --env-file .env.docker up -d --build
   ```

3. 访问 `http://服务器IP/`（前端默认 80 端口，后端仅绑定本机回环，由 Nginx 反代 `/api/` 与 `/ws/`）。

### 方式二：本地开发

```bash
# 后端（默认 8080 端口，context-path 为 /api；需本地或容器提供 MySQL/Redis）
cd Hbsz_IDC_System_end
mvn spring-boot:run

# 前端（默认 5173 端口，/api 与 /ws 已代理到 localhost:8080）
cd Hbsz_IDC_System
npm install
npm run dev
```

- 前端地址：http://localhost:5173
- 接口文档（Knife4j）：http://localhost:8080/api/swagger-ui.html
- 数据库结构由 Flyway 在后端启动时自动迁移，无需手工执行 SQL

### 常用命令

| 命令 | 说明 |
|---|---|
| `npm run dev` / `build` / `lint:check` / `test` | 前端开发 / 构建 / 检查 / 测试 |
| `mvn clean test` / `mvn package` | 后端测试（H2 独立运行）/ 打包 JAR |
| `./gradlew assembleDebug` | Android 构建 Debug APK（release 需本地签名密钥） |

## CI/CD

三条流水线均由 push / pull_request 到 `main` 分支触发（按目录路径过滤），也支持在 Actions 页面手动触发：

| 工作流 | 覆盖目录 | 流程 | 产物 |
|---|---|---|---|
| Frontend CI | `Hbsz_IDC_System/` | npm ci → ESLint → Vitest → Vite 构建 | dist 构建包 |
| Backend CI | `Hbsz_IDC_System_end/` | JUnit 全量测试 → 打包 | Spring Boot JAR |
| Android CI | `Hbsz_app_Android/` | 单元测试 → Debug 构建 | Debug APK |

构建产物保留 7 天，可在各次运行的 Artifacts 页面下载。Android 仅构建 debug 变体——release 签名密钥不入库，正式签名在本地完成。

## 安全说明

- **密钥不入库**：`.env.docker`（数据库 / Redis / JWT 凭据）、`*.pem`（服务器 SSH 密钥）、`*.jks`（Android 签名密钥）均已通过 `.gitignore` 排除
- **凭据注入**：所有敏感配置通过环境变量注入，`application-prod.yml` 与 `docker-compose.yml` 仅引用不落值
- **本仓库为私有仓库**：包含业务 Excel 模板与竣工资料模板；如需转为公开仓库，必须先清理相关文件的历史记录

## 相关文档

- [用户操作指南](用户操作指南.md)
- [部署手册](DEPLOYMENT.md) / [部署检查清单](DEPLOYMENT_CHECKLIST.md)
- [技术分析](TECHNICAL_ANALYSIS.md) / [核心流程图](CORE_FLOWCHARTS.md)
- [UI 设计标准](ui设计标准.txt)

## 说明

- 前端、后端、小程序三个子项目在 [Gitee](https://gitee.com/longezbc) 上保留有独立的完整提交历史。
