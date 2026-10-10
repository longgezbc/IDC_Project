<template>
  <div class="page-container app-download">
    <!-- 下载主卡片：深蓝舞台（与登录页/WelcomeBanner 同源 Glow+Grain） -->
    <section class="dl-hero">
      <div
        class="aurora-blob aurora-blob--cyan aurora-blob--anim hero-blob hero-blob--c"
        aria-hidden="true"
      />
      <div
        class="aurora-blob aurora-blob--blue aurora-blob--anim hero-blob hero-blob--b"
        aria-hidden="true"
      />
      <div
        class="grain-overlay"
        aria-hidden="true"
      />
      <div
        class="hero-grid"
        aria-hidden="true"
      />

      <div class="hero-content">
        <!-- App 图标与信息 -->
        <div class="hero-app">
          <img
            :src="logoImg"
            class="app-icon"
            alt="App 图标"
          >
          <div class="app-info">
            <h1>移动端 App（Android）</h1>
            <p class="app-eyebrow">
              HBSZ IDC · MOBILE CLIENT
            </p>
            <div class="app-meta">
              <span class="meta-chip">
                <span class="num">{{ versionText }}</span>
                <span class="meta-label">版本</span>
              </span>
              <span class="meta-chip">
                <span class="num">{{ sizeText }}</span>
                <span class="meta-label">大小</span>
              </span>
              <span class="meta-chip">
                <span class="num">{{ dateText }}</span>
                <span class="meta-label">更新日期</span>
              </span>
            </div>
          </div>
        </div>

        <!-- 功能亮点：玻璃芯片 -->
        <ul class="hero-features">
          <li
            v-for="(f, i) in features"
            :key="i"
            class="feature-chip"
          >
            <span class="feature-icon">
              <el-icon><component :is="f.icon" /></el-icon>
            </span>
            <span class="feature-text">{{ f.text }}</span>
          </li>
        </ul>
      </div>

      <!-- 右侧二维码区（舞台内白底卡片，保证扫码识别率） -->
      <div class="hero-qr">
        <div class="qr-card">
          <canvas
            ref="qrCanvasRef"
            class="qr-canvas"
            aria-label="App 下载二维码"
            role="img"
          />
          <p class="qr-tip">
            手机扫码直接下载
          </p>
        </div>
      </div>
    </section>

    <!-- 操作与指引区 -->
    <el-row
      :gutter="20"
      class="action-row"
    >
      <el-col
        :xs="24"
        :md="14"
      >
        <el-card
          class="action-card"
          shadow="never"
        >
          <template #header>
            <div class="card-title">
              <el-icon><Download /></el-icon>
              <span>下载安装</span>
            </div>
          </template>
          <div class="download-zone">
            <BaseButton
              type="primary"
              size="large"
              :loading="downloading"
              aria-label="下载 Android 安装包"
              @click="handleDownload"
            >
              <el-icon style="margin-right: 6px">
                <Download />
              </el-icon>
              下载安装包（{{ sizeText }}）
            </BaseButton>
            <div class="download-url num">
              {{ downloadUrl }}
            </div>
            <el-alert
              type="info"
              :closable="false"
              class="env-tip"
            >
              <p>当前访问地址：{{ origin }}（下载链接与访问域名一致，手机与电脑需在同一网络环境）</p>
            </el-alert>
          </div>
        </el-card>
      </el-col>

      <el-col
        :xs="24"
        :md="10"
      >
        <el-card
          class="action-card"
          shadow="never"
        >
          <template #header>
            <div class="card-title">
              <el-icon><Guide /></el-icon>
              <span>安装指引</span>
            </div>
          </template>
          <ol class="guide-steps">
            <li>
              <span class="step-num num">1</span>
              <span>下载安装包后点击打开，首次安装需允许「未知来源应用」权限</span>
            </li>
            <li>
              <span class="step-num num">2</span>
              <span>已安装 v1.0.0 正式版的设备可直接覆盖升级（同签名）；开发调试版需先卸载（签名不同）</span>
            </li>
            <li>
              <span class="step-num num">3</span>
              <span>安装完成后使用系统账号登录，与 Web 端数据实时同步</span>
            </li>
          </ol>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
