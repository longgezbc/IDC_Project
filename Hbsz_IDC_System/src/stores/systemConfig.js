/**
 * 系统配置状态仓库（Pinia setup store）
 *
 * 负责加载、保存并应用系统全局配置（系统名称、版本、主题色、默认视图、每页条数、自动保存等）。
 * 配置加载后会自动应用到 Element Plus 主题色（覆盖 CSS 变量）与页面标题。
 * 加载使用 loaded/loading 标记做单次加载与并发去重，请求被取消时静默处理。
 * @module stores/systemConfig
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
// 修复 L8：原 import axios 仅用于 axios.isCancel，但本项目取消请求使用 AbortController，
// 应统一用 cancelToken.js 的 isCancelError 判定（兼容 axios.Cancel 与 CanceledError）。
import { isCancelError } from '@/utils/cancelToken'
import { systemApi } from '@/api/system'

export const useSystemConfigStore = defineStore('systemConfig', () => {
  /** 默认系统配置（reset 时恢复，也作为首次加载前的初始值） */
  const DEFAULT_CONFIG = {
    name: 'IDC数据中心项目管理系统',
    version: 'V1.0.0',
    themeColor: '#1890ff',
    defaultView: 'table',
    pageSize: 20,
    autoSave: true
  }

  /** @type {import('vue').Ref<Object>} 系统配置对象（含名称、版本、主题色、默认视图、每页条数、自动保存等） */
  const config = ref({ ...DEFAULT_CONFIG })

  /** @type {import('vue').Ref<boolean>} 是否已完成首次加载（用于避免重复请求） */
  const loaded = ref(false)
  /** @type {import('vue').Ref<boolean>} 是否正在加载（用于并发去重） */
  const loading = ref(false)

  /**
   * 将主题色应用到 Element Plus 和全局 CSS 变量
   *
   * 通过覆盖 --el-color-primary 实现主题色切换，并按 1/10 步长与白色混合生成
   * primary-light-1~9 浅色变体、与黑色混合生成 primary-dark-2 深色变体。
   * @param {string} color 十六进制主题色（如 #1890ff）
   * @returns {void}
   */
  function applyThemeColor(color) {
    if (!color) return
    const root = document.documentElement
    // 主色
    root.style.setProperty('--el-color-primary', color)
    // 生成浅色变体（Element Plus 用 primary-light-N）
    const rgb = hexToRgb(color)
    if (rgb) {
      // light-3 ~ light-9：与白色混合
      for (let i = 1; i <= 9; i++) {
        const ratio = i / 10
        const mixed = mixColor(rgb, { r: 255, g: 255, b: 255 }, ratio)
        root.style.setProperty(`--el-color-primary-light-${i}`, rgbToHex(mixed))
      }
      // dark-2：与黑色混合
      const dark = mixColor(rgb, { r: 0, g: 0, b: 0 }, 0.2)
      root.style.setProperty('--el-color-primary-dark-2', rgbToHex(dark))
    }
  }

  /**
   * 应用系统名称：设置页面标题
   * @param {string} name 系统名称
   * @returns {void}
   */
  function applySystemName(name) {
    if (!name) return
    document.title = name
  }

  /**
   * 应用所有配置到全局（主题色 + 系统名称）
   * @param {Object} cfg 配置对象
   * @returns {void}
   */
  function applyConfig(cfg) {
    if (!cfg) return
    applyThemeColor(cfg.themeColor)
    applySystemName(cfg.name)
  }

  /**
   * 加载系统配置
   *
   * 首次加载或强制刷新时请求后端并应用配置；已加载或加载中直接返回当前配置以去重。
   * 请求被取消（路由切换等）时静默返回；其他异常时回退应用默认配置并标记已加载。
   * @param {boolean} [force=false] 是否强制重新加载
   * @returns {Promise<Object>} 当前系统配置对象
   */
  async function loadConfig(force = false) {
    if (loaded.value && !force) return config.value
    if (loading.value) return config.value
    loading.value = true
    try {
      const res = await systemApi.getSystemConfig()
      if (res && res.data) {
        Object.assign(config.value, res.data)
        applyConfig(config.value)
        loaded.value = true
      }
      return config.value
    } catch (e) {
      // 请求被取消（路由切换等）：静默处理，不打印错误日志
      // 修复 L8：改用 isCancelError 判定取消请求，与项目其他 store 保持一致
      if (isCancelError(e)) {
        return config.value
      }
      console.error('[系统配置] 加载失败，使用默认配置:', e)
      // 加载失败时也应用默认配置
      applyConfig(config.value)
      loaded.value = true
      return config.value
    } finally {
      loading.value = false
    }
  }

  /**
   * 保存系统配置
   *
   * 副作用：保存成功后将返回值合并进 config 并重新应用到全局。
   * @param {Object} data 待保存的配置字段
   * @returns {Promise<Object>} 后端保存响应对象
   */
  async function saveConfig(data) {
    const res = await systemApi.updateSystemConfig(data)
    if (res && res.data) {
      Object.assign(config.value, res.data)
      applyConfig(config.value)
    }
    return res
  }

  /**
   * hex 颜色转 rgb 分量
   * @param {string} hex 十六进制颜色（支持带/不带 # 的 6 位形式）
   * @returns {{r:number,g:number,b:number}|null} rgb 分量对象，非法输入返回 null
   */
  function hexToRgb(hex) {
    if (!hex) return null
    const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
    return m ? { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) } : null
  }

  /**
   * rgb 分量转 hex 颜色字符串
   * @param {{r:number,g:number,b:number}} rgb rgb 分量对象
   * @returns {string} 十六进制颜色字符串（如 #1890ff）
   */
  function rgbToHex({ r, g, b }) {
    return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('')
  }

  /**
   * 颜色混合
   * @param {{r:number,g:number,b:number}} rgb1 主体颜色
   * @param {{r:number,g:number,b:number}} rgb2 混入颜色
   * @param {number} ratio 混入 rgb2 的比例（0~1）
   * @returns {{r:number,g:number,b:number}} 混合后的 rgb 分量对象
   */
  function mixColor(rgb1, rgb2, ratio) {
    return {
      r: Math.round(rgb1.r * (1 - ratio) + rgb2.r * ratio),
      g: Math.round(rgb1.g * (1 - ratio) + rgb2.g * ratio),
      b: Math.round(rgb1.b * (1 - ratio) + rgb2.b * ratio)
    }
  }

  /**
   * 重置 store 状态到初始值（P2 修复）
   *
   * 背景：本 store 的其余 6 个业务 store 在登出时均有 reset，唯独本 store 缺失，
   * 且 persist key（hbsz:system-config）不带用户维度。共享电脑上用户 B 登录会
   * 继承用户 A 的 themeColor/pageSize 等偏好；loaded 残留为 true 也导致不刷新
   * 页面再次登录时短路，无法重新拉取后端配置。
   * 登出/会话超时时由 MainLayout 调用：恢复默认配置、复位加载标记，
   * 并移除持久化副本，避免下一个用户读到上一个用户的偏好。
   * @returns {void}
   */
  function reset() {
    config.value = { ...DEFAULT_CONFIG }
    loaded.value = false
    loading.value = false
    // 移除持久化副本（persist 插件会在 config 变化时重新写入默认值）
    localStorage.removeItem('hbsz:system-config')
    applyConfig(config.value)
  }

  return {
    config,
    loaded,
    loading,
    loadConfig,
    saveConfig,
    applyConfig,
    applyThemeColor,
    reset
  }
}, {
  // P1-5.6：持久化用户偏好到 localStorage
  // 仅持久化 config 字段（主题色、默认视图、分页大小、自动保存等用户偏好）
  // loaded/loading 不持久化：每次启动都需要重新向后端校验配置是否被管理员修改
  // 注：后端 loadConfig() 完成后会通过 Object.assign 覆盖 backend-driven 字段（name/version），
  //     持久化的用户偏好作为"启动初始值"避免主题色闪烁
  persist: {
    pick: ['config'],
    storage: localStorage,
    key: 'hbsz:system-config'
  }
})
