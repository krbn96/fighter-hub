// バックエンドの TournamentResponse に対応する型
export interface Tournament {
  id: number
  name: string
  teamSize: number
  startAt: string
  maxPlayers: number
  status: string
  createdAt: string
  updatedAt: string
}