/**
 * App 下载中心
 * 业务用途：提供 Android 客户端的下载入口（二维码 + 直接下载按钮），
 *           移动端使用频率高的工程队成员可扫码安装，现场填报更便捷。
 * 数据来源：安装包由服务器 Nginx 静态目录 /downloads/ 提供（前端仅负责展示）；
 *           下载地址按当前访问域名动态拼接，保证与用户实际网络环境一致。
 * 版本维护：版本号/APK 地址/体积/日期在运行时从 /downloads/app-latest.json
 *           清单与安装包响应头动态读取（与 App 自更新共用同一清单），
 *           升级 APK 时仅需更新服务器清单与安装包文件，本页无需改代码重建；
 *           APP_INFO 常量仅作清单不可达时的兜底展示。
 */
import { ref, computed, onMounted } from 'vue'
import QRCode from 'qrcode'
import { feedback } from '@/utils/feedback'
import { Download, Guide } from '@element-plus/icons-vue'
import logoImg from '@/static/logo/8da4edb7bc99f55c525d4722e1db8644.jpg'

/** App 版本信息内置默认值（清单不可达时的兜底展示；
 *  升级 APK 后仅需更新服务器 /downloads/app-latest.json，本页自动跟随，无需改代码重建） */
const APP_INFO = {
  fileName: 'Hbsz_IDC_v1.0.2_20260822.apk',
  version: 'v1.0.2',
  size: '3.2MB',
  date: '2026-08-22'
}

/** 响应式版本信息（onMounted 时从服务器清单与 APK 响应头刷新） */
const apkUrl = ref(`/downloads/${APP_INFO.fileName}`)
const versionText = ref(APP_INFO.version)
const sizeText = ref(APP_INFO.size)
const dateText = ref(APP_INFO.date)

/** 功能亮点（与 Android 端实际能力对应，不夸大） */
const features = [
  { icon: 'EditPen', text: '工作量快速填报（常用项卡片 + 复制昨天）' },
  { icon: 'Bell', text: '审批待办与项目通知实时推送' },
  { icon: 'Fingerprint', text: '指纹快速登录 · 在线检查新版本' },
  { icon: 'DataAnalysis', text: '项目进度与统计随时查看' }
]

const qrCanvasRef = ref(null)
const downloading = ref(false)

/** 当前访问源（扫码与直链的基础） */
const origin = typeof window !== 'undefined' ? window.location.origin : ''

/** 下载地址：与当前访问域名同源，手机扫码后浏览器直接下载 */
const downloadUrl = computed(() => `${origin}${apkUrl.value}`)

/**
 * 从服务器版本清单 /downloads/app-latest.json 动态读取版本号与 APK 地址，
 * 再以 HEAD 探测安装包真实体积与更新时间；任一步失败静默回退内置默认值。
 * 好处：后续升级 APK 只需替换服务器清单与安装包，本页展示与下载链接自动跟随。
 */
async function loadManifest() {
  try {
    const res = await fetch('/downloads/app-latest.json', { cache: 'no-cache' })
    if (res.ok) {
      const m = await res.json()
      if (m.versionName) versionText.value = `v${m.versionName}`
      if (m.apkUrl) apkUrl.value = m.apkUrl
    }
  } catch (e) {
    console.warn('读取版本清单失败，使用内置默认信息:', e)
  }
  try {
    const head = await fetch(apkUrl.value, { method: 'HEAD' })
    if (head.ok) {
      const len = Number(head.headers.get('content-length'))
      if (len > 0) sizeText.value = `${(len / 1024 / 1024).toFixed(2)}MB`
      const lastModified = head.headers.get('last-modified')
      if (lastModified) {
        const d = new Date(lastModified)
        const pad = (n) => String(n).padStart(2, '0')
        dateText.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
      }
    }
  } catch (e) {
    console.warn('探测安装包信息失败:', e)
  }
}

/** 先加载清单（下载链接就绪）再生成二维码，保证二维码指向最新版本安装包 */
onMounted(async () => {
  await loadManifest()
  if (!qrCanvasRef.value) return
  try {
    await QRCode.toCanvas(qrCanvasRef.value, downloadUrl.value, {
      width: 200,
      margin: 2,
      errorCorrectionLevel: 'M',
      color: { dark: '#0d2c5c', light: '#ffffff' }
    })
  } catch (e) {
    console.error('二维码生成失败:', e)
    feedback.error('二维码生成失败，请使用下方按钮直接下载')
  }
})

/** 触发浏览器下载（a 标签跳转，浏览器按 octet-stream 直接触发保存） */
function handleDownload() {
  downloading.value = true
  try {
    const a = document.createElement('a')
    a.href = downloadUrl.value
    a.download = apkUrl.value.split('/').pop()
    document.body.appendChild(a)
    a.click()
    a.remove()
    feedback.success('开始下载，请在浏览器下载列表中查看')
  } catch (e) {
    console.error('下载失败:', e)
    feedback.error('下载失败，可复制链接到新标签页打开')
  } finally {
    // 仅表示指令已发出（大文件下载进度由浏览器接管）
    setTimeout(() => { downloading.value = false }, 800)
  }
}
</script>

