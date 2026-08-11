package com.arslandaim.omegaplayer.di

import android.content.Context
import com.arslandaim.omegaplayer.data.LockerDao
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.data.ThemePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LockerDatabase {
        return LockerDatabase.getDatabase(context)
    }

    @Provides
    fun provideLockerDao(database: LockerDatabase): LockerDao {
        return database.lockerDao()
    }

    @Provides
    @Singleton
    fun provideThemePreferences(@ApplicationContext context: Context): ThemePreferences {
        return ThemePreferences(context)
    }
}
