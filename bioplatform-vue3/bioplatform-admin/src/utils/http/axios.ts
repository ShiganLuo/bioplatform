import axios, { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

// ==================== 类型定义 ====================

interface IResponse<T> {
  code: number
  message: string
  result: T
}

declare module 'axios' {
  interface AxiosRequestConfig {
    silent?: boolean
  }
}

// ==================== 创建实例 ====================

const baseURL = import.meta.env.VITE_API_BASE_URL

const axiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// ==================== Token 自动刷新机制 ====================

let isRefreshing = false
let requests: Array<(token: string | null) => void> = []

/**
 * 延迟加载 userStore，避免循环依赖
 * (store -> api -> http -> store)
 * 使用动态 import() 以兼容 Vite 的 ES Module 环境
 */
let _userStore: any = null
async function getUserStore() {
  if (!_userStore) {
    const mod = await import('@/stores/user')
    _userStore = mod.useUserStore()
  }
  return _userStore
}

/**
 * 执行 token 刷新逻辑
 */
async function doRefreshToken(originalRequest: AxiosRequestConfig & { _retry?: boolean }): Promise<any> {
  const userStore = await getUserStore()

  if (!isRefreshing) {
    isRefreshing = true
    try {
      // 直接用 axios 调用刷新接口，避免走拦截器
      const refreshToken = localStorage.getItem('refresh_token')
      if (!refreshToken) {
        throw new Error('No refresh token')
      }

      const refreshRes = await axios.post(`${baseURL}/api/admin/auth/refreshToken`, {
        refreshToken
      })

      const refreshData: IResponse<{ accessToken: string; refreshToken: string }> = refreshRes.data
      if (refreshData.code === 200) {
        const newAccessToken = refreshData.result.accessToken
        const newRefreshToken = refreshData.result.refreshToken

        // 更新 store 和 localStorage
        userStore.token = newAccessToken
        userStore.refreshToken = newRefreshToken
        localStorage.setItem('access_token', newAccessToken)
        localStorage.setItem('refresh_token', newRefreshToken)

        // 让所有挂起的请求重新执行
        requests.forEach((cb) => cb(newAccessToken))
        requests = []

        // 重试原请求
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`
        return axiosInstance(originalRequest)
      } else {
        throw new Error(refreshData.message || 'Refresh token failed')
      }
    } catch (err) {
      // 刷新失败，退出登录
      requests.forEach((cb) => cb(null))
      requests = []
      userStore.logout()
      ElMessage.error('登录状态已过期，请重新登录')
      return Promise.reject(err)
    } finally {
      isRefreshing = false
    }
  } else {
    // 已经在刷新 token，把请求挂起
    return new Promise((resolve, reject) => {
      requests.push((newAccessToken: string | null) => {
        if (!newAccessToken) {
          reject(new Error('刷新 token 失败'))
          return
        }
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`
        resolve(axiosInstance(originalRequest))
      })
    })
  }
}

// ==================== 请求拦截器 ====================

axiosInstance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// ==================== 响应拦截器 ====================

axiosInstance.interceptors.response.use(
  async (response: AxiosResponse<IResponse<any>>) => {
    // 二进制数据直接返回
    if (
      response.config.responseType === 'blob' ||
      response.config.responseType === 'arraybuffer'
    ) {
      return response
    }

    const { code, message } = response.data

    // 业务状态码 200 -> 返回 result
    if (code === 200) {
      return response.data.result as any
    }

    // 401 -> token 过期，尝试刷新
    if (code === 401) {
      const originalRequest = response.config as AxiosRequestConfig & { _retry?: boolean }

      // 防止刷新请求自身死循环
      if (originalRequest.url?.includes('/refreshToken')) {
        isRefreshing = false
        requests.forEach((cb) => cb(null))
        requests = []
        const userStore = await getUserStore()
        userStore.logout()
        ElMessage.error('登录状态已过期，请重新登录')
        return Promise.reject(new Error('Refresh Token 失效'))
      }

      if (!originalRequest._retry) {
        originalRequest._retry = true
        return doRefreshToken(originalRequest)
      }
    }

    // 其他非 200 状态码 -> 显示错误（除 silent 模式）
    if (!response.config.silent) {
      ElMessage.error(message || '请求失败')
    }
    return Promise.reject(new Error(message || '请求失败'))
  },
  (error) => {
    // 请求取消
    if (axios.isCancel(error)) {
      console.log('请求已取消:', error.message)
      return Promise.reject(error)
    }

    // HTTP 错误状态码处理
    if (error.response) {
      const { status, data } = error.response

      // 401 也走 token 刷新
      if (status === 401) {
        const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }
        if (originalRequest && !originalRequest.url?.includes('/refreshToken') && !originalRequest._retry) {
          originalRequest._retry = true
          return doRefreshToken(originalRequest)
        }
      }

      // 非 silent 模式显示错误
      if (!error.config?.silent) {
        ElMessage.error(data?.message || '请求失败')
      }
    } else if (error.message?.includes('timeout')) {
      if (!error.config?.silent) {
        ElMessage.error('请求超时，请稍后重试')
      }
    } else {
      if (!error.config?.silent) {
        ElMessage.error('网络连接失败，请检查网络')
      }
    }

    return Promise.reject(error)
  }
)

// ==================== 统一请求函数 ====================

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const res = await axiosInstance.request<T>(config)
    return res as any
  } catch (e) {
    return Promise.reject(e)
  }
}


const http = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'GET' })
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, data, method: 'POST' })
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, data, method: 'PUT' })
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'DELETE' })
  }
}

export default http

export { axiosInstance }
