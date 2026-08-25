import http from '@/utils/http/axios'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  roles: string[]
  permissions: string[]
  status: number
  lastLoginTime: string
  createTime: string
}

export function login(data: LoginParams) {
  return http.post<LoginResult>('/api/admin/auth/login', data)
}

export function refreshToken(refreshToken: string) {
  return http.post<LoginResult>('/api/admin/auth/refreshToken', { refreshToken: refreshToken })
}

export function getUserInfo() {
  return http.get<UserInfo>('/api/admin/auth/userInfo')
}

export function logout() {
  return http.post('/api/admin/auth/logout')
}
