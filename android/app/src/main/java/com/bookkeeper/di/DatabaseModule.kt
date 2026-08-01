package com.bookkeeper.di

import android.content.Context
import androidx.room.Room
import com.bookkeeper.data.local.AppDatabase
import com.bookkeeper.data.local.DbKey
import com.bookkeeper.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // SQLCipher 4.x：AAR 自带 native 库，会随依赖自动加载；不需要显式 loadLibs

        // 用共享钥匙构造 SupportFactory，Room 通过它打开加密 db
        val factory = SupportOpenHelperFactory(DbKey.passphraseHex.toByteArray(Charsets.UTF_8))

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "bookkeeper.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideRecurringRuleDao(db: AppDatabase): RecurringRuleDao = db.recurringRuleDao()
}
