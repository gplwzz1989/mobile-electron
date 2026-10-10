import electron from '@mobile-electron/core';

const logBox = document.getElementById('log-box');
const envBadge = document.getElementById('env-badge');

function log(data: any) {
  if (!logBox) return;
  const str = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  logBox.textContent = `[${new Date().toLocaleTimeString()}]\n${str}`;
}

// 初始化运行环境标识
if (envBadge) {
  envBadge.textContent = `${electron.platform} (${electron.isNativeContainer ? 'Native' : 'Mock'})`;
  if (electron.isNativeContainer) {
    envBadge.style.background = '#16A34A';
    envBadge.style.color = '#FFFFFF';
  }
}

// 按钮交互绑定
document.getElementById('btn-toast')?.addEventListener('click', async () => {
  await electron.device.toast('你好，来自 Mobile Electron！');
  log('已触发原生 Toast 提醒');
});

document.getElementById('btn-haptic')?.addEventListener('click', async () => {
  await electron.haptics.impact('heavy');
  log('已触发 Heavy Impact 触感震动');
});

document.getElementById('btn-bio')?.addEventListener('click', async () => {
  try {
    const avail = await electron.biometrics.isAvailable();
    if (!avail.available) {
      log({ message: '当前设备不支持或未启用生物特征验证', details: avail });
      return;
    }
    const res = await electron.biometrics.authenticate({
      title: '身份安全确认',
      description: '请验证面容或指纹以继续'
    });
    log({ result: '认证成功', details: res });
    await electron.haptics.notification('success');
  } catch (err: any) {
    log({ error: '认证失败或用户取消', message: err.message });
    await electron.haptics.notification('error');
  }
});

document.getElementById('btn-sec')?.addEventListener('click', async () => {
  try {
    const key = 'user_token';
    const val = 'SECURE_SECRET_' + Math.random().toString(36).substring(2);
    await electron.secureStorage.setItem(key, val);
    const readBack = await electron.secureStorage.getItem(key);
    log({ action: '硬件KeyStore加密写入并解密读取', key, readBack });
    await electron.haptics.notification('success');
  } catch (err: any) {
    log({ error: err.message });
  }
});

document.getElementById('btn-scan')?.addEventListener('click', async () => {
  try {
    const res = await electron.camera.scanBarcode();
    log({ action: '条码扫描完成', result: res });
    await electron.haptics.notification('success');
  } catch (err: any) {
    log({ error: err.message });
  }
});

document.getElementById('btn-cookie')?.addEventListener('click', async () => {
  try {
    const cookies = await electron.cookie.getAll({ all: true });
    log({ totalCookies: cookies.length, sample: cookies.slice(0, 5) });
  } catch (err: any) {
    log({ error: err.message });
  }
});
