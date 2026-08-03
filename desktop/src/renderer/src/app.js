// ============================================================================
// 记账单桌面客户端 v1.5
// 技术栈: Electron + React 18 (CDN) + better-sqlite3
// 架构: 单文件 React 应用，通过 IPC 与主进程数据库通信
// ============================================================================

const { useState, useEffect, useMemo, createContext, useContext, useRef } = React;

// ============================================================================
// 一、智能解析引擎 —— 把 "早餐 10" 之类的自然语言解析为交易数据
// ============================================================================

// 关键词 → 分类映射表（支持中英文输入）
const KEYWORD_MAP = {
  // --- 支出：餐饮 ---
  '早餐': '餐饮', '早饭': '餐饮', '午餐': '餐饮', '午饭': '餐饮',
  '晚餐': '餐饮', '晚饭': '餐饮', '吃饭': '餐饮', '外卖': '餐饮',
  '饭': '餐饮', '餐': '餐饮', '咖啡': '餐饮', '奶茶': '餐饮',
  '饮料': '餐饮', '水果': '餐饮', '零食': '餐饮', '火锅': '餐饮',
  '烧烤': '餐饮', '聚餐': '餐饮', '下午茶': '餐饮', '宵夜': '餐饮',
  // --- 支出：交通 ---
  '打车': '交通', '出租': '交通', '滴滴': '交通', '地铁': '交通',
  '公交': '交通', '高铁': '交通', '火车': '交通', '飞机': '交通',
  '机票': '交通', '加油': '交通', '停车': '交通', '车费': '交通',
  // --- 支出：购物 ---
  '购物': '购物', '买': '购物', '淘宝': '购物', '京东': '购物',
  '拼多多': '购物', '超市': '购物',
  // --- 支出：住房 ---
  '房租': '住房', '水电': '住房', '物业': '住房', '燃气': '住房',
  // --- 支出：娱乐 ---
  '电影': '娱乐', '游戏': '娱乐', 'KTV': '娱乐', '旅游': '娱乐',
  '门票': '娱乐', '健身': '娱乐',
  // --- 支出：医疗 ---
  '医院': '医疗', '药': '医疗', '看病': '医疗', '体检': '医疗',
  // --- 支出：教育 ---
  '书': '教育', '课程': '教育', '培训': '教育', '学费': '教育',
  // --- 支出：通讯 ---
  '话费': '通讯', '流量': '通讯', '宽带': '通讯', '充值': '通讯',
  // --- 支出：服饰 ---
  '衣服': '服饰', '鞋': '服饰', '裤子': '服饰', '帽子': '服饰',
  // --- 支出：日用 ---
  '日用': '日用', '纸巾': '日用', '洗衣液': '日用', '牙膏': '日用',
  // --- 收入 ---
  '工资': '工资', '薪水': '工资', '发工资': '工资', '月薪': '工资',
  '奖金': '奖金', '年终奖': '奖金', '红包': '礼金', '礼金': '礼金',
  '投资': '投资', '理财': '投资', '分红': '投资', '利息': '投资',
  '兼职': '兼职', '外快': '兼职', '报销': '其他', '退款': '其他',
};

// 收入分类名集合（用于判断类型）
const INCOME_CATEGORIES = new Set(['工资', '奖金', '礼金', '投资', '兼职', '其他']);

/**
 * 解析快捷输入文本
 * @param {string} text - 用户输入，如 "早餐 10"、"工资 8000"
 * @param {Array} categories - 数据库中的分类列表
 * @returns {Object|null} - { amount, note, type, categoryId, categoryName, categoryColor }
 */
function parseQuickInput(text, categories) {
  text = text.trim();
  if (!text) return null;

  // 提取金额：支持 10、10.5、10元、¥10、10块
  const amountMatch = text.match(/(?:¥|￥)?\s*(\d+\.?\d*)\s*(?:元|块|¥)?/);
  if (!amountMatch) return null;

  const amount = parseFloat(amountMatch[1]);
  if (isNaN(amount) || amount <= 0) return null;

  // 提取备注（去掉金额部分和无意义前缀）
  let note = text.replace(amountMatch[0], '').trim()
    .replace(/^(了|花|收|入|出|支|付|消费|收入|支出|收到)/, '').trim();

  // 关键词匹配分类
  let matchedCatName = null;
  let matchedType = 'EXPENSE';

  for (const [keyword, catName] of Object.entries(KEYWORD_MAP)) {
    if (text.includes(keyword)) {
      matchedCatName = catName;
      matchedType = INCOME_CATEGORIES.has(catName) ? 'INCOME' : 'EXPENSE';
      if (!note) note = keyword;
      break;
    }
  }

  // 模糊匹配分类名
  if (!matchedCatName && note) {
    const found = categories.find(c => note.includes(c.name) || c.name.includes(note));
    if (found) { matchedCatName = found.name; matchedType = found.type; }
  }

  // 查找完整分类对象
  const catObj = matchedCatName
    ? categories.find(c => c.name === matchedCatName && c.type === matchedType)
    : null;

  return {
    amount,
    note: note || matchedCatName || '记账',
    type: matchedType,
    categoryId: catObj?.id || null,
    categoryName: catObj?.name || matchedCatName || null,
    categoryColor: catObj?.color || '#6C63FF',
  };
}

// ============================================================================
// 二、国际化 —— 中英文翻译
// ============================================================================

const LANG = {
  zh: {
    appName: '记账单',
    home: '首页', transactions: '交易记录', summary: '总结报告',
    statistics: '统计', calculator: '计算器', settings: '设置',
    totalAsset: '总资产', monthIncome: '本月收入', monthExpense: '本月支出',
    todayIncome: '今日收入', todayExpense: '今日支出', balance: '结余',
    recentTx: '最近交易', viewAll: '查看全部',
    noTx: '暂无交易记录，点击右下角 + 开始记账',
    addTx: '记一笔', expense: '支出', income: '收入',
    amount: '金额', note: '备注', date: '日期', category: '分类', account: '账户',
    save: '保存', cancel: '取消', delete: '删除', confirm: '确认',
    all: '全部', search: '搜索备注或分类...', total: '共 {n} 条',
    thisWeek: '本周', thisMonth: '本月', thisQuarter: '本季度', thisYear: '本年',
    dailyExpense: '每日支出', categoryRank: '支出分类排行', noData: '暂无数据',
    exportJson: '导出数据 (JSON)', exportJsonDesc: '导出为 JSON 文件，可用于同步到手机',
    importJson: '导入数据 (JSON)', importJsonDesc: '从 JSON 文件导入数据',
    exportCsv: '导出 CSV', exportCsvDesc: '导出为 Excel 可打开的 CSV 文件',
    accountMgmt: '账户管理', about: '关于',
    exportSuccess: '导出成功！', importSuccess: '导入成功！共 {n} 条', csvSuccess: 'CSV 导出成功！',
    language: '语言', switchLang: '切换语言',
    background: '背景图片', bgDesc: '自定义应用背景图片', bgOpacity: '背景透明度',
    noBg: '无背景', chooseBg: '选择图片', bgReset: '重置背景',
    cash: '现金', bankCard: '银行卡', alipay: '支付宝', wechat: '微信',
    creditCard: '信用卡', other: '其他',
    version: '记账单 v1.5.0', versionDesc: '多端记账管理应用',
    quickAdd: '快捷记账', quickAddPlaceholder: '输入如：早餐 10、打车 30、工资 8000',
    quickAddHint: 'Enter 记账，Esc 取消', parsedAs: '识别为', quickAddSuccess: '记账成功！',
    totalIncome: '总收入', totalExpense: '总支出', totalBalance: '结余',
    txCount: '交易笔数', avgDailyExpense: '日均支出',
    prevPeriod: '上一周期', comparedToPrev: '较上期',
    topCategories: '支出 TOP 5 分类', topExpenses: '最大支出 TOP 5',
    noTxInPeriod: '该周期暂无交易记录', savingRate: '储蓄率',
    budget: '预算', budgetMgmt: '预算管理', addBudget: '新增预算', editBudget: '编辑预算',
    budgetTotal: '总预算', budgetCategory: '分类预算', budgetAmount: '预算金额',
    budgetPeriod: '周期', periodMonthly: '每月', periodWeekly: '每周', periodYearly: '每年',
    budgetUsed: '已用', budgetLeft: '剩余', budgetOver: '超支',
    noBudget: '还没有预算，点击右上角新增，控制你的开支',
    budgetExceeded: '已超支！', budgetWarning: '接近预算上限',
    selectCategory: '选择分类（留空为总预算）', budgetDeleteConfirm: '确定删除此预算？',
    pieChart: '支出分类占比', trendChart: '收支趋势', incomeLegend: '收入', expenseLegend: '支出',
    recurring: '循环记账', recurringMgmt: '循环记账管理', addRecurring: '新增循环', editRecurring: '编辑循环',
    recurPeriodDaily: '每天', recurPeriodWeekly: '每周', recurPeriodMonthly: '每月',
    nextRun: '下次记账', autoCreate: '自动记账', noRecurring: '还没有循环记账规则，如房租、工资等固定收支',
    recurringDeleteConfirm: '确定删除此循环规则？', startDate: '开始日期', recurAmount: '金额',
  },
  en: {
    appName: 'Bookkeeper',
    home: 'Home', transactions: 'Transactions', summary: 'Summary',
    statistics: 'Statistics', calculator: 'Calculator', settings: 'Settings',
    totalAsset: 'Total Assets', monthIncome: 'Month Income', monthExpense: 'Month Expense',
    todayIncome: 'Today Income', todayExpense: 'Today Expense', balance: 'Balance',
    recentTx: 'Recent Transactions', viewAll: 'View All',
    noTx: 'No transactions yet. Tap + to start.',
    addTx: 'Add Transaction', expense: 'Expense', income: 'Income',
    amount: 'Amount', note: 'Note', date: 'Date', category: 'Category', account: 'Account',
    save: 'Save', cancel: 'Cancel', delete: 'Delete', confirm: 'Confirm',
    all: 'All', search: 'Search note or category...', total: '{n} records',
    thisWeek: 'Week', thisMonth: 'Month', thisQuarter: 'Quarter', thisYear: 'Year',
    dailyExpense: 'Daily Expense', categoryRank: 'Category Ranking', noData: 'No data',
    exportJson: 'Export JSON', exportJsonDesc: 'Export data as JSON for sync',
    importJson: 'Import JSON', importJsonDesc: 'Import data from JSON file',
    exportCsv: 'Export CSV', exportCsvDesc: 'Export as CSV for Excel',
    accountMgmt: 'Accounts', about: 'About',
    exportSuccess: 'Exported!', importSuccess: 'Imported {n} records!', csvSuccess: 'CSV exported!',
    language: 'Language', switchLang: 'Switch Language',
    background: 'Background', bgDesc: 'Customize app background image', bgOpacity: 'Opacity',
    noBg: 'None', chooseBg: 'Choose Image', bgReset: 'Reset',
    cash: 'Cash', bankCard: 'Bank Card', alipay: 'Alipay', wechat: 'WeChat',
    creditCard: 'Credit Card', other: 'Other',
    version: 'Bookkeeper v1.5.0', versionDesc: 'Multi-platform expense tracker',
    quickAdd: 'Quick Add', quickAddPlaceholder: 'e.g. breakfast 10, taxi 30, salary 8000',
    quickAddHint: 'Enter to save, Esc to cancel', parsedAs: 'Detected', quickAddSuccess: 'Saved!',
    totalIncome: 'Total Income', totalExpense: 'Total Expense', totalBalance: 'Balance',
    txCount: 'Transactions', avgDailyExpense: 'Avg Daily Expense',
    prevPeriod: 'Previous', comparedToPrev: 'vs Previous',
    topCategories: 'Top 5 Categories', topExpenses: 'Top 5 Expenses',
    noTxInPeriod: 'No transactions in this period', savingRate: 'Saving Rate',
    budget: 'Budget', budgetMgmt: 'Budgets', addBudget: 'Add Budget', editBudget: 'Edit Budget',
    budgetTotal: 'Total Budget', budgetCategory: 'Category Budget', budgetAmount: 'Budget Amount',
    budgetPeriod: 'Period', periodMonthly: 'Monthly', periodWeekly: 'Weekly', periodYearly: 'Yearly',
    budgetUsed: 'Used', budgetLeft: 'Left', budgetOver: 'Over',
    noBudget: 'No budgets yet. Tap + to control your spending.',
    budgetExceeded: 'Over budget!', budgetWarning: 'Approaching limit',
    selectCategory: 'Select category (empty = total budget)', budgetDeleteConfirm: 'Delete this budget?',
    pieChart: 'Expense Breakdown', trendChart: 'Income vs Expense', incomeLegend: 'Income', expenseLegend: 'Expense',
    recurring: 'Recurring', recurringMgmt: 'Recurring', addRecurring: 'Add Recurring', editRecurring: 'Edit Recurring',
    recurPeriodDaily: 'Daily', recurPeriodWeekly: 'Weekly', recurPeriodMonthly: 'Monthly',
    nextRun: 'Next Run', autoCreate: 'Auto Create', noRecurring: 'No recurring rules yet (rent, salary, etc.)',
    recurringDeleteConfirm: 'Delete this recurring rule?', startDate: 'Start Date', recurAmount: 'Amount',
  },
};

