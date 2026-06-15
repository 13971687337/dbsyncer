<template>
  <div class="page">
    <!-- 邮件通知配置 -->
    <el-card shadow="never" class="mb-16">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">邮件通知配置</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="160px" label-position="left">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="启用邮件通知">
              <el-switch v-model="form.enabled" />
              <el-tooltip content="开启后，系统将在触发告警规则时向配置的接收人发送邮件通知" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SMTP服务器">
              <el-input v-model="form.smtpHost" placeholder="smtp.qq.com" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发件邮箱">
              <el-input v-model="form.username" placeholder="example@qq.com" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授权码">
              <el-input v-model="form.password" type="password" show-password placeholder="邮箱SMTP授权码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-button size="small" :loading="testing" @click="handleTest" style="margin-left:160px">
          测试发送
        </el-button>
      </el-form>
    </el-card>

    <!-- 告警规则 -->
    <el-card shadow="never" class="mb-16">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">告警规则</span>
        </div>
      </template>
      <el-form label-width="160px" label-position="left">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="同步失败告警">
              <el-switch v-model="form.notifyOnFailure" />
              <span class="rule-desc">每隔10分钟扫描运行中的驱动，出现失败记录时发送通知</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手动停止驱动告警">
              <el-switch v-model="form.notifyOnStop" />
              <span class="rule-desc">用户手动停止驱动时发送通知</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 接收人管理 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">通知接收人</span>
          <span class="card-subtitle">系统将向以下用户的邮箱发送告警通知</span>
        </div>
      </template>
      <el-table :data="recipients" stripe size="small">
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="nickName" label="昵称" width="150" />
        <el-table-column prop="email" label="邮箱" min-width="250">
          <template #default="{ row }">
            <template v-if="row.email">
              <el-tag v-for="(mail, idx) in row.emailList" :key="idx" size="small" class="email-tag">{{ mail }}</el-tag>
            </template>
            <span v-else class="text-muted">未配置邮箱</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.email ? 'success' : 'info'" size="small">{{ row.email ? '可接收' : '未配置' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getNotifyConfig, saveNotifyConfig, testNotify } from '@/api/notify'
import { searchUser } from '@/api/user'

const saving = ref(false)
const testing = ref(false)
const recipients = ref<any[]>([])

const form = reactive<Record<string, any>>({
  enabled: false,
  smtpHost: 'smtp.qq.com',
  username: '',
  password: '',
  notifyOnFailure: true,
  notifyOnStop: true,
})

onMounted(async () => {
  try {
    const res: any = await getNotifyConfig()
    if (res?.data) {
      const d = res.data
      Object.keys(form).forEach(k => {
        if (d[k] !== undefined) (form as any)[k] = d[k]
      })
    }
  } catch { /* ignore */ }
  try {
    const res: any = await searchUser({ pageNum: 1, pageSize: 100 })
    if (res?.data?.data) {
      recipients.value = res.data.data.map((u: any) => ({
        ...u,
        emailList: u.email ? u.email.split(',').filter(Boolean) : [],
      }))
    }
  } catch { /* ignore */ }
})

async function handleSave() {
  saving.value = true
  try {
    await saveNotifyConfig(form as Record<string, any>)
    ElMessage.success('保存成功')
  } catch { /* ignore */ } finally { saving.value = false }
}

async function handleTest() {
  if (!form.username || !form.password) {
    ElMessage.warning('请先填写发件邮箱和授权码')
    return
  }
  testing.value = true
  try {
    await testNotify()
    ElMessage.success('测试邮件发送成功，请检查收件箱')
  } catch { /* ignore */ } finally { testing.value = false }
}
</script>

<style scoped>
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; }
.card-subtitle { font-size: 12px; color: #8c8c8c; }
.help-icon { margin-left: 4px; color: #8c8c8c; cursor: help; }
.rule-desc { font-size: 12px; color: #8c8c8c; margin-left: 8px; }
.email-tag { margin-right: 4px; }
.text-muted { color: #bfbfbf; }
.mb-16 { margin-bottom: 16px; }
</style>
