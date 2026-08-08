import http from '@/utils/http/axios'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  toolCalls?: ToolCall[]
  timestamp: number
}

export interface ToolCall {
  name: string
  arguments: string
  result?: string
}

export interface ChatRequest {
  message: string
  conversationId?: string
}

export interface ChatResponse {
  reply: string
  conversationId: string
  toolCalls?: ToolCall[]
}

// 发送聊天消息 (可选认证)
export function chat(data: ChatRequest) {
  return http.post<ChatResponse>('/api/front/agent/chat', data)
}

// 获取可用工具列表
export function getTools() {
  return http.get('/api/front/agent/tools')
}

export const getSuggestions = getTools;
