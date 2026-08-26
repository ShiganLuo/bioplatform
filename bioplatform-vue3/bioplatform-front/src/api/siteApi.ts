import http from '@/utils/http/axios'

export interface SiteConfig {
  siteName: string
  siteDescription: string
  contactEmail: string
  githubUrl: string
}

export function getSiteConfig() {
  return http.get<SiteConfig>('/api/front/site-config')
}

// 反馈相关
export function getMySession() {
  return http.get('/api/front/feedback/session', { silent: true } as any)
}

export function getFeedbackMessages(sessionId: number) {
  return http.get('/api/front/feedback/messages', { params: { sessionId }, silent: true } as any)
}
