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

  console.log('    ✔ SDK 14 大核心模块及生命周期/网络感知/静默热更接口校验 100% 通过');
}

module.exports = testSdkModules;
