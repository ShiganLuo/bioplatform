import http from '@/utils/http/axios'

export interface WorkflowTemplate {
  id: number
  name: string
  description: string
  type: string
  category: string
  configTemplate: string
  schemaJson: string
  snakemakePath: string
  icon: string
  sortOrder: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface TemplateQuery {
  page?: number
  size?: number
  type?: string
  category?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listTemplates(params: TemplateQuery) {
  return http.get<PageResult<WorkflowTemplate>>('/api/admin/templates/list', { params })
}

export function getTemplate(id: number) {
  return http.get<WorkflowTemplate>(`/api/admin/templates/${id}`)
}

export function createTemplate(data: Partial<WorkflowTemplate>) {
  return http.post<WorkflowTemplate>('/api/admin/templates/create', data)
}

export function updateTemplate(data: Partial<WorkflowTemplate>) {
  return http.put('/api/admin/templates/update', data)
}

export function deleteTemplate(id: number) {
  return http.delete(`/api/admin/templates/${id}`)
}

export function importTemplates(omicsDir: string) {
  return http.post<number>('/api/admin/templates/import', { omicsDir })
}
