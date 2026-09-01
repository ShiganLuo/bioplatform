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

        <el-tab-pane label="联系信息" name="contact">
          <el-form :model="contactConfig" label-width="120px" class="config-form">
            <el-form-item label="联系邮箱">
              <el-input v-model="contactConfig.contactEmail" placeholder="support@example.com" />
            </el-form-item>
            <el-form-item label="GitHub 地址">
              <el-input v-model="contactConfig.githubUrl" placeholder="https://github.com/..." />
            </el-form-item>
            <el-form-item label="平台描述">
              <el-input v-model="contactConfig.siteDescription" type="textarea" :rows="2" />
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
              <el-input v-model="llmConfig.apiKey" type="password" show-password placeholder="sk-..." />
              <p style="font-size: 12px; color: #909399; margin-top: 4px;">
                已配置时显示遮蔽值，输入新值后保存即可更新
              </p>
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

// 原始值快照，用于比对哪些配置被修改过
let originalSnapshot: Record<string, string> = {}

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

const contactConfig = reactive({
  contactEmail: 'support@bioplatform.com',
  githubUrl: '',
  siteDescription: '一站式生物信息学分析云平台'
})

const llmConfig = reactive({
  provider: '',
  baseUrl: '',
  apiKey: '',
  model: ''
})
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

/** 收集当前所有配置的 key-value 快照 */
function collectSnapshot(): Record<string, string> {
  const snap: Record<string, string> = {}
  for (const [k, v] of Object.entries(basicConfig)) snap[`basic.${k}`] = String(v)
  for (const [k, v] of Object.entries(securityConfig)) snap[`security.${k}`] = String(v)
  for (const [k, v] of Object.entries(notificationConfig)) snap[`notification.${k}`] = String(v)
  for (const [k, v] of Object.entries(executionConfig)) snap[`execution.${k}`] = String(v)
  snap['llm_provider'] = llmConfig.provider
  snap['llm_base_url'] = llmConfig.baseUrl
  snap['llm_model'] = llmConfig.model
  snap['llm_api_key'] = llmConfig.apiKey
  snap['site_contact_email'] = contactConfig.contactEmail
  snap['site_github_url'] = contactConfig.githubUrl
  snap['site_description'] = contactConfig.siteDescription
  return snap
}

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
      } else if (configKey === 'site_contact_email') {
        contactConfig.contactEmail = configValue || ''
      } else if (configKey === 'site_github_url') {
        contactConfig.githubUrl = configValue || ''
      } else if (configKey === 'site_description') {
        contactConfig.siteDescription = configValue || ''
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
    // 加载完成后保存快照
    originalSnapshot = collectSnapshot()
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
    // 收集当前快照，与原始快照比对找出变化项
    const currentSnapshot = collectSnapshot()
    const changedConfigs: { key: string; value: string }[] = []

    for (const [key, value] of Object.entries(currentSnapshot)) {
      if (originalSnapshot[key] !== value) {
        // API Key 含遮蔽值说明未修改，跳过
        if (key === 'llm_api_key' && value.includes('***')) continue
        changedConfigs.push({ key, value })
      }
    }

    if (changedConfigs.length === 0) {
      ElMessage.info('没有配置被修改')
      saving.value = false
      return
    }

    for (const config of changedConfigs) {
      try {
        // API Key 需加密后传输
        let sendValue = config.value
        if (config.key === 'llm_api_key') {
          sendValue = await encrypt(config.value)
        }
        await updateConfig(0, { key: config.key, value: sendValue } as any)
      } catch (e) {
        // Config might not exist yet, continue
      }
    }

    // 保存成功后更新快照
    originalSnapshot = collectSnapshot()
    ElMessage.success(`配置保存成功（更新了 ${changedConfigs.length} 项）`)
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
    // 如果API Key不是遮蔽值，先加密再发送
    let keyToSend = llmConfig.apiKey
    if (keyToSend && !keyToSend.includes('***')) {
      keyToSend = await encrypt(keyToSend)
    }
    const models = await fetchLlmModels({ baseUrl: llmConfig.baseUrl, apiKey: keyToSend })
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
