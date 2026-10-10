package com.example.hbsz_app_android.di

import com.example.hbsz_app_android.data.repository.AuthRepository
import com.example.hbsz_app_android.data.repository.AuthRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 仓库层绑定模块：接口 → 实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
