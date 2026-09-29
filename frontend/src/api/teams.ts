import { apiClient } from './client'
import type { Team, TeamCreateRequest, TeamCreateResponse, TeamUpdateRequest } from '@/types/team'

// Axios呼び出しのみを担当する(api/tournaments.ts等と同じ形式)。
// localStorage/Bearer Tokenの扱いはapi/client.tsのrequest interceptorに任せる。

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

// POST /api/teams
export async function createTeam(request: TeamCreateRequest): Promise<TeamCreateResponse> {
  const response = await apiClient.post<TeamCreateResponse>('/teams', request)
  return response.data
}

// PATCH /api/teams/{id}
export async function updateTeam(id: string, request: TeamUpdateRequest): Promise<Team> {
  const response = await apiClient.patch<Team>(`/teams/${id}`, request)
  return response.data
}

// GET /api/teams/my
export async function fetchMyTeams(): Promise<Team[]> {
  const response = await apiClient.get<Team[]>('/teams/my')
  return response.data
}
