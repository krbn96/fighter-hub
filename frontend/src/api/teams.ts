import { apiClient } from './client'
import type {
  Team,
  TeamCreateRequest,
  TeamCreateResponse,
  TeamMember,
  TeamUpdateRequest,
} from '@/types/team'

// Axios呼び出しのみを担当する(api/tournaments.ts等と同じ形式)。
// localStorage/Bearer Tokenの扱いはapi/client.tsのrequest interceptorに任せる。

export interface FetchTeamsByTournamentParams {
  name?: string
  characterId?: number
  rank?: string
  available?: boolean
}

// GET /api/tournaments/{tournamentId}/teams
// エンドポイントはTournamentController配下だが、戻り値がTeamのためこちらに置く。
// nameは未指定または空白のみの場合はqueryへ含めない(api/tournaments.tsのfetchTournamentsと同じ方針)。
export async function fetchTeamsByTournament(
  tournamentId: string,
  params: FetchTeamsByTournamentParams = {},
): Promise<Team[]> {
  const trimmedName = params.name?.trim()
  const response = await apiClient.get<Team[]>(`/tournaments/${tournamentId}/teams`, {
    params: {
      name: trimmedName ? trimmedName : undefined,
      characterId: params.characterId,
      rank: params.rank,
      available: params.available,
    },
  })
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

// GET /api/teams/{id}/members
export async function fetchTeamMembers(teamId: string): Promise<TeamMember[]> {
  const response = await apiClient.get<TeamMember[]>(`/teams/${teamId}/members`)
  return response.data
}
