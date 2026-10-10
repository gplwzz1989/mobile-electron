// @mobile-electron/cli - Dev Command

const { spawn } = require('child_process');
const os = require('os');
const path = require('path');
const logger = require('../utils/logger');

function getLocalIpAddresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        addresses.push(net.address);
      }
    }
  }
  return addresses;
}

function devCommand(args = {}) {
  logger.banner();
  const root = process.cwd();
  const ips = getLocalIpAddresses();
  const preferredIp = ips[0] || '127.0.0.1';

  logger.box('Mobile Electron 本地热重载开发服务器 (Dev Mode)', [
    '正在启动 Vite 前端实时热重载开发服务器...',
    '',
    `• 本机浏览器访问:    http://localhost:5173`,
    `• Android 模拟器访问: http://10.0.2.2:5173`,
    `• iOS 模拟器访问:     http://127.0.0.1:5173`,
    `• 真机 Wi-Fi 访问:    http://${preferredIp}:5173`,
    '',
    '提示: 可在 app-config.json 中将 defaultUrl 设为上述地址进行联调。'
  ]);

  const child = spawn(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['--prefix', 'web', 'run', 'dev'], {
    cwd: root,
    stdio: 'inherit'
  });

  child.on('close', (code) => {
    process.exit(code || 0);
  });
}

module.exports = devCommand;
