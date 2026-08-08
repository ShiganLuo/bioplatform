<template>
  <div class="agent-container">
    <el-container style="height: calc(100vh - 140px);">
      <!-- Conversation Sidebar -->
      <el-aside width="280px" class="conversation-sidebar">
        <div class="sidebar-header">
          <span>对话列表</span>
          <el-button type="primary" size="small" @click="createConversation">
            <el-icon><Plus /></el-icon>
            新对话
          </el-button>
        </div>

        <div class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conversation-item"
            :class="{ active: currentConversationId === conv.id }"
            @click="selectConversation(conv.id)"
          >
            <div class="conv-title">{{ conv.title }}</div>
            <div class="conv-time">{{ conv.updatedAt }}</div>
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
            <div class="message-avatar">
              <el-avatar v-if="msg.role === 'user'" :size="36">U</el-avatar>
              <el-avatar v-else :size="36" style="background: #67c23a;">AI</el-avatar>
            </div>
            <div class="message-content">
              <div class="message-role">{{ msg.role === 'user' ? '我' : 'AI 助手' }}</div>
              <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
              <div class="message-time">{{ msg.createdAt }}</div>
            </div>
          </div>

          <div v-if="messages.length === 0" class="empty-chat">
            <el-icon :size="48" color="#c0c4cc"><ChatDotRound /></el-icon>
            <p>开始与 AI 助手对话</p>
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
import { ref, onMounted, nextTick } from 'vue'
import { Plus, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  chat,
  listConversations,
  getMessages,
  deleteConversation
} from '@/api/agentApi'
import type { Conversation, ChatMessage } from '@/api/agentApi'

const conversations = ref<Conversation[]>([])
const messages = ref<ChatMessage[]>([])
const currentConversationId = ref<string>('')
const inputMessage = ref('')
const sending = ref(false)
const messagesContainer = ref<HTMLElement>()

const renderMarkdown = (content: string) => {
  // Simple markdown rendering - in production, use md-editor-v3 or marked
  return content
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
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

  try {
    const res = await chat({
      conversationId: currentConversationId.value || undefined,
      message: messageText
    })

    const response = res as any
    if (response.conversationId && !currentConversationId.value) {
      currentConversationId.value = response.conversationId
      await loadConversations()
    }

    messages.value.push(response.message)
    scrollToBottom()
  } catch (error) {
    ElMessage.error('发送失败，请稍后重试')
    // Remove user message on error
    messages.value.pop()
  } finally {
    sending.value = false
  }
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
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e4e7ed;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
}

.conversation-item:hover {
  background: #f5f7fa;
}

.conversation-item.active {
  background: #ecf5ff;
}

.conv-title {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
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

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
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
</style>
