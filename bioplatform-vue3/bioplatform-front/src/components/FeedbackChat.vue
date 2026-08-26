<template>
  <div class="feedback-chat">
    <!-- 浮动按钮 -->
    <div class="chat-fab" @click="toggleChat" :class="{ open: chatVisible }">
      <el-icon :size="24">
        <ChatDotRound v-if="!chatVisible" />
        <Close v-else />
      </el-icon>
      <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</span>
    </div>

    <!-- 聊天窗口 -->
    <transition name="chat-slide">
      <div v-if="chatVisible" class="chat-window">
        <div class="chat-header">
          <span>在线客服</span>
          <span v-if="wsConnected" class="ws-dot online"></span>
          <span v-else class="ws-dot offline"></span>
          <el-icon class="close-btn" @click="chatVisible = false"><Close /></el-icon>
        </div>

        <!-- 未登录 -->
        <div v-if="!isLoggedIn" class="login-hint">
          <el-icon :size="32" color="#c0c4cc"><User /></el-icon>
          <p>请先登录后使用在线客服</p>
          <el-button type="primary" size="small" @click="goLogin">去登录</el-button>
        </div>

        <!-- 已登录 -->
        <template v-else>
          <div class="messages-area" ref="messagesRef">
            <div v-if="messages.length === 0 && !loadingHistory" class="welcome-hint">
              <p>您好！有什么可以帮助您的吗？</p>
            </div>
            <div v-if="loadingHistory" class="welcome-hint"><p>加载中...</p></div>
            <div
              v-for="(msg, idx) in messages" :key="idx"
              class="message-wrapper"
              :class="msg.senderType === 'user' ? 'user-msg' : 'admin-msg'"
            >
              <div class="message-bubble">
                <div class="message-text">{{ msg.content }}</div>
                <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
              </div>
            </div>
          </div>

          <!-- 连接断开提示 -->
          <div v-if="!wsConnected" class="reconnect-bar">
            <span>连接已断开</span>
            <el-button size="small" type="primary" link @click="doConnect">重新连接</el-button>
          </div>

          <div class="input-area">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="1"
              :autosize="{ minRows: 1, maxRows: 3 }"
              :placeholder="wsConnected ? '输入消息... (Enter 发送)' : '请先连接'"
              :disabled="!wsConnected"
              @keydown="handleKeydown"
            />
            <el-button
              type="primary" circle size="small" :icon="Promotion"
              :disabled="!inputText.trim() || !wsConnected"
              @click="sendMessage"
            />
          </div>
        </template>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Close, User, Promotion } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getMySession, getFeedbackMessages } from '@/api/siteApi'

const router = useRouter()
const userStore = useUserStore()

const chatVisible = ref(false)
const messages = ref<any[]>([])
const inputText = ref('')
const messagesRef = ref<HTMLElement>()
const unreadCount = ref(0)
const wsConnected = ref(false)
const loadingHistory = ref(false)

let ws: WebSocket | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let sessionId: number | null = null
let historyLoaded = false

const isLoggedIn = computed(() => userStore.isLoggedIn)

function toggleChat() {
  chatVisible.value = !chatVisible.value
  if (chatVisible.value) {
    unreadCount.value = 0
    if (isLoggedIn.value && !ws) doConnect()
  }
}

function goLogin() {
  chatVisible.value = false
  window.dispatchEvent(new CustomEvent('show-login-modal', { detail: 'login' }))
}

async function doConnect() {
  if (!isLoggedIn.value) return
  if (ws) { ws.onclose = null; ws.close(); ws = null }

  // 首次加载历史
  if (!historyLoaded) {
    historyLoaded = true
    loadingHistory.value = true
    try {
      const session = await getMySession() as any
      if (session?.id) {
        sessionId = session.id
        const history = await getFeedbackMessages(session.id) as any
        if (Array.isArray(history)) messages.value = history
        scrollToBottom()
      }
    } catch {}
    loadingHistory.value = false
  }

  // 连接 WebSocket
  const stored = localStorage.getItem('bio_user')
  let token = ''
  try { token = JSON.parse(stored || '{}').token || '' } catch {}
  if (!token) return

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  try {
    ws = new WebSocket(`${protocol}//${location.host}/ws/feedback?token=${token}`)
  } catch { return }

  ws.onopen = () => {
    wsConnected.value = true
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) ws.send('{"type":"ping"}')
    }, 25000)
  }

  ws.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.type === 'pong') return
      if (data.type === 'message') {
        const last = messages.value[messages.value.length - 1]
        if (last?.senderType === data.senderType && last?.content === data.content) return
        if (!chatVisible.value) unreadCount.value++
        messages.value.push({ ...data, createdAt: data.createdAt || new Date().toISOString() })
        scrollToBottom()
      }
      if (data.type === 'sent' && data.sessionId && !sessionId) sessionId = data.sessionId
    } catch {}
  }

  ws.onclose = () => {
    wsConnected.value = false
    ws = null
    if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
  }
}

