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

// バックエンドの TeamCreateRequest に対応する型
export interface TeamCreateRequest {
  tournamentId: number
  name: string
  rankRequirement: string | null
  characterRequirements: number[] | null
  recruitmentMessage: string | null
}

// バックエンドの TeamCreateResponse に対応する型
export interface TeamCreateResponse {
  id: number
  tournamentId: number
  ownerId: number
  name: string
  rankRequirement: string | null
  characterRequirements: number[] | null
  recruitmentMessage: string | null
  createdAt: string
  updatedAt: string
}

// バックエンドの TeamUpdateRequest(PATCH)に対応する型。
// キーを省略した場合は「更新しない」を表す(JsonNullableのundefinedに相当)。
// 今回のTeamEditViewでは4項目すべてを毎回送信するため、実際には省略しない。
export interface TeamUpdateRequest {
  name?: string
  rankRequirement?: string | null
  characterRequirements?: number[] | null
  recruitmentMessage?: string | null
}
