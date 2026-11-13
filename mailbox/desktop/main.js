import { app, BrowserWindow, dialog, shell, Menu } from 'electron';
import { existsSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createMailboxServer } from '../server.js';
import { navigationTarget } from './navigation.js';

let mainWindow;
let mailboxServer;
let stopping = false;

function focusWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

async function startDesktop() {
  // Only public OAuth client identifiers are included in a release.
  const configurationDirectory = app.isPackaged ? app.getPath('userData') : join(dirname(fileURLToPath(import.meta.url)), '..');
  const environmentFile = join(configurationDirectory, '.env');
  if (existsSync(environmentFile)) process.loadEnvFile(environmentFile);
  const packagedClients = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).desktopOAuthClients;
  const developmentClients = {
    gmail: process.env.GOOGLE_DESKTOP_CLIENT_ID,
    outlook: process.env.MICROSOFT_DESKTOP_CLIENT_ID
  };
  const desktopClients = app.isPackaged ? packagedClients
    : developmentClients.gmail || developmentClients.outlook ? developmentClients : null;
  const port = Number(process.env.PORT || 4317);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('PORT must be an integer between 1024 and 65535.');
  const origin = `http://127.0.0.1:${port}`;

  // The desktop owns its server; never attach to an unknown process using this port.
  mailboxServer = createMailboxServer({ port, externalOAuth: true, desktopClients: desktopClients ?? null });
  await new Promise((resolve, reject) => {
    mailboxServer.once('error', reject);
    mailboxServer.listen(port, '127.0.0.1', resolve);
  });

  mainWindow = new BrowserWindow({
    title: 'SuperMAS Mail', width: 1380, height: 900, minWidth: 900, minHeight: 640,
    backgroundColor: '#171817', show: false, autoHideMenuBar: true,
    webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true,
      webSecurity: true, partition: 'supermas-mail', spellcheck: false }
  });
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { label: 'Application', submenu: [
      { label: 'Open configuration folder', click: () => shell.openPath(configurationDirectory).catch(() => {}) },
      { type: 'separator' }, { role: 'quit' }
    ] },
    { role: 'editMenu' },
    { label: 'View', submenu: [{ role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' }, { role: 'togglefullscreen' }] }
  ]));
  const contents = mainWindow.webContents;
  const windowSession = contents.session;
  windowSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  windowSession.setPermissionCheckHandler(() => false);
  windowSession.on('will-download', event => event.preventDefault());
  // Renderer traffic stays local. OAuth is handed to the system browser instead.
  windowSession.webRequest.onBeforeRequest((details, callback) => {
    const destination = new URL(details.url);
    callback({ cancel: destination.origin !== origin && destination.protocol !== 'devtools:' });
  });

  function handleNavigation(event, destination) {
    const target = navigationTarget(destination, origin);
    if (target === 'application') return;
    event.preventDefault();
    if (target === 'authorization') {
      shell.openExternal(destination).catch(() => dialog.showErrorBox('Authorization could not open', 'Please check your default browser and try again.'));
    }
  }
  contents.on('will-navigate', handleNavigation);
  contents.on('will-redirect', handleNavigation);
  contents.setWindowOpenHandler(() => ({ action: 'deny' }));
  contents.on('will-attach-webview', event => event.preventDefault());
  contents.on('will-prevent-unload', event => {
    const choice = dialog.showMessageBoxSync(mainWindow, { type: 'question', title: 'Unfinished draft',
      message: 'Leave without saving the current draft?', buttons: ['Keep editing', 'Discard changes'], defaultId: 0, cancelId: 0 });
    // Electron uses preventDefault here to allow unloading despite the renderer veto.
    if (choice === 1) event.preventDefault();
  });
  mainWindow.once('ready-to-show', focusWindow);
  mailboxServer.on('account-connected', () => {
    if (mainWindow.isDestroyed()) return;
    mainWindow.loadURL(`${origin}/?connected=1`).catch(showStartupError);
    focusWindow();
  });
  await mainWindow.loadURL(origin);
}

function showStartupError(error) {
  const message = error.code === 'EADDRINUSE'
    ? 'The mailbox port is already in use. Close the running web mailbox or configure a different PORT and OAuth redirect URI.'
    : error.message;
  dialog.showErrorBox('SuperMAS Mail could not start', message);
  app.quit();
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', focusWindow);
  app.whenReady().then(startDesktop).catch(showStartupError);
  app.on('window-all-closed', () => app.quit());
  app.on('will-quit', event => {
    if (stopping || !mailboxServer?.listening) return;
    event.preventDefault();
    stopping = true;
    mailboxServer.close(() => app.quit());
    mailboxServer.closeAllConnections();
  });
}
