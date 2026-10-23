// ランクバッジ(文字色・枠線色、PlayerProfileでは`border: 1px solid currentColor`により
// 文字色がそのまま枠線色にもなる)・MASTERのMR数値の色を、MAIN/SUB CHARACTERで共有するための
// 唯一の定義。色の実体はfrontend/src/assets/base.cssのCSS変数(ライト/ダーク両テーマに対応)。
const RANK_COLOR_VARS: Record<string, string> = {
  ROOKIE: 'var(--rank-rookie)',
  IRON: 'var(--rank-iron)',
  BRONZE: 'var(--rank-bronze)',
  SILVER: 'var(--rank-silver)',
  GOLD: 'var(--rank-gold)',
  PLATINUM: 'var(--rank-platinum)',
  DIAMOND: 'var(--rank-diamond)',
  MASTER: 'var(--rank-master)',
}

// rankはDB/API上は自由文字列のため、既知の8種類以外の値が来てもエラーにせず
// 既存のtext-secondary相当にfallbackする。
const FALLBACK_RANK_COLOR_VAR = 'var(--color-text-secondary)'

export function getRankColorVar(rank: string): string {
  return RANK_COLOR_VARS[rank] ?? FALLBACK_RANK_COLOR_VAR
}

// MASTERのMRのみを対象とする色分け(呼び出し元がrank==='MASTER'であることを保証する)。
// MASTERバッジ自体の色(--rank-master)から導出せず、別の変数(--mr-band-*)を参照する
// 独立した定義にすることで、バッジの緑とMRの緑を区別できるようにしている。
// LEGENDのような追加ランクをMR値から自動判定することはしない(このmoduleはMRの色分けのみを行う)。
const MR_COLOR_BANDS: ReadonlyArray<{ max: number; colorVar: string }> = [
  { max: 1599, colorVar: 'var(--mr-band-green)' },
  { max: 1699, colorVar: 'var(--mr-band-silver)' },
  { max: 1799, colorVar: 'var(--mr-band-gold)' },
  { max: Infinity, colorVar: 'var(--mr-band-pink)' },
]

const FALLBACK_MR_COLOR_VAR = 'var(--color-primary)'

export function getMrColorVar(mr: number | null): string {
  if (mr === null) {
    return FALLBACK_MR_COLOR_VAR
  }

  const band = MR_COLOR_BANDS.find((candidate) => mr <= candidate.max)
  return band?.colorVar ?? FALLBACK_MR_COLOR_VAR
}
