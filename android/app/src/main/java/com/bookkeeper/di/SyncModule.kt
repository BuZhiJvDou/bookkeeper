package com.bookkeeper.di

import android.content.Context
import com.bookkeeper.data.sync.NsdHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideNsdHelper(@ApplicationContext context: Context): NsdHelper = NsdHelper(context)
}
