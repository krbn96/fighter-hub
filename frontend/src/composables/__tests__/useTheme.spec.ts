import { beforeEach, describe, expect, it } from 'vitest'

import { initTheme, useTheme } from '../useTheme'
import { THEME_STORAGE_KEY } from '@/constants/theme'

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  it('保存値が無い場合、初期値はlightになる', () => {
    initTheme()
    const { theme } = useTheme()

    expect(theme.value).toBe('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('toggleThemeでdarkへ切り替わる', () => {
    initTheme()
    const { theme, toggleTheme } = useTheme()

    toggleTheme()

    expect(theme.value).toBe('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('toggleTheme時にlocalStorageへ保存される', () => {
    initTheme()
    const { toggleTheme } = useTheme()

    toggleTheme()

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })

  it('保存済みのdarkがinitTheme時に復元される', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')

    initTheme()
    const { theme } = useTheme()

    expect(theme.value).toBe('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('localStorageの値が不正な場合はlightへfallbackする', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'not-a-theme')

    initTheme()
    const { theme } = useTheme()

    expect(theme.value).toBe('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })
})
