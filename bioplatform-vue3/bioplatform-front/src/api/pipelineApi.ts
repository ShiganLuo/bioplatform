import http from '@/utils/http/axios'

export interface Pipeline {
  id: number
  name: string
  description: string
  category: string
  categoryLabel?: string
  type: string
  createdAt: string
}

export interface PipelineListParams {
  page?: number
  size?: number
  category?: string
  keyword?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listPipelines(params?: PipelineListParams) {
  return http.get<PageResult<Pipeline>>('/api/front/pipelines/list', { params })
}

export function getPipelineDetail(id: number) {
  return http.get<Pipeline>(`/api/front/pipelines/${id}`)
}

export function getCategories() {
  return http.get<string[]>('/api/front/pipelines/categories')
}
