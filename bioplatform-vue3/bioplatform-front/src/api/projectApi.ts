import http from '@/utils/http/axios'

export interface Project {
  id: number
  name: string
  description: string
  organism: string
  genomeVersion: string
  status: number
  ownerName: string
  isPrivate: boolean
  createdAt: string
  updatedAt: string
}

export interface ProjectListParams {
  page?: number
  size?: number
  keyword?: string
  organism?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listPublicProjects(params: ProjectListParams) {
  return http.get<PageResult<Project>>('/api/front/projects/list', { params })
}

export function searchProjects(params: ProjectListParams = {}) {
  return http.get<PageResult<Project>>('/api/front/projects/search', { params })
}

export function getProjectDetail(id: number) {
  return http.get<Project>(`/api/front/projects/${id}`)
}