// 翻译上下文
const LangContext = createContext();
function t(key, params) {
  const lang = useContext(LangContext);
  let str = LANG[lang]?.[key] || LANG['zh'][key] || key;
  if (params) Object.entries(params).forEach(([k, v]) => str = str.replace(`{${k}}`, v));
  return str;
}

// ============================================================================
// 三、工具函数
// ============================================================================

/** 分 → 元，保留两位小数 */
const fmtMoney = (cents) => (cents / 100).toFixed(2);

/** 带符号的金额显示 */
const fmtSigned = (cents, type) => `${type === 'INCOME' ? '+' : '-'}¥${fmtMoney(cents)}`;

/** 时间戳 → "7/25 12:30" */
const fmtTime = (ts) => {
  const d = new Date(ts);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

/** 时间戳 → "2026/7/25" */
const fmtDay = (ts) => { const d = new Date(ts); return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`; };

/** 今天日期字符串 "2026-07-25" */
const todayStr = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };

// ============================================================================
// 四、周期计算 —— 用于总结报告页
// ============================================================================

/**
 * 计算指定周期的起止时间和上一周期
 * @param {'week'|'month'|'quarter'|'year'} period
 * @returns {{ start, end, prevStart, prevEnd, days, label }}
 */
function getPeriodRange(period) {
  const now = new Date();
  let start, end, prevStart, prevEnd, days, label;

  if (period === 'week') {
    const day = now.getDay() || 7;
    start = new Date(now); start.setDate(now.getDate() - day + 1); start.setHours(0, 0, 0, 0);
    end = new Date(start); end.setDate(start.getDate() + 7);
    prevStart = new Date(start); prevStart.setDate(start.getDate() - 7);
    prevEnd = new Date(start);
    days = 7;
    const e = new Date(end.getTime() - 86400000);
    label = `${start.getMonth() + 1}/${start.getDate()} - ${e.getMonth() + 1}/${e.getDate()}`;
  } else if (period === 'month') {
    start = new Date(now.getFullYear(), now.getMonth(), 1);
    end = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    prevStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    prevEnd = new Date(now.getFullYear(), now.getMonth(), 1);
    days = Math.round((end - start) / 86400000);
    label = `${now.getFullYear()}年${now.getMonth() + 1}月`;
  } else if (period === 'quarter') {
    const q = Math.floor(now.getMonth() / 3);
    start = new Date(now.getFullYear(), q * 3, 1);
    end = new Date(now.getFullYear(), q * 3 + 3, 1);
    prevStart = new Date(now.getFullYear(), q * 3 - 3, 1);
    prevEnd = new Date(now.getFullYear(), q * 3, 1);
    days = Math.round((end - start) / 86400000);
    label = `${now.getFullYear()}年Q${q + 1} (${q * 3 + 1}-${q * 3 + 3}月)`;
  } else {
    start = new Date(now.getFullYear(), 0, 1);
    end = new Date(now.getFullYear() + 1, 0, 1);
    prevStart = new Date(now.getFullYear() - 1, 0, 1);
    prevEnd = new Date(now.getFullYear(), 0, 1);
    days = 365;
    label = `${now.getFullYear()}年`;
  }

  return { start: start.getTime(), end: end.getTime(), prevStart: prevStart.getTime(), prevEnd: prevEnd.getTime(), days, label };
}

// ============================================================================
// 五、全局状态管理（简易 Zustand 替代）
// ============================================================================

const store = {
  // --- 数据 ---
  transactions: [], categories: [], accounts: [], budgets: [], recurring: [],
  // --- UI 状态 ---
  currentPage: 'home', confirmDialog: null,
  // --- 用户设置（持久化到 localStorage）---
  lang: localStorage.getItem('bookkeeper-lang') || 'zh',
  bgImage: localStorage.getItem('bookkeeper-bg') || '',
  masterOpacity: parseFloat(localStorage.getItem('bookkeeper-master-opacity') || '1'),
  bgOpacity: parseFloat(localStorage.getItem('bookkeeper-bg-opacity') || '0.3'),
  sidebarOpacity: parseFloat(localStorage.getItem('bookkeeper-sidebar-opacity') || '1'),
  cardOpacity: parseFloat(localStorage.getItem('bookkeeper-card-opacity') || '1'),
  // --- 响应式 ---
  listeners: new Set(),
  subscribe(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); },
  notify() { this.listeners.forEach(fn => fn()); },

  // --- 数据操作 ---
  async loadAll() {
    this.transactions = await window.api.transactions.getAll();
    this.categories = await window.api.categories.getAll();
    this.accounts = await window.api.accounts.getAll();
    this.budgets = await window.api.budgets.getAll();
    this.recurring = await window.api.recurring.getAll();
    this.notify();
  },
  async addTransaction(t) { await window.api.transactions.add(t); await this.loadAll(); },
  async deleteTransaction(id) { await window.api.transactions.delete(id); await this.loadAll(); },
  // --- 预算操作 ---
  async addBudget(b) { await window.api.budgets.add(b); await this.loadAll(); },
  async updateBudget(id, b) { await window.api.budgets.update(id, b); await this.loadAll(); },
  async deleteBudget(id) { await window.api.budgets.delete(id); await this.loadAll(); },

  // --- 循环记账操作 ---
  async addRecurring(r) { await window.api.recurring.add(r); await this.loadAll(); },
  async updateRecurring(id, r) { await window.api.recurring.update(id, r); await this.loadAll(); },
  async deleteRecurring(id) { await window.api.recurring.delete(id); await this.loadAll(); },

  // --- 导航 ---
  navigate(page) { this.currentPage = page; this.notify(); },

  // --- 设置（自动持久化）---
  _save(key, val) { localStorage.setItem(`bookkeeper-${key}`, val.toString()); this.notify(); },
  setLang(v) { this.lang = v; localStorage.setItem('bookkeeper-lang', v); this.notify(); },
  setBgImage(v) { this.bgImage = v; localStorage.setItem('bookkeeper-bg', v); this.notify(); },
  setMasterOpacity(v) { this.masterOpacity = v; this._save('master-opacity', v); },
  setBgOpacity(v) { this.bgOpacity = v; this._save('bg-opacity', v); },
  setSidebarOpacity(v) { this.sidebarOpacity = v; this._save('sidebar-opacity', v); },
  setCardOpacity(v) { this.cardOpacity = v; this._save('card-opacity', v); },

  // --- 弹窗 ---
  showConfirm(title, message, onConfirm) { this.confirmDialog = { title, message, onConfirm }; this.notify(); },
  hideConfirm() { this.confirmDialog = null; this.notify(); },

  // --- 计算器入口（由 App 组件注入）---
  openCalc: null,
};

/** React Hook：订阅 store 变化自动重渲染 */
function useStore() {
  const [, tick] = useState(0);
  useEffect(() => store.subscribe(() => tick(t => t + 1)), []);
  return store;
}

// ============================================================================
// 六、通用组件
// ============================================================================

/** 确认弹窗 */
function ConfirmDialog({ title, message, onConfirm, onDismiss }) {
  return h('div.modal-overlay', { onClick: e => e.target === e.currentTarget && onDismiss() },
    h('div.modal', { style: { maxWidth: 360 } },
      h('div.modal-title', null, title),
      h('p', { style: { color: 'var(--text-secondary)', marginBottom: 20 } }, message),
      h('div.modal-actions', null,
        h('button.btn.btn-outline', { onClick: onDismiss }, t('cancel')),
        h('button.btn.btn-primary', { onClick: onConfirm }, t('confirm'))
      )
    )
  );
}

/** 透明度滑块（设置页用）*/
function OpaSlider({ icon, label, value, min = 0, onChange }) {
  return h('div', { style: { display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0' } },
    h('span', { style: { fontSize: 16, width: 24, textAlign: 'center' } }, icon),
    h('span', { style: { flex: 1, fontSize: 13 } }, label),
    h('span', { style: { fontSize: 13, fontWeight: 600, color: 'var(--primary)', minWidth: 36, textAlign: 'right' } }, `${Math.round(value * 100)}%`),
    h('input', { type: 'range', min, max: 1, step: 0.01, value, style: { width: 120, accentColor: 'var(--primary)' }, onChange: e => onChange(parseFloat(e.target.value)) })
  );
}

/** 设置项（带图标、标题、副标题）*/
function SettingsItem({ icon, title, subtitle, onClick }) {
  return h('div.settings-item', { onClick },
    h('div.settings-icon', null, icon),
    h('div.settings-info', null,
      h('div.settings-title', null, title),
      h('div.settings-subtitle', null, subtitle)
    ),
    h('span', { style: { color: 'var(--text-hint)' } }, '›')
  );
}

// ============================================================================
// 七、快捷创建 ReactElement 的辅助函数
// ============================================================================

/**
 * 简写 React.createElement，支持 CSS 选择器语法
 * h('div.card', { style: {...} }, child1, child2)
 * h('button.btn.btn-primary', null, '保存')
 */
/** 数组版 h()，避免三元表达式中 ...spread 的语法问题 */
function hl(tag, props, arr) { return React.createElement(tag, props, ...arr); }

function h(tag, props, ...children) {
  const kids = children.filter(c => c !== null && c !== undefined);
  // 组件函数 / Fragment / 其他非字符串标签：直接透传，不做 CSS 选择器解析
  // 否则 h(App) / h(BudgetPage) / h(React.Fragment) 会因 tag.match 崩溃导致白屏
  if (typeof tag !== 'string') {
    return React.createElement(tag, props, ...kids);
  }
  // 解析 CSS 选择器：div.card#id → { tag: 'div', className: 'card', id: 'id' }
  const sel = tag.match(/^(\w+)?(#\w+)?(\.\S+)?/);
  const el = sel?.[1] || 'div';
  const id = sel?.[2]?.slice(1);
  const cls = sel?.[3]?.split('.').filter(Boolean).join(' ');

  const merged = { ...props };
  if (id && !merged.id) merged.id = id;
  if (cls) merged.className = [cls, merged.className].filter(Boolean).join(' ');

  return React.createElement(el, merged, ...kids);
}

// ============================================================================
// 八、App 主组件 —— 根组件，管理全局布局和状态
// ============================================================================

function App() {
  const s = useStore();
  const [showCalc, setShowCalc] = useState(false);
  const [calcFillTarget, setCalcFillTarget] = useState(null);
  const [fabPos, setFabPos] = useState({ x: window.innerWidth - 80, y: window.innerHeight - 140 });
  const fabDrag = useRef({ dragging: false, startX: 0, startY: 0, startPosX: 0, startPosY: 0, moved: false });

  useEffect(() => { store.loadAll(); }, []);

  // 注入计算器打开方法
  store.openCalc = (onFill) => { setCalcFillTarget(() => onFill); setShowCalc(true); };

  // --- 浮动按钮拖拽逻辑 ---
  const onFabDown = (e) => {
    const d = fabDrag.current;
    d.dragging = true; d.moved = false;
    d.startX = e.clientX; d.startY = e.clientY;
    d.startPosX = fabPos.x; d.startPosY = fabPos.y;
    document.addEventListener('mousemove', onFabMove);
    document.addEventListener('mouseup', onFabUp);
    e.preventDefault();
  };
  const onFabMove = (e) => {
    const d = fabDrag.current;
    if (!d.dragging) return;
    const dx = e.clientX - d.startX, dy = e.clientY - d.startY;
    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) d.moved = true;
    setFabPos({ x: d.startPosX + dx, y: d.startPosY + dy });
  };
  const onFabUp = () => {
    const d = fabDrag.current;
    d.dragging = false;
    document.removeEventListener('mousemove', onFabMove);
    document.removeEventListener('mouseup', onFabUp);
    if (!d.moved) setShowCalc(true); // 没拖动才算点击
  };
  useEffect(() => () => { document.removeEventListener('mousemove', onFabMove); document.removeEventListener('mouseup', onFabUp); }, []);

  return h(LangContext.Provider, { value: s.lang },
    h('div.app', { style: { position: 'relative' } },
      // --- 全局 CSS：透明度只影响背景色，文字始终不透明 ---
      h('style', null, `
        .sidebar { background: rgba(255,255,255,${s.masterOpacity * s.sidebarOpacity}) !important; }
        .card, .today-card, .settings-item { background: rgba(255,255,255,${s.masterOpacity * s.cardOpacity}) !important; }
        .balance-card { background: linear-gradient(135deg, rgba(108,99,255,${s.masterOpacity * s.cardOpacity}), rgba(90,82,213,${s.masterOpacity * s.cardOpacity})) !important; }
        .settings-subtitle, .today-label, .balance-label, .balance-item-label { color: #555 !important; }
        .chip { background: rgba(0,0,0,0.06) !important; color: #333 !important; border-color: rgba(0,0,0,0.15) !important; }
        .chip.selected { background: rgba(108,99,255,0.15) !important; color: var(--primary) !important; border-color: var(--primary) !important; }
        .date-group { color: #444 !important; }
        .form-label { color: #555 !important; }
        .stat-bar-percent { color: #777 !important; }
      `),
      // --- 背景图层 ---
      s.bgImage && h('div', {
        style: { position: 'fixed', inset: 0, zIndex: 0, backgroundImage: `url(${s.bgImage})`, backgroundSize: 'cover', backgroundPosition: 'center', opacity: s.bgOpacity, pointerEvents: 'none' }
      }),
      // --- 主内容区 ---
      h('div', { style: { position: 'relative', zIndex: 1, display: 'flex', width: '100%', height: '100%' } },
        h(Sidebar, null),
        h('div.main-content', null,
          s.currentPage === 'home' && h(HomePage, null),
          s.currentPage === 'transactions' && h(TransactionsPage, null),
          s.currentPage === 'summary' && h(SummaryPage, null),
          s.currentPage === 'budget' && h(BudgetPage, null),
          s.currentPage === 'recurring' && h(RecurringPage, null),
          s.currentPage === 'statistics' && h(StatisticsPage, null),
          s.currentPage === 'calculator' && h(CalculatorPage, null),
          s.currentPage === 'settings' && h(SettingsPage, null),
        )
      ),
      // --- 确认弹窗 ---
      s.confirmDialog && h(ConfirmDialog, {
        title: s.confirmDialog.title, message: s.confirmDialog.message,
        onConfirm: () => { s.confirmDialog.onConfirm(); store.hideConfirm(); },
        onDismiss: () => store.hideConfirm(),
      }),
      // --- 浮动计算器按钮（可拖拽）---
      !showCalc && h('button', {
        onMouseDown: onFabDown,
        style: { position: 'fixed', left: fabPos.x, top: fabPos.y, zIndex: 98, width: 48, height: 48, borderRadius: '50%', background: 'var(--primary)', color: 'white', border: 'none', fontSize: 22, cursor: 'grab', boxShadow: '0 4px 12px rgba(108,99,255,0.35)', display: 'flex', alignItems: 'center', justifyContent: 'center', userSelect: 'none' },
        title: '拖动移动 / 点击打开计算器',
      }, '🧮'),
      // --- 浮动计算器面板 ---
      showCalc && h(QuickCalc, {
        onFillAmount: val => { if (calcFillTarget) calcFillTarget(val); setShowCalc(false); },
        onClose: () => setShowCalc(false),
      })
    )
  );
}

// ============================================================================
// 九、侧边栏
// ============================================================================

function Sidebar() {
  const s = useStore();
  const pages = [
    { id: 'home', label: t('home'), icon: '🏠' },
    { id: 'transactions', label: t('transactions'), icon: '📋' },
    { id: 'summary', label: t('summary'), icon: '📝' },
    { id: 'budget', label: t('budget'), icon: '🎯' },
    { id: 'recurring', label: t('recurring'), icon: '🔁' },
    { id: 'statistics', label: t('statistics'), icon: '📊' },
    { id: 'calculator', label: t('calculator'), icon: '🧮' },
    { id: 'settings', label: t('settings'), icon: '⚙️' },
  ];

  return h('div.sidebar', null,
    h('div.sidebar-logo', null, `📒 ${t('appName')}`),
    h('nav.sidebar-nav', null,
      ...pages.map(p => h('div', {
        key: p.id,
        className: `nav-item ${s.currentPage === p.id ? 'active' : ''}`,
        onClick: () => store.navigate(p.id),
      }, `${p.icon}  ${p.label}`))
    )
  );
}

// ============================================================================
// 十、交易列表项组件（首页和交易记录页共用）
// ============================================================================

function TransactionItem({ transaction: tx, showDelete = true }) {
  const cat = store.categories.find(c => c.id === tx.category_id);
  const acc = store.accounts.find(a => a.id === tx.account_id);
  const isIncome = tx.type === 'INCOME';
  const isTransfer = tx.type === 'TRANSFER';
  const toAcc = isTransfer ? store.accounts.find(a => a.id === tx.to_account_id) : null;
  const [confirming, setConfirming] = useState(false);

  /** 直接删除（不走全局弹窗，避免上下文丢失）*/
  const handleDelete = async () => {
    try { await window.api.transactions.delete(tx.id); await store.loadAll(); }
    catch (e) { console.error('删除失败:', e); }
  };

  // 图标 class：转账用蓝色 transfer
  const iconClass = isTransfer ? 'transfer' : (isIncome ? 'income' : 'expense');
  const iconText = isTransfer ? '🔄' : (cat ? cat.name[0] : (isIncome ? '收' : '支'));
  // 转账备注：显示"转出账户 → 转入账户"
  const noteText = isTransfer
    ? `${acc?.name || '?'} ${t('transferTo')} ${toAcc?.name || '?'}${tx.note ? ' · ' + tx.note : ''}`
    : (tx.note || cat?.name || '');

  return h('div.transaction-item', { style: { position: 'relative' } },
    // 分类图标
    h(`div.transaction-icon.${iconClass}`, null, iconText),
    // 信息区
    h('div.transaction-info', null,
      h('div.transaction-note', null, isTransfer ? t('transferRecord') : noteText),
      h('div.transaction-date', null, isTransfer ? `${fmtTime(tx.date)} · ${noteText}` : `${fmtTime(tx.date)}${acc ? ' · ' + acc.name : ''}`)
    ),
    // 金额
    h(`div.transaction-amount.${iconClass}`, null, isTransfer ? `¥${fmtMoney(tx.amount)}` : fmtSigned(tx.amount, tx.type)),
    // 删除按钮：点 ✕ → 原地显示 [删除] [取消]
    showDelete && !confirming && h('button', {
      style: { background: 'none', border: 'none', cursor: 'pointer', fontSize: 16, color: '#ccc', marginLeft: 8, padding: '4px 8px', borderRadius: 4 },
      onClick: e => { e.stopPropagation(); setConfirming(true); },
      onMouseEnter: e => e.target.style.color = '#f44336',
      onMouseLeave: e => e.target.style.color = '#ccc',
    }, '✕'),
    showDelete && confirming && h('div', { style: { display: 'flex', gap: 4, marginLeft: 8 } },
      h('button', { style: { background: '#f44336', color: 'white', border: 'none', borderRadius: 4, padding: '4px 10px', fontSize: 12, cursor: 'pointer', fontWeight: 600 }, onClick: e => { e.stopPropagation(); handleDelete(); } }, '删除'),
      h('button', { style: { background: '#eee', border: 'none', borderRadius: 4, padding: '4px 10px', fontSize: 12, cursor: 'pointer' }, onClick: e => { e.stopPropagation(); setConfirming(false); } }, '取消')
    )
  );
}

// ============================================================================
// 十一、快捷记账栏（首页顶部）
// ============================================================================

function QuickInputBar() {
  const s = useStore();
  const [text, setText] = useState('');
  const [parsed, setParsed] = useState(null);
  const [showToast, setShowToast] = useState(false);
  const [forceType, setForceType] = useState(null); // null=自动识别, 'INCOME'|'EXPENSE'=强制
  const inputRef = useRef(null);

  // 实时解析输入
  useEffect(() => {
    if (!text.trim()) { setParsed(null); return; }
    const result = parseQuickInput(text, s.categories);
    if (result && forceType) result.type = forceType; // 手动覆盖类型
    setParsed(result);
  }, [text, s.categories, forceType]);

  /** 保存交易 */
  const handleSave = async () => {
    if (!parsed || s.accounts.length === 0) return;
    await store.addTransaction({
      type: parsed.type,
      amount: Math.round(parsed.amount * 100),
      categoryId: parsed.categoryId || s.categories.find(c => c.type === parsed.type)?.id,
      accountId: s.accounts[0].id,
      note: parsed.note,
      date: Date.now(),
    });
    setText(''); setParsed(null); setForceType(null);
    setShowToast(true); setTimeout(() => setShowToast(false), 1500);
    inputRef.current?.focus();
  };

  // 收支类型切换按钮样式
  const typeBtnStyle = (active, color) => ({
    padding: '6px 10px', borderRadius: 6, border: 'none', fontSize: 12, fontWeight: 600, cursor: 'pointer',
    background: active ? `var(--${color})` : `rgba(${color === 'expense' ? '244,67,54' : '76,175,80'},0.1)`,
    color: active ? 'white' : `var(--${color})`,
  });

  return h('div', { style: { marginBottom: 20 } },
    // 输入行
    h('div', { style: { display: 'flex', alignItems: 'center', gap: 10, background: 'var(--surface)', borderRadius: 16, padding: '12px 16px', boxShadow: '0 2px 12px rgba(108,99,255,0.12)', border: parsed ? '2px solid var(--primary)' : '2px solid transparent', transition: 'border-color 0.2s' } },
      // 收支切换
      h('div', { style: { display: 'flex', gap: 4, marginRight: 4 } },
        h('button', { onClick: () => setForceType(forceType === 'EXPENSE' ? null : 'EXPENSE'), style: typeBtnStyle((!forceType && parsed?.type === 'EXPENSE') || forceType === 'EXPENSE', 'expense') }, '支出'),
        h('button', { onClick: () => setForceType(forceType === 'INCOME' ? null : 'INCOME'), style: typeBtnStyle((!forceType && parsed?.type === 'INCOME') || forceType === 'INCOME', 'income') }, '收入')
      ),
      // 输入框
      h('input', {
        ref: inputRef, type: 'text', value: text,
        onChange: e => setText(e.target.value),
        onKeyDown: e => { if (e.key === 'Enter' && parsed) handleSave(); if (e.key === 'Escape') { setText(''); setParsed(null); setForceType(null); } },
        placeholder: t('quickAddPlaceholder'),
        style: { flex: 1, border: 'none', outline: 'none', fontSize: 16, background: 'transparent', color: 'var(--text-primary)' },
      }),
      // 识别预览标签
      parsed && h('div', { style: { display: 'flex', alignItems: 'center', gap: 8, background: parsed.type === 'INCOME' ? 'rgba(76,175,80,0.1)' : 'rgba(244,67,54,0.1)', padding: '6px 12px', borderRadius: 20, fontSize: 13, whiteSpace: 'nowrap' } },
        h('span', { style: { background: parsed.categoryColor + '30', color: parsed.categoryColor, padding: '2px 8px', borderRadius: 10, fontSize: 12, fontWeight: 600 } }, parsed.categoryName || '?'),
        h('span', { style: { fontWeight: 700, color: parsed.type === 'INCOME' ? 'var(--income)' : 'var(--expense)' } }, `${parsed.type === 'INCOME' ? '+' : '-'}¥${parsed.amount}`)
      ),
      // 保存按钮
      parsed && h('button', { onClick: handleSave, style: { background: 'var(--primary)', color: 'white', border: 'none', borderRadius: 10, padding: '8px 16px', fontSize: 14, fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' } }, t('save')),
      // 计算器按钮
      h('button', { onClick: () => store.openCalc(val => setText(val)), style: { background: 'rgba(108,99,255,0.1)', color: 'var(--primary)', border: 'none', borderRadius: 8, padding: '8px 10px', fontSize: 16, cursor: 'pointer' }, title: '打开计算器' }, '🧮')
    ),
    // 提示行
    h('div', { style: { display: 'flex', justifyContent: 'space-between', marginTop: 6, padding: '0 8px' } },
      h('span', { style: { fontSize: 11, color: 'var(--text-hint)' } }, t('quickAddHint')),
      parsed && h('span', { style: { fontSize: 11, color: 'var(--primary)' } }, `${t('parsedAs')}: ${parsed.type === 'INCOME' ? t('income') : t('expense')} · ${parsed.categoryName || '?'} · ¥${parsed.amount}`)
    ),
    // 成功提示
    showToast && h('div', { style: { position: 'fixed', top: 20, right: 20, zIndex: 1000, background: 'var(--income)', color: 'white', padding: '12px 20px', borderRadius: 10, fontSize: 14, fontWeight: 600, boxShadow: '0 4px 12px rgba(76,175,80,0.3)' } }, `✅ ${t('quickAddSuccess')}`)
  );
}

// ============================================================================
// 十二、浮动计算器面板（可拖拽）
// ============================================================================

function QuickCalc({ onFillAmount, onClose }) {
  const [items, setItems] = useState([]);
  const [inputVal, setInputVal] = useState('');
  const [inputLabel, setInputLabel] = useState('');
  const [pos, setPos] = useState({ x: window.innerWidth - 410, y: window.innerHeight - 500 });
  const inputRef = useRef(null);
  const dragRef = useRef({ dragging: false, startX: 0, startY: 0, startPosX: 0, startPosY: 0 });

  const total = items.reduce((s, i) => i.op === '+' ? s + i.value : s - i.value, 0);

  /** 添加一项 */
  const addItem = (op) => {
    const val = parseFloat(inputVal);
    if (isNaN(val) || val <= 0) return;
    setItems([...items, { id: Date.now(), label: inputLabel.trim() || `项目${items.length + 1}`, value: val, op }]);
    setInputVal(''); setInputLabel('');
    inputRef.current?.focus();
  };

  // --- 拖拽逻辑（标题栏拖动）---
  const handleDragStart = (e) => {
    if (e.target.closest('button')) return;
    const d = dragRef.current;
    d.dragging = true; d.startX = e.clientX; d.startY = e.clientY;
    d.startPosX = pos.x; d.startPosY = pos.y;
    document.addEventListener('mousemove', handleDragMove);
    document.addEventListener('mouseup', handleDragEnd);
    e.preventDefault();
  };
  const handleDragMove = (e) => {
    const d = dragRef.current;
    if (!d.dragging) return;
    setPos({ x: d.startPosX + (e.clientX - d.startX), y: d.startPosY + (e.clientY - d.startY) });
  };
  const handleDragEnd = () => {
    dragRef.current.dragging = false;
    document.removeEventListener('mousemove', handleDragMove);
    document.removeEventListener('mouseup', handleDragEnd);
  };
  useEffect(() => () => { document.removeEventListener('mousemove', handleDragMove); document.removeEventListener('mouseup', handleDragEnd); }, []);

  // 按钮通用样式
  const btn = (bg) => ({ background: bg, color: 'white', border: 'none', borderRadius: 6, padding: '6px 12px', fontWeight: 700, cursor: 'pointer', fontSize: 14, outline: 'none', flexShrink: 0 });

  return h('div', { style: { position: 'fixed', left: pos.x, top: pos.y, zIndex: 99, width: 380, background: 'var(--surface)', borderRadius: 16, boxShadow: '0 8px 32px rgba(0,0,0,0.18)', overflow: 'hidden' } },
    // 标题栏（可拖拽）
    h('div', { onMouseDown: handleDragStart, style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: 'var(--primary)', color: 'white', cursor: 'grab', userSelect: 'none' } },
      h('span', null, '🧮 计算器  ↕ 拖动'),
      h('button', { style: { background: 'none', border: 'none', color: 'white', fontSize: 18, cursor: 'pointer' }, onClick: onClose }, '✕')
    ),
    // 合计
    h('div', { style: { padding: '12px 16px', textAlign: 'center', borderBottom: '1px solid var(--border)' } },
      h('div', { style: { fontSize: 12, color: 'var(--text-hint)' } }, '合计'),
      h('div', { style: { fontSize: 28, fontWeight: 700, color: 'var(--primary)' } }, `¥${total.toFixed(2)}`)
    ),
    // 输入行
    h('div', { style: { display: 'flex', gap: 6, padding: '10px 12px', alignItems: 'center' } },
      h('input', { ref: inputRef, style: { flex: 1.5, padding: '6px 8px', border: '1px solid var(--border)', borderRadius: 6, fontSize: 13, outline: 'none', minWidth: 0 }, placeholder: '名称', value: inputLabel, onChange: e => setInputLabel(e.target.value), onKeyDown: e => e.key === 'Enter' && addItem('+') }),
      h('input', { style: { flex: 1, padding: '6px 8px', border: '1px solid var(--border)', borderRadius: 6, fontSize: 14, fontWeight: 600, outline: 'none', minWidth: 0 }, type: 'text', placeholder: '金额', value: inputVal, onChange: e => { let v = e.target.value.replace(',', '.'); if (v === '' || v === '.' || /^\d+\.?\d{0,2}$/.test(v)) setInputVal(v); }, onKeyDown: e => e.key === 'Enter' && addItem('+') }),
      h('button', { style: btn('var(--income)'), onClick: () => addItem('+') }, '+'),
      h('button', { style: btn('var(--expense)'), onClick: () => addItem('-') }, '−')
    ),
    // 记录列表
    items.length > 0 && h('div', { style: { maxHeight: 160, overflowY: 'auto', padding: '0 12px' } },
      ...items.map(item => h('div', { key: item.id, style: { display: 'flex', alignItems: 'center', padding: '6px 0', fontSize: 13, borderBottom: '1px solid var(--border)' } },
        h('span', { style: { color: item.op === '+' ? 'var(--income)' : 'var(--expense)', fontWeight: 700, marginRight: 8, width: 16, textAlign: 'center' } }, item.op),
        h('span', { style: { flex: 1 } }, item.label),
        h('span', { style: { fontWeight: 600, marginRight: 6 } }, `¥${item.value.toFixed(2)}`),
        h('button', { style: { background: 'none', border: 'none', color: '#ccc', cursor: 'pointer', fontSize: 14, padding: '2px 4px' }, onClick: () => setItems(items.filter(i => i.id !== item.id)) }, '✕')
      ))
    ),
    // 底部按钮
    h('div', { style: { display: 'flex', gap: 8, padding: '10px 12px', borderTop: items.length > 0 ? '1px solid var(--border)' : 'none' } },
      items.length > 0 && h('button', { style: { flex: 1, padding: '8px', border: '1px solid var(--border)', borderRadius: 6, background: 'transparent', cursor: 'pointer', fontSize: 13 }, onClick: () => { setItems([]); setInputVal(''); setInputLabel(''); } }, '清空'),
      h('button', { style: { flex: 2, padding: '8px', border: 'none', borderRadius: 6, background: total > 0 ? 'var(--primary)' : '#ccc', color: 'white', cursor: total > 0 ? 'pointer' : 'default', fontSize: 13, fontWeight: 600 }, onClick: () => total > 0 && onFillAmount(total.toFixed(2)), disabled: total <= 0 }, `填入 ¥${total.toFixed(2)}`)
    )
  );
}

// ============================================================================
// 十三、页面组件
// ============================================================================

// --- 账户转账弹窗（已删除：纯记账本用不到） ---
// 原 TransferDialog / store.transfer / db.addTransfer / IPC transactions:transfer 全部移除

// --- 首页 ---
function HomePage() {
  const s = useStore();
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const todayEnd = todayStart + 86400000;
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();

  const sum = (txs, type) => txs.filter(t => t.type === type).reduce((a, t) => a + t.amount, 0);
  const todayTxs = s.transactions.filter(t => t.date >= todayStart && t.date < todayEnd);
  const monthTxs = s.transactions.filter(t => t.date >= monthStart);

  return h(React.Fragment, null,
    h(QuickInputBar, null),
    // 余额卡片
    h('div.balance-card', null,
      h('div.balance-label', null, t('totalAsset')),
      h('div.balance-amount', null, `¥${fmtMoney(s.accounts.reduce((a, t) => a + t.balance, 0))}`),
      h('div.balance-row', null,
        h('div', null, h('div.balance-item-label', null, t('monthIncome')), h('div.balance-item-amount', null, `¥${fmtMoney(sum(monthTxs, 'INCOME'))}`)),
        h('div', { style: { textAlign: 'right' } }, h('div.balance-item-label', null, t('monthExpense')), h('div.balance-item-amount', null, `¥${fmtMoney(sum(monthTxs, 'EXPENSE'))}`))
      )
    ),
    // 今日收支
    h('div.today-row', null,
      h('div.today-card', null, h('div.today-label', null, t('todayIncome')), h('div.today-income', null, `¥${fmtMoney(sum(todayTxs, 'INCOME'))}`)),
      h('div.today-card', null, h('div.today-label', null, t('todayExpense')), h('div.today-expense', null, `¥${fmtMoney(sum(todayTxs, 'EXPENSE'))}`))
    ),
    // 最近交易
    h('div.card', null,
      h('div.card-header', null,
        h('span.card-title', null, t('recentTx')),
        h('button.btn.btn-outline', { onClick: () => store.navigate('transactions') }, t('viewAll'))
      ),
      s.transactions.length === 0
        ? h('div.empty-state', null, t('noTx'))
        : h(React.Fragment, null, ...s.transactions.slice(0, 10).map(tx => h(TransactionItem, { key: tx.id, transaction: tx, showDelete: false })))
    ),
    // 转账弹窗（已删除：纯记账本用不到）
  );
}

// --- 交易记录页 ---
function TransactionsPage() {
  const s = useStore();
  const [filter, setFilter] = useState('all');
  const [searchText, setSearchText] = useState('');

  let filtered = s.transactions;
  if (filter === 'income') filtered = filtered.filter(t => t.type === 'INCOME');
  if (filter === 'expense') filtered = filtered.filter(t => t.type === 'EXPENSE');
  if (searchText) {
    const q = searchText.toLowerCase();
    filtered = filtered.filter(t => (t.note?.toLowerCase().includes(q)) || store.categories.find(c => c.id === t.category_id)?.name.toLowerCase().includes(q));
  }

  // 按日分组
  const grouped = {};
  filtered.forEach(t => { const day = fmtDay(t.date); (grouped[day] ??= []).push(t); });

  return h(React.Fragment, null,
    h('h2', { style: { marginBottom: 16 } }, t('transactions')),
    h('div', { style: { display: 'flex', gap: 12, marginBottom: 16, alignItems: 'center' } },
      h('div.filter-row', { style: { marginBottom: 0 } },
        ...['all', 'expense', 'income'].map(f => h('button', { key: f, className: `filter-chip ${filter === f ? 'active' : ''}`, onClick: () => setFilter(f) }, f === 'all' ? t('all') : f === 'expense' ? t('expense') : t('income')))
      ),
      h('input.form-input', { style: { flex: 1, maxWidth: 240 }, placeholder: t('search'), value: searchText, onChange: e => setSearchText(e.target.value) }),
      h('span', { style: { color: 'var(--text-hint)', fontSize: 13, whiteSpace: 'nowrap' } }, t('total', { n: filtered.length }))
    ),
    h('div.card', null,
      ...Object.entries(grouped).map(([day, txs]) => h('div', { key: day },
        h('div.date-group', null, `${day}  (${txs.length})`),
        ...txs.map(tx => h(TransactionItem, { key: tx.id, transaction: tx }))
      )),
      filtered.length === 0 && h('div.empty-state', null, t('noData'))
    )
  );
}

// --- 总结报告页 ---
function SummaryPage() {
  const s = useStore();
  const [period, setPeriod] = useState('month');
  const { start, end, prevStart, prevEnd, days, label } = useMemo(() => getPeriodRange(period), [period]);

  const sumType = (txs, type) => txs.filter(t => t.type === type).reduce((a, t) => a + t.amount, 0);
  const cur = s.transactions.filter(t => t.date >= start && t.date < end);
  const prev = s.transactions.filter(t => t.date >= prevStart && t.date < prevEnd);

  const curIncome = sumType(cur, 'INCOME'), curExpense = sumType(cur, 'EXPENSE');
  const prevIncome = sumType(prev, 'INCOME'), prevExpense = sumType(prev, 'EXPENSE');
  const avgDaily = days > 0 ? curExpense / days : 0;
  const savingRate = curIncome > 0 ? ((curIncome - curExpense) / curIncome * 100) : 0;

  // 分类统计 TOP 5
  const catTotals = {};
  cur.filter(t => t.type === 'EXPENSE').forEach(t => { catTotals[t.category_id] = (catTotals[t.category_id] || 0) + t.amount; });
  const topCats = Object.entries(catTotals).map(([id, amount]) => ({ cat: s.categories.find(c => c.id === parseInt(id)), amount, pct: curExpense > 0 ? amount / curExpense : 0 })).sort((a, b) => b.amount - a.amount).slice(0, 5);

  // 最大支出 TOP 5
  const topExpenses = cur.filter(t => t.type === 'EXPENSE').sort((a, b) => b.amount - a.amount).slice(0, 5);

  // 变化百分比
  const pctChange = (cur, prev) => prev > 0 ? ((cur - prev) / prev * 100) : (cur > 0 ? 100 : 0);
  const expChange = pctChange(curExpense, prevExpense);

  const periodOpts = [['week', t('thisWeek')], ['month', t('thisMonth')], ['quarter', t('thisQuarter')], ['year', t('thisYear')]];

  // 摘要卡片组件
  const SummaryCard = ({ label, value, color, change, inverse }) => {
    const changeColor = change == null ? null : inverse ? (change > 0 ? 'var(--expense)' : change < 0 ? 'var(--income)' : 'var(--text-hint)') : (change > 0 ? 'var(--income)' : change < 0 ? 'var(--expense)' : 'var(--text-hint)');
    const changeText = change == null ? null : change > 0 ? `↑${change.toFixed(1)}%` : change < 0 ? `↓${Math.abs(change).toFixed(1)}%` : '→';
    return h('div.card', null,
      h('div', { style: { fontSize: 12, color: 'var(--text-secondary)', marginBottom: 4 } }, label),
      h('div', { style: { fontSize: 20, fontWeight: 700, color } }, value),
      change != null && h('div', { style: { fontSize: 12, color: changeColor, marginTop: 4 } }, `${changeText} ${t('comparedToPrev')}`)
    );
  };

  // 预计算列表（避免三元+spread语法问题）
  const catItems = topCats.length === 0 ? [h('div.empty-state', null, t('noTxInPeriod'))] :
    topCats.map((item, i) => h('div.stat-bar', { key: item.cat?.id || i },
      h('div', { style: { width: 24, textAlign: 'center', fontSize: 14, fontWeight: 700, color: i < 3 ? 'var(--primary)' : 'var(--text-hint)' } }, `#${i + 1}`),
      h('div.stat-bar-icon', { style: { background: (item.cat?.color || '#666') + '20', color: item.cat?.color || '#666' } }, item.cat?.name[0] || '?'),
      h('div.stat-bar-info', null,
        h('div.stat-bar-name', null, item.cat?.name || ''),
        h('div.stat-bar-progress', null, h('div.stat-bar-fill', { style: { width: `${item.pct * 100}%`, background: item.cat?.color || '#666' } }))
      ),
      h('div.stat-bar-right', null,
        h('div.stat-bar-amount', null, `¥${fmtMoney(item.amount)}`),
        h('div.stat-bar-percent', null, `${(item.pct * 100).toFixed(1)}%`)
      )
    ));

  const expenseItems = topExpenses.length === 0 ? [h('div.empty-state', null, t('noTxInPeriod'))] :
    topExpenses.map((tx, i) => {
      const cat = s.categories.find(c => c.id === tx.category_id);
      return h('div.transaction-item', { key: tx.id },
        h('div', { style: { width: 24, textAlign: 'center', fontSize: 14, fontWeight: 700, color: i < 3 ? '#F44336' : 'var(--text-hint)' } }, `#${i + 1}`),
        h('div.transaction-icon.expense', null, cat?.name[0] || '支'),
        h('div.transaction-info', null, h('div.transaction-note', null, tx.note || cat?.name || ''), h('div.transaction-date', null, fmtTime(tx.date))),
        h('div.transaction-amount.expense', null, `-¥${fmtMoney(tx.amount)}`)
      );
    });

  return h(React.Fragment, null,
    h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 } },
      h('h2', null, t('summary')),
      h('span', { style: { color: 'var(--text-secondary)', fontSize: 14 } }, label)
    ),
    h('div.filter-row', null, ...periodOpts.map(([p, lbl]) => h('button', { key: p, className: `filter-chip ${period === p ? 'active' : ''}`, onClick: () => setPeriod(p) }, lbl))),
    // 核心数据
    h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 } },
      h(SummaryCard, { label: t('totalIncome'), value: `¥${fmtMoney(curIncome)}`, color: 'var(--income)' }),
      h(SummaryCard, { label: t('totalExpense'), value: `¥${fmtMoney(curExpense)}`, color: 'var(--expense)', change: expChange, inverse: true }),
      h(SummaryCard, { label: t('totalBalance'), value: `¥${fmtMoney(curIncome - curExpense)}`, color: curIncome >= curExpense ? 'var(--income)' : 'var(--expense)' }),
      h(SummaryCard, { label: t('txCount'), value: cur.length.toString(), color: 'var(--primary)' })
    ),
    // 二级指标
    h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 } },
      h('div.card', null,
        h('div', { style: { fontSize: 13, color: 'var(--text-secondary)', marginBottom: 4 } }, t('avgDailyExpense')),
        h('div', { style: { fontSize: 24, fontWeight: 700 } }, `¥${fmtMoney(avgDaily)}`),
        h('div', { style: { fontSize: 12, color: 'var(--text-hint)', marginTop: 4 } }, `${t('prevPeriod')}: ¥${fmtMoney(prevExpense / (days || 1))}`)
      ),
      h('div.card', null,
        h('div', { style: { fontSize: 13, color: 'var(--text-secondary)', marginBottom: 4 } }, t('savingRate')),
        h('div', { style: { fontSize: 24, fontWeight: 700, color: savingRate >= 0 ? 'var(--income)' : 'var(--expense)' } }, `${savingRate.toFixed(1)}%`),
        h('div', { style: { marginTop: 8, height: 6, background: '#eee', borderRadius: 3, overflow: 'hidden' } },
          h('div', { style: { width: `${Math.max(0, Math.min(100, savingRate))}%`, height: '100%', background: savingRate >= 0 ? 'var(--income)' : 'var(--expense)', borderRadius: 3 } })
        )
      )
    ),
    // TOP 5 分类
    h('div.card', { style: { marginBottom: 16 } },
      h('div.card-title', { style: { marginBottom: 12 } }, t('topCategories')),
      ...catItems
    ),
    // 最大支出
    h('div.card', null,
      h('div.card-title', { style: { marginBottom: 12 } }, t('topExpenses')),
      ...expenseItems
    )
  );
}

