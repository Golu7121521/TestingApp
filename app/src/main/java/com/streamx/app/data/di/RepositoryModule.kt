package com.streamx.app.data.di

import com.streamx.app.data.repository.EmptyVideoRepository
import com.streamx.app.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindVideoRepository(impl: EmptyVideoRepository): VideoRepository
}
