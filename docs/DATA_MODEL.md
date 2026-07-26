# 数据模型文档

> 最后更新: 2026-07-25

## 1. 核心实体

### Transaction（交易记录）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| type | Enum | INCOME / EXPENSE / TRANSFER |
| amount | Decimal | 金额（单位：分，避免浮点误差） |
| category_id | Long (FK) | 分类外键 |
| account_id | Long (FK) | 账户外键 |
| to_account_id | Long? | 转入账户（仅TRANSFER） |
| note | String? | 备注 |
| tags | String? | 标签（JSON数组） |
| date | Long | 交易时间戳 |
| created_at | Long | 创建时间 |
| updated_at | Long | 更新时间 |
| is_deleted | Boolean | 软删除标记 |

### Category（分类）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| name | String | 分类名称 |
| type | Enum | INCOME / EXPENSE |
| icon | String | Material Icon 名称 |
| color | String | 颜色值（#RRGGBB） |
| parent_id | Long? | 父分类（支持子分类） |
| sort_order | Int | 排序顺序 |
| is_system | Boolean | 是否系统内置 |
| is_deleted | Boolean | 软删除标记 |

### Account（账户）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| name | String | 账户名称 |
| type | Enum | CASH / BANK_CARD / ALIPAY / WECHAT / CREDIT_CARD / OTHER |
| icon | String | 图标名称 |
| color | String | 颜色值 |
| balance | Decimal | 当前余额（单位：分） |
| currency | String | 货币代码（默认CNY） |
| sort_order | Int | 排序 |
| is_deleted | Boolean | 软删除标记 |

### Budget（预算）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| category_id | Long? | 关联分类（null=总预算） |
| amount | Decimal | 预算金额（单位：分） |
| period | Enum | MONTHLY / WEEKLY / YEARLY |
| start_date | Long | 开始日期 |
| is_deleted | Boolean | 软删除标记 |

## 2. 枚举定义

### TransactionType
```
INCOME    - 收入
EXPENSE   - 支出
TRANSFER  - 转账
```

### AccountType
```
CASH        - 现金
BANK_CARD   - 银行卡
ALIPAY      - 支付宝
WECHAT      - 微信
CREDIT_CARD - 信用卡
OTHER       - 其他
```

### BudgetPeriod
```
MONTHLY - 月度
WEEKLY  - 周度
YEARLY  - 年度
```

## 3. 默认分类

### 支出分类
| 名称 | 图标 | 颜色 |
|---|---|---|
| 餐饮 | restaurant | #FF6B6B |
| 交通 | directions_car | #4ECDC4 |
| 购物 | shopping_bag | #45B7D1 |
| 住房 | home | #96CEB4 |
| 娱乐 | sports_esports | #FFEAA7 |
| 医疗 | local_hospital | #DDA0DD |
| 教育 | school | #98D8C8 |
| 通讯 | phone | #F7DC6F |
| 服饰 | checkroom | #E8A0BF |
| 日用 | shopping_cart | #AED6F1 |
| 其他 | more_horiz | #BDC3C7 |

### 收入分类
| 名称 | 图标 | 颜色 |
|---|---|---|
| 工资 | work | #2ECC71 |
| 奖金 | emoji_events | #F1C40F |
| 投资 | trending_up | #E67E22 |
| 兼职 | laptop | #9B59B6 |
| 礼金 | card_giftcard | #E74C3C |
| 其他 | more_horiz | #95A5A6 |

## 4. SQL Schema (Room)

```sql
-- 交易记录表
CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    amount INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    account_id INTEGER NOT NULL,
    to_account_id INTEGER,
    note TEXT,
    tags TEXT,
    date INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)
);

-- 分类表
CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    icon TEXT NOT NULL,
    color TEXT NOT NULL,
    parent_id INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_system INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (parent_id) REFERENCES categories(id)
);

-- 账户表
CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    icon TEXT NOT NULL,
    color TEXT NOT NULL,
    balance INTEGER NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'CNY',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0
);

-- 预算表
CREATE TABLE budgets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id INTEGER,
    amount INTEGER NOT NULL,
    period TEXT NOT NULL,
    start_date INTEGER NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- 索引
CREATE INDEX idx_transactions_date ON transactions(date);
CREATE INDEX idx_transactions_category ON transactions(category_id);
CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_type ON transactions(type);
```

## 5. 数据存储说明

### 金额存储
- 所有金额以**分**为单位存储（Integer）
- 显示时除以100，保留2位小数
- 避免浮点数精度问题

### 时间存储
- 所有时间以 **Unix 时间戳（毫秒）** 存储
- 显示时根据用户设置的时区转换

### 软删除
- 所有表都有 `is_deleted` 字段
- 删除操作只设置标记，不物理删除
- 导出时排除已删除记录
- 定期清理（可选功能）

## 6. ER 图

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Category    │     │ Transaction  │     │   Account    │
├──────────────┤     ├──────────────┤     ├──────────────┤
│ id (PK)      │◄────│ category_id  │     │ id (PK)      │
│ name         │     │ account_id   │────►│ name         │
│ type         │     │ to_account_id│────►│ type         │
│ icon         │     │ id (PK)      │     │ balance      │
│ color        │     │ type         │     │ icon         │
│ parent_id ───┼─┐   │ amount       │     │ color        │
│ sort_order   │ │   │ note         │     │ currency     │
│ is_system    │ │   │ tags         │     │ sort_order   │
│ is_deleted   │ │   │ date         │     │ is_deleted   │
└──────────────┘ │   │ created_at   │     └──────────────┘
                 │   │ updated_at   │
                 └──►│ is_deleted   │
                     └──────────────┘

┌──────────────┐
│   Budget     │
├──────────────┤
│ id (PK)      │
│ category_id ─┼────► Category (optional)
│ amount       │
│ period       │
│ start_date   │
│ is_deleted   │
└──────────────┘
```
