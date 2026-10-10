/**
 * @file scripts/serve-update.js
 * @description 本地热更新调试微服务，用于模拟 CDN 分发 update-manifest.json 与 .mep 离线包
 *
 * 用法：
 *   node scripts/serve-update.js --port 8088
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const ROOT_DIR = path.resolve(__dirname, '..');
const BUNDLE_DIR = path.join(ROOT_DIR, 'release', 'bundles');

function parseArgs() {
  const args = process.argv.slice(2);
  let port = 8088;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--port' && args[i + 1]) {
      port = parseInt(args[++i], 10);
    }
  }
  return { port };
}

function getLocalIpAddresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        addresses.push(iface.address);
      }
    }
  }
  return addresses;
}

const MIME_TYPES = {
  '.json': 'application/json; charset=utf-8',
  '.mep': 'application/octet-stream',
  '.zip': 'application/zip',
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8'
};

const { port } = parseArgs();

const server = http.createServer((req, res) => {
  // 允许跨域
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  let reqPath = decodeURIComponent(parsedUrl.pathname);
  if (reqPath === '/' || reqPath === '') {
    reqPath = '/update-manifest.json';
  }

  const safePath = path.normalize(reqPath).replace(/^(\.\.[\/\\])+/, '');
  const filePath = path.join(BUNDLE_DIR, safePath);

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    console.warn(`[404 Not Found] ${req.method} ${req.url}`);
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'File not found', path: reqPath }));
    return;
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';
  const stat = fs.statSync(filePath);

  console.log(`[200 OK] ${req.method} ${req.url} (${(stat.size / 1024).toFixed(1)} KB)`);

  res.writeHead(200, {
    'Content-Type': contentType,
    'Content-Length': stat.size,
    'Cache-Control': 'no-cache, no-store, must-revalidate'
  });

  const readStream = fs.createReadStream(filePath);
  readStream.pipe(res);
});

server.listen(port, () => {
  console.log('\n======================================================');
  console.log(`🚀 Mobile Electron 热更新测试服务已启动 (Port: ${port})`);
  console.log(`📁 离线包分发根目录: ${BUNDLE_DIR}`);
  console.log('------------------------------------------------------');
  console.log('🔗 可用访问地址：');
  console.log(`   - 本地主机/iOS 模拟器:  http://127.0.0.1:${port}/update-manifest.json`);
  console.log(`   - Android 官方模拟器:   http://10.0.2.2:${port}/update-manifest.json`);
  const ips = getLocalIpAddresses();
  for (const ip of ips) {
    console.log(`   - 真机/局域网连接:      http://${ip}:${port}/update-manifest.json`);
  }
  console.log('======================================================\n');
});
