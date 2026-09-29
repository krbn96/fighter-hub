import { apiClient } from './client'
import type { Team } from '@/types/team'

// Axios呼び出しのみを担当する(api/tournaments.ts等と同じ形式)。

// GET /api/tournaments/{tournamentId}/teams
// エンドポイントはTournamentController配下だが、戻り値がTeamのためこちらに置く。
export async function fetchTeamsByTournament(tournamentId: string): Promise<Team[]> {
  const response = await apiClient.get<Team[]>(`/tournaments/${tournamentId}/teams`)
  return response.data
}

// GET /api/teams/{id}
export async function fetchTeamById(id: string): Promise<Team> {
  const response = await apiClient.get<Team>(`/teams/${id}`)
  return response.data
}
