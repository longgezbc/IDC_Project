# 06 - Android 模块

> 模块路径：`Hbsz_app_Android/`
> 包根：`com.example.hbsz_app_android`
> 技术定位：移动端 App（Kotlin + Jetpack Compose 单 Activity 架构），与 Web 端共用同一套后端 API（`/api/v1`）
> 文档版本：v4.0（2026-08-23）

---

## 1. 技术栈（gradle/libs.versions.toml）

| 依赖 | 版本 | 用途 |
|------|------|------|
| Kotlin / AGP | 2.0.21 / 8.13.1 | 语言 / 构建插件（Compose 编译器插件） |
| Compose BOM | 2024.12.01 | UI 框架（Material3 + adaptive-navigation-suite + icons-extended） |
| Navigation Compose | 2.8.5 | 导航（类型安全路由 + kotlinx.serialization） |
| Hilt | 2.52（+ hilt-navigation-compose 1.2.0） | 依赖注入（KSP） |
| Retrofit + kotlinx-serialization converter | 2.11.0 | 网络层（JSON 序列化） |
| OkHttp（+ logging） | 4.12.0 | HTTP 客户端 |
| Coil Compose | 2.7.0 | 图片加载（MineScreen 头像 AsyncImage） |
| DataStore Preferences | 1.1.1 | 本地存储（token/登录态） |
| Vico compose-m3 | 1.15.0 | 统计页月度趋势交互式柱状图（入场动画 + 触摸提示，取代手绘 Canvas） |
| androidx.biometric | 1.1.0 | 生物识别（指纹快速登录，第二批 #4） |

构建：`compileSdk 36 / targetSdk 36 / minSdk 26`（见 [app/build.gradle.kts](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_app_Android/app/build.gradle.kts)）；当前版本 **v1.0.3（versionCode 4）**——修复指纹快速登录密钥失效死循环（重录指纹后反复"无法识别"）；release 产物约 3.2MB（R8 混淆 + 资源收缩）。

> **网络入口（v1.0.1 变更）**：`API_BASE_URL` 统一切换为 `http://124.220.158.177/api/`（nginx 80 端口反代）。后端 8080 已仅绑定服务器本机回环 `127.0.0.1`，不再公网暴露，App 不得直连 8080。发布 APK 归档于根目录 `dist_apk/`（含 v1.0.0/v1.0.1/v1.0.2/v1.0.3 历史版本）。

## 1.1 版本自更新（v1.0.2 新增，第二批 #3）

- **分发协议**：`GET {host}/downloads/app-latest.json`（versionCode/versionName/apkUrl/forceUpdate/updateLog），清单与 APK 同源同目录（nginx 静态），杜绝"接口宣告新版但 APK 未就位"
- **实现**：`data/update/AppUpdateManager`（fetchLatest + downloadApk 进度下载）；`ui/update/UpdateViewModel + UpdateCheckHost`（弹窗状态机：Available→Downloading→Ready→安装器拉起）；挂在 AppRoot 任意登录态可提示
- **安装**：APK 下载至 `getExternalFilesDir("update")`，FileProvider content URI + `ACTION_VIEW` 拉起系统安装器（`REQUEST_INSTALL_PACKAGES` 权限，首次触发"安装未知应用"系统授权引导）
- **强制更新**：`forceUpdate=true` 时无"以后再说"、不可关闭
- **Kill switch**：改服务器 `app-latest.json`（versionCode 调低/forceUpdate=false）即停止推新；**改后必须 rebuild frontend 容器**（dist 是 COPY 进镜像的，restart 不生效）
- **发版流程**：改 `versionCode/versionName` → assembleRelease → 上传 APK 与更新 `app-latest.json` 至 `dist/downloads/` → `docker compose up -d --build frontend`
- **清单同步注意（2026-08-23 状态）**：仓库 `dist_apk/app-latest.json` 当前仍声明 `versionCode=3 / versionName=1.0.2`，而代码已升至 `versionCode=4 / versionName=1.0.3`。要让 v1.0.3 推送给存量用户，必须同步更新服务器 `app-latest.json` 的 versionCode/apkUrl 并 rebuild frontend
- 已知坑：重复下载同路径 APK 时 `File.equals` 相同 → StateFlow 判等去重不重发 → 安装器不会自动拉起。已修复：下载完成在 VM 回调内直接调 `installFile()`，不依赖 Compose LaunchedEffect

## 1.2 指纹快速登录（v1.0.2 新增，第二批 #4；v1.0.3 修复）