<style scoped>
/* ==================================================================
   App 下载中心 · Glow+Grain（与登录页/WelcomeBanner 同源 Token）
   ================================================================== */
.app-download {
  max-width: 1080px;
  margin: 0 auto;
}

/* ---------- 主卡片：深蓝舞台 ---------- */
.dl-hero {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 24px;
  padding: 40px 44px;
  border-radius: 16px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(118deg, #081833 0%, #0d2c5c 55%, #10386e 100%);
  box-shadow: 0 12px 40px rgba(8, 24, 51, 0.35);
  animation: fadeUpBlur var(--dur-slow) var(--ease-out-quart) backwards;
}

/* 舞台光斑（颜色对齐 WelcomeBanner scoped 配色） */
.hero-blob {
  position: absolute;
  filter: blur(70px);
}

.hero-blob--c {
  width: 340px;
  height: 340px;
  top: -140px;
  left: -100px;
  background: rgba(34, 211, 238, 0.16);
}

.hero-blob--b {
  width: 420px;
  height: 420px;
  bottom: -180px;
  right: -120px;
  background: rgba(30, 139, 255, 0.22);
}

/* 蓝图网格 */
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 26px 26px;
  -webkit-mask-image: linear-gradient(115deg, rgba(0, 0, 0, 0.8) 30%, rgba(0, 0, 0, 0.15) 75%);
  mask-image: linear-gradient(115deg, rgba(0, 0, 0, 0.8) 30%, rgba(0, 0, 0, 0.15) 75%);
}

.hero-content {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
  min-width: 0;
}

/* App 信息区 */
.hero-app {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.app-icon {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  object-fit: cover;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  flex-shrink: 0;
}

.hero-app h1 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.app-eyebrow {
  margin: 0 0 14px;
  font-family: var(--font-numeric);
  font-size: 10px;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: rgba(140, 190, 255, 0.85);
}

.app-meta {
  display: flex;
  gap: 10px;
}

.meta-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.14);
}

.meta-chip .num {
  font-size: 15px;
  font-weight: 700;
  color: #7fd4ff;
}

.meta-label {
  font-size: 11px;
  color: rgba(214, 230, 255, 0.7);
}

/* 功能亮点：玻璃芯片行 */
.hero-features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.feature-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(6px);
  font-size: 12.5px;
  color: rgba(226, 238, 255, 0.92);
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  font-size: 13px;
  color: #8ec8ff;
  background: linear-gradient(135deg, rgba(30, 139, 255, 0.3), rgba(34, 211, 238, 0.18));
}

/* ---------- 右侧二维码 ---------- */
.hero-qr {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 10px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(4, 12, 30, 0.4);
}

.qr-canvas {
  display: block;
  border-radius: 4px;
}

.qr-tip {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: #14203a;
}

/* ---------- 操作与指引区 ---------- */
.action-row {
  margin-top: 20px;
}

.action-card {
  height: 100%;
  border-radius: 12px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.card-title .el-icon {
  color: var(--primary-color);
}

.download-zone {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.download-url {
  padding: 10px 14px;
  border-radius: 8px;
  background: #f4f6f9;
  border: 1px solid #e2e7ee;
  font-size: 12.5px;
  color: #3c4759;
  word-break: break-all;
  user-select: all;
}

.env-tip {
  margin: 0;
}

.env-tip :deep(p) {
  margin: 0;
  line-height: 1.6;
  font-size: 12.5px;
}

/* 安装指引步骤 */
.guide-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.guide-steps li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font-size: 13.5px;
  line-height: 1.7;
  color: #3c4759;
}

.step-num {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-top: 2px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #1e8bff, #0d6bd8);
}

/* ---------- 响应式 ---------- */
@media (max-width: 768px) {
  .dl-hero {
    flex-direction: column;
    padding: 28px 24px;
    gap: 22px;
  }

  .hero-qr {
    justify-content: center;
  }

  .hero-app {
    flex-direction: column;
    gap: 14px;
  }

  .app-meta {
    flex-wrap: wrap;
  }

  .hero-features {
    flex-direction: column;
  }
}

/* 减弱动态 */
@media (prefers-reduced-motion: reduce) {
  .dl-hero {
    animation: none;
  }
}
</style>
