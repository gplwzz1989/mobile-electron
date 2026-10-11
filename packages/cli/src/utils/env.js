// @mobile-electron/cli - Environment & System Probing Utility

const { execSync, spawn } = require('child_process');
const os = require('os');
const fs = require('fs');
const path = require('path');

function runSync(command, options = {}) {
  try {
    return execSync(command, {
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
      timeout: 8000,
      ...options
    }).trim();
  } catch (err) {
    return null;
  }
}

function checkNode() {
  const version = process.version;
  const major = parseInt(version.replace(/^v/, '').split('.')[0], 10);
  return {
    name: 'Node.js',
    ok: major >= 16,
    version,
    details: major >= 16 ? `v${process.versions.node}` : `v${process.versions.node} (推荐 >= v16.0.0)`
  };
}

function checkNpm() {
  const out = runSync('npm --version');
  return {
    name: 'npm',
    ok: !!out,
    version: out || 'Not found',
    details: out ? `v${out}` : '未找到 npm'
  };
}

function checkJdk() {
  const javaHome = process.env.JAVA_HOME || '';
  let javacVer = runSync('javac -version');
  if (!javacVer && javaHome) {
    const javacBin = path.join(javaHome, 'bin', process.platform === 'win32' ? 'javac.exe' : 'javac');
    if (fs.existsSync(javacBin)) {
      javacVer = runSync(`"${javacBin}" -version`);
    }
  }

  let javaVer = runSync('java -version');
  // java -version writes to stderr in many JVM implementations
  if (!javaVer) {
    try {
      execSync('java -version', { stdio: 'pipe' });
    } catch (e) {
      if (e.stderr) javaVer = e.stderr.toString().trim();
    }
  }

  const ok = !!(javacVer || javaVer || javaHome);
  return {
    name: 'Java Development Kit (JDK)',
    ok,
    javaHome: javaHome || '未设置 JAVA_HOME 环境变量',
    version: javacVer || (javaVer ? javaVer.split('\n')[0] : '未知版本'),
    details: ok ? (javacVer || (javaVer ? javaVer.split('\n')[0] : '已检测到 JDK')) : '未检测到 JDK (需 JDK 11 或 17)'
  };
}

function checkAndroidSdk() {
  let sdkRoot = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '';
  let adbVer = runSync('adb version');
  let adbPath = 'adb';

  if (!adbVer) {
    const candidates = [
      sdkRoot ? path.join(sdkRoot, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : null,
      'C:\\Users\\Administrator\\.codex\\tools\\reverse\\platform-tools\\adb.exe',
      'D:\\Program Files\\Netease\\MuMu\\nx_device\\12.0\\shell\\adb.exe',
      path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk', 'platform-tools', 'adb.exe')
    ].filter(Boolean);

    for (const cand of candidates) {
      if (fs.existsSync(cand)) {
        adbPath = cand;
        adbVer = runSync(`"${cand}" version`);
        if (adbVer) {
          if (!sdkRoot) {
            sdkRoot = path.dirname(path.dirname(cand));
          }
          break;
        }
      }
    }
  }

  let devices = [];
  if (adbVer) {
    const devOut = runSync(`"${adbPath}" devices`);
    if (devOut) {
      devices = devOut.split('\n')
        .slice(1)
        .map(l => l.trim())
        .filter(l => l && !l.startsWith('*') && l.includes('\t'))
        .map(l => {
          const [id, state] = l.split('\t');
          return { id, state };
        });
    }
  }

  const ok = !!(sdkRoot || adbVer);
  return {
    name: 'Android SDK & Platform-Tools',
    ok,
    sdkRoot: sdkRoot || '未显式配置 ANDROID_HOME',
    adbPath: adbVer ? adbPath : null,
    adbVersion: adbVer ? adbVer.split('\n')[0] : '未找到 adb',
    devices,
    details: ok ? `adb 就绪 (${devices.length} 台设备连接)` : '未配置 ANDROID_HOME 或 platform-tools'
  };
}

function checkIos() {
  const isMac = process.platform === 'darwin';
  if (!isMac) {
    return {
      name: 'iOS Development Environment',
      ok: false,
      isMac: false,
      details: '需要 macOS 系统与 Xcode 工具链 (Windows/Linux 不支持本地编译 iOS)'
    };
  }

  const xcodeVer = runSync('xcodebuild -version');
  const simctl = runSync('xcrun simctl list devices');
  return {
    name: 'iOS Development Environment',
    ok: !!xcodeVer,
    isMac: true,
    version: xcodeVer ? xcodeVer.split('\n')[0] : '未安装 Xcode',
    hasSimulators: !!(simctl && simctl.includes('Booted') || simctl && simctl.includes('iPhone')),
    details: xcodeVer ? `${xcodeVer.split('\n')[0]} (已配置)` : '需要安装 Xcode 命令行工具'
  };
}

module.exports = {
  runSync,
  checkNode,
  checkNpm,
  checkJdk,
  checkAndroidSdk,
  checkIos
};
