import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import fs from 'fs'

/**
 * 尝试加载本地 https 证书，若不存在则自动回落为 http。
 * 证书路径可通过环境变量 VITE_DEV_SSL_KEY / VITE_DEV_SSL_CERT 指定，
 * 默认读取项目内 certs/localhost-key.pem 与 certs/localhost.pem。
 */
const resolveCertFile = (customPath) => {
  if (!customPath) {
    return null
  }
  const absolutePath = path.isAbsolute(customPath)
    ? customPath
    : path.resolve(__dirname, customPath)
  return fs.existsSync(absolutePath) ? absolutePath : null
}

const loadHttpsOptions = () => {
  if (process.env.VITE_DEV_HTTPS === 'false') {
    return false
  }

  const keyPath =
    resolveCertFile(process.env.VITE_DEV_SSL_KEY) ??
    resolveCertFile('./certs/localhost-key.pem')
  const certPath =
    resolveCertFile(process.env.VITE_DEV_SSL_CERT) ??
    resolveCertFile('./certs/localhost.pem')

  if (keyPath && certPath) {
    return {
      key: fs.readFileSync(keyPath),
      cert: fs.readFileSync(certPath),
    }
  }
  return false
}

const httpsOptions = loadHttpsOptions()

// https://vite.dev/config/
export default defineConfig({
  base: '/shop/',
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    https: httpsOptions || undefined,
    proxy: {
      // API接口代理（注意：base 是 /shop/，所以请求路径是 /shop/api/...）
      // ws: true 让该代理同时转发 WebSocket 升级请求（ASR 语音识别 /api/asr/stream 走这里）
      '/shop/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/shop/, ''),
      },
      // 静态资源代理（上传的图片，注意 base 是 /shop/）
      '/shop/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/shop/, ''),
      },
      // 绝对 /api 前缀代理：覆盖商品列表页 getImageUrl()/upload action 使用的
      // 绝对路径（如 /api/uploads/xxx.jpg、/api/product/uploadImage）。
      // 后端 context-path 为 /api，故直接转发、不做 rewrite，使
      // /api/uploads/** -> http://localhost:8081/api/uploads/** 命中后端静态资源处理器。
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
