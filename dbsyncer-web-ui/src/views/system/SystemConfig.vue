<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">系统配置</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="200px" label-position="left">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="记录同步成功数据">
              <el-switch v-model="form.enableStorageWriteSuccess" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="记录全量数据">
              <el-switch v-model="form.enableStorageWriteFull" />
              <el-tooltip content="不推荐在生产环境下开启，可在源库数据量较少时使用" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="记录同步失败数据">
              <el-switch v-model="form.enableStorageWriteFail" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="记录失败日志长度" required>
              <el-input-number v-model="form.maxStorageErrorLength" :min="1024" :max="8192" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="表执行器上限数" required>
              <el-input-number v-model="form.maxBufferActuatorSize" :min="1" :max="200" />
              <el-tooltip content="每新增一张驱动表的映射关系，就会单独新开一个表执行器处理，超过限制后使用通用执行器" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="同步数据过期时间(天)" required>
              <el-input-number v-model="form.expireDataDays" :min="1" :max="180" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="系统日志过期时间(天)" required>
              <el-input-number v-model="form.expireLogDays" :min="1" :max="180" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="打印trace信息">
              <el-switch v-model="form.enablePrintTraceInfo" />
              <el-tooltip content="仅用于排查问题，生产环境建议关闭" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="水印">
              <el-switch v-model="form.enableWatermark" />
              <el-tooltip content="刷新页面生效" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.enableWatermark">
            <el-form-item label="水印内容">
              <el-input v-model="form.watermark" maxlength="64" placeholder="请输入水印(最多64个字)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开放API">
              <el-switch v-model="form.enableRsaConfig" />
              <el-tooltip content="外部业务系统可凭此配置生成token，直接调用DBSyncer的管理接口" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="form.enableRsaConfig">
          <el-divider />
          <el-row :gutter="24">
            <el-col :span="8">
              <el-form-item label="密钥长度">
                <el-input-number v-model="form.rsaKeyLength" :min="1024" :max="8192" :step="1024" />
                <el-button style="margin-left:8px" @click="handleGenerateRSA" :loading="generating">生成RSA</el-button>
              </el-form-item>
            </el-col>
            <el-col :span="16" />
            <el-col :span="12">
              <el-form-item label="RSA公钥">
                <el-input v-model="form.rsaPublicKey" type="textarea" :rows="4" readonly />
                <el-button size="small" style="margin-top:4px" @click="copyText(form.rsaPublicKey, 'RSA公钥')">复制</el-button>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="RSA私钥">
                <el-input v-model="form.rsaPrivateKey" type="textarea" :rows="6" readonly />
                <el-button size="small" style="margin-top:4px" @click="copyText(form.rsaPrivateKey, 'RSA私钥')">复制</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, getCurrentInstance } from 'vue'
import { getSystemInfo, editSystem, generateRSA } from '@/api/system'

const { proxy } = getCurrentInstance()

const saving = ref(false)
const generating = ref(false)

const form = reactive<Record<string, any>>({
  enableStorageWriteSuccess: false,
  enableStorageWriteFull: false,
  enableStorageWriteFail: false,
  maxStorageErrorLength: 1024,
  maxBufferActuatorSize: 50,
  expireDataDays: 30,
  expireLogDays: 30,
  enablePrintTraceInfo: false,
  enableWatermark: false,
  watermark: '',
  enableRsaConfig: false,
  rsaKeyLength: 2048,
  rsaPublicKey: '',
  rsaPrivateKey: '',
})

onMounted(async () => {
  try {
    const res: any = await getSystemInfo()
    if (res?.data) {
      const d = res.data
      Object.keys(form).forEach(k => {
        if (d[k] !== undefined) (form as any)[k] = d[k]
      })
      if (d.rsaConfig) {
        form.rsaKeyLength = d.rsaConfig.keyLength || 2048
        form.rsaPublicKey = d.rsaConfig.publicKey || ''
        form.rsaPrivateKey = d.rsaConfig.privateKey || ''
      }
    }
  } catch { /* ignore */ }
})

function handleSave() {
  saving.value = true
  editSystem(form as Record<string, any>).then(() => {
    proxy.$modal.msgSuccess('修改成功')
  }).catch(() => {}).finally(() => { saving.value = false })
}

function handleGenerateRSA() {
  generating.value = true
  generateRSA().then((res: any) => {
    if (res?.data) {
      form.rsaPublicKey = res.data.publicKey || ''
      form.rsaPrivateKey = res.data.privateKey || ''
      proxy.$modal.msgSuccess('生成成功')
    }
  }).catch(() => {}).finally(() => { generating.value = false })
}

function copyText(text: string, label: string) {
  if (!text) { proxy.$modal.msgWarning(label + '为空'); return }
  navigator.clipboard.writeText(text).then(() => proxy.$modal.msgSuccess('复制' + label + '成功'))
}
</script>

<style scoped>
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; }
.help-icon { margin-left: 4px; color: #8c8c8c; cursor: help; }
</style>
