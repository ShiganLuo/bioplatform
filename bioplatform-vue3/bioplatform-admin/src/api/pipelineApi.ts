import http from '@/utils/http/axios'

export interface Pipeline {
  id: number
  name: string
  type: string
  templateId: number
  projectId: number
  metaContent: string
  metaType: string
  extraParams: string
  description: string
  category: string
  configJson: string
  dockerImage: string
  timeout: number
  ownerId: number
  createdAt: string
  updatedAt: string
}

export interface PipelineQuery {
  page?: number
  size?: number
  name?: string
  category?: string
  projectId?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listPipelines(params: PipelineQuery) {
  return http.get<PageResult<Pipeline>>('/api/admin/pipelines/list', { params })
}

export function getPipeline(id: number) {
  return http.get<Pipeline>(`/api/admin/pipelines/${id}`)
}

export function createPipeline(data: Partial<Pipeline>) {
  return http.post<Pipeline>('/api/admin/pipelines/create', data)
}

export function updatePipeline(id: number, data: Partial<Pipeline>) {
  return http.put<Pipeline>(`/api/admin/pipelines/update`, { id, ...data })
}

export function deletePipeline(id: number) {
  return http.delete(`/api/admin/pipelines/${id}`)
}

export function executePipeline(id: number, params?: Record<string, any>) {
  return http.post(`/api/admin/pipelines/${id}/execute`, params)
}
