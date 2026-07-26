const { app, BrowserWindow, ipcMain, dialog, Menu } = require('electron');
const path = require('path');
const db = require('../database/db');

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

  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  db.close();
  if (process.platform !== 'darwin') app.quit();
});

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
}
