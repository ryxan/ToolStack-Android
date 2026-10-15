package com.toolstack.io.di

import com.toolstack.io.data.ai.AiRecipeImporter
import com.toolstack.io.data.ai.FirebaseAiRecipeImporter
import com.toolstack.io.data.ai.NoopRewardedAdProvider
import com.toolstack.io.data.ai.RewardedAdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindAiRecipeImporter(
        impl: FirebaseAiRecipeImporter
    ): AiRecipeImporter

    @Binds
    abstract fun bindRewardedAdProvider(
        impl: NoopRewardedAdProvider
    ): RewardedAdProvider
}
