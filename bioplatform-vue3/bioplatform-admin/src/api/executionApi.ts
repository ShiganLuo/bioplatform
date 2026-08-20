import http from '@/utils/http/axios'

export interface Execution {
  id: number
  pipelineId: number
  pipelineName: string
  projectId: number
  projectName: string
  status: string
  progress: number
  startTime: string
  endTime: string
  executor: string
  params: string
  result: string
  errorMessage: string
  createTime: string
}

export interface ExecutionQuery {
  page?: number
  size?: number
  pipelineId?: number
  projectId?: number
  status?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listExecutions(params: ExecutionQuery) {
  return http.get<PageResult<Execution>>('/api/admin/executions/list', { params })
}

export function getExecution(id: number) {
  return http.get<Execution>(`/api/admin/executions/${id}`)
}

export function cancelExecution(id: number) {
  return http.put(`/api/admin/executions/${id}/cancel`)
}

export function getExecutionLogs(id: number) {
  return http.get<string>(`/api/admin/executions/${id}/logs`)
}
