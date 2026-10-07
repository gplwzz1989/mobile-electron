/**
 * @file build.js
 * @description 构建打包脚本：输出 ESM、CJS、UMD 产物并生成 TypeScript 类型声明
 */

const esbuild = require('esbuild');
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const distDir = path.resolve(__dirname, 'dist');
if (!fs.existsSync(distDir)) {
    fs.mkdirSync(distDir, { recursive: true });
}

async function build() {
    console.log('🚀 开始构建 @mobile-electron/core SDK...');

    // 1. 构建 ESM 产物 (供 Vue / React / Vite 等现代工程 import 使用)
    console.log('📦 打包 ESM 模块 -> dist/index.esm.js');
    await esbuild.build({
        entryPoints: [path.resolve(__dirname, 'src/index.ts')],
        bundle: true,
        format: 'esm',
        target: ['es2020'],
        outfile: path.resolve(distDir, 'index.esm.js'),
        sourcemap: true
    });

    // 2. 构建 CommonJS 产物 (供 Node / Webpack 传统工程使用)
    console.log('📦 打包 CommonJS 模块 -> dist/index.cjs');
    await esbuild.build({
        entryPoints: [path.resolve(__dirname, 'src/index.ts')],
        bundle: true,
        format: 'cjs',
        target: ['es2020'],
        outfile: path.resolve(distDir, 'index.cjs'),
        sourcemap: true
    });

    // 3. 构建 UMD / IIFE 浏览器直接引用产物 (供 HTML <script src="..."> 全局挂载)
    console.log('📦 打包 UMD 独立脚本 -> dist/mobile-electron-sdk.umd.js');
    await esbuild.build({
        entryPoints: [path.resolve(__dirname, 'src/index.ts')],
        bundle: true,
        format: 'iife',
        globalName: 'MobileElectronFacade',
        footer: {
            js: 'if (typeof window !== "undefined" && MobileElectronFacade) { window.MobileElectron = MobileElectronFacade.electron || MobileElectronFacade.default; window.mobileElectron = window.MobileElectron; }'
        },
        target: ['es2018'],
        outfile: path.resolve(distDir, 'mobile-electron-sdk.umd.js'),
        minify: false,
        sourcemap: true
    });

    // 4. 生成 TypeScript 类型声明 (.d.ts)
    console.log('📝 生成 TypeScript 类型声明 -> dist/index.d.ts');
    try {
        execSync('npx tsc', { cwd: __dirname, stdio: 'inherit' });
    } catch (e) {
        console.warn('⚠️ tsc 生成声明文件警告:', e.message);
    }

    // 5. 同步一份到 Android app/src/main/assets/dist/ 供内置页面直接使用
    const androidAssetsDist = path.resolve(__dirname, '../app/src/main/assets/dist');
    if (!fs.existsSync(androidAssetsDist)) {
        fs.mkdirSync(androidAssetsDist, { recursive: true });
    }
    const umdSrc = path.resolve(distDir, 'mobile-electron-sdk.umd.js');
    const umdDest = path.resolve(androidAssetsDist, 'mobile-electron-sdk.js');
    if (fs.existsSync(umdSrc)) {
        fs.copyFileSync(umdSrc, umdDest);
        console.log(`✅ 同步产物至原生资产目录: ${umdDest}`);
    }

    console.log('🎉 @mobile-electron/core SDK 构建完成！');
}

build().catch(err => {
    console.error('❌ 构建失败:', err);
    process.exit(1);
});
