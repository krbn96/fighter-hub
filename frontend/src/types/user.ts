// バックエンドの UserCharacterResponse に対応する型
export interface UserCharacter {
  characterId: number
  rank: string
  mr: number | null
}

// バックエンドの UserMeResponse に対応する型
export interface UserMe {
  id: number
  name: string
  characters: UserCharacter[]
  playTimeStart: string | null
  playTimeEnd: string | null
  message: string | null
  email: string | null
  xId: string | null
  discordId: string | null
  createdAt: string
  updatedAt: string
}

// バックエンドの UserPublicResponse に対応する型。
// UserMeと異なりemailを持たない(公開プロフィールでは取得できないため)。
export interface UserPublic {
  id: number
  name: string
  characters: UserCharacter[]
  playTimeStart: string | null
  playTimeEnd: string | null
  message: string | null
  xId: string | null
  discordId: string | null
  createdAt: string
  updatedAt: string
}

// バックエンドの UserUpdateRequest(PATCH /api/users/me)に対応する型。
// 今回編集するのはname/characters/playTimeStart/playTimeEnd/messageのみ。
// xId/discordIdは編集対象外のため今回は含めない(キーを省略するとJsonNullableのundefinedと
// なり、backend側では「更新しない」として扱われるため、既存値は変更されない)。
// charactersも同様にoptionalとし、Character一覧が取得できない場合は省略して送信する。
export interface UserUpdateRequest {
  name: string
  characters?: UserCharacter[]
  playTimeStart: string | null
  playTimeEnd: string | null
  message: string | null
}
