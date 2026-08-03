const { app, BrowserWindow, ipcMain, dialog, Menu } = require('electron');
const path = require('path');
const db = require('../database/db');
const { SyncManager } = require('../sync');

// 单实例锁定
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });


let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    titleBarStyle: 'hiddenInset',
    title: '记账单',
  });

  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
}

app.whenReady().then(() => {
  // 隐藏菜单栏
  Menu.setApplicationMenu(null);

  // 初始化数据库
  db.init();

  // 处理到期的循环记账规则（自动补记错过的周期）
  try {
    const created = db.processRecurringRules();
    if (created > 0) console.log(`[循环记账] 自动生成 ${created} 条交易`);
  } catch (e) {
    console.error('[循环记账] 处理失败:', e);
  }

  createWindow();

  // 初始化同步管理器（不会自动启动，需用户在设置页开启）
  syncManager = new SyncManager({ db });
  syncManager.setNotifier((event, data) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send(`bk-sync:event:${event}`, data);
    }
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  db.close();
  if (syncManager) syncManager.stop();
  if (process.platform !== 'darwin') app.quit();
});

// === 同步 IPC ===

let syncManager;

// === IPC Handlers ===

// 交易记录
ipcMain.handle('transactions:getAll', () => {
  return db.getAllTransactions();
});

ipcMain.handle('transactions:getByDateRange', (event, startDate, endDate) => {
  return db.getTransactionsByDateRange(startDate, endDate);
});

ipcMain.handle('transactions:add', (event, transaction) => {
  return db.addTransaction(transaction);
});

ipcMain.handle('transactions:update', (event, id, transaction) => {
  return db.updateTransaction(id, transaction);
});

ipcMain.handle('transactions:delete', (event, id) => {
  return db.deleteTransaction(id);
});

ipcMain.handle('transactions:getStats', (event, startDate, endDate) => {
  return db.getTransactionStats(startDate, endDate);
});

// 分类
ipcMain.handle('categories:getAll', () => {
  return db.getAllCategories();
});

ipcMain.handle('categories:add', (event, category) => {
  return db.addCategory(category);
});

ipcMain.handle('categories:delete', (event, id) => {
  return db.deleteCategory(id);
});

// 账户
ipcMain.handle('accounts:getAll', () => {
  return db.getAllAccounts();
});

ipcMain.handle('accounts:add', (event, account) => {
  return db.addAccount(account);
});

ipcMain.handle('accounts:updateBalance', (event, id, amount) => {
  return db.updateAccountBalance(id, amount);
});

// 预算
ipcMain.handle('budgets:getAll', () => {
  return db.getAllBudgets();
});

ipcMain.handle('budgets:add', (event, budget) => {
  return db.addBudget(budget);
});

ipcMain.handle('budgets:update', (event, id, budget) => {
  return db.updateBudget(id, budget);
});

ipcMain.handle('budgets:delete', (event, id) => {
  return db.deleteBudget(id);
});

// 循环记账
ipcMain.handle('recurring:getAll', () => {
  return db.getAllRecurringRules();
});

ipcMain.handle('recurring:add', (event, rule) => {
  return db.addRecurringRule(rule);
});

ipcMain.handle('recurring:update', (event, id, rule) => {
  return db.updateRecurringRule(id, rule);
});

ipcMain.handle('recurring:delete', (event, id) => {
  return db.deleteRecurringRule(id);
});

ipcMain.handle('recurring:process', () => {
  return db.processRecurringRules();
});

// 导入导出
ipcMain.handle('data:export', async () => {
  const result = await dialog.showSaveDialog(mainWindow, {
    title: '导出数据',
    defaultPath: `bookkeeper-export-${Date.now()}.json`,
    filters: [{ name: 'JSON', extensions: ['json'] }],
  });

  if (!result.canceled) {
    const data = db.exportAllData();
    const fs = require('fs');
    fs.writeFileSync(result.filePath, JSON.stringify(data, null, 2));
    return { success: true, path: result.filePath };
  }
  return { success: false };
});

ipcMain.handle('data:import', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: '导入数据',
    filters: [{ name: 'JSON', extensions: ['json'] }],
    properties: ['openFile'],
  });

  if (!result.canceled) {
    const fs = require('fs');
    const data = JSON.parse(fs.readFileSync(result.filePaths[0], 'utf-8'));
    const count = db.importData(data);
    return { success: true, count };
  }
  return { success: false };
});

ipcMain.handle('data:exportCsv', async () => {
  const result = await dialog.showSaveDialog(mainWindow, {
    title: '导出 CSV',
    defaultPath: `bookkeeper-${Date.now()}.csv`,
    filters: [{ name: 'CSV', extensions: ['csv'] }],
  });

  if (!result.canceled) {
    const csv = db.exportToCsv();
    const fs = require('fs');
    fs.writeFileSync(result.filePath, '\uFEFF' + csv, 'utf-8'); // BOM for Excel
    return { success: true, path: result.filePath };
  }
  return { success: false };
});

// 同步
ipcMain.handle('bk-sync:startServer', async () => {
  return syncManager.startServer();
});

ipcMain.handle('bk-sync:stopServer', async () => {
  return syncManager.stopServer();
});

ipcMain.handle('bk-sync:startDiscovery', async () => {
  syncManager.startDiscovery();
  return { ok: true };
});

ipcMain.handle('bk-sync:stopDiscovery', async () => {
  syncManager.stopDiscovery();
  return { ok: true };
});

ipcMain.handle('bk-sync:syncWith', async (event, peer, sinceTs) => {
  return syncManager.syncWith(peer, sinceTs || 0);
});

ipcMain.handle('bk-sync:syncWithUrl', async (event, url, sinceTs) => {
  return syncManager.syncWithUrl(url, sinceTs || 0);
});

ipcMain.handle('bk-sync:getState', async () => {
  return syncManager.getState();
});
}
