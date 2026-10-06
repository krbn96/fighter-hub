import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useAuthStore } from '../auth'
import { AUTH_TOKEN_STORAGE_KEY } from '@/constants/auth'
import type { LoginRequest, LoginResponse } from '@/types/auth'
import type { UserMe } from '@/types/user'

vi.mock('@/api/auth', () => ({
  login: vi.fn<(request: LoginRequest) => Promise<LoginResponse>>(),
}))
vi.mock('@/api/user', () => ({
  fetchMe: vi.fn<() => Promise<UserMe>>(),
}))

import { login as loginRequest } from '@/api/auth'
import { fetchMe } from '@/api/user'

const dummyUser: UserMe = {
  id: 1,
  name: 'Test User',
  characters: [],
  playTimeStart: null,
  playTimeEnd: null,
  message: null,
  email: 'test@example.com',
  xId: null,
  discordUsername: null,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.mocked(loginRequest).mockReset()
    vi.mocked(fetchMe).mockReset()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('login成功時にtoken/userがstateとlocalStorageへ保存される', async () => {
    vi.mocked(loginRequest).mockResolvedValue({ accessToken: 'token-123' })
    vi.mocked(fetchMe).mockResolvedValue(dummyUser)

    const authStore = useAuthStore()
    await authStore.login('test@example.com', 'password123')

    expect(authStore.token).toBe('token-123')
    expect(authStore.user).toEqual(dummyUser)
    expect(authStore.isAuthenticated).toBe(true)
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBe('token-123')
  })

  it('login失敗時はtoken/userが設定されずlocalStorageにも保存されない', async () => {
    vi.mocked(loginRequest).mockRejectedValue(new Error('Invalid email or password.'))

    const authStore = useAuthStore()
    await expect(authStore.login('test@example.com', 'wrong-password')).rejects.toThrow(
      'Invalid email or password.',
    )

    expect(authStore.token).toBeNull()
    expect(authStore.user).toBeNull()
    expect(authStore.isAuthenticated).toBe(false)
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('login時、token取得後のfetchMe失敗で認証状態がロールバックされる', async () => {
    vi.mocked(loginRequest).mockResolvedValue({ accessToken: 'token-123' })
    vi.mocked(fetchMe).mockRejectedValue(new Error('Unauthorized'))

    const authStore = useAuthStore()
    await expect(authStore.login('test@example.com', 'password123')).rejects.toThrow(
      'Unauthorized',
    )

    expect(authStore.token).toBeNull()
    expect(authStore.user).toBeNull()
    expect(authStore.isAuthenticated).toBe(false)
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('logoutでtoken/userとlocalStorageがクリアされAPIは呼ばれない', async () => {
    vi.mocked(loginRequest).mockResolvedValue({ accessToken: 'token-123' })
    vi.mocked(fetchMe).mockResolvedValue(dummyUser)

    const authStore = useAuthStore()
    await authStore.login('test@example.com', 'password123')

    authStore.logout()

    expect(authStore.token).toBeNull()
    expect(authStore.user).toBeNull()
    expect(authStore.isAuthenticated).toBe(false)
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('restore成功時はlocalStorageのtokenからuserを復元する', async () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, 'stored-token')
    vi.mocked(fetchMe).mockResolvedValue(dummyUser)

    const authStore = useAuthStore()
    await authStore.restore()

    expect(authStore.token).toBe('stored-token')
    expect(authStore.user).toEqual(dummyUser)
    expect(authStore.isAuthenticated).toBe(true)
  })

  it('restore失敗時(JWT無効・期限切れ)はtoken/userとlocalStorageがクリアされる', async () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, 'expired-token')
    vi.mocked(fetchMe).mockRejectedValue(new Error('Unauthorized'))

    const authStore = useAuthStore()
    await authStore.restore()

    expect(authStore.token).toBeNull()
    expect(authStore.user).toBeNull()
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('localStorageにtokenが無い場合restoreは何もせずfetchMeも呼ばれない', async () => {
    const authStore = useAuthStore()
    await authStore.restore()

    expect(authStore.token).toBeNull()
    expect(authStore.user).toBeNull()
    expect(fetchMe).not.toHaveBeenCalled()
  })
})
