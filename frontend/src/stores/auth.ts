import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginRequest } from '@/api/auth'
import { fetchMe } from '@/api/user'
import { AUTH_TOKEN_STORAGE_KEY } from '@/constants/auth'
import type { UserMe } from '@/types/user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<UserMe | null>(null)

  const isAuthenticated = computed(() => token.value !== null)

  async function login(email: string, password: string) {
    const response = await loginRequest({ email, password })

    token.value = response.accessToken
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, response.accessToken)

    user.value = await fetchMe()
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
  }

  // ページリロード時に呼び出し、localStorageに保存済みのJWTから認証状態を復元する。
  // JWTが無効・期限切れの場合はGET /users/meが失敗するため、その場合はログアウト扱いにする。
  async function restore() {
    const storedToken = localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)

    if (!storedToken) {
      return
    }

    token.value = storedToken

    try {
      user.value = await fetchMe()
    } catch {
      logout()
    }
  }

  return { token, user, isAuthenticated, login, logout, restore }
})
