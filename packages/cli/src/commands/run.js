// @mobile-electron/cli - Run Command (Industrial-grade Device Launcher)

const { execSync, spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');
const { checkAndroidSdk, runSync } = require('../utils/env');

function tryAutoConnectEmulators(adbPath) {
  const commonPorts = [16384, 7555, 5555, 62001, 21503];
  for (const port of commonPorts) {
    try {
      const out = runSync(`"${adbPath}" connect 127.0.0.1:${port}`);
      if (out && (out.includes('connected') || out.includes('already connected'))) {
        return `127.0.0.1:${port}`;
      }
    } catch (_) {}
  }
  return null;
}

function runCommand(platform = 'android', options = {}) {
  logger.banner();
  const root = process.cwd();
  platform = (platform || 'android').toLowerCase();

  if (platform === 'android') {
    console.log(`${logger.colors.bold}📱 正在准备在 Android 设备/模拟器上部署与启动应用...${logger.colors.reset}\n`);

    const sdkInfo = checkAndroidSdk();
    if (!sdkInfo.ok || !sdkInfo.adbPath) {
      logger.error('未检测到 Android SDK 或 adb 调试工具，无法自动部署。');
      console.log('  建议: 请安装 platform-tools 并配置环境变量，或使用 MuMu 自带 adb。');
      return;
    }

    const adbPath = sdkInfo.adbPath;
    let devices = sdkInfo.devices || [];

    // 如果未检测到在线设备，尝试自动探测并连接常见模拟器端口 (如 MuMu 12 / 夜神等)
    if (devices.length === 0) {
      console.log(`${logger.colors.dim}未直接发现在线设备，正在尝试自动连接本地运行中的模拟器...${logger.colors.reset}`);
      const connectedHost = tryAutoConnectEmulators(adbPath);
      if (connectedHost) {
        logger.success(`成功连接到本地模拟器: [${connectedHost}]`);
        devices = [{ id: connectedHost, state: 'device' }];
      }
    }

    if (devices.length === 0) {
      logger.warn('未检测到在线的 Android 设备或模拟器！');
      console.log('\n  故障排查建议:');
      console.log('  1. 若使用 MuMu 模拟器，请启动模拟器并在终端运行:');
      console.log(`     & "${adbPath}" connect 127.0.0.1:16384`);
      console.log('  2. 若使用 Android Studio AVD，请先在 Device Manager 中启动虚拟机；');
      console.log('  3. 若使用真实手机，请使用 USB 数据线连接，并在手机设置中开启 "USB 调试" 模式。');
      return;
    }

    const targetDevice = options.device || devices[0].id;
    logger.info(`目标设备: [${targetDevice}]`);

    const skipBuild = options['skip-build'] || options.skipBuild;
    const apkPath = path.join(root, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');

    // 1. 编译流程
    if (!skipBuild) {
      console.log(`\n${logger.colors.cyan}[1/4]${logger.colors.reset} 预处理前端资源与离线 SDK...`);
      try {
        const prepareScript = path.join(root, 'scripts', 'prepare-build.js');
        if (fs.existsSync(prepareScript)) {
          execSync(`node "${prepareScript}" --mode local`, { cwd: root, stdio: 'inherit' });
        }
      } catch (e) {
        logger.warn(`资源预处理警告: ${e.message}，继续执行 Gradle 编译...`);
      }

      console.log(`\n${logger.colors.cyan}[2/4]${logger.colors.reset} 编译 Android Debug 安装包 (assembleDebug)...`);
      const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
      try {
        execSync(`${gradlew} assembleDebug`, { cwd: root, stdio: 'inherit' });
      } catch (e) {
        logger.error(`Gradle 编译失败: ${e.message}`);
        return;
      }
    } else {
      console.log(`\n${logger.colors.yellow}[跳过构建]${logger.colors.reset} 直接复用现有 APK 安装包: ${apkPath}`);
    }

    if (!fs.existsSync(apkPath)) {
      logger.error(`找不到编译生成的 APK: ${apkPath}`);
      return;
    }

    // 2. 安装 APK
    console.log(`\n${logger.colors.cyan}[3/4]${logger.colors.reset} 正在安装 APK 至设备 [${targetDevice}]...`);
    try {
      execSync(`"${adbPath}" -s ${targetDevice} install -r -d "${apkPath}"`, { stdio: 'inherit' });
      logger.success('APK 安装成功！');
    } catch (e) {
      logger.error(`APK 安装失败: ${e.message}`);
      return;
    }

    // 3. 启动应用
    console.log(`\n${logger.colors.cyan}[4/4]${logger.colors.reset} 正在拉起主 Activity (com.example.cookiebrowser)...`);
    try {
      execSync(`"${adbPath}" -s ${targetDevice} shell am start -n com.example.cookiebrowser/.MainActivity`, { stdio: 'inherit' });
      logger.success('🚀 应用已在设备上成功启动！');

      // 4. 自动转发 DevTools 调试端口
      try {
        execSync(`"${adbPath}" -s ${targetDevice} forward tcp:9222 localabstract:webview_devtools_remote`, { stdio: 'pipe' });
        console.log(`\n${logger.colors.green}✔ DevTools 调试通道就绪:${logger.colors.reset}`);
        console.log(`  打开桌面 Chrome 浏览器访问: ${logger.colors.cyan}chrome://inspect${logger.colors.reset} 即可调试网页与查看控制台日志。`);
      } catch (_) {}

      // 如果带了 --log 参数，流式展示 Logcat
      if (options.log) {
        console.log(`\n${logger.colors.bold}📜 正在监听应用运行日志 (Logcat)... 按 Ctrl+C 退出${logger.colors.reset}`);
        const logcat = spawn(adbPath, ['-s', targetDevice, 'logcat', '-v', 'time', 'MobileElectron*:V', 'CookieBrowser*:V', '*:S'], {
          stdio: 'inherit'
        });
        process.on('SIGINT', () => {
          logcat.kill();
          process.exit(0);
        });
      }

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
