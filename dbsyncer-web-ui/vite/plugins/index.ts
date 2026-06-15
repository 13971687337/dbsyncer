import vue from '@vitejs/plugin-vue'
import createAutoImport from './auto-import'
import createCompression from './compression'
import createSetupExtend from './setup-extend'

export default function createVitePlugins(viteEnv: Record<string, string>, isBuild: boolean) {
  const vitePlugins = [vue()]
  vitePlugins.push(createAutoImport())
  vitePlugins.push(createSetupExtend())
  if (isBuild) {
    vitePlugins.push(...createCompression(viteEnv))
  }
  return vitePlugins
}
