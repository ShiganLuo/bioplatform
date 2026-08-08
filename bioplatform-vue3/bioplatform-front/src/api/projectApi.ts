import http from '@/utils/http/axios'

export interface Project {
  id: number
  name: string
  description: string
  organism: string
  genomeVersion: string
  status: string
  ownerName: string
  isPublic: boolean
  sampleCount?: number
  createdAt: string
  updatedAt: string
}

export interface ProjectListParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  organism?: string
  page?: number
  size?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

// 获取公开项目列表
export function listPublicProjects(params: ProjectListParams) {
  return http.get<PageResult<Project>>('/api/front/projects/list', { params })
}

// 获取项目详情
export function searchProjects(params: ProjectListParams = {}) {
  return http.get('/api/front/projects/search', { params })
}

export function getProjectDetail(id: number) {
  return http.get<Project>(`/api/front/projects/${id}`)
}
