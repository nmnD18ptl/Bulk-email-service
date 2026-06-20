const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const { autoUpdater } = require('electron-updater');
const log = require('electron-log');

log.transports.file.resolvePath = () => path.join(app.getPath('userData'), 'logs/main.log');

let mainWindow = null;
let springBootProcess = null;
let backendReady = false;
const BACKEND_PORT = 8080;
const STARTUP_TIMEOUT = 60000; // 60 seconds

// ─── Backend lifecycle ────────────────────────────────────────────────────────

function getJavaPath() {
  // Try JAVA_HOME first, then system PATH
  const javaHome = process.env.JAVA_HOME;
  if (javaHome) {
    return path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  }
  return 'java'; // Fall back to PATH
}

function getJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'backend', 'bulk-email-pro.jar');
  }
  return path.join(__dirname, '..', 'backend', 'target', 'bulk-email-pro-1.0.0.jar');
}

function startSpringBoot() {
  return new Promise((resolve, reject) => {
    const jarPath = getJarPath();
    const javaPath = getJavaPath();
    const userDataDir = app.getPath('userData');

    log.info(`Starting Spring Boot from: ${jarPath}`);
    log.info(`User data dir: ${userDataDir}`);

    const args = [
      '-jar', jarPath,
      `-Dspring.datasource.url=jdbc:h2:file:${userDataDir}/data/emaildb;AUTO_SERVER=TRUE`,
      `-Dlogging.file.name=${userDataDir}/logs/app.log`,
      `-Dserver.port=${BACKEND_PORT}`,
      '-Djava.awt.headless=true'
    ];

    springBootProcess = spawn(javaPath, args, {
      env: { ...process.env },
      stdio: ['ignore', 'pipe', 'pipe']
    });

    let startupTimer = setTimeout(() => {
      reject(new Error('Spring Boot startup timed out'));
    }, STARTUP_TIMEOUT);

    springBootProcess.stdout.on('data', (data) => {
      const output = data.toString();
      log.info(`[Spring Boot] ${output.trim()}`);

      // Check for successful startup
      if (output.includes('Started BulkEmailProApplication') ||
          output.includes('Tomcat started on port')) {
        clearTimeout(startupTimer);
        backendReady = true;
        log.info('Spring Boot started successfully');
        resolve(true);
      }
    });

    springBootProcess.stderr.on('data', (data) => {
      const output = data.toString();
      if (!output.includes('INFO') && !output.includes('WARN')) {
        log.error(`[Spring Boot Error] ${output.trim()}`);
      }
    });

    springBootProcess.on('error', (err) => {
      clearTimeout(startupTimer);
      log.error('Failed to start Spring Boot:', err.message);
      reject(err);
    });

    springBootProcess.on('close', (code) => {
      log.info(`Spring Boot process exited with code: ${code}`);
      backendReady = false;
      if (code !== 0 && mainWindow) {
        mainWindow.webContents.executeJavaScript(
          `window.dispatchEvent(new CustomEvent('backend-error', {detail: {code: ${code}}}))`
        );
      }
    });
  });
}

function stopSpringBoot() {
  if (springBootProcess) {
    log.info('Stopping Spring Boot...');
    springBootProcess.kill('SIGTERM');
    springBootProcess = null;
  }
}

// ─── Window ───────────────────────────────────────────────────────────────────

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    title: 'Bulk Email Pro',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js')
    },
    show: false,
    backgroundColor: '#F9FAFB',
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default'
  });

  // Show loading screen first
  mainWindow.loadFile(path.join(__dirname, 'loading.html'));
  mainWindow.once('ready-to-show', () => mainWindow.show());

  mainWindow.on('closed', () => { mainWindow = null; });
}

function loadApp() {
  if (!mainWindow) return;
  if (app.isPackaged) {
    mainWindow.loadFile(path.join(__dirname, '..', 'frontend', 'dist', 'bulk-email-pro', 'browser', 'index.html'));
  } else {
    mainWindow.loadURL('http://localhost:4200');
  }
}

// ─── App events ───────────────────────────────────────────────────────────────

app.whenReady().then(async () => {
  createWindow();

  try {
    log.info('Starting backend...');
    await startSpringBoot();

    // Wait a moment for backend to fully initialize
    await new Promise(resolve => setTimeout(resolve, 2000));

    log.info('Loading Angular app...');
    loadApp();

  } catch (err) {
    log.error('Backend startup failed:', err.message);

    // Show error and offer to retry or continue (dev mode)
    if (!app.isPackaged) {
      log.warn('Dev mode: loading app anyway (assuming backend is running separately)');
      loadApp();
    } else {
      dialog.showErrorBox('Startup Error',
        `Failed to start the email server: ${err.message}\n\nPlease ensure Java 17+ is installed.`);
    }
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  stopSpringBoot();
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => stopSpringBoot());

// ─── IPC handlers ─────────────────────────────────────────────────────────────

ipcMain.handle('get-app-version', () => app.getVersion());

ipcMain.handle('get-user-data-path', () => app.getPath('userData'));

ipcMain.handle('is-backend-ready', () => backendReady);

ipcMain.handle('open-external', (_, url) => shell.openExternal(url));

ipcMain.handle('show-open-dialog', async (_, options) => {
  const result = await dialog.showOpenDialog(mainWindow, options);
  return result;
});

ipcMain.handle('show-save-dialog', async (_, options) => {
  const result = await dialog.showSaveDialog(mainWindow, options);
  return result;
});

// ─── Auto updater ─────────────────────────────────────────────────────────────

if (app.isPackaged) {
  autoUpdater.logger = log;
  autoUpdater.checkForUpdatesAndNotify();

  autoUpdater.on('update-downloaded', () => {
    dialog.showMessageBox({
      type: 'info',
      title: 'Update Ready',
      message: 'A new version has been downloaded. Restart to install?',
      buttons: ['Restart Now', 'Later']
    }).then(result => {
      if (result.response === 0) autoUpdater.quitAndInstall();
    });
  });
}
