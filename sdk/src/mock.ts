/**
 * @file mock.ts
 * @description 浏览器环境开发调试降级 Mock 引擎
 */

import { AppInfo, NativeResponse } from './types';

export class MockEngine {
    public static isMockEnabled = true;

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

            case 'app.exit':
                alert('[Mock App Exit] exit() called');
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
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'storage.get':
                const sVal = typeof localStorage !== 'undefined' && params.key ? localStorage.getItem('me_' + params.key) : null;
                return Promise.resolve({ value: sVal, exists: sVal !== null } as unknown as T);

            case 'storage.remove':
                if (typeof localStorage !== 'undefined' && params.key) {
                    localStorage.removeItem('me_' + params.key);
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'storage.clear':
                if (typeof localStorage !== 'undefined') {
                    Object.keys(localStorage).filter(k => k.startsWith('me_')).forEach(k => localStorage.removeItem(k));
                }
                return Promise.resolve({ success: true } as unknown as T);

            case 'storage.keys':
                const sKeys = typeof localStorage !== 'undefined'
                    ? Object.keys(localStorage).filter(k => k.startsWith('me_')).map(k => k.replace('me_', ''))
                    : [];
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

            default:
                return Promise.resolve({ success: true, mock: true } as unknown as T);
        }
    }
}
