import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const trimTrailingSlash = (value) => value.replace(/\/+$/, '')

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = trimTrailingSlash(env.VITE_API_PROXY_TARGET || 'http://localhost:9000')

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
