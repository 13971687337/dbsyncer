<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">插件管理</span>
        </div>
      </template>
      <p class="hint-text">上传插件后，选择同步任务，进入高级配置关联插件。</p>
      <el-table :data="plugins" stripe size="small">
        <el-table-column prop="name" label="名称">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.unmodifiable" size="small" type="info" class="ml-1">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mappingName" label="运行驱动" />
        <el-table-column prop="className" label="类名" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" show-overflow-tooltip />
      </el-table>

      <el-divider />

      <div class="upload-section">
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          accept=".jar"
          :limit="5"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          drag
          multiple
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">点击或拖拽 JAR 文件到此处上传</div>
          <template #tip>
            <div class="el-upload__tip">支持 .jar 文件，最多上传 5 个</div>
          </template>
        </el-upload>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, getCurrentInstance } from 'vue'
import { getPlugins } from '@/api/plugin'
import { getToken } from '@/utils/auth'

const { proxy } = getCurrentInstance()
const plugins = ref<any[]>([])
const uploadUrl = (import.meta as any).env.VITE_APP_BASE_API + '/plugin/upload'
const uploadHeaders = ref<Record<string, string>>({ Authorization: 'Bearer ' + getToken() })

onMounted(async () => {
  try {
    const res: any = await getPlugins()
    if (res?.data) plugins.value = res.data || []
  } catch { /* ignore */ }
})

function handleUploadSuccess() {
  proxy.$modal.msgSuccess('插件上传成功')
  getPlugins().then((res: any) => { if (res?.data) plugins.value = res.data || [] })
}
function handleUploadError() {
  proxy.$modal.msgError('上传失败')
}
</script>

<style scoped>
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; }
.hint-text { font-size: 12px; color: #8c8c8c; margin-bottom: 12px; }
.upload-section { margin-top: 8px; }
.ml-1 { margin-left: 4px; }
</style>
