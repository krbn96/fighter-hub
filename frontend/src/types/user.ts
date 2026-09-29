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
