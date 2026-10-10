import { defineConfig } from 'vite';

export default defineConfig({
  base: './', // 相对路径寻址，保证在 Android/iOS 本地离线沙箱中正常加载
  server: {
    port: 5173,
    host: true
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false
  }
});
