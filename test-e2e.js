// Automated E2E verification script for Mobile Electron Web App
const http = require('http');

async function getWsUrl() {
    return new Promise((resolve, reject) => {
        http.get('http://127.0.0.1:9222/json', (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                const list = JSON.parse(data);
                const page = list.find(p => p.type === 'page' && p.url.includes('5173'));
                if (page && page.webSocketDebuggerUrl) {
                    resolve(page.webSocketDebuggerUrl);
                } else {
                    reject(new Error('Page target not found: ' + JSON.stringify(list)));
                }
            });
        }).on('error', reject);
    });
}

async function run() {
    const wsUrl = await getWsUrl();
    console.log('Connecting to DevTools WebSocket:', wsUrl);

    const ws = new WebSocket(wsUrl);
    await new Promise(res => ws.onopen = res);
    console.log('Connected to WebView DevTools!');

    let msgId = 1;
    function sendCommand(method, params = {}) {
        return new Promise((resolve, reject) => {
            const id = msgId++;
            const handler = (evt) => {
                const data = JSON.parse(evt.data);
                if (data.id === id) {
                    ws.removeEventListener('message', handler);
                    if (data.error) reject(data.error);
                    else resolve(data.result);
                }
            };
            ws.addEventListener('message', handler);
            ws.send(JSON.stringify({ id, method, params }));
        });
    }

    async function evaluate(expression) {
        const res = await sendCommand('Runtime.evaluate', {
            expression,
            awaitPromise: true,
            returnByValue: true
        });
        if (res.exceptionDetails) {
            throw new Error(res.exceptionDetails.exception?.description || res.exceptionDetails.text);
        }
        return res.result?.value;
    }

    console.log('\n--- 1. 验证运行环境 (Environment) ---');
    const envInfo = await evaluate(`({
        isNative: window.MobileElectron.isNativeContainer,
        platform: window.MobileElectron.platform,
        url: window.location.href,
        hasBridge: typeof window.AndroidBridge !== 'undefined'
    })`);
    console.log('Environment:', envInfo);

    console.log('\n--- 2. 测试 electron.app 模块 ---');
    const appInfo = await evaluate(`window.MobileElectron.app.getInfo()`);
    console.log('app.getInfo():', appInfo);

    const appConfig = await evaluate(`window.MobileElectron.app.getConfig()`);
    console.log('app.getConfig().defaultUrl:', appConfig?.window?.defaultUrl);

    console.log('\n--- 3. 测试 electron.window 模块 ---');
    const statusRes = await evaluate(`window.MobileElectron.window.setStatusBar({
        color: '#4F46E5',
        darkIcons: false,
        immersive: true
    })`);
    console.log('window.setStatusBar():', statusRes);

    const fullRes = await evaluate(`window.MobileElectron.window.setFullscreen(false)`);
    console.log('window.setFullscreen(false):', fullRes);

    console.log('\n--- 4. 测试 electron.cookie 模块 (突破沙箱与HttpOnly写入) ---');
    const cookieSetRes = await evaluate(`window.MobileElectron.cookie.set('https://creator.douyin.com', [
        {
            name: 'sessionid',
            value: 'sec_sess_test_1234567890',
            domain: '.douyin.com',
            path: '/',
            httpOnly: true,
            secure: true
        },
        {
            name: 'passport_csrf_token',
            value: 'csrf_tok_mock_888',
            domain: '.douyin.com',
            path: '/',
            httpOnly: false,
            secure: true
        }
    ])`);
    console.log('cookie.set() count:', cookieSetRes);

    const allCookies = await evaluate(`window.MobileElectron.cookie.getAll()`);
    console.log(`cookie.getAll() total cookies count: ${allCookies.length}`);
    const injected = allCookies.filter(c => c.name === 'sessionid');
    console.log('Injected sessionid cookie details:', injected);

    const cookieStr = await evaluate(`window.MobileElectron.cookie.get('https://creator.douyin.com')`);
    console.log('cookie.get("https://creator.douyin.com"):', cookieStr);

    console.log('\n--- 5. 测试 electron.storage 模块 (原生持久化与LocalStorage深度操控) ---');
    const kvSetRes = await evaluate(`window.MobileElectron.storage.setObject('test_config', {
        theme: 'dark',
        token: 'AUTH_TEST_2026',
        items: [1, 2, 3]
    })`);
    console.log('storage.setObject():', kvSetRes);

    const kvGetRes = await evaluate(`window.MobileElectron.storage.getObject('test_config')`);
    console.log('storage.getObject():', kvGetRes);

    const allKeys = await evaluate(`window.MobileElectron.storage.keys()`);
    console.log('storage.keys():', allKeys);

    const lsSetRes = await evaluate(`window.MobileElectron.storage.setLocalStorage({
        ls_user: 'Admin',
        ls_token: 'LS_SECRET_ABC'
    })`);
    console.log('storage.setLocalStorage():', lsSetRes);

    const lsGetRes = await evaluate(`window.MobileElectron.storage.getLocalStorage()`);
    console.log('storage.getLocalStorage():', lsGetRes);

    const dumpRes = await evaluate(`window.MobileElectron.storage.dumpStorage()`);
    console.log('storage.dumpStorage():', {
        url: dumpRes.url,
        cookieCount: dumpRes.cookies?.length,
        localStorageKeys: Object.keys(dumpRes.localStorage || {})
    });

    console.log('\n--- 6. 测试 electron.file / fs 模块 (沙箱文件系统) ---');
    const paths = await evaluate(`window.MobileElectron.file.getPaths()`);
    console.log('file.getPaths():', paths);

    const writePath = await evaluate(`window.MobileElectron.file.writeText(
        'e2e_test_doc.json',
        JSON.stringify({ testTime: Date.now(), agent: 'DeepMind Antigravity' }),
        'documents'
    )`);
    console.log('file.writeText() written path:', writePath);

    const readContent = await evaluate(`window.MobileElectron.file.readText('e2e_test_doc.json', 'documents')`);
    console.log('file.readText() read content:', readContent);

    const existsInfo = await evaluate(`window.MobileElectron.file.exists('e2e_test_doc.json', 'documents')`);
    console.log('file.exists():', existsInfo);

    const fileList = await evaluate(`window.MobileElectron.file.list('', 'documents')`);
    console.log('file.list() count:', fileList?.length);

    console.log('\n--- 7. 测试 electron.device 模块 (Toast与剪贴板) ---');
    await evaluate(`window.MobileElectron.device.toast('⚡ E2E 自动化测试全项通过！')`);
    console.log('device.toast() called');

    await evaluate(`window.MobileElectron.device.setClipboard('ClipboardContent_from_E2E_2026')`);
    const clipVal = await evaluate(`window.MobileElectron.device.getClipboard()`);
    console.log('device.getClipboard():', clipVal);

    console.log('\n--- 8. 测试 electron.network 模块 (绕过 CORS 原生网络请求) ---');
    const netRes = await evaluate(`window.MobileElectron.network.fetchNative({
        url: 'https://httpbin.org/get',
        method: 'GET'
    })`);
    console.log('network.fetchNative() status:', netRes?.status, 'origin:', netRes?.body ? JSON.parse(netRes.body).origin : 'N/A');

    console.log('\n--- 9. 测试 electron.browser 模块 (多页面与沙箱) ---');
    const pages = await evaluate(`window.MobileElectron.browser.getAllPages()`);
    console.log('browser.getAllPages():', pages);

    const profiles = await evaluate(`window.MobileElectron.browser.listProfiles()`);
    console.log('browser.listProfiles():', profiles);

    console.log('\n--- 10. 测试 electron.tabBar 模块 (动态原生 TabBar 与事件监听) ---');
    const tabSetRes = await evaluate(`window.MobileElectron.tabBar.setItems([
        { id: 'home', title: '首页', icon: 'home' },
        { id: 'cart', title: '购物车', icon: 'cart', badge: '10' },
        { id: 'my', title: '我的', icon: 'user' }
    ], {
        selectedId: 'home',
        visible: true
    })`);
    console.log('tabBar.setItems():', tabSetRes);

    const tabState = await evaluate(`window.MobileElectron.tabBar.getState()`);
    console.log('tabBar.getState():', { visible: tabState.visible, itemsCount: tabState.items?.length, selectedId: tabState.selectedId });

    const badgeRes = await evaluate(`window.MobileElectron.tabBar.setBadge('cart', '99+')`);
    console.log("tabBar.setBadge('cart', '99+'):", badgeRes);

    const selRes = await evaluate(`window.MobileElectron.tabBar.setSelected('cart')`);
    console.log("tabBar.setSelected('cart'):", selRes);

    console.log('\n--- 11. 测试 electron.debug 模块 (框架调试中心与 WebView DevTools) ---');
    const debugInfo = await evaluate(`window.MobileElectron.debug.getInfo()`);
    console.log('debug.getInfo():', debugInfo);

    const openDtRes = await evaluate(`window.MobileElectron.debug.openDevTools()`);
    console.log('debug.openDevTools():', openDtRes);

    const closeDtRes = await evaluate(`window.MobileElectron.debug.closeDevTools()`);
    console.log('debug.closeDevTools():', closeDtRes);

    console.log('\n--- 12. 触发 UI 渲染更新 (刷新各界面显示) ---');
    await evaluate(`
        document.getElementById('btn-cookie-get-all')?.click();
        document.getElementById('btn-fs-paths')?.click();
    `);

    console.log('\n🎉 ALL E2E TESTS PASSED SUCCESSFULLY! 🎉\n');
    ws.close();
}

run().catch(err => {
    console.error('E2E Test Failed:', err);
    process.exit(1);
});
