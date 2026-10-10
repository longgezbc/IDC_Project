<template>
  <div class="table-skeleton">
    <div
      v-for="row in rows"
      :key="row"
      class="table-skeleton__row"
    >
      <div
        v-for="col in cols"
        :key="col"
        class="table-skeleton__cell"
      >
        <div
          class="skeleton-line"
          :style="{ width: randomWidth(col) }"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * TableSkeleton 表格骨架屏
 *
 * 用法：
 *   <TableSkeleton v-if="loading" :rows="5" :cols="4" />
 */
defineProps({
  rows: { type: Number, default: 5 },
  cols: { type: Number, default: 4 }
})

function randomWidth(col) {
  const widths = ['60%', '80%', '45%', '70%', '55%', '90%']
  return widths[(col + 1) % widths.length]
}
</script>

<style scoped>
.table-skeleton {
  padding: 12px 0;
}

.table-skeleton__row {
  display: flex;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
}

.table-skeleton__cell {
  flex: 1;
  min-width: 0;
}
</style>
