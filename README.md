# IDC_Project

[![Frontend CI](https://github.com/longgezbc/IDC_Project/actions/workflows/frontend.yml/badge.svg)](https://github.com/longgezbc/IDC_Project/actions/workflows/frontend.yml)
[![Backend CI](https://github.com/longgezbc/IDC_Project/actions/workflows/backend.yml/badge.svg)](https://github.com/longgezbc/IDC_Project/actions/workflows/backend.yml)

数据中心综合布线项目管理系统（IDC 项目管理系统）

## 项目结构

- `Hbsz_IDC_System` — Web 前端（Vue 3 + Element Plus + Vite）
- `Hbsz_IDC_System_end` — 后端（Spring Boot + MyBatis + MySQL + Redis）
- `Hbsz_app_Android` — Android App（Kotlin + Jetpack Compose）
- `hbsz_-uniapp_-vue-master` — 小程序 / 移动端（uni-app + Vue 3）
- `docker` / `docker-compose.yml` — 容器化部署
- `docs` / `scripts` / `shared` — 文档、脚本与共享资源

## 说明

- 敏感文件（`.env.docker`、`*.pem`、`*.jks` 签名密钥等）已通过 `.gitignore` 排除，不纳入版本管理。
- 数据库、Redis、JWT 等凭据均通过环境变量注入，参考 `docker-compose.yml` 与各项目配置文件。
- 前三个子项目在 Gitee 上保留有独立的完整提交历史。
