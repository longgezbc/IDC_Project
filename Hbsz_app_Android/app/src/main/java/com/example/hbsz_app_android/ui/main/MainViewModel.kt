package com.example.hbsz_app_android.ui.main

import androidx.lifecycle.ViewModel
import com.example.hbsz_app_android.data.repository.NotificationCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 主页面 ViewModel：暴露全局未读通知数（底部导航"消息"Tab 角标数据源）。
 *
 * 数据来自 [NotificationCenter]（token 生命周期自动接线：登录建连 WebSocket 实时累加，
 * 登出清零），本层仅做透传，不持有业务逻辑。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    notificationCenter: NotificationCenter
) : ViewModel() {
    val unreadCount: StateFlow<Int> = notificationCenter.unreadCount
}
