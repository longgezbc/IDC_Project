/**
 * 全局配置
 *
 * 集中管理后端 API 基地址、本地存储键名、请求超时等全局常量。
 * 所有数值请勿散落各处，统一在此维护，便于环境切换与统一调整。
 */

// 后端 API 基地址
// 开发调试：与 web 端访问同一后端，需在「微信开发者工具 - 详情 - 本地设置」勾选「不校验合法域名」
// 正式上线前：必须改为 HTTPS 域名，并在小程序后台配置 request 合法域名
export const API_BASE_URL = 'http://124.220.158.177/api'

// 应用名称
export const APP_NAME = 'IDC项目管家'

// Token 在本地存储中的键
export const TOKEN_KEY = 'hbsz_token'
export const REFRESH_TOKEN_KEY = 'hbsz_refresh_token'
export const USER_INFO_KEY = 'hbsz_user_info'

// 请求超时时间（毫秒）
export const REQUEST_TIMEOUT = 15000
