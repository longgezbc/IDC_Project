<template>
  <!--
    全局错误边界组件
    背景：Vue 组件渲染抛错时会导致整个应用白屏，用户体验差。
    用法：用 ErrorBoundary 包裹路由出口，捕获子组件渲染错误并显示友好提示。
      <ErrorBoundary>
        <router-view />
      </ErrorBoundary>
    实现：通过 onErrorCaptured 捕获后代组件渲染错误，错误时显示重试入口；
          重试时通过 key 强制重新渲染子组件，恢复到正常状态。
  -->
  <template v-if="hasError">
    <div class="error-boundary">
      <el-result
        icon="error"
        title="页面渲染异常"
        sub-title="抱歉，页面渲染时发生异常，请刷新页面重试"
      >
        <template #extra>
          <BaseButton
            variant="primary"
            @click="handleRetry"
          >
            重试
          </BaseButton>
        </template>
      </el-result>
    </div>
  </template>
  <!-- 正常渲染子组件：使用 key 在重试时强制重新挂载，清除错误状态 -->
  <template v-else>
    <slot :key="retryKey" />
  </template>
</template>

<script setup>
/**
 * 错误边界组件
 *
 * 通过 onErrorCaptured 钩子捕获后代组件在渲染、提交阶段抛出的同步错误，
 * 阻止错误向上冒泡导致整页白屏，并展示友好的重试入口。
 *
 * 注意：onErrorCaptured 仅捕获组件渲染阶段的同步错误，不捕获异步错误、
 *       事件回调中的错误或 Promise rejection（这些需由 errorHandler / window 监听处理）。
 */
import { ref, onErrorCaptured } from 'vue'
import { reportVueError } from '@/utils/errorReporter'

// 是否捕获到渲染错误
const hasError = ref(false)
// 重试 key：每次重试递增，强制子组件重新挂载
const retryKey = ref(0)

/**
 * 捕获后代组件渲染错误
 *
 * 返回 false 阻止错误继续向上冒泡（避免触发 app.config.errorHandler 重复上报）；
 * 同时将错误上报到监控系统（仅生产环境生效）。
 */
onErrorCaptured((err, instance, info) => {
  console.error('[错误边界] 捕获渲染错误:', err, info)
  hasError.value = true
  // 上报到错误监控平台（开发环境仅打印，生产环境才会上报）
  reportVueError(err, `errorBoundary:${info}`)
  // 阻止错误继续冒泡，避免触发全局 errorHandler 重复上报
  return false
})

/**
 * 重试：清除错误状态并强制重新渲染子组件
 */
function handleRetry() {
  hasError.value = false
  retryKey.value++
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 32px;
}
</style>
