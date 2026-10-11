// @mobile-electron/cli - Build Command

const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');

function buildCommand(args = {}) {
  const mode = (args.mode || 'local').toLowerCase();
  const shouldBundle = !!args.bundle;
  const buildApk = !!args.apk;
  const root = process.cwd();

  logger.banner();
  console.log(`${logger.colors.bold}🚀 正在启动 Mobile Electron 一体化构建流程 [模式: ${mode.toUpperCase()}]${logger.colors.reset}\n`);

  try {
    // 1. 构建 SDK
    logger.step(1, shouldBundle ? 4 : 3, '构建 @mobile-electron/core 原生 SDK...');
    execSync('npm --prefix sdk run build', { cwd: root, stdio: 'inherit' });

    // 2. 构建 Web 前端
    logger.step(2, shouldBundle ? 4 : 3, '构建 Web 前端页面与生产静态资源 (Vite)...');
    execSync('npm --prefix web run build', { cwd: root, stdio: 'inherit' });

    // 3. 执行资源与配置同步
    logger.step(3, shouldBundle ? 4 : 3, `同步产物至原生工程并写入启动模式 [${mode}]...`);
    const prepScript = path.join(root, 'scripts', 'prepare-build.js');
    if (fs.existsSync(prepScript)) {
      execSync(`node "${prepScript}" --mode ${mode}`, { cwd: root, stdio: 'inherit' });
    }

    // 4. 可选：构建 .mep 离线热更差分包
    if (shouldBundle) {
      logger.step(4, 4, '扫描产物哈希并生成 .mep 离线热更包与增量清单 (update-manifest.json)...');
      const bundleScript = path.join(root, 'scripts', 'build-bundle.js');
      if (fs.existsSync(bundleScript)) {
        execSync(`node "${bundleScript}"`, { cwd: root, stdio: 'inherit' });
      }
    }

    // 5. 可选：触发 Gradle 构建 Android 安装包
    const isRelease = !!args.release;
    const isAab = !!args.aab;
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

    if (buildApk || isRelease || isAab) {
      const hasKeyProps = fs.existsSync(path.join(root, 'key.properties')) || fs.existsSync(path.join(root, 'app', 'key.properties'));
      if (isRelease || isAab) {
        if (hasKeyProps) {
          console.log(`\n${logger.colors.green}🔒 [Signing] 已检测到 key.properties 正式签名配置，将打包带正式数字签名的 Release 包...${logger.colors.reset}`);
        } else {
          console.log(`\n${logger.colors.yellow}ℹ️ [Signing] 未检测到 key.properties，将使用调试签名打包 Release。正式发布前请配置 key.properties。${logger.colors.reset}`);
        }

        if (isAab) {
          console.log(`\n${logger.colors.cyan}[Gradle]${logger.colors.reset} 正在编译 Google Play Release AAB Bundle...`);
          execSync(`${gradlew} bundleRelease`, { cwd: root, stdio: 'inherit' });
          logger.success('Release AAB 构建完成！(app/build/outputs/bundle/release/)');
        } else {
          console.log(`\n${logger.colors.cyan}[Gradle]${logger.colors.reset} 正在编译 Android Release APK...`);
          execSync(`${gradlew} assembleRelease`, { cwd: root, stdio: 'inherit' });
          logger.success('Android Release APK 构建完成！(app/build/outputs/apk/release/)');
        }
      } else {
        console.log(`\n${logger.colors.cyan}[Gradle]${logger.colors.reset} 正在编译 Android Debug APK 安装包...`);
        execSync(`${gradlew} assembleDebug`, { cwd: root, stdio: 'inherit' });
        logger.success('Android Debug APK 构建完成！(app/build/outputs/apk/debug/)');
      }
    }

    console.log('\n' + '='.repeat(60));
    logger.success(`构建流程全部完成！所有产物已同步对齐。`);
  } catch (err) {
    logger.error(`构建过程中断: ${err.message}`);
    process.exit(1);
  }
}

module.exports = buildCommand;
