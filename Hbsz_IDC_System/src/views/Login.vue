<template>
  <div class="login-container">
    <!-- 装饰层：三枚呼吸光斑 + 颗粒噪点（纯装饰，屏幕阅读器隐藏） -->
    <div
      class="aurora-blob aurora-blob--cyan aurora-blob--anim login-blob login-blob--c"
      aria-hidden="true"
    />
    <div
      class="aurora-blob aurora-blob--blue aurora-blob--anim login-blob login-blob--b"
      aria-hidden="true"
    />
    <div
      class="aurora-blob aurora-blob--violet aurora-blob--anim login-blob login-blob--v"
      aria-hidden="true"
    />

    <!-- 粒子星网层：漂浮粒子 + 连线（光斑之上、卡片之下；光标聚合交互） -->
    <LoginParticles aria-hidden="true" />

    <div class="login-box fade-up-enter">
      <!-- 左侧：机房门厅舞台（深蓝，与站内 WelcomeBanner 同源） -->
      <div class="login-stage">
        <div
          class="grain-overlay"
          aria-hidden="true"
        />
        <div
          class="stage-grid"
          aria-hidden="true"
        />
        <div
          class="stage-rings"
          aria-hidden="true"
        >
          <span /><span /><span />
        </div>

        <div class="stage-content">
          <!-- 品牌 -->
          <header class="stage-brand">
            <img
              :src="logoImg"
              class="brand-logo"
              alt="系统 Logo"
            >
            <div class="brand-copy">
              <h1>IDC 数据中心<br>综合布线项目管理平台</h1>
              <p class="brand-eyebrow">
                IDC · STRUCTURED CABLING · PROJECT CONSOLE
              </p>
            </div>
          </header>

          <!-- 特性：玻璃芯片条目 -->
          <ul class="stage-features">
            <li
              v-for="(item, idx) in features"
              :key="idx"
              class="feature-chip"
              :style="{ animationDelay: 0.18 + idx * 0.12 + 's' }"
            >
              <span class="feature-icon">
                <el-icon><component :is="item.icon" /></el-icon>
              </span>
              <span class="feature-text">
                <span class="feature-title">{{ item.title }}</span>
                <span class="feature-desc">{{ item.desc }}</span>
              </span>
            </li>
          </ul>

          <!-- 底部读数：等宽数字 + 落定动画（机房监控读数感） -->
          <footer class="stage-metrics">
            <div
              v-for="(m, idx) in metrics"
              :key="idx"
              class="metric-chip"
            >
              <span class="metric-value num">{{ m.value }}</span>
              <span class="metric-label">{{ m.label }}</span>
            </div>
          </footer>
        </div>
      </div>

      <!-- 右侧：登录表单（暖白工作区） -->
      <div class="login-panel">
        <div class="panel-inner">
          <header class="panel-header">
            <h2>登录控制台</h2>
            <p class="panel-subtitle">
              使用项目组分配的账号进入系统
            </p>
          </header>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-position="top"
            aria-label="登录表单"
            @keyup.enter="handleLogin"
          >
            <el-form-item
              prop="username"
              label="用户名"
            >
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                :prefix-icon="User"
                autocomplete="username"
                aria-label="用户名"
                aria-required="true"
              />
            </el-form-item>
            <el-form-item
              prop="password"
              label="密码"
            >
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                :prefix-icon="Lock"
                show-password
                autocomplete="current-password"
                aria-label="密码"
                aria-required="true"
              />
            </el-form-item>
            <!-- P0-4.1：图形验证码（后端强制校验，未通过则登录失败） -->
            <el-form-item
              prop="captcha"
              label="验证码"
            >
              <div class="captcha-row">
                <el-input
                  v-model="loginForm.captcha"
                  placeholder="请输入图片中的字符"
                  size="large"
                  :prefix-icon="CircleCheck"
                  aria-label="验证码"
                  aria-required="true"
                  autocomplete="off"
                  maxlength="4"
                  @keyup.enter="handleLogin"
                />
                <div
                  class="captcha-image-box"
                  title="点击刷新验证码"
                  role="button"
                  tabindex="0"
                  aria-label="刷新验证码"
                  @click="refreshCaptcha"
                  @keyup.enter="refreshCaptcha"
                >
                  <img
                    v-if="captchaImage"
                    :src="captchaImage"
                    class="captcha-image"
                    alt="验证码图片"
                  >
                  <span
                    v-else
                    class="captcha-loading"
                  >加载中...</span>
                  <el-icon class="captcha-refresh">
                    <Refresh />
                  </el-icon>
                </div>
              </div>
            </el-form-item>
            <el-form-item>
              <div class="login-options">
                <el-checkbox v-model="loginForm.remember">
                  记住我
                </el-checkbox>
                <span class="forgot-hint">
                  <el-icon><InfoFilled /></el-icon>
                  忘记密码请联系管理员重置
                </span>
              </div>
            </el-form-item>
            <el-form-item>
              <button
                type="button"
                class="login-btn"
                :class="{ 'is-loading': loading }"
                :disabled="loading"
                :aria-disabled="loading"
                aria-label="登录"
                @click="handleLogin"
              >
                <span
                  v-if="!loading"
                  class="btn-content"
                >
                  <span>进入系统</span>
                  <el-icon class="btn-icon"><ArrowRight /></el-icon>
                </span>
                <span
                  v-else
                  class="btn-loading"
                >
                  <span class="loading-spinner" />
                  <span>登录中...</span>
                </span>
              </button>
            </el-form-item>
          </el-form>

          <div
            v-if="isDev"
            class="login-tip"
          >
            <el-icon><InfoFilled /></el-icon>
            <span>演示账号：admin / admin123（仅开发环境显示）</span>
          </div>
        </div>
        <div class="login-footer">
          Copyright © 2026 IDC数据中心项目管理系统 · 版权所有
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 登录页面（Glow+Grain 重设计版）
 * 业务用途：系统统一登录入口，用户输入账号密码 + 图形验证码完成身份认证后进入后台。
 * 主要功能区：左侧"机房门厅"深蓝舞台（品牌 + 特性 + 运行读数，视觉与站内
 *           WelcomeBanner 同源：呼吸光斑 / grain / 蓝图网格 / 等高线圆环）；
 *           右侧暖白表单区（用户名、密码、验证码、记住我）。
 * 数据来源：调用 useUserStore().login 进行登录鉴权（内部请求后端接口并保存 token）。
 *           调用 authApi.getCaptcha 获取图片验证码（captchaId + base64 图片）。
 *
 * P0-4.1：图形验证码强制启用。后端 LoginDTO 已将 captchaId/captcha 标记为 @NotBlank，
 *        未提供或校验失败将返回业务码 3006（CAPTCHA_ERROR），用户需重新获取并输入。
 *        验证码一次性使用：无论登录成功失败均失效，故登录失败后自动刷新验证码。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { feedback } from '@/utils/feedback'
