/**
 * 应用入口
 * 兼容 Vue2 / Vue3 两套运行时：通过条件编译（#ifndef/#ifdef VUE3）分别导出对应的初始化逻辑。
 * - Vue2：new Vue({...App}).$mount() 挂载应用
 * - Vue3：使用 createSSRApp 创建应用并由 createApp 工厂函数返回
 */
import App from './App'

// #ifndef VUE3
import Vue from 'vue'
import './uni.promisify.adaptor'
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
  ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
export function createApp() {
  const app = createSSRApp(App)
  return {
    app
  }
}
// #endif
