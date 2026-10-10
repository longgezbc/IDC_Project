package com.example.hbsz_app_android.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.remote.dto.CustomerDto
import com.example.hbsz_app_android.data.repository.CustomerRepository
import com.example.hbsz_app_android.ui.components.EmptyState
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.StatusTag
import com.example.hbsz_app_android.ui.components.TagTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val customers: List<CustomerDto> = emptyList(),
    /** 分页状态：当前页码 / 是否还有更多 / 是否正在加载下一页 */
    val pageNum: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false
)

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CustomerUiState())
    val state: StateFlow<CustomerUiState> = _state

    companion object {
        private const val PAGE_SIZE = 20
    }

    init {
        load()
    }

    fun refresh() = load()

    /** 重置到第一页并整体替换列表 */
    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val res = customerRepository.getCustomers(pageNum = 1, pageSize = PAGE_SIZE)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    customers = res.data.records,
                    pageNum = 1,
                    hasMore = PAGE_SIZE < res.data.total
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    error = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 加载下一页，成功后追加到列表尾部 */
    fun loadMore() {
        val cur = _state.value
        if (cur.loadingMore || !cur.hasMore || cur.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            val nextPage = _state.value.pageNum + 1
            when (val res = customerRepository.getCustomers(pageNum = nextPage, pageSize = PAGE_SIZE)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loadingMore = false,
                    customers = _state.value.customers + res.data.records,
                    pageNum = nextPage,
                    hasMore = nextPage * PAGE_SIZE < res.data.total
                )
                is Resource.Error -> _state.value = _state.value.copy(loadingMore = false)
                Resource.Loading -> Unit
            }
        }
    }
}

/**
 * 客户管理页 —— 对齐 Web 端 customer/CustomerManagement.vue：
 * 客户列表（联系人/电话/项目数统计/地址），卡片式布局适配移动端。
 * 数据源：GET /customers（customer:query 权限）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    onBack: () -> Unit = {},
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "客户管理 (${state.customers.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.loading -> LoadingState(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
            state.error != null -> ErrorState(
                message = state.error ?: "加载失败",
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onRetry = { viewModel.refresh() }
            )
            state.customers.isEmpty() -> Column(Modifier.padding(padding)) {
                EmptyState(text = "暂无客户")
            }
            else -> {
                val listState = rememberLazyListState()
                // 滚动接近底部（最后可见项 >= 总项数-3）时自动加载下一页。
                // key 用 lastVisibleIndex + totalItemsCount：短列表追加后若仍全部可见可继续触发（布尔 key 不变化会漏拉）
                val lastVisibleIndex by remember {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                }
                LaunchedEffect(lastVisibleIndex, listState.layoutInfo.totalItemsCount) {
                    if (lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 3) viewModel.loadMore()
                }
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.customers, key = { it.id }) { CustomerCard(it) { phone ->
                        // 跳转系统拨号盘（不带权限）；无拨号器的设备兜底提示
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        runCatching { context.startActivity(dialIntent) }
                            .onFailure {
                                android.widget.Toast.makeText(
                                    context, "未找到拨号应用", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                    } }
                    // 末尾 footer：加载中 / 没有更多
                    if (state.loadingMore || (!state.hasMore && state.customers.isNotEmpty())) {
                        item(key = "footer") {
                            Text(
                                text = if (state.loadingMore) "加载中…" else "— 没有更多了 —",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerDto, onCall: (String?) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.name.take(2),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusTag(
                            text = if (customer.status == 1) "合作中" else "已停用",
                            tone = if (customer.status == 1) TagTone.SUCCESS else TagTone.INFO
                        )
                    }
                    Text(
                        text = "联系人：${customer.contactPerson ?: "-"} · ${customer.contactPhone ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onCall(customer.contactPhone) },
                    enabled = !customer.contactPhone.isNullOrBlank()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Call,
                        contentDescription = "拨打电话",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${customer.projectCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "累计项目",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = customer.customerCode ?: "-",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "客户编号",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1.6f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = customer.address ?: "-",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
