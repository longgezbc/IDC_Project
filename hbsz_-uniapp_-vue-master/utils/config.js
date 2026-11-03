/**
 * 全局配置
 *
 * 集中管理后端 API 基地址、本地存储键名、请求超时等全局常量。
 * 所有数值请勿散落各处，统一在此维护，便于环境切换与统一调整。
 */

// 后端 API 基地址
// 安全修复（P0-2）：原硬编码明文 HTTP IP，Authorization 头中的 JWT 可被中间人窃听。
// 改为按构建环境区分：生产环境必须 HTTPS（小程序后台需配置 request/socket 合法域名）；
// 开发环境保留原地址用于联调（需在「微信开发者工具 - 详情 - 本地设置」勾选「不校验合法域名」）。
// TODO: 上线前将 PROD_API_BASE_URL 替换为正式 HTTPS 域名。
const DEV_API_BASE_URL = 'http://124.220.158.177/api'
const PROD_API_BASE_URL = 'https://your-domain.com/api'
export const API_BASE_URL = process.env.NODE_ENV === 'production' ? PROD_API_BASE_URL : DEV_API_BASE_URL

// 应用名称
export const APP_NAME = 'IDC项目管家'

// Token 在本地存储中的键
export const TOKEN_KEY = 'hbsz_token'
export const REFRESH_TOKEN_KEY = 'hbsz_refresh_token'
export const USER_INFO_KEY = 'hbsz_user_info'

// 请求超时时间（毫秒）
export const REQUEST_TIMEOUT = 15000
