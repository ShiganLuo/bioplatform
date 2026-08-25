import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, refreshToken as refreshTokenApi, getUserInfo, logout as logoutApi } from '@/api/loginApi'
import type { UserInfo, LoginParams } from '@/api/loginApi'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('access_token') || '')
  const refreshTokenValue = ref<string>(localStorage.getItem('refresh_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  // Check if user is authenticated
  function isAuthenticated() {
    return !!token.value
  }

  // Login action
  async function login(params: LoginParams) {
    try {
      const res = await loginApi(params)
      // axios interceptor already unwraps ApiResponse.result
      if (!res?.accessToken) {
        console.error('Login failed: no accessToken in response', res)
        return false
      }
      token.value = res.accessToken
      refreshTokenValue.value = res.refreshToken
      localStorage.setItem('access_token', res.accessToken)
      localStorage.setItem('refresh_token', res.refreshToken)

      // Fetch user info after login
      await fetchUserInfo()
      return true
    } catch (error) {
      return false
    }
  }

  // Fetch user info
  async function fetchUserInfo() {
    try {
      const res = await getUserInfo()
      // axios interceptor already unwraps ApiResponse.result
      userInfo.value = res
      localStorage.setItem('userInfo', JSON.stringify(res))
    } catch (error) {
      console.error('Failed to fetch user info:', error)
    }
  }

  // Refresh token
  async function refresh() {
    try {
      const res = await refreshTokenApi(refreshTokenValue.value)
      // axios interceptor already unwraps ApiResponse.result
      token.value = res.accessToken
      refreshTokenValue.value = res.refreshToken
      localStorage.setItem('access_token', res.accessToken)
      localStorage.setItem('refresh_token', res.refreshToken)
    } catch (error) {
      logout()
    }
  }

  // Logout action
  async function logout() {
    try {
      await logoutApi()
    } catch (error) {
      // Ignore logout errors
    } finally {
      token.value = ''
      refreshTokenValue.value = ''
      userInfo.value = null
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      localStorage.removeItem('userInfo')
      router.push('/login')
    }
  }

  // Check permission
  function hasPermission(permission: string) {
    if (!userInfo.value) return false
    return userInfo.value.permissions?.includes(permission) || false
  }

  // Check role
  function hasRole(role: string) {
    if (!userInfo.value) return false
    return userInfo.value.roles?.includes(role) || false
  }

  // Initialize user info from localStorage
  function initUserInfo() {
    const stored = localStorage.getItem('userInfo')
    if (stored) {
      try {
        userInfo.value = JSON.parse(stored)
      } catch (error) {
        console.error('Failed to parse user info:', error)
      }
    }
  }

  return {
    token,
    refreshToken: refreshTokenValue,
    userInfo,
    isAuthenticated,
    login,
    fetchUserInfo,
    refresh,
    logout,
    hasPermission,
    hasRole,
    initUserInfo
  }
})
