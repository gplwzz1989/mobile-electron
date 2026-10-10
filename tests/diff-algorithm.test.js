// tests/diff-algorithm.test.js - Incremental Diff Update Logic Tests

const assert = require('assert');

function computeDiff(localFilesMap, remoteFilesList, fullPackageSize) {
  if (!localFilesMap || Object.keys(localFilesMap).length === 0) {
    return {
      canUseDiff: false,
      reason: 'No local manifest found'
    };
  }

  const changedFiles = [];
  const reusableFiles = [];
  let diffSize = 0;

  for (const item of remoteFilesList) {
    const localHash = localFilesMap[item.path];
    if (localHash && localHash === item.hash) {
      reusableFiles.push(item);
    } else {
      changedFiles.push(item);
      diffSize += (item.size || 0);
    }
  }

  const totalRemoteFiles = remoteFilesList.length;
  // 必须至少能复用 1 个文件，且变动文件不能是全部
  const canUseDiff = changedFiles.length > 0 && reusableFiles.length > 0;
  const savedPercent = fullPackageSize > 0 
    ? Math.max(0, Math.round((1 - diffSize / fullPackageSize) * 100))
    : 0;

  return {
    canUseDiff,
    changedCount: changedFiles.length,
    reusableCount: reusableFiles.length,
    diffSize,
    savedPercent,
    changedFiles,
    reusableFiles
  };
}

function testDiffAlgorithm() {
  console.log('  ▸ 测试增量差分热更新计算算法 (Diff Engine)...');

  const localFiles = {
    'index.html': 'hash_aaa',
    'assets/app.js': 'hash_bbb',
    'assets/style.css': 'hash_ccc',
    'mobile-electron-sdk.js': 'hash_ddd'
  };

  // 场景 1: 仅业务 JS 发生了更新 (修改 1 个文件，其余 3 个文件哈希一致)
  const remoteFiles1 = [
    { path: 'index.html', hash: 'hash_aaa', size: 2000 },
    { path: 'assets/app.js', hash: 'hash_bbb_MODIFIED', size: 50000 }, // changed!
    { path: 'assets/style.css', hash: 'hash_ccc', size: 10000 },
    { path: 'mobile-electron-sdk.js', hash: 'hash_ddd', size: 40000 }
  ];
  const fullPkgSize = 65000;

  const result1 = computeDiff(localFiles, remoteFiles1, fullPkgSize);
  assert.strictEqual(result1.canUseDiff, true, '应允许使用增量更新');
  assert.strictEqual(result1.changedCount, 1, '应有 1 个变更文件');
  assert.strictEqual(result1.reusableCount, 3, '应有 3 个可复用文件');
  assert.strictEqual(result1.diffSize, 50000);
  assert.strictEqual(result1.changedFiles[0].path, 'assets/app.js');
  assert.ok(result1.savedPercent > 0, '节省流量应大于 0%');

  // 场景 2: 全部文件都改变（无法差分，降级全量）
  const remoteFiles2 = [
    { path: 'index.html', hash: 'hash_new1', size: 2000 },
    { path: 'assets/app.js', hash: 'hash_new2', size: 50000 },
    { path: 'assets/style.css', hash: 'hash_new3', size: 10000 },
    { path: 'mobile-electron-sdk.js', hash: 'hash_new4', size: 40000 }
  ];
  const result2 = computeDiff(localFiles, remoteFiles2, fullPkgSize);
  assert.strictEqual(result2.canUseDiff, false, '所有文件改变时应降级为全量下载');

  // 场景 3: 本地无清单（全新安装或异常破坏）
  const result3 = computeDiff({}, remoteFiles1, fullPkgSize);
  assert.strictEqual(result3.canUseDiff, false, '本地无清单时必须降级为全量');
  assert.strictEqual(result3.reason, 'No local manifest found');

  console.log('    ✔ 增量差分计算逻辑与平滑降级机制 100% 通过');
}

module.exports = testDiffAlgorithm;
