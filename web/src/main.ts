import electron, { Page, CookieDetail } from '@mobile-electron/core';

let currentActivePage: Page | null = null;
const createdPages: Map<string, Page> = new Map();

function log(msg: string, type: 'i' | 's' | 'e' = 'i') {
  const consoleLogs = document.getElementById('console-logs');
  if (!consoleLogs) return;

  const entry = document.createElement('div');
  entry.className = 'log-line';

  const now = new Date();
  const timeStr = `[${now.toTimeString().split(' ')[0]}.${String(now.getMilliseconds()).padStart(3, '0')}]`;

  entry.innerHTML = `<span class="log-t">${timeStr}</span><span class="log-${type}">${escapeHtml(msg)}</span>`;
  consoleLogs.appendChild(entry);
  consoleLogs.scrollTop = consoleLogs.scrollHeight;
}

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

let toastTimer: any = null;
function showToast(msg: string) {
  const hud = document.getElementById('toast-hud');
  if (!hud) return;
  hud.textContent = msg;
  hud.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    hud.classList.remove('show');
  }, 2200);
}

async function appToast(msg: string) {
  showToast(msg);
  try {
    await electron.device.toast(msg);
  } catch {}
}

function showResult(boxId: string, data: any) {
  const box = document.getElementById(boxId);
  if (!box) return;
  box.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  box.classList.add('show');
}

function initEnvironment() {
  const statusTag = document.getElementById('status-tag');
  const statusText = document.getElementById('status-text');

  const isNative = electron.isNativeContainer;
  const platform = (electron.platform || 'web').toLowerCase();

  if (statusTag && statusText) {
    if (isNative) {
      if (platform === 'android') {
        statusTag.className = 'status-tag online android';
        statusText.textContent = 'Android 原生';
      } else if (platform === 'ios') {
        statusTag.className = 'status-tag online ios';
        statusText.textContent = 'iOS 原生';
      } else {
        statusTag.className = 'status-tag online';
        statusText.textContent = `在线 (${platform})`;
      }
    } else {
      statusTag.className = 'status-tag';
      statusText.textContent = 'Web 仿真';
    }
  }

  log(`运行环境就绪 | 平台: ${platform} | 原生容器: ${isNative}`, isNative ? 's' : 'i');

  try {
    electron.on('network:responseCaptured', (evt: any) => {
      log(`响应捕获 [${evt.url}]: ${evt.status}`, 'i');
    });
  } catch (e) {}
}

function setupTheme() {
  const toggleBtn = document.getElementById('btn-toggle-theme');
  const savedTheme = localStorage.getItem('app-theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  if (savedTheme === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark');
  }

  toggleBtn?.addEventListener('click', () => {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    const next = isDark ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('app-theme', next);
    appToast(`已切换至${next === 'dark' ? '深色' : '浅色'}主题`);
  });
}

function setupTabs() {
  const tabBtns = document.querySelectorAll<HTMLButtonElement>('.tab-pill');
  const tabPanels = document.querySelectorAll<HTMLElement>('.tab-panel');

  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetTabId = btn.getAttribute('data-tab');
      if (!targetTabId) return;

      tabBtns.forEach(b => b.classList.remove('active'));
      tabPanels.forEach(p => p.classList.remove('active'));

      btn.classList.add('active');
      const targetPanel = document.getElementById(targetTabId);
      if (targetPanel) {
        targetPanel.classList.add('active');
      }
    });
  });
}

