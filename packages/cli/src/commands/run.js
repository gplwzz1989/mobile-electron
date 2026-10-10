// @mobile-electron/cli - Run Command (Device Launcher)

const { execSync, spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');
const { checkAndroidSdk, runSync } = require('../utils/env');

function runCommand(platform = 'android', options = {}) {
  logger.banner();
  const root = process.cwd();
  platform = platform.toLowerCase();

  if (platform === 'android') {
    console.log(`${logger.colors.bold}📱 正在准备在 Android 设备/模拟器上部署与启动应用...${logger.colors.reset}\n`);

    const sdkInfo = checkAndroidSdk();
    if (!sdkInfo.ok) {
      logger.error('未检测到 Android SDK 或 adb 调试工具，无法自动部署。');
      return;
    }

    if (sdkInfo.devices.length === 0) {
      logger.warn('未检测到在线的 Android 设备或模拟器！');
      console.log('  建议: 请先启动 Android Studio 模拟器或将真机开启 USB 调试模式连接电脑。');
      return;
    }

    const targetDevice = sdkInfo.devices[0].id;
    logger.info(`目标设备: [${targetDevice}]`);

    // 1. 编译 Debug APK
    console.log(`\n${logger.colors.cyan}[1/3]${logger.colors.reset} 编译 Android Debug 安装包...`);
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
    try {
      execSync(`${gradlew} assembleDebug`, { cwd: root, stdio: 'inherit' });
    } catch (e) {
      logger.error(`Gradle 编译失败: ${e.message}`);
      return;
    }

    // 2. 安装 APK
    const apkPath = path.join(root, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
    if (!fs.existsSync(apkPath)) {
      logger.error(`找不到编译生成的 APK: ${apkPath}`);
      return;
    }

    console.log(`\n${logger.colors.cyan}[2/3]${logger.colors.reset} 正在安装 APK 至设备 [${targetDevice}]...`);
    try {
      execSync(`adb -s ${targetDevice} install -r "${apkPath}"`, { stdio: 'inherit' });
      logger.success('APK 安装成功！');
    } catch (e) {
      logger.error(`APK 安装失败: ${e.message}`);
      return;
    }

    // 3. 启动应用
    console.log(`\n${logger.colors.cyan}[3/3]${logger.colors.reset} 正在拉起主 Activity (com.example.cookiebrowser)...`);
    try {
      execSync(`adb -s ${targetDevice} shell am start -n com.example.cookiebrowser/.MainActivity`, { stdio: 'inherit' });
      logger.success('🚀 应用已在设备上成功启动！');
    } catch (e) {
      logger.error(`启动失败: ${e.message}`);
    }

  } else if (platform === 'ios') {
    console.log(`${logger.colors.bold}🍎 正在准备在 iOS 模拟器上启动应用...${logger.colors.reset}\n`);
    if (process.platform !== 'darwin') {
      logger.warn('当前运行环境非 macOS，原生 iOS 应用无法直接在本地编译运行。');
      console.log('  说明: iOS 源码位于 ios/MobileElectron/ 目录。');
      console.log('  方式 1: 将代码推送到 GitHub，由 .github/workflows/ci.yml 自动在 macOS Runner 上编译构建。');
      console.log('  方式 2: 在 Mac 电脑上使用 Xcode 打开 ios/ 目录运行。');
      return;
    }

    logger.info('正在调用 Xcode 构建并启动 iOS 模拟器...');
    // macOS 环境构建调用
    try {
      execSync('xcodebuild -project ios/MobileElectron.xcodeproj -scheme MobileElectron -destination "platform=iOS Simulator,name=iPhone 15" build', {
        cwd: root,
        stdio: 'inherit'
      });
      logger.success('iOS 应用已在模拟器启动！');
    } catch (e) {
      logger.error(`iOS 构建失败: ${e.message}`);
    }
  } else {
    logger.error(`不支持的平台: ${platform}。可选: android, ios`);
  }
}

module.exports = runCommand;
