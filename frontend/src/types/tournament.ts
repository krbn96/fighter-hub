// バックエンドの TournamentResponse に対応する型
export interface Tournament {
  id: number
  name: string
  teamSize: number
  startAt: string
  recruitmentDeadline: string
  maxPlayers: number
  createdAt: string
  updatedAt: string
}
