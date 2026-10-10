/**
 * 应用入口
 *
 * 创建 Vue 应用并完成全局装配：按需注册 Element Plus 图标、注册公共组件
 * （BaseButton/StatusTag/EmptyState）与 v-permission 权限指令，配置全局错误处理器
 * 与未处理 Promise 异常监听，初始化错误上报与性能监控，最后挂载 Pinia、路由、
 * Element Plus（中文语言包）并渲染到 #app。
 * @module main
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './styles/global.css'
import BaseButton from './components/BaseButton.vue'
import StatusTag from './components/StatusTag.vue'
import EmptyState from './components/EmptyState.vue'
import TableSkeleton from './components/TableSkeleton.vue'
import StatCard from './components/StatCard.vue'
import { setupPermissionDirective } from './utils/permission'
import { vSubmit } from './utils/submitGuard'
import { initPerformanceMonitor } from './utils/performance'
import { initErrorReporter, reportVueError } from './utils/errorReporter'
import { registerElementPlusIcons } from './utils/icons'

const app = createApp(App)

// 按需注册项目中实际使用的 Element Plus 图标，避免全量注册导致打包体积膨胀
registerElementPlusIcons(app)

// 全局注册公共按钮组件
app.component('BaseButton', BaseButton)
// 全局注册状态标签与空状态组件，供列表页统一使用
app.component('StatusTag', StatusTag)
app.component('EmptyState', EmptyState)
app.component('TableSkeleton', TableSkeleton)
// 全局注册统计卡片组件，供工作台/列表页数据概览区统一使用
app.component('StatCard', StatCard)

// 注册权限指令 v-permission
setupPermissionDirective(app)
// 注册提交防抖指令 v-submit
app.directive('submit', vSubmit)

// 全局错误处理器：捕获未处理的组件异常，避免白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[全局错误]', err)
  console.error('[错误信息]', info)
  // 上报到错误监控平台
  reportVueError(err, info)
}

// 捕获未处理的 Promise 异常
window.addEventListener('unhandledrejection', (event) => {
  console.error('[未处理的 Promise 异常]', event.reason)
})

// 初始化错误上报（仅生产环境）
initErrorReporter()

// P1-5.6：注册 Pinia 持久化插件
// 启用后，store 中通过 persist 选项声明的状态字段会自动同步到 localStorage
// 用于持久化用户偏好（主题色、分页大小、默认视图等），刷新/重开标签页后保留
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 初始化性能监控（仅生产环境）
initPerformanceMonitor()

app.mount('#app')
