import http from '@/utils/http/axios'

export interface User {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  roles: string[]
  status: number
  createTime: string
}

export interface UserQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listUsers(params: UserQuery) {
  return http.get<PageResult<User>>('/api/admin/users/list', { params })
}

export function createUser(data: Partial<User> & { password?: string }) {
  return http.post<User>('/api/admin/users/create', data)
}

export function updateUser(id: number, data: Partial<User>) {
  return http.put<User>('/api/admin/users/update', { id, ...data })
}

export function updateUserStatus(id: number, status: number) {
  return http.put('/api/admin/users/status', { id, status })
}

export function deleteUser(id: number) {
  return http.delete(`/api/admin/users/${id}`)
}

export function resetPassword(id: number, password: string) {
  return http.put(`/api/admin/users/reset-password`, { 
    "id": id,
    "newPassword": password
  })
}