import { User, Lock, ArrowRight, CircleCheck, Refresh } from '@element-plus/icons-vue'
import LoginParticles from '@/components/LoginParticles.vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
// 登录页品牌 Logo 图片
import logoImg from '@/static/logo/8da4edb7bc99f55c525d4722e1db8644.jpg'

const router = useRouter()          // 路由实例，登录成功后跳转
const route = useRoute()            // 当前路由，用于获取 redirect 参数
const userStore = useUserStore()    // 用户状态仓库，提供登录方法与用户信息
const loginFormRef = ref()          // 登录表单引用，用于触发表单校验
const loading = ref(false)          // 登录请求进行中的加载状态，控制按钮禁用与动画
// 仅开发环境显示演示账号提示
const isDev = import.meta.env.DEV

// 验证码状态：captchaId 为后端返回的标识（提交登录时携带），captchaImage 为 base64 图片
const captchaId = ref('')
const captchaImage = ref('')

const loginForm = reactive({
  username: isDev ? 'admin' : '',
  password: isDev ? 'admin123' : '',
  captcha: '',                       // 用户输入的验证码答案
  remember: true
})

// 表单校验规则：用户名必填且不少于 3 位，密码必填且不少于 8 位（与 Settings 修改密码策略一致），验证码必填
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, message: '用户名长度不能少于 3 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码长度不能少于 8 位', trigger: 'blur' }
  ],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

