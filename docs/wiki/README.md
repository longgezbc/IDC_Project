# Code Wiki - 数据中心综合布线项目管理系统

> **版本**：v4.0 | **生成日期**：2026-08-23
>
> **v4.0 变更摘要**（纳入 2026-08-23 部署内容）：
> - **App v1.0.3（versionCode 4）发布**：修复指纹快速登录密钥失效死循环（重录指纹后反复"无法识别"），APK 归档于 `dist_apk/Hbsz_IDC_v1.0.3_20260823.apk`
> - **自定义指纹图标**：`@element-plus/icons-vue` 不提供指纹图标，新增自定义 SVG 组件 [FingerprintIcon.vue](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/components/FingerprintIcon.vue)，在 [utils/icons.js](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/src/utils/icons.js) 注册为全局 `Fingerprint`，用法与内置图标一致
> - **白屏事故教训**：从 icons.js 移除图标时，import 块与 icons 数组两处必须同步清理，否则 Rollup 把未定义标识符当全局变量（构建通过）→ 运行时 ReferenceError 致整站白屏
> - **前端 dist 自包含部署**：`dist/downloads/`（APK + `app-latest.json`）随前端镜像一同构建打入，部署自包含；改清单需 rebuild frontend 容器（restart 不生效）
> - 后端测试扩至 134 个（134/134 通过，新增 `ProjectServiceImplManagerLinkTest` ×4）
> - 2026-08-23 全量重部署：后端 JAR hash 不变 → 零停机；前端因 dist 落后于 src 已 rebuild
>
> **v3.0 变更摘要**（纳入 2026-08-22 部署内容）：
> - 通知主动触达体系（W1-W3）：工作量填报/审核、文档审核、任务分配/进度审核全链路站内通知
> - 每日待办摘要推送（W5，`DailyTodoDigestScheduler`，每日 09:00）
> - 工作量被驳回项「一键重报」（W4，`QuickReportPanel.vue`）
> - 登录页粒子背景（`LoginParticles.vue`）
> - 端口安全加固：后端 8080 仅绑定 `127.0.0.1`，App v1.0.1 切换至 nginx 80 端口反代
> - 后端测试扩至 130 个（130/130 通过）、前端 45 个、ESLint 0 警告
>
> 本套文档基于当前代码库全量分析生成，是项目唯一的 Code Wiki（根目录旧版 `CODE_WIKI.md` 已删除）。
> 文档中的文件链接均为可点击的本地源码引用。

## 文档目录

| 篇目 | 内容 | 适合读者 |
|------|------|----------|
| [01-项目总览与架构](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/01-项目总览与架构.md) | 项目简介、四端架构图、请求处理链路、仓库目录、关键设计约束 | 所有人（新成员必读） |
| [02-后端-架构与基础设施](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/02-后端-架构与基础设施.md) | 技术栈依赖、分层架构、配置体系、JWT 认证、RBAC、AOP 切面、定时任务、工具类、本地运行 | 后端开发 |
| [03-后端-业务模块与API](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/03-后端-业务模块与API.md) | 28 个 Controller 端点清单、重点业务流程（进度审核流/项目转移/工作量/日报导出）、Service 层清单、权限码总表 | 后端开发、前端联调 |
| [04-数据库设计](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/04-数据库设计.md) | 实体与表清单（sys_/biz_）、ER 关系、17 个 Flyway 迁移脚本、Mapper 清单、变更流程约定 | 后端开发、DBA |
| [05-前端Web模块](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/05-前端Web模块.md) | Vue 3 技术栈、构建配置、路由表（28 条）、状态管理、API 层、工具库、组件（含 FingerprintIcon 自定义图标）、UI 规范、快速填报设计 | 前端开发 |
| [06-Android模块](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/06-Android模块.md) | Compose 技术栈、包结构、导航、网络认证、UI 体系、关键业务页、版本自更新、指纹快速登录、构建命令 | Android 开发 |
| [07-微信小程序模块](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/07-微信小程序模块.md) | uni-app 工程配置、34 个页面清单、token 刷新队列机制、发布注意事项 | 小程序开发 |
| [08-部署与运行](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/08-部署与运行.md) | Docker 5 服务拓扑、环境变量、Nginx 规则、本地开发、生产构建与部署流程、验证清单 | 运维、全角色 |

## 快速导航

- **我想跑起来这个项目** → [08-部署与运行 §4 本地开发环境](file:///c:/Users/QianLong/Desktop/Hbsz/docs/wiki/08-部署与运行.md)
- **我要新增一个后端接口** → 02（拦截器/权限注解）+ 03（Controller 模式）+ 04（如需表变更，Flyway 规范）
- **我要改前端页面** → 05（路由守卫/API 层/组件体系）
- **我要发版** → 08 §5-§7（构建产物 → 先后端后前端 → 验证清单）
- **核心业务规则**（进度必须带图、工作量需审核、转移唯一索引等） → 01 §5 关键设计决策与约束

## 关联文档（仓库根目录）

- 用户手册：`docs/USER_GUIDE_{ADMIN|PM|ENGINEER|USER}.md`
- 部署手册：`DEPLOYMENT.md`、`DEPLOY_RUNBOOK_20260820.md`、`PRODUCTION_CHECKLIST.md`
- 代码评审：`CODE_REVIEW_REPORT.md`、`CODE_REVIEW_REPORT_MINIAPP.md`、`WCAG_COMPLIANCE_REPORT.md`
- 架构分析：`TECH_STACK.md`、`PROJECT_STRUCTURE.md`、`CORE_FLOWCHARTS.md`

## 维护约定

- 代码发生结构性变更（新增模块/端点/表/页面）时，请同步更新对应篇目
- Flyway 新增迁移脚本后，更新 04 篇的迁移脚本表
- 图标增删时，参考 05 §7 关于 icons.js 的两处同步维护约定
