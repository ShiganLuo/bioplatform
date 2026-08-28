import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, LoginParams, RegisterParams } from '@/api/authApi'
import { login as loginApi, register as registerApi, getUserInfo, logout as logoutApi } from '@/api/authApi'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const refreshToken = ref<string>('')
    const userInfo = ref<UserInfo | null>(null)
    let _logoutTimer: ReturnType<typeof setTimeout> | null = null
    let _loggingOut = false

    const isLoggedIn = computed(() => !!token.value)
    const isAuthenticated = isLoggedIn
    const username = computed(() => userInfo.value?.username || '')
    const nickname = computed(() => userInfo.value?.nickName || userInfo.value?.username || '')

    /** 启动 token 过期定时检查 */
    function _startExpiryCheck() {
      _stopExpiryCheck()
      if (!token.value) return
      try {
        const base64Url = token.value.split('.')[1]
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
        const payload = JSON.parse(atob(base64))
        if (payload.exp) {
          const msLeft = payload.exp * 1000 - Date.now()
          if (msLeft <= 0) {
            logout()
            return
          }
          // 提前10秒检查，避免边界情况
          _logoutTimer = setTimeout(() => logout(), msLeft + 10000)
        }
      } catch {
        // token 解析失败，不做处理
      }
    }

    function _stopExpiryCheck() {
      if (_logoutTimer) {
        clearTimeout(_logoutTimer)
        _logoutTimer = null
      }
    }

    // 登录
    async function login(params: LoginParams) {
      const res = await loginApi(params) as any
      // axios interceptor already unwraps: returns {accessToken, refreshToken, userInfo}
      const data = res?.result || res
      if (!data?.accessToken) {
        console.error('登录失败: 无accessToken', res)
        throw new Error('登录失败')
      }
      token.value = data.accessToken
      refreshToken.value = data.refreshToken || ''
      _startExpiryCheck()
      // After login, fetch user info
      await fetchUserInfo()
      return data
    }

    // 注册
    async function register(params: RegisterParams) {
      const res = await registerApi(params) as any
      // axios interceptor already unwraps result
      return res?.result || res
    }

    // 获取用户信息
    async function fetchUserInfo() {
      try {
        const res = await getUserInfo() as any
        // axios interceptor already unwraps result
        const data = res?.result || res
        userInfo.value = data
        return userInfo.value
      } catch (e) {
        console.error('获取用户信息失败', e)
        logout()
        return null
      }
    }

    // 退出登录（防重复调用）
    async function logout() {
      if (_loggingOut) return
      _loggingOut = true
      _stopExpiryCheck()
      try {
        await logoutApi()
      } catch {
        // ignore logout errors
      }
      token.value = ''
      refreshToken.value = ''
      userInfo.value = null
      localStorage.removeItem('bio_user')
      _loggingOut = false
    }

    // 页面加载时，如果已有token则启动过期检查
    if (token.value) {
      _startExpiryCheck()
    }

    return {
      token,
      refreshToken,
      userInfo,
      isLoggedIn,
      isAuthenticated,
      username,
      nickname,
      login,
      register,
      fetchUserInfo,
      logout,
    }
  },
  {
    persist: {
      key: 'bio_user',
      paths: ['token', 'refreshToken', 'userInfo'],
    },
  }
)
