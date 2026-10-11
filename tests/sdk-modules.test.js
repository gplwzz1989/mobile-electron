// tests/sdk-modules.test.js - SDK Modules & Mock Engine Tests

const assert = require('assert');
const path = require('path');

async function testSdkModules() {
  console.log('  ▸ 测试 @mobile-electron/core SDK 导出的所有核心模块与 Mock 仿真...');

  // 引入编译好的 SDK (CommonJS)
  const sdkPath = path.resolve(__dirname, '../sdk/dist/index.cjs');
  const { electron, MobileElectron } = require(sdkPath);

  assert.ok(electron, 'electron 单例必须存在');
  assert.ok(MobileElectron, 'MobileElectron 类必须导出');

  // 1. App 模块
  assert.ok(electron.app, 'electron.app 必须存在');
  const info = await electron.app.getInfo();
  assert.ok(info.appName, 'getInfo 应返回 appName');
  assert.ok(info.version, 'getInfo 应返回 version');

  const cfg = await electron.app.getConfig();
  assert.ok(cfg, 'getConfig 应返回有效配置');

  // 云控接口
  assert.strictEqual(typeof electron.app.fetchRemoteConfig, 'function');
  assert.strictEqual(typeof electron.app.applyRemoteConfig, 'function');
  assert.strictEqual(typeof electron.app.resetRemoteConfig, 'function');
  assert.strictEqual(typeof electron.app.getConfigStatus, 'function');

  // 通用 URL Scheme / DeepLink 协议拉起接口
  assert.strictEqual(typeof electron.app.canOpenUrl, 'function', 'canOpenUrl 必须为函数');
  assert.strictEqual(typeof electron.app.openUrl, 'function', 'openUrl 必须为函数');

  const canOpenDouyin = await electron.app.canOpenUrl('snssdk1128://webview?url=https://iesdouyin.com/user/123');
  assert.strictEqual(typeof canOpenDouyin, 'boolean');

  const openDouyinRes = await electron.app.openUrl('snssdk1128://webview?url=https://iesdouyin.com/user/123', {
    fallbackUrl: 'https://www.douyin.com'
  });
  assert.strictEqual(openDouyinRes.success, true);

  // 2. Storage & SecureStorage 模块
  assert.ok(electron.storage, 'electron.storage 必须存在');
  await electron.storage.set('test_k', 'test_v');
  const storedVal = await electron.storage.get('test_k');
  assert.strictEqual(storedVal, 'test_v', 'KV 存取值必须一致');

  assert.ok(electron.secureStorage, 'electron.secureStorage 必须存在');
  await electron.secureStorage.setItem('secret_k', 'super_secret');
  const secVal = await electron.secureStorage.getItem('secret_k');
  assert.strictEqual(secVal, 'super_secret', '安全加密存储存取值必须一致');

  // 3. Cookie 模块
  assert.ok(electron.cookie, 'electron.cookie 必须存在');
  assert.strictEqual(typeof electron.cookie.get, 'function');
  assert.strictEqual(typeof electron.cookie.set, 'function');
  assert.strictEqual(typeof electron.cookie.getAll, 'function');

  // 4. File 模块
  assert.ok(electron.file, 'electron.file 必须存在');
  assert.strictEqual(typeof electron.file.readText, 'function');
  assert.strictEqual(typeof electron.file.writeText, 'function');
  assert.strictEqual(typeof electron.file.exists, 'function');

  // 5. 特权与硬件插件 (Haptics, Biometrics, Camera)
  assert.ok(electron.haptics, 'electron.haptics 必须存在');
  assert.strictEqual(typeof electron.haptics.impact, 'function');
  assert.strictEqual(typeof electron.haptics.notification, 'function');

  assert.ok(electron.biometrics, 'electron.biometrics 必须存在');
  const bio = await electron.biometrics.isAvailable();
  assert.strictEqual(typeof bio.supported, 'boolean');

  assert.ok(electron.camera, 'electron.camera 必须存在');
  assert.strictEqual(typeof electron.camera.takePhoto, 'function');
  assert.strictEqual(typeof electron.camera.scanBarcode, 'function');

  // 6. TabBar 模块
  assert.ok(electron.tabBar, 'electron.tabBar 必须存在');
  assert.strictEqual(typeof electron.tabBar.setItems, 'function');
  assert.strictEqual(typeof electron.tabBar.onTabClick, 'function');

  // 7. Network 网络状态与生命周期
  assert.strictEqual(typeof electron.app.onStateChange, 'function', 'app.onStateChange 必须为函数');
  assert.strictEqual(typeof electron.network.getStatus, 'function', 'network.getStatus 必须为函数');
  assert.strictEqual(typeof electron.network.onNetworkChange, 'function', 'network.onNetworkChange 必须为函数');
  const netStatus = await electron.network.getStatus();
  assert.strictEqual(typeof netStatus.isConnected, 'boolean');

  // 8. HotUpdate 增量热更新与静默就绪监听模块
  assert.ok(electron.hotUpdate, 'electron.hotUpdate 必须存在');
  assert.strictEqual(typeof electron.hotUpdate.onUpdateReady, 'function', 'hotUpdate.onUpdateReady 必须为函数');
  const ver = await electron.hotUpdate.getCurrentVersion();
  assert.ok(ver.version, '热更版本应包含 version 字段');

  // 9. 多窗口与画中画悬浮窗 (Floating Window Overlay)
  assert.strictEqual(typeof electron.window.createFloatingWindow, 'function', 'createFloatingWindow 必须为函数');
  assert.strictEqual(typeof electron.window.closeFloatingWindow, 'function', 'closeFloatingWindow 必须为函数');
  assert.strictEqual(typeof electron.window.minimizeFloatingWindow, 'function', 'minimizeFloatingWindow 必须为函数');
  assert.strictEqual(typeof electron.window.updateFloatingWindow, 'function', 'updateFloatingWindow 必须为函数');
  assert.strictEqual(typeof electron.window.evalFloatingWindow, 'function', 'evalFloatingWindow 必须为函数');
  assert.strictEqual(typeof electron.window.getFloatingWindowState, 'function', 'getFloatingWindowState 必须为函数');

  const floatWin = await electron.window.createFloatingWindow({
    id: 'test_float_win',
    url: 'https://m.bilibili.com',
    title: '测试悬浮画中画',
    width: 300,
    height: 220,
    x: 50,
    y: 100
  });
  assert.strictEqual(floatWin.success, true);
  assert.strictEqual(floatWin.id, 'test_float_win');
  assert.strictEqual(floatWin.width, 300);

  const floatState = await electron.window.getFloatingWindowState('test_float_win');
  assert.ok(floatState);
  assert.strictEqual(floatState.title, '测试悬浮画中画');
  assert.strictEqual(floatState.isMinimized, false);

  const updateRes = await electron.window.updateFloatingWindow('test_float_win', { x: 80, y: 120 });
  assert.strictEqual(updateRes, true);

  const minRes = await electron.window.minimizeFloatingWindow('test_float_win', true);
  assert.strictEqual(minRes, true);

  const evalRes = await electron.window.evalFloatingWindow('test_float_win', 'document.title');
  assert.ok(evalRes);

  const closeRes = await electron.window.closeFloatingWindow('test_float_win');
  assert.strictEqual(closeRes, true);

  // 10. 跨窗口全双工 IPC 通信通道 (Cross-Window IPC Bus)
  assert.ok(electron.ipc, 'electron.ipc 必须存在');
  assert.strictEqual(typeof electron.ipc.send, 'function');
  assert.strictEqual(typeof electron.ipc.sendTo, 'function');
  assert.strictEqual(typeof electron.ipc.broadcast, 'function');
  assert.strictEqual(typeof electron.ipc.on, 'function');
  assert.strictEqual(typeof electron.ipc.once, 'function');

  let receivedIpcPayload = null;
  const unsubIpc = electron.ipc.on('test-channel', (event) => {
    receivedIpcPayload = event.payload;
  });

  const sendRes = await electron.ipc.send('test-channel', { message: 'hello from test', count: 42 });
  assert.strictEqual(sendRes, true);
  assert.ok(receivedIpcPayload, 'IPC 监听器应成功接收到消息载荷');
  assert.strictEqual(receivedIpcPayload.message, 'hello from test');
  assert.strictEqual(receivedIpcPayload.count, 42);

  const sendToRes = await electron.ipc.sendTo('main', 'test-channel', { direct: true });
  assert.strictEqual(sendToRes, true);

  unsubIpc();

  // 11. 剪贴板模块测试 (Clipboard Module)
  assert.ok(electron.clipboard, 'electron.clipboard 必须存在');
  assert.strictEqual(typeof electron.clipboard.readText, 'function');
  assert.strictEqual(typeof electron.clipboard.writeText, 'function');
  assert.strictEqual(typeof electron.clipboard.hasText, 'function');
  assert.strictEqual(typeof electron.clipboard.clear, 'function');

  await electron.clipboard.writeText('Hello Mobile Electron Clipboard!');
  const clipText = await electron.clipboard.readText();
  assert.strictEqual(clipText, 'Hello Mobile Electron Clipboard!');
  const hasTextBefore = await electron.clipboard.hasText();
  assert.strictEqual(hasTextBefore, true);

  await electron.clipboard.clear();
  const hasTextAfter = await electron.clipboard.hasText();
  assert.strictEqual(hasTextAfter, false);

  // 12. 本地系统通知模块测试 (Notification Module)
  assert.ok(electron.notification, 'electron.notification 必须存在');
  assert.strictEqual(typeof electron.notification.show, 'function');
  assert.strictEqual(typeof electron.notification.cancel, 'function');
  assert.strictEqual(typeof electron.notification.cancelAll, 'function');
  assert.strictEqual(typeof electron.notification.areNotificationsEnabled, 'function');

  const notifRes = await electron.notification.show({
    title: '系统通知测试',
    body: '这是一条单元测试通知',
    id: 9999
  });
  assert.strictEqual(notifRes.success, true);
  assert.strictEqual(notifRes.id, 9999);

  const cancelRes = await electron.notification.cancel(9999);
  assert.strictEqual(cancelRes, true);

  const cancelAllRes = await electron.notification.cancelAll();
  assert.strictEqual(cancelAllRes, true);

  const notifEnabled = await electron.notification.areNotificationsEnabled();
  assert.strictEqual(notifEnabled, true);

  // 13. 状态栏模块测试 (StatusBar Module)
  assert.ok(electron.statusBar, 'electron.statusBar 必须存在');
  assert.strictEqual(await electron.statusBar.setStyle('light'), true);
  assert.strictEqual(await electron.statusBar.setColor('#0F172A'), true);
  assert.strictEqual(await electron.statusBar.setTransparent(true), true);
  assert.strictEqual(await electron.statusBar.show(), true);
  assert.strictEqual(await electron.statusBar.hide(), true);
  const statusInfo = await electron.statusBar.getInfo();
  assert.strictEqual(typeof statusInfo.height, 'number');

  // 14. 屏幕与显示控制模块测试 (Screen Module)
  assert.ok(electron.screen, 'electron.screen 必须存在');
  assert.strictEqual(await electron.screen.setOrientation('landscape'), true);
  assert.strictEqual(await electron.screen.getOrientation(), 'portrait');
  assert.strictEqual(await electron.screen.setKeepScreenOn(true), true);
  assert.strictEqual(await electron.screen.setBrightness(0.8), true);
  assert.strictEqual(await electron.screen.getBrightness(), 1.0);

  // 15. 软键盘控制与感知模块测试 (Keyboard Module)
  assert.ok(electron.keyboard, 'electron.keyboard 必须存在');
  assert.strictEqual(await electron.keyboard.hide(), true);
  assert.strictEqual(await electron.keyboard.show(), true);
  const kbState = await electron.keyboard.isVisible();
  assert.strictEqual(typeof kbState.visible, 'boolean');
  assert.strictEqual(typeof kbState.height, 'number');
  const unsubKb = electron.keyboard.onVisibilityChange(() => {});
  assert.strictEqual(typeof unsubKb, 'function');
  unsubKb();

  // 16. 电池电量与状态感知模块测试 (Battery Module)
  assert.ok(electron.battery, 'electron.battery 必须存在');
  const batteryStatus = await electron.battery.getStatus();
  assert.strictEqual(typeof batteryStatus.level, 'number');
  assert.strictEqual(typeof batteryStatus.isCharging, 'boolean');
  assert.strictEqual(typeof batteryStatus.plugged, 'string');
  const unsubBattery = electron.battery.onChange(() => {});
  assert.strictEqual(typeof unsubBattery, 'function');
  unsubBattery();

  // 17. 设备硬件信息查询扩展测试 (Device.getInfo)
  assert.strictEqual(typeof electron.device.getInfo, 'function');
  const devInfo = await electron.device.getInfo();
  assert.strictEqual(typeof devInfo.brand, 'string');
  assert.strictEqual(typeof devInfo.model, 'string');
  assert.strictEqual(typeof devInfo.screenWidth, 'number');
  assert.strictEqual(typeof devInfo.deviceId, 'string');

  // 18. 交互与系统分享扩展测试 (Dialog.actionSheet & Dialog.share)
  assert.strictEqual(typeof electron.dialog.actionSheet, 'function');
  const sheetRes = await electron.dialog.actionSheet({ options: ['选项A', '选项B'] });
  assert.strictEqual(sheetRes.index, 0);
  assert.strictEqual(sheetRes.value, '选项A');
  assert.strictEqual(typeof electron.dialog.share, 'function');
  assert.strictEqual(await electron.dialog.share({ title: '分享', text: '内容' }), true);

  // 19. 文件系统下载管理器与打开扩展测试 (File.downloadFile & File.openFile)
  assert.strictEqual(typeof electron.file.downloadFile, 'function');
  const dlRes = await electron.file.downloadFile({ url: 'https://example.com/file.zip' });
  assert.strictEqual(dlRes.success, true);
  assert.ok(dlRes.downloadId > 0);
  assert.strictEqual(typeof electron.file.openFile, 'function');
  assert.strictEqual(await electron.file.openFile('test.pdf'), true);

  // 20. 应用生命周期控制扩展测试 (App.minimize & App.relaunch)
  assert.strictEqual(typeof electron.app.minimize, 'function');
  assert.strictEqual(typeof electron.app.relaunch, 'function');

  console.log('    ✔ 全栈 23+ 大核心原生运行时模块、状态栏、屏幕控制、软键盘、电池、原生分享及硬件信息校验 100% 通过');
}

module.exports = testSdkModules;

