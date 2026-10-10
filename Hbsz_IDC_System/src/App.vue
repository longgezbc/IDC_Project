<template>
  <!-- 用错误边界包裹路由出口，捕获组件渲染错误避免整页白屏 -->
  <ErrorBoundary>
    <router-view />
  </ErrorBoundary>
</template>

<script setup>
import { onMounted } from 'vue'
import { useSystemConfigStore } from '@/stores/systemConfig'
// 全局错误边界组件（捕获路由组件渲染错误）
import ErrorBoundary from '@/components/ErrorBoundary.vue'

const systemConfigStore = useSystemConfigStore()

// 应用启动时加载系统配置，应用主题色和系统名称
onMounted(() => {
  systemConfigStore.loadConfig().catch(err => {
    console.warn('[App] 系统配置加载失败，使用默认值:', err.message)
  })
})
</script>
