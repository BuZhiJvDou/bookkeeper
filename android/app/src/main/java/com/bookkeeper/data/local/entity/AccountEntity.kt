package com.bookkeeper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bookkeeper.domain.model.Account
import com.bookkeeper.domain.model.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val icon: String,
    val color: String,
    val balance: Long = 0,
    val currency: String = "CNY",
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toDomain(): Account = Account(
        id = id,
        name = name,
        type = type,
        icon = icon,
        color = color,
        balance = balance,
        currency = currency,
        sortOrder = sortOrder,
        isDeleted = isDeleted,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(a: Account) = AccountEntity(
            id = a.id,
            name = a.name,
            type = a.type,
            icon = a.icon,
            color = a.color,
            balance = a.balance,
            currency = a.currency,
            sortOrder = a.sortOrder,
            isDeleted = a.isDeleted,
            createdAt = a.createdAt,
            updatedAt = a.updatedAt
        )
    }
}
