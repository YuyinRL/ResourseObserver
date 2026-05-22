import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'

// 构建输出直接落到 Mod 资源目录，执行 `npm run build` 后资源服务器即可托管。
// 使用 `base: './'` 让产物里的 <script> / 样式表路径为相对路径，
// 这样无论是否通过子路径访问都能正常加载。
export default defineConfig({
  base: './',
  plugins: [
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: path.resolve(__dirname, '../src/main/resources/assets/resourceobserver/web'),
    emptyOutDir: true,
    // 将 chunk 压缩上限放宽（MUI + Radix 基础包较大）
    chunkSizeWarningLimit: 1500,
  },
  server: {
    // dev server 代理，把 /api 转发到 Minecraft 服务端 8877 端口
    proxy: {
      '/api': 'http://127.0.0.1:8877',
    },
  },
  assetsInclude: ['**/*.svg', '**/*.csv'],
})

