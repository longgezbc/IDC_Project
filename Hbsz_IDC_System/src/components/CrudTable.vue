<template>
  <!--
    CrudTable 通用列表布局组件（P1-2.2）
    封装列表页通用的 el-card 容器 + 工具栏插槽 + el-table（列定义通过默认插槽传入）+
    TableSkeleton 加载态 + el-pagination 分页，减少各列表页的重复模板与 CSS。

    说明：
      - 工具栏（新增按钮 + 搜索表单）通过 #toolbar 插槽传入，父组件完全控制布局。
      - el-table-column 通过默认插槽传入，父组件按业务需求定义列与单元格渲染。
      - 空状态通过 #empty 插槽透传到 el-table（可选，未传入时使用 el-table 默认空文本）。
      - 分页通过 v-model:current-page / v-model:page-size 双向绑定父组件的 queryParams。
      - @change 事件在页码或每页条数变化时触发，父组件调用 fetchList 拉取新数据。
      - 加载态使用 TableSkeleton 骨架屏，避免 el-table v-loading 遮罩遮挡分页器。

    用法示例详见 src/views/system/PositionManagement.vue
  -->
  <el-card
    shadow="never"
    class="crud-table"
  >
    <!-- 工具栏插槽：新增按钮 + 搜索表单 -->
    <div
      v-if="$slots.toolbar"
      class="crud-toolbar"
    >
      <slot name="toolbar" />
    </div>

    <!-- 表格区域：加载中显示骨架屏，否则渲染 el-table + 默认插槽中的列定义 -->
    <TableSkeleton
      v-if="loading"
      :rows="skeletonRows"
      :cols="skeletonCols"
    />
    <el-table
      v-else
      :data="data"
      :stripe="stripe"
      :border="border"
      :row-key="rowKey"
      :tree-props="treeProps"
      :default-expand-all="defaultExpandAll"
      @selection-change="onSelectionChange"
    >
      <slot />
      <template
        v-if="$slots.empty"
        #empty
      >
        <slot name="empty" />
      </template>
    </el-table>

    <!-- 分页 -->
    <div
      v-if="showPagination"
      class="crud-pagination"
    >
      <el-pagination
        v-model:current-page="innerCurrentPage"
        v-model:page-size="innerPageSize"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onChange"
        @current-change="onChange"
      />
    </div>
  </el-card>
</template>

<script setup>
/**
 * CrudTable 通用列表布局组件
 *
 * 业务用途：统一各列表页（用户/角色/职位/客户管理等）的表格布局结构，
 *          减少重复的 el-card + toolbar + el-table + el-pagination 模板与 CSS。
 * 数据来源：data / total / currentPage / pageSize 均由父组件传入（通常来自 useCrudList 组合式函数）。
 * 副作用：本组件不直接拉取数据，仅在分页变化时 emit('change')，由父组件调用 fetchList。
 *
 * P1-2.2：从各列表页抽取的通用布局，配合 useCrudList 组合式函数使用可大幅减少样板代码。
 */
// 注：TableSkeleton 已在 main.js 全局注册，无需在此导入
import { computed } from 'vue'

const props = defineProps({
  /** 表格数据（通常来自 useCrudList 的 list） */
  data: { type: Array, default: () => [] },
  /** 是否加载中（显示骨架屏） */
  loading: { type: Boolean, default: false },
  /** 数据总条数（用于分页） */
  total: { type: Number, default: 0 },
  /** 当前页码（v-model:current-page） */
  currentPage: { type: Number, default: 1 },
  /** 每页条数（v-model:page-size） */
  pageSize: { type: Number, default: 10 },
  /** 可选每页条数 */
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] },
  /** 是否显示分页器 */
  showPagination: { type: Boolean, default: true },
  /** 骨架屏行数 */
  skeletonRows: { type: Number, default: 5 },
  /** 骨架屏列数 */
  skeletonCols: { type: Number, default: 5 },
  /** 是否显示斑马纹（树形表格通常关闭，避免父子行交替色影响层级阅读） */
  stripe: { type: Boolean, default: true },
  /** 是否显示纵向边框 */
  border: { type: Boolean, default: true },
  /** 行数据的 Key（树形表格必传，如 'id'） */
  rowKey: { type: [String, Function], default: undefined },
  /** 树形表格配置（如 { children: 'children' }） */
  treeProps: { type: Object, default: undefined },
  /** 是否默认展开所有树节点 */
  defaultExpandAll: { type: Boolean, default: false }
})

const emit = defineEmits(['update:currentPage', 'update:pageSize', 'change', 'selection-change'])

/** 表格选择行变化时触发，父组件据此启用批量操作按钮 */
function onSelectionChange(rows) {
  emit('selection-change', rows)
}

// 分页参数的双向绑定代理：父组件用 v-model:current-page / v-model:page-size 绑定 queryParams
const innerCurrentPage = computed({
  get: () => props.currentPage,
  set: (v) => emit('update:currentPage', v)
})

const innerPageSize = computed({
  get: () => props.pageSize,
  set: (v) => emit('update:pageSize', v)
})

/** 分页参数变化时触发 change 事件，父组件监听后调用 fetchList */
function onChange() {
  emit('change')
}
</script>

<style scoped>
.crud-table {
  border-radius: 6px;
}

.crud-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.crud-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>

<!--
  非 scoped 全局样式：#toolbar 插槽内部的布局规范，供所有使用 CrudTable 的页面共享。
  说明：slot 内容在父组件作用域中渲染，CrudTable 的 scoped 样式无法直接作用其上，
        因此用非 scoped 样式提供默认布局；各页面若需定制，可用 scoped 样式覆盖
        （scoped 选择器带 data-v 属性，特异性更高，会覆盖此处默认值）。
-->
<style>
/* 工具栏左侧（标题/新增按钮/tab 等）：水平排列、垂直居中 */
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 工具栏右侧（搜索表单/视图切换按钮等）：水平排列、垂直居中、超宽换行 */
.toolbar-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

/* 视图切换按钮组（双视图页面用），默认靠右对齐 */
.toolbar-right .view-switch {
  display: flex;
  gap: 4px;
  margin-left: auto;
}
</style>
