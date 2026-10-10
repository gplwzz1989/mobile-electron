// tests/run-all.js - Mobile Electron Master Test Suite Runner

const testJsonRpcProtocol = require('./rpc-protocol.test');
const testSdkModules = require('./sdk-modules.test');
const testDiffAlgorithm = require('./diff-algorithm.test');
const testDomainWhitelist = require('./domain-whitelist.test');

async function runAllTests() {
  console.log('\n======================================================');
  console.log('🧪 Mobile Electron 全栈跨端核心套件自动化测试');
  console.log('======================================================\n');

  const startTime = Date.now();
  let passCount = 0;
  let totalCount = 4;

  try {
    // 1. JSON-RPC 协议测试
    testJsonRpcProtocol();
    passCount++;

    // 2. SDK 模块与 Mock 仿真测试
    await testSdkModules();
    passCount++;

    // 3. 增量差分热更算法测试
    testDiffAlgorithm();
    passCount++;

    // 4. 安全沙箱白名单规则测试
    testDomainWhitelist();
    passCount++;

    const duration = Date.now() - startTime;
    console.log('\n======================================================');
    console.log(`🎉 全部测试通过！通过率: 100% (${passCount}/${totalCount}) · 耗时: ${duration}ms`);
    console.log('======================================================\n');
    process.exit(0);
  } catch (err) {
    console.error('\n✖ 测试执行失败:', err);
    process.exit(1);
  }
}

runAllTests();
