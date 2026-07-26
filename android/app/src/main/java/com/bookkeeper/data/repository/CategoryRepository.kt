package com.bookkeeper.data.repository

import com.bookkeeper.data.local.dao.CategoryDao
import com.bookkeeper.data.local.entity.CategoryEntity
import com.bookkeeper.domain.model.Category
import com.bookkeeper.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { list -> list.map { it.toDomain() } }

    fun getCategoriesByType(type: TransactionType): Flow<List<Category>> =
        categoryDao.getCategoriesByType(type).map { list -> list.map { it.toDomain() } }

    suspend fun getCategoryById(id: Long): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    suspend fun insertCategory(category: Category): Long =
        categoryDao.insertCategory(CategoryEntity.fromDomain(category))

    suspend fun updateCategory(category: Category) =
        categoryDao.updateCategory(CategoryEntity.fromDomain(category))

    suspend fun deleteCategory(id: Long) =
        categoryDao.softDeleteCategory(id)

    suspend fun getCategoryCount(): Int =
        categoryDao.getCategoryCount()

    suspend fun initDefaultCategories() {
        if (categoryDao.getCategoryCount() > 0) return

        val defaults = listOf(
            // 支出分类
            Category(name = "餐饮", type = TransactionType.EXPENSE, icon = "restaurant", color = "#FF6B6B", sortOrder = 0, isSystem = true),
            Category(name = "交通", type = TransactionType.EXPENSE, icon = "directions_car", color = "#4ECDC4", sortOrder = 1, isSystem = true),
            Category(name = "购物", type = TransactionType.EXPENSE, icon = "shopping_bag", color = "#45B7D1", sortOrder = 2, isSystem = true),
            Category(name = "住房", type = TransactionType.EXPENSE, icon = "home", color = "#96CEB4", sortOrder = 3, isSystem = true),
            Category(name = "娱乐", type = TransactionType.EXPENSE, icon = "sports_esports", color = "#FFEAA7", sortOrder = 4, isSystem = true),
            Category(name = "医疗", type = TransactionType.EXPENSE, icon = "local_hospital", color = "#DDA0DD", sortOrder = 5, isSystem = true),
            Category(name = "教育", type = TransactionType.EXPENSE, icon = "school", color = "#98D8C8", sortOrder = 6, isSystem = true),
            Category(name = "通讯", type = TransactionType.EXPENSE, icon = "phone", color = "#F7DC6F", sortOrder = 7, isSystem = true),
            Category(name = "服饰", type = TransactionType.EXPENSE, icon = "checkroom", color = "#E8A0BF", sortOrder = 8, isSystem = true),
            Category(name = "日用", type = TransactionType.EXPENSE, icon = "shopping_cart", color = "#AED6F1", sortOrder = 9, isSystem = true),
            Category(name = "其他", type = TransactionType.EXPENSE, icon = "more_horiz", color = "#BDC3C7", sortOrder = 10, isSystem = true),
            // 收入分类
            Category(name = "工资", type = TransactionType.INCOME, icon = "work", color = "#2ECC71", sortOrder = 0, isSystem = true),
            Category(name = "奖金", type = TransactionType.INCOME, icon = "emoji_events", color = "#F1C40F", sortOrder = 1, isSystem = true),
            Category(name = "投资", type = TransactionType.INCOME, icon = "trending_up", color = "#E67E22", sortOrder = 2, isSystem = true),
            Category(name = "兼职", type = TransactionType.INCOME, icon = "laptop", color = "#9B59B6", sortOrder = 3, isSystem = true),
            Category(name = "礼金", type = TransactionType.INCOME, icon = "card_giftcard", color = "#E74C3C", sortOrder = 4, isSystem = true),
            Category(name = "其他", type = TransactionType.INCOME, icon = "more_horiz", color = "#95A5A6", sortOrder = 5, isSystem = true),
        )
        categoryDao.insertCategories(defaults.map { CategoryEntity.fromDomain(it) })
    }

    suspend fun getAllForExport(): List<Category> =
        categoryDao.getAllCategoriesForExport().map { it.toDomain() }

    suspend fun importCategories(categories: List<Category>) {
        categoryDao.insertCategories(categories.map { CategoryEntity.fromDomain(it) })
    }
}
