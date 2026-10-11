/**
 * @file mock.ts
 * @description 浏览器环境开发调试降级 Mock 引擎
 */

import { AppInfo, NativeResponse } from './types';

export class MockEngine {
    public static isMockEnabled = true;
    public static onMockEvent?: (eventName: string, data: any) => void;
    private static memoryStorage = new Map<string, string>();
    private static memorySecureStorage = new Map<string, string>();
    private static mockFloatingWindows = new Map<string, any>();
    private static mockClipboardText: string = '';

    public static mockInvoke<T = any>(module: string, action: string, params: Record<string, any> = {}): Promise<T> {
        console.warn(`[MobileElectron Mock] (${module}.${action}) called outside of native container. Returning mock fallback.`, params);

        switch (`${module}.${action}`) {
            case 'app.getInfo':
                return Promise.resolve({
                    appName: 'Mobile Electron Web App (Dev Mode)',
                    appId: 'com.example.mockapp',
                    version: '2.0.0-mock',
                    framework: 'MobileElectron-WebFallback',
                    frameworkVersion: '2.0.0',
                    multiProfileSupported: false,
                    defaultUrl: typeof window !== 'undefined' ? window.location.href : '',
                    config: {}
                } as unknown as T);

            case 'app.getConfig':
                return Promise.resolve({} as unknown as T);

            case 'app.getConfigStatus':
                return Promise.resolve({
                    source: 'builtin_asset',
                    defaultUrl: typeof window !== 'undefined' ? window.location.href : '',
                    whitelistCount: 8,
                    version: '2.0.0-mock'
                } as unknown as T);

            case 'app.fetchRemoteConfig':
                return Promise.resolve({
                    success: true,
                    source: 'remote_cached',
                    config: {
                        app: { name: 'Mobile Electron (Remote Mock)', version: '2.2.0' },
                        security: { whitelist: ['*', 'https://*.example.com'] },
                        window: { defaultUrl: params?.url || '' }
                    }
                } as unknown as T);

            case 'app.applyRemoteConfig':
                return Promise.resolve({ success: true, applied: true } as unknown as T);

            case 'app.resetRemoteConfig':
                return Promise.resolve({ success: true, source: 'builtin_asset' } as unknown as T);

            case 'app.exit':
                alert('[Mock App Exit] exit() called');
                return Promise.resolve({ success: true } as unknown as T);

            case 'app.canOpenUrl':
                return Promise.resolve({ canOpen: true } as unknown as T);

            case 'app.openUrl':
                console.info('[Mock App openUrl]', params?.url, 'fallback:', params?.fallbackUrl);
                return Promise.resolve({ success: true } as unknown as T);

            case 'window.setStatusBar':
            case 'window.setFullscreen':
            case 'window.setDebugToolbarVisible':
                return Promise.resolve({ success: true } as unknown as T);

            case 'window.goBack':
                if (typeof window !== 'undefined' && window.history) {
                    window.history.back();
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'window.canGoBack':
                return Promise.resolve({ canGoBack: typeof window !== 'undefined' && window.history.length > 1 } as unknown as T);

            case 'window.reload':
                if (typeof window !== 'undefined') {
                    window.location.reload();
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'window.loadUrl':
                if (typeof window !== 'undefined' && params.url) {
                    window.location.href = params.url;
                }
                return Promise.resolve({ success: true, url: params.url } as unknown as T);

            case 'window.createFloatingWindow': {
                const fId = params.id || 'mock_float_' + Math.random().toString(36).substring(2, 8);
                const winObj = {
                    id: fId,
                    url: params.url || '',
                    title: params.title || '悬浮画中画',
                    width: params.width || 280,
                    height: params.height || 200,
                    x: params.x !== undefined ? params.x : 100,
                    y: params.y !== undefined ? params.y : 80,
                    isMinimized: false,
                    isVisible: true
                };
                MockEngine.mockFloatingWindows.set(fId, winObj);
                return Promise.resolve({
                    success: true,
                    id: fId,
                    width: winObj.width,
                    height: winObj.height,
                    x: winObj.x,
                    y: winObj.y
                } as unknown as T);
            }

            case 'window.closeFloatingWindow': {
                const targetId = params.id || (MockEngine.mockFloatingWindows.keys().next().value);
                let deleted = false;
                if (targetId && MockEngine.mockFloatingWindows.has(targetId)) {
                    deleted = MockEngine.mockFloatingWindows.delete(targetId);
                }
                return Promise.resolve({ success: deleted } as unknown as T);
            }

            case 'window.setFloatingWindowVisible': {
                const target = MockEngine.mockFloatingWindows.get(params.id);
                if (target) {
                    target.isVisible = params.visible !== false;
                    return Promise.resolve({ success: true } as unknown as T);
                }
                return Promise.resolve({ success: false } as unknown as T);
            }

            case 'window.minimizeFloatingWindow': {
                const target = MockEngine.mockFloatingWindows.get(params.id);
                if (target) {
                    target.isMinimized = params.minimized !== false;
                    return Promise.resolve({ success: true } as unknown as T);
                }
                return Promise.resolve({ success: false } as unknown as T);
            }

            case 'window.updateFloatingWindow': {
                const target = MockEngine.mockFloatingWindows.get(params.id);
                if (target) {
                    if (params.width !== undefined) target.width = params.width;
                    if (params.height !== undefined) target.height = params.height;
                    if (params.x !== undefined) target.x = params.x;
                    if (params.y !== undefined) target.y = params.y;
                    return Promise.resolve({ success: true } as unknown as T);
                }
                return Promise.resolve({ success: false } as unknown as T);
            }

            case 'window.evalFloatingWindow': {
                return Promise.resolve({
                    success: true,
                    result: 'mock_eval_result_for_' + (params.script || '')
                } as unknown as T);
            }

            case 'window.getFloatingWindowState': {
                const targetId = params.id || (MockEngine.mockFloatingWindows.keys().next().value);
                const target = targetId ? MockEngine.mockFloatingWindows.get(targetId) : null;
                if (target) {
                    return Promise.resolve({ ...target } as unknown as T);
                }
                return Promise.reject(new Error('Floating window not found: ' + targetId));
            }

            case 'ipc.send':
            case 'ipc.broadcast': {
                const eventPayload = {
                    channel: params.channel,
                    senderId: params.senderId || 'mock_sender',
                    targetId: 'all',
                    payload: params.payload,
                    timestamp: Date.now()
                };
                if (typeof window !== 'undefined' && (window as any).__onNativeRpcEvent) {
                    (window as any).__onNativeRpcEvent(JSON.stringify({ event: 'ipc.message', data: eventPayload }));
                }
                if (MockEngine.onMockEvent) {
                    MockEngine.onMockEvent('ipc.message', eventPayload);
                }
                return Promise.resolve({ success: true, recipientCount: 1 } as unknown as T);
            }

            case 'ipc.sendTo': {
                const eventPayload = {
                    channel: params.channel,
                    senderId: params.senderId || 'mock_sender',
                    targetId: params.targetId,
                    payload: params.payload,
                    timestamp: Date.now()
                };
                if (typeof window !== 'undefined' && (window as any).__onNativeRpcEvent) {
                    (window as any).__onNativeRpcEvent(JSON.stringify({ event: 'ipc.message', data: eventPayload }));
                }
                if (MockEngine.onMockEvent) {
                    MockEngine.onMockEvent('ipc.message', eventPayload);
                }
                return Promise.resolve({ success: true, targetId: params.targetId } as unknown as T);
            }

            case 'dialog.alert':
                if (typeof window !== 'undefined') {
                    window.alert(params.message || '');
                }
                return Promise.resolve({ confirmed: true } as unknown as T);

            case 'dialog.confirm':
                const conf = typeof window !== 'undefined' ? window.confirm(params.message || '') : true;
                return Promise.resolve({ confirmed: conf } as unknown as T);

            case 'dialog.prompt':
                const promptVal = typeof window !== 'undefined' ? window.prompt(params.message || '', params.defaultValue || '') : null;
                return Promise.resolve({ confirmed: promptVal !== null, value: promptVal } as unknown as T);

            case 'storage.set':
                if (typeof localStorage !== 'undefined' && params.key) {
                    localStorage.setItem('me_' + params.key, params.value || '');
                } else if (params.key) {
                    MockEngine.memoryStorage.set('me_' + params.key, params.value || '');
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'storage.get':
                let sVal: string | null = null;
                if (typeof localStorage !== 'undefined' && params.key) {
                    sVal = localStorage.getItem('me_' + params.key);
                } else if (params.key) {
                    sVal = MockEngine.memoryStorage.has('me_' + params.key)
                        ? MockEngine.memoryStorage.get('me_' + params.key)!
                        : null;
                }
                return Promise.resolve({ value: sVal, exists: sVal !== null } as unknown as T);

            case 'storage.remove':
                if (typeof localStorage !== 'undefined' && params.key) {
                    localStorage.removeItem('me_' + params.key);
                } else if (params.key) {
                    MockEngine.memoryStorage.delete('me_' + params.key);
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'storage.clear':
                if (typeof localStorage !== 'undefined') {
                    Object.keys(localStorage).filter(k => k.startsWith('me_')).forEach(k => localStorage.removeItem(k));
                } else {
                    MockEngine.memoryStorage.clear();
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'storage.keys':
                let sKeys: string[] = [];
                if (typeof localStorage !== 'undefined') {
                    sKeys = Object.keys(localStorage).filter(k => k.startsWith('me_')).map(k => k.replace('me_', ''));
                } else {
                    sKeys = Array.from(MockEngine.memoryStorage.keys()).filter(k => k.startsWith('me_')).map(k => k.replace('me_', ''));
                }
                return Promise.resolve({ keys: sKeys } as unknown as T);

            case 'file.getPaths':
                return Promise.resolve({ documents: '/mock/documents', cache: '/mock/cache', temp: '/mock/temp' } as unknown as T);

            case 'file.write':
                return Promise.resolve({ success: true, bytesWritten: (params.content || '').length, fullPath: '/mock/documents/' + params.path } as unknown as T);

            case 'file.read':
                return Promise.resolve({ content: 'Mock file content for ' + params.path, size: 24, encoding: params.encoding || 'utf8' } as unknown as T);

            case 'file.exists':
                return Promise.resolve({ exists: true, isFile: true, isDirectory: false } as unknown as T);

            case 'file.delete':
            case 'file.mkdir':
            case 'file.download':
                return Promise.resolve({ success: true, size: 1024, fullPath: '/mock/documents/' + (params.destinationPath || '') } as unknown as T);

            case 'file.list':
                return Promise.resolve({ files: [{ name: 'sample.txt', isDirectory: false, size: 100, lastModified: Date.now() }] } as unknown as T);

            case 'device.toast':
                console.log(`[Toast]: ${params.message}`);
                return Promise.resolve({} as unknown as T);

            case 'device.getClipboard':
                return Promise.resolve({ text: '' } as unknown as T);

            case 'device.setClipboard':
                return Promise.resolve({ success: true } as unknown as T);

            case 'cookie.get':
            case 'cookie.getAll':
            case 'page.getCookies':
            case 'page.getAllCookies': {
                const rawDoc = typeof document !== 'undefined' ? document.cookie : '';
                const items = rawDoc.split(';').filter(Boolean).map(p => {
                    const idx = p.indexOf('=');
                    const name = idx > -1 ? p.slice(0, idx).trim() : p.trim();
                    const value = idx > -1 ? p.slice(idx + 1).trim() : '';
                    return {
                        name,
                        value,
                        domain: typeof window !== 'undefined' ? window.location.hostname : 'localhost',
                        path: '/',
                        secure: false,
                        httpOnly: false
                    };
                });
                return Promise.resolve({
                    cookies: rawDoc,
                    details: items,
                    count: items.length,
                    profile: params.profile || 'default'
                } as unknown as T);
            }

            case 'cookie.import':
            case 'cookie.set':
            case 'page.setCookies':
                if (typeof document !== 'undefined') {
                    if (Array.isArray(params.cookies)) {
                        params.cookies.forEach((c: any) => {
                            if (c.name) document.cookie = `${c.name}=${c.value}; path=${c.path || '/'}`;
                        });
                    } else if (params.input || params.cookies) {
                        document.cookie = params.input || params.cookies;
                    }
                }
                return Promise.resolve({ count: 1, success: true, profile: params.profile || 'default' } as unknown as T);

            case 'cookie.clear':
                return Promise.resolve({ success: true, profile: params.profile || 'default' } as unknown as T);

            case 'storage.getLocalStorage':
            case 'page.getLocalStorage': {
                const data: Record<string, string> = {};
                if (typeof localStorage !== 'undefined') {
                    for (let i = 0; i < localStorage.length; i++) {
                        const k = localStorage.key(i);
                        if (k && !k.startsWith('me_')) data[k] = localStorage.getItem(k) || '';
                    }
                }
                return Promise.resolve({ data, count: Object.keys(data).length } as unknown as T);
            }

            case 'storage.setLocalStorage':
            case 'page.setLocalStorage': {
                if (typeof localStorage !== 'undefined' && params.data) {
                    for (const k of Object.keys(params.data)) {
                        localStorage.setItem(k, params.data[k]);
                    }
                }
                return Promise.resolve({ success: true } as unknown as T);
            }

            case 'storage.clearLocalStorage':
            case 'page.clearLocalStorage': {
                if (typeof localStorage !== 'undefined') {
                    localStorage.clear();
                }
                return Promise.resolve({ success: true } as unknown as T);
            }

            case 'storage.dumpStorage':
            case 'page.dumpStorage': {
                const ls: Record<string, string> = {};
                const ss: Record<string, string> = {};
                if (typeof localStorage !== 'undefined') {
                    for (let i = 0; i < localStorage.length; i++) {
                        const k = localStorage.key(i);
                        if (k) ls[k] = localStorage.getItem(k) || '';
                    }
                }
                if (typeof sessionStorage !== 'undefined') {
                    for (let i = 0; i < sessionStorage.length; i++) {
                        const k = sessionStorage.key(i);
                        if (k) ss[k] = sessionStorage.getItem(k) || '';
                    }
                }
                return Promise.resolve({
                    cookies: [],
                    cookieString: typeof document !== 'undefined' ? document.cookie : '',
                    localStorage: ls,
                    sessionStorage: ss,
                    url: typeof window !== 'undefined' ? window.location.href : '',
                    profile: params.profile || 'default'
                } as unknown as T);
            }


            case 'network.getStatus': {
                const online = typeof navigator !== 'undefined' && typeof (navigator as any).onLine === 'boolean'
                    ? (navigator as any).onLine
                    : true;
                return Promise.resolve({
                    isConnected: online,
                    type: 'wifi',
                    isMetered: false
                } as unknown as T);
            }

            case 'network.fetch':
                return Promise.resolve({
                    status: 200,
                    headers: { 'content-type': 'application/json' },
                    body: JSON.stringify({ message: 'Mock native response', params })
                } as unknown as T);

            case 'page.create':
                return Promise.resolve({
                    pageId: 'page_mock_' + Date.now(),
                    profile: params.profile || 'default',
                    url: params.url || '',
                    multiProfileSupported: false,
                    hardware: params.hardware || {}
                } as unknown as T);

            case 'page.list':
                return Promise.resolve({
                    pages: [
                        { pageId: 'main', url: typeof window !== 'undefined' ? window.location.href : '', title: 'Mock Main', isHeadless: false, isForeground: true, profile: 'default' }
                    ],
                    multiProfileSupported: false
                } as unknown as T);

            case 'page.listProfiles':
                return Promise.resolve({ profiles: ['default'] } as unknown as T);

            case 'page.deleteProfile':
            case 'page.bringToFront':
            case 'page.sendToBack':
            case 'page.clearData':
            case 'page.close':
                return Promise.resolve({ success: true } as unknown as T);

            case 'page.startDouyinQrLogin':
                return Promise.resolve({ success: true, message: 'Mock Douyin QR Login Triggered' } as unknown as T);

            case 'page.extractQrCode':
                return Promise.resolve({ base64: '' } as unknown as T);

            case 'page.evaluate':
                return Promise.resolve({ result: null } as unknown as T);

            case 'tabbar.setItems':
                return Promise.resolve({ success: true, count: (params?.items || []).length } as unknown as T);

            case 'tabbar.show':
            case 'tabbar.setVisible':
                return Promise.resolve({ success: true, visible: true } as unknown as T);

            case 'tabbar.hide':
                return Promise.resolve({ success: true, visible: false } as unknown as T);

            case 'tabbar.toggle':
                return Promise.resolve({ success: true, visible: true } as unknown as T);

            case 'tabbar.setSelected':
            case 'tabbar.setBadge':
                return Promise.resolve({ success: true } as unknown as T);

            case 'tabbar.getState':
                return Promise.resolve({ visible: true, selectedId: 'tab_0', selectedIndex: 0, items: [] } as unknown as T);

            case 'debug.show':
            case 'debug.hide':
            case 'debug.toggle':
            case 'debug.setFloatingButtonVisible':
                return Promise.resolve({ success: true, visible: true } as unknown as T);

            case 'debug.openDevTools':
                if (typeof window !== 'undefined' && (window as any).eruda) {
                    (window as any).eruda.show();
                }
                return Promise.resolve({ success: true, devtoolsSupported: true, opened: true } as unknown as T);

            case 'debug.closeDevTools':
                if (typeof window !== 'undefined' && (window as any).eruda) {
                    (window as any).eruda.hide();
                }
                return Promise.resolve({ success: true, opened: false } as unknown as T);

            case 'debug.toggleDevTools':
                return Promise.resolve({ success: true } as unknown as T);

            case 'debug.getInfo':
                return Promise.resolve({ platform: 'web-mock', pid: 0 } as unknown as T);

            case 'hotUpdate.getCurrentVersion':
                return Promise.resolve({
                    version: '2.0.0-mock',
                    buildNumber: 100,
                    isHotUpdated: false,
                    source: 'builtin',
                    description: 'Web Mock 开发仿真环境'
                } as unknown as T);

            case 'hotUpdate.checkForUpdate':
                return Promise.resolve({
                    hasUpdate: true,
                    currentVersion: '2.0.0-mock',
                    currentBuild: 100,
                    latestVersion: '2.1.0',
                    latestBuild: 210,
                    packageUrl: 'http://127.0.0.1:8088/bundle-v2.1.0.mep',
                    packageHash: 'sha256:mock_hash_for_browser_test',
                    packageSize: 33075,
                    description: 'Mock 模拟新版本增量包发布 (热更测试)',
                    mandatory: false,
                    diff: {
                        isDiffSupported: true,
                        canUseDiff: true,
                        changedCount: 2,
                        reusableCount: 3,
                        diffSize: 4890,
                        fullPackageSize: 33075,
                        savedPercent: 85.2,
                        filesBaseUrl: 'http://127.0.0.1:8088/files/',
                        changedFiles: [
                            { path: 'index.html', hash: 'sha256:mock_html', size: 3200 },
                            { path: 'assets/index.js', hash: 'sha256:mock_js', size: 1690 }
                        ]
                    }
                } as unknown as T);

            case 'hotUpdate.downloadAndInstall':
                return Promise.resolve({
                    version: '2.1.0',
                    buildNumber: 210,
                    isHotUpdated: true,
                    source: 'sandbox',
                    updateType: params?.diff?.canUseDiff ? 'incremental_diff' : 'full_package',
                    diffFilesCount: params?.diff?.canUseDiff ? 2 : 0,
                    hash: 'sha256:mock_hash_for_browser_test',
                    appliedAt: Date.now()
                } as unknown as T);

            case 'hotUpdate.applyAndReload':
                if (typeof window !== 'undefined') {
                    window.location.reload();
                }
                return Promise.resolve({ applied: true } as unknown as T);

            case 'hotUpdate.rollback':
                return Promise.resolve({ rolledBack: true } as unknown as T);

            case 'haptics.impact':
            case 'haptics.notification':
            case 'haptics.selection':
            case 'haptics.vibrate':
                if (typeof window !== 'undefined' && 'vibrate' in navigator) {
                    navigator.vibrate(params?.duration || 20);
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'biometrics.isAvailable':
                return Promise.resolve({
                    available: true,
                    supported: true,
                    deviceSecure: true,
                    hasFingerprint: true,
                    hasFace: true,
                    biometricType: 'face'
                } as unknown as T);

            case 'biometrics.authenticate':
                const ok = typeof window !== 'undefined'
                    ? confirm(`[Mock 生物识别] ${params?.title || '验证身份'}\n${params?.description || '模拟指纹/面容识别成功？'}`)
                    : true;
                if (ok) {
                    return Promise.resolve({ success: true, authenticated: true } as unknown as T);
                } else {
                    return Promise.reject(new Error('用户取消了生物识别 (Mock)'));
                }

            case 'secureStorage.setItem': {
                const k = 'sec_' + (params?.key || '');
                if (typeof sessionStorage !== 'undefined') {
                    sessionStorage.setItem(k, params?.value || '');
                } else {
                    MockEngine.memorySecureStorage.set(k, params?.value || '');
                }
                return Promise.resolve({ success: true } as unknown as T);
            }

            case 'secureStorage.getItem': {
                const k = 'sec_' + (params?.key || '');
                let val: string | null = null;
                if (typeof sessionStorage !== 'undefined') {
                    val = sessionStorage.getItem(k);
                } else {
                    val = MockEngine.memorySecureStorage.has(k)
                        ? MockEngine.memorySecureStorage.get(k)!
                        : null;
                }
                return Promise.resolve({ value: val, exists: val !== null } as unknown as T);
            }

            case 'secureStorage.removeItem': {
                const k = 'sec_' + (params?.key || '');
                if (typeof sessionStorage !== 'undefined') {
                    sessionStorage.removeItem(k);
                } else {
                    MockEngine.memorySecureStorage.delete(k);
                }
                return Promise.resolve({ success: true } as unknown as T);
            }

            case 'secureStorage.clear': {
                if (typeof sessionStorage !== 'undefined') {
                    const toDel: string[] = [];
                    for (let i = 0; i < sessionStorage.length; i++) {
                        const k = sessionStorage.key(i);
                        if (k && k.startsWith('sec_')) toDel.push(k);
                    }
                    toDel.forEach(k => sessionStorage.removeItem(k));
                }
                return Promise.resolve({ success: true } as unknown as T);
            }

            case 'secureStorage.getAllKeys': {
                const keys: string[] = [];
                if (typeof sessionStorage !== 'undefined') {
                    for (let i = 0; i < sessionStorage.length; i++) {
                        const k = sessionStorage.key(i);
                        if (k && k.startsWith('sec_')) keys.push(k.substring(4));
                    }
                }
                return Promise.resolve({ keys } as unknown as T);
            }

            case 'camera.takePhoto':
            case 'camera.pickImage':
                return Promise.resolve({
                    base64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
                    width: 640,
                    height: 480
                } as unknown as T);

            case 'camera.scanBarcode':
                const mockCode = prompt('[Mock 模拟扫码] 请输入扫码结果字符串:', 'https://mobile-electron.dev/scanned-sample');
                if (mockCode !== null) {
                    return Promise.resolve({ text: mockCode, format: 'QR_CODE' } as unknown as T);
                } else {
                    return Promise.reject(new Error('用户取消了扫码 (Mock)'));
                }

            case 'clipboard.readText':
                return Promise.resolve({ text: MockEngine.mockClipboardText || '' } as unknown as T);

            case 'clipboard.writeText':
                MockEngine.mockClipboardText = params?.text || '';
                return Promise.resolve({ success: true } as unknown as T);

            case 'clipboard.hasText':
                return Promise.resolve({ hasText: Boolean(MockEngine.mockClipboardText) } as unknown as T);

            case 'clipboard.clear':
                MockEngine.mockClipboardText = '';
                return Promise.resolve({ success: true } as unknown as T);

            case 'notification.show': {
                const notifId = params?.id || 'mock_notif_' + Date.now();
                console.info('[Mock Notification]', params?.title, params?.body || params?.message);
                return Promise.resolve({ success: true, id: notifId } as unknown as T);
            }

            case 'notification.cancel':
            case 'notification.cancelAll':
                return Promise.resolve({ success: true } as unknown as T);

            case 'notification.areNotificationsEnabled':
                return Promise.resolve({ enabled: true } as unknown as T);

            default:
                return Promise.resolve({ success: true, mock: true } as unknown as T);
        }
    }
}

