import http from '@/utils/http/axios'

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  email: string
  password: string
  nickName?: string
  verifyCode: string
}

export interface UserInfo {
  id: number
  username: string
  email: string
  nickName: string
  avatar?: string
  role: string
  createdAt: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  refreshToken?: string
  expiresIn?: number
  username: string
}

// 用户登录
export function login(data: LoginParams) {
  return http.post<LoginResult>('/api/front/auth/login', data)
}

// 用户注册
export function register(data: RegisterParams) {
  return http.post('/api/front/auth/register', data)
}

// 发送邮箱验证码
export function sendEmailCode(email: string) {
  return http.post('/api/front/auth/sendEmailCode', { email })
}

// 获取当前用户信息
export function getUserInfo() {
  return http.get<UserInfo>('/api/front/auth/userInfo')
}

// 退出登录
export function logout() {
  return http.post('/api/front/auth/logout')
}
