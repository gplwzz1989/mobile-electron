import electron, { Page, CookieDetail } from '@mobile-electron/core';

// 当前处于选中操作的 Page 实例
let currentActivePage: Page | null = null;
const createdPages: Map<string, Page> = new Map();

// 日志记录函数
function log(msg: string, type: 'info' | 'success' | 'error' = 'info') {
    const consoleLogs = document.getElementById('console-logs');
    if (!consoleLogs) return;

    const entry = document.createElement('div');
    entry.className = `log-entry log-${type}`;

    const now = new Date();
    const timeStr = `[${now.toTimeString().split(' ')[0]}.${String(now.getMilliseconds()).padStart(3, '0')}]`;

    entry.innerHTML = `<span class="log-time">${timeStr}</span><span class="log-msg">${escapeHtml(msg)}</span>`;
    consoleLogs.appendChild(entry);
    consoleLogs.scrollTop = consoleLogs.scrollHeight;
}

function escapeHtml(str: string): string {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function showResult(boxId: string, data: any) {
    const box = document.getElementById(boxId);
    if (!box) return;
    box.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
    box.classList.add('visible');
}

// 初始化页面环境状态
function initEnvironment() {
    const badgeContainer = document.getElementById('badge-container');
    const badgePlatform = document.getElementById('badge-platform');

    const isNative = electron.isNativeContainer;
    const platform = electron.platform;

    if (badgeContainer) {
        if (isNative) {
            badgeContainer.className = 'badge badge-online';
            badgeContainer.textContent = `🟢 原生容器环境 (${platform.toUpperCase()})`;
        } else {
            badgeContainer.className = 'badge badge-mock';
            badgeContainer.textContent = `🟡 Web Mock 仿真环境`;
        }
    }

    if (badgePlatform) {
        badgePlatform.textContent = platform;
    }

    log(`🚀 运行环境就绪 | 平台: ${platform} | 是否原生容器: ${isNative}`, isNative ? 'success' : 'info');

    // 监听网络响应捕获全局事件
    try {
        electron.on('network:responseCaptured', (evt: any) => {
            log(`📡 捕获全局网络响应 [${evt.url}]: 状态码 ${evt.status}`, 'info');
        });
    } catch (e) {
        // ignore
    }
}

// 绑定选项卡切换
function setupTabs() {
    const tabBtns = document.querySelectorAll<HTMLButtonElement>('.tab-btn');
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

// 快捷操作栏
function setupQuickBar() {
    document.getElementById('btn-quick-reload')?.addEventListener('click', async () => {
        log('触发页面刷新 reload()...');
        try {
            await electron.window.reload();
            log('页面刷新指令已发送', 'success');
        } catch (err: any) {
            log(`刷新失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-quick-back')?.addEventListener('click', async () => {
        try {
            const can = await electron.window.canGoBack();
            if (can) {
                await electron.window.goBack();
                log('已后退上一历史页面', 'success');
            } else {
                log('当前页面无历史记录可后退', 'info');
                await electron.device.toast('当前已是第一页');
            }
        } catch (err: any) {
            log(`后退失败: ${err.message}`, 'error');
        }
    });

    let isFullscreen = false;
    document.getElementById('btn-toggle-fullscreen')?.addEventListener('click', async () => {
        isFullscreen = !isFullscreen;
        try {
            await electron.window.setFullscreen(isFullscreen);
            log(`切换全屏状态: ${isFullscreen}`, 'success');
        } catch (err: any) {
            log(`全屏切换失败: ${err.message}`, 'error');
        }
    });

    let isToolbarShown = false;
    document.getElementById('btn-toggle-toolbar')?.addEventListener('click', async () => {
        isToolbarShown = !isToolbarShown;
        try {
            await electron.window.setDebugToolbarVisible(isToolbarShown);
            log(`切换原生工具栏显隐: ${isToolbarShown}`, 'success');
        } catch (err: any) {
            log(`工具栏控制失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-quick-toast')?.addEventListener('click', async () => {
        try {
            await electron.device.toast('⚡ Hello Mobile Electron! SDK 调用成功！');
            log('Toast 弹出成功', 'success');
        } catch (err: any) {
            log(`Toast 失败: ${err.message}`, 'error');
        }
    });
}

// 模块 1: 应用与窗口 (App & Window)
function setupAppAndWindow() {
    // getInfo
    document.getElementById('btn-app-info')?.addEventListener('click', async () => {
        log('调用 electron.app.getInfo()...');
        try {
            const info = await electron.app.getInfo();
            showResult('app-info-display', info);
            log(`获取应用信息成功: ${info.appName} v${info.version}`, 'success');
        } catch (err: any) {
            showResult('app-info-display', `错误: ${err.message}`);
            log(`获取应用信息失败: ${err.message}`, 'error');
        }
    });

    // getConfig
    document.getElementById('btn-app-config')?.addEventListener('click', async () => {
        log('调用 electron.app.getConfig()...');
        try {
            const cfg = await electron.app.getConfig();
            showResult('app-info-display', cfg);
            log('获取 app-config.json 配置成功', 'success');
        } catch (err: any) {
            showResult('app-info-display', `错误: ${err.message}`);
            log(`获取配置失败: ${err.message}`, 'error');
        }
    });

    // exit
    document.getElementById('btn-app-exit')?.addEventListener('click', async () => {
        const ok = await electron.dialog.confirm('确定要退出应用程序吗？', '退出确认');
        if (ok) {
            log('调用 electron.app.exit()...');
            await electron.app.exit();
        }
    });

    // setStatusBar 色板快捷点击
    document.querySelectorAll<HTMLButtonElement>('.btn-color').forEach(btn => {
        btn.addEventListener('click', async () => {
            const color = btn.getAttribute('data-color') || '#FFFFFF';
            const dark = btn.getAttribute('data-dark') === 'true';
            const colorInput = document.getElementById('input-status-color') as HTMLInputElement;
            if (colorInput) colorInput.value = color;

            try {
                await electron.window.setStatusBar({
                    color,
                    darkIcons: dark,
                    immersive: true
                });
                log(`状态栏已应用颜色: ${color}, 深色图标: ${dark}`, 'success');
            } catch (err: any) {
                log(`设置状态栏失败: ${err.message}`, 'error');
            }
        });
    });

    // 自定义颜色应用
    document.getElementById('btn-set-status-bar')?.addEventListener('click', async () => {
        const colorInput = document.getElementById('input-status-color') as HTMLInputElement;
        const color = colorInput?.value?.trim() || '#FFFFFF';
        try {
            await electron.window.setStatusBar({
                color,
                darkIcons: true,
                immersive: true
            });
            log(`状态栏已应用颜色: ${color}`, 'success');
        } catch (err: any) {
            log(`设置状态栏失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-fullscreen-on')?.addEventListener('click', async () => {
        await electron.window.setFullscreen(true);
        log('已开启全屏模式', 'success');
    });

    document.getElementById('btn-fullscreen-off')?.addEventListener('click', async () => {
        await electron.window.setFullscreen(false);
        log('已退出全屏模式', 'success');
    });

    document.getElementById('btn-toolbar-show')?.addEventListener('click', async () => {
        await electron.window.setDebugToolbarVisible(true);
        log('已显示原生调试工具栏', 'success');
    });

    document.getElementById('btn-toolbar-hide')?.addEventListener('click', async () => {
        await electron.window.setDebugToolbarVisible(false);
        log('已隐藏原生调试工具栏', 'success');
    });

    // 加载 URL
    document.getElementById('btn-window-load-url')?.addEventListener('click', async () => {
        const input = document.getElementById('input-window-url') as HTMLInputElement;
        const url = input?.value?.trim();
        if (!url) return;
        log(`调用 electron.window.loadUrl("${url}")...`);
        try {
            await electron.window.loadUrl(url);
            log(`已导航至 ${url}`, 'success');
        } catch (err: any) {
            log(`加载 URL 失败: ${err.message}`, 'error');
        }
    });
}

// 模块 2: Cookie 核心中枢
function setupCookie() {
    let lastLoadedCookies: CookieDetail[] = [];

    function renderCookieTable(cookies: CookieDetail[]) {
        lastLoadedCookies = cookies;
        const tbody = document.querySelector('#table-cookies tbody');
        const counter = document.getElementById('cookie-counter');
        if (!tbody) return;

        if (counter) counter.textContent = `${cookies.length} 项`;

        if (!cookies || cookies.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">未找到 Cookie 记录</td></tr>';
            return;
        }

        tbody.innerHTML = cookies.map(c => `
            <tr>
                <td style="font-weight:600; color:#4f46e5;">${escapeHtml(c.name)}</td>
                <td title="${escapeHtml(c.value)}" style="max-width:140px; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(c.value)}</td>
                <td>${escapeHtml(c.domain || '-')}</td>
                <td><span style="color:${c.httpOnly ? '#10b981' : '#94a3b8'}; font-weight:bold;">${c.httpOnly ? '✔ HttpOnly' : '✕'}</span></td>
                <td><span style="color:${c.secure ? '#0284c7' : '#94a3b8'};">${c.secure ? '✔ Secure' : '✕'}</span></td>
            </tr>
        `).join('');
    }

    // 全量获取
    document.getElementById('btn-cookie-get-all')?.addEventListener('click', async () => {
        log('🔥 调用 electron.cookie.getAll() 全量提取底座 SQLite Cookie...');
        try {
            const list = await electron.cookie.getAll();
            renderCookieTable(list);
            log(`全量提取成功！共获取 ${list.length} 条 Cookie (包含底层所有 HttpOnly)`, 'success');
        } catch (err: any) {
            log(`提取 Cookie 失败: ${err.message}`, 'error');
        }
    });

    // 获取当前页面
    document.getElementById('btn-cookie-get-current')?.addEventListener('click', async () => {
        log('调用 electron.cookie.getDetails()...');
        try {
            const list = await electron.cookie.getDetails();
            renderCookieTable(list);
            log(`当前页面 Cookie 提取成功！共 ${list.length} 条`, 'success');
        } catch (err: any) {
            log(`提取当前页面 Cookie 失败: ${err.message}`, 'error');
        }
    });

    // 清空 Cookie
    document.getElementById('btn-cookie-clear')?.addEventListener('click', async () => {
        const ok = await electron.dialog.confirm('确定要清空底层所有的 Cookie 吗？', '清空 Cookie');
        if (!ok) return;
        try {
            await electron.cookie.clear();
            renderCookieTable([]);
            log('Cookie 已全部清空', 'success');
            await electron.device.toast('Cookie 已成功清空');
        } catch (err: any) {
            log(`清空 Cookie 失败: ${err.message}`, 'error');
        }
    });

    // 读取指定 URL 字符串
    document.getElementById('btn-cookie-read-url')?.addEventListener('click', async () => {
        const input = document.getElementById('input-cookie-read-url') as HTMLInputElement;
        const url = input?.value?.trim() || 'https://m.baidu.com';
        log(`读取 ${url} 的 Cookie 字符串...`);
        try {
            const str = await electron.cookie.get(url);
            log(`[${url}] Cookie: ${str || '(空)'}`, 'success');
            await electron.dialog.alert(str || '(暂无 Cookie)', `${url} Cookie 内容`);
        } catch (err: any) {
            log(`读取 Cookie 字符串失败: ${err.message}`, 'error');
        }
    });

    // 注入自定义结构体 Cookie
    document.getElementById('btn-cookie-inject')?.addEventListener('click', async () => {
        const targetUrl = (document.getElementById('input-cookie-target-url') as HTMLInputElement)?.value?.trim();
        const name = (document.getElementById('input-cookie-name') as HTMLInputElement)?.value?.trim();
        const value = (document.getElementById('input-cookie-value') as HTMLInputElement)?.value?.trim();
        const domain = (document.getElementById('input-cookie-domain') as HTMLInputElement)?.value?.trim();
        const httpOnly = (document.getElementById('chk-cookie-httponly') as HTMLInputElement)?.checked;
        const secure = (document.getElementById('chk-cookie-secure') as HTMLInputElement)?.checked;

        if (!targetUrl || !name || !value) {
            await electron.device.toast('请填写目标 URL、键名与键值');
            return;
        }

        const cookieItem: CookieDetail = {
            name,
            value,
            domain: domain || undefined,
            path: '/',
            httpOnly,
            secure
        };

        log(`注入 Cookie [${name}=${value}] (httpOnly: ${httpOnly})...`);
        try {
            await electron.cookie.set(targetUrl, [cookieItem]);
            log(`Cookie [${name}] 写入成功！`, 'success');
            await electron.device.toast(`Cookie ${name} 注入成功`);
            // 自动刷新表格
            const list = await electron.cookie.getAll();
            renderCookieTable(list);
        } catch (err: any) {
            log(`注入 Cookie 失败: ${err.message}`, 'error');
        }
    });

    // 注入测试预设
    document.getElementById('btn-cookie-inject-preset')?.addEventListener('click', async () => {
        const presetCookies: CookieDetail[] = [
            { name: 'sessionid', value: 'sec_sess_9988776655', domain: '.douyin.com', path: '/', httpOnly: true, secure: true },
            { name: 'passport_csrf_token', value: 'csrf_tok_112233', domain: '.douyin.com', path: '/', httpOnly: false, secure: true },
            { name: 'BAIDUID', value: 'ABCDEF1234567890:FG=1', domain: '.baidu.com', path: '/', httpOnly: false, secure: false },
            { name: 'BDUSS', value: 'sec_bduss_super_token_mock', domain: '.baidu.com', path: '/', httpOnly: true, secure: true }
        ];

        log('⚡ 正在批量注入测试 Cookie 预设包 (含多条 HttpOnly 会话Token)...');
        try {
            await electron.cookie.set('https://creator.douyin.com', [presetCookies[0], presetCookies[1]]);
            await electron.cookie.set('https://www.baidu.com', [presetCookies[2], presetCookies[3]]);
            log('批量测试预设 Cookie 注入完成！', 'success');
            await electron.device.toast('批量预设 Cookie 注入完成');
            const list = await electron.cookie.getAll();
            renderCookieTable(list);
        } catch (err: any) {
            log(`批量注入预设失败: ${err.message}`, 'error');
        }
    });

    // 过滤表格
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

// 模块 3: 存储与快照 (Storage & Dump)
function setupStorage() {
    // KV 存储
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
            log(`原生持久化 KV 写入成功: [${key}]`, 'success');
            await electron.device.toast(`KV 保存成功: ${key}`);
        } catch (err: any) {
            log(`KV 写入失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-kv-get')?.addEventListener('click', async () => {
        const key = (document.getElementById('input-kv-key') as HTMLInputElement)?.value?.trim();
        if (!key) return;
        try {
            const res = await electron.storage.getObject(key);
            showResult('kv-result-box', res !== null ? res : '(未找到键对应的值)');
            log(`读取 KV [${key}]: ${JSON.stringify(res)}`, 'success');
        } catch (err: any) {
            log(`读取 KV 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-kv-keys')?.addEventListener('click', async () => {
        try {
            const keys = await electron.storage.keys();
            showResult('kv-result-box', { allKeys: keys });
            log(`获取所有已存键名 (${keys.length} 项): ${keys.join(', ')}`, 'success');
        } catch (err: any) {
            log(`获取键列表失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-kv-remove')?.addEventListener('click', async () => {
        const key = (document.getElementById('input-kv-key') as HTMLInputElement)?.value?.trim();
        if (!key) return;
        try {
            await electron.storage.remove(key);
            log(`已删除键 [${key}]`, 'success');
            await electron.device.toast(`键 ${key} 已删除`);
        } catch (err: any) {
            log(`删除键失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-kv-clear')?.addEventListener('click', async () => {
        try {
            await electron.storage.clear();
            showResult('kv-result-box', '已清空所有原生存储数据');
            log('原生存储数据已全部清空', 'success');
        } catch (err: any) {
            log(`清空原生存储失败: ${err.message}`, 'error');
        }
    });

    // LocalStorage 操作
    document.getElementById('btn-ls-read')?.addEventListener('click', async () => {
        log('读取目标 WebView 的 LocalStorage...');
        try {
            const data = await electron.storage.getLocalStorage();
            showResult('ls-result-box', data);
            log(`LocalStorage 读取成功: ${Object.keys(data).length} 个条目`, 'success');
        } catch (err: any) {
            log(`读取 LocalStorage 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-ls-write')?.addEventListener('click', async () => {
        const testData = {
            authToken: 'ME_AUTH_2026_' + Math.random().toString(36).substring(2, 8),
            themePreference: 'dark-mode',
            lastAccessTime: new Date().toISOString()
        };
        log('向当前页面批量写入 LocalStorage 键值...');
        try {
            await electron.storage.setLocalStorage(testData);
            showResult('ls-result-box', { written: testData });
            log('LocalStorage 批量写入成功！', 'success');
            await electron.device.toast('LocalStorage 批量写入成功');
        } catch (err: any) {
            log(`写入 LocalStorage 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-ls-clear')?.addEventListener('click', async () => {
        try {
            await electron.storage.clearLocalStorage();
            showResult('ls-result-box', 'LocalStorage 已清空');
            log('当前页面 LocalStorage 已清空', 'success');
        } catch (err: any) {
            log(`清空 LocalStorage 失败: ${err.message}`, 'error');
        }
    });

    // SessionStorage 操作
    document.getElementById('btn-ss-read')?.addEventListener('click', async () => {
        try {
            const data = await electron.storage.getSessionStorage();
            showResult('ls-result-box', data);
            log(`SessionStorage 读取成功: ${Object.keys(data).length} 项`, 'success');
        } catch (err: any) {
            log(`读取 SessionStorage 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-ss-write')?.addEventListener('click', async () => {
        try {
            await electron.storage.setSessionStorage({ sessionTraceId: 'trace_' + Date.now() });
            log('SessionStorage 写入成功', 'success');
        } catch (err: any) {
            log(`写入 SessionStorage 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-ss-clear')?.addEventListener('click', async () => {
        try {
            await electron.storage.clearSessionStorage();
            log('SessionStorage 已清空', 'success');
        } catch (err: any) {
            log(`清空 SessionStorage 失败: ${err.message}`, 'error');
        }
    });

    // dumpStorage 快照导出
    document.getElementById('btn-dump-storage')?.addEventListener('click', async () => {
        log('📸 一键导出全量存储快照 (Cookie + LocalStorage + SessionStorage)...');
        try {
            const dump = await electron.storage.dumpStorage();
            showResult('dump-result-box', dump);
            log(`全量快照导出成功！包含 ${dump.cookies?.length || 0} 条 Cookie (含HttpOnly), ${Object.keys(dump.localStorage || {}).length} 条 LocalStorage`, 'success');
        } catch (err: any) {
            log(`导出存储快照失败: ${err.message}`, 'error');
        }
    });
}

// 模块 4: 沙箱文件系统 (File System)
function setupFileSystem() {
    document.getElementById('btn-fs-paths')?.addEventListener('click', async () => {
        log('获取沙箱物理路径 (getPaths)...');
        try {
            const paths = await electron.file.getPaths();
            showResult('fs-paths-display', paths);
            log(`沙箱路径获取成功: Documents=${paths.documents}`, 'success');
        } catch (err: any) {
            log(`获取沙箱路径失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-fs-list')?.addEventListener('click', async () => {
        const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
        log(`遍历目录列表: [${dir}]...`);
        try {
            const files = await electron.file.list('', dir);
            showResult('fs-paths-display', { directory: dir, files });
            log(`目录遍历成功，共找到 ${files.length} 个文件/子目录`, 'success');
        } catch (err: any) {
            log(`遍历目录失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-fs-write')?.addEventListener('click', async () => {
        const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
        const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
        const content = (document.getElementById('input-fs-content') as HTMLTextAreaElement)?.value;
        if (!fileName) return;

        log(`写入沙箱文件: ${dir}/${fileName}...`);
        try {
            const fullPath = await electron.file.writeText(fileName, content, dir);
            showResult('fs-file-result', `写入成功！完整物理路径:\n${fullPath}`);
            log(`文件写入成功: ${fullPath}`, 'success');
            await electron.device.toast('沙箱文件写入成功');
        } catch (err: any) {
            log(`文件写入失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-fs-read')?.addEventListener('click', async () => {
        const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
        const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
        if (!fileName) return;

        log(`读取沙箱文件: ${dir}/${fileName}...`);
        try {
            const text = await electron.file.readText(fileName, dir);
            showResult('fs-file-result', text);
            log(`文件内容读取成功 (${text.length} 字符)`, 'success');
        } catch (err: any) {
            log(`读取文件失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-fs-exists')?.addEventListener('click', async () => {
        const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
        const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
        if (!fileName) return;

        try {
            const res = await electron.file.exists(fileName, dir);
            showResult('fs-file-result', res);
            log(`文件存在性检查: exists=${res.exists}, isFile=${res.isFile}, isDir=${res.isDirectory}`, 'success');
        } catch (err: any) {
            log(`检查失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-fs-mkdir')?.addEventListener('click', async () => {
        const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
        const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
        if (!fileName) return;

        try {
            const ok = await electron.file.mkdir(fileName, dir);
            log(`目录创建结果: ${ok}`, 'success');
            await electron.device.toast('目录创建完成');
        } catch (err: any) {
            log(`创建目录失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-fs-delete')?.addEventListener('click', async () => {
        const fileName = (document.getElementById('input-fs-filename') as HTMLInputElement)?.value?.trim();
        const dir = (document.getElementById('select-fs-dir') as HTMLSelectElement)?.value as any || 'documents';
        if (!fileName) return;

        const ok = await electron.dialog.confirm(`确定删除 ${dir}/${fileName} 吗？`, '删除确认');
        if (!ok) return;

        try {
            await electron.file.delete(fileName, dir);
            log(`文件/目录已删除: ${fileName}`, 'success');
            showResult('fs-file-result', `文件已删除: ${fileName}`);
        } catch (err: any) {
            log(`删除失败: ${err.message}`, 'error');
        }
    });

    // 下载文件
    document.getElementById('btn-fs-download')?.addEventListener('click', async () => {
        const url = (document.getElementById('input-fs-download-url') as HTMLInputElement)?.value?.trim();
        const dest = (document.getElementById('input-fs-download-name') as HTMLInputElement)?.value?.trim() || 'downloaded_file.bin';
        if (!url) return;

        log(`开始原生后台下载: ${url} -> ${dest}...`);
        try {
            const res = await electron.file.download({
                url,
                destinationPath: dest,
                directory: 'documents'
            });
            log(`下载成功！文件大小: ${res.size} 字节, 存储于: ${res.fullPath}`, 'success');
            await electron.dialog.alert(`下载成功！\n文件大小: ${res.size} 字节\n路径: ${res.fullPath}`, '下载完成');
        } catch (err: any) {
            log(`下载失败: ${err.message}`, 'error');
        }
    });
}

// 模块 5: 原生网络请求 (Network)
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

        log(`🚀 发起绕过 CORS 原生网络请求: [${method}] ${url}...`);
        try {
            let bodyData: any = undefined;
            if (method !== 'GET' && bodyStr) {
                try {
                    bodyData = JSON.parse(bodyStr);
                } catch {
                    bodyData = bodyStr;
                }
            }

            const res = await electron.network.fetchNative({
                url,
                method,
                headers: {
                    'User-Agent': 'MobileElectron/2.0 NativeClient',
                    'Content-Type': 'application/json'
                },
                body: bodyData
            });

            showResult('net-result-box', {
                status: res.status,
                headers: res.headers,
                body: res.body
            });
            log(`原生 HTTP 响应 [${res.status}]: ${typeof res.body === 'string' ? res.body.slice(0, 100) : 'JSON Data'}...`, 'success');
        } catch (err: any) {
            showResult('net-result-box', `网络错误: ${err.message}`);
            log(`原生请求失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-net-listen')?.addEventListener('click', () => {
        const pattern = (document.getElementById('input-net-pattern') as HTMLInputElement)?.value?.trim() || '';
        log(`注册网络响应监听器 (Pattern: "${pattern}")...`);

        try {
            electron.network.onResponse(pattern, (res) => {
                const box = document.getElementById('net-listen-box');
                if (box) {
                    box.textContent = `[Captured at ${new Date().toLocaleTimeString()}]\nURL: ${res.url}\nStatus: ${res.status}\nBody Preview: ${String(res.body).slice(0, 150)}`;
                    box.classList.add('visible');
                }
                log(`📡 监听到匹配响应 [${res.url}]: 状态码 ${res.status}`, 'success');
            });
            log('网络监听器注册成功', 'success');
            const listenBox = document.getElementById('net-listen-box');
            if (listenBox) {
                listenBox.textContent = `监听已激活 (关键词: "${pattern}")，等待捕获网络流量...`;
                listenBox.classList.add('visible');
            }
        } catch (err: any) {
            log(`注册监听器失败: ${err.message}`, 'error');
        }
    });
}

// 模块 6: 设备能力与原生弹窗 (Device & Dialog)
function setupDeviceAndDialog() {
    document.getElementById('btn-device-toast')?.addEventListener('click', async () => {
        const text = (document.getElementById('input-toast-text') as HTMLInputElement)?.value?.trim() || '默认 Toast';
        try {
            await electron.device.toast(text);
            log(`Toast 已弹出: "${text}"`, 'success');
        } catch (err: any) {
            log(`Toast 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-clipboard-set')?.addEventListener('click', async () => {
        const text = (document.getElementById('input-clipboard-text') as HTMLInputElement)?.value || '';
        try {
            await electron.device.setClipboard(text);
            log(`写入剪贴板成功: "${text}"`, 'success');
            await electron.device.toast('已成功写入系统剪贴板');
        } catch (err: any) {
            log(`写入剪贴板失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-clipboard-get')?.addEventListener('click', async () => {
        try {
            const clip = await electron.device.getClipboard();
            showResult('device-result-box', `剪贴板当前内容:\n"${clip}"`);
            log(`读取剪贴板: "${clip}"`, 'success');
        } catch (err: any) {
            log(`读取剪贴板失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-dialog-alert')?.addEventListener('click', async () => {
        log('触发原生 alert 对话框...');
        await electron.dialog.alert('欢迎使用 Mobile Electron 移动端跨端开发框架！原生对话框测试完毕。', '原生提示');
        showResult('dialog-result-box', 'Alert 对话框已关闭');
        log('Alert 对话框已关闭', 'success');
    });

    document.getElementById('btn-dialog-confirm')?.addEventListener('click', async () => {
        log('触发原生 confirm 对话框...');
        const confirmed = await electron.dialog.confirm('请问您是否认可全网页驱动 Native 容器的技术架构路线？', '架构路线确认');
        showResult('dialog-result-box', { userChoice: confirmed ? '用户点击了【确定】' : '用户点击了【取消】' });
        log(`Confirm 交互结果: ${confirmed}`, 'success');
    });

    document.getElementById('btn-dialog-prompt')?.addEventListener('click', async () => {
        log('触发原生 prompt 对话框...');
        const inputVal = await electron.dialog.prompt('请输入您期望注入的自定义 Cookie 名称：', 'mock_token');
        showResult('dialog-result-box', { promptInput: inputVal !== null ? inputVal : '(用户点击了取消)' });
        log(`Prompt 交互结果: ${inputVal}`, 'success');
    });
}

// 模块 7: 多页面沙箱与 Profile 调度 (Browser)
function setupBrowser() {
    async function refreshPageList() {
        try {
            const pages = await electron.browser.getAllPages();
            const profiles = await electron.browser.listProfiles();
            showResult('browser-pages-display', {
                totalActivePages: pages.length,
                allProfiles: profiles,
                pages
            });
            log(`活跃页面列表已更新: 共 ${pages.length} 个页面`, 'success');
        } catch (err: any) {
            log(`获取页面列表失败: ${err.message}`, 'error');
        }
    }

    document.getElementById('btn-browser-refresh')?.addEventListener('click', refreshPageList);
    document.getElementById('btn-browser-pages')?.addEventListener('click', refreshPageList);

    document.getElementById('btn-browser-profiles')?.addEventListener('click', async () => {
        try {
            const profiles = await electron.browser.listProfiles();
            showResult('browser-pages-display', { profiles });
            log(`系统中存在的独立 Profile 分区: ${profiles.join(', ')}`, 'success');
        } catch (err: any) {
            log(`获取 Profile 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-browser-douyin-login')?.addEventListener('click', async () => {
        log('启动抖音创作者中心扫码登录与二维码抓取流程...');
        try {
            const res = await electron.browser.startDouyinQrLogin();
            showResult('browser-pages-display', res);
            log(`抖音登录初始化结果: ${res.message}`, 'success');
        } catch (err: any) {
            log(`抖音登录启动失败: ${err.message}`, 'error');
        }
    });

    // 新建页面
    document.getElementById('btn-new-page-create')?.addEventListener('click', async () => {
        const url = (document.getElementById('input-new-page-url') as HTMLInputElement)?.value?.trim() || 'https://m.baidu.com';
        const profile = (document.getElementById('input-new-page-profile') as HTMLInputElement)?.value?.trim() || 'default';
        const preset = (document.getElementById('select-new-page-preset') as HTMLSelectElement)?.value as any;
        const headless = (document.getElementById('select-new-page-headless') as HTMLSelectElement)?.value === 'true';

        log(`🚀 创建独立沙箱新页面 [Profile: ${profile}, Headless: ${headless}, 硬件预设: ${preset}] -> ${url}...`);
        try {
            const page = await electron.browser.newPage({
                url,
                profile,
                headless,
                hardwarePreset: preset
            });

            currentActivePage = page;
            createdPages.set(page.pageId, page);

            log(`页面创建成功！PageId: ${page.pageId}, Profile: ${page.profile}`, 'success');
            await electron.device.toast(`页面创建成功: ${page.pageId}`);

            // 显示控制器卡片
            const opsCard = document.getElementById('active-page-ops-card');
            const pageIdSpan = document.getElementById('current-op-page-id');
            if (opsCard) opsCard.classList.remove('hidden');
            if (pageIdSpan) pageIdSpan.textContent = `${page.pageId} (${profile})`;

            await refreshPageList();
        } catch (err: any) {
            log(`创建页面失败: ${err.message}`, 'error');
        }
    });

    // 控制器操作
    document.getElementById('btn-page-front')?.addEventListener('click', async () => {
        if (!currentActivePage) return;
        try {
            await currentActivePage.bringToFront();
            log(`页面 [${currentActivePage.pageId}] 已切换至前台全屏展示`, 'success');
        } catch (err: any) {
            log(`切换前台失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-page-back')?.addEventListener('click', async () => {
        if (!currentActivePage) return;
        try {
            await currentActivePage.sendToBack();
            log(`页面 [${currentActivePage.pageId}] 已退回后台，切回主页`, 'success');
        } catch (err: any) {
            log(`退回后台失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-page-get-cookies')?.addEventListener('click', async () => {
        if (!currentActivePage) return;
        try {
            const cookies = await currentActivePage.getAllCookies();
            showResult('page-op-result', { pageId: currentActivePage.pageId, cookies });
            log(`提取该页面 Cookie 成功: 共 ${cookies.length} 条 (含HttpOnly)`, 'success');
        } catch (err: any) {
            log(`提取 Cookie 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-page-get-ls')?.addEventListener('click', async () => {
        if (!currentActivePage) return;
        try {
            const ls = await currentActivePage.getLocalStorage();
            showResult('page-op-result', { pageId: currentActivePage.pageId, localStorage: ls });
            log(`提取该页面 LocalStorage 成功`, 'success');
        } catch (err: any) {
            log(`提取 LocalStorage 失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-page-dump')?.addEventListener('click', async () => {
        if (!currentActivePage) return;
        try {
            const dump = await currentActivePage.dumpStorage();
            showResult('page-op-result', dump);
            log(`导出该页面快照成功！`, 'success');
        } catch (err: any) {
            log(`导出快照失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-page-hw')?.addEventListener('click', async () => {
        if (!currentActivePage) return;
        try {
            const hw = await currentActivePage.getHardware();
            showResult('page-op-result', hw);
            log(`获取该页面硬件指纹成功: CPU=${hw.cpuCores}核, 内存=${hw.deviceMemory}GB`, 'success');
        } catch (err: any) {
            log(`获取硬件指纹失败: ${err.message}`, 'error');
        }
    });

    document.getElementById('btn-page-eval')?.addEventListener('click', async () => {
        if (!currentActivePage) return;
        const script = (document.getElementById('input-page-eval') as HTMLInputElement)?.value?.trim() || 'document.title';
        try {
            const result = await currentActivePage.evaluate(script);
            showResult('page-op-result', { script, result });
            log(`页面动态 JS 执行成功: 结果=${JSON.stringify(result)}`, 'success');
        } catch (err: any) {
            log(`动态 JS 执行失败: ${err.message}`, 'error');
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
            log(`页面 [${pageId}] 已销毁释放`, 'success');
            await refreshPageList();
        } catch (err: any) {
            log(`关闭页面失败: ${err.message}`, 'error');
        }
    });
}

// 底部控制台操作
function setupConsoleDrawer() {
    const drawer = document.querySelector('.console-drawer');
    const toggleBtn = document.getElementById('btn-toggle-console');
    const clearBtn = document.getElementById('btn-clear-logs');
    const copyBtn = document.getElementById('btn-copy-logs');
    const consoleLogs = document.getElementById('console-logs');

    toggleBtn?.addEventListener('click', () => {
        drawer?.classList.toggle('collapsed');
    });

    clearBtn?.addEventListener('click', () => {
        if (consoleLogs) {
            consoleLogs.innerHTML = '';
            log('日志已清空', 'info');
        }
    });

    copyBtn?.addEventListener('click', async () => {
        if (consoleLogs) {
            const text = consoleLogs.innerText;
            try {
                await electron.device.setClipboard(text);
                await electron.device.toast('日志已复制到剪贴板');
            } catch {
                navigator.clipboard?.writeText(text);
            }
        }
    });
}

// 统一主入口初始化
window.addEventListener('DOMContentLoaded', () => {
    initEnvironment();
    setupTabs();
    setupQuickBar();
    setupAppAndWindow();
    setupCookie();
    setupStorage();
    setupFileSystem();
    setupNetwork();
    setupDeviceAndDialog();
    setupBrowser();
    setupConsoleDrawer();
});
