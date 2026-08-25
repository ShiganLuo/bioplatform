import http from '@/utils/http/axios'

export interface SystemConfig {
  id: number
  key: string
  value: string
  description: string
  category: string
  updateTime: string
}

export interface DashboardData {
  totalUsers: number
  totalProjects: number
  totalPipelines: number
  totalExecutions: number
  recentExecutions: any[]
  systemInfo: {
    version: string
    uptime: string
    cpuUsage: number
    memoryUsage: number
    diskUsage: number
  }
}

export function getConfigs(category?: string) {
  return http.get<SystemConfig[]>('/api/admin/system/configs', { params: { category } })
}

export function updateConfig(id: number, data: Partial<SystemConfig>) {
  return http.put<SystemConfig>(`/api/admin/system/configs`, data)
}

export function getDashboard() {
  return http.get<DashboardData>('/api/admin/system/dashboard')
}

export function getSystemLogs(params: { page?: number; size?: number; level?: string }) {
  return http.get('/api/admin/logs/list', { params })
}

export function fetchLlmModels(data: { baseUrl: string; apiKey: string }) {
  return http.post<string[]>('/api/admin/system/llm/fetch-models', data, { silent: true } as any)
}
