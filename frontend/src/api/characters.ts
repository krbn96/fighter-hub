import { apiClient } from './client'
import type { Character } from '@/types/character'

// 一覧を1回取得しID→nameへ変換する用途を想定(Characterごとの個別取得は行わない)。
// TeamCreateView/TeamEditViewの既存apiClient直呼び出しのリファクタは今回対象外(Phase 5)。

// GET /api/characters
export async function fetchCharacters(): Promise<Character[]> {
  const response = await apiClient.get<Character[]>('/characters')
  return response.data
}
