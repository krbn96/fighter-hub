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

// バックエンドの TournamentCreateRequest に対応する型(ADMIN専用)。
// startAt/recruitmentDeadlineはタイムゾーン無しのローカル日時文字列
// (例: "2026-12-20T13:00:00")。Dateオブジェクトへの変換は行わない
// (formatDateTime.tsと同じ方針。UTC変換による意図しない時刻のずれを避けるため)。
export interface TournamentCreateRequest {
  name: string
  teamSize: number
  startAt: string
  recruitmentDeadline: string
  maxPlayers: number
}

// バックエンドの TournamentUpdateRequest(PATCH、ADMIN専用)に対応する型。
// キーを省略した場合は「更新しない」を表す(JsonNullableのundefinedに相当、TeamUpdateRequestと同じ方針)。
// AdminTournamentEditViewでは5項目すべてを毎回送信するため、実際には省略しない。
export interface TournamentUpdateRequest {
  name?: string
  teamSize?: number
  startAt?: string
  recruitmentDeadline?: string
  maxPlayers?: number
}

// TournamentForm.vue(Create/Edit共通フォーム)が扱うフォーム値の型。
// TournamentCreateRequestと同じ形(5項目は常にすべて埋まる。TeamFormValuesと同じ方針)。
export interface TournamentFormValues {
  name: string
  teamSize: number
  maxPlayers: number
  startAt: string
  recruitmentDeadline: string
}
