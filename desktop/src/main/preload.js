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
  recurring: {
    getAll: () => ipcRenderer.invoke('recurring:getAll'),
    add: (rule) => ipcRenderer.invoke('recurring:add', rule),
    update: (id, rule) => ipcRenderer.invoke('recurring:update', id, rule),
    delete: (id) => ipcRenderer.invoke('recurring:delete', id),
    process: () => ipcRenderer.invoke('recurring:process'),
  },
  data: {
    export: () => ipcRenderer.invoke('data:export'),
    import: () => ipcRenderer.invoke('data:import'),
    exportCsv: () => ipcRenderer.invoke('data:exportCsv'),
  },
  sync: {
    startServer: () => ipcRenderer.invoke('bk-sync:startServer'),
    stopServer: () => ipcRenderer.invoke('bk-sync:stopServer'),
    startDiscovery: () => ipcRenderer.invoke('bk-sync:startDiscovery'),
    stopDiscovery: () => ipcRenderer.invoke('bk-sync:stopDiscovery'),
    syncWith: (peer, sinceTs) => ipcRenderer.invoke('bk-sync:syncWith', peer, sinceTs),
    syncWithUrl: (url, sinceTs) => ipcRenderer.invoke('bk-sync:syncWithUrl', url, sinceTs),
    getState: () => ipcRenderer.invoke('bk-sync:getState'),
    diagnose: () => ipcRenderer.invoke('bk-sync:diagnose'),
    on: (event, fn) => {
      const ch = `bk-sync:event:${event}`;
      const wrapped = (_e, data) => fn(data);
      ipcRenderer.on(ch, wrapped);
      return () => ipcRenderer.removeListener(ch, wrapped);
    }
  },
  system: {
    getLocalIp: () => ipcRenderer.invoke('system:getLocalIp'),
  },
});