function sendMessage() {
  const content = inputText.value.trim()
  if (!content || !ws || ws.readyState !== WebSocket.OPEN) return
  messages.value.push({ senderType: 'user', content, createdAt: new Date().toISOString() })
  ws.send(JSON.stringify({ type: 'message', sessionId, content, userName: userStore.nickname }))
  inputText.value = ''
  scrollToBottom()
}

function handleKeydown(e: Event | KeyboardEvent) {
  const ke = e as KeyboardEvent
  if (ke.key === 'Enter' && !ke.shiftKey) { ke.preventDefault(); sendMessage() }
}

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function scrollToBottom() {
  nextTick(() => { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight })
}

function cleanup() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
  if (ws) { ws.onclose = null; ws.close(); ws = null }
  wsConnected.value = false
}

watch(chatVisible, (v) => { if (!v) cleanup() })
watch(() => userStore.isLoggedIn, (v) => { if (!v) { cleanup(); messages.value = []; sessionId = null; historyLoaded = false } })
onUnmounted(cleanup)

function openChat() {
  chatVisible.value = true
  unreadCount.value = 0
  nextTick(() => { if (isLoggedIn.value && !ws) doConnect() })
}
defineExpose({ toggleChat, openChat })
</script>

<style scoped>
.feedback-chat { position: fixed; bottom: 24px; right: 24px; z-index: 9999; }
.chat-fab { width: 56px; height: 56px; border-radius: 50%; background: #409eff; color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 4px 12px rgba(64,158,255,.4); transition: all .3s; position: relative; }
.chat-fab:hover { transform: scale(1.1); }
.chat-fab.open { background: #909399; }
.unread-badge { position: absolute; top: -4px; right: -4px; background: #f56c6c; color: #fff; font-size: 12px; min-width: 18px; height: 18px; border-radius: 9px; display: flex; align-items: center; justify-content: center; padding: 0 4px; }
.chat-window { position: absolute; bottom: 68px; right: 0; width: 360px; height: 480px; background: #fff; border-radius: 12px; box-shadow: 0 8px 32px rgba(0,0,0,.12); display: flex; flex-direction: column; overflow: hidden; border: 1px solid #ebeef5; }
.chat-slide-enter-active, .chat-slide-leave-active { transition: all .3s ease; }
.chat-slide-enter-from, .chat-slide-leave-to { opacity: 0; transform: translateY(20px) scale(.95); }
.chat-header { padding: 14px 16px; background: #409eff; color: #fff; font-weight: 600; font-size: 15px; display: flex; align-items: center; gap: 8px; }
.close-btn { margin-left: auto; cursor: pointer; opacity: .8; }
.close-btn:hover { opacity: 1; }
.ws-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.ws-dot.online { background: #67c23a; }
.ws-dot.offline { background: #f56c6c; }
.login-hint { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; color: #909399; }
.messages-area { flex: 1; overflow-y: auto; padding: 16px; }
.welcome-hint { text-align: center; color: #909399; font-size: 14px; padding: 20px 0; }
.message-wrapper { margin-bottom: 10px; display: flex; }
.message-wrapper.user-msg { justify-content: flex-end; }
.message-wrapper.admin-msg { justify-content: flex-start; }
.message-bubble { max-width: 80%; padding: 8px 12px; border-radius: 12px; font-size: 14px; line-height: 1.5; }
.user-msg .message-bubble { background: #409eff; color: #fff; border-bottom-right-radius: 4px; }
.admin-msg .message-bubble { background: #f5f7fa; color: #303133; border-bottom-left-radius: 4px; }
.message-text { white-space: pre-wrap; word-break: break-word; }
.message-time { font-size: 11px; margin-top: 4px; text-align: right; opacity: .7; }
.user-msg .message-time { color: rgba(255,255,255,.8); }
.admin-msg .message-time { color: #c0c4cc; }
.reconnect-bar { padding: 6px 16px; background: #fdf6ec; border-top: 1px solid #ebeef5; display: flex; align-items: center; justify-content: space-between; font-size: 13px; color: #e6a23c; }
.input-area { padding: 10px 12px; border-top: 1px solid #ebeef5; display: flex; gap: 8px; align-items: flex-end; }
.input-area :deep(.el-textarea__inner) { resize: none; }
@media (max-width: 480px) { .chat-window { width: calc(100vw - 32px); right: -8px; height: 400px; } }
</style>
