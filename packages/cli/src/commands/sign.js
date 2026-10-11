// @mobile-electron/cli - Sign Setup Command

const fs = require('fs');
const path = require('path');
const logger = require('../utils/logger');

function signSetupCommand() {
  logger.banner();
  console.log(`${logger.colors.bold}🔒 Mobile Electron 商业化数字签名配置助手${logger.colors.reset}\n`);

  const root = process.cwd();
  const exampleFile = path.join(root, 'key.properties.example');
  const targetFile = path.join(root, 'key.properties');

  if (!fs.existsSync(targetFile)) {
    if (fs.existsSync(exampleFile)) {
      fs.copyFileSync(exampleFile, targetFile);
      logger.success(`已为您自动创建签名配置文件: key.properties`);
    } else {
      const template = `# Mobile Electron Android 发布签名配置文件\nstorePassword=your_store_password\nkeyPassword=your_key_password\nkeyAlias=release-key\nstoreFile=release.jks\n`;
      fs.writeFileSync(targetFile, template, 'utf8');
      logger.success(`已生成 key.properties 模板文件`);
    }
  } else {
    logger.info(`检测到已存在 key.properties 配置文件: ${targetFile}`);
  }

  console.log(`\n${logger.colors.cyan}═══════════════════════════════════════════════════════════════${logger.colors.reset}`);
  console.log(`${logger.colors.bold}📱 Android 企业正式证书一键生成命令 (请直接复制执行):${logger.colors.reset}`);
  console.log(`${logger.colors.cyan}═══════════════════════════════════════════════════════════════${logger.colors.reset}`);
  console.log(`\n  ${logger.colors.yellow}keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release-key${logger.colors.reset}\n`);

  console.log(`${logger.colors.cyan}═══════════════════════════════════════════════════════════════${logger.colors.reset}`);
  console.log(`${logger.colors.bold}🚀 配置完成后，打包正式发布包命令:${logger.colors.reset}`);
  console.log(`${logger.colors.cyan}═══════════════════════════════════════════════════════════════${logger.colors.reset}`);
  console.log(`  • 打包国内各应用商店 Release APK:`);
  console.log(`    ${logger.colors.green}npx mobile-electron build --release${logger.colors.reset}  (或 ./gradlew assembleRelease)\n`);
  console.log(`  • 打包 Google Play 上架专用 AAB 安装包:`);
  console.log(`    ${logger.colors.green}npx mobile-electron build --release --aab${logger.colors.reset}  (或 ./gradlew bundleRelease)\n`);

  console.log(`${logger.colors.cyan}═══════════════════════════════════════════════════════════════${logger.colors.reset}`);
  console.log(`${logger.colors.bold}🍎 iOS 签名与上架提示:${logger.colors.reset}`);
  console.log(`${logger.colors.cyan}═══════════════════════════════════════════════════════════════${logger.colors.reset}`);
  console.log(`  1. 用 Xcode 打开 ios/MobileElectron.xcodeproj；`);
  console.log(`  2. 在 Signing & Capabilities 勾选 "Automatically manage signing"；`);
  console.log(`  3. 选中您的 Apple Developer Team 账号即可全自动完成正式证书与描述文件关联。\n`);
  console.log(`📖 完整详尽的发布实战指南请参阅: docs/SIGNING_AND_RELEASE_GUIDE.md\n`);
}

module.exports = signSetupCommand;
