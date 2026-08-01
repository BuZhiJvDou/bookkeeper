package com.bookkeeper.data.local.dao

import androidx.room.*
import com.bookkeeper.data.local.entity.CategoryEntity
import com.bookkeeper.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 AND type = :type ORDER BY sortOrder ASC")
    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND type = :type AND isDeleted = 0 LIMIT 1")
    suspend fun getCategoryByName(name: String, type: TransactionType): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteCategory(id: Long)

    @Query("SELECT COUNT(*) FROM categories WHERE isDeleted = 0")
    suspend fun getCategoryCount(): Int

    @Query("SELECT * FROM categories WHERE isDeleted = 0")
    suspend fun getAllCategoriesForExport(): List<CategoryEntity>

    // 同步用
    @Query("SELECT * FROM categories WHERE updatedAt > :since ORDER BY id")
    suspend fun getAllForSync(since: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun getByIdSync(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<CategoryEntity>)
}
