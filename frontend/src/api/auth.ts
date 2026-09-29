import { apiClient } from './client'
import type { LoginRequest, LoginResponse } from '@/types/auth'

// Axios呼び出しのみを担当する。localStorage/Piniaの操作はstores/auth.ts側で行う。
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', request)
  return response.data
}
