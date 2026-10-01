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

// バックエンドの TeamMemberResponse に対応する型。
// role/statusはbackend側に存在しないため含まない(owner判定はteam.ownerIdとの比較で行う)。
export interface TeamMember {
  userId: number
  userName: string
  joinedAt: string
}

// TeamForm.vue(Create/Edit共通フォーム)が扱うフォーム値の型。
// TeamCreateRequestからtournamentIdを除いたものと同じ形で、4項目は常にすべて埋まる
// (TeamUpdateRequestと違いoptionalにしない。Editは既存仕様どおり4項目を毎回送信するため)。
export interface TeamFormValues {
  name: string
  rankRequirement: string | null
  characterRequirements: number[] | null
  recruitmentMessage: string | null
}
