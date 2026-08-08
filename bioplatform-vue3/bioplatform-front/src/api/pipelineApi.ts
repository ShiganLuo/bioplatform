import http from '@/utils/http/axios'

export interface Pipeline {
  id: number
  name: string
  description: string
  category: string
  categoryLabel?: string
  version: string
  author: string
  parameters?: string
  configTemplate?: string
  createdAt: string
}

export interface PipelineCategory {
  id: string
  name: string
  label: string
  icon?: string
}

export interface PipelineListParams {
  pageNum?: number
  pageSize?: number
  category?: string
  keyword?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

// 获取分析流程列表
export function listPipelines(params?: PipelineListParams) {
  return http.get<PageResult<Pipeline>>('/api/front/pipelines/list', { params })
}

// 获取分析流程详情
export function getPipelineDetail(id: number) {
  return http.get<Pipeline>(`/api/front/pipelines/${id}`)
}

// 获取流程分类列表
export function getCategories() {
  return http.get<PipelineCategory[]>('/api/front/pipelines/categories')
}
