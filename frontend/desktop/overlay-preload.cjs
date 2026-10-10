const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cacheBrowseOverlay', Object.freeze({
  subscribe(callback) {
    if (typeof callback !== 'function') return () => {};
    const listener = (_event, state) => callback(state);
    ipcRenderer.on('popup:state', listener);
    return () => ipcRenderer.removeListener('popup:state', listener);
  },
  action(id, action) {
    if (typeof id === 'string' && typeof action === 'string') ipcRenderer.send('popup:action', { id, action });
  },
  close(reason) { ipcRenderer.send('popup:close', String(reason || 'programmatic')); },
}));