// 左侧舞台区的系统特性列表（图标、标题、描述）——真实功能描述，非营销套话
const features = [
  { icon: 'DataAnalysis', title: '全流程项目管理', desc: '工勘、出库、上架、布线、调试、收尾全流程跟踪' },
  { icon: 'FolderOpened', title: '文档统一管理', desc: '技术文档、合同、验收报告分类归档管理' },
  { icon: 'Bell', title: '关键节点提醒', desc: '里程碑与关键节点及时通知，避免项目延期' }
]

// 左侧底部运行读数（等宽数字 + 落定动画，机房监控面板语感）
const metrics = [
  { value: '6', label: '阶段全流程覆盖' },
  { value: '37', label: '张业务数据表联动' },
  { value: '4', label: '端（Web/Android/小程序）' }
]

/**
 * 获取图形验证码
 * 调用 GET /captcha 接口，后端返回 { captchaId, image }（image 为 base64 data URL），
 * 将 captchaId 存入响应式变量（提交登录时携带），图片直接渲染给用户识别。
 * 失败时清空验证码状态并提示用户。
 * @returns {Promise<void>}
 */
async function refreshCaptcha() {
  try {
    const res = await authApi.getCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.image
    loginForm.captcha = ''  // 清空用户输入，避免用旧答案提交新图片
  } catch (error) {
    console.error('获取验证码失败:', error?.message)
    captchaImage.value = ''
    captchaId.value = ''
    feedback.error('验证码获取失败，请点击刷新重试')
  }
}

// 页面加载时获取验证码
onMounted(() => {
  refreshCaptcha()
})

/**
 * 处理登录提交
 * 先触发表单校验，校验通过后调用 store 登录接口（携带 captchaId + captcha）；
 * 成功则提示并跳转首页；失败时错误信息由请求拦截器统一提示，并自动刷新验证码
 * （验证码一次性使用，失败后已失效）。
 * @returns {Promise<void>}
 */
