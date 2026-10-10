/**
 * Element Plus 图标按需注册
 *
 * 背景：原先使用 `import * as Icons` 全量注册所有图标（约 280 个），
 *      导致首屏打包体积增加数百 KB，而项目实际使用的图标只有 40+ 个。
 * 优化：仅显式 import 实际用到的图标组件，减少打包体积。
 * 维护：新增使用图标时，需在下方 import 与 icons 数组中同步添加。
 * @module utils/icons
 */
import {
  // 通用操作
  Search,
  Refresh,
  RefreshLeft,
  Plus,
  Edit,
  EditPen,
  Delete,
  View,
  Download,
  Upload,
  UploadFilled,
  Back,
  ArrowLeft,
  ArrowRight,
  ArrowDown,
  ArrowUp,
  HomeFilled,
  // 视图/布局
  Grid,
  Menu,
  Setting,
  SwitchButton,
  Odometer,
  Fold,
  Expand,
  FullScreen,
  // 业务对象
  Folder,
  FolderOpened,
  Files,
  Document,
  Box,
  Flag,
  Connection,
  Postcard,
  // 状态/统计
  Finished,
  Loading,
  TrendCharts,
  DataAnalysis,
  DataLine,
  Top,
  Warning,
  PieChart,
  Clock,
  Timer,
  Histogram,
  CircleCheck,
  CircleClose,
  // 用户/权限
  User,
  UserFilled,
  Key,
  Lock,
  Unlock,
  // 通知/沟通
  Bell,
  Message,
  Iphone,
  ChatDotRound,
  InfoFilled,
  // 业务属性
  Calendar,
  OfficeBuilding,
  Money,
  // 业务辅助图标
  Star,
  List,
  // 操作类图标
  Share
} from '@element-plus/icons-vue'
// 自定义图标：@element-plus/icons-vue（截至 2.3.2 最新版）不提供指纹图标，
// FingerprintIcon 参照 Material Design 指纹图标绘制，用法与 Element Plus 图标一致
import FingerprintIcon from '@/components/FingerprintIcon.vue'

// 实际使用的图标集合，供按需注册
const icons = [
  Search, Refresh, RefreshLeft, Plus, Edit, EditPen, Delete, View, Download, Upload, UploadFilled,
  Back, ArrowLeft, ArrowRight, ArrowDown, ArrowUp, HomeFilled, Grid, Menu, Setting, SwitchButton, Odometer,
  Fold, Expand, FullScreen,
  Folder, FolderOpened, Files, Document, Box, Flag, Connection, Postcard,
  Finished, Loading, TrendCharts, DataAnalysis, DataLine, Top, Warning, PieChart, Clock, Timer, Histogram, CircleCheck, CircleClose,
  User, UserFilled, Key, Lock, Unlock,
  Bell, Message, Iphone, ChatDotRound, InfoFilled,
  Calendar, OfficeBuilding, Money, Star, List, Share
]

/**
 * 在 Vue 应用实例上注册本项目使用到的 Element Plus 图标组件
 * @param {import('vue').App} app Vue 应用实例
 * @returns {void}
 */
export function registerElementPlusIcons(app) {
  icons.forEach((icon) => {
    app.component(icon.name, icon)
  })
  // 指纹图标为自定义 SVG 组件（图标库未提供），同样注册为全局组件，
  // 模板中 <el-icon><Fingerprint /></el-icon> 用法与内置图标完全一致
  app.component('Fingerprint', FingerprintIcon)
}
