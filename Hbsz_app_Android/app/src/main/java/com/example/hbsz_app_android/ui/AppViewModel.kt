package com.example.hbsz_app_android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 应用级 ViewModel：监听本地 token，驱动 登录/主页 切换。
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    userPreferences: UserPreferences
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object LoggedIn : UiState
        data object LoggedOut : UiState
    }

    val uiState: StateFlow<UiState> = userPreferences.tokenFlow
        .map { token -> if (token.isNullOrBlank()) UiState.LoggedOut else UiState.LoggedIn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)
}
