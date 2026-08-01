/**
 * 共享同步协议 schema（与 Android 现有 SyncManager.ExportData 完全兼容）
 *
 * v2 在 v1 基础上加：
 *   - version: 2
 *   - deviceId, clientTs
 *   - schemaVersion（每表）
 *   - budgets[], recurring[]（Android 旧版没这两张，桌面新版加）
 *
 * 桌面端用普通 JSON.parse/stringify；不在这里用任何 schema 验证库
 * （沿用项目原则：少依赖、保持简单）。
 */

const PROTOCOL_VERSION = 2;
const SCHEMA_VERSION = 1;

// HTTP 层常量
const DEFAULT_PORT = 17860;            // 1-7-8-6-0
const MDNS_SERVICE_TYPE = '_bookkeeper._tcp';
const HTTP_TIMEOUT_MS = 30000;
const SIG_TIMESTAMP_WINDOW_MS = 5 * 60 * 1000;

// HTTP headers
const HDR_DEVICE = 'X-Bk-Device';
const HDR_TS = 'X-Bk-Ts';
const HDR_SIG = 'X-Bk-Sig';

// HTTP paths
const PATH_PING = '/api/v2/ping';
const PATH_SYNC = '/api/v2/sync';
const PATH_SNAPSHOT = '/api/v2/snapshot';

function buildPayload(db, deviceId, sinceTs) {
  // sinceTs: 只返回 updated_at > sinceTs 的记录（增量），null = 全量
  const since = Number(sinceTs) || 0;
  const filter = since > 0 ? ` AND updated_at > ${since}` : '';
  const transactions = db.prepare(
    `SELECT id, type, amount, category_id, account_id, to_account_id, note, tags,
            date, created_at, updated_at, is_deleted
     FROM transactions WHERE 1=1 ${filter}`
  ).all();
  const categories = db.prepare(
    `SELECT id, name, type, icon, color, parent_id, sort_order, is_system, is_deleted, created_at, updated_at
     FROM categories WHERE 1=1 ${filter}`
  ).all();
  const accounts = db.prepare(
    `SELECT id, name, type, icon, color, balance, currency, sort_order, is_deleted, created_at, updated_at
     FROM accounts WHERE 1=1 ${filter}`
  ).all();
  const budgets = db.prepare(
    `SELECT id, category_id, amount, period, start_date, is_deleted, created_at, updated_at
     FROM budgets WHERE 1=1 ${filter}`
  ).all();
  const recurring = db.prepare(
    `SELECT id, type, amount, category_id, account_id, note, period, next_run, auto_create, last_run, is_deleted, created_at, updated_at
     FROM recurring_rules WHERE 1=1 ${filter}`
  ).all();

  return {
    version: PROTOCOL_VERSION,
    schemaVersion: SCHEMA_VERSION,
    deviceId,
    clientTs: Date.now(),
    data: {
      transactions: transactions.map(t => ({
        id: t.id, type: t.type, amount: t.amount, categoryId: t.category_id,
        accountId: t.account_id, toAccountId: t.to_account_id, note: t.note,
        tags: t.tags ? JSON.parse(t.tags) : null,
        date: t.date, createdAt: t.created_at, updatedAt: t.updated_at,
        isDeleted: !!t.is_deleted
      })),
      categories: categories.map(c => ({
        id: c.id, name: c.name, type: c.type, icon: c.icon, color: c.color,
        parentId: c.parent_id, sortOrder: c.sort_order, isSystem: !!c.is_system,
        isDeleted: !!c.is_deleted, createdAt: c.created_at, updatedAt: c.updated_at
      })),
      accounts: accounts.map(a => ({
        id: a.id, name: a.name, type: a.type, icon: a.icon, color: a.color,
        balance: a.balance, currency: a.currency, sortOrder: a.sort_order,
        isDeleted: !!a.is_deleted, createdAt: a.created_at, updatedAt: a.updated_at
      })),
      budgets: budgets.map(b => ({
        id: b.id, categoryId: b.category_id, amount: b.amount, period: b.period,
        startDate: b.start_date, isDeleted: !!b.is_deleted,
        createdAt: b.created_at, updatedAt: b.updated_at
      })),
      recurring: recurring.map(r => ({
        id: r.id, type: r.type, amount: r.amount, categoryId: r.category_id,
        accountId: r.account_id, note: r.note, period: r.period, nextRun: r.next_run,
        autoCreate: !!r.auto_create, lastRun: r.last_run,
        isDeleted: !!r.is_deleted, createdAt: r.created_at, updatedAt: r.updated_at
      }))
    }
  };
}

