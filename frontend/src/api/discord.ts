import { apiClient } from './client'
import type { DiscordAuthorizeResponse } from '@/types/discord'

// POST /api/oauth/discord/authorize
// JWT必須。既存apiClientのAuthorizationヘッダー付与(Bearer interceptor)をそのまま利用する。
export async function startDiscordAuthorization(): Promise<DiscordAuthorizeResponse> {
  const response = await apiClient.post<DiscordAuthorizeResponse>('/oauth/discord/authorize')
  return response.data
}

// DELETE /api/users/me/discord
// JWT必須。未連携状態で呼んでも204(冪等)。
export async function unlinkDiscordAccount(): Promise<void> {
  await apiClient.delete('/users/me/discord')
}
