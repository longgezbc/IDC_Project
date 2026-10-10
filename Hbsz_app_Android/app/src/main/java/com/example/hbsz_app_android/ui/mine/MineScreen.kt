package com.example.hbsz_app_android.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.hbsz_app_android.BuildConfig
import com.example.hbsz_app_android.data.local.UserPreferences
import com.example.hbsz_app_android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MineViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: com.example.hbsz_app_android.data.repository.UserRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    val username: StateFlow<String> = userPreferences.usernameFlow
        .map { it ?: "未登录" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "未登录")

    /** GET /user/profile：职位 · 部门（用于用户卡副标题） */
    private val _subtitle = MutableStateFlow("加载中…")
    val subtitle: StateFlow<String> = _subtitle

    /** 头像 URL（Coil AsyncImage 加载；空则回退占位图标） */
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl

    init {
        viewModelScope.launch {
            when (val res = userRepository.getProfile()) {
                is com.example.hbsz_app_android.common.Resource.Success -> {
                    val user = res.data
                    _subtitle.value = listOfNotNull(
                        user.position?.takeIf { it.isNotBlank() },
                        user.department?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ").ifBlank { user.roles.joinToString("、").ifBlank { "IDC 项目管家" } }
                    _avatarUrl.value = user.avatar?.takeIf { it.isNotBlank() }
                }
                else -> _subtitle.value = "IDC 项目管家"
            }
        }
    }

    /** 退出登录：清空本地 token 后自动回到登录页 */
    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}

private data class MenuEntry(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * 我的页 —— 对齐 Web 端个人中心 + 小程序「我的」：
 * 品牌头部用户卡 → 工作入口（我的任务/填报/归档）→ 设置区 → 退出登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    viewModel: MineViewModel = hiltViewModel(),
    onOpenTasks: () -> Unit = {},
    onOpenSubmissions: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenNotifySettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val username by viewModel.username.collectAsStateWithLifecycle()
    val subtitle by viewModel.subtitle.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ===== 用户卡（品牌渐变头部） =====
            Surface(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Box(
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 头像：Coil AsyncImage 加载真实头像 URL（圆形裁剪），无头像回退占位图标
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "用户头像",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = username,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 工作入口 =====
            MenuCard(
                entries = listOf(
                    MenuEntry("我的任务", Icons.Outlined.AssignmentTurnedIn, onOpenTasks),
                    MenuEntry("进度填报记录", Icons.Outlined.EditNote, onOpenSubmissions),
                    MenuEntry("我的归档", Icons.Outlined.Archive, onOpenArchive)
                )
            )

            Spacer(Modifier.height(12.dp))

            // ===== 账号与设置 =====
            MenuCard(
                entries = listOf(
                    MenuEntry("账号安全", Icons.Outlined.Security, onOpenSecurity),
                    MenuEntry("个人资料", Icons.Outlined.Badge, onOpenProfile),
                    MenuEntry("通知设置", Icons.Outlined.Settings, onOpenNotifySettings),
                    MenuEntry("关于应用", Icons.Outlined.Info, onOpenAbout)
                )
            )

            Spacer(Modifier.height(24.dp))

            // ===== 退出登录 =====
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("退出登录")
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "IDC 项目管家 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // ===== 退出确认（对齐 Web 端 feedback.confirm 交互） =====
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("提示", fontWeight = FontWeight.SemiBold) },
            text = { Text("确认退出登录吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("退出") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 菜单分组卡片：图标 + 文本 + 右箭头，分隔线对齐 Web 端列表样式 */
@Composable
private fun MenuCard(entries: List<MenuEntry>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = entry.onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (index < entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