/**
 * 把同步 payload 应用到本地 db
 * 策略：last-write-wins + tombstone
 */
function applyPayload(db, payload) {
  const now = Date.now();
  let inserted = 0, updated = 0, tombstoned = 0;

  const upsert = (table, idCol, record, cols) => {
    const existing = db.prepare(`SELECT ${idCol}, updated_at FROM ${table} WHERE ${idCol} = ?`).get(record.id);
    if (!existing) {
      const placeholders = cols.map(() => '?').join(',');
      const colNames = cols.join(',');
      const values = cols.map(c => {
        const v = record[c];
        if (c === 'tags' && v != null) return JSON.stringify(v);
        if (typeof v === 'boolean') return v ? 1 : 0;
        return v;
      });
      db.prepare(`INSERT INTO ${table} (${idCol}, ${colNames}) VALUES (?, ${placeholders})`)
        .run(record.id, ...values);
      inserted++;
    } else if (record.updatedAt && record.updatedAt > existing.updated_at) {
      const sets = cols.map(c => `${c} = ?`).join(', ');
      const values = cols.map(c => {
        const v = record[c];
        if (c === 'tags' && v != null) return JSON.stringify(v);
        if (typeof v === 'boolean') return v ? 1 : 0;
        return v;
      });
      db.prepare(`UPDATE ${table} SET ${sets} WHERE ${idCol} = ?`).run(...values, record.id);
      if (record.isDeleted) tombstoned++; else updated++;
    }
  };

  const txn = db.transaction(() => {
    for (const t of payload.data.transactions) {
      upsert('transactions', 'id', t,
        ['type', 'amount', 'category_id', 'account_id', 'to_account_id',
         'note', 'tags', 'date', 'created_at', 'updated_at', 'is_deleted']);
    }
    for (const c of payload.data.categories) {
      upsert('categories', 'id', c,
        ['name', 'type', 'icon', 'color', 'parent_id', 'sort_order',
         'is_system', 'is_deleted', 'created_at', 'updated_at']);
    }
    for (const a of payload.data.accounts) {
      upsert('accounts', 'id', a,
        ['name', 'type', 'icon', 'color', 'balance', 'currency',
         'sort_order', 'is_deleted', 'created_at', 'updated_at']);
    }
    for (const b of payload.data.budgets) {
      upsert('budgets', 'id', b,
        ['category_id', 'amount', 'period', 'start_date',
         'is_deleted', 'created_at', 'updated_at']);
    }
    for (const r of payload.data.recurring) {
      upsert('recurring_rules', 'id', r,
        ['type', 'amount', 'category_id', 'account_id', 'note', 'period',
         'next_run', 'auto_create', 'last_run', 'is_deleted', 'created_at', 'updated_at']);
    }
  });
  txn();

  return { inserted, updated, tombstoned, at: now };
}

module.exports = {
  PROTOCOL_VERSION, SCHEMA_VERSION,
  DEFAULT_PORT, MDNS_SERVICE_TYPE, HTTP_TIMEOUT_MS, SIG_TIMESTAMP_WINDOW_MS,
  HDR_DEVICE, HDR_TS, HDR_SIG, PATH_PING, PATH_SYNC, PATH_SNAPSHOT,
  buildPayload, applyPayload
};
