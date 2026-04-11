import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const trimTrailingSlash = (value) => value.replace(/\/+$/, '')

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = trimTrailingSlash(env.VITE_API_PROXY_TARGET || 'http://localhost:9000')

  return {
    plugins: [
      vue(),
      AutoImport({
        dts: false,
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      }),
      Components({
        dts: false,
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      }),
    ],
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return undefined
            if (id.includes('node_modules/echarts')) return 'report-charts'
            if (id.includes('node_modules/zrender')) return 'chart-renderer'
            if (id.includes('node_modules/element-plus')) return 'element-plus'
            if (id.includes('node_modules/vue') || id.includes('node_modules/@vue')) return 'vue-vendor'
            if (id.includes('node_modules/axios')) return 'http-vendor'
            return undefined
          },
        },
      },
    },
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
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.js',
      restoreMocks: true,
    },
  }
})
