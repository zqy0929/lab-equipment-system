import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境将 /api 代理到后端 8080 端口
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    },
    // 启动即预编译所有页面组件，消除首次切换菜单时的按需编译延迟
    warmup: {
      clientFiles: [
        './src/main.js',
        './src/App.vue',
        './src/views/**/*.vue',
        './src/components/**/*.vue'
      ]
    }
  }
})
