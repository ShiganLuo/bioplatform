<template>
  <div class="agent-container">
    <el-container style="height: calc(100vh - 140px);">
      <!-- Conversation Sidebar -->
      <el-aside :width="sidebarCollapsed ? '48px' : '280px'" class="conversation-sidebar" style="transition: width 0.2s;">
        <div class="sidebar-header">
          <div v-if="!sidebarCollapsed" style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
            <span style="font-size: 15px; font-weight: 600;">对话列表</span>
            <el-button size="small" circle @click="sidebarCollapsed = !sidebarCollapsed">
              <el-icon><component :is="sidebarCollapsed ? 'Expand' : 'Fold'"></component></el-icon>
            </el-button>
          </div>
          <div v-if="!sidebarCollapsed" style="display: flex; gap: 6px; flex-wrap: wrap;">
            <el-button v-if="!batchMode" type="primary" size="small" @click="createConversation">
              <el-icon><Plus /></el-icon> 新对话
            </el-button>
            <el-button v-if="conversations.length > 0" size="small" :type="batchMode ? 'warning' : 'default'" @click="toggleBatchMode">
              <span v-text="batchMode ? '取消' : '编辑'"></span>
            </el-button>
            <el-button v-if="batchMode && selectedIds.length > 0" type="danger" size="small" @click="handleBatchDelete">
              删除 (<span v-text="selectedIds.length"></span>)
            </el-button>
            <el-button v-if="!batchMode && conversations.length > 0" type="danger" size="small" plain @click="handleDeleteAll">
              清空
            </el-button>
          </div>
          <el-button v-if="sidebarCollapsed" size="small" circle @click="sidebarCollapsed = !sidebarCollapsed">
            <el-icon><component :is="sidebarCollapsed ? 'Expand' : 'Fold'"></component></el-icon>
          </el-button>
        </div>

        <div v-if="!sidebarCollapsed" class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conversation-item"
            :class="{ active: currentConversationId === conv.id, selected: selectedIds.includes(conv.id) }"
            @click="batchMode ? toggleSelect(conv.id) : selectConversation(conv.id)"
          >
            <el-checkbox v-if="batchMode" :model-value="selectedIds.includes(conv.id)" @click.stop @change="toggleSelect(conv.id)" />
            <el-icon v-if="!batchMode"><ChatDotRound /></el-icon>
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <el-icon v-if="!batchMode" class="conv-delete" @click.stop="handleDeleteConversation(conv.id)"><Delete /></el-icon>
          </div>

          <el-empty v-if="conversations.length === 0" description="暂无对话" />
        </div>
      </el-aside>

      <!-- Chat Area -->
      <el-main class="chat-area">
        <div class="chat-messages" ref="messagesContainer">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message-item"
            :class="msg.role"
          >
            <div class="msg-avatar" :class="msg.role">
              <el-icon><ChatDotRound v-if="msg.role === 'assistant'" /><User v-else /></el-icon>
            </div>
            <div class="message-content">
              <div class="message-role">{{ msg.role === 'user' ? '我' : 'AI 助手' }}</div>
              <div v-if="msg.content" class="message-text" v-html="renderMarkdown(msg.content)"></div>
              <div class="message-time">{{ msg.createdAt }}</div>
            </div>
          </div>

          <!-- 流式渲染：独立于 messages 数组，保证 Vue 响应式 -->
          <div v-if="streamingContent" class="message-item assistant">
            <div class="msg-avatar assistant">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="message-content">
              <div class="message-role">AI 助手</div>
              <div class="message-text" v-html="renderMarkdown(streamingContent)"></div>
            </div>
          </div>

          <!-- Welcome Message -->
          <div v-if="messages.length === 0 && !streamingContent" class="welcome-section">
            <div class="welcome-icon">
              <el-icon :size="48" color="#409eff"><ChatDotRound /></el-icon>
            </div>
            <h3>您好！我是 AI 生信助手</h3>
            <p>我可以帮您解答生物信息学相关问题，协助数据分析和流程设计。</p>
            <div class="suggestions">
              <div
                v-for="(s, idx) in defaultSuggestions"
                :key="idx"
                class="suggestion-chip"
                @click="sendMessageFromSuggestion(s)"
              >
                {{ s }}
              </div>
            </div>
          </div>

          <!-- Loading Indicator -->
          <div v-if="sending && !streamingContent" class="message-item assistant">
            <div class="msg-avatar assistant">
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
        <div class="chat-input">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="输入您的问题... (Shift+Enter 换行，Enter 发送)"
            @keydown.enter.exact.prevent="sendMessage"
            :disabled="sending"
          />
          <el-button
            type="primary"
            :loading="sending"
            :disabled="!inputMessage.trim()"
            @click="sendMessage"
            class="send-btn"
          >
            发送
          </el-button>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import { Plus, ChatDotRound, Expand, Fold, User, Delete } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { marked } from 'marked'

