import { apiClient } from './client'
import type { Tournament } from '@/types/tournament'

// Axios呼び出しのみを担当する(api/auth.ts, api/user.tsと同じ形式)。
export async function fetchTournaments(): Promise<Tournament[]> {
  const response = await apiClient.get<Tournament[]>('/tournaments')
  return response.data
}

export async function fetchTournamentById(id: string): Promise<Tournament> {
  const response = await apiClient.get<Tournament>(`/tournaments/${id}`)
  return response.data
}
