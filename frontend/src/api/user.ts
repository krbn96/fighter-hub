import { apiClient } from './client'
import type { UserMe } from '@/types/user'

// Axios呼び出しのみを担当する。localStorage/Piniaの操作はstores/auth.ts側で行う。
export async function fetchMe(): Promise<UserMe> {
  const response = await apiClient.get<UserMe>('/users/me')
  return response.data
}
