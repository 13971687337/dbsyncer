import compression from 'vite-plugin-compression'
import type { Plugin } from 'vite'

export default function createCompression(env: Record<string, string>): Plugin[] {
  const { VITE_BUILD_COMPRESS } = env
  const plugins: Plugin[] = []
  if (VITE_BUILD_COMPRESS) {
    const compressList = VITE_BUILD_COMPRESS.split(',')
    if (compressList.includes('gzip')) {
      plugins.push(compression({ ext: '.gz', deleteOriginFile: false }) as Plugin)
    }
    if (compressList.includes('brotli')) {
      plugins.push(compression({ ext: '.br', algorithm: 'brotliCompress', deleteOriginFile: false }) as Plugin)
    }
  }
  return plugins
}
