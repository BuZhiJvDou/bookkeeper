package com.bookkeeper.data.sync

import android.content.Context
import android.util.Log
import com.bookkeeper.data.local.AppDatabase
import com.bookkeeper.data.local.entity.AccountEntity
import com.bookkeeper.data.local.entity.BudgetEntity
import com.bookkeeper.data.local.entity.CategoryEntity
import com.bookkeeper.data.local.entity.RecurringRuleEntity
import com.bookkeeper.data.local.entity.TransactionEntity
import com.bookkeeper.domain.model.Account
import com.bookkeeper.domain.model.Budget
import com.bookkeeper.domain.model.Category
import com.bookkeeper.domain.model.RecurringRule
import com.bookkeeper.domain.model.Transaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步仓库 — 协调 Room 与 SyncClient 之间
 *
 * 主要能力：
 *   - buildPayload(): 从 Room 读所有数据
 *   - applyPayload(): last-write-wins + tombstone 写回 Room
 *   - getDeviceId(): 启动时生成的稳定 ID
 *   - 状态：StateFlow<SyncState> 给 UI 订阅
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun getDeviceId(): String = DeviceId.get(context)

    suspend fun buildPayload(sinceTs: Long = 0): SyncPayload {
        val sinceFilter = sinceTs.coerceAtLeast(0)
        val transactions = db.transactionDao().getAllForSync(sinceFilter).map { it.toDomain() }
        val categories = db.categoryDao().getAllForSync(sinceFilter).map { it.toDomain() }
        val accounts = db.accountDao().getAllForSync(sinceFilter).map { it.toDomain() }
        val budgets = db.budgetDao().getAllForSync(sinceFilter).map { it.toDomain() }
        val recurring = db.recurringRuleDao().getAllForSync(sinceFilter).map { it.toDomain() }
        return SyncPayload(
            deviceId = getDeviceId(),
            clientTs = System.currentTimeMillis(),
            data = SyncData(
                transactions = transactions,
                categories = categories,
                accounts = accounts,
                budgets = budgets,
                recurring = recurring
            )
        )
    }

    /**
     * 应用远端 payload 到本地 db
     * 策略：last-write-wins + tombstone
     */
    suspend fun applyPayload(payload: SyncPayload): SyncApplyResult {
        var inserted = 0
        var updated = 0
        var tombstoned = 0

        // 同步方法不能 suspend，所以每条数据都同步阻塞调用
        // 性能 OK（量级：100-10000 笔），符合单人记账场景
        // Categories
        for (c in payload.data.categories) {
            val existing = db.categoryDao().getByIdSync(c.id)
            if (existing == null) {
                db.categoryDao().upsert(CategoryEntity.fromDomain(c))
                inserted++
            } else if (c.updatedAt > existing.updatedAt) {
                db.categoryDao().upsert(CategoryEntity.fromDomain(c))
                if (c.isDeleted) tombstoned++ else updated++
            }
        }
        // Accounts
        for (a in payload.data.accounts) {
            val existing = db.accountDao().getByIdSync(a.id)
            if (existing == null) {
                db.accountDao().upsert(AccountEntity.fromDomain(a))
                inserted++
            } else if (a.updatedAt > existing.updatedAt) {
                db.accountDao().upsert(AccountEntity.fromDomain(a))
                if (a.isDeleted) tombstoned++ else updated++
            }
        }
        // Transactions
        for (t in payload.data.transactions) {
            val existing = db.transactionDao().getByIdSync(t.id)
            if (existing == null) {
                db.transactionDao().upsert(TransactionEntity.fromDomain(t))
                inserted++
            } else if (t.updatedAt > existing.updatedAt) {
                db.transactionDao().upsert(TransactionEntity.fromDomain(t))
                if (t.isDeleted) tombstoned++ else updated++
            }
        }
        // Budgets
        for (b in payload.data.budgets) {
            val existing = db.budgetDao().getByIdSync(b.id)
            if (existing == null) {
                db.budgetDao().upsert(BudgetEntity.fromDomain(b))
                inserted++
            } else if (b.updatedAt > existing.updatedAt) {
                db.budgetDao().upsert(BudgetEntity.fromDomain(b))
                if (b.isDeleted) tombstoned++ else updated++
            }
        }
        // Recurring
        for (r in payload.data.recurring) {
            val existing = db.recurringRuleDao().getByIdSync(r.id)
            if (existing == null) {
                db.recurringRuleDao().upsert(RecurringRuleEntity.fromDomain(r))
                inserted++
            } else if (r.updatedAt > existing.updatedAt) {
                db.recurringRuleDao().upsert(RecurringRuleEntity.fromDomain(r))
                if (r.isDeleted) tombstoned++ else updated++
            }
        }

        return SyncApplyResult(inserted, updated, tombstoned, System.currentTimeMillis())
    }

    /**
     * 与远端设备做完整一次同步：
     *   1. 推本地
     *   2. 收服务端返回
     *   3. 应用到本地
     */
    suspend fun syncWith(peer: LanPeer): Result<SyncSummary> = runCatching {
        val client = SyncClient(getDeviceId(), "http://${peer.host}:${peer.port}")
        // 1. 推送
        val localPayload = buildPayload(0)
        val resp = client.sync(localPayload)
        if (!resp.ok) throw SyncException(resp.error ?: "sync failed")
        val pushed = resp.applied ?: SyncApplyResult(0, 0, 0, System.currentTimeMillis())
        // 2. 把服务端 peer 增量也 apply
        val peerPayload = resp.envelope?.let { SyncCrypto.decryptPayload<SyncPayload>(it) }
        val pulled = if (peerPayload != null) applyPayload(peerPayload) else SyncApplyResult(0, 0, 0, System.currentTimeMillis())
        SyncSummary(peer, pushed, pulled)
    }.onFailure { e ->
        Log.e("BkSync", "syncWith failed", e)
    }

    /**
     * 公网 / 跨网段同步：URL 由用户手填
     */
    suspend fun syncWithUrl(url: String): Result<SyncSummary> = runCatching {
        val client = SyncClient(getDeviceId(), url)
        client.ping() // 先 ping 验证可达
        val localPayload = buildPayload(0)
        val resp = client.sync(localPayload)
        if (!resp.ok) throw SyncException(resp.error ?: "sync failed")
        val pushed = resp.applied ?: SyncApplyResult(0, 0, 0, System.currentTimeMillis())
        val peerPayload = resp.envelope?.let { SyncCrypto.decryptPayload<SyncPayload>(it) }
        val pulled = if (peerPayload != null) applyPayload(peerPayload) else SyncApplyResult(0, 0, 0, System.currentTimeMillis())
        SyncSummary(null, pushed, pulled)
    }.onFailure { e ->
        Log.e("BkSync", "syncWithUrl failed", e)
    }
}

data class SyncState(
    val syncing: Boolean = false,
    val lastError: String? = null,
    val lastSyncAt: Long = 0L
)

data class SyncSummary(
    val peer: LanPeer?,
    val pushed: SyncApplyResult,
    val pulled: SyncApplyResult
) {
    val totalInserted: Int get() = pushed.inserted + pulled.inserted
    val totalUpdated: Int get() = pushed.updated + pulled.updated
}