- **依赖**：`androidx.biometric:biometric:1.1.0`；MainActivity 基类改为 **FragmentActivity**（BiometricPrompt 硬性要求）
- **安全模型**：`data/local/CredentialVault` —— Android Keystore AES-256-GCM 密钥绑定生物识别（`setUserAuthenticationRequired(true)` + `setInvalidatedByBiometricEnrollment(true)`），密码密文只有 BiometricPrompt(CryptoObject) 验证通过才能加/解密，App 自身无法绕过；新录入指纹自动失效旧密文
- **开启**：登录页"开启指纹快速登录"勾选（Row.toggleable，整行可点）→ 登录成功 → AppRoot 层 `BiometricSetupHost` 弹指纹验证 → 加密落盘。**必须挂 AppRoot**：挂 LoginScreen 会因登录成功切页卸载导致弹窗被取消
- **使用**：登出/token 失效回登录页 → "指纹快速登录"按钮 → 指纹验证 → 用户名/密码自动填充（验证码仍需手输，保留人机校验）
- **仅支持 BIOMETRIC_STRONG**（Class 3 硬件级防伪造）；不支持的设备不显示入口
- **登出语义**：`UserPreferences.clear()` 只清登录态、**保留 biometric 凭据**（密文受指纹保护；"快速登录"的使用场景正是登出后）
- **⚠ v1.0.3 关键修复**：v1.0.2 在"重录指纹 → Keystore 密钥失效"场景下会反复弹"无法识别"进入死循环。v1.0.3（versionCode 4）修复该死循环：捕获密钥失效后重建密钥并清除旧密文，引导用户重新开启指纹登录，不再卡在无效密文上反复验证

## 2. 包结构

```
com.example.hbsz_app_android/
├── HbszApplication.kt        # @HiltAndroidApp 入口
├── MainActivity.kt           # 单 Activity（FragmentActivity 基类，承载 AppNavHost）
├── AppViewModel.kt           # 全局状态（登录用户、通知未读数）
├── common/                   # Resource（统一 Result 封装）、Format、DownloadSaver
├── data/
│   ├── local/
│   │   ├── UserPreferences.kt     # DataStore：token/refreshToken/username/roles
│   │   ├── CredentialVault.kt     # Keystore AES-GCM 凭据加解密
│   │   └── BiometricLoginManager.kt # BiometricPrompt + CryptoObject
│   ├── remote/
│   │   ├── ApiService.kt            # Retrofit 接口（全部后端端点）
│   │   ├── AuthInterceptor.kt       # 自动附加 Authorization: Bearer
│   │   ├── ApiResponse.kt           # 统一响应模型（code/message/data）
│   │   └── dto/                     # AuthDtos、BackendDtos、WorkloadDtos、
│   │                                # WorkloadQuickReportDtos、ProjectAuthDtos
│   ├── update/
│   │   └── AppUpdateManager.kt      # 版本自更新（拉清单/下载/进度）
│   └── repository/
│       ├── AuthRepository.kt        # 登录/登出/token 刷新
│       └── HbszRepository.kt        # 全业务仓储（含 ProjectAuth 授权/转移、
│                                    # getMyReportContext 快速填报上下文）
├── di/
│   ├── NetworkModule.kt     # Json/Authenticator/OkHttp/Retrofit/ApiService
│   │                        # （401 清除登录态、超时、日志、BASE_URL 含 /api）
│   └── AppModule.kt         # Repository/Preferences 提供
└── ui/
    ├── AppViewModel 已列于根包
    ├── main/MainScreen.kt           # 底部 Tab 壳（Scaffold + NavigationBar）
    ├── navigation/                  # Screen.kt 路由表 + AppNavHost.kt NavHost
    ├── login/LoginScreen(+VM)       # 验证码登录 + 蓝图网格渐变背景
    ├── home/HomeScreen.kt           # 工作台（语义色功能宫格、品牌渐变、近期动态、转移日志入口）
    ├── project/                     # ProjectScreen、ProjectDetailScreen、
    │                                # ProjectTransferDialog（三模式转移弹窗）、TransferLogsScreen
    ├── task/MyTasksScreen.kt        # 我的任务
    ├── progress/                    # ProgressReportScreen、ReviewScreen、MySubmissionsScreen
    ├── workload/                    # WorkloadReportScreen（快速填报卡片流）、WorkloadReviewScreen
    ├── document/DocumentScreen.kt   # 资料管理
    ├── customer/CustomerScreen.kt
    ├── archive/ArchiveScreen.kt
    ├── statistics/StatisticsScreen.kt  # Vico 柱状图
    ├── daily/DailyReportScreen.kt
    ├── notification/NotificationScreen.kt
    ├── approval/ApprovalTodoScreen.kt
    ├── mine/MineScreen.kt           # 我的（Coil 头像）
    ├── profile/                     # ProfileScreen、AccountSecurityScreen、
    │                                # NotificationSettingsScreen、AboutScreen
    ├── components/                  # StatusTag、StatCard、Skeleton（Shimmer 骨架）、
    │                                # BlueprintGrid（工程蓝图网格纹理）、CommonStates（空/错/载入
    │                                # 语义色 + 暗色模式）、PullRefreshBox、FeedbackSnackbar、PickerFields
    └── theme/                       # Color.kt、Type.kt、Theme.kt（动态色/暗色）
```

