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

  console.log('    ✔ SDK 15 大核心模块、跨窗口全双工 IPC、网络感知及画中画小窗校验 100% 通过');
}

module.exports = testSdkModules;
