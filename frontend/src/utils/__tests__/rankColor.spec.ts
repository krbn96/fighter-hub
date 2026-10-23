import { describe, expect, it } from 'vitest'

import { getMrColorVar, getRankColorVar } from '../rankColor'

describe('getRankColorVar', () => {
  it.each([
    ['ROOKIE', 'var(--rank-rookie)'],
    ['IRON', 'var(--rank-iron)'],
    ['BRONZE', 'var(--rank-bronze)'],
    ['SILVER', 'var(--rank-silver)'],
    ['GOLD', 'var(--rank-gold)'],
    ['PLATINUM', 'var(--rank-platinum)'],
    ['DIAMOND', 'var(--rank-diamond)'],
    ['MASTER', 'var(--rank-master)'],
  ])('%sは%sを返す', (rank, expected) => {
    expect(getRankColorVar(rank)).toBe(expected)
  })

  it('未知のrank文字列はエラーにせずfallback色を返す', () => {
    expect(getRankColorVar('LEGEND')).toBe('var(--color-text-secondary)')
    expect(getRankColorVar('')).toBe('var(--color-text-secondary)')
  })
})

describe('getMrColorVar', () => {
  it('1599以下は緑を返す', () => {
    expect(getMrColorVar(0)).toBe('var(--mr-band-green)')
    expect(getMrColorVar(1599)).toBe('var(--mr-band-green)')
  })

  it('1600〜1699は銀を返す(下限・上限境界)', () => {
    expect(getMrColorVar(1600)).toBe('var(--mr-band-silver)')
    expect(getMrColorVar(1699)).toBe('var(--mr-band-silver)')
  })

  it('1700〜1799は金を返す(下限・上限境界)', () => {
    expect(getMrColorVar(1700)).toBe('var(--mr-band-gold)')
    expect(getMrColorVar(1799)).toBe('var(--mr-band-gold)')
  })

  it('1800以上はピンクを返す', () => {
    expect(getMrColorVar(1800)).toBe('var(--mr-band-pink)')
    expect(getMrColorVar(9999)).toBe('var(--mr-band-pink)')
  })

  it('mrがnullの場合はエラーにせずfallback色を返す', () => {
    expect(getMrColorVar(null)).toBe('var(--color-primary)')
  })

  it('MRの緑色はMASTERバッジの色(--rank-master)とは別の変数を参照する', () => {
    expect(getMrColorVar(1599)).not.toBe('var(--rank-master)')
  })
})