## 3. 导航（navigation/）

- `Screen.kt`：底部 Tab 枚举（工作台/项目/资料/我的）+ `Dest` 二级路由表（`project_detail`、`my_tasks`、`progress_report`、`workload_report`、`approval_todo`、`transfer_logs`、`profile` 等 14+ 目的地）
- `AppNavHost.kt`：注册 NavHost、参数路由（项目详情/进度填报传 projectId）、`navigateToTab` Tab 切换（保存/恢复状态）

## 4. 网络与认证

```
ApiService（Retrofit，BASE_URL 含 /api）
   ▲ AuthInterceptor：从 UserPreferences.tokenFlow 读取 token → Authorization: Bearer
   ▲ Authenticator：401 时清除登录态并跳转登录
   ▼ ApiResponse<T>：统一 code/message/data，Repository 转 Resource<T>（Loading/Success/Error）
```

- 认证端点：`/captcha`、`/auth/login`、`/auth/logout`、`/auth/info`
- 业务端点覆盖：dashboard、projects（含 7 个授权/转移端点）、tasks/progress、notifications、documents、customers、statistics、files/upload、daily-report、workload（含 `/workload/my/report-context`）、approval/todos
- 详见 [ApiService.kt](file:///c:/Users/QianLong/Desktop/Hbsz/Hbsz_app_Android/app/src/main/java/com/example/hbsz_app_android/data/remote/ApiService.kt)

## 5. UI 设计体系（与 Web 端对齐）

- 语义色图标系统（对应 Web 端软色图标系统）；StatusTag 的 `actionTextOf/actionToneOf` 扩展
- StatCard 数字 `tabular-nums` 对齐；Shimmer 骨架加载态
- CommonStates：空态/错误/加载三态 + 暗色模式支持
- BlueprintGrid：工程蓝图网格纹理背景；品牌渐变
- 无 emoji 图标（Material Icons extended）

## 6. 关键业务页

| 页面 | 功能 |
|------|------|
| WorkloadReportScreen | 2026-08-21 重构：单项目自动选中、14 天频次卡片（含元数据）、一键复制昨日（仅空值）、语义状态指示（通过绿/待审橙/驳回红）、实时提交计数 |
| ProjectDetailScreen + ProjectTransferDialog | 授权工程师区块 + 三模式转移弹窗（转移并撤销旧授权/保留授权/移交全部） |
| TransferLogsScreen | 项目转移审计日志 |
| ProgressReportScreen | 进度填报（施工图片上传、跨字段日期校验） |
| ApprovalTodoScreen | 审批待办聚合 |

## 7. 构建与运行

```powershell
cd Hbsz_app_Android
.\gradlew assembleDebug      # 调试包 → app/build/outputs/apk/debug/
.\gradlew assembleRelease    # 发布包（签名配置见 build.gradle.kts）
```

- `AndroidManifest.xml`：权限 `INTERNET`（+ 条件 WRITE_EXTERNAL_STORAGE）、`REQUEST_INSTALL_PACKAGES`（自更新安装）、`networkSecurityConfig`、launcher `MainActivity`、FileProvider
- 签名：读取顺序为环境变量（CI/其他机器）优先，回退到根目录 `keystore.properties`（已 gitignore）；keystore 文件 `hbsz-release.jks`（已 gitignore，**丢失即永远无法同签名升级，请离线备份两处以上**）
- 模拟器验证：登录/工作台/项目列表截图归档于 `logs/ui_*.png`
- 模拟器生物识别测试流程：settings put + appops REQUEST_INSTALL_PACKAGES allow；`adb emu finger touch 1`；通过 `android.settings.FINGERPRINT_ENROLL` 录入指纹（需先设 PIN）

## 8. 版本演进

| 版本 | versionCode | 要点 |
|------|------------|------|
| v1.0.0 | 1 | 首版（2026-08-21） |
| v1.0.1 | 2 | API_BASE_URL 切换至 nginx 80 反代；后端 8080 仅绑 127.0.0.1 |
| v1.0.2 | 3 | 新增版本自更新（第二批 #3）；新增指纹快速登录（第二批 #4） |
| v1.0.3 | 4 | 修复指纹快速登录密钥失效死循环（重录指纹后反复"无法识别"） |

> 当前 `dist_apk/app-latest.json` 仍声明 v1.0.2（versionCode 3），需更新清单并在前端容器 rebuild 后才能向存量用户推送 v1.0.3。
