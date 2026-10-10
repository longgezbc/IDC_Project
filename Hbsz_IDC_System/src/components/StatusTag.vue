<template>
  <!--
    通用状态标签组件
    背景：项目列表/详情/进度等页面重复使用 el-tag + getStatusType/getTaskStatusType 组合，
          每处都要 import 工具函数、写 :type 绑定，重复且容易写错。
    用法：
      <StatusTag :value="row.status" type="project" />  // 项目状态
      <StatusTag :value="task.status" type="task" />    // 任务状态
    备注：size/effect 透传给 el-tag，默认 small/light
  -->
  <el-tag
    :type="tagType"
    :effect="effect"
    :size="size"
  >
    <slot>{{ value }}</slot>
  </el-tag>
</template>

<script setup>
/**
 * @param {string} value 状态原始值（中文，如"进行中"）
 * @param {'project'|'task'} [type='project'] 状态类型，决定使用哪个映射
 * @param {string} [effect='light'] el-tag effect
 * @param {string} [size='small'] el-tag size
 */
import { computed } from 'vue'
import { getStatusType, getTaskStatusType } from '@/utils/format'

const props = defineProps({
  value: { type: String, default: '' },
  type: { type: String, default: 'project' }, // 'project' | 'task'
  effect: { type: String, default: 'light' },
  size: { type: String, default: 'small' }
})

const tagType = computed(() => {
  // type=task 用任务状态映射；其他视为项目状态
  return props.type === 'task'
    ? getTaskStatusType(props.value)
    : getStatusType(props.value)
})
</script>

<style scoped>
/* el-tag 默认样式已足够，无需额外覆盖 */
</style>
