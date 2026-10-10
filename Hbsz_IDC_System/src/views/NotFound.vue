<template>
  <div class="not-found">
    <div class="content">
      <div class="error-code">
        404
      </div>
      <div class="error-title">
        页面未找到
      </div>
      <div class="error-desc">
        抱歉，您访问的页面不存在或已被删除
      </div>
      <div class="actions">
        <BaseButton
          variant="primary"
          icon-name="HomeFilled"
          @click="router.push('/')"
        >
          返回首页
        </BaseButton>
        <BaseButton
          icon-name="Back"
          @click="router.back()"
        >
          返回上页
        </BaseButton>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 404 页面
 * 业务用途：访问的路由不存在时展示的兜底页面，提供返回首页 / 返回上一页的导航。
 * 数据来源：无接口调用，仅使用 vue-router 进行页面跳转。
 */
import { useRouter } from 'vue-router'
const router = useRouter() // 路由实例，用于返回首页或上一页
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
}

.content {
  text-align: center;
}

.error-code {
  font-size: 120px;
  font-weight: 700;
  background: linear-gradient(135deg, #1890ff, #40a9ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1;
  margin-bottom: 16px;
}

.error-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}

.error-desc {
  font-size: 14px;
  color: #909399;
  margin-bottom: 24px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
