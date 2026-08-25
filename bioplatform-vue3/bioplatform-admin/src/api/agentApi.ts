import http from '@/utils/http/axios'

export interface Conversation {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export interface ChatRequest {
  conversationId?: string
  message: string
}

export interface ChatResponse {
  conversationId: string
  message: ChatMessage
}

export function chat(data: ChatRequest) {
  return http.post<ChatResponse>('/api/admin/agent/chat', data, { silent: true } as any)
}

export function chatStream(
  data: ChatRequest,
  onToken: (token: string) => void,
  onDone: (info: { conversationId: string }) => void,
  onError: (err: string) => void
): AbortController {
  const abortController = new AbortController()
  const token = localStorage.getItem('access_token') || ''
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  fetch(`/api/admin/agent/chat/stream`, {
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

export function listConversations() {
  return http.get<Conversation[]>('/api/admin/agent/conversations')
}

export function getMessages(conversationId: string) {
  return http.get<ChatMessage[]>(`/api/admin/agent/conversations/${conversationId}/messages`)
}

export function deleteConversation(conversationId: string) {
  return http.delete(`/api/admin/agent/conversations/${conversationId}`)
}

export function batchDeleteConversations(ids: string[]) {
  return http.post('/api/admin/agent/conversations/batch-delete', { ids })
}

export function deleteAllConversations() {
  return http.delete('/api/admin/agent/conversations/all')
}
