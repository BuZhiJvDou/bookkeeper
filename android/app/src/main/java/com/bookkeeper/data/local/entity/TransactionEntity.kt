package com.bookkeeper.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookkeeper.domain.model.Transaction
import com.bookkeeper.domain.model.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"]
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"]
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"]
        )
    ],
    indices = [
        Index("categoryId"),
        Index("accountId"),
        Index("date"),
        Index("type")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val note: String? = null,
    val tags: String? = null,
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        type = type,
        amount = amount,
        categoryId = categoryId,
        accountId = accountId,
        toAccountId = toAccountId,
        note = note,
        tags = parseTags(tags),
        date = date,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun parseTags(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                json.decodeFromString(ListSerializer(String.serializer()), raw)
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun fromDomain(t: Transaction) = TransactionEntity(
            id = t.id,
            type = t.type,
            amount = t.amount,
            categoryId = t.categoryId,
            accountId = t.accountId,
            toAccountId = t.toAccountId,
            note = t.note,
            tags = if (t.tags.isNotEmpty()) {
                json.encodeToString(ListSerializer(String.serializer()), t.tags)
            } else null,
            date = t.date,
            createdAt = t.createdAt,
            updatedAt = t.updatedAt,
            isDeleted = t.isDeleted
        )
    }
}
