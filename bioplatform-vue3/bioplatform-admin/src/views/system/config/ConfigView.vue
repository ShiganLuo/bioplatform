<template>
  <div class="config-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>系统配置</span>
          <el-button type="primary" @click="handleSave" :loading="saving">
            <el-icon><Check /></el-icon>
            保存配置
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-click="handleTabClick">
        <el-tab-pane label="基础配置" name="basic">
          <el-form :model="basicConfig" label-width="120px" class="config-form">
            <el-form-item label="平台名称">
              <el-input v-model="basicConfig.platformName" />
            </el-form-item>
            <el-form-item label="平台描述">
              <el-input
                v-model="basicConfig.platformDescription"
                type="textarea"
                :rows="3"
              />
            </el-form-item>
            <el-form-item label="管理员邮箱">
              <el-input v-model="basicConfig.adminEmail" />
            </el-form-item>
            <el-form-item label="文件存储路径">
              <el-input v-model="basicConfig.storagePath" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="安全配置" name="security">
          <el-form :model="securityConfig" label-width="120px" class="config-form">
            <el-form-item label="Token 过期时间">
              <el-input-number v-model="securityConfig.tokenExpireMinutes" :min="5" :max="1440" />
              <span style="margin-left: 8px; color: #909399;">分钟</span>
            </el-form-item>
            <el-form-item label="登录失败锁定">
              <el-switch v-model="securityConfig.loginLockEnabled" />
            </el-form-item>
            <el-form-item label="最大失败次数">
              <el-input-number v-model="securityConfig.maxLoginAttempts" :min="3" :max="10" />
            </el-form-item>
            <el-form-item label="密码最小长度">
              <el-input-number v-model="securityConfig.minPasswordLength" :min="6" :max="32" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="通知配置" name="notification">
          <el-form :model="notificationConfig" label-width="120px" class="config-form">
            <el-form-item label="启用邮件通知">
              <el-switch v-model="notificationConfig.emailEnabled" />
            </el-form-item>
            <el-form-item label="SMTP 服务器">
              <el-input v-model="notificationConfig.smtpHost" :disabled="!notificationConfig.emailEnabled" />
            </el-form-item>
            <el-form-item label="SMTP 端口">
              <el-input-number v-model="notificationConfig.smtpPort" :disabled="!notificationConfig.emailEnabled" />
            </el-form-item>
            <el-form-item label="发件人邮箱">
              <el-input v-model="notificationConfig.senderEmail" :disabled="!notificationConfig.emailEnabled" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="执行配置" name="execution">
          <el-form :model="executionConfig" label-width="120px" class="config-form">
            <el-form-item label="最大并发数">
              <el-input-number v-model="executionConfig.maxConcurrency" :min="1" :max="100" />
            </el-form-item>
            <el-form-item label="超时时间">
              <el-input-number v-model="executionConfig.timeoutMinutes" :min="10" :max="1440" />
              <span style="margin-left: 8px; color: #909399;">分钟</span>
            </el-form-item>
            <el-form-item label="自动清理">
              <el-switch v-model="executionConfig.autoCleanup" />
            </el-form-item>
            <el-form-item label="保留天数">
              <el-input-number v-model="executionConfig.retentionDays" :min="1" :max="365" :disabled="!executionConfig.autoCleanup" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { getConfigs, updateConfig } from '@/api/systemApi'

const saving = ref(false)
const activeTab = ref('basic')

const basicConfig = reactive({
  platformName: '生物信息学云平台',
  platformDescription: '一站式生物信息学数据分析云平台',
  adminEmail: 'admin@bioplatform.com',
  storagePath: '/data/bioplatform/storage'
})

const securityConfig = reactive({
  tokenExpireMinutes: 60,
  loginLockEnabled: true,
  maxLoginAttempts: 5,
  minPasswordLength: 8
})

const notificationConfig = reactive({
  emailEnabled: false,
  smtpHost: 'smtp.example.com',
  smtpPort: 587,
  senderEmail: 'noreply@bioplatform.com'
})

const executionConfig = reactive({
  maxConcurrency: 10,
  timeoutMinutes: 120,
  autoCleanup: true,
  retentionDays: 30
})

const loadConfigs = async () => {
  try {
    const res = await getConfigs()
    // Parse and assign config values
    const configs = res as any[]
    configs.forEach((config: any) => {
      const [category, key] = config.key.split('.')
      if (category === 'basic' && key in basicConfig) {
        (basicConfig as any)[key] = config.value
      } else if (category === 'security' && key in securityConfig) {
        (securityConfig as any)[key] = config.value
      } else if (category === 'notification' && key in notificationConfig) {
        (notificationConfig as any)[key] = config.value
      } else if (category === 'execution' && key in executionConfig) {
        (executionConfig as any)[key] = config.value
      }
    })
  } catch (error) {
    console.error('Failed to load configs:', error)
  }
}

const handleTabClick = () => {
  // Tab switch handler if needed
}

const handleSave = async () => {
  saving.value = true
  try {
    // Save all configs
    const allConfigs = [
      ...Object.entries(basicConfig).map(([key, value]) => ({
        key: `basic.${key}`,
        value: String(value)
      })),
      ...Object.entries(securityConfig).map(([key, value]) => ({
        key: `security.${key}`,
        value: String(value)
      })),
      ...Object.entries(notificationConfig).map(([key, value]) => ({
        key: `notification.${key}`,
        value: String(value)
      })),
      ...Object.entries(executionConfig).map(([key, value]) => ({
        key: `execution.${key}`,
        value: String(value)
      }))
    ]

    // Update configs in sequence (or batch if API supports)
    for (const config of allConfigs) {
      try {
        await updateConfig(0, { key: config.key, value: config.value } as any)
      } catch (e) {
        // Config might not exist yet, continue
      }
    }

    ElMessage.success('配置保存成功')
  } catch (error) {
    ElMessage.error('配置保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.config-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-form {
  max-width: 600px;
}
</style>
