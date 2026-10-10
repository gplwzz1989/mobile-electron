/**
 * @file scripts/build-bundle.js
 * @description Mobile Electron 离线资源包 (.mep) 与更新清单 (update-manifest.json) 打包生成工具
 *
 * 用法范例：
 *   node scripts/build-bundle.js --version 2.1.0 --desc "优化性能并支持热更新"
 *   node scripts/build-bundle.js --skip-build --version 2.1.0
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execSync } = require('child_process');
const AdmZip = require('adm-zip');

const ROOT_DIR = path.resolve(__dirname, '..');
const SDK_DIR = path.join(ROOT_DIR, 'sdk');
const WEB_DIR = path.join(ROOT_DIR, 'web');
const WEB_DIST_DIR = path.join(WEB_DIR, 'dist');
const OUTPUT_DIR = path.join(ROOT_DIR, 'release', 'bundles');

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    version: '2.1.0',
    desc: 'Mobile Electron 离线包增量更新',
    out: OUTPUT_DIR,
    baseUrl: '',
    skipBuild: false,
    mandatory: false
  };

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--version' && args[i + 1]) {
      options.version = args[++i];
    } else if (args[i] === '--desc' && args[i + 1]) {
      options.desc = args[++i];
    } else if (args[i] === '--out' && args[i + 1]) {
      options.out = path.resolve(ROOT_DIR, args[++i]);
    } else if (args[i] === '--base-url' && args[i + 1]) {
      options.baseUrl = args[++i];
    } else if (args[i] === '--skip-build') {
      options.skipBuild = true;
    } else if (args[i] === '--mandatory') {
      options.mandatory = true;
    }
  }

  return options;
}

function computeFileHash(filePath) {
  const buffer = fs.readFileSync(filePath);
  return 'sha256:' + crypto.createHash('sha256').update(buffer).digest('hex');
}

function computeBufferHash(buffer) {
  return 'sha256:' + crypto.createHash('sha256').update(buffer).digest('hex');
}

function getAllFiles(dirPath, arrayOfFiles = []) {
  const files = fs.readdirSync(dirPath);

  files.forEach(file => {
    const fullPath = path.join(dirPath, file);
    if (fs.statSync(fullPath).isDirectory()) {
      arrayOfFiles = getAllFiles(fullPath, arrayOfFiles);
    } else {
      arrayOfFiles.push(fullPath);
    }
  });

  return arrayOfFiles;
}

async function main() {
  const opts = parseArgs();

  console.log('\n======================================================');
  console.log(`📦 Mobile Electron 离线包构建打包工具 (Target: v${opts.version})`);
  console.log('======================================================\n');

  if (!opts.skipBuild) {
    console.log('📦 [1/4] 构建 @mobile-electron/core SDK...');
    execSync('npm run build', { cwd: SDK_DIR, stdio: 'inherit' });

    console.log('\n🌐 [2/4] 构建 Web 界面资源 (Vite)...');
    execSync('npm run build', { cwd: WEB_DIR, stdio: 'inherit' });
  } else {
    console.log('⏩ 跳过重新编译前端 (--skip-build)');
  }

  if (!fs.existsSync(WEB_DIST_DIR)) {
    console.error('❌ 错误: 未找到 Web 产物目录:', WEB_DIST_DIR);
    process.exit(1);
  }

  // 保证 SDK UMD 单文件存在
  const sdkUmdSrc = path.join(SDK_DIR, 'dist', 'mobile-electron-sdk.umd.js');
  if (fs.existsSync(sdkUmdSrc)) {
    fs.copyFileSync(sdkUmdSrc, path.join(WEB_DIST_DIR, 'mobile-electron-sdk.js'));
  }

  console.log('\n🔍 [3/4] 扫描资源并计算各文件 SHA-256 哈希清单...');
  const allFiles = getAllFiles(WEB_DIST_DIR);
  const fileManifest = {};

  for (const filePath of allFiles) {
    const relPath = path.relative(WEB_DIST_DIR, filePath).replace(/\\/g, '/');
    if (relPath === 'manifest.json') continue; // 排除自身

    const hash = computeFileHash(filePath);
    const stat = fs.statSync(filePath);
    fileManifest[relPath] = {
      hash,
      size: stat.size
    };
    console.log(`  - ${relPath.padEnd(35)} (${(stat.size / 1024).toFixed(1)} KB) -> ${hash.slice(0, 18)}...`);
  }

  // 生成内部 manifest.json
  const versionParts = opts.version.split('.').map(Number);
  const buildNum = (versionParts[0] || 1) * 10000 + (versionParts[1] || 0) * 100 + (versionParts[2] || 0);

  const internalManifest = {
    appId: 'com.example.cookiebrowser',
    version: opts.version,
    buildNumber: buildNum,
    minAppVersion: '2.0.0',
    createdAt: new Date().toISOString(),
    description: opts.desc,
    fileCount: Object.keys(fileManifest).length,
    files: fileManifest
  };

  const manifestPath = path.join(WEB_DIST_DIR, 'manifest.json');
  fs.writeFileSync(manifestPath, JSON.stringify(internalManifest, null, 2), 'utf-8');

  console.log('\n🗜️  [4/4] 压缩生成 .mep (Mobile Electron Package) 离线包...');
  fs.mkdirSync(opts.out, { recursive: true });

  const zip = new AdmZip();
  // 添加 web/dist 下所有内容
  const updatedFiles = getAllFiles(WEB_DIST_DIR);
  for (const filePath of updatedFiles) {
    const relPath = path.relative(WEB_DIST_DIR, filePath).replace(/\\/g, '/');
    const zipDir = path.dirname(relPath) === '.' ? '' : path.dirname(relPath);
    zip.addLocalFile(filePath, zipDir);
  }

  const bundleFileName = `bundle-v${opts.version}.mep`;
  const bundleFilePath = path.join(opts.out, bundleFileName);
  zip.writeZip(bundleFilePath);

  const bundleStat = fs.statSync(bundleFilePath);
  const bundleHash = computeFileHash(bundleFilePath);

  // 生成供服务端分发的 update-manifest.json
  const packageDownloadUrl = opts.baseUrl
    ? (opts.baseUrl.endsWith('/') ? opts.baseUrl : opts.baseUrl + '/') + bundleFileName
    : bundleFileName;

  // 将文件完整复制到 release/bundles/files 供增量差分下载使用
  const filesOutputDir = path.join(opts.out, 'files');
  if (fs.existsSync(filesOutputDir)) {
    fs.rmSync(filesOutputDir, { recursive: true, force: true });
  }
  fs.mkdirSync(filesOutputDir, { recursive: true });
  for (const filePath of updatedFiles) {
    const relPath = path.relative(WEB_DIST_DIR, filePath);
    const targetFile = path.join(filesOutputDir, relPath);
    fs.mkdirSync(path.dirname(targetFile), { recursive: true });
    fs.copyFileSync(filePath, targetFile);
  }

  const updateManifest = {
    appId: 'com.example.cookiebrowser',
    latestVersion: opts.version,
    buildNumber: buildNum,
    minAppVersion: '2.0.0',
    packageUrl: packageDownloadUrl,
    packageHash: bundleHash,
    packageSize: bundleStat.size,
    description: opts.desc,
    mandatory: opts.mandatory,
    publishedAt: new Date().toISOString(),
    filesBaseUrl: 'files/',
    files: fileManifest
  };

  const updateManifestPath = path.join(opts.out, 'update-manifest.json');
  fs.writeFileSync(updateManifestPath, JSON.stringify(updateManifest, null, 2), 'utf-8');

  console.log('======================================================');
  console.log('🎉 离线资源包打包成功！');
  console.log(`📁 输出目录:   ${path.relative(ROOT_DIR, opts.out)}`);
  console.log(`📦 离线包文件: ${bundleFileName} (${(bundleStat.size / 1024).toFixed(1)} KB)`);
  console.log(`🔑 SHA-256:    ${bundleHash}`);
  console.log(`📋 更新清单:   ${path.relative(ROOT_DIR, updateManifestPath)}`);
  console.log('======================================================\n');
}

main().catch(err => {
  console.error('打包离线包失败:', err);
  process.exit(1);
});
