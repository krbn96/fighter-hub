import { apiClient } from './client'
import type { Tournament } from '@/types/tournament'

export interface FetchTournamentsParams {
  name?: string
  recruiting?: boolean
}

// Axios呼び出しのみを担当する(api/auth.ts, api/user.tsと同じ形式)。
// nameは未指定または空白のみの場合はqueryへ含めない(backend側もその場合は条件なしとして扱うが、
// 送信自体も省略して意図を明確にする)。
export async function fetchTournaments(params: FetchTournamentsParams = {}): Promise<Tournament[]> {
  const trimmedName = params.name?.trim()
  const response = await apiClient.get<Tournament[]>('/tournaments', {
    params: {
      name: trimmedName ? trimmedName : undefined,
      recruiting: params.recruiting,
    },
  })
  return response.data
}

export async function fetchTournamentById(id: string): Promise<Tournament> {
  const response = await apiClient.get<Tournament>(`/tournaments/${id}`)
  return response.data
}
