import axios from 'axios'
import type {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// ============================================================
// Type Definitions
// ============================================================

export interface IResponse<T = any> {
  code: number
  message: string
  result: T
}

// 业务错误码枚举
export enum ApiStatus {
  SUCCESS = 200,
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  SERVER_ERROR = 500
}

// 扩展 AxiosRequestConfig，支持 silent 标记
declare module 'axios' {
  interface AxiosRequestConfig {
    silent?: boolean
  }
}

// ============================================================
// Axios Instance
// ============================================================

const baseURL = import.meta.env.VITE_API_BASE_URL

const axiosInstance: AxiosInstance = axios.create({
  timeout: 30000,
  baseURL,
  headers: {
    Accept: 'application/json, text/plain, */*',
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest'
  }
})

// ============================================================
// Token Refresh Mechanism
// ============================================================

let isRefreshing = false
let requests: (() => void)[] = []

/** 解析 JWT payload（不验证签名，仅读取过期时间） */
function parseJwtPayload(token: string): { exp?: number } | null {
  try {
    const base64Url = token.split('.')[1]
    if (!base64Url) return null
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}

/** 检查 token 是否即将过期（默认 5 分钟内） */
function isTokenExpiringSoon(token: string, withinMs: number = 5 * 60 * 1000): boolean {
  const payload = parseJwtPayload(token)
  if (!payload?.exp) return true
  return payload.exp * 1000 - Date.now() < withinMs
}

/**
 * 从 localStorage 读取 bio_user 中存储的 accessToken
 * bio_user 是 pinia-plugin-persistedstate 的存储格式: { token, userInfo }
 */
function getStoredToken(): string {
  try {
    const stored = localStorage.getItem('bio_user')
    if (stored) {
      const parsed = JSON.parse(stored)
      return parsed.token || ''
    }
  } catch {
    // ignore parse errors
  }
  return ''
}

/**
 * 检查是否是刷新 token 的请求（防止死循环）
 */
function isRefreshRequest(config?: AxiosRequestConfig): boolean {
  return !!config?.url?.includes('/admin/auth/refreshToken')
}

/**
 * 处理 401 响应，尝试刷新 token
 */
async function handleUnauthorized(
  originalRequest: InternalAxiosRequestConfig
): Promise<AxiosResponse> {
  const userStore = useUserStore()
  const currentToken = getStoredToken()

  // 如果刷新请求本身返回 401，说明 refresh token 也失效了，强制登出
  if (isRefreshRequest(originalRequest)) {
    isRefreshing = false
    requests.forEach((cb) => cb())
    requests = []
    userStore.logout()
    ElMessage.error('登录状态已过期，请重新登录')
    return Promise.reject(new Error('Refresh Token 失效'))
  }

  // 如果用户已登录，尝试刷新 token
  if (currentToken) {
    if (!isRefreshing) {
      isRefreshing = true
      try {
        const refreshRes = await axiosInstance.post('/api/admin/auth/refreshToken', {
          token: currentToken
        })
        const newAccessToken: string = refreshRes.data.result

        // 更新 store 中的 token（pinia-plugin-persistedstate 会自动持久化）
        userStore.token = newAccessToken

        // 让所有挂起请求重新执行
        requests.forEach((cb) => cb())
        requests = []

        // 重试原请求
        originalRequest.headers.set('Authorization', `Bearer ${newAccessToken}`)
        return axiosInstance(originalRequest)
      } catch {
        // 刷新失败，退出登录
        userStore.logout()
        ElMessage.error('登录状态已过期，请重新登录')
        requests.forEach((cb) => cb())
        requests = []
        return Promise.reject(new Error('刷新 Token 失败'))
      } finally {
        isRefreshing = false
      }
    } else {
      // 已经在刷新 token，把请求挂起
      return new Promise((resolve) => {
        requests.push(() => {
          // token 刷新完成后重试原请求
          const newToken = getStoredToken()
          if (newToken) {
            originalRequest.headers.set('Authorization', `Bearer ${newToken}`)
            resolve(axiosInstance(originalRequest))
          }
        })
      })
    }
  }

  // 未登录状态，直接登出
  userStore.logout()
  ElMessage.error('登录已过期，请重新登录')
  return Promise.reject(new Error('未登录'))
}

// ============================================================
// Request Interceptor
// ============================================================

axiosInstance.interceptors.request.use(
  async (request: InternalAxiosRequestConfig) => {
    // 从 localStorage 读取 bio_user 中的 token（pinia-plugin-persistedstate 格式）
    const token = getStoredToken()
    if (token) {
      // 主动刷新：token 即将过期时提前刷新，避免 401
      if (isTokenExpiringSoon(token) && !isRefreshRequest(request)) {
        if (!isRefreshing) {
          isRefreshing = true
          try {
            const refreshRes = await axiosInstance.post('/api/admin/auth/refreshToken', {
              token
            })
            const newAccessToken: string = refreshRes.data.result
            const userStore = useUserStore()
            userStore.token = newAccessToken
            requests.forEach((cb) => cb())
            requests = []
          } catch (e) {
            console.warn('Proactive token refresh failed, will retry on 401')
          } finally {
            isRefreshing = false
          }
        }
      }
      // 使用最新 token
      const latestToken = getStoredToken()
      if (latestToken) {
        request.headers.set('Authorization', `Bearer ${latestToken}`)
      }
    }
    return request
  },
  (error) => {
    return Promise.reject(error)
  }
)

// ============================================================
// Response Interceptor
// ============================================================

axiosInstance.interceptors.response.use(
  async (response: AxiosResponse) => {
    const { code, message: msg } = response.data as IResponse

    // 二进制数据直接返回（blob / arraybuffer）
    if (
      response.config.responseType === 'blob' ||
      response.config.responseType === 'arraybuffer'
    ) {
      return response
    }

    // HTTP 状态码 401（由后端在 HTTP 层返回）
    if (response.status === ApiStatus.UNAUTHORIZED) {
      return handleUnauthorized(response.config as InternalAxiosRequestConfig)
    }

    // 业务状态码 200 -> 返回 result
    if (code === ApiStatus.SUCCESS) {
      return Promise.resolve(response)
    }

    // 业务状态码 401 -> token 过期，触发刷新流程
    if (code === ApiStatus.UNAUTHORIZED) {
      return handleUnauthorized(response.config as InternalAxiosRequestConfig)
    }

    // 其他业务错误码
    if (!response.config.silent) {
      ElMessage.error(msg || '请求失败')
    }
    return Promise.reject(new Error(msg || '请求失败'))
  },
  (error) => {
    // 请求被取消
    if (axios.isCancel(error)) {
      console.log('请求已取消:', error.message)
      return Promise.reject(error)
    }

    // HTTP 401 -> token 过期
    if (error.response?.status === ApiStatus.UNAUTHORIZED) {
      return handleUnauthorized(error.config as InternalAxiosRequestConfig)
    }

    // 其他 HTTP 错误
    if (error.response) {
      const { status, data } = error.response
      const errorMessage = data?.message
      switch (status) {
        case ApiStatus.FORBIDDEN:
          ElMessage.error(errorMessage || '没有权限执行此操作')
          break
        case ApiStatus.NOT_FOUND:
          ElMessage.error(errorMessage || '请求的资源不存在')
          break
        case ApiStatus.SERVER_ERROR:
          ElMessage.error(errorMessage || '服务器错误，请稍后重试')
          break
        default:
          ElMessage.error(errorMessage || `请求失败 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络连接')
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }

    return Promise.reject(error)
  }
)

// ============================================================
// Request Helper
// ============================================================

async function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  try {
    const res = await axiosInstance.request<T>(config)
    return res.data as T
  } catch (e) {
    return Promise.reject(e)
  }
}

// ============================================================
// API Methods
// 支持两种调用方式以保持向后兼容：
//   1. 标准 axios 风格: api.get<T>('/url', { params })
//   2. 配置对象风格:   api.get<T>({ url: '/url', params })
// ============================================================

function get<T = any>(
  urlOrConfig: string | AxiosRequestConfig,
  config?: AxiosRequestConfig
): Promise<T> {
  if (typeof urlOrConfig === 'string') {
    return request<T>({ url: urlOrConfig, method: 'GET', ...config })
  }
  return request<T>({ ...urlOrConfig, method: 'GET' })
}

function post<T = any>(
  urlOrConfig: string | AxiosRequestConfig,
  data?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  if (typeof urlOrConfig === 'string') {
    return request<T>({ url: urlOrConfig, method: 'POST', data, ...config })
  }
  return request<T>({ ...urlOrConfig, method: 'POST' })
}

function put<T = any>(
  urlOrConfig: string | AxiosRequestConfig,
  data?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  if (typeof urlOrConfig === 'string') {
    return request<T>({ url: urlOrConfig, method: 'PUT', data, ...config })
  }
  return request<T>({ ...urlOrConfig, method: 'PUT' })
}

function del<T = any>(
  urlOrConfig: string | AxiosRequestConfig,
  config?: AxiosRequestConfig
): Promise<T> {
  if (typeof urlOrConfig === 'string') {
    return request<T>({ url: urlOrConfig, method: 'DELETE', ...config })
  }
  return request<T>({ ...urlOrConfig, method: 'DELETE' })
}

// ============================================================
// Exports
// ============================================================

export const api = { get, post, put, del }

// 默认导出，保持与现有 API 文件的兼容性
// import http from '@/utils/http/axios'
// http.get<T>('/url', { params })
export default api
