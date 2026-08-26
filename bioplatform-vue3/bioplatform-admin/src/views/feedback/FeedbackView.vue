<template>
  <div class="feedback-view">
    <div class="feedback-container">
      <!-- 会话列表 -->
      <div class="session-list">
        <div class="list-header">
          <span>反馈会话</span>
          <div style="display:flex;gap:6px;align-items:center;">
            <span :class="['ws-dot', wsConnected ? 'online' : 'offline']" :title="wsConnected ? '已连接' : '未连接'"></span>
            <el-button size="small" circle @click="loadSessions"><el-icon><Refresh /></el-icon></el-button>
          </div>
        </div>
        <div class="sessions">
          <div
            v-for="session in sessions" :key="session.id"
            class="session-item" :class="{ active: currentSessionId === session.id }"
            @click="selectSession(session)"
          >
            <div class="session-info">
              <span class="session-name">{{ session.userName || '匿名用户' }}</span>
              <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
            </div>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无反馈会话" :image-size="40" />
        </div>
      </div>

      <!-- 聊天区域 -->
      <div class="chat-area">
        <template v-if="currentSessionId">
          <div class="chat-header">
            <span>{{ currentSession?.userName || '匿名用户' }}</span>
            <div style="display:flex;gap:8px;align-items:center;">
              <el-button v-if="!wsConnected" size="small" type="warning" plain @click="doConnect">重新连接</el-button>
              <el-button size="small" type="danger" plain @click="handleCloseSession">关闭会话</el-button>
            </div>
          </div>
          <div class="messages-area" ref="messagesRef">
            <div
              v-for="msg in messages" :key="msg.id"
              class="message-wrapper"
              :class="msg.senderType === 'admin' ? 'admin-msg' : 'user-msg'"
            >
              <div class="message-bubble">
                <div class="message-sender">{{ msg.senderName }}</div>
                <div class="message-text">{{ msg.content }}</div>
                <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
              </div>
            </div>
          </div>
          <div v-if="!wsConnected" class="reconnect-bar">
            <span>连接已断开，无法发送消息</span>
            <el-button size="small" type="primary" link @click="doConnect">重新连接</el-button>
          </div>
          <div class="input-area">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              :autosize="{ minRows: 1, maxRows: 3 }"
              :placeholder="wsConnected ? '输入回复内容... (Enter 发送)' : '请先连接'"
              :disabled="!wsConnected"
              @keydown="handleKeydown"
            />
            <el-button type="primary" :disabled="!inputText.trim() || !wsConnected" @click="sendMessage">发送</el-button>
          </div>
        </template>
        <div v-else class="no-session">
          <el-icon :size="48" color="#c0c4cc"><ChatDotRound /></el-icon>
          <p>选择一个会话开始回复</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted } from 'vue'
import { Refresh, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOpenSessions, getSessionMessages, closeSession } from '@/api/feedbackApi'
import type { FeedbackSession, FeedbackMessage } from '@/api/feedbackApi'

const sessions = ref<FeedbackSession[]>([])
const currentSessionId = ref<number | null>(null)
const currentSession = ref<FeedbackSession | null>(null)
const messages = ref<FeedbackMessage[]>([])
const inputText = ref('')
const messagesRef = ref<HTMLElement>()
const wsConnected = ref(false)

let ws: WebSocket | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null

function scrollToBottom() {
  nextTick(() => { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight })
}

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

async function loadSessions() {
  try { sessions.value = Array.isArray(await getOpenSessions()) ? await getOpenSessions() as any : [] } catch { sessions.value = [] }
}

async function selectSession(session: FeedbackSession) {
  currentSessionId.value = session.id
  currentSession.value = session
  try { messages.value = Array.isArray(await getSessionMessages(session.id)) ? await getSessionMessages(session.id) as any : [] } catch { messages.value = [] }
  scrollToBottom()
}