marked.setOptions({ gfm: true, breaks: true })
import {
  chatStream,
  listConversations,
  getMessages,
  deleteConversation,
  batchDeleteConversations,
  deleteAllConversations
} from '@/api/agentApi'
import type { Conversation, ChatMessage } from '@/api/agentApi'

const conversations = ref<Conversation[]>([])
const messages = ref<ChatMessage[]>([])
const currentConversationId = ref<string>('')
const inputMessage = ref('')
const sending = ref(false)
const messagesContainer = ref<HTMLElement>()
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

const sendMessageFromSuggestion = (text: string) => {
  inputMessage.value = text
  sendMessage()
}

const renderMarkdown = (content: string) => {
  if (!content) return ''
  try { return marked.parse(content) as string } catch { return content }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const loadConversations = async () => {
  try {
    const res = await listConversations()
    conversations.value = res as any
  } catch (error) {
    console.error('Failed to load conversations:', error)
  }
}

const selectConversation = async (id: string) => {
  currentConversationId.value = id
  try {
    const res = await getMessages(id)
    messages.value = res as any
    scrollToBottom()
  } catch (error) {
    console.error('Failed to load messages:', error)
  }
}

const createConversation = () => {
  currentConversationId.value = ''
  messages.value = []
}

const handleDeleteConversation = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteConversation(id)
    if (currentConversationId.value === id) {
      currentConversationId.value = ''
      messages.value = []
    }
    await loadConversations()
  } catch (e) {
    console.error('删除对话失败:', e)
  }
}

const toggleBatchMode = () => {
  batchMode.value = !batchMode.value
  if (!batchMode.value) selectedIds.value = []
}

const toggleSelect = (id: string) => {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条对话？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await batchDeleteConversations(selectedIds.value)
    if (selectedIds.value.includes(currentConversationId.value)) {
      currentConversationId.value = ''
      messages.value = []
    }
    selectedIds.value = []
    batchMode.value = false
    await loadConversations()
  } catch (e) {
    console.error('批量删除失败:', e)
  }
}

const handleDeleteAll = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有对话？此操作不可恢复。', '警告', { type: 'error' })
  } catch { return }
  try {
    await deleteAllConversations()
    currentConversationId.value = ''
    messages.value = []
    batchMode.value = false
    selectedIds.value = []
    await loadConversations()
  } catch (e) {
    console.error('清空对话失败:', e)
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || sending.value) return

  const userMessage: ChatMessage = {
    id: Date.now().toString(),
    conversationId: currentConversationId.value,
    role: 'user',
    content: inputMessage.value,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMessage)
  scrollToBottom()

  const messageText = inputMessage.value
  inputMessage.value = ''
  sending.value = true
  streamingContent.value = ''

  let newConversationId = ''

  chatStream(
    { conversationId: currentConversationId.value || undefined, message: messageText },
    // onToken - 直接改 ref，Vue 保证响应式
    (token) => {
      streamingContent.value += token
      scrollToBottom()
    },
    // onDone - 把流式内容转入 messages 数组
    (info) => {
      if (streamingContent.value) {
        messages.value.push({
          id: Date.now().toString(),
          conversationId: info.conversationId || currentConversationId.value,
          role: 'assistant',
          content: streamingContent.value,
          createdAt: new Date().toISOString()
        } as ChatMessage)
      }
      if (info.conversationId && !currentConversationId.value) {
        currentConversationId.value = info.conversationId
        loadConversations()
      }
      streamingContent.value = ''
      sending.value = false
      scrollToBottom()
    },
    // onError - 静默处理
    () => {
      if (!streamingContent.value) {
        // 什么都没收到，不产生消息
      }
      streamingContent.value = ''
      sending.value = false
      scrollToBottom()
    }
  )
}

onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.agent-container {
  height: 100%;
}

.conversation-sidebar {
  background: #fff;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
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

.conv-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.conv-delete {
  color: #c0c4cc;
  cursor: pointer;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s, color 0.2s;
}

.conversation-item:hover .conv-delete {
  opacity: 1;
}

.conv-delete:hover {
  color: #f56c6c;
}

.chat-area {
  display: flex;
  flex-direction: column;
  padding: 0;
  background: #f5f7fa;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.msg-avatar.assistant {
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
}

.msg-avatar.user {
  background: linear-gradient(135deg, #67c23a, #409eff);
  color: #fff;
}

.message-content {
  max-width: 70%;
  margin: 0 12px;
}

.message-item.user .message-content {
  text-align: right;
}

.message-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.message-text {
  background: #fff;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  line-height: 1.6;
  font-size: 14px;
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

.message-text :deep(pre code) {
  background: none;
  padding: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.message-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid #ebeef5;
  padding: 8px 12px;
  text-align: left;
  font-size: 13px;
}

.message-text :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}

.message-item.user .message-text {
  background: #409eff;
  color: #fff;
}

.message-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
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

.chat-input {
  padding: 16px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input .el-textarea {
  flex: 1;
}

.send-btn {
  height: 76px;
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
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
</style>
