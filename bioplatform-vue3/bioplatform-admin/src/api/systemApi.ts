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

export function deleteConfig(key: string) {
  return http.delete(`/api/admin/system/configs/${key}`)
}

export function getDashboard() {
  return http.get<DashboardData>('/api/admin/system/dashboard')
}

export interface OperationLogItem {
  id: number
  userId: number | null
  username: string | null
  module: string | null
  operation: string
  method: string | null
  params: string | null
  result: string | null
  status: string
  ip: string | null
  createdAt: string
}

export interface LogQuery {
  page?: number
  size?: number
  userId?: number
  username?: string
  module?: string
  status?: string
  operation?: string
  keyword?: string
  startDate?: string
  endDate?: string
}

export function getSystemLogs(params: LogQuery) {
  return http.get('/api/admin/logs/list', { params })
}

export function fetchLlmModels(data: { baseUrl: string; apiKey: string; provider?: string }) {
  return http.post<string[]>('/api/admin/system/llm/fetch-models', data, { silent: true } as any)
}
