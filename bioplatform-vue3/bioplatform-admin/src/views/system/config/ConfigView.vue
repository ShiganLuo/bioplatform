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

        <el-tab-pane label="LLM 配置" name="llm">
          <el-form :model="llmConfig" label-width="120px" class="config-form">
            <el-form-item label="LLM 提供商">
              <el-select v-model="llmConfig.provider" placeholder="选择提供商" @change="handleProviderChange" style="width: 100%">
                <el-option
                  v-for="p in llmProviders"
                  :key="p.key"
                  :label="p.name"
                  :value="p.key"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="模型名称">
              <div style="display: flex; gap: 8px; width: 100%">
                <el-select
                  v-model="llmConfig.model"
                  placeholder="选择或输入模型"
                  filterable
                  allow-create
                  style="flex: 1"
                >
                  <el-option
                    v-for="m in availableModels"
                    :key="m"
                    :label="m"
                    :value="m"
                  />
                </el-select>
                <el-button @click="handleFetchModels" :loading="fetchingModels" :disabled="!llmConfig.baseUrl || !llmConfig.apiKey">
                  获取模型
                </el-button>
              </div>
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="llmConfig.apiKey" type="password" show-password placeholder="sk-..." @input="apiKeyDirty = true" />
              <p style="font-size: 12px; color: #909399; margin-top: 4px;">切换提供商后请确认 API Key 是否匹配</p>
            </el-form-item>
            <el-form-item label="API Base URL">
              <el-input v-model="llmConfig.baseUrl" placeholder="根据提供商自动填充" />
              <p style="font-size: 12px; color: #909399; margin-top: 4px;">选择提供商后自动填充，也可手动修改</p>
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
import { getConfigs, updateConfig, fetchLlmModels } from '@/api/systemApi'
import { encrypt, isEncrypted } from '@/utils/crypto'

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

const llmConfig = reactive({
  provider: '',
  baseUrl: '',
  apiKey: '',
  model: ''
})
// 标记 API Key 是否被用户实际修改过
const apiKeyDirty = ref(false)
// 提供商列表（硬编码）
const llmProviders = [
  { key: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1' },
  { key: 'mimo', name: 'Xiaomi MiMo', baseUrl: 'https://token-plan-cn.xiaomimimo.com/v1' },
  { key: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1' },
  { key: 'qwen', name: '通义千问', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { key: 'zhipu', name: '智谱 GLM', baseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { key: 'custom', name: '自定义', baseUrl: '' },
]
// 动态模型列表（从 API 拉取）
const availableModels = ref<string[]>([])
const fetchingModels = ref(false)

const loadConfigs = async () => {
  try {
    const res = await getConfigs()
    const configs = res as any[]
    configs.forEach((config: any) => {
      const configKey = config.configKey || config.key
      const configValue = config.configValue || config.value
      // LLM 配置使用扁平 key
      if (configKey === 'llm_api_key') {
        llmConfig.apiKey = configValue || ''
      } else if (configKey === 'llm_model') {
        llmConfig.model = configValue || ''
      } else if (configKey === 'llm_base_url') {
        llmConfig.baseUrl = configValue || ''
      } else if (configKey === 'llm_provider') {
        llmConfig.provider = configValue || ''
      } else {
        const [category, key] = configKey.split('.')
        if (category === 'basic' && key in basicConfig) {
          (basicConfig as any)[key] = configValue
        } else if (category === 'security' && key in securityConfig) {
          (securityConfig as any)[key] = configValue
        } else if (category === 'notification' && key in notificationConfig) {
          (notificationConfig as any)[key] = configValue
        } else if (category === 'execution' && key in executionConfig) {
          (executionConfig as any)[key] = configValue
        }
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
      })),
      // LLM 配置使用扁平 key，与数据库一致
      { key: 'llm_provider', value: llmConfig.provider },
      { key: 'llm_base_url', value: llmConfig.baseUrl },
      { key: 'llm_model', value: llmConfig.model },
    ]
    // API Key 只在用户实际修改时才发送，且前端加密后再传输
    if (llmConfig.apiKey && llmConfig.apiKey.includes('***')) {
      ElMessage.warning('API Key 为遮蔽值未更新，请先输入真实的 API Key 再保存')
    } else if (apiKeyDirty.value && llmConfig.apiKey) {
      const encryptedKey = await encrypt(llmConfig.apiKey)
      allConfigs.push({ key: 'llm_api_key', value: encryptedKey })
    }

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

onMounted(async () => {
  loadConfigs()
})

function handleProviderChange(providerKey: string) {
  const p = llmProviders.find(p => p.key === providerKey)
  if (p) {
    llmConfig.baseUrl = p.baseUrl
  }
  llmConfig.model = ''
  availableModels.value = []
}

async function handleFetchModels() {
  fetchingModels.value = true
  try {
    const models = await fetchLlmModels({ baseUrl: llmConfig.baseUrl, apiKey: llmConfig.apiKey })
    availableModels.value = (models as any) || []
    if (availableModels.value.length > 0 && !llmConfig.model) {
      llmConfig.model = availableModels.value[0]
    }
    ElMessage.success(`获取到 ${availableModels.value.length} 个模型`)
  } catch (e: any) {
    ElMessage.error(e?.message || '获取模型列表失败')
  } finally {
    fetchingModels.value = false
  }
}
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