function setupQuickBar() {
  document.getElementById('btn-quick-reload')?.addEventListener('click', async () => {
    log('页面刷新...');
    try {
      await electron.window.reload();
      log('已触发刷新', 's');
    } catch (err: any) {
      log(`刷新失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-quick-back')?.addEventListener('click', async () => {
    try {
      const can = await electron.window.canGoBack();
      if (can) {
        await electron.window.goBack();
        log('已后退', 's');
      } else {
        log('无上一页可退', 'i');
        await appToast('已是第一页');
      }
    } catch (err: any) {
      log(`后退异常: ${err.message}`, 'e');
    }
  });

  let isFullscreen = false;
  document.getElementById('btn-toggle-fullscreen')?.addEventListener('click', async () => {
    isFullscreen = !isFullscreen;
    try {
      await electron.window.setFullscreen(isFullscreen);
      log(`全屏: ${isFullscreen}`, 's');
    } catch (err: any) {
      log(`全屏失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-quick-debug')?.addEventListener('click', async () => {
    try {
      await electron.debug.show();
      log('已调出框架调试中心', 's');
    } catch (err: any) {
      log(`调出调试中心失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-quick-devtools')?.addEventListener('click', async () => {
    try {
      const res = await electron.debug.openDevTools();
      log(`DevTools 开启结果: ${JSON.stringify(res)}`, 's');
      await appToast('已开启 DevTools');
    } catch (err: any) {
      log(`开启 DevTools 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-quick-toast')?.addEventListener('click', async () => {
    try {
      await appToast('Mobile Electron 极简控制台');
      log('Toast 弹出成功', 's');
    } catch (err: any) {
      log(`Toast 失败: ${err.message}`, 'e');
    }
  });
}

function setupAppAndWindow() {
  document.getElementById('btn-app-info')?.addEventListener('click', async () => {
    log('获取应用信息...');
    try {
      const info = await electron.app.getInfo();
      showResult('app-info-display', info);
      log(`成功: ${info.appName} v${info.version}`, 's');
    } catch (err: any) {
      showResult('app-info-display', err.message);
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-app-config')?.addEventListener('click', async () => {
    log('获取配置...');
    try {
      const cfg = await electron.app.getConfig();
      showResult('app-info-display', cfg);
      log('获取配置成功', 's');
    } catch (err: any) {
      showResult('app-info-display', err.message);
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-app-exit')?.addEventListener('click', async () => {
    const ok = await electron.dialog.confirm('确定退出应用吗？', '确认');
    if (ok) {
      log('退出应用...');
      await electron.app.exit();
    }
  });

  document.querySelectorAll<HTMLElement>('.color-dot').forEach(dot => {
    dot.addEventListener('click', async () => {
      const color = dot.getAttribute('data-color') || '#FFFFFF';
      const dark = dot.getAttribute('data-dark') === 'true';
      const colorInput = document.getElementById('input-status-color') as HTMLInputElement;
      if (colorInput) colorInput.value = color;

      try {
        await electron.window.setStatusBar({ color, darkIcons: dark, immersive: true });
        log(`状态栏已设为: ${color}`, 's');
      } catch (err: any) {
        log(`设置状态栏失败: ${err.message}`, 'e');
      }
    });
  });

  document.getElementById('btn-set-status-bar')?.addEventListener('click', async () => {
    const colorInput = document.getElementById('input-status-color') as HTMLInputElement;
    const color = colorInput?.value?.trim() || '#FFFFFF';
    try {
      await electron.window.setStatusBar({ color, darkIcons: true, immersive: true });
      log(`状态栏已设为: ${color}`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fullscreen-on')?.addEventListener('click', async () => {
    await electron.window.setFullscreen(true);
    log('已进入全屏', 's');
  });

  document.getElementById('btn-fullscreen-off')?.addEventListener('click', async () => {
    await electron.window.setFullscreen(false);
    log('已退出全屏', 's');
  });

  document.getElementById('btn-toolbar-show')?.addEventListener('click', async () => {
    await electron.window.setDebugToolbarVisible(true);
    log('已显示原生底栏', 's');
  });

  document.getElementById('btn-toolbar-hide')?.addEventListener('click', async () => {
    await electron.window.setDebugToolbarVisible(false);
    log('已隐藏原生底栏', 's');
  });

  document.getElementById('btn-window-load-url')?.addEventListener('click', async () => {
    const input = document.getElementById('input-window-url') as HTMLInputElement;
    const url = input?.value?.trim();
    if (!url) return;
    log(`加载 URL: ${url}`);
    try {
      await electron.window.loadUrl(url);
      log(`导航成功: ${url}`, 's');
    } catch (err: any) {
      log(`导航失败: ${err.message}`, 'e');
    }
  });
}

function setupCookie() {
  let lastLoadedCookies: CookieDetail[] = [];

  function renderCookieTable(cookies: CookieDetail[]) {
    lastLoadedCookies = cookies;
    const tbody = document.querySelector('#table-cookies tbody');
    const counter = document.getElementById('cookie-counter');
    if (!tbody) return;

    if (counter) counter.textContent = `${cookies.length} 项`;

    if (!cookies || cookies.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:#94a3b8;">无 Cookie 记录</td></tr>';
      return;
    }

    tbody.innerHTML = cookies.map(c => `
      <tr>
        <td style="font-weight:600; color:#2563eb;">${escapeHtml(c.name)}</td>
        <td title="${escapeHtml(c.value)}" style="max-width:120px; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(c.value)}</td>
        <td>${escapeHtml(c.domain || '-')}</td>
        <td><span style="color:${c.httpOnly ? '#10b981' : '#94a3b8'};">${c.httpOnly ? '✔' : '✕'}</span></td>
      </tr>
    `).join('');
  }

  document.getElementById('btn-cookie-get-all')?.addEventListener('click', async () => {
    log('全量读取 Cookie...');
    try {
      const list = await electron.cookie.getAll();
      renderCookieTable(list);
      log(`提取成功: ${list.length} 条 (含HttpOnly)`, 's');
    } catch (err: any) {
      log(`提取失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-cookie-get-current')?.addEventListener('click', async () => {
    log('提取当前页面 Cookie...');
    try {
      const list = await electron.cookie.getDetails();
      renderCookieTable(list);
      log(`当前页面获取: ${list.length} 条`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-cookie-clear')?.addEventListener('click', async () => {
    const ok = await electron.dialog.confirm('确定清空所有 Cookie 吗？', '提示');
    if (!ok) return;
    try {
      await electron.cookie.clear();
      renderCookieTable([]);
      log('Cookie 已清空', 's');
      await appToast('Cookie 已清空');
    } catch (err: any) {
      log(`清空失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-cookie-read-url')?.addEventListener('click', async () => {
    const input = document.getElementById('input-cookie-read-url') as HTMLInputElement;
    const url = input?.value?.trim() || 'https://creator.douyin.com';
    log(`读取 ${url} Cookie...`);
    try {
      const str = await electron.cookie.get(url);
      log(`Cookie 内容: ${str ? str.slice(0, 60) + '...' : '(空)'}`, 's');
      await electron.dialog.alert(str || '(无 Cookie)', `${url}`);
    } catch (err: any) {
      log(`读取失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-cookie-inject')?.addEventListener('click', async () => {
    const targetUrl = (document.getElementById('input-cookie-target-url') as HTMLInputElement)?.value?.trim();
    const name = (document.getElementById('input-cookie-name') as HTMLInputElement)?.value?.trim();
    const value = (document.getElementById('input-cookie-value') as HTMLInputElement)?.value?.trim();
    const domain = (document.getElementById('input-cookie-domain') as HTMLInputElement)?.value?.trim();
    const httpOnly = (document.getElementById('chk-cookie-httponly') as HTMLInputElement)?.checked;
    const secure = (document.getElementById('chk-cookie-secure') as HTMLInputElement)?.checked;

    if (!targetUrl || !name || !value) {
      await appToast('请填写 URL、键名与键值');
      return;
    }

    const item: CookieDetail = {
      name,
      value,
      domain: domain || undefined,
      path: '/',
      httpOnly,
      secure
    };

    log(`写入 Cookie: [${name}] (httpOnly: ${httpOnly})...`);
    try {
      await electron.cookie.set(targetUrl, [item]);
      log(`Cookie [${name}] 写入成功`, 's');
      await appToast(`Cookie ${name} 注入成功`);
      const list = await electron.cookie.getAll();
      renderCookieTable(list);
    } catch (err: any) {
      log(`写入失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-cookie-inject-preset')?.addEventListener('click', async () => {
    const presetCookies: CookieDetail[] = [
      { name: 'sessionid', value: 'sec_sess_sample_token', domain: '.douyin.com', path: '/', httpOnly: true, secure: true },
      { name: 'passport_csrf_token', value: 'csrf_mock_7788', domain: '.douyin.com', path: '/', httpOnly: false, secure: true }
    ];

    log('注入测试预设 Cookie...');
    try {
      await electron.cookie.set('https://creator.douyin.com', presetCookies);
      log('预设注入成功', 's');
      await appToast('测试 Cookie 写入成功');
      const list = await electron.cookie.getAll();
      renderCookieTable(list);
    } catch (err: any) {
      log(`注入失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('input-filter-cookie')?.addEventListener('input', (e) => {
    const filter = (e.target as HTMLInputElement).value.toLowerCase();
    const filtered = lastLoadedCookies.filter(c =>
      c.name.toLowerCase().includes(filter) ||
      (c.domain && c.domain.toLowerCase().includes(filter)) ||
      c.value.toLowerCase().includes(filter)
    );
    renderCookieTable(filtered);
  });
}

function setupStorage() {
  document.getElementById('btn-kv-set')?.addEventListener('click', async () => {
    const key = (document.getElementById('input-kv-key') as HTMLInputElement)?.value?.trim();
    const val = (document.getElementById('input-kv-value') as HTMLInputElement)?.value?.trim();
    if (!key) return;

    try {
      let parsedVal: any = val;
      try {
        parsedVal = JSON.parse(val);
        await electron.storage.setObject(key, parsedVal);
      } catch {
        await electron.storage.set(key, val);
      }
      log(`保存 KV 成功: [${key}]`, 's');
      await appToast(`保存成功: ${key}`);
    } catch (err: any) {
      log(`保存失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-kv-get')?.addEventListener('click', async () => {
    const key = (document.getElementById('input-kv-key') as HTMLInputElement)?.value?.trim();
    if (!key) return;
    try {
      const res = await electron.storage.getObject(key);
      showResult('kv-result-box', res !== null ? res : '(未找到)');
      log(`读取 KV [${key}]: ${JSON.stringify(res)}`, 's');
    } catch (err: any) {
      log(`读取失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-kv-keys')?.addEventListener('click', async () => {
    try {
      const keys = await electron.storage.keys();
      showResult('kv-result-box', { allKeys: keys });
      log(`全部键名: ${keys.join(', ')}`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-kv-remove')?.addEventListener('click', async () => {
    const key = (document.getElementById('input-kv-key') as HTMLInputElement)?.value?.trim();
    if (!key) return;
    try {
      await electron.storage.remove(key);
      log(`已删除键: [${key}]`, 's');
      await appToast(`已删除: ${key}`);
    } catch (err: any) {
      log(`删除失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-kv-clear')?.addEventListener('click', async () => {
    try {
      await electron.storage.clear();
      showResult('kv-result-box', '已清空存储');
      log('存储已清空', 's');
    } catch (err: any) {
      log(`清空失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-ls-read')?.addEventListener('click', async () => {
    try {
      const data = await electron.storage.getLocalStorage();
      showResult('ls-result-box', data);
      log(`LocalStorage 读取成功: ${Object.keys(data).length} 项`, 's');
    } catch (err: any) {
      log(`读取失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-ls-write')?.addEventListener('click', async () => {
    const testData = { token: 'TOKEN_' + Date.now(), user: 'admin' };
    try {
      await electron.storage.setLocalStorage(testData);
      showResult('ls-result-box', testData);
      log('写入 LocalStorage 成功', 's');
      await appToast('LocalStorage 写入成功');
    } catch (err: any) {
      log(`写入失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-ls-clear')?.addEventListener('click', async () => {
    try {
      await electron.storage.clearLocalStorage();
      showResult('ls-result-box', 'LocalStorage 已清空');
      log('已清空 LocalStorage', 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-ss-read')?.addEventListener('click', async () => {
    try {
      const data = await electron.storage.getSessionStorage();
      showResult('ls-result-box', data);
      log('读取 SessionStorage 成功', 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-ss-write')?.addEventListener('click', async () => {
    try {
      await electron.storage.setSessionStorage({ sid: 'sess_' + Date.now() });
      log('写入 SessionStorage 成功', 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-ss-clear')?.addEventListener('click', async () => {
    try {
      await electron.storage.clearSessionStorage();
      log('清空 SessionStorage 成功', 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-dump-storage')?.addEventListener('click', async () => {
    log('导出全量快照...');
    try {
      const dump = await electron.storage.dumpStorage();
      showResult('dump-result-box', dump);
      log(`快照导出完成: ${dump.cookies?.length || 0} 条 Cookie`, 's');
    } catch (err: any) {
      log(`导出失败: ${err.message}`, 'e');
    }
  });
}

function setupFileSystem() {
  document.getElementById('btn-fs-paths')?.addEventListener('click', async () => {
    try {
      const paths = await electron.file.getPaths();
      showResult('fs-paths-display', paths);
      log(`沙箱路径: ${paths.documents}`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fs-list')?.addEventListener('click', async () => {
    const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
    try {
      const files = await electron.file.list('', dir);
      showResult('fs-paths-display', { dir, files });
      log(`文件数: ${files.length}`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fs-write')?.addEventListener('click', async () => {
    const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
    const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
    const content = (document.getElementById('input-fs-content') as HTMLTextAreaElement)?.value;
    if (!fileName) return;

    try {
      const fullPath = await electron.file.writeText(fileName, content, dir);
      showResult('fs-file-result', `写入成功:\n${fullPath}`);
      log(`文件写入: ${fullPath}`, 's');
      await appToast('写入成功');
    } catch (err: any) {
      log(`写入失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fs-read')?.addEventListener('click', async () => {
    const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
    const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
    if (!fileName) return;

    try {
      const text = await electron.file.readText(fileName, dir);
      showResult('fs-file-result', text);
      log(`读取完成 (${text.length} 字符)`, 's');
    } catch (err: any) {
      log(`读取失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fs-exists')?.addEventListener('click', async () => {
    const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
    const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
    if (!fileName) return;

    try {
      const res = await electron.file.exists(fileName, dir);
      showResult('fs-file-result', res);
      log(`检查结果: exists=${res.exists}`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fs-mkdir')?.addEventListener('click', async () => {
    const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
    const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
    if (!fileName) return;

    try {
      await electron.file.mkdir(fileName, dir);
      log('目录创建成功', 's');
      await appToast('目录创建完成');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fs-delete')?.addEventListener('click', async () => {
    const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
    const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
    if (!fileName) return;

    const ok = await electron.dialog.confirm(`删除 ${dir}/${fileName}？`, '确认');
    if (!ok) return;

    try {
      await electron.file.delete(fileName, dir);
      log(`已删除: ${fileName}`, 's');
      showResult('fs-file-result', `已删除: ${fileName}`);
    } catch (err: any) {
      log(`删除失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-fs-download')?.addEventListener('click', async () => {
    const url = (document.getElementById('input-fs-download-url') as HTMLInputElement)?.value?.trim();
    const dest = (document.getElementById('input-fs-download-name') as HTMLInputElement)?.value?.trim() || 'readme.txt';
    if (!url) return;

    log(`下载: ${url}...`);
    try {
      const res = await electron.file.download({ url, destinationPath: dest, directory: 'documents' });
      log(`下载完成 (${res.size} 字节)`, 's');
      await electron.dialog.alert(`下载完成: ${res.size} 字节\n${res.fullPath}`, '提示');
    } catch (err: any) {
      log(`下载失败: ${err.message}`, 'e');
    }
  });
}

function setupNetwork() {
  const presetSelect = document.getElementById('select-net-preset') as HTMLSelectElement;
  const urlInput = document.getElementById('input-net-url') as HTMLInputElement;

  presetSelect?.addEventListener('change', () => {
    if (urlInput) urlInput.value = presetSelect.value;
  });

  document.getElementById('btn-net-send')?.addEventListener('click', async () => {
    const method = (document.getElementById('select-net-method') as HTMLSelectElement)?.value as any || 'GET';
    const url = urlInput?.value?.trim();
    const bodyStr = (document.getElementById('input-net-body') as HTMLTextAreaElement)?.value?.trim();

    if (!url) return;

    log(`发起请求: [${method}] ${url}...`);
    try {
      let bodyData: any = undefined;
      if (method !== 'GET' && bodyStr) {
        try { bodyData = JSON.parse(bodyStr); } catch { bodyData = bodyStr; }
      }

      const res = await electron.network.fetchNative({
        url,
        method,
        headers: { 'Content-Type': 'application/json' },
        body: bodyData
      });

      showResult('net-result-box', { status: res.status, body: res.body });
      log(`响应 [${res.status}]`, 's');
    } catch (err: any) {
      showResult('net-result-box', err.message);
      log(`请求异常: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-net-listen')?.addEventListener('click', () => {
    const pattern = (document.getElementById('input-net-pattern') as HTMLInputElement)?.value?.trim() || '';
    log(`监听 Pattern: "${pattern}"...`);
    try {
      electron.network.onResponse(pattern, (res) => {
        const box = document.getElementById('net-listen-box');
        if (box) {
          box.textContent = `[${new Date().toLocaleTimeString()}] ${res.url} -> ${res.status}`;
          box.classList.add('show');
        }
        log(`捕获: [${res.url}] (${res.status})`, 's');
      });
      log('监听已就绪', 's');
      const box = document.getElementById('net-listen-box');
      if (box) {
        box.textContent = `正在监听关键词: "${pattern}"...`;
        box.classList.add('show');
      }
    } catch (err: any) {
      log(`监听失败: ${err.message}`, 'e');
    }
  });
}

function setupDeviceAndDialog() {
  document.getElementById('btn-device-toast')?.addEventListener('click', async () => {
    const text = (document.getElementById('input-toast-text') as HTMLInputElement)?.value?.trim() || 'Toast';
    try {
      await appToast(text);
      log(`Toast: "${text}"`, 's');
    } catch (err: any) {
      log(`Toast 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-clipboard-set')?.addEventListener('click', async () => {
    const text = (document.getElementById('input-clipboard-text') as HTMLInputElement)?.value || '';
    try {
      await electron.device.setClipboard(text);
      log(`写入剪贴板: "${text}"`, 's');
      await appToast('已写入剪贴板');
    } catch (err: any) {
      log(`剪贴板失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-clipboard-get')?.addEventListener('click', async () => {
    try {
      const clip = await electron.device.getClipboard();
      showResult('device-result-box', `剪贴板:\n"${clip}"`);
      log(`读取剪贴板: "${clip}"`, 's');
    } catch (err: any) {
      log(`读取失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-dialog-alert')?.addEventListener('click', async () => {
    log('触发 Alert...');
    await electron.dialog.alert('原生提示对话框测试。', '提示');
    showResult('dialog-result-box', 'Alert 已关闭');
    log('Alert 关闭', 's');
  });

  document.getElementById('btn-dialog-confirm')?.addEventListener('click', async () => {
    log('触发 Confirm...');
    const confirmed = await electron.dialog.confirm('是否确认此操作？', '询问');
    showResult('dialog-result-box', { confirmed });
    log(`Confirm 结果: ${confirmed}`, 's');
  });

  document.getElementById('btn-dialog-prompt')?.addEventListener('click', async () => {
    log('触发 Prompt...');
    const inputVal = await electron.dialog.prompt('输入测试内容：', 'default_val');
    showResult('dialog-result-box', { inputVal });
    log(`Prompt 结果: ${inputVal}`, 's');
  });
}

function setupBrowser() {
  async function refreshPageList() {
    try {
      const pages = await electron.browser.getAllPages();
      const profiles = await electron.browser.listProfiles();
      showResult('browser-pages-display', { total: pages.length, profiles, pages });
      log(`活跃页面: ${pages.length} 个`, 's');
    } catch (err: any) {
      log(`获取失败: ${err.message}`, 'e');
    }
  }

  document.getElementById('btn-browser-refresh')?.addEventListener('click', refreshPageList);
  document.getElementById('btn-browser-pages')?.addEventListener('click', refreshPageList);

  document.getElementById('btn-browser-profiles')?.addEventListener('click', async () => {
    try {
      const profiles = await electron.browser.listProfiles();
      showResult('browser-pages-display', { profiles });
      log(`Profiles: ${profiles.join(', ')}`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-browser-douyin-login')?.addEventListener('click', async () => {
    log('启动抖音扫码监控...');
    try {
      const res = await electron.browser.startDouyinQrLogin();
      showResult('browser-pages-display', res);
      log(`启动结果: ${res.message}`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-new-page-create')?.addEventListener('click', async () => {
    const url = (document.getElementById('input-new-page-url') as HTMLInputElement)?.value?.trim() || 'https://m.baidu.com';
    const profile = (document.getElementById('input-new-page-profile') as HTMLInputElement)?.value?.trim() || 'default';
    const preset = (document.getElementById('select-new-page-preset') as HTMLSelectElement)?.value as any;
    const headless = (document.getElementById('select-new-page-headless') as HTMLSelectElement)?.value === 'true';

    log(`创建新页面 [${profile}] -> ${url}...`);
    try {
      const page = await electron.browser.newPage({ url, profile, headless, hardwarePreset: preset });
      currentActivePage = page;
      createdPages.set(page.pageId, page);

      log(`页面创建成功: ${page.pageId}`, 's');
      await appToast(`页面已创建: ${page.pageId}`);

      const opsCard = document.getElementById('active-page-ops-card');
      const pageIdSpan = document.getElementById('current-op-page-id');
      if (opsCard) opsCard.classList.remove('hidden');
      if (pageIdSpan) pageIdSpan.textContent = `${page.pageId} (${profile})`;

      await refreshPageList();
    } catch (err: any) {
      log(`创建失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-front')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    try {
      await currentActivePage.bringToFront();
      log(`切到前台: [${currentActivePage.pageId}]`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-back')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    try {
      await currentActivePage.sendToBack();
      log(`退回后台: [${currentActivePage.pageId}]`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-get-cookies')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    try {
      const cookies = await currentActivePage.getAllCookies();
      showResult('page-op-result', { pageId: currentActivePage.pageId, cookies });
      log(`提取 Cookie: ${cookies.length} 条`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-get-ls')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    try {
      const ls = await currentActivePage.getLocalStorage();
      showResult('page-op-result', { pageId: currentActivePage.pageId, localStorage: ls });
      log(`提取 LocalStorage 成功`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-dump')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    try {
      const dump = await currentActivePage.dumpStorage();
      showResult('page-op-result', dump);
      log(`导出快照成功`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-hw')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    try {
      const hw = await currentActivePage.getHardware();
      showResult('page-op-result', hw);
      log(`指纹: ${hw.cpuCores}核, ${hw.deviceMemory}GB`, 's');
    } catch (err: any) {
      log(`失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-eval')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    const script = (document.getElementById('input-page-eval') as HTMLInputElement)?.value?.trim() || 'document.title';
    try {
      const result = await currentActivePage.evaluate(script);
      showResult('page-op-result', { script, result });
      log(`JS 执行: ${JSON.stringify(result)}`, 's');
    } catch (err: any) {
      log(`执行失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-page-close')?.addEventListener('click', async () => {
    if (!currentActivePage) return;
    try {
      const pageId = currentActivePage.pageId;
      await currentActivePage.close();
      createdPages.delete(pageId);
      currentActivePage = null;
      document.getElementById('active-page-ops-card')?.classList.add('hidden');
      log(`已销毁: [${pageId}]`, 's');
      await refreshPageList();
    } catch (err: any) {
      log(`关闭失败: ${err.message}`, 'e');
    }
  });
}

function setupTabBar() {
  // 注册全局 TabBar 点击监听 (核心要求 1)
  electron.tabBar.onTabClick((tab) => {
    log(`[原生 TabBar 点击] ID: ${tab.id} | Index: ${tab.index} | 标题: ${tab.title}`, 's');
    const display = document.getElementById('tabbar-click-event-display');
    if (display) {
      display.innerHTML = `<strong>⚡ 捕获到原生 TabBar 点击:</strong><br>` +
        `ID: <span style="color:#FBBF24;">${tab.id}</span> | 索引: ${tab.index} | 标题: ${tab.title}<br>` +
        `触发时间: ${new Date().toLocaleTimeString()}`;
    }
    appToast(`点击了底栏: ${tab.title}`);
  });

  // 预设 4 项配置
  document.getElementById('btn-tabbar-preset-4')?.addEventListener('click', async () => {
    try {
      const res = await electron.tabBar.setItems([
        { id: 'app', title: '工作台', icon: 'home' },
        { id: 'explore', title: '探索', icon: 'search' },
        { id: 'manage', title: '管理', icon: 'grid', badge: '3' },
        { id: 'my', title: '我的', icon: 'user' }
      ], {
        selectedId: 'app',
        backgroundColor: '#FFFFFF',
        color: '#64748B',
        selectedColor: '#4F46E5',
        visible: true
      });
      log(`注入预设 4项 TabBar 成功: count=${res.count}`, 's');
      await appToast('已显示 4 项原生 TabBar');
    } catch (err: any) {
      log(`注入 TabBar 失败: ${err.message}`, 'e');
    }
  });

  // 预设 5 项电商配置
  document.getElementById('btn-tabbar-preset-5')?.addEventListener('click', async () => {
    try {
      const res = await electron.tabBar.setItems([
        { id: 'mall_home', title: '首页', icon: 'home' },
        { id: 'category', title: '分类', icon: 'grid' },
        { id: 'discover', title: '发现', icon: 'search' },
        { id: 'cart', title: '购物车', icon: 'cart', badge: '99+' },
        { id: 'mine', title: '我的', icon: 'user' }
      ], {
        selectedId: 'mall_home',
        backgroundColor: '#F8FAFC',
        color: '#94A3B8',
        selectedColor: '#EC4899',
        visible: true
      });
      log(`注入预设 5项 TabBar 成功: count=${res.count}`, 's');
      await appToast('已显示 5 项原生 TabBar (含99+角标)');
    } catch (err: any) {
      log(`注入 TabBar 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-show')?.addEventListener('click', async () => {
    try {
      await electron.tabBar.show();
      log('原生 TabBar: 显示', 's');
    } catch (err: any) {
      log(`显示 TabBar 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-hide')?.addEventListener('click', async () => {
    try {
      await electron.tabBar.hide();
      log('原生 TabBar: 隐藏', 's');
    } catch (err: any) {
      log(`隐藏 TabBar 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-toggle')?.addEventListener('click', async () => {
    try {
      const vis = await electron.tabBar.toggle();
      log(`原生 TabBar 显隐切换: ${vis}`, 's');
    } catch (err: any) {
      log(`切换 TabBar 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-select-home')?.addEventListener('click', async () => {
    try {
      await electron.tabBar.setSelected(0);
      log('选中 TabBar 首页 (index=0)', 's');
    } catch (err: any) {
      log(`选中失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-select-cart')?.addEventListener('click', async () => {
    try {
      await electron.tabBar.setSelected('cart');
      log('选中 TabBar 购物车 (id=cart)', 's');
    } catch (err: any) {
      log(`选中失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-select-my')?.addEventListener('click', async () => {
    try {
      await electron.tabBar.setSelected('my');
      log('选中 TabBar 我的 (id=my)', 's');
    } catch (err: any) {
      log(`选中失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-badge-update')?.addEventListener('click', async () => {
    try {
      await electron.tabBar.setBadge('cart', '88');
      log("设置购物车角标为 '88'", 's');
    } catch (err: any) {
      log(`设置角标失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-badge-clear')?.addEventListener('click', async () => {
    try {
      await electron.tabBar.setBadge('cart', '');
      await electron.tabBar.setBadge('manage', '');
      log('已清空所有角标', 's');
    } catch (err: any) {
      log(`清空角标失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-tabbar-get-state')?.addEventListener('click', async () => {
    try {
      const state = await electron.tabBar.getState();
      showResult('tabbar-state-display', state);
      log(`获取 TabBar 状态: visible=${state.visible}, items=${state.items?.length}`, 's');
    } catch (err: any) {
      log(`获取状态失败: ${err.message}`, 'e');
    }
  });
}

function setupDebug() {
  document.getElementById('btn-debug-show')?.addEventListener('click', async () => {
    try {
      await electron.debug.show();
      log('调出框架调试中心成功', 's');
    } catch (err: any) {
      log(`调出失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-debug-hide')?.addEventListener('click', async () => {
    try {
      await electron.debug.hide();
      log('已关闭框架调试中心', 's');
    } catch (err: any) {
      log(`关闭失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-debug-toggle')?.addEventListener('click', async () => {
    try {
      const visible = await electron.debug.toggle();
      log(`切换框架调试中心: ${visible}`, 's');
    } catch (err: any) {
      log(`切换失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-debug-float-on')?.addEventListener('click', async () => {
    try {
      await electron.debug.setFloatingButtonVisible(true);
      log('已显示悬浮调试球', 's');
    } catch (err: any) {
      log(`显示悬浮球失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-debug-float-off')?.addEventListener('click', async () => {
    try {
      await electron.debug.setFloatingButtonVisible(false);
      log('已隐藏悬浮调试球', 's');
    } catch (err: any) {
      log(`隐藏悬浮球失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-debug-get-info')?.addEventListener('click', async () => {
    try {
      const info = await electron.debug.getInfo();
      showResult('debug-info-display', info);
      log(`框架调试信息: ${JSON.stringify(info)}`, 's');
    } catch (err: any) {
      log(`获取调试信息失败: ${err.message}`, 'e');
    }
  });

  // WebView DevTools 控制 (核心要求 2)
  document.getElementById('btn-devtools-open')?.addEventListener('click', async () => {
    try {
      const res = await electron.debug.openDevTools();
      showResult('devtools-result-display', res);
      log(`打开当前 WebView DevTools 成功: ${JSON.stringify(res)}`, 's');
      await appToast('已在当前 WebView 开启 DevTools');
    } catch (err: any) {
      log(`打开 DevTools 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-devtools-close')?.addEventListener('click', async () => {
    try {
      const res = await electron.debug.closeDevTools();
      showResult('devtools-result-display', res);
      log(`已关闭 DevTools: ${JSON.stringify(res)}`, 's');
      await appToast('已关闭 DevTools');
    } catch (err: any) {
      log(`关闭 DevTools 失败: ${err.message}`, 'e');
    }
  });

  document.getElementById('btn-devtools-toggle')?.addEventListener('click', async () => {
    try {
      const res = await electron.debug.toggleDevTools();
      showResult('devtools-result-display', res);
      log(`切换 DevTools 结果: ${JSON.stringify(res)}`, 's');
    } catch (err: any) {
      log(`切换 DevTools 失败: ${err.message}`, 'e');
    }
  });
}

function setupConsoleDrawer() {
  const drawer = document.querySelector('.drawer-footer');
  const toggleBtn = document.getElementById('btn-toggle-console');
  const clearBtn = document.getElementById('btn-clear-logs');
  const copyBtn = document.getElementById('btn-copy-logs');
  const consoleLogs = document.getElementById('console-logs');

  toggleBtn?.addEventListener('click', (e) => {
    if ((e.target as HTMLElement).tagName.toLowerCase() === 'button') return;
    drawer?.classList.toggle('collapsed');
  });

  clearBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (consoleLogs) {
      consoleLogs.innerHTML = '';
      log('日志已清空', 'i');
    }
  });

  copyBtn?.addEventListener('click', async (e) => {
    e.stopPropagation();
    if (consoleLogs) {
      const text = consoleLogs.innerText;
      try {
        await electron.device.setClipboard(text);
        await appToast('已复制日志');
      } catch {
        navigator.clipboard?.writeText(text);
      }
    }
  });
}

function initApp() {
  document.addEventListener('touchstart', () => {}, { passive: true });
  initEnvironment();
  setupTheme();
  setupTabs();
  setupQuickBar();
  setupAppAndWindow();
  setupTabBar();
  setupDebug();
  setupCookie();
  setupStorage();
  setupFileSystem();
  setupNetwork();
  setupDeviceAndDialog();
  setupBrowser();
  setupConsoleDrawer();
}

if (document.readyState === 'loading') {
  window.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}
