import { ref } from 'vue'
import { THEME_STORAGE_KEY, type Theme } from '@/constants/theme'

// Application/Team/Tournament用途ではないUI設定のため、Piniaは使わず
// モジュールスコープのrefで共有する(AppHeaderのトグルボタンと連動する)。
const theme = ref<Theme>('light')

function isValidTheme(value: string | null): value is Theme {
  return value === 'light' || value === 'dark'
}

function applyTheme(value: Theme) {
  theme.value = value
  document.documentElement.setAttribute('data-theme', value)
}

// アプリ起動時に一度だけ呼び出す。保存値が無い/不正な場合はLightへfallbackする。
export function initTheme() {
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  applyTheme(isValidTheme(stored) ? stored : 'light')
}

export function useTheme() {
  function toggleTheme() {
    const next: Theme = theme.value === 'light' ? 'dark' : 'light'
    applyTheme(next)
    localStorage.setItem(THEME_STORAGE_KEY, next)
  }

  return { theme, toggleTheme }
}