// ============================================================================
// 图表组件（纯 SVG，无第三方库）
// ============================================================================

/**
 * 环形饼图（分类占比）
 * @param {Array<{label,value,color}>} data - 各扇区数据
 * @param {number} size - 直径像素
 */
function DonutChart({ data, size = 180 }) {
  const total = data.reduce((a, d) => a + d.value, 0);
  if (total <= 0) return h('div.empty-state', null, t('noData'));

  const r = size / 2;          // 外半径
  const inner = r * 0.6;       // 内半径（环形）
  const cx = r, cy = r;
  let angle = -Math.PI / 2;    // 从 12 点方向开始

  // 计算每个扇区路径
  const arcs = data.map((d, i) => {
    const frac = d.value / total;
    const start = angle;
    const end = angle + frac * Math.PI * 2;
    angle = end;
    const large = frac > 0.5 ? 1 : 0;
    const x1 = cx + r * Math.cos(start), y1 = cy + r * Math.sin(start);
    const x2 = cx + r * Math.cos(end), y2 = cy + r * Math.sin(end);
    const ix1 = cx + inner * Math.cos(end), iy1 = cy + inner * Math.sin(end);
    const ix2 = cx + inner * Math.cos(start), iy2 = cy + inner * Math.sin(start);
    const path = `M ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} L ${ix1} ${iy1} A ${inner} ${inner} 0 ${large} 0 ${ix2} ${iy2} Z`;
    return h('path', { key: i, d: path, fill: d.color, stroke: '#fff', strokeWidth: 1 });
  });

  return h('div', { style: { display: 'flex', alignItems: 'center', gap: 20, flexWrap: 'wrap' } },
    // SVG 图形
    h('svg', { width: size, height: size, viewBox: `0 0 ${size} ${size}` },
      ...arcs,
      // 中心总额
      h('text', { x: cx, y: cy - 6, textAnchor: 'middle', fontSize: 12, fill: '#999' }, t('expense')),
      h('text', { x: cx, y: cy + 14, textAnchor: 'middle', fontSize: 16, fontWeight: 700, fill: '#333' }, `¥${fmtMoney(total)}`)
    ),
    // 图例
    h('div', { style: { flex: 1, minWidth: 140 } },
      ...data.map((d, i) => h('div', { key: i, style: { display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, fontSize: 13 } },
        h('span', { style: { width: 12, height: 12, borderRadius: 3, background: d.color, flexShrink: 0 } }),
        h('span', { style: { flex: 1, color: '#555' } }, d.label),
        h('span', { style: { color: '#999' } }, `${((d.value / total) * 100).toFixed(1)}%`)
      ))
    )
  );
}

