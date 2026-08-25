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
  return http.post<ChatResponse>('/api/front/agent/chat', data, { silent: true } as any)
}

export function chatStream(
  data: ChatRequest,
  onToken: (token: string) => void,
  onDone: (info: { conversationId: string }) => void,
  onError: (err: string) => void
): AbortController {
  const abortController = new AbortController()
  let token = ''
  try {
    const stored = localStorage.getItem('bio_user')
    if (stored) token = JSON.parse(stored).token || ''
  } catch {}
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  fetch(`/api/front/agent/chat/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
    signal: abortController.signal,
  }).then(async (response) => {
    if (!response.ok) {
      onError(`HTTP ${response.status}`)
      return
    }
    const reader = response.body?.getReader()
    if (!reader) { onError('无法读取响应流'); return }
    const decoder = new TextDecoder()
    let buffer = ''
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const jsonStr = line.slice(5).trim()
          if (!jsonStr) continue
          try {
            const obj = JSON.parse(jsonStr)
            if (obj.error) { onError(obj.error); return }
            if (obj.done) { onDone({ conversationId: String(obj.conversationId) }); return }
            if (obj.delta) { onToken(obj.delta) }
          } catch { /* skip malformed */ }
        }
      }
    } catch (e: any) {
      if (e.name !== 'AbortError') onError(e.message || '流式读取异常')
    }
  }).catch((e: any) => {
    if (e.name !== 'AbortError') onError(e.message || '请求失败')
  })
  return abortController
}

// 获取可用工具列表
export function getTools() {
  return http.get('/api/front/agent/tools')
}

// 获取当前用户的对话列表
export function getConversations() {
  return http.get('/api/front/agent/conversations', { silent: true } as any)
}

// 获取对话的消息历史
export function getMessages(conversationId: number) {
  return http.get(`/api/front/agent/conversations/${conversationId}/messages`, { silent: true } as any)
}

// 删除对话
export function deleteConversation(conversationId: string) {
  return http.del(`/api/front/agent/conversations/${conversationId}`, { silent: true } as any)
}

// 批量删除对话
export function batchDeleteConversations(ids: string[]) {
  return http.post('/api/front/agent/conversations/batch-delete', { ids }, { silent: true } as any)
}

// 清空所有对话
export function deleteAllConversations() {
  return http.del('/api/front/agent/conversations/all', { silent: true } as any)
}

// getSuggestions 暂未实现后端端点，前端使用默认建议
