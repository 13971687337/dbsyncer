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
      '^/(login|connector|mapping|monitor|task|user|plugin|config|system|openapi|app|index|tableGroup)': {
        target: 'http://127.0.0.1:18686',
        changeOrigin: true,
      },
    },
  },
})
