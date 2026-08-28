import http from '@/utils/http/axios'

export interface SampleMeta {
  id: number
  projectId: number
  name: string
  metaMode: string
  metaContent: string
  description: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export function listSampleMeta(projectId: number) {
  return http.get<SampleMeta[]>('/api/admin/sample-meta/list', { params: { projectId } })
}

export function getSampleMeta(id: number) {
  return http.get<SampleMeta>(`/api/admin/sample-meta/${id}`)
}

export function createSampleMeta(data: Partial<SampleMeta>) {
  return http.post<SampleMeta>('/api/admin/sample-meta/create', data)
}

export function updateSampleMeta(data: SampleMeta) {
  return http.put<void>('/api/admin/sample-meta/update', data)
}

export function deleteSampleMeta(id: number) {
  return http.delete<void>(`/api/admin/sample-meta/${id}`)
}
