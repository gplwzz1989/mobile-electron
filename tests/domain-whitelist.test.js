// tests/domain-whitelist.test.js - Security Whitelist Matching Algorithm Tests

const assert = require('assert');

function normalizePattern(pattern) {
  if (!pattern) return '';
  let p = pattern.trim().toLowerCase();
  if (p.startsWith('http://')) p = p.substring(7);
  if (p.startsWith('https://')) p = p.substring(8);
  if (p.startsWith('file://')) return 'file';
  const slashIdx = p.indexOf('/');
  if (slashIdx !== -1) p = p.substring(0, slashIdx);
  const colonIdx = p.indexOf(':');
  if (colonIdx !== -1) p = p.substring(0, colonIdx);
  return p;
}

function isDomainAllowed(rawUrl, whitelist) {
  if (!rawUrl) return false;
  if (!whitelist || whitelist.length === 0) return false;

  let urlObj;
  try {
    urlObj = new URL(rawUrl);
  } catch {
    if (rawUrl.startsWith('file://')) {
      return whitelist.some(r => r === '*' || r.startsWith('file'));
    }
    return false;
  }

  if (urlObj.protocol === 'file:') {
    return whitelist.some(r => r === '*' || r.startsWith('file'));
  }

  const host = urlObj.hostname.toLowerCase();

  for (const rawRule of whitelist) {
    if (rawRule === '*' || rawRule === '*.*') return true;

    const rule = normalizePattern(rawRule);
    if (!rule) continue;

    if (rule === '*' || rule === '*.*') return true;
    if (rule === host) return true;
    if (rule.startsWith('*.')) {
      const rootDomain = rule.substring(2);
      if (host === rootDomain || host.endsWith('.' + rootDomain)) {
        return true;
      }
    }
  }

  return false;
}

function testDomainWhitelist() {
  console.log('  ▸ 测试安全沙箱动态白名单匹配规则 (Domain Whitelist)...');

  const whitelist = [
    'file://*',
    'https://*.douyin.com',
    'https://*.amemv.com',
    'http://127.0.0.1:*',
    'http://localhost:*'
  ];

  // 应该允许的 URL
  assert.strictEqual(isDomainAllowed('file:///dist/index.html', whitelist), true);
  assert.strictEqual(isDomainAllowed('https://creator.douyin.com/creator-micro/home', whitelist), true);
  assert.strictEqual(isDomainAllowed('https://api.amemv.com/v1/feed', whitelist), true);
  assert.strictEqual(isDomainAllowed('http://127.0.0.1:8088/manifest.json', whitelist), true);
  assert.strictEqual(isDomainAllowed('http://localhost:5173', whitelist), true);

  // 应该被阻断拦截的非信任 URL
  assert.strictEqual(isDomainAllowed('https://evil-hacker.com/malicious.js', whitelist), false);
  assert.strictEqual(isDomainAllowed('https://fake-douyin.com', whitelist), false);
  assert.strictEqual(isDomainAllowed('http://192.168.1.100:8080', whitelist), false);
  assert.strictEqual(isDomainAllowed('', whitelist), false);

  console.log('    ✔ 安全域名白名单通配符过滤与防钓鱼鉴权 100% 通过');
}

module.exports = testDomainWhitelist;
