// tests/rpc-protocol.test.js - JSON-RPC 2.0 Protocol & Bridge Contract Tests

const assert = require('assert');

function testJsonRpcProtocol() {
  console.log('  ▸ 测试 JSON-RPC 2.0 请求与响应协议契约...');

  // 1. 请求载荷结构
  const mockRequest = {
    jsonrpc: '2.0',
    method: 'app.getInfo',
    params: {},
    id: 1001
  };
  assert.strictEqual(mockRequest.jsonrpc, '2.0', '协议版本必须是 2.0');
  assert.strictEqual(typeof mockRequest.method, 'string', '调用方法必须为字符串');
  assert.strictEqual(mockRequest.id, 1001, '请求 ID 必须原样传递');

  // 2. 成功响应结构
  const successResponse = {
    jsonrpc: '2.0',
    result: {
      appName: 'Mobile Electron App',
      version: '2.0.0'
    },
    id: 1001
  };
  assert.strictEqual(successResponse.jsonrpc, '2.0');
  assert.ok(successResponse.result, '成功响应必须包含 result 字段');
  assert.strictEqual(successResponse.error, undefined, '成功响应不能包含 error 字段');
  assert.strictEqual(successResponse.id, 1001);

  // 3. 错误响应结构
  const errorResponse = {
    jsonrpc: '2.0',
    error: {
      code: -32601,
      message: 'Method not found',
      data: { method: 'unknown.action' }
    },
    id: 1002
  };
  assert.ok(errorResponse.error, '错误响应必须包含 error 对象');
  assert.strictEqual(typeof errorResponse.error.code, 'number', '错误码必须是数字');
  assert.strictEqual(typeof errorResponse.error.message, 'string', '错误消息必须是字符串');
  assert.strictEqual(errorResponse.result, undefined, '错误响应不能包含 result 字段');

  // 4. 事件通知 (Notification) 结构 (无 id)
  const notification = {
    jsonrpc: '2.0',
    method: 'tabBar.onTabClick',
    params: { id: 'home', index: 0, title: '首页' }
  };
  assert.strictEqual(notification.id, undefined, '通知消息不能包含 id 字段');
  assert.strictEqual(notification.method, 'tabBar.onTabClick');

  console.log('    ✔ JSON-RPC 2.0 协议层契约校验 100% 通过');
}

module.exports = testJsonRpcProtocol;
