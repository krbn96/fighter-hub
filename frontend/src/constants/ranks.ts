// FIGHTER HUBの確定ランク仕様(8種類)。
// value(英字コード)をAPI(rankRequirement)へ送信し、labelを画面表示に使う。
// MR(Master Rate)による絞り込みは今回未実装。
export interface RankOption {
  value: string
  label: string
}

export const RANK_OPTIONS: RankOption[] = [
  { value: 'ROOKIE', label: 'ルーキー' },
  { value: 'IRON', label: 'アイアン' },
  { value: 'BRONZE', label: 'ブロンズ' },
  { value: 'SILVER', label: 'シルバー' },
  { value: 'GOLD', label: 'ゴールド' },
  { value: 'PLATINUM', label: 'プラチナ' },
  { value: 'DIAMOND', label: 'ダイヤモンド' },
  { value: 'MASTER', label: 'マスター' },
]
