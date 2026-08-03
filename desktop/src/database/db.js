const Database = require('better-sqlite3-multiple-ciphers');
const path = require('path');
const { app } = require('electron');
const fs = require('fs');
const { getPassphrase } = require('./dbkey');

const DB_PATH = path.join(
  process.env.APPDATA || (process.platform === 'darwin'
    ? path.join(process.env.HOME, 'Library/Application Support')
    : path.join(process.env.HOME, '.config')),
  'Bookkeeper',
  'bookkeeper.db'
);

let db;

function init() {
  // 确保目录存在
  const dir = path.dirname(DB_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

  db = new Database(DB_PATH);
  // 启用 SQLCipher 整库加密（必须在任何其它 pragma 前调用）
  db.pragma(`cipher='sqlcipher'`);
  db.pragma(`key="x'${getPassphrase()}'"`);
  // SQLCipher 4 兼容旧库 1.x 默认设置；显式指定 page_size 与 kdf_iter
  db.pragma('cipher_page_size = 4096');
  db.pragma('kdf_iter = 256000');
  db.pragma('cipher_use_hmac = ON');
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');

  createTables();
  seedDefaults();
}

function createTables() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      type TEXT NOT NULL CHECK(type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
      icon TEXT NOT NULL DEFAULT 'circle',
      color TEXT NOT NULL DEFAULT '#6C63FF',
      parent_id INTEGER,
      sort_order INTEGER DEFAULT 0,
      is_system INTEGER DEFAULT 0,
      is_deleted INTEGER DEFAULT 0,
      FOREIGN KEY (parent_id) REFERENCES categories(id)
    );

    CREATE TABLE IF NOT EXISTS accounts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'CASH',
      icon TEXT NOT NULL DEFAULT 'wallet',
      color TEXT NOT NULL DEFAULT '#2ECC71',
      balance INTEGER DEFAULT 0,
      currency TEXT DEFAULT 'CNY',
      sort_order INTEGER DEFAULT 0,
      is_deleted INTEGER DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      type TEXT NOT NULL CHECK(type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
      amount INTEGER NOT NULL,
      category_id INTEGER NOT NULL,
      account_id INTEGER NOT NULL,
      to_account_id INTEGER,
      note TEXT,
      tags TEXT,
      date INTEGER NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      is_deleted INTEGER DEFAULT 0,
      FOREIGN KEY (category_id) REFERENCES categories(id),
      FOREIGN KEY (account_id) REFERENCES accounts(id),
      FOREIGN KEY (to_account_id) REFERENCES accounts(id)
    );

    CREATE TABLE IF NOT EXISTS budgets (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      category_id INTEGER,
      amount INTEGER NOT NULL,
      period TEXT NOT NULL DEFAULT 'MONTHLY',
      start_date INTEGER NOT NULL,
      is_deleted INTEGER DEFAULT 0,
      FOREIGN KEY (category_id) REFERENCES categories(id)
    );

    CREATE TABLE IF NOT EXISTS recurring_rules (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      type TEXT NOT NULL CHECK(type IN ('INCOME', 'EXPENSE')),
      amount INTEGER NOT NULL,
      category_id INTEGER NOT NULL,
      account_id INTEGER NOT NULL,
      note TEXT,
      period TEXT NOT NULL DEFAULT 'MONTHLY' CHECK(period IN ('DAILY', 'WEEKLY', 'MONTHLY')),
      next_run INTEGER NOT NULL,
      auto_create INTEGER DEFAULT 1,
      last_run INTEGER,
      is_deleted INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL,
      FOREIGN KEY (category_id) REFERENCES categories(id),
      FOREIGN KEY (account_id) REFERENCES accounts(id)
    );

    CREATE INDEX IF NOT EXISTS idx_trans_date ON transactions(date);
    CREATE INDEX IF NOT EXISTS idx_trans_category ON transactions(category_id);
    CREATE INDEX IF NOT EXISTS idx_trans_account ON transactions(account_id);
    CREATE INDEX IF NOT EXISTS idx_trans_type ON transactions(type);
  `);
}

function seedDefaults() {
  const count = db.prepare('SELECT COUNT(*) as c FROM categories').get().c;
  if (count > 0) return;

  const insertCategory = db.prepare(
    'INSERT INTO categories (name, type, icon, color, sort_order, is_system) VALUES (?, ?, ?, ?, ?, 1)'
  );

  const expenseCategories = [
    ['餐饮', 'restaurant', '#FF6B6B'],
    ['交通', 'directions_car', '#4ECDC4'],
    ['购物', 'shopping_bag', '#45B7D1'],
    ['住房', 'home', '#96CEB4'],
    ['娱乐', 'sports_esports', '#FFEAA7'],
    ['医疗', 'local_hospital', '#DDA0DD'],
    ['教育', 'school', '#98D8C8'],
    ['通讯', 'phone', '#F7DC6F'],
    ['服饰', 'checkroom', '#E8A0BF'],
    ['日用', 'shopping_cart', '#AED6F1'],
    ['其他', 'more_horiz', '#BDC3C7'],
  ];

  const incomeCategories = [
    ['工资', 'work', '#2ECC71'],
    ['奖金', 'emoji_events', '#F1C40F'],
    ['投资', 'trending_up', '#E67E22'],
    ['兼职', 'laptop', '#9B59B6'],
    ['礼金', 'card_giftcard', '#E74C3C'],
    ['其他', 'more_horiz', '#95A5A6'],
  ];

  const insertMany = db.transaction(() => {
    expenseCategories.forEach(([name, icon, color], i) => {
      insertCategory.run(name, 'EXPENSE', icon, color, i);
    });
    incomeCategories.forEach(([name, icon, color], i) => {
      insertCategory.run(name, 'INCOME', icon, color, i);
    });
  });
  insertMany();

  // 默认账户
  const insertAccount = db.prepare(
    'INSERT INTO accounts (name, type, icon, color, sort_order) VALUES (?, ?, ?, ?, ?)'
  );
  insertAccount.run('现金', 'CASH', 'payments', '#2ECC71', 0);
  insertAccount.run('银行卡', 'BANK_CARD', 'credit_card', '#3498DB', 1);
  insertAccount.run('支付宝', 'ALIPAY', 'account_balance_wallet', '#1677FF', 2);
  insertAccount.run('微信', 'WECHAT', 'chat', '#07C160', 3);
}

// === 交易 CRUD ===

function getAllTransactions() {
  return db.prepare(
    'SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY date DESC'
  ).all();
}

function getTransactionsByDateRange(startDate, endDate) {
  return db.prepare(
    'SELECT * FROM transactions WHERE is_deleted = 0 AND date BETWEEN ? AND ? ORDER BY date DESC'
  ).all(startDate, endDate);
}

function addTransaction(t) {
  const now = Date.now();
  const result = db.prepare(`
    INSERT INTO transactions (type, amount, category_id, account_id, to_account_id, note, tags, date, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(t.type, t.amount, t.categoryId, t.accountId, t.toAccountId || null, t.note || null, t.tags ? JSON.stringify(t.tags) : null, t.date, now, now);

  // 更新账户余额
  const balanceChange = t.type === 'INCOME' ? t.amount : -t.amount;
  db.prepare('UPDATE accounts SET balance = balance + ? WHERE id = ?').run(balanceChange, t.accountId);

  return result.lastInsertRowid;
}

function updateTransaction(id, t) {
  const now = Date.now();
  return db.prepare(`
    UPDATE transactions SET type = ?, amount = ?, category_id = ?, account_id = ?, note = ?, date = ?, updated_at = ?
    WHERE id = ?
  `).run(t.type, t.amount, t.categoryId, t.accountId, t.note, t.date, now, id);
}

function deleteTransaction(id) {
  return db.prepare('UPDATE transactions SET is_deleted = 1, updated_at = ? WHERE id = ?').run(Date.now(), id);
}

// === 循环记账规则 ===

/** 计算下一次执行时间：在当前 next_run 基础上加一个周期 */
function computeNextRun(period, from) {
  const d = new Date(from);
  if (period === 'DAILY') d.setDate(d.getDate() + 1);
  else if (period === 'WEEKLY') d.setDate(d.getDate() + 7);
  else d.setMonth(d.getMonth() + 1); // MONTHLY
  return d.getTime();
}

/** 获取所有循环记账规则（含分类/账户名） */
function getAllRecurringRules() {
  return db.prepare(`
    SELECT r.*, c.name AS category_name, c.color AS category_color, a.name AS account_name
    FROM recurring_rules r
    LEFT JOIN categories c ON r.category_id = c.id
    LEFT JOIN accounts a ON r.account_id = a.id
    WHERE r.is_deleted = 0
    ORDER BY r.next_run ASC
  `).all();
}

/** 新增循环记账规则 */
function addRecurringRule(r) {
  const now = Date.now();
  const result = db.prepare(`
    INSERT INTO recurring_rules (type, amount, category_id, account_id, note, period, next_run, auto_create, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(r.type, r.amount, r.categoryId, r.accountId, r.note || null, r.period, r.nextRun, r.autoCreate ? 1 : 0, now);
  return result.lastInsertRowid;
}

/** 更新循环记账规则 */
function updateRecurringRule(id, r) {
  return db.prepare(`
    UPDATE recurring_rules SET type = ?, amount = ?, category_id = ?, account_id = ?, note = ?, period = ?, next_run = ?, auto_create = ?
    WHERE id = ?
  `).run(r.type, r.amount, r.categoryId, r.accountId, r.note || null, r.period, r.nextRun, r.autoCreate ? 1 : 0, id);
}

/** 删除循环记账规则（软删除） */
function deleteRecurringRule(id) {
  return db.prepare('UPDATE recurring_rules SET is_deleted = 1 WHERE id = ?').run(id);
}

/**
 * 处理所有到期的循环记账规则。
 * 对每条 next_run <= now 且 auto_create=1 的规则：
 * 生成对应交易 + 更新账户余额 + 推进 next_run 到下一周期。
 * 可能一次补上多个错过的周期。返回本次生成的交易数。
 * 整体用事务保证一致性。
 */
function processRecurringRules() {
  const now = Date.now();
  const run = db.transaction(() => {
    let created = 0;
    const due = db.prepare('SELECT * FROM recurring_rules WHERE is_deleted = 0 AND auto_create = 1 AND next_run <= ?').all(now);
    for (const rule of due) {
      let nextRun = rule.next_run;
      // 补齐所有错过的周期（防止长期未开启应用漏记）
      let guard = 0;
      while (nextRun <= now && guard < 1000) {
        db.prepare(`
          INSERT INTO transactions (type, amount, category_id, account_id, note, date, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `).run(rule.type, rule.amount, rule.category_id, rule.account_id, rule.note || null, nextRun, now, now);
        const balanceChange = rule.type === 'INCOME' ? rule.amount : -rule.amount;
        db.prepare('UPDATE accounts SET balance = balance + ? WHERE id = ?').run(balanceChange, rule.account_id);
        created++;
        nextRun = computeNextRun(rule.period, nextRun);
        guard++;
      }
      db.prepare('UPDATE recurring_rules SET next_run = ?, last_run = ? WHERE id = ?').run(nextRun, now, rule.id);
    }
    return created;
  });
  return run();
}

function getTransactionStats(startDate, endDate) {
  const totalIncome = db.prepare(
    "SELECT COALESCE(SUM(amount), 0) as total FROM transactions WHERE is_deleted = 0 AND type = 'INCOME' AND date BETWEEN ? AND ?"
  ).get(startDate, endDate).total;

  const totalExpense = db.prepare(
    "SELECT COALESCE(SUM(amount), 0) as total FROM transactions WHERE is_deleted = 0 AND type = 'EXPENSE' AND date BETWEEN ? AND ?"
  ).get(startDate, endDate).total;

  const categoryTotals = db.prepare(`
    SELECT c.name, c.color, SUM(t.amount) as total
    FROM transactions t
    JOIN categories c ON t.category_id = c.id
    WHERE t.is_deleted = 0 AND t.type = 'EXPENSE' AND t.date BETWEEN ? AND ?
    GROUP BY t.category_id
    ORDER BY total DESC
  `).all(startDate, endDate);

  return { totalIncome, totalExpense, categoryTotals };
}

// === 分类 CRUD ===

function getAllCategories() {
  return db.prepare('SELECT * FROM categories WHERE is_deleted = 0 ORDER BY sort_order').all();
}

function addCategory(c) {
  return db.prepare(
    'INSERT INTO categories (name, type, icon, color) VALUES (?, ?, ?, ?)'
  ).run(c.name, c.type, c.icon || 'circle', c.color || '#6C63FF').lastInsertRowid;
}

function deleteCategory(id) {
  return db.prepare('UPDATE categories SET is_deleted = 1 WHERE id = ?').run(id);
}

// === 账户 CRUD ===

function getAllAccounts() {
  return db.prepare('SELECT * FROM accounts WHERE is_deleted = 0 ORDER BY sort_order').all();
}

function addAccount(a) {
  return db.prepare(
    'INSERT INTO accounts (name, type, icon, color) VALUES (?, ?, ?, ?)'
  ).run(a.name, a.type, a.icon || 'wallet', a.color || '#2ECC71').lastInsertRowid;
}

function updateAccountBalance(id, amount) {
  return db.prepare('UPDATE accounts SET balance = balance + ? WHERE id = ?').run(amount, id);
}

// === 预算 CRUD ===

/**
 * 获取所有预算（含分类信息与当期已用金额）
 * period: MONTHLY(月) | WEEKLY(周) | YEARLY(年)
 * category_id 为 NULL 表示"总预算"（不限分类）
 */
function getAllBudgets() {
  const budgets = db.prepare(`
    SELECT b.*, c.name AS category_name, c.color AS category_color, c.icon AS category_icon
    FROM budgets b
    LEFT JOIN categories c ON b.category_id = c.id
    WHERE b.is_deleted = 0
    ORDER BY b.id DESC
  `).all();

  // 计算每个预算当期已用金额
  return budgets.map((b) => {
    const [start, end] = currentPeriodRange(b.period);
    let used;
    if (b.category_id == null) {
      // 总预算：统计当期所有支出
      used = db.prepare(
        "SELECT COALESCE(SUM(amount),0) AS t FROM transactions WHERE is_deleted=0 AND type='EXPENSE' AND date BETWEEN ? AND ?"
      ).get(start, end).t;
    } else {
      // 分类预算：仅统计该分类支出
      used = db.prepare(
        "SELECT COALESCE(SUM(amount),0) AS t FROM transactions WHERE is_deleted=0 AND type='EXPENSE' AND category_id=? AND date BETWEEN ? AND ?"
      ).get(b.category_id, start, end).t;
    }
    return { ...b, used, periodStart: start, periodEnd: end };
  });
}

/** 计算指定周期的当期起止时间戳 */
function currentPeriodRange(period) {
  const now = new Date();
  let start, end;
  if (period === 'WEEKLY') {
    const day = now.getDay() || 7; // 周一为一周起点
    start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - day + 1).getTime();
    end = start + 7 * 86400000 - 1;
  } else if (period === 'YEARLY') {
    start = new Date(now.getFullYear(), 0, 1).getTime();
    end = new Date(now.getFullYear() + 1, 0, 1).getTime() - 1;
  } else {
    // MONTHLY 默认
    start = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
    end = new Date(now.getFullYear(), now.getMonth() + 1, 1).getTime() - 1;
  }
  return [start, end];
}

function addBudget(b) {
  return db.prepare(
    'INSERT INTO budgets (category_id, amount, period, start_date) VALUES (?, ?, ?, ?)'
  ).run(b.categoryId || null, b.amount, b.period || 'MONTHLY', b.startDate || Date.now()).lastInsertRowid;
}

function updateBudget(id, b) {
  return db.prepare(
    'UPDATE budgets SET category_id = ?, amount = ?, period = ? WHERE id = ?'
  ).run(b.categoryId || null, b.amount, b.period || 'MONTHLY', id);
}

function deleteBudget(id) {
  return db.prepare('UPDATE budgets SET is_deleted = 1 WHERE id = ?').run(id);
}

// === 导入导出 ===

function exportAllData() {
  return {
    version: '1.0',
    exportedAt: Date.now(),
    deviceId: `desktop-${require('os').hostname()}`,
    data: {
      transactions: db.prepare('SELECT * FROM transactions WHERE is_deleted = 0').all(),
      categories: db.prepare('SELECT * FROM categories WHERE is_deleted = 0').all(),
      accounts: db.prepare('SELECT * FROM accounts WHERE is_deleted = 0').all(),
      budgets: db.prepare('SELECT * FROM budgets WHERE is_deleted = 0').all(),
    },
  };
}

function importData(exportData) {
  const data = exportData.data;
  let count = 0;

  const run = db.transaction(() => {
    // 导入分类
    const existingCategories = new Set(
      db.prepare('SELECT id FROM categories').all().map((r) => r.id)
    );
    for (const c of data.categories) {
      if (!existingCategories.has(c.id)) {
        db.prepare(
          'INSERT OR REPLACE INTO categories (id, name, type, icon, color, parent_id, sort_order, is_system, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
        ).run(c.id, c.name, c.type, c.icon, c.color, c.parent_id, c.sort_order, c.is_system, c.is_deleted);
      }
    }

    // 导入账户
    const existingAccounts = new Set(
      db.prepare('SELECT id FROM accounts').all().map((r) => r.id)
    );
    for (const a of data.accounts) {
      if (!existingAccounts.has(a.id)) {
        db.prepare(
          'INSERT OR REPLACE INTO accounts (id, name, type, icon, color, balance, currency, sort_order, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
        ).run(a.id, a.name, a.type, a.icon, a.color, a.balance, a.currency, a.sort_order, a.is_deleted);
      }
    }

    // 导入交易
    const existingTransactions = new Set(
      db.prepare('SELECT id FROM transactions').all().map((r) => r.id)
    );
    for (const t of data.transactions) {
      if (!existingTransactions.has(t.id)) {
        db.prepare(
          'INSERT OR REPLACE INTO transactions (id, type, amount, category_id, account_id, to_account_id, note, tags, date, created_at, updated_at, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
        ).run(t.id, t.type, t.amount, t.category_id, t.account_id, t.to_account_id, t.note, t.tags, t.date, t.created_at, t.updated_at, t.is_deleted);
        count++;
      }
    }

    // 导入预算（兼容旧版无 budgets 字段的备份）
    if (Array.isArray(data.budgets)) {
      const existingBudgets = new Set(
        db.prepare('SELECT id FROM budgets').all().map((r) => r.id)
      );
      for (const b of data.budgets) {
        if (!existingBudgets.has(b.id)) {
          db.prepare(
            'INSERT OR REPLACE INTO budgets (id, category_id, amount, period, start_date, is_deleted) VALUES (?, ?, ?, ?, ?, ?)'
          ).run(b.id, b.category_id, b.amount, b.period, b.start_date, b.is_deleted);
        }
      }
    }
  });

  run();
  return count;
}

function exportToCsv() {
  const transactions = db.prepare('SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY date DESC').all();
  const categories = new Map(db.prepare('SELECT id, name FROM categories').all().map((c) => [c.id, c.name]));
  const accounts = new Map(db.prepare('SELECT id, name FROM accounts').all().map((a) => [a.id, a.name]));

  const header = '日期,类型,金额,分类,账户,备注';
  const rows = transactions.map((t) => {
    const date = new Date(t.date).toLocaleString('zh-CN');
    const type = t.type === 'INCOME' ? '收入' : t.type === 'EXPENSE' ? '支出' : '转账';
    const amount = (t.amount / 100).toFixed(2);
    const category = categories.get(t.category_id) || '未知';
    const account = accounts.get(t.account_id) || '未知';
    const note = (t.note || '').replace(/,/g, '，');
    return `${date},${type},${amount},${category},${account},${note}`;
  });

  return [header, ...rows].join('\n');
}

function close() {
  if (db) db.close();
}

module.exports = {
  init,
  close,
  getAllTransactions,
  getTransactionsByDateRange,
  addTransaction,
  updateTransaction,
  deleteTransaction,
  getTransactionStats,
  getAllCategories,
  addCategory,
  deleteCategory,
  getAllAccounts,
  addAccount,
  updateAccountBalance,
  getAllBudgets,
  addBudget,
  updateBudget,
  deleteBudget,
  getAllRecurringRules,
  addRecurringRule,
  updateRecurringRule,
  deleteRecurringRule,
  processRecurringRules,
  exportAllData,
  importData,
  exportToCsv,
};
