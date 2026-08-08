import http from '@/utils/http/axios'

export interface Project {
  id: number
  name: string
  description: string
  owner: string
  status: string
  type: string
  species: string
  sampleCount: number
  createTime: string
  updateTime: string
}

export interface ProjectQuery {
  page?: number
  size?: number
  name?: string
  status?: string
  type?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listProjects(params: ProjectQuery) {
  return http.get<PageResult<Project>>('/api/projects', { params })
}

export function getProject(id: number) {
  return http.get<Project>(`/api/admin/projects/${id}`)
}

export function createProject(data: Partial<Project>) {
  return http.post<Project>('/api/projects', data)
}

export function updateProject(id: number, data: Partial<Project>) {
  return http.put<Project>(`/api/admin/projects/${id}`, data)
}

export function deleteProject(id: number) {
  return http.delete(`/api/admin/projects/${id}`)
}
