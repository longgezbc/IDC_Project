import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue(),
      // 生产环境压缩 HTML
      mode === 'production' && {
        name: 'html-minify',
        transformIndexHtml(html) {
          return html.replace(/\s+/g, ' ').replace(/>\s+</g, '><')
        }
      }
    ].filter(Boolean),
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        // 跨端共享模块：与小程序共用的纯函数放这里，避免双端重复维护
        '@shared': fileURLToPath(new URL('../shared', import.meta.url))
      }
    },
    server: {
      port: 5173,
      open: true,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        },
        // WebSocket 代理（站内通知实时推送）
        '/ws': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          ws: true
        }
      }
    },
    build: {
      // 输出目录
      outDir: 'dist',
      // 静态资源目录
      assetsDir: 'assets',
      // 生产环境压缩：移除 debugger 与日志类 console 调用
      minify: 'terser',
      terserOptions: {
        compress: {
          // P2 修复：原 drop_console: true 会把 console.error/warn 一并移除，
          // 与 websocket.js（"error 始终输出，便于线上排查"）、errorReporter.js、
          // stores/user.js 等处的关键错误日志意图矛盾，线上排障困难。
          // 改用 pure_funcs 仅移除日志类调用，保留 error/warn。
          drop_console: false,
          pure_funcs: ['console.log', 'console.info', 'console.debug'],
          drop_debugger: mode === 'production'
        }
      },
      // 生产环境不生成 sourcemap，减小包体积
      sourcemap: mode !== 'production',
      // chunk 大小警告阈值（KB）
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          // 入口文件名
          entryFileNames: 'assets/[name]-[hash].js',
          // 代码分割后的文件名
          chunkFileNames: 'assets/[name]-[hash].js',
          // 静态资源文件名
          assetFileNames: 'assets/[name]-[hash].[ext]',
          // 手动代码分割，将第三方依赖单独打包
          manualChunks: {
            // Vue 核心库
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            // Element Plus UI 库
            'element-vendor': ['element-plus', '@element-plus/icons-vue'],
            // 图表库
            'echarts-vendor': ['echarts'],
            // 工具库
            'utils-vendor': ['axios', 'dayjs']
          }
        }
      }
    },
    // 依赖预构建优化
    optimizeDeps: {
      include: [
        'vue',
        'vue-router',
        'pinia',
        'axios',
        'dayjs',
        'element-plus',
        '@element-plus/icons-vue',
        'echarts',
        'sockjs-client',
        'stompjs'
      ]
    }
  }
})
