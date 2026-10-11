/**
 * @file pack-packages.js
 * @description 一键打包所有 NPM 子包为可离线分发的 .tgz 产物
 * 覆盖: @mobile-electron/core, @mobile-electron/cli, create-mobile-electron-app
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const outDir = path.join(root, 'release', 'npm');

if (!fs.existsSync(outDir)) {
  fs.mkdirSync(outDir, { recursive: true });
}

console.log('======================================================');
console.log('📦 Mobile Electron NPM 生态包自动化打包与归档启动');
console.log('======================================================\n');

// 1. 确保 SDK 先行编译
console.log('🔨 [1/4] 编译 @mobile-electron/core 最新产物...');
execSync('npm run build', { cwd: path.join(root, 'sdk'), stdio: 'inherit' });

const targets = [
  { name: '@mobile-electron/core', dir: path.join(root, 'sdk') },
  { name: '@mobile-electron/cli', dir: path.join(root, 'packages', 'cli') },
  { name: 'create-mobile-electron-app', dir: path.join(root, 'packages', 'create-mobile-electron-app') }
];

const packedFiles = [];

targets.forEach((target, index) => {
  console.log(`\n📦 [${index + 2}/4] 打包 ${target.name}...`);
  const output = execSync('npm pack', { cwd: target.dir, encoding: 'utf-8' }).trim();
  const tarballName = output.split('\n').filter(Boolean).pop().trim();
  const sourcePath = path.join(target.dir, tarballName);
  const destPath = path.join(outDir, tarballName);

  if (fs.existsSync(sourcePath)) {
    fs.copyFileSync(sourcePath, destPath);
    fs.unlinkSync(sourcePath);
    const stats = fs.statSync(destPath);
    const sizeKb = (stats.size / 1024).toFixed(1);
    packedFiles.push({ name: target.name, tarball: tarballName, size: `${sizeKb} KB`, path: destPath });
    console.log(`  ✔ 已导出至: release/npm/${tarballName} (${sizeKb} KB)`);
  }
});

console.log('\n======================================================');
console.log('🎉 全部 NPM 生态包打包归档完成！产物列表:');
console.log('======================================================');
packedFiles.forEach(f => {
  console.log(`  • ${f.name.padEnd(28)} -> ${f.tarball} (${f.size})`);
});
console.log('\n💡 离线测试安装方式:');
console.log(`  npm install ./release/npm/${packedFiles[0]?.tarball || 'mobile-electron-core-2.0.0.tgz'}\n`);
