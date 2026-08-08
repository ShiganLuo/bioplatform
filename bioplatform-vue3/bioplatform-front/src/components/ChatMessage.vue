<template>
  <div class="message-wrapper" :class="[message.role + '-message']">
    <!-- Assistant Avatar -->
    <div v-if="message.role === 'assistant'" class="message-avatar assistant-avatar">
      <el-icon><ChatDotRound /></el-icon>
    </div>

    <div class="message-body">
      <!-- Message Bubble -->
      <div class="message-bubble" :class="message.role">
        <div v-if="message.role === 'assistant'" class="message-text" v-html="renderedContent"></div>
        <div v-else class="message-text">{{ message.content }}</div>
      </div>

      <!-- Tool Calls -->
      <div v-if="message.toolCalls && message.toolCalls.length > 0" class="tool-calls">
        <div class="tool-calls-header">
          <el-icon><SetUp /></el-icon>
          <span>调用了 {{ message.toolCalls.length }} 个工具</span>
        </div>
        <div
          v-for="(tool, idx) in message.toolCalls"
          :key="idx"
          class="tool-call-item"
        >
          <div class="tool-name">
            <el-icon><MagicStick /></el-icon>
            {{ tool.name }}
          </div>
          <div v-if="tool.result" class="tool-result">
            {{ truncate(tool.result, 200) }}
          </div>
        </div>
      </div>

      <!-- Timestamp -->
      <span class="message-time">{{ formatTime(message.timestamp) }}</span>
    </div>

    <!-- User Avatar -->
    <div v-if="message.role === 'user'" class="message-avatar user-avatar">
      <el-icon><User /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChatDotRound, User, SetUp, MagicStick } from '@element-plus/icons-vue'
import type { ChatMessage } from '@/api/agentApi'
import { marked } from 'marked'
import hljs from 'highlight.js'

const props = defineProps<{
  message: ChatMessage
}>()

// Configure marked
marked.setOptions({
  gfm: true,
  breaks: true,
})

const renderedContent = computed(() => {
  if (props.message.role !== 'assistant') return props.message.content
  try {
    return marked.parse(props.message.content) as string
  } catch {
    return props.message.content
  }
})

function formatTime(ts: number) {
  const d = new Date(ts)
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  return `${h}:${m}`
}

function truncate(text: string, max: number) {
  return text.length > max ? text.substring(0, max) + '...' : text
}
</script>

<style scoped>
.message-wrapper {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.user-message {
  justify-content: flex-end;
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

.user-avatar {
  background: linear-gradient(135deg, #67c23a, #409eff);
  color: #fff;
}

.message-body {
  max-width: 75%;
  display: flex;
  flex-direction: column;
}

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

.message-bubble.user {
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  border-top-right-radius: 4px;
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

.message-text :deep(a) {
  color: #409eff;
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

/* Tool Calls */
.tool-calls {
  margin-top: 8px;
  background: #fafafa;
  border-radius: 8px;
  padding: 10px;
  border: 1px solid #ebeef5;
}

.tool-calls-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.tool-call-item {
  padding: 8px;
  background: #fff;
  border-radius: 6px;
  margin-bottom: 4px;
}

.tool-call-item:last-child {
  margin-bottom: 0;
}

.tool-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #409eff;
}

.tool-result {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  white-space: pre-wrap;
}

.message-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

.user-message .message-time {
  text-align: right;
}
</style>
