<template>
  <!--
    仪表盘欢迎横幅组件（P1-2.1 拆分自 Dashboard.vue；2026-08 大气·光晕重设计）
    父组件通过 :nickname / :subtitle / :stats 传入展示内容；
    stats 为数组，每项 { value, label, color? }，按顺序渲染。
    角色差异由父组件计算后传入，本组件不感知角色。
    视觉：机房光缆辉光 —— 墨蓝深色舞台区 + 三枚呼吸光斑（青/蓝/紫）+
    颗粒噪点压住渐变塑料感 + 细蓝图网格保留工程语境，指标为玻璃芯片。
  -->
  <section class="welcome-banner fade-up-enter">
    <!-- 装饰层（纯装饰，屏幕阅读器隐藏）：光斑 / 颗粒 / 蓝图网格 / 等高线圆环 -->
    <div
      class="aurora-blob aurora-blob--cyan aurora-blob--anim"
      aria-hidden="true"
    />
    <div
      class="aurora-blob aurora-blob--blue aurora-blob--anim"
      aria-hidden="true"
    />
    <div
      class="aurora-blob aurora-blob--violet aurora-blob--anim"
      aria-hidden="true"
    />
    <div
      class="grain-overlay"
      aria-hidden="true"
    />
    <div
      class="banner-grid"
      aria-hidden="true"
    />
    <div
      class="banner-rings"
      aria-hidden="true"
    >
      <span /><span /><span />
    </div>

    <div class="welcome-content">
      <div class="welcome-text">
        <p class="welcome-greeting">
          {{ greeting }}
        </p>
        <h2 class="welcome-title">
          欢迎回来，{{ nickname }}
        </h2>
        <p class="welcome-subtitle">
          {{ subtitle }}
        </p>
      </div>
      <div
        v-if="stats.length"
        class="welcome-stats"
      >
        <div
          v-for="(stat, idx) in stats"
          :key="idx"
          class="welcome-stat"
        >
          <div
            class="welcome-stat-value num"
            :style="stat.color ? { color: stat.color } : {}"
          >
            {{ stat.value }}
          </div>
          <div class="welcome-stat-label">
            {{ stat.label }}
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
/**
 * 仪表盘欢迎横幅（大气·光晕版）
 * 业务用途：登录后顶部展示个性化欢迎语 + 3~4 个核心指标，按角色差异化展示。
 * 数据来源：所有展示数据由父组件按角色计算后通过 props 传入，本组件不直接访问任何 store。
 * 视觉依据：global.css 的 Glow+Grain Token（--aurora-* / .grain-overlay / .aurora-blob）。
 */
import { computed } from 'vue'

defineProps({
  /** 用户昵称（用于「欢迎回来，xxx」文案） */
  nickname: { type: String, default: '' },
  /** 角色对应的欢迎副标题（含日期） */
  subtitle: { type: String, default: '' },
  /** 角色对应的核心指标数组，每项 { value, label, color? } */
  stats: { type: Array, default: () => [] }
})

/** 按当前时段生成问候语（替代原 emoji 挥手，无障碍友好） */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了，注意休息'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
</script>

<style scoped>
.welcome-banner {
  position: relative;
  overflow: hidden;
  padding: 28px 32px;
  margin-bottom: 20px;
  border-radius: var(--radius-lg);
  /* 深色舞台区：墨蓝基底（非纯黑），承接光斑辉光 */
  background: linear-gradient(118deg, #081833 0%, #0d2c5c 55%, #10386e 100%);
  color: #fff;
  box-shadow: 0 10px 34px -10px rgba(10, 30, 63, 0.55);
}

/* ===== 呼吸光斑：三枚大 blur 圆（青/蓝/紫），错峰呼吸 ===== */
/* 透明度写入背景色而非元素 opacity，避免与 glowPulse 的 opacity 动画叠加 */
.aurora-blob--cyan {
  width: 340px;
  height: 340px;
  top: -160px;
  right: 18%;
  background: rgba(34, 211, 238, 0.16);
  animation-delay: 0s;
}

.aurora-blob--blue {
  width: 420px;
  height: 420px;
  bottom: -240px;
  left: 24%;
  background: rgba(30, 139, 255, 0.22);
  animation-delay: -2.4s;
}

.aurora-blob--violet {
  width: 260px;
  height: 260px;
  bottom: -110px;
  right: -70px;
  background: rgba(139, 124, 246, 0.14);
  animation-delay: -4.8s;
}

/* 蓝图网格：细白线网格，弱化保留（工程语境连续性），右下淡出 */
.banner-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 26px 26px;
  -webkit-mask-image: linear-gradient(115deg, rgba(0, 0, 0, 0.85) 30%, rgba(0, 0, 0, 0.2) 78%);
  mask-image: linear-gradient(115deg, rgba(0, 0, 0, 0.85) 30%, rgba(0, 0, 0, 0.2) 78%);
}

/* 等高线圆环：右下角三圈同心描边圆，工程图纸意象 */
.banner-rings {
  position: absolute;
  right: -60px;
  bottom: -110px;
  width: 280px;
  height: 280px;
}

.banner-rings span {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.banner-rings span:nth-child(1) { inset: 0; }
.banner-rings span:nth-child(2) { inset: 38px; border-color: rgba(255, 255, 255, 0.09); }
.banner-rings span:nth-child(3) { inset: 78px; border-color: rgba(255, 255, 255, 0.06); }

.welcome-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
}

.welcome-text {
  min-width: 0;
}

.welcome-greeting {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 2px;
  opacity: 0.72;
  margin-bottom: 6px;
}

.welcome-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.5px;
  line-height: 1.25;
  margin-bottom: 8px;
  /* 标题光感：细白描边提亮（深底上的柔和高光） */
  text-shadow: 0 1px 12px rgba(120, 190, 255, 0.25);
}

.welcome-subtitle {
  font-size: 13px;
  opacity: 0.8;
  line-height: 1.5;
}

/* 指标芯片：玻璃质感（半透 + blur + 亮边），辉光氛围中的悬浮玻璃 */
.welcome-stats {
  display: flex;
  align-items: stretch;
  gap: 14px;
  flex-shrink: 0;
}

.welcome-stat {
  min-width: 92px;
  padding: 12px 18px;
  text-align: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.09);
  border: 1px solid rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  transition: background var(--dur-base), transform var(--dur-base), box-shadow var(--dur-base);
}

.welcome-stat:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateY(-1px);
  /* hover 泛出品牌蓝辉光（与 StatCard 光晕语言呼应） */
  box-shadow: 0 0 24px -6px rgba(64, 169, 255, 0.45);
}

.welcome-stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.welcome-stat-label {
  font-size: 12px;
  opacity: 0.8;
  margin-top: 3px;
  white-space: nowrap;
}

/* 窄屏：指标芯片换行铺满，避免与文案挤压 */
@media screen and (max-width: 992px) {
  .welcome-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 18px;
  }

  .welcome-stats {
    width: 100%;
    flex-wrap: wrap;
  }

  .welcome-stat {
    flex: 1;
    min-width: 84px;
  }
}

@media screen and (max-width: 768px) {
  .welcome-banner {
    padding: 20px;
  }
}
</style>
