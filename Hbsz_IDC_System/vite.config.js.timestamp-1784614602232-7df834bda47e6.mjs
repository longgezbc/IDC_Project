// vite.config.js
import { defineConfig, loadEnv } from "file:///C:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/node_modules/vite/dist/node/index.js";
import vue from "file:///C:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import { fileURLToPath, URL } from "node:url";
var __vite_injected_original_import_meta_url = "file:///C:/Users/QianLong/Desktop/Hbsz/Hbsz_IDC_System/vite.config.js";
var vite_config_default = defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [
      vue(),
      // 生产环境压缩 HTML
      mode === "production" && {
        name: "html-minify",
        transformIndexHtml(html) {
          return html.replace(/\s+/g, " ").replace(/>\s+</g, "><");
        }
      }
    ].filter(Boolean),
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", __vite_injected_original_import_meta_url)),
        // 跨端共享模块：与小程序共用的纯函数放这里，避免双端重复维护
        "@shared": fileURLToPath(new URL("../shared", __vite_injected_original_import_meta_url))
      }
    },
    server: {
      port: 5173,
      open: true,
      proxy: {
        "/api": {
          target: "http://localhost:8080",
          changeOrigin: true
        },
        // WebSocket 代理（站内通知实时推送）
        "/ws": {
          target: "http://localhost:8080",
          changeOrigin: true,
          ws: true
        }
      }
    },
    build: {
      // 输出目录
      outDir: "dist",
      // 静态资源目录
      assetsDir: "assets",
      // 生产环境去除 console 和 debugger
      minify: "terser",
      terserOptions: {
        compress: {
          drop_console: mode === "production",
          drop_debugger: mode === "production"
        }
      },
      // 生产环境不生成 sourcemap，减小包体积
      sourcemap: mode !== "production",
      // chunk 大小警告阈值（KB）
      chunkSizeWarningLimit: 1e3,
      rollupOptions: {
        output: {
          // 入口文件名
          entryFileNames: "assets/[name]-[hash].js",
          // 代码分割后的文件名
          chunkFileNames: "assets/[name]-[hash].js",
          // 静态资源文件名
          assetFileNames: "assets/[name]-[hash].[ext]",
          // 手动代码分割，将第三方依赖单独打包
          manualChunks: {
            // Vue 核心库
            "vue-vendor": ["vue", "vue-router", "pinia"],
            // Element Plus UI 库
            "element-vendor": ["element-plus", "@element-plus/icons-vue"],
            // 图表库
            "echarts-vendor": ["echarts"],
            // 工具库
            "utils-vendor": ["axios", "dayjs"]
          }
        }
      }
    },
    // 依赖预构建优化
    optimizeDeps: {
      include: [
        "vue",
        "vue-router",
        "pinia",
        "axios",
        "dayjs",
        "element-plus",
        "@element-plus/icons-vue",
        "echarts",
        "sockjs-client",
        "stompjs"
      ]
    }
  };
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcuanMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJDOlxcXFxVc2Vyc1xcXFxRaWFuTG9uZ1xcXFxEZXNrdG9wXFxcXEhic3pcXFxcSGJzel9JRENfU3lzdGVtXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ZpbGVuYW1lID0gXCJDOlxcXFxVc2Vyc1xcXFxRaWFuTG9uZ1xcXFxEZXNrdG9wXFxcXEhic3pcXFxcSGJzel9JRENfU3lzdGVtXFxcXHZpdGUuY29uZmlnLmpzXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ltcG9ydF9tZXRhX3VybCA9IFwiZmlsZTovLy9DOi9Vc2Vycy9RaWFuTG9uZy9EZXNrdG9wL0hic3ovSGJzel9JRENfU3lzdGVtL3ZpdGUuY29uZmlnLmpzXCI7aW1wb3J0IHsgZGVmaW5lQ29uZmlnLCBsb2FkRW52IH0gZnJvbSAndml0ZSdcbmltcG9ydCB2dWUgZnJvbSAnQHZpdGVqcy9wbHVnaW4tdnVlJ1xuaW1wb3J0IHsgZmlsZVVSTFRvUGF0aCwgVVJMIH0gZnJvbSAnbm9kZTp1cmwnXG5cbmV4cG9ydCBkZWZhdWx0IGRlZmluZUNvbmZpZygoeyBtb2RlIH0pID0+IHtcbiAgLy8gXHU1MkEwXHU4RjdEXHU3M0FGXHU1ODgzXHU1M0Q4XHU5MUNGXG4gIGNvbnN0IGVudiA9IGxvYWRFbnYobW9kZSwgcHJvY2Vzcy5jd2QoKSwgJycpXG5cbiAgcmV0dXJuIHtcbiAgICBwbHVnaW5zOiBbXG4gICAgICB2dWUoKSxcbiAgICAgIC8vIFx1NzUxRlx1NEVBN1x1NzNBRlx1NTg4M1x1NTM4Qlx1N0YyOSBIVE1MXG4gICAgICBtb2RlID09PSAncHJvZHVjdGlvbicgJiYge1xuICAgICAgICBuYW1lOiAnaHRtbC1taW5pZnknLFxuICAgICAgICB0cmFuc2Zvcm1JbmRleEh0bWwoaHRtbCkge1xuICAgICAgICAgIHJldHVybiBodG1sLnJlcGxhY2UoL1xccysvZywgJyAnKS5yZXBsYWNlKC8+XFxzKzwvZywgJz48JylcbiAgICAgICAgfVxuICAgICAgfVxuICAgIF0uZmlsdGVyKEJvb2xlYW4pLFxuICAgIHJlc29sdmU6IHtcbiAgICAgIGFsaWFzOiB7XG4gICAgICAgICdAJzogZmlsZVVSTFRvUGF0aChuZXcgVVJMKCcuL3NyYycsIGltcG9ydC5tZXRhLnVybCkpLFxuICAgICAgICAvLyBcdThERThcdTdBRUZcdTUxNzFcdTRFQUJcdTZBMjFcdTU3NTdcdUZGMUFcdTRFMEVcdTVDMEZcdTdBMEJcdTVFOEZcdTUxNzFcdTc1MjhcdTc2ODRcdTdFQUZcdTUxRkRcdTY1NzBcdTY1M0VcdThGRDlcdTkxQ0NcdUZGMENcdTkwN0ZcdTUxNERcdTUzQ0NcdTdBRUZcdTkxQ0RcdTU5MERcdTdFRjRcdTYyQTRcbiAgICAgICAgJ0BzaGFyZWQnOiBmaWxlVVJMVG9QYXRoKG5ldyBVUkwoJy4uL3NoYXJlZCcsIGltcG9ydC5tZXRhLnVybCkpXG4gICAgICB9XG4gICAgfSxcbiAgICBzZXJ2ZXI6IHtcbiAgICAgIHBvcnQ6IDUxNzMsXG4gICAgICBvcGVuOiB0cnVlLFxuICAgICAgcHJveHk6IHtcbiAgICAgICAgJy9hcGknOiB7XG4gICAgICAgICAgdGFyZ2V0OiAnaHR0cDovL2xvY2FsaG9zdDo4MDgwJyxcbiAgICAgICAgICBjaGFuZ2VPcmlnaW46IHRydWVcbiAgICAgICAgfSxcbiAgICAgICAgLy8gV2ViU29ja2V0IFx1NEVFM1x1NzQwNlx1RkYwOFx1N0FEOVx1NTE4NVx1OTAxQVx1NzdFNVx1NUI5RVx1NjVGNlx1NjNBOFx1OTAwMVx1RkYwOVxuICAgICAgICAnL3dzJzoge1xuICAgICAgICAgIHRhcmdldDogJ2h0dHA6Ly9sb2NhbGhvc3Q6ODA4MCcsXG4gICAgICAgICAgY2hhbmdlT3JpZ2luOiB0cnVlLFxuICAgICAgICAgIHdzOiB0cnVlXG4gICAgICAgIH1cbiAgICAgIH1cbiAgICB9LFxuICAgIGJ1aWxkOiB7XG4gICAgICAvLyBcdThGOTNcdTUxRkFcdTc2RUVcdTVGNTVcbiAgICAgIG91dERpcjogJ2Rpc3QnLFxuICAgICAgLy8gXHU5NzU5XHU2MDAxXHU4RDQ0XHU2RTkwXHU3NkVFXHU1RjU1XG4gICAgICBhc3NldHNEaXI6ICdhc3NldHMnLFxuICAgICAgLy8gXHU3NTFGXHU0RUE3XHU3M0FGXHU1ODgzXHU1M0JCXHU5NjY0IGNvbnNvbGUgXHU1NDhDIGRlYnVnZ2VyXG4gICAgICBtaW5pZnk6ICd0ZXJzZXInLFxuICAgICAgdGVyc2VyT3B0aW9uczoge1xuICAgICAgICBjb21wcmVzczoge1xuICAgICAgICAgIGRyb3BfY29uc29sZTogbW9kZSA9PT0gJ3Byb2R1Y3Rpb24nLFxuICAgICAgICAgIGRyb3BfZGVidWdnZXI6IG1vZGUgPT09ICdwcm9kdWN0aW9uJ1xuICAgICAgICB9XG4gICAgICB9LFxuICAgICAgLy8gXHU3NTFGXHU0RUE3XHU3M0FGXHU1ODgzXHU0RTBEXHU3NTFGXHU2MjEwIHNvdXJjZW1hcFx1RkYwQ1x1NTFDRlx1NUMwRlx1NTMwNVx1NEY1M1x1NzlFRlxuICAgICAgc291cmNlbWFwOiBtb2RlICE9PSAncHJvZHVjdGlvbicsXG4gICAgICAvLyBjaHVuayBcdTU5MjdcdTVDMEZcdThCNjZcdTU0NEFcdTk2MDhcdTUwM0NcdUZGMDhLQlx1RkYwOVxuICAgICAgY2h1bmtTaXplV2FybmluZ0xpbWl0OiAxMDAwLFxuICAgICAgcm9sbHVwT3B0aW9uczoge1xuICAgICAgICBvdXRwdXQ6IHtcbiAgICAgICAgICAvLyBcdTUxNjVcdTUzRTNcdTY1ODdcdTRFRjZcdTU0MERcbiAgICAgICAgICBlbnRyeUZpbGVOYW1lczogJ2Fzc2V0cy9bbmFtZV0tW2hhc2hdLmpzJyxcbiAgICAgICAgICAvLyBcdTRFRTNcdTc4MDFcdTUyMDZcdTUyNzJcdTU0MEVcdTc2ODRcdTY1ODdcdTRFRjZcdTU0MERcbiAgICAgICAgICBjaHVua0ZpbGVOYW1lczogJ2Fzc2V0cy9bbmFtZV0tW2hhc2hdLmpzJyxcbiAgICAgICAgICAvLyBcdTk3NTlcdTYwMDFcdThENDRcdTZFOTBcdTY1ODdcdTRFRjZcdTU0MERcbiAgICAgICAgICBhc3NldEZpbGVOYW1lczogJ2Fzc2V0cy9bbmFtZV0tW2hhc2hdLltleHRdJyxcbiAgICAgICAgICAvLyBcdTYyNEJcdTUyQThcdTRFRTNcdTc4MDFcdTUyMDZcdTUyNzJcdUZGMENcdTVDMDZcdTdCMkNcdTRFMDlcdTY1QjlcdTRGOURcdThENTZcdTUzNTVcdTcyRUNcdTYyNTNcdTUzMDVcbiAgICAgICAgICBtYW51YWxDaHVua3M6IHtcbiAgICAgICAgICAgIC8vIFZ1ZSBcdTY4MzhcdTVGQzNcdTVFOTNcbiAgICAgICAgICAgICd2dWUtdmVuZG9yJzogWyd2dWUnLCAndnVlLXJvdXRlcicsICdwaW5pYSddLFxuICAgICAgICAgICAgLy8gRWxlbWVudCBQbHVzIFVJIFx1NUU5M1xuICAgICAgICAgICAgJ2VsZW1lbnQtdmVuZG9yJzogWydlbGVtZW50LXBsdXMnLCAnQGVsZW1lbnQtcGx1cy9pY29ucy12dWUnXSxcbiAgICAgICAgICAgIC8vIFx1NTZGRVx1ODg2OFx1NUU5M1xuICAgICAgICAgICAgJ2VjaGFydHMtdmVuZG9yJzogWydlY2hhcnRzJ10sXG4gICAgICAgICAgICAvLyBcdTVERTVcdTUxNzdcdTVFOTNcbiAgICAgICAgICAgICd1dGlscy12ZW5kb3InOiBbJ2F4aW9zJywgJ2RheWpzJ11cbiAgICAgICAgICB9XG4gICAgICAgIH1cbiAgICAgIH1cbiAgICB9LFxuICAgIC8vIFx1NEY5RFx1OEQ1Nlx1OTg4NFx1Njc4NFx1NUVGQVx1NEYxOFx1NTMxNlxuICAgIG9wdGltaXplRGVwczoge1xuICAgICAgaW5jbHVkZTogW1xuICAgICAgICAndnVlJyxcbiAgICAgICAgJ3Z1ZS1yb3V0ZXInLFxuICAgICAgICAncGluaWEnLFxuICAgICAgICAnYXhpb3MnLFxuICAgICAgICAnZGF5anMnLFxuICAgICAgICAnZWxlbWVudC1wbHVzJyxcbiAgICAgICAgJ0BlbGVtZW50LXBsdXMvaWNvbnMtdnVlJyxcbiAgICAgICAgJ2VjaGFydHMnLFxuICAgICAgICAnc29ja2pzLWNsaWVudCcsXG4gICAgICAgICdzdG9tcGpzJ1xuICAgICAgXVxuICAgIH1cbiAgfVxufSlcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBd1UsU0FBUyxjQUFjLGVBQWU7QUFDOVcsT0FBTyxTQUFTO0FBQ2hCLFNBQVMsZUFBZSxXQUFXO0FBRjRLLElBQU0sMkNBQTJDO0FBSWhRLElBQU8sc0JBQVEsYUFBYSxDQUFDLEVBQUUsS0FBSyxNQUFNO0FBRXhDLFFBQU0sTUFBTSxRQUFRLE1BQU0sUUFBUSxJQUFJLEdBQUcsRUFBRTtBQUUzQyxTQUFPO0FBQUEsSUFDTCxTQUFTO0FBQUEsTUFDUCxJQUFJO0FBQUE7QUFBQSxNQUVKLFNBQVMsZ0JBQWdCO0FBQUEsUUFDdkIsTUFBTTtBQUFBLFFBQ04sbUJBQW1CLE1BQU07QUFDdkIsaUJBQU8sS0FBSyxRQUFRLFFBQVEsR0FBRyxFQUFFLFFBQVEsVUFBVSxJQUFJO0FBQUEsUUFDekQ7QUFBQSxNQUNGO0FBQUEsSUFDRixFQUFFLE9BQU8sT0FBTztBQUFBLElBQ2hCLFNBQVM7QUFBQSxNQUNQLE9BQU87QUFBQSxRQUNMLEtBQUssY0FBYyxJQUFJLElBQUksU0FBUyx3Q0FBZSxDQUFDO0FBQUE7QUFBQSxRQUVwRCxXQUFXLGNBQWMsSUFBSSxJQUFJLGFBQWEsd0NBQWUsQ0FBQztBQUFBLE1BQ2hFO0FBQUEsSUFDRjtBQUFBLElBQ0EsUUFBUTtBQUFBLE1BQ04sTUFBTTtBQUFBLE1BQ04sTUFBTTtBQUFBLE1BQ04sT0FBTztBQUFBLFFBQ0wsUUFBUTtBQUFBLFVBQ04sUUFBUTtBQUFBLFVBQ1IsY0FBYztBQUFBLFFBQ2hCO0FBQUE7QUFBQSxRQUVBLE9BQU87QUFBQSxVQUNMLFFBQVE7QUFBQSxVQUNSLGNBQWM7QUFBQSxVQUNkLElBQUk7QUFBQSxRQUNOO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxJQUNBLE9BQU87QUFBQTtBQUFBLE1BRUwsUUFBUTtBQUFBO0FBQUEsTUFFUixXQUFXO0FBQUE7QUFBQSxNQUVYLFFBQVE7QUFBQSxNQUNSLGVBQWU7QUFBQSxRQUNiLFVBQVU7QUFBQSxVQUNSLGNBQWMsU0FBUztBQUFBLFVBQ3ZCLGVBQWUsU0FBUztBQUFBLFFBQzFCO0FBQUEsTUFDRjtBQUFBO0FBQUEsTUFFQSxXQUFXLFNBQVM7QUFBQTtBQUFBLE1BRXBCLHVCQUF1QjtBQUFBLE1BQ3ZCLGVBQWU7QUFBQSxRQUNiLFFBQVE7QUFBQTtBQUFBLFVBRU4sZ0JBQWdCO0FBQUE7QUFBQSxVQUVoQixnQkFBZ0I7QUFBQTtBQUFBLFVBRWhCLGdCQUFnQjtBQUFBO0FBQUEsVUFFaEIsY0FBYztBQUFBO0FBQUEsWUFFWixjQUFjLENBQUMsT0FBTyxjQUFjLE9BQU87QUFBQTtBQUFBLFlBRTNDLGtCQUFrQixDQUFDLGdCQUFnQix5QkFBeUI7QUFBQTtBQUFBLFlBRTVELGtCQUFrQixDQUFDLFNBQVM7QUFBQTtBQUFBLFlBRTVCLGdCQUFnQixDQUFDLFNBQVMsT0FBTztBQUFBLFVBQ25DO0FBQUEsUUFDRjtBQUFBLE1BQ0Y7QUFBQSxJQUNGO0FBQUE7QUFBQSxJQUVBLGNBQWM7QUFBQSxNQUNaLFNBQVM7QUFBQSxRQUNQO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQ0YsQ0FBQzsiLAogICJuYW1lcyI6IFtdCn0K