/**
 * 收支趋势折线图（双线：收入绿 / 支出红）
 * @param {Array<{label,income,expense}>} data
 */
function TrendLineChart({ data, height = 160 }) {
  if (!data || data.length === 0) return h('div.empty-state', null, t('noData'));
  const W = 320, H = height, pad = 28;
  const maxV = Math.max(...data.flatMap(d => [d.income, d.expense]), 1);
  const stepX = (W - pad * 2) / Math.max(data.length - 1, 1);
  const scaleY = (v) => H - pad - (v / maxV) * (H - pad * 2);

  // 生成折线 points 字符串
  const line = (key) => data.map((d, i) => `${pad + i * stepX},${scaleY(d[key])}`).join(' ');

  return h('div', null,
    h('svg', { width: '100%', height: H, viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: 'xMidYMid meet' },
      // 基准横线
      h('line', { x1: pad, y1: H - pad, x2: W - pad, y2: H - pad, stroke: '#eee', strokeWidth: 1 }),
      // 收入线
      h('polyline', { points: line('income'), fill: 'none', stroke: '#4CAF50', strokeWidth: 2, strokeLinejoin: 'round' }),
      // 支出线
      h('polyline', { points: line('expense'), fill: 'none', stroke: '#F44336', strokeWidth: 2, strokeLinejoin: 'round' }),
      // 数据点
      ...data.map((d, i) => h('circle', { key: 'i' + i, cx: pad + i * stepX, cy: scaleY(d.income), r: 2.5, fill: '#4CAF50' })),
      ...data.map((d, i) => h('circle', { key: 'e' + i, cx: pad + i * stepX, cy: scaleY(d.expense), r: 2.5, fill: '#F44336' })),
      // X 轴标签（最多显示首尾及中间几个）
      ...data.map((d, i) => (i === 0 || i === data.length - 1 || i === Math.floor(data.length / 2))
        ? h('text', { key: 'x' + i, x: pad + i * stepX, y: H - 8, textAnchor: 'middle', fontSize: 9, fill: '#999' }, d.label)
        : null)
    ),
    // 图例
    h('div', { style: { display: 'flex', gap: 16, justifyContent: 'center', marginTop: 8, fontSize: 12 } },
      h('span', { style: { display: 'flex', alignItems: 'center', gap: 4 } }, h('span', { style: { width: 12, height: 3, background: '#4CAF50', display: 'inline-block' } }), t('incomeLegend')),
      h('span', { style: { display: 'flex', alignItems: 'center', gap: 4 } }, h('span', { style: { width: 12, height: 3, background: '#F44336', display: 'inline-block' } }), t('expenseLegend'))
    )
  );
}

