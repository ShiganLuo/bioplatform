import http from '@/utils/http/axios'

export interface FeedbackSession {
  id: number
  userId: number
  userName: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface FeedbackMessage {
  id: number
  sessionId: number
  senderType: 'user' | 'admin' | 'system'
  senderName: string
  content: string
  createdAt: string
}

// 管理员: 获取所有open会话
export function getOpenSessions() {
  return http.get<FeedbackSession[]>('/api/admin/feedback/sessions')
}

// 管理员: 获取会话消息
export function getSessionMessages(sessionId: number) {
  return http.get<FeedbackMessage[]>(`/api/admin/feedback/sessions/${sessionId}/messages`)
}

// 管理员: 关闭会话
export function closeSession(sessionId: number) {
  return http.put(`/api/admin/feedback/sessions/${sessionId}/close`)
}

// 前台: 获取当前用户的open会话
export function getMySession() {
  return http.get('/api/front/feedback/session', { silent: true } as any)
}

// 前台: 获取会话历史消息
export function getFeedbackMessages(sessionId: number) {
  return http.get<FeedbackMessage[]>('/api/front/feedback/messages', { params: { sessionId }, silent: true } as any)
}
