import axios from 'axios'
import { AUTH_TOKEN_STORAGE_KEY } from '@/constants/auth'

// baseURLを/apiにしておくと、開発環境ではvite.config.tsのproxyによって
// 既存Spring Bootバックエンドへ転送される。
export const apiClient = axios.create({
  baseURL: '/api',
})

// localStorageにJWTが保存されている場合のみAuthorizationヘッダーを付与する。
// 401発生時の共通処理(自動ログアウト等)はDay 2ではまだ実装しない。
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})
