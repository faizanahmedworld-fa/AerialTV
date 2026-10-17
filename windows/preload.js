const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('aerial', {
  countries: () => ipcRenderer.invoke('countries'),
  systemCountries: () => ipcRenderer.invoke('system-countries'),
  playlist: (country) => ipcRenderer.invoke('playlist', country),
  setStreamHeaders: (headers) => ipcRenderer.send('stream-headers', headers),
  toggleFullscreen: (value) => ipcRenderer.send('fullscreen', value),
  onFullscreen: (fn) => ipcRenderer.on('fullscreen', (_e, v) => fn(v)),
  version: () => ipcRenderer.invoke('version'),
  checkUpdate: () => ipcRenderer.invoke('check-update'),
  installUpdate: () => ipcRenderer.invoke('install-update'),
  onUpdateAvailable: (fn) => ipcRenderer.on('update-available', (_e, v) => fn(v)),
  onUpdateProgress: (fn) => ipcRenderer.on('update-progress', (_e, v) => fn(v)),
})