// --- 统计页 ---
function StatisticsPage() {
  const s = useStore();
  const [period, setPeriod] = useState('month');
  const now = new Date();
  let startDate;
  if (period === 'week') { startDate = new Date(now); startDate.setDate(now.getDate() - 7); startDate.setHours(0, 0, 0, 0); }
  else if (period === 'month') { startDate = new Date(now.getFullYear(), now.getMonth(), 1); }
  else { startDate = new Date(now.getFullYear(), 0, 1); }
  startDate = startDate.getTime();

  const filtered = s.transactions.filter(t => t.date >= startDate);
  const totalIncome = filtered.filter(t => t.type === 'INCOME').reduce((a, t) => a + t.amount, 0);
  const totalExpense = filtered.filter(t => t.type === 'EXPENSE').reduce((a, t) => a + t.amount, 0);

  // 每日支出柱状图数据
  const dailyData = useMemo(() => {
    const days = period === 'week' ? 7 : period === 'month' ? 30 : 365;
    return Array.from({ length: days }, (_, i) => {
      const d = new Date(now); d.setDate(d.getDate() - (days - 1 - i));
      const ds = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
      return { label: `${d.getMonth() + 1}/${d.getDate()}`, value: s.transactions.filter(t => t.type === 'EXPENSE' && t.date >= ds && t.date < ds + 86400000).reduce((a, t) => a + t.amount, 0) / 100 };
    });
  }, [s.transactions, period]);
  const maxDaily = Math.max(...dailyData.map(d => d.value), 1);

  // 分类统计
  const catTotals = {};
  filtered.filter(t => t.type === 'EXPENSE').forEach(t => { catTotals[t.category_id] = (catTotals[t.category_id] || 0) + t.amount; });
  const stats = Object.entries(catTotals).map(([id, amount]) => ({ cat: s.categories.find(c => c.id === parseInt(id)), amount, pct: totalExpense > 0 ? amount / totalExpense : 0 })).sort((a, b) => b.amount - a.amount);

  // 饼图数据：Top 6 分类 + 其他合并
  const pieData = useMemo(() => {
    const top = stats.slice(0, 6).map(st => ({
      label: st.cat?.name || '?',
      value: st.amount / 100,
      color: st.cat?.color || '#6C63FF',
    }));
    const restTotal = stats.slice(6).reduce((a, st) => a + st.amount, 0);
    if (restTotal > 0) top.push({ label: t('other'), value: restTotal / 100, color: '#BDC3C7' });
    return top;
  }, [stats]);

  // 趋势数据：按周期分桶（周=7天，月=按周分4段，年=12月）
  const trendData = useMemo(() => {
    const buckets = [];
    if (period === 'week') {
      // 近 7 天
      for (let i = 6; i >= 0; i--) {
        const d = new Date(now); d.setDate(d.getDate() - i);
        const ds = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
        const de = ds + 86400000;
        const txs = s.transactions.filter(t => t.date >= ds && t.date < de);
        buckets.push({ label: `${d.getMonth() + 1}/${d.getDate()}`, income: txs.filter(t => t.type === 'INCOME').reduce((a, t) => a + t.amount, 0) / 100, expense: txs.filter(t => t.type === 'EXPENSE').reduce((a, t) => a + t.amount, 0) / 100 });
      }
    } else if (period === 'month') {
      // 本月按周分段
      for (let w = 0; w < 5; w++) {
        const ws = new Date(now.getFullYear(), now.getMonth(), 1 + w * 7).getTime();
        const we = new Date(now.getFullYear(), now.getMonth(), 1 + (w + 1) * 7).getTime();
        const txs = s.transactions.filter(t => t.date >= ws && t.date < we);
        if (ws > now.getTime()) break;
        buckets.push({ label: `W${w + 1}`, income: txs.filter(t => t.type === 'INCOME').reduce((a, t) => a + t.amount, 0) / 100, expense: txs.filter(t => t.type === 'EXPENSE').reduce((a, t) => a + t.amount, 0) / 100 });
      }
    } else {
      // 本年 12 月
      for (let m = 0; m < 12; m++) {
        const ms = new Date(now.getFullYear(), m, 1).getTime();
        const me = new Date(now.getFullYear(), m + 1, 1).getTime();
        const txs = s.transactions.filter(t => t.date >= ms && t.date < me);
        buckets.push({ label: `${m + 1}月`, income: txs.filter(t => t.type === 'INCOME').reduce((a, t) => a + t.amount, 0) / 100, expense: txs.filter(t => t.type === 'EXPENSE').reduce((a, t) => a + t.amount, 0) / 100 });
      }
    }
    return buckets;
  }, [s.transactions, period]);

  return h(React.Fragment, null,
    h('h2', { style: { marginBottom: 16 } }, t('statistics')),
    h('div.filter-row', null, ...[['week', t('thisWeek')], ['month', t('thisMonth')], ['year', t('thisYear')]].map(([p, lbl]) => h('button', { key: p, className: `filter-chip ${period === p ? 'active' : ''}`, onClick: () => setPeriod(p) }, lbl))),
    h('div.card', { style: { marginBottom: 16 } },
      h('div', { style: { display: 'flex', justifyContent: 'space-between' } },
        h('div', null, h('div', { style: { fontSize: 14, color: '#666' } }, t('income')), h('div', { style: { fontSize: 22, fontWeight: 700, color: 'var(--income)' } }, `¥${fmtMoney(totalIncome)}`)),
        h('div', { style: { textAlign: 'center' } }, h('div', { style: { fontSize: 14, color: '#666' } }, t('balance')), h('div', { style: { fontSize: 22, fontWeight: 700, color: 'var(--primary)' } }, `¥${fmtMoney(totalIncome - totalExpense)}`)),
        h('div', { style: { textAlign: 'right' } }, h('div', { style: { fontSize: 14, color: '#666' } }, t('expense')), h('div', { style: { fontSize: 22, fontWeight: 700, color: 'var(--expense)' } }, `¥${fmtMoney(totalExpense)}`))
      )
    ),
    // 柱状图
    h('div.card', { style: { marginBottom: 16 } },
      h('div.card-title', { style: { marginBottom: 12 } }, t('dailyExpense')),
      h('div', { style: { display: 'flex', alignItems: 'flex-end', gap: 2, height: 120, overflowX: 'auto', paddingBottom: 20, position: 'relative' } },
        ...dailyData.map((d, i) => h('div', { key: i, style: { display: 'flex', flexDirection: 'column', alignItems: 'center', flex: '1 0 0', minWidth: 12 } },
          h('div', { style: { width: '100%', maxWidth: 20, height: d.value > 0 ? Math.max(4, (d.value / maxDaily) * 90) : 0, background: 'var(--primary)', borderRadius: 3, opacity: 0.8 }, title: `${d.label}: ¥${d.value.toFixed(2)}` }),
          (period !== 'year' || i % 30 === 0) && h('span', { style: { fontSize: 9, color: 'var(--text-hint)', position: 'absolute', bottom: 0, whiteSpace: 'nowrap' } }, d.label)
        ))
      )
    ),
    // 收支趋势折线图
    h('div.card', { style: { marginBottom: 16 } },
      h('div.card-title', { style: { marginBottom: 12 } }, t('trendChart')),
      h(TrendLineChart, { data: trendData })
    ),
    // 支出分类占比饼图
    h('div.card', { style: { marginBottom: 16 } },
      h('div.card-title', { style: { marginBottom: 12 } }, t('pieChart')),
      h(DonutChart, { data: pieData })
    ),
    // 分类排行
    h('h3', { style: { marginBottom: 12, fontSize: 16 } }, t('categoryRank')),
    ...(stats.length === 0 ? [h('div.empty-state', null, t('noData'))] :
      [h('div.card', null, ...stats.map(st => h('div.stat-bar', { key: st.cat?.id },
        h('div.stat-bar-icon', { style: { background: (st.cat?.color || '#6C63FF') + '20', color: st.cat?.color || '#6C63FF' } }, st.cat?.name[0] || '?'),
        h('div.stat-bar-info', null, h('div.stat-bar-name', null, st.cat?.name || ''), h('div.stat-bar-progress', null, h('div.stat-bar-fill', { style: { width: `${st.pct * 100}%`, background: st.cat?.color || '#6C63FF' } }))),
        h('div.stat-bar-right', null, h('div.stat-bar-amount', null, `¥${fmtMoney(st.amount)}`), h('div.stat-bar-percent', null, `${(st.pct * 100).toFixed(1)}%`))
      )))])
  );
}

