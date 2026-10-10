<script setup lang="ts">
import { ref, onMounted } from 'vue';
import electron from '@mobile-electron/core';

const platform = ref(electron.platform);
const isNative = ref(electron.isNativeContainer);
const logs = ref<string[]>(['应用初始化完成...']);

function log(msg: string) {
  logs.value.unshift(`[${new Date().toLocaleTimeString()}] ${msg}`);
}

async function triggerToast() {
  await electron.device.toast('你好，来自 Vue 3 + Mobile Electron！');
  log('触发原生 Toast');
}

async function triggerHaptics() {
  await electron.haptics.impact('heavy');
  log('触发震动触感 (Heavy Impact)');
}

async function triggerBiometrics() {
  try {
    const avail = await electron.biometrics.isAvailable();
    if (!avail.available) {
      log('当前设备未配置或不支持生物识别');
      return;
    }
    await electron.biometrics.authenticate({
      title: '身份安全认证',
      description: '请通过指纹或面容以确认操作'
    });
    log('生物识别认证成功！');
    await electron.haptics.notification('success');
  } catch (err: any) {
    log(`认证失败: ${err.message}`);
    await electron.haptics.notification('error');
  }
}

async function triggerSecureStorage() {
  try {
    const key = 'auth_secret';
    const val = 'TOKEN_' + Math.random().toString(36).substring(2);
    await electron.secureStorage.setItem(key, val);
    const read = await electron.secureStorage.getItem(key);
    log(`硬件KeyStore加密写入并解密成功: ${read}`);
    await electron.haptics.notification('success');
  } catch (err: any) {
    log(`加密存储失败: ${err.message}`);
  }
}

async function triggerScanBarcode() {
  try {
    const res = await electron.camera.scanBarcode();
    log(`扫码成功 [${res.format}]: ${res.text}`);
    await electron.haptics.notification('success');
  } catch (err: any) {
    log(`扫码取消或失败: ${err.message}`);
  }
}

async function getCookies() {
  try {
    const cookies = await electron.cookie.getAll({ all: true });
    log(`读取底层全量 Cookie 成功: 共 ${cookies.length} 条`);
  } catch (err: any) {
    log(`读取失败: ${err.message}`);
  }
}

onMounted(() => {
  log(`运行环境: ${platform.value} (${isNative.value ? 'Native 容器' : 'Web Mock 仿真'})`);
});
</script>

<template>
  <div class="container">
    <header class="header">
      <div class="brand">
        <span class="icon">⚡</span>
        <h1>{{APP_TITLE}}</h1>
      </div>
      <span class="badge" :class="{ native: isNative }">
        {{ isNative ? 'Native' : 'Mock' }}
      </span>
    </header>

    <main class="main">
      <div class="card">
        <h3>原生系统能力</h3>
        <div class="grid">
          <button class="btn btn-primary" @click="triggerToast">原生 Toast</button>
          <button class="btn btn-outline" @click="triggerHaptics">触感反馈</button>
          <button class="btn btn-outline" @click="triggerBiometrics">生物识别</button>
          <button class="btn btn-outline" @click="triggerSecureStorage">硬件加密存储</button>
          <button class="btn btn-outline" @click="triggerScanBarcode">扫码 (Camera)</button>
          <button class="btn btn-outline" @click="getCookies">底层 Cookie</button>
        </div>
      </div>

      <div class="card">
        <h3>实时日志</h3>
        <div class="log-box">
          <div v-for="(item, idx) in logs" :key="idx" class="log-item">
            {{ item }}
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.container {
  max-width: 600px;
  margin: 0 auto;
  padding: 16px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}
.icon {
  font-size: 24px;
}
h1 {
  font-size: 20px;
  font-weight: 700;
}
.badge {
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--border);
  color: var(--text-sub);
}
.badge.native {
  background: #16A34A;
  color: #FFF;
}
.card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
}
h3 {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-sub);
  margin-bottom: 12px;
  text-transform: uppercase;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.btn {
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: none;
}
.btn-primary {
  background: var(--primary);
  color: #FFF;
}
.btn-outline {
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text);
}
.log-box {
  background: var(--bg);
  padding: 12px;
  border-radius: 8px;
  font-family: monospace;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--border);
}
.log-item {
  margin-bottom: 4px;
}
</style>
