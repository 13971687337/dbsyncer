import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/login': 'http://127.0.0.1:18686',
      '/connector': 'http://127.0.0.1:18686',
      '/mapping': 'http://127.0.0.1:18686',
      '/monitor': 'http://127.0.0.1:18686',
      '/task': 'http://127.0.0.1:18686',
      '/user': 'http://127.0.0.1:18686',
      '/plugin': 'http://127.0.0.1:18686',
      '/config': 'http://127.0.0.1:18686',
      '/system': 'http://127.0.0.1:18686',
      '/openapi': 'http://127.0.0.1:18686',
      '/app': 'http://127.0.0.1:18686',
      '/index': 'http://127.0.0.1:18686',
      '/tableGroup': 'http://127.0.0.1:18686',
    },
  },
})