// --- 计算器页 ---
function CalculatorPage() {
  const [items, setItems] = useState([]);
  const [inputVal, setInputVal] = useState('');
  const [inputLabel, setInputLabel] = useState('');
  const inputRef = useRef(null);

  const total = items.reduce((s, i) => i.op === '+' ? s + i.value : s - i.value, 0);

  const addItem = (op) => {
    const val = parseFloat(inputVal);
    if (isNaN(val) || val <= 0) return;
    setItems([...items, { id: Date.now(), label: inputLabel.trim() || `项目${items.length + 1}`, value: val, op }]);
    setInputVal(''); setInputLabel('');
    inputRef.current?.focus();
  };

  return h(React.Fragment, null,
    h('h2', { style: { marginBottom: 20 } }, t('calculator')),
    // 合计卡片
    h('div', { style: { background: 'linear-gradient(135deg, var(--primary), var(--primary-dark))', color: 'white', borderRadius: 16, padding: '24px 28px', marginBottom: 20 } },
      h('div', { style: { fontSize: 14, opacity: 0.8, marginBottom: 4 } }, '合计'),
      h('div', { style: { fontSize: 36, fontWeight: 700 } }, `¥${total.toFixed(2)}`),
      h('div', { style: { fontSize: 13, opacity: 0.7, marginTop: 4 } }, `共 ${items.length} 项`)
    ),
    // 输入区
    h('div', { style: { display: 'flex', gap: 10, marginBottom: 20, alignItems: 'center', background: 'var(--surface)', borderRadius: 12, padding: 14, boxShadow: 'var(--shadow)' } },
      h('input.form-input', { ref: inputRef, style: { flex: 2, marginBottom: 0 }, placeholder: '名称（如：余额、余额包）', value: inputLabel, onChange: e => setInputLabel(e.target.value), onKeyDown: e => e.key === 'Enter' && addItem('+') }),
      h('input.form-input', { style: { flex: 1.5, marginBottom: 0, fontSize: 16, fontWeight: 600 }, type: 'text', placeholder: '金额', value: inputVal, onChange: e => { let v = e.target.value.replace(',', '.'); if (v === '' || v === '.' || /^\d+\.?\d{0,2}$/.test(v)) setInputVal(v); }, onKeyDown: e => e.key === 'Enter' && addItem('+') }),
      h('button.btn.btn-primary', { style: { padding: '10px 18px', fontSize: 15, fontWeight: 700 }, onClick: () => addItem('+') }, '+ 加'),
      h('button.btn.btn-outline', { style: { padding: '10px 18px', fontSize: 15, fontWeight: 700, color: 'var(--expense)', borderColor: 'var(--expense)' }, onClick: () => addItem('-') }, '− 减')
    ),
    h('div', { style: { fontSize: 12, color: 'var(--text-hint)', marginBottom: 16, padding: '0 4px' } }, '💡 输入名称和金额，点 + 加入合计，点 − 减去。Enter 快速添加。'),
    // 记录列表
    ...(items.length > 0 ? [h('div.card', null,
      ...items.map((item, i) => h('div', { key: item.id, style: { display: 'flex', alignItems: 'center', padding: '10px 0', borderBottom: i < items.length - 1 ? '1px solid var(--border)' : 'none' } },
        h('span', { style: { width: 28, height: 28, borderRadius: 8, background: item.op === '+' ? 'rgba(76,175,80,0.1)' : 'rgba(244,67,54,0.1)', color: item.op === '+' ? 'var(--income)' : 'var(--expense)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 16, marginRight: 12, flexShrink: 0 } }, item.op),
        h('span', { style: { flex: 1, fontWeight: 500 } }, item.label),
        h('span', { style: { fontWeight: 700, fontSize: 16, marginRight: 12, color: item.op === '+' ? 'var(--income)' : 'var(--expense)' } }, `${item.op}¥${item.value.toFixed(2)}`),
        h('button', { style: { background: 'none', border: 'none', cursor: 'pointer', color: '#ccc', fontSize: 16, padding: '4px 8px' }, onClick: () => setItems(items.filter(i => i.id !== item.id)) }, '✕')
      )),
      h('div', { style: { textAlign: 'right', paddingTop: 12 } }, h('button.btn.btn-outline', { style: { fontSize: 13 }, onClick: () => { setItems([]); setInputVal(''); setInputLabel(''); } }, '清空全部'))
    )] : [h('div.card', null,
      h('div', { style: { color: 'var(--text-hint)', textAlign: 'center', padding: '32px 0' } },
        h('div', { style: { fontSize: 32, marginBottom: 12 } }, '🧮'),
        h('div', { style: { marginBottom: 8 } }, '输入金额后点 + 或 − 开始计算'),
        h('div', { style: { fontSize: 13 } }, '示例：余额 8 + 余额包 3 + 小荷包 7.11 = ¥18.11')
      )
    )])
  );
}

// ============================================================================
// 预算管理页 —— 设置预算 + 进度条 + 超支预警
// ============================================================================
function BudgetPage() {
  const s = useStore();
  const [editing, setEditing] = useState(null); // null=不显示表单, {}=新增, {...}=编辑

  const PERIOD_LABEL = { MONTHLY: t('periodMonthly'), WEEKLY: t('periodWeekly'), YEARLY: t('periodYearly') };

  // 预算卡片列表（每条含进度条 + 超支/预警状态）
  const budgetCards = s.budgets.length === 0
    ? [h('div.card', { style: { textAlign: 'center', padding: '48px 0', color: 'var(--text-hint)' } },
        h('div', { style: { fontSize: 40, marginBottom: 16 } }, '🎯'),
        h('div', null, t('noBudget')))]
    : s.budgets.map(b => {
        const pct = b.amount > 0 ? Math.min(b.used / b.amount, 1) : 0;
        const rawPct = b.amount > 0 ? b.used / b.amount : 0;
        const over = b.used > b.amount;                 // 超支
        const warning = !over && rawPct >= 0.8;          // 接近上限（≥80%）
        const left = b.amount - b.used;
        const barColor = over ? '#F44336' : warning ? '#FF9800' : '#4CAF50';
        const title = b.category_id == null ? t('budgetTotal') : (b.category_name || t('budgetCategory'));

        return h('div.card', { key: b.id, style: { marginBottom: 12 } },
          // 头部：标题 + 周期标签 + 操作按钮
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            h('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
              h('span', { style: { fontSize: 16, fontWeight: 600, color: '#333' } }, title),
              h('span', { style: { fontSize: 12, padding: '2px 8px', borderRadius: 10, background: 'rgba(108,99,255,0.1)', color: '#6C63FF' } }, PERIOD_LABEL[b.period] || b.period)
            ),
            h('div', { style: { display: 'flex', gap: 6 } },
              h('button', { style: { background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#888', padding: '2px 6px' }, onClick: () => setEditing(b) }, '✏️'),
              h('button', { style: { background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#888', padding: '2px 6px' }, onClick: () => { if (confirm(t('budgetDeleteConfirm'))) store.deleteBudget(b.id); } }, '🗑')
            )
          ),
          // 进度条
          h('div', { style: { height: 10, borderRadius: 5, background: 'rgba(0,0,0,0.06)', overflow: 'hidden', marginBottom: 8 } },
            h('div', { style: { height: '100%', width: `${pct * 100}%`, background: barColor, borderRadius: 5, transition: 'width 0.3s' } })
          ),
          // 数字行：已用 / 预算 + 剩余/超支
          h('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#555' } },
            h('span', null, `${t('budgetUsed')} ¥${fmtMoney(b.used)} / ¥${fmtMoney(b.amount)}`),
            over
              ? h('span', { style: { color: '#F44336', fontWeight: 600 } }, `⚠️ ${t('budgetExceeded')} ¥${fmtMoney(-left)}`)
              : warning
                ? h('span', { style: { color: '#FF9800', fontWeight: 600 } }, `${t('budgetWarning')} · ${t('budgetLeft')} ¥${fmtMoney(left)}`)
                : h('span', { style: { color: '#4CAF50' } }, `${t('budgetLeft')} ¥${fmtMoney(left)}`)
          )
        );
      });

  return h(React.Fragment, null,
    // 标题栏 + 新增按钮
    h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 } },
      h('h2', null, t('budgetMgmt')),
      h('button.btn.btn-primary', { onClick: () => setEditing({}) }, `+ ${t('addBudget')}`)
    ),
    ...budgetCards,
    // 编辑/新增表单弹窗
    editing !== null && h(BudgetForm, { budget: editing, onClose: () => setEditing(null) })
  );
}

// 预算新增/编辑表单弹窗
function BudgetForm({ budget, onClose }) {
  const s = useStore();
  const isEdit = budget && budget.id != null;
  const [amount, setAmount] = useState(isEdit ? (budget.amount / 100).toString() : '');
  const [period, setPeriod] = useState(isEdit ? budget.period : 'MONTHLY');
  const [categoryId, setCategoryId] = useState(isEdit ? (budget.category_id || '') : '');

  // 仅支出分类可设预算
  const expenseCats = s.categories.filter(c => c.type === 'EXPENSE');

  const handleSave = async () => {
    const cents = Math.round(parseFloat(amount.replace(',', '.')) * 100);
    if (!cents || cents <= 0) return;
    const payload = { amount: cents, period, categoryId: categoryId || null };
    if (isEdit) await store.updateBudget(budget.id, payload);
    else await store.addBudget(payload);
    onClose();
  };

  const periods = [['MONTHLY', t('periodMonthly')], ['WEEKLY', t('periodWeekly')], ['YEARLY', t('periodYearly')]];

  return h('div.modal-overlay', { onClick: e => e.target === e.currentTarget && onClose() },
    h('div.modal', { style: { maxWidth: 400 } },
      h('div.modal-title', null, isEdit ? t('editBudget') : t('addBudget')),
      // 金额
      h('div', { style: { marginBottom: 16 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('budgetAmount')),
        h('input.input', { type: 'text', inputMode: 'decimal', value: amount, placeholder: '0.00', autoFocus: true,
          onChange: e => setAmount(e.target.value.replace(/[^0-9.,]/g, '')),
          onKeyDown: e => e.key === 'Enter' && handleSave() })
      ),
      // 周期选择
      h('div', { style: { marginBottom: 16 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('budgetPeriod')),
        h('div', { style: { display: 'flex', gap: 8 } },
          ...periods.map(([val, label]) => h('button', {
            key: val,
            style: { flex: 1, padding: '8px 0', borderRadius: 8, border: period === val ? '2px solid #6C63FF' : '1px solid var(--border)', background: period === val ? 'rgba(108,99,255,0.08)' : '#fff', color: period === val ? '#6C63FF' : '#555', cursor: 'pointer', fontWeight: period === val ? 600 : 400 },
            onClick: () => setPeriod(val)
          }, label))
        )
      ),
      // 分类选择（留空为总预算）
      h('div', { style: { marginBottom: 20 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('selectCategory')),
        h('select.input', { value: categoryId, onChange: e => setCategoryId(e.target.value) },
          h('option', { value: '' }, `— ${t('budgetTotal')} —`),
          ...expenseCats.map(c => h('option', { key: c.id, value: c.id }, c.name))
        )
      ),
      // 操作按钮
      h('div.modal-actions', null,
        h('button.btn.btn-outline', { onClick: onClose }, t('cancel')),
        h('button.btn.btn-primary', { onClick: handleSave }, t('save'))
      )
    )
  );
}

// ============================================================================
// 循环记账页 —— 固定周期自动记账（房租/工资等）
// ============================================================================
function RecurringPage() {
  const s = useStore();
  const [editing, setEditing] = useState(null);

  const PERIOD_LABEL = { DAILY: t('recurPeriodDaily'), WEEKLY: t('recurPeriodWeekly'), MONTHLY: t('recurPeriodMonthly') };

  // 循环规则卡片列表
  const cards = s.recurring.length === 0
    ? [h('div.card', { style: { textAlign: 'center', padding: '48px 0', color: 'var(--text-hint)' } },
        h('div', { style: { fontSize: 40, marginBottom: 16 } }, '🔁'),
        h('div', null, t('noRecurring')))]
    : s.recurring.map(r => {
        const isIncome = r.type === 'INCOME';
        const nextDate = new Date(r.next_run);
        const nextStr = `${nextDate.getFullYear()}-${String(nextDate.getMonth() + 1).padStart(2, '0')}-${String(nextDate.getDate()).padStart(2, '0')}`;
        return h('div.card', { key: r.id, style: { marginBottom: 12 } },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 } },
            h('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
              h('span', { style: { width: 36, height: 36, borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', background: (r.category_color || '#6C63FF') + '20', color: r.category_color || '#6C63FF', fontWeight: 600 } }, r.category_name ? r.category_name[0] : '🔁'),
              h('div', null,
                h('div', { style: { fontSize: 15, fontWeight: 600, color: '#333' } }, r.note || r.category_name || t('recurring')),
                h('div', { style: { fontSize: 12, color: '#999' } }, `${PERIOD_LABEL[r.period] || r.period} · ${r.account_name || ''}`)
              )
            ),
            h('div', { style: { display: 'flex', gap: 6 } },
              h('button', { style: { background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#888', padding: '2px 6px' }, onClick: () => setEditing(r) }, '✏️'),
              h('button', { style: { background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: '#888', padding: '2px 6px' }, onClick: () => { if (confirm(t('recurringDeleteConfirm'))) store.deleteRecurring(r.id); } }, '🗑')
            )
          ),
          h('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: 13 } },
            h('span', { style: { color: isIncome ? 'var(--income)' : 'var(--expense)', fontWeight: 700 } }, `${isIncome ? '+' : '-'}¥${fmtMoney(r.amount)}`),
            h('span', { style: { color: '#666' } }, `${t('nextRun')}: ${nextStr}`)
          )
        );
      });

  return h(React.Fragment, null,
    h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 } },
      h('h2', null, t('recurringMgmt')),
      h('button.btn.btn-primary', { onClick: () => setEditing({}) }, `+ ${t('addRecurring')}`)
    ),
    ...cards,
    editing !== null && h(RecurringForm, { rule: editing, onClose: () => setEditing(null) })
  );
}

// 循环记账新增/编辑表单弹窗
function RecurringForm({ rule, onClose }) {
  const s = useStore();
  const isEdit = rule && rule.id != null;
  const [type, setType] = useState(isEdit ? rule.type : 'EXPENSE');
  const [amount, setAmount] = useState(isEdit ? (rule.amount / 100).toString() : '');
  const [period, setPeriod] = useState(isEdit ? rule.period : 'MONTHLY');
  const [categoryId, setCategoryId] = useState(isEdit ? rule.category_id : '');
  const [accountId, setAccountId] = useState(isEdit ? rule.account_id : (s.accounts[0]?.id || ''));
  const [note, setNote] = useState(isEdit ? (rule.note || '') : '');
  // 开始/下次日期，默认今天
  const defaultDate = isEdit ? new Date(rule.next_run) : new Date();
  const [startDate, setStartDate] = useState(`${defaultDate.getFullYear()}-${String(defaultDate.getMonth() + 1).padStart(2, '0')}-${String(defaultDate.getDate()).padStart(2, '0')}`);

  // 按当前类型筛选分类
  const cats = s.categories.filter(c => c.type === type);

  const handleSave = async () => {
    const cents = Math.round(parseFloat((amount || '').replace(',', '.')) * 100);
    if (!cents || cents <= 0) return;
    const catId = categoryId || cats[0]?.id;
    if (!catId || !accountId) return;
    const nextRun = new Date(startDate + 'T00:00:00').getTime();
    const payload = { type, amount: cents, categoryId: Number(catId), accountId: Number(accountId), note: note || null, period, nextRun, autoCreate: true };
    if (isEdit) await store.updateRecurring(rule.id, payload);
    else await store.addRecurring(payload);
    onClose();
  };

  const periods = [['DAILY', t('recurPeriodDaily')], ['WEEKLY', t('recurPeriodWeekly')], ['MONTHLY', t('recurPeriodMonthly')]];
  const typeBtn = (active, color) => ({ flex: 1, padding: '8px 0', borderRadius: 8, border: 'none', cursor: 'pointer', fontWeight: 600, background: active ? `var(--${color})` : `rgba(${color === 'expense' ? '244,67,54' : '76,175,80'},0.1)`, color: active ? '#fff' : `var(--${color})` });

  return h('div.modal-overlay', { onClick: e => e.target === e.currentTarget && onClose() },
    h('div.modal', { style: { maxWidth: 420 } },
      h('div.modal-title', null, isEdit ? t('editRecurring') : t('addRecurring')),
      // 收支类型
      h('div', { style: { display: 'flex', gap: 8, marginBottom: 16 } },
        h('button', { style: typeBtn(type === 'EXPENSE', 'expense'), onClick: () => { setType('EXPENSE'); setCategoryId(''); } }, t('expense')),
        h('button', { style: typeBtn(type === 'INCOME', 'income'), onClick: () => { setType('INCOME'); setCategoryId(''); } }, t('income'))
      ),
      // 金额
      h('div', { style: { marginBottom: 16 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('recurAmount')),
        h('input.input', { type: 'text', inputMode: 'decimal', value: amount, placeholder: '0.00', autoFocus: true,
          onChange: e => setAmount(e.target.value.replace(/[^0-9.,]/g, '')),
          onKeyDown: e => e.key === 'Enter' && handleSave() })
      ),
      // 分类
      h('div', { style: { marginBottom: 16 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('category')),
        h('select.input', { value: categoryId, onChange: e => setCategoryId(e.target.value) },
          ...cats.map(c => h('option', { key: c.id, value: c.id }, c.name))
        )
      ),
      // 账户
      h('div', { style: { marginBottom: 16 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('account')),
        h('select.input', { value: accountId, onChange: e => setAccountId(e.target.value) },
          ...s.accounts.map(a => h('option', { key: a.id, value: a.id }, a.name))
        )
      ),
      // 周期
      h('div', { style: { marginBottom: 16 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('budgetPeriod')),
        h('div', { style: { display: 'flex', gap: 8 } },
          ...periods.map(([val, label]) => h('button', {
            key: val,
            style: { flex: 1, padding: '8px 0', borderRadius: 8, border: period === val ? '2px solid #6C63FF' : '1px solid var(--border)', background: period === val ? 'rgba(108,99,255,0.08)' : '#fff', color: period === val ? '#6C63FF' : '#555', cursor: 'pointer', fontWeight: period === val ? 600 : 400 },
            onClick: () => setPeriod(val)
          }, label))
        )
      ),
      // 开始/下次日期
      h('div', { style: { marginBottom: 16 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('startDate')),
        h('input.input', { type: 'date', value: startDate, onChange: e => setStartDate(e.target.value) })
      ),
      // 备注
      h('div', { style: { marginBottom: 20 } },
        h('label', { style: { display: 'block', fontSize: 13, color: '#555', marginBottom: 6 } }, t('note')),
        h('input.input', { type: 'text', value: note, onChange: e => setNote(e.target.value) })
      ),
      h('div.modal-actions', null,
        h('button.btn.btn-outline', { onClick: onClose }, t('cancel')),
        h('button.btn.btn-primary', { onClick: handleSave }, t('save'))
      )
    )
  );
}

// --- 设置页 ---
function SettingsPage() {
  const s = useStore();
  const [opaExpanded, setOpaExpanded] = useState(false);
  const [syncState, setSyncState] = useState({ running: false, peers: [], lastResult: null });
  const [syncUrl, setSyncUrl] = useState('');
  const [diagnoseInfo, setDiagnoseInfo] = useState(null);
  const [showDiagnose, setShowDiagnose] = useState(false);
  const typeName = type => ({ CASH: t('cash'), BANK_CARD: t('bankCard'), ALIPAY: t('alipay'), WECHAT: t('wechat'), CREDIT_CARD: t('creditCard'), OTHER: t('other') }[type] || t('other'));

  // 同步状态轮询（每 2 秒）
  useEffect(() => {
    const id = setInterval(async () => {
      try {
        const st = await window.api.sync.getState();
        setSyncState(prev => ({ ...prev, ...st }));
      } catch (e) { /* sync not initialized yet */ }
    }, 2000);
    return () => clearInterval(id);
  }, []);

  // 启动/停止同步服务（局域网广播 + HTTP 接收）
  const toggleServer = async () => {
    if (syncState.running) {
      await window.api.sync.stopServer();
      setSyncState(s => ({ ...s, running: false }));
    } else {
      const r = await window.api.sync.startServer();
      if (r && r.success !== false) {
        setSyncState(s => ({ ...s, running: true, port: r.port }));
        await window.api.sync.startDiscovery();
        // 自动弹出诊断
        runDiagnose();
      } else {
        alert('启动同步服务失败：' + (r?.error || 'unknown'));
      }
    }
  };

  // 诊断：拿本机 IP + 端口 + 防火墙提示
  const runDiagnose = async () => {
    try {
      const info = await window.api.sync.diagnose();
      setDiagnoseInfo(info);
      setShowDiagnose(true);
    } catch (e) {
      setDiagnoseInfo({ error: e.message });
      setShowDiagnose(true);
    }
  };

  // 与指定 URL 同步（公网模式）
  const syncWithUrl = async () => {
    if (!syncUrl.trim()) { alert('请填写 URL'); return; }
    setSyncState(s => ({ ...s, busy: true }));
    try {
      const r = await window.api.sync.syncWithUrl(syncUrl.trim(), 0);
      setSyncState(s => ({ ...s, lastResult: r, busy: false }));
      await store.loadAll();
    } catch (e) {
      setSyncState(s => ({ ...s, lastResult: { error: e.message }, busy: false }));
    }
  };

  // 与指定 peer 同步
  const syncWithPeer = async (peer) => {
    setSyncState(s => ({ ...s, busy: true }));
    try {
      const r = await window.api.sync.syncWith(peer, 0);
      setSyncState(s => ({ ...s, lastResult: r, busy: false }));
      await store.loadAll();
    } catch (e) {
      setSyncState(s => ({ ...s, lastResult: { error: e.message }, busy: false }));
    }
  };

  // 背景图选择
  const chooseBg = () => {
    const input = document.createElement('input'); input.type = 'file'; input.accept = 'image/*';
    input.onchange = e => { const file = e.target.files[0]; if (!file) return; const reader = new FileReader(); reader.onload = ev => store.setBgImage(ev.target.result); reader.readAsDataURL(file); };
    input.click();
  };

  return h(React.Fragment, null,
    h('h2', { style: { marginBottom: 16 } }, t('settings')),

    // 语言切换
    h('div', { style: { marginBottom: 12, fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)' } }, t('language')),
    h('div.settings-item', null,
      h('div.settings-icon', null, '🌐'),
      h('div.settings-info', null, h('div.settings-title', null, t('switchLang')), h('div.settings-subtitle', null, s.lang === 'zh' ? '中文' : 'English')),
      h('div', { style: { display: 'flex', gap: 8 } },
        h('button', { className: `chip ${s.lang === 'zh' ? 'selected' : ''}`, onClick: () => store.setLang('zh') }, '中文'),
        h('button', { className: `chip ${s.lang === 'en' ? 'selected' : ''}`, onClick: () => store.setLang('en') }, 'English')
      )
    ),

    // 透明度设置（可展开）
    h('div', { style: { margin: '20px 0 12px', fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)' } }, t('background')),
    h('div.settings-item', { style: { flexDirection: 'column', alignItems: 'stretch', cursor: 'pointer' } },
      h('div', { onClick: () => setOpaExpanded(!opaExpanded), style: { display: 'flex', alignItems: 'center', gap: 12 } },
        h('div.settings-icon', null, '🎨'),
        h('div.settings-info', { style: { flex: 1 } }, h('div.settings-title', null, '透明度设置'), h('div.settings-subtitle', null, `总透明度 ${Math.round(s.masterOpacity * 100)}%`)),
        h('span', { style: { fontSize: 18, color: 'var(--text-hint)', transition: 'transform 0.2s', transform: opaExpanded ? 'rotate(90deg)' : 'none' } }, '›')
      ),
      opaExpanded && h('div', { style: { paddingTop: 12, borderTop: '1px solid var(--border)', marginTop: 12 } },
        // 背景图选择
        h('div', { style: { display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 } },
          h('span', { style: { fontSize: 16, width: 24, textAlign: 'center' } }, '🖼'),
          h('span', { style: { flex: 1, fontSize: 13 } }, '背景图片'),
          s.bgImage && h('span', { style: { fontSize: 12, color: 'var(--income)' } }, '✅'),
          h('button.btn.btn-outline', { style: { padding: '4px 10px', fontSize: 12 }, onClick: chooseBg }, '选择'),
          s.bgImage && h('button.btn.btn-outline', { style: { padding: '4px 10px', fontSize: 12 }, onClick: () => store.setBgImage('') }, '重置')
        ),
        h(OpaSlider, { icon: '🎚', label: '总透明度', value: s.masterOpacity, onChange: v => store.setMasterOpacity(v) }),
        h(OpaSlider, { icon: '🌅', label: '背景图', value: s.bgOpacity, min: 0.05, onChange: v => store.setBgOpacity(v) }),
        h(OpaSlider, { icon: '📋', label: '侧边栏', value: s.sidebarOpacity, onChange: v => store.setSidebarOpacity(v) }),
        h(OpaSlider, { icon: '🃏', label: '卡片', value: s.cardOpacity, onChange: v => store.setCardOpacity(v) })
      )
    ),

    // 数据操作
    h('div', { style: { margin: '20px 0 12px', fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)' } }, 'Data'),
    h(SettingsItem, { icon: '📤', title: t('exportJson'), subtitle: t('exportJsonDesc'), onClick: async () => { if ((await window.api.data.export()).success) alert(t('exportSuccess')); } }),
    h(SettingsItem, { icon: '📥', title: t('importJson'), subtitle: t('importJsonDesc'), onClick: async () => { const r = await window.api.data.import(); if (r.success) { alert(t('importSuccess', { n: r.count })); await store.loadAll(); } } }),
    h(SettingsItem, { icon: '📊', title: t('exportCsv'), subtitle: t('exportCsvDesc'), onClick: async () => { if ((await window.api.data.exportCsv()).success) alert(t('csvSuccess')); } }),

    // 数据同步（局域网 + 跨网段）
    h('div', { style: { margin: '20px 0 12px', fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)' } }, '数据同步'),

    // 启动/停止服务
    h('div.settings-item', { style: { cursor: 'pointer' }, onClick: toggleServer },
      h('div.settings-icon', null, syncState.running ? '🟢' : '⚫'),
      h('div.settings-info', { style: { flex: 1 } },
        h('div.settings-title', null, syncState.running ? '同步服务运行中' : '启动同步服务'),
        h('div.settings-subtitle', null, syncState.running
          ? `本机端口 ${syncState.port || 17860}，局域网/手机可发现`
          : '开启后手机可在"局域网设备"中看到本机')
      ),
      h('div', { style: { display: 'flex', gap: 8 } },
        h('button.btn.btn-outline', { style: { padding: '4px 12px', fontSize: 12 }, onClick: e => { e.stopPropagation(); toggleServer(); } },
          syncState.running ? '停止' : '启动'),
        h('button.btn.btn-outline', { style: { padding: '4px 12px', fontSize: 12 }, onClick: e => { e.stopPropagation(); runDiagnose(); } },
          '诊断')
      )
    ),

    // 诊断信息
    showDiagnose && diagnoseInfo && h('div', { style: { marginTop: 8, padding: 12, background: 'var(--surface)', borderRadius: 8, border: '1px solid var(--border)', fontSize: 12 } },
      h('div', { style: { display: 'flex', justifyContent: 'space-between', marginBottom: 8 } },
        h('strong', null, '同步诊断信息'),
        h('span', { style: { cursor: 'pointer', color: 'var(--text-secondary)' }, onClick: () => setShowDiagnose(false) }, '✕')
      ),
      diagnoseInfo.error
        ? h('div', { style: { color: 'var(--expense)' } }, '诊断失败: ' + diagnoseInfo.error)
        : h(React.Fragment, null,
            h('div', { style: { marginBottom: 4 } }, '本机 IP：'),
            ...(diagnoseInfo.ips || []).map(ip =>
              h('div', { key: ip.address, style: { marginLeft: 12, marginBottom: 2, fontFamily: 'monospace' } },
                '• ' + ip.address + ' (' + ip.name + ')')
            ),
            h('div', { style: { marginTop: 8, marginBottom: 4 } }, '端口：' + (diagnoseInfo.port || 17860)),
            h('div', { style: { marginBottom: 4 } },
              '服务状态：' + (diagnoseInfo.serverRunning ? '🟢 运行中' : '🔴 未启动') + ' /  ' +
              '发现：' + (diagnoseInfo.discoveryRunning ? '🟢 搜索中' : '🔴 未启动')),
            h('div', { style: { marginTop: 10, padding: 8, background: 'rgba(108,99,255,0.08)', borderRadius: 4 } },
              h('div', { style: { fontWeight: 'bold', marginBottom: 4 } }, '如果手机搜不到本机：'),
              ...(diagnoseInfo.tips || []).map((tip, i) =>
                h('div', { key: i, style: { marginBottom: 2 } }, '• ' + tip)
              ),
              h('div', { style: { marginTop: 6, color: 'var(--text-secondary)' } },
                '公网同步：在手机端填写 URL：http://' + (diagnoseInfo.ips?.[0]?.address || 'your-ip') + ':' + (diagnoseInfo.port || 17860))
            )
          )
    ),

    // 发现的 peer
    syncState.running && syncState.peers && syncState.peers.length > 0 &&
      h('div', { style: { marginTop: 8, padding: 12, background: 'var(--surface)', borderRadius: 8, border: '1px solid var(--border)' } },
        h('div', { style: { fontSize: 12, color: 'var(--text-secondary)', marginBottom: 8 } }, '发现的设备：'),
        ...syncState.peers.map(peer =>
          h('div', { key: peer.id, style: { display: 'flex', alignItems: 'center', padding: '6px 0', borderTop: '1px solid var(--border)' } },
            h('div', { style: { flex: 1 } },
              h('div', { style: { fontSize: 13 } }, peer.name || '未知设备'),
              h('div', { style: { fontSize: 11, color: 'var(--text-hint)' } }, peer.address || peer.host)
            ),
            h('button.btn.btn-primary', {
              style: { padding: '4px 12px', fontSize: 12 },
              onClick: () => syncWithPeer(peer),
              disabled: syncState.busy
            }, '同步')
          )
        )
      ),

    // 公网 URL 同步
    h('div', { style: { marginTop: 12, padding: 12, background: 'var(--surface)', borderRadius: 8, border: '1px solid var(--border)' } },
      h('div', { style: { fontSize: 12, color: 'var(--text-secondary)', marginBottom: 6 } }, '跨网段/公网同步（手填 URL）：'),
      h('div', { style: { display: 'flex', gap: 8 } },
        h('input.input', { style: { flex: 1 }, placeholder: 'https://your-domain.com:17860', value: syncUrl, onChange: e => setSyncUrl(e.target.value) }),
        h('button.btn.btn-primary', { onClick: syncWithUrl, disabled: syncState.busy || !syncUrl.trim() },
          syncState.busy ? '同步中...' : '同步')
      ),
      syncState.lastResult && h('div', {
        style: {
          marginTop: 8, padding: 8, borderRadius: 4, fontSize: 12,
          background: syncState.lastResult.error ? 'rgba(244,67,54,0.1)' : 'rgba(46,204,113,0.1)',
          color: syncState.lastResult.error ? 'var(--expense)' : 'var(--income)'
        }
      },
        syncState.lastResult.error
          ? '同步失败: ' + syncState.lastResult.error
          : '同步成功: ' + JSON.stringify(syncState.lastResult)
      )
    ),

    // 账户列表
    h('div', { style: { margin: '20px 0 12px', fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)' } }, t('accountMgmt')),
    ...store.accounts.map(a => h('div.settings-item', { key: a.id },
      h('div.settings-icon', null, h('span', { style: { background: a.color + '20', color: a.color, borderRadius: '50%', width: 32, height: 32, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700 } }, a.name[0])),
      h('div.settings-info', null, h('div.settings-title', null, a.name), h('div.settings-subtitle', null, typeName(a.type))),
      h('span', { style: { fontWeight: 600 } }, `¥${fmtMoney(a.balance)}`)
    )),

    // 关于
    h('div', { style: { margin: '20px 0 12px', fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)' } }, t('about')),
    h(SettingsItem, { icon: 'ℹ️', title: t('version'), subtitle: t('versionDesc') })
  );
}

// ============================================================================
// 十四、启动
// ============================================================================

ReactDOM.createRoot(document.getElementById('root')).render(h(App, null));
