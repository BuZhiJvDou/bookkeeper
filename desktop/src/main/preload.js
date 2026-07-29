const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  transactions: {
    getAll: () => ipcRenderer.invoke('transactions:getAll'),
    getByDateRange: (startDate, endDate) =>
      ipcRenderer.invoke('transactions:getByDateRange', startDate, endDate),
    add: (transaction) => ipcRenderer.invoke('transactions:add', transaction),
    update: (id, transaction) =>
      ipcRenderer.invoke('transactions:update', id, transaction),
    delete: (id) => ipcRenderer.invoke('transactions:delete', id),
    getStats: (startDate, endDate) =>
      ipcRenderer.invoke('transactions:getStats', startDate, endDate),
    transfer: (data) => ipcRenderer.invoke('transactions:transfer', data),
  },
  categories: {
    getAll: () => ipcRenderer.invoke('categories:getAll'),
    add: (category) => ipcRenderer.invoke('categories:add', category),
    delete: (id) => ipcRenderer.invoke('categories:delete', id),
  },
  accounts: {
    getAll: () => ipcRenderer.invoke('accounts:getAll'),
    add: (account) => ipcRenderer.invoke('accounts:add', account),
    updateBalance: (id, amount) =>
      ipcRenderer.invoke('accounts:updateBalance', id, amount),
  },
  budgets: {
    getAll: () => ipcRenderer.invoke('budgets:getAll'),
    add: (budget) => ipcRenderer.invoke('budgets:add', budget),
    update: (id, budget) => ipcRenderer.invoke('budgets:update', id, budget),
    delete: (id) => ipcRenderer.invoke('budgets:delete', id),
  },
  data: {
    export: () => ipcRenderer.invoke('data:export'),
    import: () => ipcRenderer.invoke('data:import'),
    exportCsv: () => ipcRenderer.invoke('data:exportCsv'),
  },
});
