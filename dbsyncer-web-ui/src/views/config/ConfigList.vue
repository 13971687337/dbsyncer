<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">配置管理</span>
        </div>
      </template>
      <el-row :gutter="24">
        <el-col :span="12">
          <h4 class="section-title">配置列表</h4>
          <p class="hint-text">
            导出所有配置，请点击
            <el-button type="primary" link size="small" @click="handleDownload">下载</el-button>
          </p>
          <el-table :data="configs" stripe size="small">
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="primary">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="updateTime" label="修改时间" width="170" />
          </el-table>
        </el-col>
        <el-col :span="12">
          <div class="upload-section">
            <el-upload
              :action="uploadUrl"
              :headers="uploadHeaders"
              accept=".json"
              :limit="5"
              :on-success="handleUploadSuccess"
              drag
              multiple
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">点击或拖拽 JSON 文件到此处上传</div>
              <template #tip>
                <div class="el-upload__tip">支持 .json 文件，最多上传 5 个</div>
              </template>
            </el-upload>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, getCurrentInstance } from 'vue'
import { getConfig } from '@/api/config'
import { getToken } from '@/utils/auth'

const { proxy } = getCurrentInstance()
const configs = ref<any[]>([])
const uploadUrl = (import.meta as any).env.VITE_APP_BASE_API + '/config/upload'
const uploadHeaders = ref<Record<string, string>>({ Authorization: 'Bearer ' + getToken() })

onMounted(async () => {
  try {
    const res: any = await getConfig()
    if (res?.data) configs.value = res.data || []
  } catch { /* ignore */ }
})

function handleDownload() {
  window.open((import.meta as any).env.VITE_APP_BASE_API + '/config/download')
}

function handleUploadSuccess() {
  proxy.$modal.msgSuccess('上传成功')
  getConfig().then((res: any) => { if (res?.data) configs.value = res.data || [] })
}
</script>

<style scoped>
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; }
.section-title { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
.hint-text { font-size: 12px; color: #8c8c8c; margin-bottom: 12px; }
.upload-section { padding-top: 28px; }
</style>
