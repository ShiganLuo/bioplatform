<template>
  <div class="agent-view">
    <div class="page-header">
      <h1 class="page-title">AI 生信助手</h1>
      <p class="page-desc">智能问答，助您解决生物信息学分析中的各种问题</p>
    </div>

    <div class="chat-container">
      <!-- Conversations Sidebar -->
      <div class="conversations-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-header">
          <el-button v-if="!sidebarCollapsed && !batchMode" type="primary" size="small" @click="createNewConversation">
            <el-icon><Plus /></el-icon> 新对话
          </el-button>
          <el-button v-if="!sidebarCollapsed && conversations.length > 0" size="small" :type="batchMode ? 'warning' : 'default'" @click="toggleBatchMode">
            <span v-text="batchMode ? '取消' : '编辑'"></span>
          </el-button>
          <el-button v-if="batchMode && selectedIds.length > 0" type="danger" size="small" @click="handleBatchDelete">
            删除 (<span v-text="selectedIds.length"></span>)
          </el-button>
          <el-button v-if="!sidebarCollapsed && !batchMode && conversations.length > 0" type="danger" size="small" plain @click="handleDeleteAll">
            清空
          </el-button>
          <el-button size="small" circle @click="sidebarCollapsed = !sidebarCollapsed" style="margin-left: auto;">
            <el-icon><component :is="sidebarCollapsed ? 'Expand' : 'Fold'"></component></el-icon>
          </el-button>
        </div>
        <div v-if="!sidebarCollapsed" class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conversation-item"
            :class="{ active: conversationId === String(conv.id), selected: selectedIds.includes(String(conv.id)) }"
            @click="batchMode ? toggleSelect(String(conv.id)) : switchConversation(conv)"
          >
            <el-checkbox v-if="batchMode" :model-value="selectedIds.includes(String(conv.id))" @click.stop @change="toggleSelect(String(conv.id))" />
            <el-icon v-if="!batchMode"><ChatDotRound /></el-icon>
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <el-icon v-if="!batchMode" class="conv-delete" @click.stop="handleDeleteConversation(String(conv.id))"><Delete /></el-icon>
          </div>
          <el-empty v-if="conversations.length === 0" description="暂无对话" :image-size="40" />
        </div>
      </div>

      <!-- Chat Area -->
      <div class="chat-main">
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
          <ChatMessage v-if="msg.content" :message="msg" />
        </template>

        <!-- 流式渲染：独立于 messages 数组 -->
        <div v-if="streamingContent" class="message-wrapper assistant-message">
          <div class="message-avatar assistant-avatar">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="message-content">
            <div class="message-bubble assistant">
              <div class="message-text" v-html="renderStreamContent(streamingContent)"></div>
            </div>
          </div>
        </div>

        <!-- Loading Indicator -->
        <div v-if="loading && !streamingContent" class="message-wrapper assistant-message">
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
      </div> <!-- end chat-main -->
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ChatDotRound, Promotion, Plus, Expand, Fold, Delete } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { chatStream, getConversations, getMessages, deleteConversation, batchDeleteConversations, deleteAllConversations } from '@/api/agentApi'
import type { ChatMessage as ChatMessageType } from '@/api/agentApi'
import ChatMessage from '@/components/ChatMessage.vue'
import { marked } from 'marked'

marked.setOptions({ gfm: true, breaks: true })

function renderStreamContent(content: string): string {
  if (!content) return ''
  try { return marked.parse(content) as string } catch { return content }
}

const messagesRef = ref<HTMLElement>()
const messages = ref<ChatMessageType[]>([])
const inputText = ref('')
const loading = ref(false)
const conversationId = ref('')
const conversations = ref<any[]>([])
const sidebarCollapsed = ref(false)
const streamingContent = ref('')
const batchMode = ref(false)
const selectedIds = ref<string[]>([])

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
  streamingContent.value = ''
  scrollToBottom()

  chatStream(
    { message: text.trim(), conversationId: conversationId.value || undefined },
    // onToken - 直接改 ref，Vue 保证响应式
    (token) => {
      streamingContent.value += token
      scrollToBottom()
    },
    // onDone - 把流式内容转入 messages 数组
    (info) => {
      if (streamingContent.value) {
        messages.value.push({
          role: 'assistant',
          content: streamingContent.value,
          timestamp: Date.now(),
        })
      }
      if (info.conversationId) {
        conversationId.value = info.conversationId
        loadConversations()
      }
      streamingContent.value = ''
      loading.value = false
      scrollToBottom()
    },
    // onError - 静默处理
    () => {
      streamingContent.value = ''
      loading.value = false
      scrollToBottom()
    }
  )
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

function createNewConversation() {
  conversationId.value = ''
  messages.value = []
}

async function handleDeleteConversation(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteConversation(id)
    if (conversationId.value === id) {
      conversationId.value = ''
      messages.value = []
    }
    await loadConversations()
  } catch (e) {
    console.error('删除对话失败:', e)
  }
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) selectedIds.value = []
}

function toggleSelect(id: string) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条对话？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await batchDeleteConversations(selectedIds.value)
    if (selectedIds.value.includes(conversationId.value)) {
      conversationId.value = ''
      messages.value = []
    }
    selectedIds.value = []
    batchMode.value = false
    await loadConversations()
  } catch (e) {
    console.error('批量删除失败:', e)
  }
}

async function handleDeleteAll() {
  try {
    await ElMessageBox.confirm('确定清空所有对话？此操作不可恢复。', '警告', { type: 'error' })
  } catch { return }
  try {
    await deleteAllConversations()
    conversationId.value = ''
    messages.value = []
    batchMode.value = false
    selectedIds.value = []
    await loadConversations()
  } catch (e) {
    console.error('清空对话失败:', e)
  }
}

async function switchConversation(conv: any) {
  conversationId.value = String(conv.id)
  try {
    const msgs = await getMessages(conv.id) as any
    const msgList = Array.isArray(msgs) ? msgs : []
    messages.value = msgList.map((m: any) => ({
      role: m.role,
      content: m.content,
      timestamp: m.createdAt ? new Date(m.createdAt).getTime() : Date.now(),
    }))
    scrollToBottom()
  } catch {
    messages.value = []
  }
}

async function loadConversations() {
  try {
    const res = await getConversations() as any
    conversations.value = Array.isArray(res) ? res : []
  } catch {
    conversations.value = []
  }
}

onMounted(async () => {
  await loadConversations()
  // 加载最近对话的历史消息
  if (conversations.value.length > 0) {
    await switchConversation(conversations.value[0])
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
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  min-height: 500px;
}

/* Conversations Sidebar */
.conversations-sidebar {
  width: 220px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.2s;
}

.conversations-sidebar.collapsed {
  width: 48px;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  gap: 8px;
  align-items: center;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.conversation-item:hover {
  background: #f5f7fa;
}

.conversation-item.active {
  background: #ecf5ff;
  color: #409eff;
}

.conversation-item.selected {
  background: #fef0f0;
}

.conv-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.conv-delete {
  color: #c0c4cc;
  cursor: pointer;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s, color 0.2s;
  margin-left: auto;
}

.conversation-item:hover .conv-delete {
  opacity: 1;
}

.conv-delete:hover {
  color: #f56c6c;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
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

/* 流式消息气泡样式（与 ChatMessage 组件一致） */
.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 14px;
}

.message-bubble.assistant {
  background: #f5f7fa;
  color: #303133;
  border-top-left-radius: 4px;
}

.message-text :deep(p) {
  margin: 0 0 8px;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
  font-size: 13px;
}

.message-text :deep(code) {
  background: rgba(64, 158, 255, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
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
