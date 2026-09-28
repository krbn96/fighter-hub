// バックエンドの GET /api/characters, GET /api/characters/{id} が返す
// CharacterResponse(id, name)に対応する型
export interface Character {
  id: number
  name: string
}
