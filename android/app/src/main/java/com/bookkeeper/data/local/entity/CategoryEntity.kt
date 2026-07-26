package com.bookkeeper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bookkeeper.domain.model.Category
import com.bookkeeper.domain.model.TransactionType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val icon: String,
    val color: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isDeleted: Boolean = false
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        type = type,
        icon = icon,
        color = color,
        parentId = parentId,
        sortOrder = sortOrder,
        isSystem = isSystem,
        isDeleted = isDeleted
    )

    companion object {
        fun fromDomain(c: Category) = CategoryEntity(
            id = c.id,
            name = c.name,
            type = c.type,
            icon = c.icon,
            color = c.color,
            parentId = c.parentId,
            sortOrder = c.sortOrder,
            isSystem = c.isSystem,
            isDeleted = c.isDeleted
        )
    }
}
