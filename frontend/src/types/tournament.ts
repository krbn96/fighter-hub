// バックエンドの TournamentResponse に対応する型
export interface Tournament {
  id: number
  name: string
  teamSize: number
  startAt: string
  recruitmentDeadline: string
  maxPlayers: number
  // Backendとの互換性のため残すが、画面上の募集状態表示にはstatusを使わず、
  // recruitmentDeadlineから導出する(@/utils/recruitmentStatus参照)。
  status: string
  createdAt: string
  updatedAt: string
}
