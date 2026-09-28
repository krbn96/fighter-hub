import axios from 'axios'

// baseURLを/apiにしておくと、開発環境ではvite.config.tsのproxyによって
// 既存Spring Bootバックエンドへ転送される。
// JWT認証のヘッダー付与などはまだ実装しない。
export const apiClient = axios.create({
  baseURL: '/api',
})
