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
  return http.post<ChatResponse>('/api/admin/agent/chat', data)
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