function doConnect() {
  if (ws) { ws.onclose = null; ws.close(); ws = null }

  const token = localStorage.getItem('access_token') || ''
  if (!token) return

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  try { ws = new WebSocket(`${protocol}//${location.host}/ws/feedback?token=${token}&role=admin`) } catch { return }

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
      if (data.type === 'message' && data.sessionId === currentSessionId.value) {
        const last = messages.value[messages.value.length - 1]
        if (last?.senderType === data.senderType && last?.content === data.content) return
        messages.value.push({ id: Date.now(), sessionId: data.sessionId, senderType: data.senderType, senderName: data.senderName, content: data.content, createdAt: data.createdAt || new Date().toISOString() })
        scrollToBottom()
      }
      if (data.type === 'new_session') loadSessions()
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
  if (!content || !currentSessionId.value || !ws || ws.readyState !== WebSocket.OPEN) return
  messages.value.push({ id: Date.now(), sessionId: currentSessionId.value, senderType: 'admin', senderName: '客服', content, createdAt: new Date().toISOString() })
  scrollToBottom()
  ws.send(JSON.stringify({ type: 'message', sessionId: currentSessionId.value, content }))
  inputText.value = ''
}

function handleKeydown(e: Event | KeyboardEvent) {
  const ke = e as KeyboardEvent
  if (ke.key === 'Enter' && !ke.shiftKey) { ke.preventDefault(); sendMessage() }
}

async function handleCloseSession() {
  if (!currentSessionId.value) return
  try {
    await ElMessageBox.confirm('确定关闭该反馈会话？', '提示', { type: 'warning' })
    await closeSession(currentSessionId.value)
    ElMessage.success('会话已关闭')
    currentSessionId.value = null; currentSession.value = null; messages.value = []
    await loadSessions()
  } catch {}
}

function cleanup() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
  if (ws) { ws.onclose = null; ws.close(); ws = null }
  wsConnected.value = false
}

onMounted(() => { loadSessions(); doConnect() })
onUnmounted(cleanup)
</script>

<style scoped>
.feedback-view { height: calc(100vh - 160px); }
.feedback-container { display: flex; height: 100%; background: #fff; border-radius: 8px; border: 1px solid #ebeef5; overflow: hidden; }
.session-list { width: 260px; border-right: 1px solid #ebeef5; display: flex; flex-direction: column; }
.list-header { padding: 12px 16px; border-bottom: 1px solid #ebeef5; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.ws-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.ws-dot.online { background: #67c23a; }
.ws-dot.offline { background: #f56c6c; }
.sessions { flex: 1; overflow-y: auto; padding: 8px; }
.session-item { padding: 12px; border-radius: 8px; cursor: pointer; transition: background .2s; margin-bottom: 4px; }
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; }
.session-info { display: flex; justify-content: space-between; align-items: center; }
.session-name { font-size: 14px; color: #303133; font-weight: 500; }
.session-time { font-size: 12px; color: #c0c4cc; }
.chat-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-header { padding: 12px 16px; border-bottom: 1px solid #ebeef5; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.messages-area { flex: 1; overflow-y: auto; padding: 16px; }
.message-wrapper { margin-bottom: 12px; display: flex; }
.message-wrapper.admin-msg { justify-content: flex-end; }
.message-wrapper.user-msg { justify-content: flex-start; }
.message-bubble { max-width: 70%; padding: 10px 14px; border-radius: 12px; background: #f5f7fa; }
.admin-msg .message-bubble { background: #ecf5ff; }
.message-sender { font-size: 12px; color: #909399; margin-bottom: 4px; }
.message-text { font-size: 14px; color: #303133; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.message-time { font-size: 11px; color: #c0c4cc; margin-top: 4px; text-align: right; }
.reconnect-bar { padding: 6px 16px; background: #fdf6ec; border-top: 1px solid #faecd8; display: flex; align-items: center; justify-content: space-between; font-size: 13px; color: #e6a23c; }
.input-area { padding: 12px 16px; border-top: 1px solid #ebeef5; display: flex; gap: 8px; align-items: flex-end; }
.input-area .el-input { flex: 1; }
.no-session { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #c0c4cc; }
.no-session p { margin-top: 12px; font-size: 14px; }
</style>
