/**
 * @file scripts/prepare-build.js
 * @description Mobile Electron 双模打包配置与资源构建同步工具
 * 
 * 用法范例：
 *   node scripts/prepare-build.js --mode local
 *   node scripts/prepare-build.js --mode online --url https://m.baidu.com
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ROOT_DIR = path.resolve(__dirname, '..');
const SDK_DIR = path.join(ROOT_DIR, 'sdk');
const WEB_DIR = path.join(ROOT_DIR, 'web');
const WEB_DIST_DIR = path.join(WEB_DIR, 'dist');

const ANDROID_CONFIG_PATH = path.join(ROOT_DIR, 'app', 'src', 'main', 'assets', 'app-config.json');
const ANDROID_ASSETS_DIST = path.join(ROOT_DIR, 'app', 'src', 'main', 'assets', 'dist');

const IOS_CONFIG_PATH = path.join(ROOT_DIR, 'ios', 'MobileElectron', 'app-config.json');
const IOS_BUNDLE_DIST = path.join(ROOT_DIR, 'ios', 'MobileElectron', 'dist');

// 解析命令行参数
function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    mode: 'local', // 'local' | 'online'
    url: '',
    skipBuild: false
  };

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--mode' && args[i + 1]) {
      options.mode = args[++i].toLowerCase();
    } else if (args[i] === '--url' && args[i + 1]) {
      options.url = args[++i];
    } else if (args[i] === '--skip-build') {
      options.skipBuild = true;
    }
  }

  return options;
}

// 递归复制目录
function copyDirSync(src, dest) {
  if (!fs.existsSync(src)) return;
  fs.mkdirSync(dest, { recursive: true });
  const entries = fs.readdirSync(src, { withFileTypes: true });

  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);

    if (entry.isDirectory()) {
      copyDirSync(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

// 清空目录内容（保留目录本身）
function emptyDirSync(dir) {
  if (!fs.existsSync(dir)) return;
  const entries = fs.readdirSync(dir);
  for (const file of entries) {
    const curPath = path.join(dir, file);
    fs.rmSync(curPath, { recursive: true, force: true });
  }
}

// 更新配置文件
function updateConfigFile(filePath, defaultUrl, extraWhitelistHost) {
  if (!fs.existsSync(filePath)) {
    console.warn(`[Warn] 配置文件不存在: ${filePath}`);
    return;
  }

  try {
    const raw = fs.readFileSync(filePath, 'utf-8');
    const config = JSON.parse(raw);

    if (!config.window) config.window = {};
    config.window.defaultUrl = defaultUrl;

    if (extraWhitelistHost && config.security && Array.isArray(config.security.whitelist)) {
      if (!config.security.whitelist.includes(extraWhitelistHost) && !config.security.whitelist.includes('*')) {
        config.security.whitelist.push(extraWhitelistHost);
      }
    }

    fs.writeFileSync(filePath, JSON.stringify(config, null, 2), 'utf-8');
    console.log(`✓ 成功更新配置 [${path.relative(ROOT_DIR, filePath)}] -> defaultUrl: ${defaultUrl}`);
  } catch (err) {
    console.error(`[Error] 无法更新配置文件 ${filePath}:`, err.message);
  }
}

async function main() {
  const { mode, url, skipBuild } = parseArgs();

  console.log('\n======================================================');
  console.log(`🚀 Mobile Electron 打包预处理启动 [模式: ${mode.toUpperCase()}]`);
  console.log('======================================================\n');

  if (mode === 'local') {
    // ----------------------------------------------------
    // 1. 本地离线包模式
    // ----------------------------------------------------
    if (!skipBuild) {
      console.log('📦 [1/4] 构建 @mobile-electron/core SDK...');
      execSync('npm run build', { cwd: SDK_DIR, stdio: 'inherit' });

      console.log('\n🌐 [2/4] 构建 Web 界面与静态离线包 (Vite)...');
      execSync('npm run build', { cwd: WEB_DIR, stdio: 'inherit' });
    } else {
      console.log('⏩ 跳过 Web 与 SDK 重新构建步骤 (--skip-build)');
    }

    if (!fs.existsSync(WEB_DIST_DIR)) {
      console.error('❌ 未找到 Web 构建产物目录:', WEB_DIST_DIR);
      process.exit(1);
    }

    console.log('\n📂 [3/4] 同步构建产物至 Android Assets & iOS Bundle...');

    // 同步至 Android assets/dist
    fs.mkdirSync(ANDROID_ASSETS_DIST, { recursive: true });
    emptyDirSync(ANDROID_ASSETS_DIST);
    copyDirSync(WEB_DIST_DIR, ANDROID_ASSETS_DIST);
    const sdkUmdSrc = path.join(SDK_DIR, 'dist', 'mobile-electron-sdk.umd.js');
    if (fs.existsSync(sdkUmdSrc)) {
      fs.copyFileSync(sdkUmdSrc, path.join(ANDROID_ASSETS_DIST, 'mobile-electron-sdk.js'));
    }
    console.log(`  -> 已同步至 Android: ${path.relative(ROOT_DIR, ANDROID_ASSETS_DIST)}`);

    // 同步至 iOS Bundle dist
    fs.mkdirSync(IOS_BUNDLE_DIST, { recursive: true });
    emptyDirSync(IOS_BUNDLE_DIST);
    copyDirSync(WEB_DIST_DIR, IOS_BUNDLE_DIST);
    if (fs.existsSync(sdkUmdSrc)) {
      fs.copyFileSync(sdkUmdSrc, path.join(IOS_BUNDLE_DIST, 'mobile-electron-sdk.js'));
    }
    console.log(`  -> 已同步至 iOS:     ${path.relative(ROOT_DIR, IOS_BUNDLE_DIST)}`);

    console.log('\n⚙️  [4/4] 写入本地模式默认启动配置...');
    const localUrl = 'file:///dist/index.html';
    updateConfigFile(ANDROID_CONFIG_PATH, localUrl);
    updateConfigFile(IOS_CONFIG_PATH, localUrl);

    console.log('\n✅ 本地离线包打包准备就绪！');
    console.log('   App 启动时将优先加载内置本地页面，并享有完整原生特权能力。\n');

  } else if (mode === 'online') {
    // ----------------------------------------------------
    // 2. 线上在线 URL 模式
    // ----------------------------------------------------
    const targetUrl = url || 'https://m.baidu.com';
    let host = '';
    try {
      const parsed = new URL(targetUrl);
      host = parsed.hostname;
    } catch {}

    console.log(`⚙️  [1/1] 写入在线模式目标 URL: ${targetUrl}`);
    updateConfigFile(ANDROID_CONFIG_PATH, targetUrl, host);
    updateConfigFile(IOS_CONFIG_PATH, targetUrl, host);

    console.log('\n✅ 在线 URL 模式配置就绪！');
    console.log(`   App 启动时将直接加载: ${targetUrl}\n`);
  } else {
    console.error(`❌ 未知模式: ${mode}。支持模式: 'local' | 'online'`);
    process.exit(1);
  }
}

main().catch(err => {
  console.error('打包预处理失败:', err);
  process.exit(1);
});
