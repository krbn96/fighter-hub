import { apiClient } from './client'
import type { UserCreateRequest, UserCreateResponse, UserMe } from '@/types/user'

// Axios呼び出しのみを担当する。localStorage/Piniaの操作はstores/auth.ts側で行う。
export async function fetchMe(): Promise<UserMe> {
  const response = await apiClient.get<UserMe>('/users/me')
  return response.data
}

// POST /api/users。新規ユーザー登録(公開API、認証不要)。
export async function createUser(request: UserCreateRequest): Promise<UserCreateResponse> {
  const response = await apiClient.post<UserCreateResponse>('/users', request)
  return response.data
}
