<template>
  <div class="agent-view">
    <div class="page-header">
      <h1 class="page-title">AI 生信助手</h1>
      <p class="page-desc">智能问答，助您解决生物信息学分析中的各种问题</p>
    </div>

    <div class="chat-container">
      <!-- Messages Area -->
      <div class="messages-area" ref="messagesRef">
        <!-- Welcome Message -->
        <div v-if="messages.length === 0" class="welcome-section">
          <div class="welcome-icon">
            <el-icon :size="48" color="#409eff"><ChatDotRound /></el-icon>
          </div>
          <h3>您好！我是 AI 生信助手</h3>
          <p>我可以帮您解答生物信息学相关问题，协助数据分析和流程设计。</p>
          <div class="suggestions">
            <div
              v-for="(suggestion, idx) in suggestions"
              :key="idx"
              class="suggestion-chip"
              @click="sendMessage(suggestion)"
            >
              {{ suggestion }}
            </div>
          </div>
        </div>

        <!-- Chat Messages -->
        <template v-for="(msg, idx) in messages" :key="idx">
          <ChatMessage :message="msg" />
        </template>

        <!-- Loading Indicator -->
        <div v-if="loading" class="message-wrapper assistant-message">
          <div class="message-avatar assistant-avatar">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入您的问题... (Enter 发送，Shift+Enter 换行)"
            @keydown="handleKeydown"
            :disabled="loading"
          />
          <el-button
            type="primary"
            circle
            :icon="Promotion"
            :disabled="!inputText.trim() || loading"
            @click="handleSend"
            class="send-btn"
          />
        </div>
        <p class="input-hint">AI 生成的内容仅供参考，请以实际数据和文献为准</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { chat, getSuggestions } from '@/api/agentApi'
import type { ChatMessage as ChatMessageType } from '@/api/agentApi'
import ChatMessage from '@/components/ChatMessage.vue'

const messagesRef = ref<HTMLElement>()
const messages = ref<ChatMessageType[]>([])
const inputText = ref('')
const loading = ref(false)
const conversationId = ref('')

const defaultSuggestions = [
  'RNA-seq 数据分析的标准流程是什么？',
  '如何进行全基因组关联分析（GWAS）？',
  '推荐一些常用的基因组可视化工具',
  '单细胞测序数据如何进行降维分析？',
]

const suggestions = ref(defaultSuggestions)

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function sendMessage(text: string) {
  if (!text.trim() || loading.value) return

  const userMsg: ChatMessageType = {
    role: 'user',
    content: text.trim(),
    timestamp: Date.now(),
  }
  messages.value.push(userMsg)
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await chat({
      message: text.trim(),
      conversationId: conversationId.value || undefined,
    })
    const data = res as any
    const reply = data.reply || data.data?.reply || '抱歉，暂时无法回答您的问题。'
    conversationId.value = data.conversationId || data.data?.conversationId || conversationId.value

    const assistantMsg: ChatMessageType = {
      role: 'assistant',
      content: reply,
      toolCalls: data.toolCalls || data.data?.toolCalls,
      timestamp: Date.now(),
    }
    messages.value.push(assistantMsg)
  } catch {
    const errorMsg: ChatMessageType = {
      role: 'assistant',
      content: '抱歉，请求出错，请稍后重试。',
      timestamp: Date.now(),
    }
    messages.value.push(errorMsg)
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function handleSend() {
  sendMessage(inputText.value)
}

function handleKeydown(e: Event | KeyboardEvent) {
  const keyEvent = e as KeyboardEvent
  if (keyEvent.key === 'Enter' && !keyEvent.shiftKey) {
    keyEvent.preventDefault()
    handleSend()
  }
}

onMounted(async () => {
  try {
    const res = await getSuggestions()
    const data = res as any
    const list = data || data?.data
    if (Array.isArray(list) && list.length > 0) {
      suggestions.value = list
    }
  } catch {
    // use defaults
  }
})
</script>

<style scoped>
.agent-view {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 8px;
}

.page-desc {
  color: #909399;
  font-size: 15px;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  min-height: 500px;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* Welcome Section */
.welcome-section {
  text-align: center;
  padding: 40px 0;
}

.welcome-icon {
  margin-bottom: 16px;
}

.welcome-section h3 {
  font-size: 20px;
  color: #303133;
  margin-bottom: 8px;
}

.welcome-section p {
  color: #909399;
  margin-bottom: 24px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  max-width: 600px;
  margin: 0 auto;
}

.suggestion-chip {
  padding: 10px 16px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 20px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
}

.suggestion-chip:hover {
  background: #ecf5ff;
  border-color: #409eff;
  color: #409eff;
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* Input Area */
.input-area {
  padding: 16px 24px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.input-wrapper :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 10px 16px;
  resize: none;
}

.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
}

.input-hint {
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
  margin-top: 8px;
}

/* Message styles */
.message-wrapper {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assistant-avatar {
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
}

.message-content {
  max-width: 75%;
}

@media (max-width: 768px) {
  .agent-view {
    height: calc(100vh - 100px);
  }
  .messages-area {
    padding: 16px;
  }
}
</style>
