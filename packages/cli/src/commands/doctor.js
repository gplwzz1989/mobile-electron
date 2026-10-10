// @mobile-electron/cli - Doctor Command

const os = require('os');
const logger = require('../utils/logger');
const { checkNode, checkNpm, checkJdk, checkAndroidSdk, checkIos } = require('../utils/env');

function doctorCommand() {
  logger.banner();
  console.log(`${logger.colors.bold}🔍 正在执行 Mobile Electron 开发环境深度体检...${logger.colors.reset}\n`);

  const results = [];

  // 1. OS & Node
  console.log(`${logger.colors.bold}[1/5] 宿主操作系统与基础运行环境:${logger.colors.reset}`);
  const osInfo = `${os.type()} ${os.release()} (${os.arch()})`;
  console.log(`  ✔ 操作系统:      ${osInfo}`);

  const nodeRes = checkNode();
  console.log(`  ${nodeRes.ok ? '✔' : '✖'} Node.js:       ${nodeRes.details}`);
  results.push(nodeRes.ok);

  const npmRes = checkNpm();
  console.log(`  ${npmRes.ok ? '✔' : '✖'} npm:           ${npmRes.details}`);
  results.push(npmRes.ok);

  // 2. JDK
  console.log(`\n${logger.colors.bold}[2/5] Java 开发环境 (Android 构建必备):${logger.colors.reset}`);
  const jdkRes = checkJdk();
  if (jdkRes.ok) {
    console.log(`  ✔ JDK:           ${jdkRes.version}`);
    if (jdkRes.javaHome !== '未设置 JAVA_HOME 环境变量') {
      console.log(`    JAVA_HOME:     ${jdkRes.javaHome}`);
    }
  } else {
    console.log(`  ✖ JDK:           未检测到 JDK 编译器 (javac)`);
    console.log(`    建议: 请安装 OpenJDK 17 或 11，并配置 JAVA_HOME 环境变量`);
  }
  results.push(jdkRes.ok);

  // 3. Android SDK
  console.log(`\n${logger.colors.bold}[3/5] Android SDK 与 ADB 调试桥:${logger.colors.reset}`);
  const androidRes = checkAndroidSdk();
  if (androidRes.ok) {
    console.log(`  ✔ Android SDK:   ${androidRes.sdkRoot}`);
    console.log(`  ✔ ADB 调试桥:    ${androidRes.adbVersion}`);
    if (androidRes.devices.length > 0) {
      console.log(`  ✔ 在线设备 (${androidRes.devices.length}):`);
      androidRes.devices.forEach(d => console.log(`      - [${d.id}] 状态: ${d.state}`));
    } else {
      console.log(`  ⚠ 在线设备:      暂无已连接设备或运行中的模拟器`);
    }
  } else {
    console.log(`  ✖ Android SDK:   未找到 Android SDK 或 platform-tools`);
    console.log(`    建议: 请配置 ANDROID_HOME 环境变量并将 platform-tools 添加至 PATH`);
  }
  results.push(androidRes.ok);

  // 4. iOS
  console.log(`\n${logger.colors.bold}[4/5] iOS 开发环境 (WebKit / Swift 必备):${logger.colors.reset}`);
  const iosRes = checkIos();
  if (iosRes.isMac) {
    if (iosRes.ok) {
      console.log(`  ✔ Xcode:         ${iosRes.version}`);
      console.log(`  ✔ iOS 模拟器:    ${iosRes.hasSimulators ? '已安装并就绪' : '需启动模拟器'}`);
    } else {
      console.log(`  ✖ Xcode:         ${iosRes.details}`);
    }
    results.push(iosRes.ok);
  } else {
    console.log(`  ℹ 跨端提示:      ${iosRes.details}`);
    console.log(`    说明: Windows 端可专注构建 Android 容器与 Web 前端；iOS 工程可通过 Git 仓库在 macOS 或 CI/CD (GitHub Actions) 上进行自动化打包。`);
  }

  // 5. 项目工程结构检测
  console.log(`\n${logger.colors.bold}[5/5] 当前工程核心结构自检:${logger.colors.reset}`);
  const fs = require('fs');
  const path = require('path');
  const root = process.cwd();

  const checks = [
    { label: 'Android 原生工程 (app/)', exists: fs.existsSync(path.join(root, 'app', 'src', 'main', 'java')) },
    { label: 'iOS 原生工程 (ios/)', exists: fs.existsSync(path.join(root, 'ios', 'MobileElectron')) },
    { label: '统一 SDK 源码 (sdk/)', exists: fs.existsSync(path.join(root, 'sdk', 'src', 'index.ts')) },
    { label: 'Web 前端控制台 (web/)', exists: fs.existsSync(path.join(root, 'web', 'src', 'main.ts')) },
    { label: '离线热更发布目录 (release/)', exists: fs.existsSync(path.join(root, 'release', 'bundles')) }
  ];

  checks.forEach(c => {
    console.log(`  ${c.exists ? '✔' : '⚠'} ${c.label}: ${c.exists ? '已就绪' : '未检测到'}`);
  });

  // Summary
  console.log('\n' + '='.repeat(60));
  const passCount = results.filter(Boolean).length;
  if (passCount >= 3) {
    logger.success(`环境体检完成！Mobile Electron 开发环境整体良好 (${passCount}/${results.length} 项就绪)。`);
  } else {
    logger.warn(`环境体检发现部分缺失项 (${passCount}/${results.length} 项就绪)，请根据上方建议完善配置。`);
  }
}

module.exports = doctorCommand;
