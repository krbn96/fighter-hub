// バックエンドの TeamResponse に対応する型
export interface Team {
  id: number
  tournamentId: number
  tournamentName: string
  ownerId: number
  ownerName: string
  name: string
  rankRequirement: string | null
  characterRequirements: number[] | null
  recruitmentMessage: string | null
  createdAt: string
  updatedAt: string
}
