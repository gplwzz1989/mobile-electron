import React, { useState, useEffect } from 'react';
import electron from '@mobile-electron/core';

export default function App() {
  const [logs, setLogs] = useState<string[]>(['应用初始化完成...']);
  const platform = electron.platform;
  const isNative = electron.isNativeContainer;

  const addLog = (msg: string) => {
    setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ${msg}`, ...prev]);
  };

  useEffect(() => {
    addLog(`运行环境: ${platform} (${isNative ? 'Native 容器' : 'Web Mock 仿真'})`);
  }, []);

  const handleToast = async () => {
    await electron.device.toast('你好，来自 React 18 + Mobile Electron！');
    addLog('触发原生 Toast');
  };

  const handleHaptics = async () => {
    await electron.haptics.impact('heavy');
    addLog('触发 Heavy 冲击触感');
  };

  const handleBiometrics = async () => {
    try {
      const avail = await electron.biometrics.isAvailable();
      if (!avail.available) {
        addLog('当前设备未配置或不支持生物识别');
        return;
      }
      await electron.biometrics.authenticate({
        title: '身份安全认证',
        description: '请验证面容或指纹以确认操作'
      });
      addLog('生物识别验证成功！');
      await electron.haptics.notification('success');
    } catch (err: any) {
      addLog(`认证失败: ${err.message}`);
      await electron.haptics.notification('error');
    }
  };

  const handleCookies = async () => {
    try {
      const cookies = await electron.cookie.getAll({ all: true });
      addLog(`提取底层全量 Cookie 成功: 共 ${cookies.length} 条`);
    } catch (err: any) {
      addLog(`读取失败: ${err.message}`);
    }
  };

  return (
    <div style={{ maxWidth: 600, margin: '0 auto', padding: 16 }}>
      <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: 24 }}>⚡</span>
          <h1 style={{ fontSize: 20, fontWeight: 700 }}>{{APP_TITLE}}</h1>
        </div>
        <span style={{
          fontSize: 12,
          padding: '3px 8px',
          borderRadius: 999,
          background: isNative ? '#16A34A' : 'var(--border)',
          color: isNative ? '#FFF' : 'var(--text-sub)'
        }}>
          {isNative ? 'Native' : 'Mock'}
        </span>
      </header>

      <main>
        <div style={{
          background: 'var(--card-bg)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 16,
          marginBottom: 16
        }}>
          <h3 style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-sub)', marginBottom: 12, textTransform: 'uppercase' }}>
            原生系统能力
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <button
              onClick={handleToast}
              style={{ padding: 12, borderRadius: 8, background: 'var(--primary)', color: '#FFF', fontWeight: 600, border: 'none', cursor: 'pointer' }}
            >
              原生 Toast
            </button>
            <button
              onClick={handleHaptics}
              style={{ padding: 12, borderRadius: 8, background: 'transparent', border: '1px solid var(--border)', color: 'var(--text)', fontWeight: 600, cursor: 'pointer' }}
            >
              触感反馈
            </button>
            <button
              onClick={handleBiometrics}
              style={{ padding: 12, borderRadius: 8, background: 'transparent', border: '1px solid var(--border)', color: 'var(--text)', fontWeight: 600, cursor: 'pointer' }}
            >
              生物识别
            </button>
            <button
              onClick={handleCookies}
              style={{ padding: 12, borderRadius: 8, background: 'transparent', border: '1px solid var(--border)', color: 'var(--text)', fontWeight: 600, cursor: 'pointer' }}
            >
              底层 Cookie
            </button>
          </div>
        </div>

        <div style={{
          background: 'var(--card-bg)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 16
        }}>
          <h3 style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-sub)', marginBottom: 12, textTransform: 'uppercase' }}>
            实时日志
          </h3>
          <div style={{
            background: 'var(--bg)',
            padding: 12,
            borderRadius: 8,
            fontFamily: 'monospace',
            fontSize: 12,
            maxHeight: 200,
            overflowY: 'auto',
            border: '1px solid var(--border)'
          }}>
            {logs.map((item, idx) => (
              <div key={idx} style={{ marginBottom: 4 }}>{item}</div>
            ))}
          </div>
        </div>
      </main>
    </div>
  );
}
