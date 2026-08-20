import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, LoginParams, RegisterParams } from '@/api/authApi'
import { login as loginApi, register as registerApi, getUserInfo, logout as logoutApi } from '@/api/authApi'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const userInfo = ref<UserInfo | null>(null)

    const isLoggedIn = computed(() => !!token.value)
    const isAuthenticated = isLoggedIn
    const username = computed(() => userInfo.value?.username || '')
    const nickname = computed(() => userInfo.value?.nickName || userInfo.value?.username || '')

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

    // 退出登录
    async function logout() {
      try {
        await logoutApi()
      } catch {
        // ignore logout errors
      }
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('bio_user')
    }

    return {
      token,
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
      paths: ['token', 'userInfo'],
    },
  }
)
