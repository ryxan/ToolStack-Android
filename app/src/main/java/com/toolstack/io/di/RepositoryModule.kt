package com.toolstack.io.di

import com.toolstack.io.data.repository.BearingRepository
import com.toolstack.io.data.repository.JsonBearingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBearingRepository(
        impl: JsonBearingRepository
    ): BearingRepository
}
