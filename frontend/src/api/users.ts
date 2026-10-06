import { apiClient } from './client'
import type { UserMe, UserPublic, UserUpdateRequest } from '@/types/user'

// 公開プロフィール(GET /api/users/{id})用のwrapper。
// 認証済み本人用のfetchMe()はapi/user.tsに既存のため、そちらは変更しない。

// GET /api/users/{id}
export async function fetchUserById(userId: number): Promise<UserPublic> {
  const response = await apiClient.get<UserPublic>(`/users/${userId}`)
  return response.data
}

// PATCH /api/users/me
// requestのキーを省略した項目(charactersが未取得の場合のcharacters)は
// JSON.stringifyでそのままキー自体が省略され、JsonNullableのundefined(更新しない)として扱われる。
// xId/discordUsernameはこのAPIでは編集できない(discordUsernameはDiscord OAuthフロー経由でのみ更新される)。
export async function updateMe(request: UserUpdateRequest): Promise<UserMe> {
  const response = await apiClient.patch<UserMe>('/users/me', request)
  return response.data
}
