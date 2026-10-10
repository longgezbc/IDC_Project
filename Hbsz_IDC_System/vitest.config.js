import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vitest 测试配置（与 vite.config.js 分离，避免继承 dev server / 构建优化等无关配置）
 *
 * 说明：
 *   - environment: happy-dom —— 提供 localStorage（cryptoStorage 依赖）与 Blob；
 *   - plugins: @vitejs/plugin-vue —— 组件测试（*.test.js 挂载 .vue）需解析 SFC，
 *     纯 js 测试不受影响（与 vite.config.js 的插件保持一致，不含 html-minify）；
 *   - 别名与 vite.config.js 保持一致（@ → src，@shared → ../shared）；
 *   - 仅收集 src 下的 *.test.js / *.spec.js。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared': fileURLToPath(new URL('../shared', import.meta.url))
    }
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.{test,spec}.js'],
    // 默认不监听文件变化（CI 友好），本地调试可用 vitest --watch
    watch: false
  }
})
