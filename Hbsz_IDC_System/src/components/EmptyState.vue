<template>
  <div class="empty-state">
    <el-icon class="empty-state__icon">
      <component :is="iconName" />
    </el-icon>
    <div class="empty-state__title">
      {{ title }}
    </div>
    <div
      v-if="description"
      class="empty-state__desc"
    >
      {{ description }}
    </div>
    <div
      v-if="$slots.action"
      class="empty-state__action"
    >
      <slot name="action" />
    </div>
  </div>
</template>

<script setup>
/**
 * EmptyState 空状态组件
 *
 * 用法：
 *   <EmptyState icon-name="Document" title="暂无项目" description="点击新建项目开始">
 *     <template #action>
 *       <BaseButton variant="primary" icon-name="Plus" @click="...">新建项目</BaseButton>
 *     </template>
 *   </EmptyState>
 */
defineProps({
  iconName: {
    type: String,
    default: 'Box'
  },
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.empty-state__action {
  margin-top: 8px;
}
</style>
