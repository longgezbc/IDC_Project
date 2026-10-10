import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ============================================================================
// 正式签名配置（2026-08-21 启用）
// 读取顺序：环境变量（CI/其他机器）优先，回退到根目录 keystore.properties（本地，已 gitignore）
// keystore 文件：根目录 hbsz-release.jks（已 gitignore）
// ⚠ 丢失 keystore 或密码 = 该应用永远无法同签名升级，请离线备份两处以上
// ============================================================================
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.hbsz_app_android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.hbsz_app_android"
        minSdk = 26
        targetSdk = 36
        // v1.0.4：生产服务器迁移 124.220.158.177 → 106.53.54.22（API 基址与自更新通道同步切换）
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("HBSZ_KEYSTORE_PATH")
                ?: keystoreProps.getProperty("storeFile")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
            }
            storePassword = System.getenv("HBSZ_KEYSTORE_PASSWORD")
                ?: keystoreProps.getProperty("storePassword")
            keyAlias = System.getenv("HBSZ_KEY_ALIAS")
                ?: keystoreProps.getProperty("keyAlias")
            keyPassword = System.getenv("HBSZ_KEY_PASSWORD")
                ?: keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            // 走 nginx 80 端口反代（后端 8080 已仅绑服务器本机回环，不再公网暴露）
            buildConfigField("String", "API_BASE_URL", "\"http://106.53.54.22/api/\"")
        }
        release {
            // 发布构建启用代码混淆与资源收缩，防止反编译、减小体积
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", "\"http://106.53.54.22/api/\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose UI（BOM 统一管理版本）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)

    // 导航
    implementation(libs.androidx.navigation.compose)

    // 依赖注入（Hilt）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // 网络（Retrofit + OkHttp + Kotlinx Serialization）
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // 本地键值存储（DataStore）
    implementation(libs.androidx.datastore.preferences)

    // 图片加载（Coil）
    implementation(libs.coil.compose)

    // 图表（Vico，M3 风格）：统计页交互式柱状图
    implementation(libs.vico.compose.m3)

    // 生物识别（指纹/面部/虹膜统一 API）：登录页"指纹快速登录"（第二批 #4）
    implementation(libs.androidx.biometric)

    // 测试
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
