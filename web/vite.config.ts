import { defineConfig } from 'vite';
import path from 'path';

export default defineConfig({
  base: './',
  server: {
    host: '0.0.0.0',
    port: 5173,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*'
    }
  },
  resolve: {
    alias: {
      '@mobile-electron/core': path.resolve(__dirname, '../sdk/dist/index.esm.js')
    }
  }
});