async function handleLogin() {
  if (!loginFormRef.value) return
  loginFormRef.value.validate(async valid => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login({
        username: loginForm.username,
        password: loginForm.password,
        captchaId: captchaId.value,
        captcha: loginForm.captcha
      })
      feedback.success('登录成功，欢迎回来')
      // 登录成功后跳回原页面（路由守卫保存的 redirect 参数），无则跳首页
      // 安全：仅接受站内相对路径；排除 // 开头的协议相对 URL（会被浏览器解析为外部域）
      const redirect = route.query.redirect
      if (redirect && typeof redirect === 'string'
        && redirect.startsWith('/') && !redirect.startsWith('//')) {
        router.push(redirect)
      } else {
        router.push('/')
      }
    } catch (error) {
      // 错误信息已在 request 拦截器中统一提示
      console.error('登录失败:', error)
      // 验证码一次性使用：登录失败后已失效，需重新获取
      refreshCaptcha()
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
/* ==================================================================
   登录页 · Glow+Grain（与 global.css Token 同源）
   隐喻：机房门禁控制台。左侧深蓝门厅（光斑/网格/圆环，与站内
   WelcomeBanner 呼应），右侧暖白工作区承载表单。
   ================================================================== */

.login-container {
  position: relative;
  width: 100vw;
  min-height: 100vh;
  background: #0a1e3f;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 24px;
}

/* ---------- 容器级光斑：压住深底大面积纯色，制造氛围 ----------
   基类 .aurora-blob / 呼吸动画 --anim 来自 global.css；
   颜色修饰类为 WelcomeBanner 的 scoped 定义，此处补齐同源配色。 */
.login-blob {
  position: absolute;
  filter: blur(90px);
}

.login-blob--c {
  width: 560px;
  height: 560px;
  top: -200px;
  left: -160px;
  background: rgba(34, 211, 238, 0.16);
}

.login-blob--b {
  width: 640px;
  height: 640px;
  bottom: -260px;
  right: -200px;
  background: rgba(30, 139, 255, 0.22);
}

.login-blob--v {
  width: 420px;
  height: 420px;
  top: 30%;
  left: 42%;
  background: rgba(139, 124, 246, 0.14);
}

/* ---------- 卡片：非对称双栏 ---------- */
.login-box {
  position: relative;
  z-index: 1;
  width: 1000px;
  max-width: 100%;
  min-height: 620px;
  display: flex;
  border-radius: 20px;
  overflow: hidden;
  background: #fdfdfb;
  box-shadow:
    0 30px 80px rgba(4, 12, 30, 0.55),
    0 0 0 1px rgba(255, 255, 255, 0.08);
}

/* ============ 左侧：机房门厅舞台 ============ */
.login-stage {
  position: relative;
  width: 55%;
  background: linear-gradient(118deg, #081833 0%, #0d2c5c 55%, #10386e 100%);
  color: #fff;
  display: flex;
  align-items: stretch;
  overflow: hidden;
}

/* 蓝图网格：细线 + 斜向蒙版，保留工程语境 */
.stage-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 26px 26px;
  -webkit-mask-image: linear-gradient(115deg, rgba(0, 0, 0, 0.85) 30%, rgba(0, 0, 0, 0.2) 78%);
  mask-image: linear-gradient(115deg, rgba(0, 0, 0, 0.85) 30%, rgba(0, 0, 0, 0.2) 78%);
}

/* 等高线圆环：右下角，呼应站内 banner-rings */
.stage-rings {
  position: absolute;
  right: -70px;
  bottom: -70px;
  width: 260px;
  height: 260px;
  opacity: 0.5;
}

.stage-rings span {
  position: absolute;
  inset: 0;
  border: 1px solid rgba(120, 180, 255, 0.28);
  border-radius: 50%;
}

.stage-rings span:nth-child(2) {
  inset: 34px;
}

.stage-rings span:nth-child(3) {
  inset: 68px;
  border-color: rgba(34, 211, 238, 0.35);
}

.stage-content {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 36px;
  padding: 52px 46px 44px;
}

/* ---------- 品牌 ---------- */
.stage-brand {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

.brand-logo {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  object-fit: cover;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  flex-shrink: 0;
}

.stage-brand h1 {
  margin: 0 0 10px;
  font-size: 23px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.5px;
}

/* mono caps 眉线：工程图纸标注感 */
.brand-eyebrow {
  margin: 0;
  font-family: var(--font-numeric);
  font-size: 10px;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: rgba(140, 190, 255, 0.85);
}

/* ---------- 特性：玻璃芯片 ---------- */
.stage-features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-chip {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(6px);
  transition:
    background var(--dur-base) var(--ease-out-quart),
    transform var(--dur-base) var(--ease-out-quart);
  animation: stageChipIn 0.6s var(--ease-out-quart) backwards;
}

.feature-chip:hover {
  background: rgba(255, 255, 255, 0.13);
  transform: translateX(4px);
}

@keyframes stageChipIn {
  from {
    opacity: 0;
    transform: translateX(-18px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #8ec8ff;
  background: linear-gradient(135deg, rgba(30, 139, 255, 0.28), rgba(34, 211, 238, 0.16));
  border: 1px solid rgba(120, 180, 255, 0.25);
  flex-shrink: 0;
}

.feature-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.feature-title {
  font-size: 14px;
  font-weight: 600;
}

.feature-desc {
  font-size: 12px;
  line-height: 1.5;
  color: rgba(214, 230, 255, 0.78);
}

/* ---------- 运行读数 ---------- */
.stage-metrics {
  display: flex;
  gap: 12px;
  padding-top: 22px;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
}

.metric-chip {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.metric-value {
  font-family: var(--font-numeric);
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  color: #7fd4ff;
}

.metric-value.num {
  animation: numberSettle var(--dur-base) var(--ease-out-quart) backwards;
  animation-delay: 0.5s;
}

.metric-label {
  font-size: 11px;
  line-height: 1.4;
  color: rgba(214, 230, 255, 0.66);
}

/* ============ 右侧：暖白工作区 ============ */
.login-panel {
  width: 45%;
  display: flex;
  flex-direction: column;
  background: #fdfdfb;
}

.panel-inner {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 46px 24px;
}

.panel-header {
  margin-bottom: 30px;
}

.panel-header h2 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  color: #14203a;
  letter-spacing: -0.3px;
}

.panel-subtitle {
  margin: 0;
  font-size: 13px;
  color: #6b7689;
}

/* ---------- 表单（交互稳定优先：仅 focus ring，无装饰动效） ---------- */
:deep(.el-form-item) {
  width: 100%;
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  color: #3c4759;
}

:deep(.el-form-item__content) {
  width: 100%;
  line-height: 1.5;
}

:deep(.el-input) {
  width: 100%;
}

:deep(.el-input__wrapper) {
  padding: 4px 14px;
  border-radius: 10px;
  background: #f4f6f9;
  box-shadow: 0 0 0 1px #e2e7ee inset;
  transition: all 0.25s ease;
  box-sizing: border-box;
  width: 100%;
}

:deep(.el-input__wrapper:hover) {
  background: #fff;
  box-shadow: 0 0 0 1px #cdd6e0 inset;
}

:deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 2px #1e8bff inset, 0 4px 12px rgba(30, 139, 255, 0.14);
}

:deep(.el-input__inner) {
  height: 42px;
  font-size: 14px;
  color: #14203a;
}

:deep(.el-input__inner::placeholder) {
  color: #9aa5b5;
}

:deep(.el-input__prefix-inner) {
  font-size: 16px;
  color: #9aa5b5;
  margin-right: 6px;
}

:deep(.el-input__suffix-inner) {
  font-size: 16px;
  color: #9aa5b5;
  margin-left: 4px;
}

:deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 10px;
}

/* 忘记密码：真实指引（系统未提供自助找回，联系管理员为唯一路径） */
.forgot-hint {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #8a94a6;
}

.forgot-hint .el-icon {
  font-size: 13px;
}

/* ---------- 验证码 ---------- */
.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.captcha-row :deep(.el-input) {
  flex: 1;
  min-width: 0;
}

.captcha-image-box {
  flex-shrink: 0;
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 42px;
  border-radius: 10px;
  background: #f4f6f9;
  border: 1px solid #e2e7ee;
  cursor: pointer;
  user-select: none;
  transition: all 0.25s ease;
  overflow: hidden;
}

.captcha-image-box:hover {
  border-color: #1e8bff;
  transform: translateY(-1px);
  box-shadow: var(--glow-primary);
}

.captcha-image-box:focus-visible {
  outline: 2px solid #1e8bff;
  outline-offset: 2px;
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.captcha-loading {
  color: #9aa5b5;
  font-weight: 400;
  font-size: 12px;
}

.captcha-refresh {
  position: absolute;
  right: 4px;
  bottom: 4px;
  font-size: 14px;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 4px;
  padding: 1px;
  opacity: 0;
  transition: all 0.3s ease;
}

.captcha-image-box:hover .captcha-refresh {
  opacity: 1;
  transform: rotate(180deg);
}

/* ---------- 登录按钮：全站光晕 Token ---------- */
.login-btn {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #1e8bff 0%, #0d6bd8 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition:
    transform var(--dur-base) var(--ease-out-quart),
    box-shadow var(--dur-base) var(--ease-out-quart);
  box-shadow: var(--glow-primary);
}

.login-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s ease;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px -4px rgba(30, 139, 255, 0.48);
}

.login-btn:hover:not(:disabled)::before {
  left: 100%;
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.login-btn:focus-visible {
  outline: 2px solid #0d6bd8;
  outline-offset: 3px;
}

.login-btn.is-loading {
  background: linear-gradient(135deg, #69b1ff 0%, #409ef0 100%);
  cursor: not-allowed;
}

.btn-content {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.btn-icon {
  font-size: 16px;
  transition: transform var(--dur-base) var(--ease-out-quart);
}

.login-btn:hover .btn-icon {
  transform: translateX(4px);
}

.btn-loading {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------- 开发环境提示 ---------- */
.login-tip {
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f4ff 100%);
  border: 1px solid #bae0ff;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 12px;
  color: #0d6bd8;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

/* ---------- 版权 ---------- */
.login-footer {
  text-align: center;
  font-size: 11px;
  color: #a8b1c0;
  padding: 0 46px 22px;
}

/* ============ 响应式 ============ */
@media (max-width: 992px) {
  .login-box {
    flex-direction: column;
    min-height: 0;
    max-height: 92vh;
    overflow-y: auto;
  }

  .login-stage {
    width: 100%;
  }

  .stage-content {
    padding: 36px 32px 30px;
    gap: 26px;
  }

  /* 移动端压缩叙事：隐藏特性描述，仅保留标题行 */
  .feature-chip {
    padding: 10px 14px;
  }

  .stage-rings {
    width: 180px;
    height: 180px;
    right: -50px;
    bottom: -50px;
  }

  .login-panel {
    width: 100%;
  }

  .panel-inner {
    padding: 34px 32px 20px;
  }

  .login-footer {
    padding: 0 32px 18px;
  }
}

@media (max-width: 576px) {
  .login-container {
    padding: 14px;
    align-items: flex-start;
  }

  .login-box {
    border-radius: 16px;
  }

  .stage-content {
    padding: 28px 22px 24px;
    gap: 20px;
  }

  /* 小屏：特性折叠为横向可滚动芯片行，保住首屏表单位置 */
  .stage-features {
    flex-direction: row;
    overflow-x: auto;
    gap: 10px;
    padding-bottom: 4px;
    scrollbar-width: thin;
  }

  .feature-chip {
    flex-shrink: 0;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding: 10px 12px;
    max-width: 190px;
  }

  .feature-desc {
    display: none;
  }

  .stage-metrics {
    flex-wrap: wrap;
  }

  .metric-chip {
    min-width: calc(50% - 6px);
  }

  .metric-value {
    font-size: 20px;
  }

  .stage-brand h1 {
    font-size: 19px;
  }

  .brand-logo {
    width: 52px;
    height: 52px;
  }

  .panel-inner {
    padding: 26px 22px 16px;
  }

  .panel-header h2 {
    font-size: 21px;
  }

  .login-footer {
    padding: 0 22px 14px;
  }
}

/* 平板竖屏 */
@media (min-width: 577px) and (max-width: 768px) and (orientation: portrait) {
  .stage-content {
    gap: 22px;
  }

  .feature-desc {
    font-size: 11px;
  }
}

/* 减弱动态：只保留静态呈现（全局 token 已处理 aurora-blob，这里覆盖本页动画） */
@media (prefers-reduced-motion: reduce) {
  .feature-chip {
    animation: none;
  }

  .metric-value.num {
    animation: none;
  }

  .login-btn,
  .login-btn::before,
  .btn-icon,
  .captcha-image-box,
  .feature-chip {
    transition: none;
  }
}
</style>
